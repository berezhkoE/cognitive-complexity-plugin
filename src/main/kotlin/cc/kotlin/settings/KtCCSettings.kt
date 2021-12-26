package cc.kotlin.settings

import com.intellij.openapi.components.SimplePersistentStateComponent
import com.intellij.openapi.components.service

class KtCCSettings :
    SimplePersistentStateComponent<KtCCSettingsState>(KtCCSettingsState()) {

    companion object {
        fun getInstance(): KtCCSettings {
            return service()
        }
    }

    var showPropertyAccessorsComplexity
        get() = state.showPropertyAccessorComplexity
        set(value) {
            state.showPropertyAccessorComplexity = value
        }
}