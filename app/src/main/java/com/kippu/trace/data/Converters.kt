package com.kippu.trace.data

import androidx.room.TypeConverter
import com.kippu.trace.model.AnniversaryType
import com.kippu.trace.model.DisplayMode

class Converters {
    @TypeConverter
    fun fromRepeatMode(mode: com.kippu.trace.model.RepeatMode): String = mode.name

    @TypeConverter
    fun toRepeatMode(mode: String): com.kippu.trace.model.RepeatMode = com.kippu.trace.model.RepeatMode.valueOf(mode)

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
