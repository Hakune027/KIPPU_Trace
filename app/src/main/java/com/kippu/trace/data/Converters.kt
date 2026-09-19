package com.kippu.trace.data

import androidx.room.TypeConverter
import com.kippu.trace.model.AnniversaryType
import com.kippu.trace.model.DisplayMode
import com.kippu.trace.model.RepeatMode

class Converters {
    @TypeConverter
    fun fromRepeatMode(mode: RepeatMode): String = mode.name

    @TypeConverter
    fun toRepeatMode(mode: String): RepeatMode = RepeatMode.valueOf(mode)

    @TypeConverter
    fun fromDisplayMode(mode: DisplayMode): String {
        return mode.name
    }

    @TypeConverter
    fun toDisplayMode(mode: String): DisplayMode {
        return DisplayMode.valueOf(mode)
    }

    @TypeConverter
    fun fromAnniversaryType(type: AnniversaryType): String {
        return type.name
    }

    @TypeConverter
    fun toAnniversaryType(type: String): AnniversaryType {
        return AnniversaryType.valueOf(type)
    }
}
