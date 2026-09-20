package com.kippu.trace.ui.components

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.requiredWidth
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.lazy.grid.rememberLazyGridState
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowLeft
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowRight
import androidx.compose.material.icons.filled.ArrowDropDown
import androidx.compose.material3.DatePicker
import androidx.compose.material3.DatePickerDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.rememberDatePickerState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.scale
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.TextStyle as ComposeTextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.TextUnit
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import com.kippu.trace.R
import com.kippu.trace.utils.AnniversaryUtils
import com.kippu.trace.utils.LunarDate
import com.kippu.trace.utils.LunarMonth
import com.kippu.trace.utils.LunarUtils
import java.time.DayOfWeek
import java.time.LocalDate
import java.time.format.TextStyle

data class DateSelection(val millis: Long, val isLunar: Boolean)

@Composable
fun AutoSizeSingleLineText(
    text: String,
    style: ComposeTextStyle,
    modifier: Modifier = Modifier,
    minFontSize: TextUnit = 12.sp,
) {
    BoxWithConstraints(modifier = modifier.fillMaxWidth()) {
        val availableWidth = maxWidth
        var fontSize by remember(text, availableWidth, style.fontSize) {
            mutableStateOf(style.fontSize)
        }

        Text(
            text = text,
            maxLines = 1,
            softWrap = false,
            overflow = TextOverflow.Ellipsis,
            style = style.copy(fontSize = fontSize),
            onTextLayout = { result ->
                if (result.didOverflowWidth && fontSize.value > minFontSize.value) {
                    fontSize = (fontSize.value - 1f)
                        .coerceAtLeast(minFontSize.value)
                        .sp
                }
            },
        )
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun DateSelectionDialog(
    initialDateMillis: Long,
    initialIsLunar: Boolean,
    onConfirm: (DateSelection) -> Unit,
    onDismiss: () -> Unit,
) {
    val solarState = rememberDatePickerState(initialSelectedDateMillis = initialDateMillis)
    val initialLunar = remember(initialDateMillis) { LunarUtils.fromMillis(initialDateMillis) }
    var isLunar by remember { mutableStateOf(initialIsLunar) }
    var lunarYear by remember { mutableIntStateOf(initialLunar.year.coerceIn(LunarUtils.MIN_YEAR, LunarUtils.MAX_YEAR)) }
    var lunarMonth by remember { mutableStateOf(LunarMonth(initialLunar.month, initialLunar.isLeapMonth)) }
    var lunarDay by remember { mutableIntStateOf(initialLunar.day) }

    fun selectCalendar(lunar: Boolean) {
        if (lunar == isLunar) return
        if (lunar) {
            val converted = LunarUtils.fromMillis(solarState.selectedDateMillis ?: initialDateMillis)
            lunarYear = converted.year.coerceIn(LunarUtils.MIN_YEAR, LunarUtils.MAX_YEAR)
            lunarMonth = LunarMonth(converted.month, converted.isLeapMonth)
            lunarDay = converted.day
        } else {
            solarState.selectedDateMillis = LunarUtils.toMillis(
                LunarDate(lunarYear, lunarMonth.month, lunarDay, lunarMonth.isLeap)
            )
        }
        isLunar = lunar
    }

    Dialog(
        onDismissRequest = onDismiss,
        properties = DialogProperties(usePlatformDefaultWidth = false),
    ) {
        Surface(
            shape = RoundedCornerShape(28.dp),
            color = MaterialTheme.colorScheme.surface,
            tonalElevation = 0.dp,
            modifier = Modifier
                .widthIn(min = 320.dp, max = 480.dp)
                .fillMaxWidth(0.85f)
                .heightIn(max = 720.dp)
                .verticalScroll(rememberScrollState()),
        ) {
            Column(
                modifier = Modifier.padding(top = 20.dp, bottom = 16.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
            ) {
                Text(
                    text = stringResource(R.string.select_date),
                    style = MaterialTheme.typography.titleLarge.copy(fontWeight = FontWeight.Bold),
                    modifier = Modifier
                        .align(Alignment.Start)
                        .padding(horizontal = 20.dp),
                )

                SlidingSegmentedControl(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 20.dp, vertical = 12.dp),
                    options = listOf(
                        SlidingSegmentOption(stringResource(R.string.solar_calendar)),
                        SlidingSegmentOption(stringResource(R.string.lunar_calendar)),
                    ),
                    selectedIndex = if (isLunar) 1 else 0,
                    onSelected = { selectCalendar(it == 1) },
                )

                if (isLunar) {
                    LunarDatePicker(
                        year = lunarYear,
                        month = lunarMonth,
                        day = lunarDay,
                        onYearChange = { lunarYear = it },
                        onMonthChange = { lunarMonth = it },
                        onDayChange = { lunarDay = it },
                    )
                } else {
                    BoxWithConstraints(modifier = Modifier.fillMaxWidth()) {
                        val scale = (maxWidth / 360.dp).coerceIn(0.88f, 1.1f)
                        Box(modifier = Modifier.fillMaxWidth(), contentAlignment = Alignment.Center) {
                            DatePicker(
                                state = solarState,
                                title = null,
                                headline = null,
                                showModeToggle = false,
                                colors = DatePickerDefaults.colors(
                                    containerColor = MaterialTheme.colorScheme.surface,
                                    dividerColor = Color.Transparent,
                                ),
                                modifier = Modifier.requiredWidth(360.dp).scale(scale),
                            )
                        }
                    }
                }

                Spacer(modifier = Modifier.height(8.dp))
                Row(
                    modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp),
                    horizontalArrangement = Arrangement.End,
                ) {
                    TextButton(onClick = onDismiss) {
                        Text(stringResource(R.string.cancel))
                    }
                    TextButton(onClick = {
                        val millis = if (isLunar) {
                            LunarUtils.toMillis(LunarDate(lunarYear, lunarMonth.month, lunarDay, lunarMonth.isLeap))
                        } else {
                            solarState.selectedDateMillis ?: initialDateMillis
                        }
                        onConfirm(DateSelection(millis, isLunar))
                    }) {
                        Text(stringResource(R.string.confirm))
                    }
                }
            }
        }
    }
}

@Composable
private fun LunarDatePicker(
    year: Int,
    month: LunarMonth,
    day: Int,
    onYearChange: (Int) -> Unit,
    onMonthChange: (LunarMonth) -> Unit,
    onDayChange: (Int) -> Unit,
) {
    val months = remember(year) { LunarUtils.monthsOf(year) }
    val safeMonth = month.takeIf { it in months } ?: LunarMonth(month.month)
    val maxDay = remember(year, safeMonth) { LunarUtils.daysInMonth(year, safeMonth) }
    val safeDay = day.coerceIn(1, maxDay)
    val currentMonthIndex = months.indexOf(safeMonth).coerceAtLeast(0)
    var showYearPicker by remember { mutableStateOf(false) }

    LaunchedEffect(safeMonth, safeDay) {
        if (safeMonth != month) onMonthChange(safeMonth)
        if (safeDay != day) onDayChange(safeDay)
    }

    fun moveMonth(direction: Int) {
        val nextIndex = currentMonthIndex + direction
        when {
            nextIndex in months.indices -> onMonthChange(months[nextIndex])
            direction < 0 && year > LunarUtils.MIN_YEAR -> {
                val previousYear = year - 1
                onYearChange(previousYear)
                onMonthChange(LunarUtils.monthsOf(previousYear).last())
            }
            direction > 0 && year < LunarUtils.MAX_YEAR -> {
                onYearChange(year + 1)
                onMonthChange(LunarUtils.monthsOf(year + 1).first())
            }
        }
    }

    Column(
        modifier = Modifier.fillMaxWidth().padding(horizontal = 20.dp),
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            TextButton(onClick = { showYearPicker = !showYearPicker }) {
                val monthText = if (safeMonth.isLeap) {
                    stringResource(R.string.lunar_leap_month, safeMonth.month)
                } else {
                    safeMonth.month.toString()
                }
                Text(
                    text = stringResource(R.string.lunar_month_header, year, monthText),
                    style = MaterialTheme.typography.titleSmall.copy(fontWeight = FontWeight.Bold),
                )
                Icon(Icons.Default.ArrowDropDown, contentDescription = null)
            }
            Spacer(modifier = Modifier.weight(1f))
            IconButton(
                enabled = currentMonthIndex > 0 || year > LunarUtils.MIN_YEAR,
                onClick = { moveMonth(-1) },
            ) {
                Icon(
                    Icons.AutoMirrored.Filled.KeyboardArrowLeft,
                    contentDescription = stringResource(R.string.previous_month),
                )
            }
            IconButton(
                enabled = currentMonthIndex < months.lastIndex || year < LunarUtils.MAX_YEAR,
                onClick = { moveMonth(1) },
            ) {
                Icon(
                    Icons.AutoMirrored.Filled.KeyboardArrowRight,
                    contentDescription = stringResource(R.string.next_month),
                )
            }
        }

        if (showYearPicker) {
            LunarYearPicker(
                selectedYear = year,
                onYearSelected = {
                    onYearChange(it)
                    showYearPicker = false
                },
            )
        } else {
            LunarMonthGrid(
                year = year,
                month = safeMonth,
                selectedDay = safeDay,
                onDaySelected = onDayChange,
            )
        }
    }
}

@Composable
private fun LunarMonthGrid(
    year: Int,
    month: LunarMonth,
    selectedDay: Int,
    onDaySelected: (Int) -> Unit,
) {
    val configuration = LocalConfiguration.current
    val locale = configuration.locales[0]
    val weekDays = remember(locale) {
        listOf(
            DayOfWeek.MONDAY,
            DayOfWeek.TUESDAY,
            DayOfWeek.WEDNESDAY,
            DayOfWeek.THURSDAY,
            DayOfWeek.FRIDAY,
            DayOfWeek.SATURDAY,
            DayOfWeek.SUNDAY,
        ).map { it.getDisplayName(TextStyle.NARROW_STANDALONE, locale) }
    }
    val maxDay = remember(year, month) { LunarUtils.daysInMonth(year, month) }
    val lunarToday = remember {
        LunarUtils.fromMillis(AnniversaryUtils.millis(LocalDate.now()))
    }
    val firstDayOffset = remember(year, month) {
        AnniversaryUtils.date(
            LunarUtils.toMillis(LunarDate(year, month.month, 1, month.isLeap))
        ).dayOfWeek.value - 1
    }

    Row(modifier = Modifier.fillMaxWidth()) {
        weekDays.forEach { label ->
            Box(
                modifier = Modifier.weight(1f).height(40.dp),
                contentAlignment = Alignment.Center,
            ) {
                Text(label, style = MaterialTheme.typography.bodyMedium)
            }
        }
    }

    repeat(6) { row ->
        Row(modifier = Modifier.fillMaxWidth()) {
            repeat(7) { column ->
                val day = row * 7 + column - firstDayOffset + 1
                Box(
                    modifier = Modifier.weight(1f).height(48.dp),
                    contentAlignment = Alignment.Center,
                ) {
                    if (day in 1..maxDay) {
                        val selected = day == selectedDay
                        val isToday = lunarToday.year == year &&
                            lunarToday.month == month.month &&
                            lunarToday.isLeapMonth == month.isLeap &&
                            lunarToday.day == day
                        Surface(
                            onClick = { onDaySelected(day) },
                            modifier = Modifier.size(40.dp),
                            shape = CircleShape,
                            color = if (selected) MaterialTheme.colorScheme.primary else Color.Transparent,
                            border = if (isToday && !selected) {
                                BorderStroke(1.dp, MaterialTheme.colorScheme.primary)
                            } else {
                                null
                            },
                        ) {
                            Box(contentAlignment = Alignment.Center) {
                                Text(
                                    text = day.toString(),
                                    color = if (selected) {
                                        MaterialTheme.colorScheme.onPrimary
                                    } else {
                                        if (isToday) MaterialTheme.colorScheme.primary
                                        else MaterialTheme.colorScheme.onSurface
                                    },
                                    style = MaterialTheme.typography.bodyLarge,
                                )
                            }
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun LunarYearPicker(
    selectedYear: Int,
    onYearSelected: (Int) -> Unit,
) {
    val years = remember { (LunarUtils.MIN_YEAR..LunarUtils.MAX_YEAR).toList() }
    val selectedIndex = years.indexOf(selectedYear).coerceAtLeast(0)
    val gridState = rememberLazyGridState(
        initialFirstVisibleItemIndex = (selectedIndex - 6).coerceAtLeast(0),
    )

    LazyVerticalGrid(
        columns = GridCells.Fixed(3),
        state = gridState,
        modifier = Modifier.fillMaxWidth().height(328.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp),
        horizontalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        items(years) { year ->
            val selected = year == selectedYear
            Surface(
                onClick = { onYearSelected(year) },
                shape = RoundedCornerShape(20.dp),
                color = if (selected) {
                    MaterialTheme.colorScheme.primary
                } else {
                    Color.Transparent
                },
            ) {
                Box(
                    modifier = Modifier.height(44.dp),
                    contentAlignment = Alignment.Center,
                ) {
                    Text(
                        text = year.toString(),
                        color = if (selected) {
                            MaterialTheme.colorScheme.onPrimary
                        } else {
                            MaterialTheme.colorScheme.onSurface
                        },
                    )
                }
            }
        }
    }
}
