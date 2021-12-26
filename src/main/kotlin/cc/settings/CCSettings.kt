package cc.settings

import com.intellij.ide.IdeBundle
import com.intellij.openapi.components.SimplePersistentStateComponent
import com.intellij.openapi.components.State
import com.intellij.openapi.components.Storage
import com.intellij.openapi.components.service
import com.intellij.ui.ColorUtil
import com.intellij.ui.JBColor
import java.awt.Color
import java.util.*

@State(name = "CognitiveComplexitySetting", storages = [(Storage("cognitive_complexity.xml"))])
class CCSettings :
    SimplePersistentStateComponent<CCSettingsState>(CCSettingsState()) {

    companion object {
        fun getInstance(): CCSettings {
            return service()
        }
    }

    var thresholdsList
        get() = state.thresholdsList
        set(value) {
            state.thresholdsList = value
        }

    var defaultText
        get() = state.defaultText
        set(value) {
            state.defaultText = value
        }

    val ourDefaultColors: Map<String, Color> = mapOf(
        "Blue" to JBColor.namedColor("HintColor.Blue", JBColor(0xeaf6ff, 0x4f556b)),
        "Green" to JBColor.namedColor("HintColor.Green", JBColor(0xeffae7, 0x49544a)),
        "Orange" to JBColor.namedColor("HintColor.Orange", JBColor(0xf6e9dc, 0x806052)),
        "Rose" to JBColor.namedColor("HintColor.Rose", JBColor(0xf2dcda, 0x6e535b)),
        "Violet" to JBColor.namedColor("HintColor.Violet", JBColor(0xe6e0f1, 0x534a57)),
        "Yellow" to JBColor.namedColor("HintColor.Yellow", JBColor(0xffffe4, 0x4f4b41)),
        "Gray" to JBColor.namedColor("HintColor.Gray", JBColor(0xf5f5f5, 0x45484a))
    )

    fun getColor(id: String): Color? {
        val color = ourDefaultColors[id]
        return color ?: ColorUtil.fromHex(id, null)
    }

    fun getColorName(id: String): String {
        return if (ourDefaultColors.containsKey(id)) IdeBundle.message(
            "color.name." + id.lowercase(
                Locale.ENGLISH
            )
        ) else IdeBundle.message("settings.file.color.custom.name")
    }
}