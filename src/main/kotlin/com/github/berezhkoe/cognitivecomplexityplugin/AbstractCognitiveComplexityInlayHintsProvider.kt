package com.github.berezhkoe.cognitivecomplexityplugin

import com.intellij.codeInsight.hints.*
import com.intellij.lang.Language
import com.intellij.openapi.extensions.ExtensionPointName
import javax.swing.JPanel

@Suppress("UnstableApiUsage")
abstract class AbstractCognitiveComplexityInlayHintsProvider : InlayHintsProvider<NoSettings> {
    companion object {
        var EP_NAME: ExtensionPointName<AbstractCognitiveComplexityInlayHintsProvider> =
                ExtensionPointName.create("com.github.berezhkoe.cognitivecomplexityplugin.cognitiveComplexityInlayProvider")

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

    override fun createSettings() = NoSettings()

    abstract fun getLanguage() : Language
}