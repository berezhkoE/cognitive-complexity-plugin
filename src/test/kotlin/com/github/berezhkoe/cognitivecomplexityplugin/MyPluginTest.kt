package com.github.berezhkoe.cognitivecomplexityplugin

import com.github.berezhkoe.cognitivecomplexityplugin.kotlin.KtCognitiveComplexityInlayProvider
import com.intellij.psi.PsiDocumentManager
import com.intellij.testFramework.LightPlatformCodeInsightTestCase
import junit.framework.TestCase
import org.jetbrains.kotlin.psi.KtNamedFunction
import org.jetbrains.kotlin.psi.psiUtil.getChildOfType

class MyPluginTest : LightPlatformCodeInsightTestCase() {
    override fun getTestDataPath() = "src/test/testData/complexity"

    private fun doTest(path: String, complexity: Int) {
        configureByFile(path)
        val getVisitor = KtCognitiveComplexityInlayProvider()::getElementVisitor

        PsiDocumentManager.getInstance(project).getPsiFile(editor.document)?.getChildOfType<KtNamedFunction>()?.let {
            TestCase.assertEquals(complexity,
                CognitiveComplexityInlayHintsCollector.getComplexityScore(it, getVisitor))
        }
    }

    fun testFirst() {
        doTest("/1.kt", 8)
    }

    fun testSecond() {
        doTest("/2.kt", 35)
    }

    fun testThird() {
        doTest("/3.kt", 9)
    }

    fun testForth() {
        doTest("/4.kt", 2)
    }

    fun testFifth() {
        doTest("/5.kt", 7)
    }

    fun testSixth() {
        doTest("/6.kt", 1)
    }

    fun testSeventh() {
        doTest("/7.kt", 20)
    }

    fun testEighth() {
        doTest("/8.kt", 21)
    }
}
