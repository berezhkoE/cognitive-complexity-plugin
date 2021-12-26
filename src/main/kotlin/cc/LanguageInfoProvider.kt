package cc

import cc.settings.CCSettings
import cc.settings.CCSettingsState
import com.intellij.codeInsight.hints.*
import com.intellij.codeInsight.hints.presentation.InlayPresentation
import com.intellij.codeInsight.hints.presentation.InsetPresentation
import com.intellij.codeInsight.hints.presentation.RoundWithBackgroundPresentation
import com.intellij.lang.Language
import com.intellij.openapi.editor.BlockInlayPriority
import com.intellij.openapi.editor.Editor
import com.intellij.openapi.extensions.ExtensionPointName
import com.intellij.psi.PsiElement
import com.intellij.psi.PsiFile
import com.intellij.psi.PsiRecursiveElementVisitor
import com.intellij.psi.util.CachedValueProvider
import com.intellij.psi.util.CachedValuesManager
import com.intellij.psi.util.PsiModificationTracker
import org.jetbrains.kotlin.psi.psiUtil.startOffsetSkippingComments
import java.awt.Color
import javax.swing.JPanel

@Suppress("UnstableApiUsage")
abstract class LanguageInfoProvider : InlayHintsProvider<NoSettings> {
    companion object {
        var EP_NAME: ExtensionPointName<LanguageInfoProvider> =
            ExtensionPointName.create("berezhkoe.cognitivecomplexity.languageInfoProvider")

        val myKey = SettingsKey<NoSettings>("cognitive.complexity.hint")
    }

    override val key: SettingsKey<NoSettings> = myKey

    override val name: String = "CognitiveComplexityInlayProvider"

    override val previewText = "CognitiveComplexityInlayProvider"

    override fun createConfigurable(settings: NoSettings): ImmediateConfigurable {
        return object : ImmediateConfigurable {
            override fun createComponent(listener: ChangeListener) = JPanel()
        }
    }

    override fun getCollectorFor(
        file: PsiFile,
        editor: Editor,
        settings: NoSettings,
        sink: InlayHintsSink
    ): InlayHintsCollector? {
        return CCInlayHintsCollector(
            editor,
            getLanguage()
        )
    }

    override fun createSettings() = NoSettings()

    abstract fun getLanguage(): Language

    abstract fun getElementVisitor(element: PsiElement): CCElementVisitor

    abstract fun isClassMember(element: PsiElement): Boolean

    abstract fun isClassWithBody(element: PsiElement): Boolean

    class CCInlayHintsCollector(
        private val editor: Editor,
        private val language: Language
    ) :
        FactoryInlayHintsCollector(editor) {

        private val provider = EP_NAME.extensionList.first { it.getLanguage() == language }

        private val settings = CCSettings.getInstance()

        companion object {
            fun getComplexityScore(
                element: PsiElement,
                getVisitor: (element: PsiElement) -> CCElementVisitor
            ): Int {
                return CachedValuesManager.getCachedValue(element) {
                    CachedValueProvider.Result.create(
                        getVisitor(element).evalComplexity(element),
                        PsiModificationTracker.MODIFICATION_COUNT
                    )
                }
            }
        }

        override fun collect(element: PsiElement, editor: Editor, sink: InlayHintsSink): Boolean {
            val complexityScore = if (provider.isClassWithBody(element)) {
                evalClassComplexity(element)
            } else if (provider.isClassMember(element)) {
                getComplexityScore(element, provider::getElementVisitor)
            } else null

            complexityScore?.let { score ->
                getPresentation(element, score)?.let {
                    sink.addBlockElement(
                        offset = element.startOffsetSkippingComments,
                        relatesToPrecedingText = false,
                        showAbove = true,
                        priority = BlockInlayPriority.CODE_VISION,
                        presentation = it
                    )
                }
            }
            return true
        }

        private fun getPresentation(element: PsiElement, complexityScore: Int): InlayPresentation? {
            val hintSettings = getHintSettings(complexityScore) ?: return null
            return RoundWithBackgroundPresentation(
                InsetPresentation(
                    factory.text(getInlayText(complexityScore, hintSettings)),
                    left = 7,
                    right = 7,
                    top = 2,
                    down = 2
                ),
                8,
                8,
                getInlayColor(hintSettings),
                0.9f
            ).shiftTo(element.startOffsetSkippingComments, editor)
        }

        private fun getHintSettings(complexityScore: Int): CCSettingsState.ThresholdState? {
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

        private fun getInlayText(complexityScore: Int, hintSettings: CCSettingsState.ThresholdState): String {
            return hintSettings
                .let { it.text!! }
                .replaceFirst("%complexity%", complexityScore.toString())
        }

        private fun getInlayColor(hintSettings: CCSettingsState.ThresholdState): Color {
            return hintSettings
                .let { settings.getColor(it.color!!)!! }
        }

        private fun InlayPresentation.shiftTo(offset: Int, editor: Editor): InlayPresentation {
            val document = editor.document
            val column = offset - document.getLineStartOffset(document.getLineNumber(offset))

            return factory.seq(factory.textSpacePlaceholder(column, true), this)
        }

        private fun evalClassComplexity(element: PsiElement): Int {
            var complexity = 0

            element.accept(object : PsiRecursiveElementVisitor() {
                override fun visitElement(element: PsiElement) {
                    if (provider.isClassMember(element)) {
                        complexity += getComplexityScore(element, provider::getElementVisitor)
                    } else {
                        super.visitElement(element)
                    }
                }
            })
            return complexity
        }

        override fun equals(other: Any?): Boolean {
            if (other is CCInlayHintsCollector) {
                return editor == other.editor
            }
            return false
        }

        override fun hashCode(): Int {
            return editor.hashCode()
        }
    }

    abstract class CCElementVisitor : PsiRecursiveElementVisitor(true) {
        protected var complexity = 0
        protected var nesting = 0

        fun evalComplexity(element: PsiElement): Int {
            element.accept(this)
            return complexity
        }

        protected fun increaseComplexityAndNesting() {
            complexity += 1 + nesting
            nesting++
        }

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
}