package com.kippu.trace.ui.components

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.animateContentSize
import androidx.compose.animation.expandVertically
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.shrinkVertically
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.background
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import com.kippu.trace.R
import com.kippu.trace.model.AnniversaryType
import com.kippu.trace.model.DateEvent
import com.kippu.trace.model.DisplayMode
import com.kippu.trace.model.RepeatMode

@Stable
class AnniversarySettingsState(event: DateEvent? = null) {
    var type by mutableStateOf(event?.anniversaryType ?: AnniversaryType.NONE)
    private var lastType by mutableStateOf(
        event?.anniversaryType?.takeIf { it != AnniversaryType.NONE }
            ?: AnniversaryType.CUSTOM_DAYS,
    )
    var customDays by mutableStateOf((event?.customDays ?: 100).toString())
    var year by mutableStateOf(event?.showYear ?: true)
    var month by mutableStateOf(event?.showMonth ?: true)
    var week by mutableStateOf(event?.showWeek ?: true)
    var message by mutableStateOf(event?.anniversaryMessage ?: "")
    var repeat by mutableStateOf(event?.repeatMode ?: RepeatMode.NONE)
    private var lastRepeat by mutableStateOf(
        event?.repeatMode?.takeIf { it != RepeatMode.NONE } ?: RepeatMode.YEARLY,
    )
    var repeatInterval by mutableStateOf((event?.repeatInterval ?: 1).toString())
    var repeatDays by mutableStateOf((event?.repeatCustomDays ?: 100).toString())

    val visibleAnniversaryType: AnniversaryType
        get() = type.takeIf { it != AnniversaryType.NONE } ?: lastType

    val visibleRepeatMode: RepeatMode
        get() = repeat.takeIf { it != RepeatMode.NONE } ?: lastRepeat

    fun setAnniversaryEnabled(enabled: Boolean) {
        if (enabled) {
            type = lastType
        } else {
            if (type != AnniversaryType.NONE) lastType = type
            type = AnniversaryType.NONE
        }
    }

    fun selectAnniversaryType(selected: AnniversaryType) {
        if (selected == AnniversaryType.NONE) return
        lastType = selected
        type = selected
    }

    fun setRepeatEnabled(enabled: Boolean) {
        if (enabled) {
            repeat = lastRepeat
        } else {
            if (repeat != RepeatMode.NONE) lastRepeat = repeat
            repeat = RepeatMode.NONE
        }
    }

    fun selectRepeatMode(selected: RepeatMode) {
        if (selected == RepeatMode.NONE) return
        lastRepeat = selected
        repeat = selected
    }

    fun valid(mode: DisplayMode): Boolean = when {
        mode == DisplayMode.COUNT_DOWN && repeat == RepeatMode.CUSTOM_DAYS -> (repeatDays.toIntOrNull() ?: 0) > 0
        mode == DisplayMode.COUNT_DOWN && repeat != RepeatMode.NONE -> (repeatInterval.toIntOrNull() ?: 0) > 0
        mode == DisplayMode.ACCUMULATE && type == AnniversaryType.CUSTOM_DAYS -> (customDays.toIntOrNull() ?: 0) > 0
        else -> true
    }

    fun applyTo(event: DateEvent): DateEvent {
        val interval = repeatInterval.toIntOrNull() ?: 1
        val customRepeatDays = repeatDays.toIntOrNull() ?: 100
        val keepsRepeatAnchor = event.repeatMode == repeat &&
            event.repeatInterval == interval &&
            event.repeatCustomDays == customRepeatDays

        return event.copy(
            anniversaryType = type,
            customDays = customDays.toIntOrNull() ?: 100,
            showYear = year,
            showMonth = month,
            showWeek = week,
            anniversaryMessage = message.trim(),
            repeatMode = repeat,
            repeatInterval = interval,
            repeatCustomDays = customRepeatDays,
            repeatAnchorDate = event.repeatAnchorDate.takeIf { keepsRepeatAnchor },
        )
    }
}

@OptIn(
    ExperimentalMaterial3Api::class,
)
@Composable
fun AnniversarySettings(
    state: AnniversarySettingsState,
    mode: DisplayMode,
    modifier: Modifier = Modifier,
) {
    if (mode == DisplayMode.ACCUMULATE) {
        Column(
            modifier = modifier.fillMaxWidth(),
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text(
                    text = stringResource(R.string.anniversary_section),
                    style = MaterialTheme.typography.titleSmall,
                )
                Switch(
                    checked = state.type != AnniversaryType.NONE,
                    onCheckedChange = state::setAnniversaryEnabled,
                    modifier = Modifier.testTag("anniversary_enabled_switch"),
                )
            }

            AnimatedVisibility(
                visible = state.type != AnniversaryType.NONE,
                enter = expandVertically(
                    expandFrom = Alignment.Top,
                    animationSpec = spring(stiffness = Spring.StiffnessMediumLow),
                ) + fadeIn(animationSpec = tween(180)),
                exit = shrinkVertically(
                    shrinkTowards = Alignment.Top,
                    animationSpec = spring(stiffness = Spring.StiffnessMediumLow),
                ) + fadeOut(animationSpec = tween(140)),
            ) {
                val visibleType = state.visibleAnniversaryType
                Column {
                    Spacer(modifier = Modifier.height(12.dp))
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .animateContentSize(
                                animationSpec = spring(stiffness = Spring.StiffnessMediumLow),
                            )
                            .clip(RoundedCornerShape(20.dp))
                            .background(MaterialTheme.colorScheme.onSurface.copy(alpha = 0.035f))
                            .padding(16.dp),
                    ) {
                        Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                            AnniversaryTypeSwitcher(
                                selected = visibleType,
                                onSelected = state::selectAnniversaryType,
                            )
                            if (visibleType == AnniversaryType.CUSTOM_DAYS) {
                                DaysInput(state.customDays, { state.customDays = it })
                            }
                            if (visibleType == AnniversaryType.CALENDAR) {
                                UnitSwitch(R.string.anniversary_unit_year, state.year) { state.year = it }
                                UnitSwitch(R.string.anniversary_unit_month, state.month) { state.month = it }
                                UnitSwitch(R.string.anniversary_unit_week, state.week) { state.week = it }
                            }
                            OutlinedTextField(
                                value = state.message,
                                onValueChange = { state.message = it.take(120) },
                                label = { Text(stringResource(R.string.anniversary_message)) },
                                shape = RoundedCornerShape(16.dp),
                                modifier = Modifier.fillMaxWidth(),
                                maxLines = 3,
                            )
                        }
                    }
                }
            }
        }
    } else {
        Column(
            modifier = modifier.fillMaxWidth(),
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text(
                    text = stringResource(R.string.repeat_section),
                    style = MaterialTheme.typography.titleSmall,
                )
                Switch(
                    checked = state.repeat != RepeatMode.NONE,
                    onCheckedChange = state::setRepeatEnabled,
                    modifier = Modifier.testTag("repeat_enabled_switch"),
                )
            }

            AnimatedVisibility(
                visible = state.repeat != RepeatMode.NONE,
                enter = expandVertically(
                    expandFrom = Alignment.Top,
                    animationSpec = spring(stiffness = Spring.StiffnessMediumLow),
                ) + fadeIn(animationSpec = tween(180)),
                exit = shrinkVertically(
                    shrinkTowards = Alignment.Top,
                    animationSpec = spring(stiffness = Spring.StiffnessMediumLow),
                ) + fadeOut(animationSpec = tween(140)),
            ) {
                val visibleRepeat = state.visibleRepeatMode
                Column {
                    Spacer(modifier = Modifier.height(12.dp))
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .animateContentSize(
                                animationSpec = spring(stiffness = Spring.StiffnessMediumLow),
                            )
                            .clip(RoundedCornerShape(20.dp))
                            .background(MaterialTheme.colorScheme.onSurface.copy(alpha = 0.035f))
                            .padding(16.dp),
                    ) {
                        Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                        RepeatModeSelector(
                                selected = visibleRepeat,
                                onSelected = state::selectRepeatMode,
                            )
                            when (visibleRepeat) {
                                RepeatMode.YEARLY -> IntervalInput(state.repeatInterval, { state.repeatInterval = it }, R.string.anniversary_unit_year)
                                RepeatMode.MONTHLY -> IntervalInput(state.repeatInterval, { state.repeatInterval = it }, R.string.anniversary_unit_month)
                                RepeatMode.WEEKLY -> IntervalInput(state.repeatInterval, { state.repeatInterval = it }, R.string.anniversary_unit_week)
                                RepeatMode.CUSTOM_DAYS -> IntervalInput(state.repeatDays, { state.repeatDays = it }, R.string.day_unit)
                                RepeatMode.NONE -> Unit
                            }
                            Text(stringResource(R.string.repeat_hint), style = MaterialTheme.typography.bodySmall)
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun AnniversaryTypeSwitcher(
    selected: AnniversaryType,
    onSelected: (AnniversaryType) -> Unit,
) {
    SlidingSegmentedControl(
        options = listOf(
            SlidingSegmentOption(stringResource(R.string.anniversary_days_tab)),
            SlidingSegmentOption(stringResource(R.string.anniversary_preset_tab)),
        ),
        selectedIndex = if (selected == AnniversaryType.CUSTOM_DAYS) 0 else 1,
        onSelected = { index ->
            onSelected(
                if (index == 0) AnniversaryType.CUSTOM_DAYS else AnniversaryType.CALENDAR,
            )
        },
    )
}

@Composable
private fun IntervalInput(value: String, onChange: (String) -> Unit, unit: Int) {
    PositiveNumberInput(
        value = value,
        onChange = onChange,
        label = R.string.repeat_interval_label,
        maxLength = 6,
        suffix = { Text(stringResource(unit)) },
    )
}

@Composable
private fun RepeatModeSelector(selected: RepeatMode, onSelected: (RepeatMode) -> Unit) {
    val options = listOf(
        RepeatMode.YEARLY to R.string.anniversary_unit_year,
        RepeatMode.MONTHLY to R.string.anniversary_unit_month,
        RepeatMode.WEEKLY to R.string.anniversary_unit_week,
        RepeatMode.CUSTOM_DAYS to R.string.day_unit,
    )
    SlidingSegmentedControl(
        options = options.map { (_, label) -> SlidingSegmentOption(stringResource(label)) },
        selectedIndex = options.indexOfFirst { (repeat) -> repeat == selected }.coerceAtLeast(0),
        onSelected = { index -> onSelected(options[index].first) },
    )
}

@Composable
private fun DaysInput(value: String, onChange: (String) -> Unit) {
    PositiveNumberInput(
        value = value,
        onChange = onChange,
        label = R.string.anniversary_custom_days_label,
        maxLength = 10,
    )
}

@Composable
private fun PositiveNumberInput(
    value: String,
    onChange: (String) -> Unit,
    label: Int,
    maxLength: Int,
    suffix: @Composable (() -> Unit)? = null,
) {
    val invalid = (value.toIntOrNull() ?: 0) <= 0
    OutlinedTextField(
        value = value,
        onValueChange = { input ->
            onChange(input.filter { char -> char in '0'..'9' }.take(maxLength))
        },
        label = { Text(stringResource(label)) },
        suffix = suffix,
        singleLine = true,
        isError = invalid,
        supportingText = {
            if (invalid) Text(stringResource(R.string.positive_days_required))
        },
        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
        shape = RoundedCornerShape(16.dp),
        modifier = Modifier.fillMaxWidth(),
    )
}

@Composable
private fun UnitSwitch(label: Int, checked: Boolean, onChange: (Boolean) -> Unit) {
    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
        Text(stringResource(label), style = MaterialTheme.typography.bodyMedium)
        Switch(checked = checked, onCheckedChange = onChange)
    }
}
