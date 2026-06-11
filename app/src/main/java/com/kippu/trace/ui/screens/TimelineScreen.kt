package com.kippu.trace.ui.screens

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.slideInHorizontally
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material3.FloatingActionButton
import androidx.compose.material3.Icon
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
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.kippu.trace.R
import com.kippu.trace.model.DateEvent
import com.kippu.trace.ui.components.TimelineEventCard
import kotlin.math.abs

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
    onAddClick: () -> Unit,
) {
    val context = LocalContext.current
    val sortedEvents = remember(events) { events.sortedBy { it.targetDate } }
    val listState = rememberLazyListState()

    // ── 基于时间间隔（天）计算卡片间距 ──
    val dayGaps = remember(sortedEvents) {
        if (sortedEvents.size < 2) emptyList<Float>()
        else sortedEvents.zipWithNext { a, b ->
            abs(b.targetDate - a.targetDate) / (1000f * 60f * 60f * 24f)
        }
    }

    // ── 锚点精确定位：通过 onGloballyPositioned 绑定 ──
    val anchorPositions = remember { mutableStateMapOf<Int, Offset>() }
    var containerRootOffset by remember { mutableStateOf<Offset?>(null) }

    val bgColor = MaterialTheme.colorScheme.background

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(bgColor)
            .onGloballyPositioned { coords ->
                containerRootOffset = coords.positionInRoot()
            }
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
            // ════════════════════════════════════════════════
            // 层1: 曲线 + 锚点（坐标通过 onGloballyPositioned 精确匹配卡片）
            // ════════════════════════════════════════════════
            val curveColor = MaterialTheme.colorScheme.outline
            val rootOffset = containerRootOffset

            Canvas(modifier = Modifier.fillMaxSize()) {
                if (rootOffset == null) return@Canvas

                // 从 map 提取已记录的锚点，按 index 排序
                val anchors = anchorPositions
                    .toList()
                    .sortedBy { it.first }
                    .map { (idx, pos) -> Anchor(idx, pos.x, pos.y) }
                    .filter { it.y >= -size.height && it.y <= size.height * 2f }

                if (anchors.size < 2) return@Canvas

                val vpW = size.width
                val vpH = size.height

                // Catmull-Rom → 三次贝塞尔主路径
                val path = Path()
                path.moveTo(anchors.first().x, anchors.first().y)

                for (i in 0 until anchors.size - 1) {
                    val p0 = anchors.getOrElse(i - 1) { anchors.first() }
                    val p1 = anchors[i]
                    val p2 = anchors[i + 1]
                    val p3 = anchors.getOrElse(i + 2) { anchors.last() }

                    val cp1x = p1.x + (p2.x - p0.x) / 6f
                    val cp1y = p1.y + (p2.y - p0.y) / 6f
                    val cp2x = p2.x - (p3.x - p1.x) / 6f
                    val cp2y = p2.y - (p3.y - p1.y) / 6f

                    path.cubicTo(cp1x, cp1y, cp2x, cp2y, p2.x, p2.y)
                }

                // ── 延伸至视口边缘 ──
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

                // 泛光层（主曲线）
                for (layer in listOf(24f, 14f, 8f, 4f, 2f)) {
                    drawPath(path, curveColor.copy(alpha = 0.03f / (layer / 3f)),
                        style = Stroke(layer * density))
                }
                drawPath(path, curveColor.copy(alpha = 0.40f),
                    style = Stroke(1.8.dp.toPx()))

                // 延伸段（稍淡）
                drawPath(extPath, curveColor.copy(alpha = 0.15f),
                    style = Stroke(1.5.dp.toPx()))
                for (layer in listOf(10f, 5f)) {
                    drawPath(extPath, curveColor.copy(alpha = 0.015f),
                        style = Stroke(layer * density))
                }

                // ── 锚点五层视觉效果 ──
                anchors.forEach { a ->
                    val r = 22.dp.toPx()
                    drawCircle(
                        Brush.radialGradient(
                            colors = listOf(
                                curveColor.copy(alpha = 0.18f),
                                curveColor.copy(alpha = 0.05f),
                                Color.Transparent,
                            ),
                            center = Offset(a.x, a.y), radius = r,
                        ),
                        radius = r, center = Offset(a.x, a.y),
                    )
                    drawCircle(curveColor.copy(alpha = 0.50f), 8.dp.toPx(),
                        Offset(a.x, a.y), style = Stroke(2.2.dp.toPx()))
                    drawCircle(curveColor.copy(alpha = 0.65f), 4.dp.toPx(),
                        Offset(a.x, a.y))
                    drawCircle(Color.White.copy(alpha = 0.70f), 2.2.dp.toPx(),
                        Offset(a.x, a.y))
                    drawCircle(Color.White.copy(alpha = 0.9f), 1.dp.toPx(),
                        Offset(a.x, a.y))
                }

                // ── 连接杆：从锚点延伸到卡片边缘 ──
                val rodLen = 24.dp.toPx()
                anchors.forEach { a ->
                    val isLeft = a.index % 2 == 0
                    val startX = if (isLeft) a.x - rodLen else a.x
                    val endX   = if (isLeft) a.x else a.x + rodLen
                    // 阴影杆
                    drawLine(
                        curveColor.copy(alpha = 0.07f),
                        Offset(startX, a.y),
                        Offset(endX, a.y),
                        strokeWidth = 6.dp.toPx(),
                    )
                    // 实线杆
                    drawLine(
                        curveColor.copy(alpha = 0.25f),
                        Offset(startX, a.y + 0.5f),
                        Offset(endX, a.y + 0.5f),
                        strokeWidth = 1.8.dp.toPx(),
                    )
                }
            }

            // ════════════════════════════════════════════════
            // 层2: 卡片列表
            // ════════════════════════════════════════════════
            LazyColumn(
                state = listState,
                contentPadding = PaddingValues(top = 40.dp, bottom = 100.dp),
            ) {
                item(key = "motto") {
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
                }

                itemsIndexed(
                    sortedEvents,
                    key = { idx, _ -> "tla_${idx}" }
                ) { index, event ->
                    val isLeft = index % 2 == 0

                    // ── 基于时间间隔（天）动态计算卡片间距 ──
                    // 间距与事件间隔成正比：每天 10dp，最小 28dp，最大 400dp
                    // 覆盖 0–37 天的线性比例区间，超过 37 天统一为最大间距
                    val timeGapSpacing = if (index > 0 && index - 1 < dayGaps.size) {
                        val daysDiff = dayGaps[index - 1]
                        (28.dp + (10.dp * daysDiff)).coerceIn(28.dp, 400.dp)
                    } else {
                        28.dp
                    }

                    val delay = (index * 40).coerceAtMost(200)
                    AnimatedVisibility(
                        visible = true,
                        enter = fadeIn(tween(300, delayMillis = delay)) +
                                slideInHorizontally(tween(300, delayMillis = delay)) {
                                    if (isLeft) -it / 4 else it / 4
                                },
                    ) {
                        Row(
                            Modifier
                                .fillMaxWidth()
                                .padding(top = timeGapSpacing, bottom = 4.dp),
                            verticalAlignment = Alignment.CenterVertically,
                        ) {
                            if (isLeft) {
                                // 左卡片
                                TimelineEventCard(
                                    event, { onEventClick(event) },
                                    Modifier.weight(1f).padding(start = 8.dp, end = 0.dp),
                                )
                                // 连接杆空间（视觉杆线由 Canvas 层绘制）
                                Spacer(Modifier.width(24.dp))
                                // 锚点标记：位于连接杆末端，onGloballyPositioned 精确取坐标
                                Box(
                                    modifier = Modifier
                                        .size(1.dp)
                                        .onGloballyPositioned { coords ->
                                            val root = coords.positionInRoot()
                                            val ref = containerRootOffset ?: return@onGloballyPositioned
                                            anchorPositions[index] = Offset(
                                                root.x - ref.x,
                                                root.y - ref.y,
                                            )
                                        }
                                )
                                Spacer(Modifier.width(14.dp))
                                Spacer(Modifier.weight(1f).padding(horizontal = 8.dp))
                            } else {
                                // 右卡片
                                Spacer(Modifier.weight(1f).padding(horizontal = 8.dp))
                                Spacer(Modifier.width(14.dp))
                                // 锚点标记：位于连接杆末端
                                Box(
                                    modifier = Modifier
                                        .size(1.dp)
                                        .onGloballyPositioned { coords ->
                                            val root = coords.positionInRoot()
                                            val ref = containerRootOffset ?: return@onGloballyPositioned
                                            anchorPositions[index] = Offset(
                                                root.x - ref.x,
                                                root.y - ref.y,
                                            )
                                        }
                                )
                                // 连接杆空间
                                Spacer(Modifier.width(24.dp))
                                TimelineEventCard(
                                    event, { onEventClick(event) },
                                    Modifier.weight(1f).padding(start = 0.dp, end = 8.dp),
                                )
                            }
                        }
                    }
                }
            }
        }

        // ── FAB ──
        FloatingActionButton(
            onClick = onAddClick,
            shape = CircleShape,
            containerColor = MaterialTheme.colorScheme.surface,
            contentColor = MaterialTheme.colorScheme.onSurface,
            modifier = Modifier
                .align(Alignment.BottomEnd)
                .padding(end = 20.dp, bottom = 88.dp)
                .size(48.dp),
        ) {
            Icon(Icons.Default.Add, stringResource(R.string.name_this_moment), Modifier.size(24.dp))
        }
    }
}

private data class Anchor(val index: Int, val x: Float, val y: Float)
