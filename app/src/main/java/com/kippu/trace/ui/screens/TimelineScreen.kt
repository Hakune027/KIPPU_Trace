package com.kippu.trace.ui.screens

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.mutableStateMapOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.layout.positionInRoot
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.kippu.trace.R
import com.kippu.trace.model.DateEvent
import com.kippu.trace.ui.components.TimelineEventCard
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId
import kotlin.math.absoluteValue
import kotlin.math.sqrt

/**
 * 时间轴页面
 *
 * 卡片左右交替排列，锚点通过横向连接杆连接到卡片边缘。
 * Catmull-Rom 样条动态穿过所有锚点，随滚动实时变化。
 * — 锚点通过 onGloballyPositioned 与连接杆末端精确绑定，消除坐标空间不一致
 * — 卡片间距根据事件时间间隔（天）动态计算，时间轴可自由滚动
 * — 卡片统一基础尺寸，高度根据文字量动态伸缩
 * — 连接杆由 Canvas 层绘制，与曲线风格统一
 */
@Composable
fun TimelineScreen(
    events: List<DateEvent>,
    onEventClick: (DateEvent) -> Unit,
) {
    val sortedEvents = remember(events) { events.sortedBy { it.targetDate } }
    val todayEpochDay = LocalDate.now(ZoneId.systemDefault()).toEpochDay()
    val timelineItems = remember(sortedEvents, todayEpochDay) {
        (sortedEvents.map { event ->
            TimelineItem.Event(event, eventEpochDay(event.targetDate))
        } + TimelineItem.Now(todayEpochDay))
            .sortedWith(
                compareBy<TimelineItem> { it.epochDay }
                    .thenBy { if (it is TimelineItem.Now) 1 else 0 }
            )
    }
    // ── 基于时间坐标间隔（天）计算项目间距，"现在"也参与坐标计算 ──
    val dayGaps = remember(timelineItems) {
        timelineItems.zipWithNext { a, b ->
            (b.epochDay - a.epochDay).absoluteValue.toFloat()
        }
    }
    val nowItemIndex = remember(timelineItems) {
        timelineItems.indexOfFirst { it is TimelineItem.Now }
    }
    val maxPastDayGap = remember(dayGaps, nowItemIndex) {
        dayGaps.take(nowItemIndex.coerceAtLeast(0)).maxOrNull()?.coerceAtLeast(1f) ?: 1f
    }
    val maxFutureDayGap = remember(dayGaps, nowItemIndex) {
        dayGaps.drop(nowItemIndex.coerceAtLeast(0)).maxOrNull()?.coerceAtLeast(1f) ?: 1f
    }

    // ── 锚点位置（一次性捕获，Column 无回收，位置永不失效）──
    val anchorPositions = remember { mutableStateMapOf<Int, Offset>() }
    // 内容区域在 root 坐标系中的偏移，用于把 onGloballyPositioned 坐标转为内容本地坐标
    var contentRootOffset by remember { mutableStateOf(Offset.Zero) }

    val bgColor = MaterialTheme.colorScheme.background

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(bgColor)
    ) {
        if (sortedEvents.isEmpty()) {
            Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    Text(
                        stringResource(R.string.timeline_subtitle),
                        style = MaterialTheme.typography.bodyLarge.copy(
                            color = MaterialTheme.colorScheme.onBackground.copy(alpha = 0.35f),
                            textAlign = TextAlign.Center,
                        ),
                        modifier = Modifier.padding(horizontal = 48.dp),
                    )
                    Spacer(Modifier.height(8.dp))
                    Text(
                        stringResource(R.string.timeline_empty_hint),
                        style = MaterialTheme.typography.bodyMedium.copy(
                            color = MaterialTheme.colorScheme.onBackground.copy(alpha = 0.22f),
                            textAlign = TextAlign.Center,
                        ),
                    )
                }
            }
        } else {
            val curveColor = MaterialTheme.colorScheme.outline
            val accentColor = MaterialTheme.colorScheme.primary
            val surfaceColor = MaterialTheme.colorScheme.surface

            // ════════════════════════════════════════════════
            // 静态时间轴：所有锚点一次性捕获，曲线整条绘制后随内容滚动
            // ════════════════════════════════════════════════
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .verticalScroll(rememberScrollState()),
            ) {
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .onGloballyPositioned { coords ->
                            contentRootOffset = coords.positionInRoot()
                        }
                ) {
                    // 层1: 曲线 + 锚点 — 铺满内容高度
                    Canvas(modifier = Modifier.matchParentSize()) {
                        // 从 map 提取已记录的锚点，按 index 排序
                        val anchors = anchorPositions
                            .toList()
                            .sortedBy { it.first }
                            .map { (idx, pos) -> Anchor(idx, pos.x, pos.y, isLeft = eventSideIndex(timelineItems, idx) % 2 == 0) }

                        if (anchors.isEmpty()) return@Canvas

                        val rodLen = 24.dp.toPx()
                        val nodeRadius = 8.dp.toPx()

                        if (anchors.size == 1) {
                            val a = anchors.first()
                            val center = Offset(a.x, a.y)
                            drawLine(accentColor.copy(alpha = 0.08f), Offset(a.x, 0f), Offset(a.x, size.height), strokeWidth = 7.dp.toPx())
                            drawLine(curveColor.copy(alpha = 0.16f), Offset(a.x, 0f), Offset(a.x, size.height), strokeWidth = 1.4.dp.toPx())
                            val isLeft = a.isLeft
                            val sX = if (isLeft) a.x - rodLen else a.x + nodeRadius
                            val eX = if (isLeft) a.x - nodeRadius else a.x + rodLen
                            drawLine(accentColor.copy(alpha = 0.08f), Offset(sX, a.y), Offset(eX, a.y), strokeWidth = 7.dp.toPx())
                            drawLine(curveColor.copy(alpha = 0.20f), Offset(sX, a.y + 0.5f), Offset(eX, a.y + 0.5f), strokeWidth = 2.4.dp.toPx())
                            drawLine(accentColor.copy(alpha = 0.32f), Offset(sX, a.y), Offset(eX, a.y), strokeWidth = 1.dp.toPx())
                            drawCircle(Brush.radialGradient(listOf(accentColor.copy(alpha = 0.18f), accentColor.copy(alpha = 0.07f), Color.Transparent), center = center, radius = 18.dp.toPx()), radius = 18.dp.toPx(), center = center)
                            drawCircle(color = surfaceColor.copy(alpha = 0.96f), radius = nodeRadius, center = center)
                            drawCircle(color = accentColor.copy(alpha = 0.55f), radius = nodeRadius, center = center, style = Stroke(1.6.dp.toPx()))
                            drawCircle(color = accentColor.copy(alpha = 0.84f), radius = 4.4.dp.toPx(), center = center)
                            drawCircle(color = Color.White.copy(alpha = 0.82f), radius = 1.35.dp.toPx(), center = Offset(a.x - 1.3.dp.toPx(), a.y - 1.3.dp.toPx()))
                            return@Canvas
                        }

                        val vpH = size.height

                        // 弦长参数化 Catmull-Rom → 三次贝塞尔
                        // 近距离节点自动收紧曲线、远距离节点保持舒展，消除近节点处的大角度折弯
                        val path = Path()
                        path.moveTo(anchors.first().x, anchors.first().y)
                        for (i in 0 until anchors.size - 1) {
                            val p0 = anchors.getOrElse(i - 1) { anchors.first() }
                            val p1 = anchors[i]
                            val p2 = anchors[i + 1]
                            val p3 = anchors.getOrElse(i + 2) { anchors.last() }

                            // 弦长（Euclidean），控制点缩放基于实际距离而非均匀间隔
                            fun dist(a: Anchor, b: Anchor): Float {
                                val dx = a.x - b.x; val dy = a.y - b.y
                                return sqrt(dx * dx + dy * dy).coerceAtLeast(1f)
                            }
                            val d01 = dist(p0, p1)
                            val d12 = dist(p1, p2)
                            val d23 = dist(p2, p3)

                            val t0 = 0f
                            val t1 = d01
                            val t2 = d01 + d12
                            val t3 = d01 + d12 + d23

                            val s1 = (t2 - t1) / (3f * (t2 - t0).coerceAtLeast(1f))
                            val s2 = (t2 - t1) / (3f * (t3 - t1).coerceAtLeast(1f))

                            val cp1x = p1.x + (p2.x - p0.x) * s1
                            val cp1y = p1.y + (p2.y - p0.y) * s1
                            val cp2x = p2.x - (p3.x - p1.x) * s2
                            val cp2y = p2.y - (p3.y - p1.y) * s2
                            path.cubicTo(cp1x, cp1y, cp2x, cp2y, p2.x, p2.y)
                        }

                        // ── 延伸至画布两端 ──
                        val firstDirY = anchors[1].y - anchors[0].y
                        val firstExtX: Float
                        val firstExtY: Float
                        if (firstDirY != 0f && anchors[0].y > 0f) {
                            firstExtY = 0f
                            firstExtX = anchors[0].x + (anchors[1].x - anchors[0].x) * (anchors[0].y / firstDirY)
                        } else {
                            firstExtX = anchors[0].x; firstExtY = anchors[0].y
                        }
                        val n = anchors.lastIndex
                        val lastDirY = anchors[n].y - anchors[n - 1].y
                        val lastExtX: Float
                        val lastExtY: Float
                        if (lastDirY != 0f && anchors[n].y < vpH) {
                            lastExtY = vpH
                            lastExtX = anchors[n].x + (anchors[n].x - anchors[n - 1].x) * ((vpH - anchors[n].y) / lastDirY)
                        } else {
                            lastExtX = anchors[n].x; lastExtY = anchors[n].y
                        }

                        val extPath = Path()
                        extPath.moveTo(firstExtX, firstExtY)
                        extPath.lineTo(anchors.first().x, anchors.first().y)
                        extPath.addPath(path)
                        extPath.lineTo(lastExtX, lastExtY)

                        // 泛光层
                        listOf(20.dp.toPx() to 0.018f, 12.dp.toPx() to 0.030f, 6.dp.toPx() to 0.055f)
                            .forEach { (sw, a) -> drawPath(path, accentColor.copy(alpha = a), style = Stroke(sw)) }
                        drawPath(path, curveColor.copy(alpha = 0.26f), style = Stroke(3.2.dp.toPx()))
                        drawPath(path, accentColor.copy(alpha = 0.58f), style = Stroke(1.35.dp.toPx()))
                        for (layer in listOf(9.dp.toPx(), 5.dp.toPx()))
                            drawPath(extPath, accentColor.copy(alpha = 0.018f), style = Stroke(layer))
                        drawPath(extPath, curveColor.copy(alpha = 0.12f), style = Stroke(1.3.dp.toPx()))

                        // 连接杆
                        anchors.forEach { a ->
                            val isLeft = a.isLeft
                            val startX = if (isLeft) a.x - rodLen else a.x + nodeRadius
                            val endX = if (isLeft) a.x - nodeRadius else a.x + rodLen
                            drawLine(accentColor.copy(alpha = 0.08f), Offset(startX, a.y), Offset(endX, a.y), strokeWidth = 7.dp.toPx())
                            drawLine(curveColor.copy(alpha = 0.20f), Offset(startX, a.y + 0.5f), Offset(endX, a.y + 0.5f), strokeWidth = 2.4.dp.toPx())
                            drawLine(accentColor.copy(alpha = 0.32f), Offset(startX, a.y), Offset(endX, a.y), strokeWidth = 1.dp.toPx())
                        }

                        // 锚点节点
                        anchors.forEach { a ->
                            val center = Offset(a.x, a.y)
                            val glowRadius = 18.dp.toPx()
                            drawCircle(Brush.radialGradient(listOf(accentColor.copy(alpha = 0.18f), accentColor.copy(alpha = 0.07f), Color.Transparent), center = center, radius = glowRadius), radius = glowRadius, center = center)
                            drawCircle(color = surfaceColor.copy(alpha = 0.96f), radius = nodeRadius, center = center)
                            drawCircle(color = accentColor.copy(alpha = 0.55f), radius = nodeRadius, center = center, style = Stroke(1.6.dp.toPx()))
                            drawCircle(color = accentColor.copy(alpha = 0.84f), radius = 4.4.dp.toPx(), center = center)
                            drawCircle(color = Color.White.copy(alpha = 0.82f), radius = 1.35.dp.toPx(), center = Offset(a.x - 1.3.dp.toPx(), a.y - 1.3.dp.toPx()))
                        }
                    }

                    // 层2: 卡片列表
                    Column(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalAlignment = Alignment.CenterHorizontally,
                    ) {
                        // Motto
                        Text(
                            stringResource(R.string.timeline_subtitle),
                            style = MaterialTheme.typography.bodySmall.copy(
                                color = MaterialTheme.colorScheme.onBackground.copy(alpha = 0.3f),
                                textAlign = TextAlign.Center, fontSize = 12.sp,
                            ),
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(horizontal = 48.dp, vertical = 20.dp),
                        )

                        Spacer(Modifier.height(40.dp))

                        // 时间线 item
                        timelineItems.forEachIndexed { index, item ->
                            val isLeft = eventSideIndex(timelineItems, index) % 2 == 0

                            val timeGapSpacing = if (index > 0 && index - 1 < dayGaps.size) {
                                val gapIndex = index - 1
                                val maxGapForSide = if (gapIndex < nowItemIndex) maxPastDayGap else maxFutureDayGap
                                timelineGapSpacing(dayGaps[gapIndex], maxGapForSide)
                            } else {
                                28.dp
                            }

                            when (item) {
                                is TimelineItem.Now -> {
                                    TimelineNowDivider(
                                        modifier = Modifier.padding(top = timeGapSpacing, bottom = 8.dp),
                                    )
                                }
                                is TimelineItem.Event -> {
                                    val event = item.event
                                    Row(
                                        Modifier
                                            .fillMaxWidth()
                                            .padding(top = timeGapSpacing, bottom = 4.dp),
                                        verticalAlignment = Alignment.CenterVertically,
                                    ) {
                                        if (isLeft) {
                                            TimelineEventCard(event, { onEventClick(event) }, Modifier.weight(1f).padding(start = 8.dp, end = 0.dp))
                                            Spacer(Modifier.width(24.dp))
                                            TimelineAnchor(index, anchorPositions, contentRootOffset)
                                            Spacer(Modifier.width(14.dp))
                                            Spacer(Modifier.weight(1f).padding(horizontal = 8.dp))
                                        } else {
                                            Spacer(Modifier.weight(1f).padding(horizontal = 8.dp))
                                            Spacer(Modifier.width(14.dp))
                                            TimelineAnchor(index, anchorPositions, contentRootOffset)
                                            Spacer(Modifier.width(24.dp))
                                            TimelineEventCard(event, { onEventClick(event) }, Modifier.weight(1f).padding(start = 0.dp, end = 8.dp))
                                        }
                                    }
                                }
                            }
                        }

                        // 底部留白
                        Spacer(Modifier.height(100.dp))
                    }
                }
            }
        }

        // ── 顶部渐隐遮罩 ──
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .height(60.dp)
                .align(Alignment.TopCenter)
                .background(
                    Brush.verticalGradient(
                        colors = listOf(bgColor, Color.Transparent),
                    )
                )
        )

        // ── 底部渐隐遮罩 ──
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .height(80.dp)
                .align(Alignment.BottomCenter)
                .background(
                    Brush.verticalGradient(
                        colors = listOf(Color.Transparent, bgColor),
                    )
                )
        )
    }
}

@Composable
private fun TimelineNowDivider(modifier: Modifier = Modifier) {
    val color = MaterialTheme.colorScheme.primary

    Row(
        modifier = modifier
            .fillMaxWidth()
            .padding(horizontal = 22.dp, vertical = 10.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Canvas(
            modifier = Modifier
                .weight(1f)
                .height(1.dp),
        ) {
            drawLine(
                color.copy(alpha = 0.35f),
                Offset.Zero,
                Offset(size.width, 0f),
                strokeWidth = 1.4.dp.toPx(),
            )
        }
        Text(
            text = stringResource(R.string.timeline_now),
            style = MaterialTheme.typography.labelMedium.copy(
                color = color,
                fontWeight = FontWeight.SemiBold,
                letterSpacing = 0.sp,
            ),
            modifier = Modifier.padding(horizontal = 10.dp),
        )
        Canvas(
            modifier = Modifier
                .weight(1f)
                .height(1.dp),
        ) {
            drawLine(
                color.copy(alpha = 0.35f),
                Offset.Zero,
                Offset(size.width, 0f),
                strokeWidth = 1.4.dp.toPx(),
            )
        }
    }
}

private sealed class TimelineItem {
    abstract val epochDay: Long

    data class Event(
        val event: DateEvent,
        override val epochDay: Long,
    ) : TimelineItem()

    data class Now(
        override val epochDay: Long,
    ) : TimelineItem()
}

private fun eventEpochDay(targetDateMillis: Long): Long {
    return Instant.ofEpochMilli(targetDateMillis)
        .atZone(ZoneId.of("UTC"))
        .toLocalDate()
        .toEpochDay()
}

private fun eventSideIndex(items: List<TimelineItem>, itemIndex: Int): Int {
    return items.take(itemIndex).count { it is TimelineItem.Event }
}

private fun timelineGapSpacing(daysDiff: Float, maxDayGap: Float) = when {
    daysDiff <= 0f -> 18.dp
    else -> {
        val minSpacing = 32.dp
        val maxSpacing = 640.dp
        val ratio = (daysDiff / maxDayGap.coerceAtLeast(1f)).coerceIn(0f, 1f)
        // 线性插值（不用 ratio*ratio 压缩），让时间轴拉伸更充分
        minSpacing + (maxSpacing - minSpacing) * ratio
    }
}

/**
 * 锚点生命周期管理。
 *
 * 通过 onGloballyPositioned 将锚点坐标写入 anchorPositions。
 * 清理由外层 SideEffect + visibleEventIndexRange 统一负责，
 * 使用 ±10 索引缓冲保留边缘附近的锚点不被误删。
 */
@Composable
private fun TimelineAnchor(
    index: Int,
    anchorPositions: MutableMap<Int, Offset>,
    contentRootOffset: Offset,
) {
    Box(
        modifier = Modifier
            .size(1.dp)
            .onGloballyPositioned { coords ->
                val root = coords.positionInRoot()
                anchorPositions[index] = Offset(
                    root.x - contentRootOffset.x,
                    root.y - contentRootOffset.y,
                )
            }
    )
}

private data class Anchor(
    val index: Int,
    val x: Float,
    val y: Float,
    val isLeft: Boolean,
)