package com.github.berezhkoe.cognitivecomplexityplugin

import com.github.berezhkoe.cognitivecomplexityplugin.settings.CognitiveComplexitySettings
import com.intellij.codeInsight.hints.FactoryInlayHintsCollector
import com.intellij.codeInsight.hints.InlayHintsSink
import com.intellij.codeInsight.hints.presentation.InlayPresentation
import com.intellij.codeInsight.hints.presentation.InsetPresentation
import com.intellij.codeInsight.hints.presentation.RoundWithBackgroundPresentation
import com.intellij.openapi.editor.Editor
import com.intellij.psi.PsiElement
import com.intellij.psi.PsiRecursiveElementVisitor
import com.intellij.psi.util.CachedValueProvider
import com.intellij.psi.util.CachedValuesManager
import com.intellij.psi.util.PsiModificationTracker
import org.jetbrains.kotlin.psi.psiUtil.startOffset
import java.awt.Color

@Suppress("UnstableApiUsage")
class CognitiveComplexityInlayHintsCollector(
    private val editor: Editor,
    private val getElementVisitor: (PsiElement) -> AbstractCognitiveComplexityElementVisitor,
    private val isClass: (PsiElement) -> Boolean,
    private val isClassMember: (PsiElement) -> Boolean
) :
    FactoryInlayHintsCollector(editor) {

    companion object {
        fun getComplexityScore(
            element: PsiElement,
            getVisitor: (element: PsiElement) -> AbstractCognitiveComplexityElementVisitor
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
        if (isClass(element)) {
            val complexityScore = evalClassComplexity(element)

            if (complexityScore > 0) {
                sink.addBlockElement(
                    offset = element.startOffset,
                    relatesToPrecedingText = false,
                    showAbove = true,
                    priority = 0,
                    presentation = getPresentation(element, complexityScore)
                )
            }
        } else if (isClassMember(element)) {
            val complexityScore = getComplexityScore(element, getElementVisitor)

            if (complexityScore > 0) {
                sink.addBlockElement(
                    offset = element.startOffset,
                    relatesToPrecedingText = false,
                    showAbove = true,
                    priority = 0,
                    presentation = getPresentation(element, complexityScore)
                )
            }
        }
        return true
    }

    private fun getPresentation(element: PsiElement, complexityScore: Int): InlayPresentation {
        return RoundWithBackgroundPresentation(
            InsetPresentation(
                factory.text(getInlayText(complexityScore)),
                left = 7,
                right = 7,
                top = 1,
                down = 1
            ),
            8,
            8,
            getInlayColor(complexityScore),
            0.6f
        ).shiftTo(element.startOffset, editor)
    }

    private fun getInlayText(complexityScore: Int): String {
        return CognitiveComplexitySettings.getInstance().thresholdsList.stream()
            .sorted { o1, o2 -> o1.threshold.compareTo(o2.threshold) }
            .filter { complexityScore <= it.threshold }
            .findFirst()
            .map { it.text!!.replaceFirst("%complexity%", complexityScore.toString()) }
            .orElse("Oh what a %complexity%!")
    }

    private fun getInlayColor(complexityScore: Int): Color {
        val settings = CognitiveComplexitySettings.getInstance()
        return settings.thresholdsList.stream()
            .sorted { o1, o2 -> o1.threshold.compareTo(o2.threshold) }
            .filter { complexityScore <= it.threshold }
            .findFirst()
            .map { settings.getColor(it.color!!)!! }
            .orElse(settings.ourDefaultColors["Blue"])
    }

    private fun InlayPresentation.shiftTo(offset: Int, editor: Editor): InlayPresentation {
        val document = editor.document
        val column = offset - document.getLineStartOffset(document.getLineNumber(offset))

        return factory.seq(factory.textSpacePlaceholder(column, true), this)
    }

//    /**
//     * @see PsiElement.isClass
//     * @return true if PsiElement is Class member with block,
//     *         for example function, constructor, initializer block, static block, etc.
//     */
//    protected abstract fun PsiElement.isClassMember(): Boolean
//
//    /**
//     * @return true if PsiElement is Class, Interface, Enum, etc.
//     */
//    protected abstract fun PsiElement.isClass(): Boolean
//
//    abstract fun getElementVisitor(element: PsiElement): AbstractCognitiveComplexityElementVisitor

    private fun evalClassComplexity(element: PsiElement): Int {
        var complexity = 0

        element.accept(object : PsiRecursiveElementVisitor() {
            override fun visitElement(element: PsiElement) {
                if (isClassMember(element)) {
                    complexity += getComplexityScore(element, getElementVisitor)
                } else {
                    super.visitElement(element)
                }
            }
        })
        return complexity
    }

    override fun equals(other: Any?): Boolean {
        if (other is CognitiveComplexityInlayHintsCollector) {
            return editor == other.editor
        }
        return false
    }

    override fun hashCode(): Int {
        return editor.hashCode()
    }

    abstract class AbstractCognitiveComplexityElementVisitor : PsiRecursiveElementVisitor(true) {
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