package cc.kotlin.settings

import com.intellij.openapi.components.SimplePersistentStateComponent
import com.intellij.openapi.components.State
import com.intellij.openapi.components.Storage
import com.intellij.openapi.components.service

@State(name = "KotlinCCSetting", storages = [(Storage("cognitive_complexity.xml"))])
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