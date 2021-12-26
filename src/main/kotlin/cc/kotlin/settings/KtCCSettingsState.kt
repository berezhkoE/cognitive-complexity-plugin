package cc.kotlin.settings

import com.intellij.openapi.components.BaseState

class KtCCSettingsState: BaseState() {
    var showPropertyAccessorComplexity by property(false)
}