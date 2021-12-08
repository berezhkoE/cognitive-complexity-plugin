package com.github.berezhkoe.cognitivecomplexityplugin.settings

import com.intellij.ide.IdeBundle
import com.intellij.openapi.components.SimplePersistentStateComponent
import com.intellij.openapi.components.State
import com.intellij.openapi.components.Storage
import com.intellij.openapi.components.service
import com.intellij.ui.ColorUtil
import com.intellij.ui.JBColor
import java.awt.Color
import java.util.*
import java.util.Map

@State(name = "CognitiveComplexitySetting", storages = [(Storage("cognitive_complexity.xml"))])
class CognitiveComplexitySettings :
    SimplePersistentStateComponent<CognitiveComplexitySettingsState>(CognitiveComplexitySettingsState()) {

    companion object {
        fun getInstance(): CognitiveComplexitySettings {
            return service()
        }
    }

    var showPropertyAccessorsComplexity
        get() = state.showPropertyAccessorComplexity
        set(value) {
            state.showPropertyAccessorComplexity = value
        }

    var thresholdsList
        get() = state.thresholdsList
        set(value) {
            state.thresholdsList = value
        }

    var enableHighlighting
        get() = state.enableHighlighting
        set(value) {
            state.enableHighlighting = value
        }


    val ourDefaultColors: MutableMap<String, Color> = Map.of(
        "Blue", JBColor.namedColor("HintColor.Blue", JBColor(0xeaf6ff, 0x4f556b)),
        "Green", JBColor.namedColor("HintColor.Green", JBColor(0xeffae7, 0x49544a)),
        "Orange", JBColor.namedColor("HintColor.Orange", JBColor(0xf6e9dc, 0x806052)),
        "Rose", JBColor.namedColor("HintColor.Rose", JBColor(0xf2dcda, 0x6e535b)),
        "Violet", JBColor.namedColor("HintColor.Violet", JBColor(0xe6e0f1, 0x534a57)),
        "Yellow", JBColor.namedColor("HintColor.Yellow", JBColor(0xffffe4, 0x4f4b41)),
//        "Gray", JBColor.namedColor("HintColor.Gray", JBColor(0xf5f5f5, 0x45484a))
    )

    fun getColor(id: String): Color? {
        val color = ourDefaultColors[id]
        return color ?: ColorUtil.fromHex(id, null)
    }

    fun getColorName(id: String): String {
        return if (ourDefaultColors.containsKey(id)) IdeBundle.message(
            "color.name." + id.toLowerCase(
                Locale.ENGLISH
            )
        ) else IdeBundle.message("settings.file.color.custom.name")
    }
}