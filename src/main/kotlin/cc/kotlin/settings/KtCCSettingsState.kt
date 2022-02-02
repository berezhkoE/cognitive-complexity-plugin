package cc.kotlin.settings

import com.intellij.openapi.components.BaseState

class KtCCSettingsState: BaseState() {
    var considerPropertyAccessorComplexity by property(false)
}