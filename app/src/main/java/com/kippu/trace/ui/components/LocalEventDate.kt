package com.kippu.trace.ui.components

import androidx.compose.runtime.compositionLocalOf
import java.time.LocalDate

/** Shared calendar day so cards refresh together after midnight or returning to the app. */
val LocalEventDate = compositionLocalOf { LocalDate.now() }
