package com.epai.oblender

import android.content.Context
import android.content.SharedPreferences

object VirtualPointerSettings {
    private const val PREFS_NAME = "obl_virtual_pointer"
    private const val KEY_STILLNESS_TIME = "stillness_time_ms"
    private const val KEY_STILLNESS_THRESHOLD = "stillness_threshold_px"
    private const val KEY_TAP_MAX_DIST = "tap_max_dist_px"
    private const val KEY_TAP_MAX_TIME = "tap_max_time_ms"
    private const val KEY_SENSITIVITY = "cursor_sensitivity"

    private var prefs: SharedPreferences? = null

    @JvmStatic
    fun init(context: Context) {
        prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
    }

    @JvmStatic
    fun getStillnessTimeMs(): Int = prefs?.getInt(KEY_STILLNESS_TIME, 500)
        ?.coerceIn(200, 1500) ?: 500

    @JvmStatic
    fun setStillnessTimeMs(value: Int) {
        prefs?.edit()?.putInt(KEY_STILLNESS_TIME, value.coerceIn(200, 1500))?.apply()
    }

    @JvmStatic
    fun getStillnessThresholdPx(): Int = prefs?.getInt(KEY_STILLNESS_THRESHOLD, 4)
        ?.coerceIn(1, 20) ?: 4

    @JvmStatic
    fun setStillnessThresholdPx(value: Int) {
        prefs?.edit()?.putInt(KEY_STILLNESS_THRESHOLD, value.coerceIn(1, 20))?.apply()
    }

    @JvmStatic
    fun getTapMaxDistPx(): Int = prefs?.getInt(KEY_TAP_MAX_DIST, 16)
        ?.coerceIn(4, 40) ?: 16

    @JvmStatic
    fun setTapMaxDistPx(value: Int) {
        prefs?.edit()?.putInt(KEY_TAP_MAX_DIST, value.coerceIn(4, 40))?.apply()
    }

    @JvmStatic
    fun getTapMaxTimeMs(): Int = prefs?.getInt(KEY_TAP_MAX_TIME, 300)
        ?.coerceIn(100, 600) ?: 300

    @JvmStatic
    fun setTapMaxTimeMs(value: Int) {
        prefs?.edit()?.putInt(KEY_TAP_MAX_TIME, value.coerceIn(100, 600))?.apply()
    }

    @JvmStatic
    fun getSensitivity(): Float = prefs?.getFloat(KEY_SENSITIVITY, 1.0f)
        ?.coerceIn(0.25f, 3.0f) ?: 1.0f

    @JvmStatic
    fun setSensitivity(value: Float) {
        prefs?.edit()?.putFloat(KEY_SENSITIVITY, value.coerceIn(0.25f, 3.0f))?.apply()
    }
}
