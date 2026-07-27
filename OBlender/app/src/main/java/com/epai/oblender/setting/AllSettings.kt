package com.epai.oblender.setting

import com.movtery.layer_controller.utils.snap.SnapMode

object AllSettings {
    object editorEnableWidgetSnap {
        var state: Boolean = true
        fun save(v: Boolean) { state = v }
    }
    object editorSnapInAllLayers {
        var state: Boolean = false
        fun save(v: Boolean) { state = v }
    }
    object editorWidgetSnapMode {
        var state: SnapMode = SnapMode.FullScreen
        fun save(v: SnapMode) { state = v }
    }
    object joystickHideWhenMouse {
        var state: Boolean = false
    }
    object joystickHideWhenGamepad {
        var state: Boolean = false
    }
    object joystickControlSize {
        var state: Float = 120f
    }
    object joystickControlX {
        var state: Float = 5000f
    }
    object joystickControlY {
        var state: Float = 5000f
    }
    object joystickDeadZoneRatio {
        var state: Float = 20f
    }
    object joystickControlCanLock {
        var state: Boolean = false
    }
}
