package com.github.berezhkoe.cognitivecomplexityplugin

import com.intellij.codeInsight.hints.FactoryInlayHintsCollector
import com.intellij.codeInsight.hints.InlayHintsSink
import com.intellij.codeInsight.hints.presentation.InlayPresentation
import com.intellij.openapi.editor.Editor
import com.intellij.psi.PsiElement
import com.intellij.psi.PsiRecursiveElementVisitor
import com.intellij.psi.tree.TokenSet
import com.intellij.psi.util.CachedValueProvider
import com.intellij.psi.util.CachedValuesManager
import com.intellij.psi.util.PsiModificationTracker

@Suppress("UnstableApiUsage")
abstract class AbstractCognitiveComplexityInlayHintsCollector(private val editor: Editor) :
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
        if (element.isClass()) {
            val complexityScore = evalClassComplexity(element)

            if (complexityScore > 0) {
                sink.addBlockElement(
                    offset = getStartOffset(element),
                    relatesToPrecedingText = false,
                    showAbove = true,
                    priority = 0,
                    presentation = factory.text("Complexity score is $complexityScore")
                        .shiftTo(getStartOffset(element), editor)
                )
            }
        } else if (element.isClassMember()) {
            val complexityScore = getComplexityScore(element, this::getElementVisitor)

            if (complexityScore > 0) {
                sink.addBlockElement(
                    offset = getStartOffset(element),
                    relatesToPrecedingText = false,
                    showAbove = true,
                    priority = 0,
                    presentation = factory.text("Complexity score is $complexityScore")
                        .shiftTo(getStartOffset(element), editor)
                )
            }
        }
        return true
    }

    /**
     * @see PsiElement.isClass
     * @return true if PsiElement is Class member with block,
     *         for example function, constructor, initializer block, static block, etc.
     */
    protected abstract fun PsiElement.isClassMember(): Boolean

    /**
     * @return true if PsiElement is Class, Interface, Enum, etc.
     */
    protected abstract fun PsiElement.isClass(): Boolean

    protected abstract fun getStartOffset(element: PsiElement): Int

    private fun InlayPresentation.shiftTo(offset: Int, editor: Editor): InlayPresentation {
        val document = editor.document
        val column = offset - document.getLineStartOffset(document.getLineNumber(offset))

        return factory.seq(factory.textSpacePlaceholder(column, true), this)
    }

    abstract fun getElementVisitor(element: PsiElement): AbstractCognitiveComplexityElementVisitor

    private fun evalClassComplexity(element: PsiElement): Int {
        var complexity = 0

        val getVisitor = this::getElementVisitor
        element.accept(object : PsiRecursiveElementVisitor() {
            override fun visitElement(element: PsiElement) {
                if (element.isClassMember()) {
                    complexity += getComplexityScore(element, getVisitor)
                } else {
                    super.visitElement(element)
                }
            }
        })
        return complexity
    }

    override fun equals(other: Any?): Boolean {
        if (other is AbstractCognitiveComplexityInlayHintsCollector) {
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