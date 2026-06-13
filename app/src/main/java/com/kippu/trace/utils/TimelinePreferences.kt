package com.kippu.trace.utils

import android.content.Context

enum class TimelineScaleMode { UNIFIED, DUAL }

object TimelinePreferences {
    private const val PREFS_NAME = "timeline_prefs"
    private const val KEY_SCALE_MODE = "scale_mode"

    fun getScaleMode(context: Context): TimelineScaleMode {
        val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        val name = prefs.getString(KEY_SCALE_MODE, TimelineScaleMode.UNIFIED.name)
            ?: TimelineScaleMode.UNIFIED.name
        return try {
            TimelineScaleMode.valueOf(name)
        } catch (_: IllegalArgumentException) {
            TimelineScaleMode.UNIFIED
        }
    }

    fun setScaleMode(context: Context, mode: TimelineScaleMode) {
        context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
            .edit()
            .putString(KEY_SCALE_MODE, mode.name)
            .apply()
    }

    fun scaleModeLabel(mode: TimelineScaleMode, context: Context): String = when (mode) {
        TimelineScaleMode.UNIFIED -> context.getString(com.kippu.trace.R.string.timeline_scale_unified)
        TimelineScaleMode.DUAL -> context.getString(com.kippu.trace.R.string.timeline_scale_dual)
    }
}
