package cc.settings

import cc.CCBundle.message
import com.intellij.openapi.components.BaseState

class CCSettingsState: BaseState() {
    var thresholdsList by list<ThresholdState>()

    var defaultText by string(message("default.hint.text"))

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