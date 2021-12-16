package cc

import cc.kotlin.KtCCInlayProvider
import com.intellij.testFramework.LightPlatformCodeInsightTestCase
import junit.framework.TestCase
import org.jetbrains.kotlin.psi.KtNamedFunction
import org.jetbrains.kotlin.psi.psiUtil.getChildOfType

class KotlinComplexityCalculationTest : LightPlatformCodeInsightTestCase() {
    override fun getTestDataPath() = "src/test/testData/kotlin/complexity"

    private fun doTest(path: String, complexity: Int) {
        configureByFile(path)
        val getVisitor = KtCCInlayProvider()::getElementVisitor

        TestCase.assertEquals(
            complexity,
            LanguageInfoProvider.CCInlayHintsCollector.Companion.getComplexityScore(
                requireNotNull(file.getChildOfType<KtNamedFunction>()),
                getVisitor
            )
        )
    }

    fun `test first`() = doTest("/${getTestName(true)}.kt", 8)

    fun `test second`() = doTest("/${getTestName(true)}.kt", 35)

    fun `test third`() = doTest("/${getTestName(true)}.kt", 9)

    fun `test forth`() = doTest("/${getTestName(true)}.kt", 2)

    fun `test fifth`() = doTest("/${getTestName(true)}.kt", 7)

    fun `test sixth`() = doTest("/${getTestName(true)}.kt", 1)

    fun `test seventh`() = doTest("/${getTestName(true)}.kt", 20)

    fun `test eighth`() = doTest("/${getTestName(true)}.kt", 21)

    override fun getTestName(lowercaseFirstLetter: Boolean): String {
        return super.getTestName(lowercaseFirstLetter).trim().replace(' ', '_')
    }
}
