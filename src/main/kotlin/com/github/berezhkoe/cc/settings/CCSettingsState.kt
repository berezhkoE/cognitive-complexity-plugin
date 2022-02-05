package com.github.berezhkoe.cc.settings

import com.github.berezhkoe.cc.CCBundle.message
import com.intellij.openapi.components.BaseState
import com.intellij.util.containers.addAllIfNotNull

class CCSettingsState : BaseState() {
    var thresholdsList by list<ThresholdState>()

    var defaultText by string(message("default.hint.text"))

    var showBeforeAnnotations by property(true)

    class ThresholdState : BaseState() {
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

    init {
        thresholdsList.addAllIfNotNull(
            ThresholdState.fromMapping(0, "Gray", message("default.hint.text")),
            ThresholdState.fromMapping(10, "Blue", message("default.hint.text")),
            ThresholdState.fromMapping(50, "Green", message("default.hint.text")),
            ThresholdState.fromMapping(80, "Violet", message("default.hint.text")),
        )
    }
}