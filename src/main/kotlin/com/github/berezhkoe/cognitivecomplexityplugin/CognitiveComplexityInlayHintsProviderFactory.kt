package com.github.berezhkoe.cognitivecomplexityplugin

import com.intellij.codeInsight.hints.InlayHintsProviderFactory
import com.intellij.codeInsight.hints.ProviderInfo
import com.intellij.openapi.project.Project

@Suppress("UnstableApiUsage")
class CognitiveComplexityInlayHintsProviderFactory : InlayHintsProviderFactory {
    override fun getProvidersInfo(project: Project): List<ProviderInfo<out Any>> {
        return LanguageInfoProvider.EP_NAME
            .extensionList.map { ProviderInfo(it.getLanguage(), it) }
    }
}