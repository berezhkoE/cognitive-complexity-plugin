package com.github.berezhkoe.cognitivecomplexityplugin

import com.intellij.openapi.editor.Editor
import com.intellij.psi.PsiElement
import com.intellij.psi.PsiRecursiveElementVisitor
import com.intellij.psi.tree.TokenSet
import org.jetbrains.kotlin.descriptors.ClassDescriptor
import org.jetbrains.kotlin.descriptors.SimpleFunctionDescriptor
import org.jetbrains.kotlin.idea.caches.resolve.analyze
import org.jetbrains.kotlin.idea.inspections.RecursivePropertyAccessorInspection
import org.jetbrains.kotlin.idea.util.getReceiverTargetDescriptor
import org.jetbrains.kotlin.lexer.KtToken
import org.jetbrains.kotlin.lexer.KtTokens
import org.jetbrains.kotlin.name.Name
import org.jetbrains.kotlin.psi.*
import org.jetbrains.kotlin.psi.psiUtil.parents
import org.jetbrains.kotlin.psi.psiUtil.startOffset
import org.jetbrains.kotlin.resolve.BindingContext
import org.jetbrains.kotlin.resolve.inline.InlineUtil
import org.jetbrains.kotlin.resolve.scopes.receivers.Receiver
import org.jetbrains.kotlin.resolve.scopes.receivers.ReceiverValue
import org.jetbrains.kotlin.types.expressions.OperatorConventions
import org.jetbrains.kotlin.util.OperatorNameConventions

class KtCognitiveComplexityInlayHintsCollector(editor: Editor) :
    AbstractCognitiveComplexityInlayHintsCollector(editor) {

    override fun getElementVisitor(element: PsiElement): AbstractCognitiveComplexityElementVisitor {
        return object : AbstractCognitiveComplexityElementVisitor() {
            override fun processElement(element: PsiElement) {
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
                        if (parent is KtContainerNodeForControlStructureBody
                            && parent.expression is KtIfExpression
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
                    is KtBreakExpression -> if (element.labelQualifier != null) complexity++
                    is KtContinueExpression -> if (element.labelQualifier != null) complexity++
                    is KtLambdaExpression -> nesting++
                    is KtBinaryExpression -> processComplexBinaryExpression(element)
                    is KtElement -> if (isRecursiveCall(element)) complexity++
                }
            }

            override fun postProcess(element: PsiElement) {
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

            override fun PsiElement.isBinaryExpression(): Boolean {
                return this is KtBinaryExpression && getLogicalOperationsTokens().contains(operationToken)
            }

            private fun KtParenthesizedExpression.containsComplexBinaryExpression(): Boolean {
                return containsBinaryExpression() && (expression as KtBinaryExpression).containsBinaryExpression()
            }

            private fun KtParenthesizedExpression.containsBinaryExpression(): Boolean {
                return expression is KtBinaryExpression
            }

            private fun KtBinaryExpression.containsBinaryExpression(): Boolean {
                return left is KtBinaryExpression || right is KtBinaryExpression
            }

            private fun processComplexBinaryExpression(element: KtBinaryExpression) {
                val tokens: MutableList<Pair<KtToken, Int>> = mutableListOf()

                element.accept(object : PsiRecursiveElementVisitor() {
                    override fun visitElement(element: PsiElement) {
                        if (element is KtBinaryExpression) {
                            val operationToken = KtPsiUtil.getOperationToken(element)
                            if (operationToken != null && getLogicalOperationsTokens().contains(operationToken)) {
                                if (tokens.size != 0 && tokens.last().first == getTempNegOperationToken()) {
                                    processComplexBinaryExpression(element)
                                } else {
                                    tokens.add(Pair(operationToken, element.operationReference.startOffset))
                                    super.visitElement(element) // visit children
                                }
                            }
                        }
                        if (element is KtPrefixExpression) {
                            // if it's `!`
                            if (getNegationOperationToken() == element.operationToken) {
                                // that is applied to parentheses
                                (element.baseExpression as? KtParenthesizedExpression)?.let {
                                    tokens.add(Pair(getTempNegOperationToken(), element.startOffset))
                                    super.visitElement(element) // visit children
                                }
                            }
                        }
                        if (element is KtParenthesizedExpression) {
                            super.visitElement(element)
                            if (tokens.size != 0 && tokens.last().first == getTempNegOperationToken()) {
                                tokens[tokens.size - 1] = Pair(getNegationOperationToken(), tokens.last().second)
                            }
                        }
                    }
                })

                // sort tokens by offset
                val sortedTokens = tokens.sortedBy { it.second }.map { it.first }

                var prevToken: KtToken? = null
                for (token in sortedTokens) {
                    if (token != prevToken) {
                        complexity++
                    }
                    prevToken = token
                }
            }

            private fun getLogicalOperationsTokens(): TokenSet {
                return TokenSet.create(
                    KtTokens.ANDAND,
                    KtTokens.OROR
                )
            }

            private fun getNegationOperationToken(): KtToken {
                return KtTokens.EXCL
            }

            private fun getTempNegOperationToken(): KtToken {
                return KtTokens.QUEST
            }

            private fun isRecursiveCall(element: KtElement): Boolean {
                if (RecursivePropertyAccessorInspection.isRecursivePropertyAccess(element)) return true
                if (RecursivePropertyAccessorInspection.isRecursiveSyntheticPropertyAccess(element)) return true
                // Fast check for names without resolve
                val resolveName = getCallNameFromPsi(element) ?: return false
                val enclosingFunction = getEnclosingFunction(element, false) ?: return false

                val enclosingFunctionName = enclosingFunction.name
                if (enclosingFunctionName != OperatorNameConventions.INVOKE.asString()
                    && enclosingFunctionName != resolveName.asString()
                ) return false

                // Check that there were no not-inlined lambdas on the way to enclosing function
                if (enclosingFunction != getEnclosingFunction(element, true)) return false

                val bindingContext = element.analyze()
                val enclosingFunctionDescriptor =
                    bindingContext[BindingContext.FUNCTION, enclosingFunction] ?: return false

                val call = bindingContext[BindingContext.CALL, element] ?: return false
                val resolvedCall = bindingContext[BindingContext.RESOLVED_CALL, call] ?: return false

                if (resolvedCall.candidateDescriptor.original != enclosingFunctionDescriptor) return false

                fun isDifferentReceiver(receiver: Receiver?): Boolean {
                    if (receiver !is ReceiverValue) return false

                    val receiverOwner = receiver.getReceiverTargetDescriptor(bindingContext) ?: return true

                    return when (receiverOwner) {
                        is SimpleFunctionDescriptor -> receiverOwner != enclosingFunctionDescriptor
                        is ClassDescriptor -> receiverOwner != enclosingFunctionDescriptor.containingDeclaration
                        else -> return true
                    }
                }

                if (isDifferentReceiver(resolvedCall.dispatchReceiver)) return false
                return true
            }

            private fun getEnclosingFunction(element: KtElement, stopOnNonInlinedLambdas: Boolean): KtNamedFunction? {
                for (parent in element.parents) {
                    when (parent) {
                        is KtFunctionLiteral -> if (stopOnNonInlinedLambdas && !InlineUtil.isInlinedArgument(
                                parent,
                                parent.analyze(),
                                false
                            )
                        ) return null
                        is KtNamedFunction -> {
                            when (parent.parent) {
                                is KtBlockExpression, is KtClassBody, is KtFile, is KtScript -> return parent
                                else -> if (stopOnNonInlinedLambdas && !InlineUtil.isInlinedArgument(
                                        parent,
                                        parent.analyze(),
                                        false
                                    )
                                ) return null
                            }
                        }
                        is KtClassOrObject -> return null
                    }
                }
                return null
            }

            private fun getCallNameFromPsi(element: KtElement): Name? {
                when (element) {
                    is KtSimpleNameExpression -> when (val elementParent = element.getParent()) {
                        is KtCallExpression -> return Name.identifier(element.getText())
                        is KtOperationExpression -> {
                            val operationReference = elementParent.operationReference
                            if (element == operationReference) {
                                val node = operationReference.getReferencedNameElementType()
                                return if (node is KtToken) {
                                    val conventionName = if (elementParent is KtPrefixExpression)
                                        OperatorConventions.getNameForOperationSymbol(node, true, false)
                                    else
                                        OperatorConventions.getNameForOperationSymbol(node)

                                    conventionName ?: Name.identifier(element.getText())
                                } else {
                                    Name.identifier(element.getText())
                                }
                            }
                        }
                    }

                    is KtArrayAccessExpression -> return OperatorNameConventions.GET
                    is KtThisExpression -> if (element.getParent() is KtCallExpression) return OperatorNameConventions.INVOKE
                }

                return null
            }
        }
    }

    override fun PsiElement.isClassMember(): Boolean {
        return this is KtSecondaryConstructor || this is KtClassInitializer
                || this is KtNamedFunction || this is KtObjectDeclaration
                || this is KtPropertyAccessor
    }

    override fun PsiElement.isClass(): Boolean {
        return this is KtClass
    }

    override fun getStartOffset(element: PsiElement): Int {
        return element.startOffset
    }
}