package com.github.berezhkoe.cognitivecomplexityplugin

import com.intellij.codeInsight.hints.*
import com.intellij.codeInsight.hints.presentation.InlayPresentation
import com.intellij.openapi.editor.Editor
import com.intellij.psi.PsiElement
import com.intellij.psi.PsiFile
import com.intellij.psi.PsiRecursiveElementVisitor
import com.intellij.psi.tree.TokenSet
import org.jetbrains.kotlin.lexer.KtTokens
import org.jetbrains.kotlin.psi.*
import org.jetbrains.kotlin.psi.psiUtil.startOffset
import javax.swing.JPanel

@Suppress("UnstableApiUsage")
class CognitiveComplexityInlayProvider : InlayHintsProvider<NoSettings> {
    companion object {
        val myKey = SettingsKey<NoSettings>("cognitive.complexity.hint")
    }
    override val key: SettingsKey<NoSettings> = myKey

    override val name: String = "CognitiveComplexityInlayProvider"

    override val previewText = "CognitiveComplexityInlayProvider"

    var LOGICAL_OPERATIONS = TokenSet.create(
        KtTokens.LT,
        KtTokens.GT,
        KtTokens.LTEQ,
        KtTokens.GTEQ,
        KtTokens.EQEQEQ,
        KtTokens.EXCLEQEQEQ,
        KtTokens.EQEQ,
        KtTokens.EXCLEQ,
        KtTokens.ANDAND,
        KtTokens.OROR,
        KtTokens.EQ,
        KtTokens.NOT_IN,
        KtTokens.NOT_IS
    )

    override fun createConfigurable(settings: NoSettings): ImmediateConfigurable {
        return object : ImmediateConfigurable {
            override fun createComponent(listener: ChangeListener) = JPanel()
        }
    }

    override fun createSettings() = NoSettings()

    override fun getCollectorFor(
        file: PsiFile,
        editor: Editor,
        settings: NoSettings,
        sink: InlayHintsSink
    ): InlayHintsCollector {
        return object : FactoryInlayHintsCollector(editor) {
            override fun collect(element: PsiElement, editor: Editor, sink: InlayHintsSink): Boolean {
                if (element is KtNamedFunction) {
                    val complexityScore = evalComplexity(element)
                    sink.addBlockElement(
                        offset = element.startOffset,
                        relatesToPrecedingText = false,
                        showAbove = true,
                        priority = 0,
                        presentation = factory.text("Complexity score is $complexityScore")
                            .shiftTo(element.startOffset, editor)
                    )
                }
                return true
            }

            private fun InlayPresentation.shiftTo(offset: Int, editor: Editor): InlayPresentation {
                val document = editor.document
                val column = offset - document.getLineStartOffset(document.getLineNumber(offset))

                return factory.seq(factory.textSpacePlaceholder(column, true), this)
            }

            private fun evalComplexity(element: PsiElement): Int {
                var complexity = 0
                var nesting = 0

                element.accept(object : PsiRecursiveElementVisitor(true) {
                    override fun visitElement(element: PsiElement) {
                        when (element) {
                            is KtWhileExpression -> increaseComplexityAndNesting()
                            is KtDoWhileExpression -> increaseComplexityAndNesting()
                            is KtWhenExpression -> increaseComplexityAndNesting()
                            is KtIfExpression -> {
                                // if exists `else` that is not `else if`
                                val ktExpression = element.`else`
                                if (ktExpression != null && ktExpression !is KtIfExpression) {
                                    complexity++
                                }

                                val parent = element.parent
                                if ((parent is KtContainerNodeForControlStructureBody)
                                    && (parent.expression is KtIfExpression)
                                ) {
                                    nesting++
                                    complexity++
                                } else {
                                    increaseComplexityAndNesting()
                                }
                            }
                            // `else if`
                            is KtContainerNodeForControlStructureBody -> {
                                if ((element.expression is KtIfExpression) && (element.firstChild is KtIfExpression)) {
                                    nesting--
                                }
                            }
                            is KtForExpression -> increaseComplexityAndNesting()
                            is KtCatchClause -> increaseComplexityAndNesting()
                            is KtBreakExpression -> complexity++
                            is KtContinueExpression -> complexity++
                            is KtLambdaExpression -> nesting++
                            is KtBinaryExpression -> processComplexBinaryExpression(element)

                        }
                        super.visitElement(element)
                        postProcess(element)
                    }

                    private fun postProcess(element: PsiElement) {
                        if ((element is KtWhileExpression) ||
                            (element is KtWhenExpression) ||
                            (element is KtDoWhileExpression) ||
                            ((element is KtIfExpression) && (element.`else` !is KtIfExpression)) ||
                            (element is KtForExpression) ||
                            (element is KtCatchClause) ||
                            (element is KtLambdaExpression)
                        ) {
                            nesting--
                        }
                    }

                    /**
                     *  Cognitive complexity increments for each new sequence of like operators
                     */
                    private fun processComplexBinaryExpression(element: KtBinaryExpression) {
                        val operationToken = KtPsiUtil.getOperationToken(element)
                        if (LOGICAL_OPERATIONS.contains(operationToken)) {
                            // binary expression is complex
                            if ((element.left is KtBinaryExpression) || (element.right is KtBinaryExpression)) {
                                val parent = element.parent
                                // top-level composite binary expression
                                if (parent !is KtBinaryExpression) {
                                    complexity++
                                }

                                // closest left neighbour
                                var leftExpr = element.left
                                if (leftExpr is KtBinaryExpression) {
                                    while ((leftExpr as KtBinaryExpression).right is KtBinaryExpression) {
                                        leftExpr = leftExpr.right
                                    }

                                    if (operationToken != KtPsiUtil.getOperationToken(leftExpr)) {
                                        complexity++
                                    }
                                }

                                // closest right neighbour
                                var rightExpr = element.right
                                if (rightExpr is KtBinaryExpression) {
                                    while ((rightExpr as KtBinaryExpression).left is KtBinaryExpression) {
                                        rightExpr = rightExpr.left
                                    }

                                    if (operationToken != KtPsiUtil.getOperationToken(rightExpr)) {
                                        complexity++
                                    }
                                }
                            }
                        }
                    }

                    private fun increaseComplexityAndNesting() {
                        increaseComplexity()
                        nesting++
                    }

                    private fun increaseComplexity() {
                        complexity += 1 + nesting
                    }

                })

                return complexity
            }
        }
    }
}