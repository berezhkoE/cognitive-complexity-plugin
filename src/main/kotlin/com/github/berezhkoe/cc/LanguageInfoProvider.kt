package com.github.berezhkoe.cc

import com.github.berezhkoe.cc.settings.CCSettings
import com.github.berezhkoe.cc.settings.CCSettingsState
import com.intellij.codeInsight.hints.*
import com.intellij.codeInsight.hints.presentation.InlayPresentation
import com.intellij.codeInsight.hints.presentation.InsetPresentation
import com.intellij.codeInsight.hints.presentation.RoundWithBackgroundPresentation
import com.intellij.lang.Language
import com.intellij.openapi.editor.BlockInlayPriority
import com.intellij.openapi.editor.Editor
import com.intellij.openapi.extensions.ExtensionPointName
import com.intellij.openapi.project.Project
import com.intellij.psi.PsiElement
import com.intellij.psi.PsiFile
import com.intellij.psi.PsiRecursiveElementVisitor
import com.intellij.psi.util.CachedValueProvider
import com.intellij.psi.util.CachedValuesManager
import com.intellij.psi.util.parentOfTypes
import com.intellij.util.applyIf
import org.jetbrains.kotlin.psi.KtObjectDeclaration
import org.jetbrains.kotlin.psi.KtProperty
import org.jetbrains.kotlin.psi.KtReturnExpression
import org.jetbrains.kotlin.psi.psiUtil.startOffset
import org.jetbrains.kotlin.psi.psiUtil.startOffsetSkippingComments
import java.awt.Color
import javax.swing.JPanel

abstract class MyLanguageVisitor : PsiRecursiveElementVisitor(true) {
    override fun visitElement(element: PsiElement) {
        processElement(element)
        if (!element.isBinaryExpression()) {
            super.visitElement(element)
        }
        postProcess(element)
    }

    /**
     * Increases complexity and nesting
     */
    protected abstract fun processElement(element: PsiElement)

    /**
     * Decreases nesting
     */
    protected abstract fun postProcess(element: PsiElement)

    /**
     * @return true if PsiElement is Binary Expression with operations || or &&
     */
    protected abstract fun PsiElement.isBinaryExpression(): Boolean
}

class ComplexitySink {
    private var complexity = 0
    private var nesting = 0

    fun decreaseNesting() {
        nesting--
    }

    fun increaseNesting() {
        nesting++
    }

    fun increaseComplexity() {
        complexity++
    }

    fun increaseComplexity(amount: Int) {
        complexity += amount
    }

    fun increaseComplexityAndNesting() {
        complexity += 1 + nesting
        nesting++
    }

    fun getComplexity(): Int {
        return complexity
    }
}

@Suppress("UnstableApiUsage")
interface LanguageInfoProvider {
    companion object {
        var EP_NAME: ExtensionPointName<LanguageInfoProvider> =
            ExtensionPointName.create("berezhkoe.cognitivecomplexity.languageInfoProvider")

        val myKey = SettingsKey<NoSettings>("cognitive.complexity.hint")
    }

    fun getVisitor(sink: ComplexitySink): MyLanguageVisitor

    val language: Language

    fun isClassMember(element: PsiElement): Boolean

    fun isClassWithBody(element: PsiElement): Boolean
}

@Suppress("UnstableApiUsage")
internal class CCInlayHintsProviderFactory : InlayHintsProviderFactory {
    override fun getProvidersInfo(project: Project): List<ProviderInfo<out Any>> {
        return LanguageInfoProvider.EP_NAME
            .extensionList.map { ProviderInfo(it.language, MyInlayHintsProvider(it)) }
    }

    private class MyInlayHintsProvider(private val languageInfoProvider: LanguageInfoProvider) :
        InlayHintsProvider<NoSettings> {
        override fun getCollectorFor(
            file: PsiFile,
            editor: Editor,
            settings: NoSettings,
            sink: InlayHintsSink
        ): InlayHintsCollector {
            return MyFactoryInlayHintsCollector(languageInfoProvider, editor)
        }

        class MyFactoryInlayHintsCollector(
            private val languageInfoProvider: LanguageInfoProvider,
            private val editor: Editor
        ) :
            FactoryInlayHintsCollector(editor) {
            private val settings = CCSettings.getInstance()

            private fun getClassMemberComplexity(element: PsiElement): Int {
                return obtainElementComplexity(element, languageInfoProvider)
            }

            companion object {
                private fun obtainElementComplexity(
                    element: PsiElement,
                    languageInfoProvider: LanguageInfoProvider
                ): Int {
                    return CachedValuesManager.getCachedValue(element) {
                        CachedValueProvider.Result.create(
                            ComplexitySink().apply {
                                element.accept(languageInfoProvider.getVisitor(this))
                            }.getComplexity(),
                            element
                        )
                    }
                }
            }

            private fun getClassComplexity(element: PsiElement): Int {
                return ComplexitySink().also { sink ->
                    element.accept(object : PsiRecursiveElementVisitor() {
                        override fun visitElement(element: PsiElement) {
                            if (languageInfoProvider.isClassMember(element)) {
                                sink.increaseComplexity(getClassMemberComplexity(element))
                            } else {
                                super.visitElement(element)
                            }
                        }
                    })
                }.getComplexity()
            }

            override fun collect(element: PsiElement, editor: Editor, sink: InlayHintsSink): Boolean {
                val complexityScore = if (languageInfoProvider.isClassWithBody(element)) {
                    getClassComplexity(element)
                } else if (languageInfoProvider.isClassMember(element)) {
                    getClassMemberComplexity(element)
                } else null

                complexityScore?.let { score ->
                    applySinkResults(element, score, sink)
                }

                return true
            }

            private fun applySinkResults(element: PsiElement, score: Int, sink: InlayHintsSink) {
                getPresentation(element, score)?.let {
                    sink.addBlockElement(
                        offset = if (settings.showBeforeAnnotations) {
                            element.startOffsetSkippingComments
                        } else {
                            element.textOffset
                        },
                        relatesToPrecedingText = false,
                        showAbove = true,
                        priority = BlockInlayPriority.DOC_RENDER,
                        presentation = it
                    )
                }
            }

            private fun InlayPresentation.shiftTo(offset: Int, editor: Editor): InlayPresentation {
                val document = editor.document
                val column = offset - document.getLineStartOffset(document.getLineNumber(offset))

                return factory.seq(factory.textSpacePlaceholder(column, true), this)
            }

            private fun getPresentation(element: PsiElement, complexityScore: Int): InlayPresentation? {
                val hintSettings = getHintSettings(complexityScore) ?: return null
                return getTextPresentation(
                    complexityScore,
                    hintSettings
                ).applyIf(hintSettings.color != CCBundle.message("settings.colors.dialog.no.color")) {
                    InsetPresentation(
                        RoundWithBackgroundPresentation(
                            InsetPresentation(this, left = 7, right = 7),
                            8,
                            8,
                            getInlayColor(hintSettings),
                            0.9f
                        ), top = 2, down = 2
                    )
                }.shiftTo(getHintOffset(element), editor)
            }

            private fun getHintOffset(element: PsiElement): Int {
                if (element is KtObjectDeclaration) {
                    element.parentOfTypes(KtProperty::class, KtReturnExpression::class)?.let {
                        if (editor.document.getLineNumber(it.startOffset) == editor.document.getLineNumber(element.startOffset)) {
                            return it.startOffset
                        }
                    }
                }
                return element.startOffset
            }

            private fun getTextPresentation(
                complexityScore: Int,
                hintSettings: CCSettingsState.ThresholdState
            ): InlayPresentation {
                return InsetPresentation(
                    factory.text(getInlayText(complexityScore, hintSettings)),
                    top = 2,
                    down = 2
                )
            }

            private fun getHintSettings(complexityScore: Int): CCSettingsState.ThresholdState? {
                if (settings.thresholdsList.isNotEmpty()) {
                    val sortedSettings = settings.thresholdsList
                        .apply { sortWith { o1, o2 -> o1.threshold.compareTo(o2.threshold) } }
                    return sortedSettings
                        .firstOrNull {
                            if (complexityScore == 0) {
                                it.threshold == 0
                            } else {
                                complexityScore <= it.threshold
                            }
                        } ?: return if (complexityScore != 0) {
                        sortedSettings.last()
                    } else null
                }
                return null
            }

            private fun getInlayText(complexityScore: Int, hintSettings: CCSettingsState.ThresholdState): String {
                return hintSettings
                    .let { it.text!! }
                    .replaceFirst("%complexity%", complexityScore.toString())
            }

            private fun getInlayColor(hintSettings: CCSettingsState.ThresholdState): Color {
                return hintSettings
                    .let { settings.getColor(it.color!!)!! }
            }


            override fun equals(other: Any?): Boolean {
                if (other is MyFactoryInlayHintsCollector) {
                    return editor == other.editor
                }
                return false
            }

            override fun hashCode(): Int {
                return editor.hashCode()
            }
        }

        override fun createSettings() = NoSettings()

        override val key: SettingsKey<NoSettings> = LanguageInfoProvider.myKey

        override val name: String = "CCInlayProvider"

        override val previewText = "CCInlayProvider"

        override fun createConfigurable(settings: NoSettings): ImmediateConfigurable {
            return object : ImmediateConfigurable {
                override fun createComponent(listener: ChangeListener) = JPanel()
            }
        }
    }
}
