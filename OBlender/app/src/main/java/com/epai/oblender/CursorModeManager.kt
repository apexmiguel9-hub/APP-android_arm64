package com.epai.oblender

import android.content.Context
import android.content.SharedPreferences

const val CURSOR_MODE_TOUCH = 0
const val CURSOR_MODE_VIRTUAL = 1
const val CURSOR_MODE_PRECISION = 2

object CursorModeManager {
    private const val PREFS_NAME = "obl_cursor"
    private const val KEY_MODE = "mode"

    private var prefs: SharedPreferences? = null

    @JvmStatic
    fun init(context: Context) {
        prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
    }

    @JvmStatic
    fun getMode(): Int = prefs?.getInt(KEY_MODE, CURSOR_MODE_TOUCH) ?: CURSOR_MODE_TOUCH

    @JvmStatic
    fun setMode(mode: Int) {
        prefs?.edit()?.putInt(KEY_MODE, mode)?.apply()
    }
}
