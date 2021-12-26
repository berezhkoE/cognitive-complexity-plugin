package cc.kotlin.settings

import cc.CCBundle
import com.intellij.codeInsight.daemon.DaemonCodeAnalyzer
import com.intellij.codeInsight.hints.InlayHintsPassFactory
import com.intellij.openapi.application.invokeLater
import com.intellij.openapi.options.BoundSearchableConfigurable
import com.intellij.openapi.project.ProjectManager
import com.intellij.openapi.ui.DialogPanel
import com.intellij.ui.components.JBCheckBox
import com.intellij.util.ui.GridBag
import java.awt.GridBagConstraints
import java.awt.GridBagLayout

@Suppress("UnstableApiUsage")
class KtCCSettingsConfigurable : BoundSearchableConfigurable("Kotlin", "Kotlin", _id = ID) {
    companion object {
        const val ID = "Settings.CognitiveComplexity.Kotlin"
    }

    private val settings
        get() = KtCCSettings.getInstance()

    private var panel: DialogPanel = DialogPanel(GridBagLayout())

    private val showPropertyAccessorsComplexity: JBCheckBox =
        JBCheckBox(
            CCBundle.message("settings.kotlin.show.property.accessor.complexity"),
            settings.showPropertyAccessorsComplexity
        )

    override fun createPanel(): DialogPanel {
        val gb = GridBag().apply {
            defaultAnchor = GridBagConstraints.NORTHWEST
            defaultFill = GridBagConstraints.HORIZONTAL
            nextLine()
        }
        panel.add(showPropertyAccessorsComplexity, gb.next().weightx(1.0).weighty(1.0))
        gb.nextLine()
        panel.reset()
        return panel
    }

    override fun isModified(): Boolean {
        return super.isModified()
                || settings.showPropertyAccessorsComplexity != showPropertyAccessorsComplexity.isSelected
    }

    override fun apply() {
        super.apply()
        settings.showPropertyAccessorsComplexity = showPropertyAccessorsComplexity.isSelected

        invokeLater {
            InlayHintsPassFactory.forceHintsUpdateOnNextPass()
            ProjectManager.getInstance().openProjects.forEach { project ->
                DaemonCodeAnalyzer.getInstance(project).restart()
            }
        }
    }

    override fun reset() {
        super.reset()
        showPropertyAccessorsComplexity.isSelected = settings.showPropertyAccessorsComplexity
    }
}