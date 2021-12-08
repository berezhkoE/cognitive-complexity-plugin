package com.github.berezhkoe.cognitivecomplexityplugin.settings

import com.intellij.openapi.components.BaseState

class CognitiveComplexitySettingsState: BaseState() {
    var showPropertyAccessorComplexity by property(false)

    var thresholdsList by list<ThresholdState>()

    var enableHighlighting by property(false)

    class ThresholdState: BaseState() {
        companion object {
            fun fromMapping(threshold: Int, color: String, text: String) = ThresholdState().apply {
                this.threshold = threshold
                this.color = color
                this.text = text
            }
        }

        var threshold by property(0)
        var color by string("")
        var text by string("")
    }

}