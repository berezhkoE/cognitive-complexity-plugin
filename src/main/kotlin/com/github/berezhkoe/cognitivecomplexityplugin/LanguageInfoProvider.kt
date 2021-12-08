package com.github.berezhkoe.cognitivecomplexityplugin

import com.intellij.codeInsight.hints.*
import com.intellij.lang.Language
import com.intellij.openapi.editor.Editor
import com.intellij.openapi.extensions.ExtensionPointName
import com.intellij.psi.PsiElement
import com.intellij.psi.PsiFile
import javax.swing.JPanel

@Suppress("UnstableApiUsage")
abstract class LanguageInfoProvider : InlayHintsProvider<NoSettings> {
    companion object {
        var EP_NAME: ExtensionPointName<LanguageInfoProvider> =
            ExtensionPointName.create("berezhkoe.cognitivecomplexityplugin.languageInfoProvider")

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
        return CognitiveComplexityInlayHintsCollector(
            editor,
            this::getElementVisitor,
            this::isClass,
            this::isClassMember
        )
    }

    override fun createSettings() = NoSettings()

    abstract fun getLanguage(): Language

    abstract fun getElementVisitor(element: PsiElement): CognitiveComplexityInlayHintsCollector.AbstractCognitiveComplexityElementVisitor

    abstract fun isClassMember(element: PsiElement): Boolean

    abstract fun isClass(element: PsiElement): Boolean
}