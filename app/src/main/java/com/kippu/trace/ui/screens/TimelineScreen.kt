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
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.Composable
import androidx.compose.runtime.mutableStateMapOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.nativeCanvas
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.layout.positionInRoot
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.kippu.trace.R
import com.kippu.trace.ui.theme.AccentColor
import com.kippu.trace.model.DateEvent
import com.kippu.trace.ui.components.TimelineEventCard
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId
import kotlin.math.absoluteValue
import kotlin.math.sqrt
import kotlinx.coroutines.delay
import kotlin.math.PI
import kotlin.math.roundToInt
import kotlin.math.cos
import kotlin.math.sin
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.LinearOutSlowInEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.IntOffset

/**
 * 时间轴页面
 *
 * 卡片左右交替排列，锚点通过横向连接杆连接到卡片边缘。
 * Catmull-Rom 样条动态穿过所有锚点，随滚动实时变化。
 * 「现在」大节点居中，将时间轴切分为过去和未来两段独立曲线，两端曲线向现在节点渐隐。
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

    val anchorPositions = remember { mutableStateMapOf<Int, Offset>() }
    var contentRootOffset by remember { mutableStateOf(Offset.Zero) }

    val bgColor = MaterialTheme.colorScheme.background

    val infiniteTransition = rememberInfiniteTransition()

    val nowPulse by infiniteTransition.animateFloat(
        initialValue = 0.85f, targetValue = 1.0f,
        animationSpec = infiniteRepeatable(tween(1800, easing = LinearOutSlowInEasing), RepeatMode.Reverse),
        label = "nowPulse"
    )

    val starTwinkle by infiniteTransition.animateFloat(
        initialValue = 0f, targetValue = 2f * PI.toFloat(),
        animationSpec = infiniteRepeatable(tween(5000, easing = LinearOutSlowInEasing), RepeatMode.Reverse),
        label = "starTwinkle"
    )

    val meteorPhase by infiniteTransition.animateFloat(
        initialValue = 0f, targetValue = 1f,
        animationSpec = infiniteRepeatable(tween(9000, easing = LinearEasing), RepeatMode.Restart),
        label = "meteorPhase"
    )

    val nowRotation by infiniteTransition.animateFloat(
        initialValue = 0f, targetValue = 2f * PI.toFloat(),
        animationSpec = infiniteRepeatable(tween(8000, easing = LinearEasing), RepeatMode.Restart),
        label = "nowRotation"
    )

    // 星空：固定种子的随机分布，避免重组时闪烁
    val stars = remember {
        val rng = java.util.Random(42)
        val all = mutableListOf<Star>()
        // 微星：大量细小暗淡
        repeat(150) {
            all.add(Star(rng.nextFloat(), rng.nextFloat(), 0.25f + rng.nextFloat() * 0.45f, 0.08f + rng.nextFloat() * 0.12f, rng.nextFloat() * 6f, rng.nextFloat() * 3f))
        }
        // 亮星：中等，带光晕
        repeat(35) {
            all.add(Star(rng.nextFloat(), rng.nextFloat(), 0.7f + rng.nextFloat() * 0.8f, 0.25f + rng.nextFloat() * 0.30f, rng.nextFloat() * 6f, rng.nextFloat() * 2f))
        }
        // 大星：少量，更亮，分布更均匀
        repeat(5) {
            all.add(Star(rng.nextFloat() * 0.8f + 0.1f, rng.nextFloat() * 0.8f + 0.1f, 1.2f + rng.nextFloat() * 1.3f, 0.50f + rng.nextFloat() * 0.25f, rng.nextFloat() * 4f, rng.nextFloat() * 1.5f))
        }
        all
    }

    // 流星：从屏幕外滑入，跨越屏幕后滑出
    val meteors = remember {
        val rng = java.util.Random(77)
        (0 until 7).map { i ->
            // 角度分布更广：~10°~80°，方向有变化
            val angle = PI.toFloat() * (0.05f + rng.nextFloat() * 0.40f)
            // 起点分散在不同位置
            val margin = 0.25f + rng.nextFloat() * 0.40f
            val startX = -margin
            val startY = -margin * (0.2f + rng.nextFloat() * 0.8f)
            MeteorEvent(
                trigger = i / 5f + rng.nextFloat() * 0.08f, // 均匀分布触发时机
                startX = startX,
                startY = startY,
                angle = angle,
                length = 80f + rng.nextFloat() * 70f,
                speed = 2.00f + rng.nextFloat() * 1.20f,
            )
        }
    }

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
            val nowColor = MaterialTheme.colorScheme.secondary
            val nowLabel = stringResource(R.string.timeline_now)
            val starColor = MaterialTheme.colorScheme.onBackground
            val scrollState = rememberScrollState()

            // ── 远景星空（固定层，随滚动做极慢视差）──
            Canvas(modifier = Modifier.fillMaxSize()) {
                val parallax = scrollState.value.toFloat() * 0.01f
                val vpW = size.width; val vpH = size.height
                                // ── 极淡银河/星云氛围 ──
                val nebulaBrush = Brush.radialGradient(
                    colors = listOf(
                        AccentColor.copy(alpha = 0.012f),
                        AccentColor.copy(alpha = 0.005f),
                        Color.Transparent,
                    ),
                    center = Offset(vpW * 0.5f, vpH * 0.35f),
                    radius = vpW * 0.60f,
                )
                drawRect(nebulaBrush, size = Size(vpW, vpH))
                val nebulaBrush2 = Brush.radialGradient(
                    colors = listOf(
                        nowColor.copy(alpha = 0.008f),
                        Color.Transparent,
                    ),
                    center = Offset(vpW * 0.72f, vpH * 0.65f),
                    radius = vpW * 0.45f,
                )
                drawRect(nebulaBrush2, size = Size(vpW, vpH))

                stars.forEach { star ->
                    var cy = (star.y * vpH - parallax) % vpH
                    if (cy < 0f) cy += vpH
                    val cx = star.x * vpW
                    val twinkle = 0.5f + 0.5f * sin(starTwinkle * star.speed + star.phase)
                    val a = (star.alpha * (0.25f + 0.75f * twinkle)).coerceIn(0f, 1f)
                    val r = star.radius.dp.toPx() * (0.75f + 0.25f * twinkle)
                    drawCircle(
                        Brush.radialGradient(listOf(starColor.copy(alpha = a), Color.Transparent), center = Offset(cx, cy), radius = r * 1.8f),
                        radius = r * 1.8f, center = Offset(cx, cy)
                    )
                    drawCircle(color = starColor.copy(alpha = a * 1.3f), radius = r, center = Offset(cx, cy))
                }

                // ── 流星：从屏外滑入，横跨后滑出 ──
                meteors.forEach { m ->
                    val local = (meteorPhase - m.trigger + 1f) % 1f
                    // 全程可视窗口：足够跨越整个屏幕
                    val activeWindow = 0.55f
                    if (local >= activeWindow) return@forEach
                    val prog = (local / activeWindow).coerceIn(0f, 1f)
                    // alpha：屏外渐显→屏内全亮→屏外渐隐
                    val alpha = when {
                        prog < 0.12f -> (prog / 0.12f).coerceIn(0f, 1f)
                        prog > 0.82f -> ((1f - prog) / 0.18f).coerceIn(0f, 1f)
                        else -> 1f
                    } * 0.85f
                    val dx = cos(m.angle) * vpW * m.speed * prog
                    val dy = sin(m.angle) * vpW * m.speed * prog
                    val hx = m.startX * vpW + dx
                    val hy = m.startY * vpH + dy - parallax * 0.5f
                    val tailLen = m.length.dp.toPx()
                    val tdx = -cos(m.angle)
                    val tdy = -sin(m.angle)

                    // 仅绘制屏幕可见部分（性能优化）
                    if (hx < -tailLen || hx > vpW + tailLen || hy < -tailLen || hy > vpH + tailLen) return@forEach

                    // 锥形尾巴路径
                    val headWidth = 1.8.dp.toPx()
                    val perpX = -tdy * headWidth
                    val perpY = tdx * headWidth
                    val tipX = hx + tdx * tailLen
                    val tipY = hy + tdy * tailLen
                    val midX = hx + tdx * tailLen * 0.4f
                    val midY = hy + tdy * tailLen * 0.4f
                    val midW = headWidth * 0.35f
                    val tailPath = Path().apply {
                        moveTo(hx + perpX, hy + perpY)
                        quadraticTo(midX + tdy * midW, midY - tdx * midW, tipX, tipY)
                        lineTo(tipX, tipY)
                        quadraticTo(midX - tdy * midW, midY + tdx * midW, hx - perpX, hy - perpY)
                        close()
                    }
                    drawPath(tailPath, starColor.copy(alpha = alpha * 0.35f))
                    drawPath(tailPath, starColor.copy(alpha = alpha * 0.12f), style = Stroke(2.dp.toPx()))

                    // 头部多层光晕
                    drawCircle(
                        Brush.radialGradient(listOf(starColor.copy(alpha = alpha * 0.5f), Color.Transparent), center = Offset(hx, hy), radius = 8.dp.toPx()),
                        radius = 8.dp.toPx(), center = Offset(hx, hy)
                    )
                    drawCircle(
                        Brush.radialGradient(listOf(starColor.copy(alpha = alpha * 0.7f), Color.Transparent), center = Offset(hx, hy), radius = 4.dp.toPx()),
                        radius = 4.dp.toPx(), center = Offset(hx, hy)
                    )
                    drawCircle(color = starColor.copy(alpha = alpha), radius = 1.5.dp.toPx(), center = Offset(hx, hy))
                    drawCircle(color = Color.White.copy(alpha = alpha * 0.6f), radius = 0.6.dp.toPx(), center = Offset(hx + 0.3.dp.toPx(), hy - 0.3.dp.toPx()))

                    // 尾迹碎粒
                    val rng = java.util.Random(m.hashCode().toLong())
                    repeat(5) {
                        val t = 0.15f + rng.nextFloat() * 0.7f
                        val px = hx + tdx * tailLen * t + rng.nextFloat() * 6.dp.toPx() - 3.dp.toPx()
                        val py = hy + tdy * tailLen * t + rng.nextFloat() * 6.dp.toPx() - 3.dp.toPx()
                        drawCircle(color = starColor.copy(alpha = alpha * 0.25f * (1f - t)), radius = 0.3.dp.toPx(), center = Offset(px, py))
                    }
                }
            }

            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .verticalScroll(scrollState),
            ) {
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .onGloballyPositioned { coords ->
                            contentRootOffset = coords.positionInRoot()
                        }
                ) {
                    // 层1: 曲线 + 锚点
                    Canvas(modifier = Modifier.matchParentSize()) {
                        val vpH = size.height

                        val allAnchors = anchorPositions
                            .toList()
                            .sortedBy { it.first }
                            .map { (idx, pos) -> Anchor(idx, pos.x, pos.y, isLeft = eventSideIndex(timelineItems, idx) % 2 == 0, isNow = idx == nowItemIndex) }

                        if (allAnchors.isEmpty()) return@Canvas

                        val rodLen = 24.dp.toPx()
                        val nodeRadius = 8.dp.toPx()
                        val nowNodeRadius = 30.dp.toPx()

                        val eventAnchors = allAnchors.filter { !it.isNow }
                        val nowAnchor = allAnchors.find { it.isNow }

                        // ── 单锚点 ──
                        if (allAnchors.size == 1) {
                            val a = allAnchors.first()
                            val center = Offset(a.x, a.y)
                            val isNow = a.isNow
                            val r = if (isNow) nowNodeRadius else nodeRadius

                            if (!isNow) {
                                drawLine(accentColor.copy(alpha = 0.28f), Offset(a.x, 0f), Offset(a.x, size.height), strokeWidth = 1.dp.toPx())
                            }
                            if (isNow) {
                                drawCircle(Brush.radialGradient(listOf(nowColor.copy(alpha = 0.10f * nowPulse), Color.Transparent), center = center, radius = 56.dp.toPx()), radius = 56.dp.toPx(), center = center)
                            }
                            drawCircle(color = surfaceColor, radius = r, center = center)
                            drawCircle(color = if (isNow) nowColor.copy(alpha = 0.30f) else accentColor.copy(alpha = 0.30f), radius = r, center = center, style = Stroke(if (isNow) 2.dp.toPx() else 1.6.dp.toPx()))
                            if (isNow) {
                                drawCircle(color = nowColor.copy(alpha = 0.40f), radius = 4.dp.toPx(), center = center)
                            } else {
                                drawCircle(color = accentColor.copy(alpha = 0.45f), radius = 3.5.dp.toPx(), center = center)
                            }
                            return@Canvas
                        }

                        // 按 now 分离
                        val pastAnchors = eventAnchors.filter { it.index < nowItemIndex }
                        val futureAnchors = eventAnchors.filter { it.index > nowItemIndex }

                        // ── Catmull-Rom → 三次贝塞尔 样条绘制 ──
                        fun drawSpline(anchors: List<Anchor>, extTop: Boolean, extBottom: Boolean) {
                            if (anchors.isEmpty()) return
                            if (anchors.size == 1) {
                                val a = anchors.first()
                                drawLine(accentColor.copy(alpha = 0.14f), Offset(a.x, 0f), Offset(a.x, vpH), strokeWidth = 1.dp.toPx())
                                return
                            }

                            val path = Path()
                            path.moveTo(anchors.first().x, anchors.first().y)
                            for (i in 0 until anchors.size - 1) {
                                val p0 = anchors.getOrElse(i - 1) { anchors.first() }
                                val p1 = anchors[i]
                                val p2 = anchors[i + 1]
                                val p3 = anchors.getOrElse(i + 2) { anchors.last() }

                                fun dist(a: Anchor, b: Anchor): Float {
                                    val dx = a.x - b.x; val dy = a.y - b.y
                                    return sqrt(dx * dx + dy * dy).coerceAtLeast(1f)
                                }
                                val d01 = dist(p0, p1)
                                val d12 = dist(p1, p2)
                                val d23 = dist(p2, p3)
                                val t0 = 0f; val t1 = d01; val t2 = d01 + d12; val t3 = d01 + d12 + d23
                                val s1 = (t2 - t1) / (3f * (t2 - t0).coerceAtLeast(1f))
                                val s2 = (t2 - t1) / (3f * (t3 - t1).coerceAtLeast(1f))
                                val cp1x = p1.x + (p2.x - p0.x) * s1
                                val cp1y = p1.y + (p2.y - p0.y) * s1
                                val cp2x = p2.x - (p3.x - p1.x) * s2
                                val cp2y = p2.y - (p3.y - p1.y) * s2
                                path.cubicTo(cp1x, cp1y, cp2x, cp2y, p2.x, p2.y)
                            }

                            drawPath(path, accentColor.copy(alpha = 0.55f), style = Stroke(1.4.dp.toPx()))

                            // 向两端延伸的尾巴（沿曲线方向继续延伸并渐隐）
                            if (extTop) {
                                val firstDirY = anchors[1].y - anchors[0].y
                                val extX: Float
                                if (firstDirY != 0f && anchors[0].y > 0f) {
                                    extX = anchors[0].x + (anchors[1].x - anchors[0].x) * (anchors[0].y / firstDirY)
                                } else { extX = anchors[0].x }
                                val tailLen = 160.dp.toPx()
                                val tailEnd = (anchors[0].y - tailLen).coerceAtLeast(-tailLen)
                                drawLine(
                                    Brush.verticalGradient(listOf(accentColor.copy(alpha = 0.55f), Color.Transparent), startY = anchors[0].y, endY = tailEnd),
                                    Offset(anchors[0].x, anchors[0].y), Offset(extX, tailEnd),
                                    strokeWidth = 1.4.dp.toPx()
                                )
                            }
                            if (extBottom) {
                                val n = anchors.lastIndex
                                val lastDirY = anchors[n].y - anchors[n - 1].y
                                val extX: Float
                                if (lastDirY != 0f && anchors[n].y < vpH) {
                                    extX = anchors[n].x + (anchors[n].x - anchors[n - 1].x) * ((vpH - anchors[n].y) / lastDirY)
                                } else { extX = anchors[n].x }
                                val tailLen = 160.dp.toPx()
                                val tailEnd = (anchors[n].y + tailLen).coerceAtMost(vpH + tailLen)
                                drawLine(
                                    Brush.verticalGradient(listOf(accentColor.copy(alpha = 0.55f), Color.Transparent), startY = anchors[n].y, endY = tailEnd),
                                    Offset(anchors[n].x, anchors[n].y), Offset(extX, tailEnd),
                                    strokeWidth = 1.4.dp.toPx()
                                )
                            }
                        }

                        // ── 绘制过去曲线（向顶部延伸，底部向 now 节点出头）──
                        drawSpline(pastAnchors, extTop = true, extBottom = false)

                        // ── 绘制未来曲线（向底部延伸，顶部向 now 节点出头）──
                        drawSpline(futureAnchors, extTop = false, extBottom = true)

                        // ── 向 now 节点延伸的曲线段（单条贝塞尔曲线，自身渐隐）──
                        nowAnchor?.let { now ->
                            if (pastAnchors.isNotEmpty()) {
                                val last = pastAnchors.last()
                                val sx = last.x; val sy = last.y
                                val ex = now.x; val ey = now.y - nowNodeRadius * 1.9f
                                val dy = ey - sy; val dx = ex - sx
                                val cp1 = Offset(sx + dx * 0.12f, sy + dy * 0.40f)
                                val cp2 = Offset(sx + dx * 0.65f, sy + dy * 0.75f)
                                val extPath = Path().apply {
                                    moveTo(sx, sy)
                                    cubicTo(cp1.x, cp1.y, cp2.x, cp2.y, ex, ey)
                                }
                                drawPath(extPath, Brush.verticalGradient(listOf(accentColor.copy(alpha = 0.38f), Color.Transparent), startY = sy, endY = ey), style = Stroke(1.35.dp.toPx()))
                            }
                            if (futureAnchors.isNotEmpty()) {
                                val first = futureAnchors.first()
                                val sx = now.x; val sy = now.y + nowNodeRadius * 1.6f
                                val ex = first.x; val ey = first.y
                                val dy = ey - sy; val dx = ex - sx
                                val cp1 = Offset(sx + dx * 0.35f, sy + dy * 0.25f)
                                val cp2 = Offset(sx + dx * 0.88f, sy + dy * 0.60f)
                                val extPath = Path().apply {
                                    moveTo(sx, sy)
                                    cubicTo(cp1.x, cp1.y, cp2.x, cp2.y, ex, ey)
                                }
                                drawPath(extPath, Brush.verticalGradient(listOf(Color.Transparent, accentColor.copy(alpha = 0.38f)), startY = sy, endY = ey), style = Stroke(1.35.dp.toPx()))
                            }
                        }

                        // ── 事件锚点连接杆 ──
                        eventAnchors.forEach { a ->
                            val isLeft = a.isLeft
                            val startX = if (isLeft) a.x - rodLen else a.x + nodeRadius
                            val endX = if (isLeft) a.x - nodeRadius else a.x + rodLen
                            drawLine(accentColor.copy(alpha = 0.28f), Offset(startX, a.y), Offset(endX, a.y), strokeWidth = 1.dp.toPx())
                            // 连接杆端点装饰点
                            drawCircle(color = accentColor.copy(alpha = 0.35f), radius = 1.dp.toPx(), center = Offset(startX, a.y))
                            drawCircle(color = accentColor.copy(alpha = 0.35f), radius = 1.dp.toPx(), center = Offset(endX, a.y))
                        }

                        // ── 事件锚点节点 ──
                        eventAnchors.forEach { a ->
                            val center = Offset(a.x, a.y)
                            // 节点填充 — 径向渐变
                            drawCircle(
                                Brush.radialGradient(listOf(surfaceColor, surfaceColor.copy(alpha = 0.65f)), center = center, radius = nodeRadius),
                                radius = nodeRadius, center = center
                            )
                            drawCircle(color = accentColor.copy(alpha = 0.30f), radius = nodeRadius, center = center, style = Stroke(1.6.dp.toPx()))
                            drawCircle(color = accentColor.copy(alpha = 0.55f), radius = 3.5.dp.toPx(), center = center)
                        }

                                                // ──「现在」光点 ──

                        nowAnchor?.let { a ->

                            val center = Offset(a.x, a.y)



                            // 外层柔和光晕（脉冲）

                            val glowRadius = 40.dp.toPx() + (nowPulse - 0.85f) / 0.15f * 16.dp.toPx()

                            drawCircle(

                                Brush.radialGradient(

                                    listOf(nowColor.copy(alpha = 0.12f * nowPulse), Color.Transparent),

                                    center = center, radius = glowRadius

                                ),

                                radius = glowRadius, center = center

                            )



                            // 4 条旋转光射线（尖端以小圆点收尾）

                            val rayLen = 24.dp.toPx()

                            (0 until 4).forEach { i ->

                                val angle = nowRotation + i.toFloat() * (PI.toFloat() * 0.5f)

                                val ex = center.x + cos(angle) * rayLen

                                val ey = center.y + sin(angle) * rayLen

                                val rayMod = 0.5f + 0.5f * cos(starTwinkle * 2f + i.toFloat() * 1.57f)

                                val rayAlpha = 0.20f * nowPulse * rayMod

                                drawLine(

                                    nowColor.copy(alpha = rayAlpha),

                                    center, Offset(ex, ey),

                                    strokeWidth = 1.5.dp.toPx()

                                )

                                // 射线尖端光点

                                drawCircle(

                                    nowColor.copy(alpha = rayAlpha * 1.5f),

                                    radius = 1.2.dp.toPx(), center = Offset(ex, ey)

                                )

                            }



                            // 光点核心 — 多层发光点

                            val coreR = 4.5.dp.toPx()

                            drawCircle(

                                Brush.radialGradient(

                                    listOf(Color.White.copy(alpha = 0.9f), nowColor.copy(alpha = 0.6f), Color.Transparent),

                                    center = center, radius = coreR * 2.5f

                                ),

                                radius = coreR * 2.5f, center = center

                            )

                            drawCircle(color = Color.White.copy(alpha = 0.95f), radius = coreR, center = center)

                            drawCircle(color = Color.White.copy(alpha = 0.50f), radius = coreR * 0.5f, center = center)



                            // 「NOW」标注 — 更轻量的文字

                            val textPaint = android.graphics.Paint().apply {

                                color = android.graphics.Color.argb(

                                    (nowColor.alpha * 0.50f).toInt(),

                                    (nowColor.red * 255).toInt(),

                                    (nowColor.green * 255).toInt(),

                                    (nowColor.blue * 255).toInt()

                                )

                                textSize = 10.sp.toPx()

                                isAntiAlias = true

                                typeface = android.graphics.Typeface.DEFAULT

                            }

                            drawContext.canvas.nativeCanvas.drawText(

                                nowLabel,

                                a.x + 20.dp.toPx(),

                                a.y + 3.sp.toPx(),

                                textPaint

                            )

                        }                    }

                    // 层2: 卡片列表
                    Column(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalAlignment = Alignment.CenterHorizontally,
                    ) {
                        Spacer(Modifier.height(50.dp))

                        timelineItems.forEachIndexed { index, item ->
                            val isLeft = eventSideIndex(timelineItems, index) % 2 == 0

                            val timeGapSpacing = if (index > 0 && index - 1 < dayGaps.size) {
                                val gapIndex = index - 1
                                val maxGapForSide = if (gapIndex < nowItemIndex) maxPastDayGap else maxFutureDayGap
                                timelineGapSpacing(dayGaps[gapIndex], maxGapForSide)
                            } else {
                                28.dp
                            }

                            // 卡片入场动画
                            val entryAnim = remember { Animatable(0f) }
                            LaunchedEffect(Unit) {
                                delay((index * 70L).coerceAtMost(800L))
                                entryAnim.animateTo(1f, spring(
                                    dampingRatio = Spring.DampingRatioLowBouncy,
                                    stiffness = Spring.StiffnessMediumLow
                                ))
                            }
                            val density = LocalDensity.current.density
                            Box(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .alpha(entryAnim.value)
                                    .offset { IntOffset(0, ((1f - entryAnim.value) * density * 20).roundToInt()) }
                            ) {
                            when (item) {
                                is TimelineItem.Now -> {
                                    TimelineNowNode(
                                        index = index,
                                        anchorPositions = anchorPositions,
                                        contentRootOffset = contentRootOffset,
                                        modifier = Modifier.padding(top = timeGapSpacing + 60.dp, bottom = 60.dp),
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
                        }

                        Spacer(Modifier.height(100.dp))
                    }
                }
            }
        }

        // ── 顶部渐隐遮罩 ──
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .height(40.dp)
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
                .height(56.dp)
                .align(Alignment.BottomCenter)
                .background(
                    Brush.verticalGradient(
                        colors = listOf(Color.Transparent, bgColor),
                    )
                )
        )
    }
}

/**
 * 「现在」大节点 — 锚点居中，NOW 标签在 Canvas 中绘制于节点右侧。
 */
@Composable
private fun TimelineNowNode(
    index: Int,
    anchorPositions: MutableMap<Int, Offset>,
    contentRootOffset: Offset,
    modifier: Modifier = Modifier,
) {
    Box(
        modifier = modifier.fillMaxWidth(),
        contentAlignment = Alignment.Center,
    ) {
        TimelineAnchor(index, anchorPositions, contentRootOffset)
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
        val maxSpacing = 560.dp
        val ratio = (daysDiff / maxDayGap.coerceAtLeast(1f)).coerceIn(0f, 1f)
        minSpacing + (maxSpacing - minSpacing) * ratio
    }
}

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
    val isNow: Boolean = false,
)

private data class Star(
    val x: Float,      // 0..1 相对位置
    val y: Float,      // 0..1 相对位置
    val radius: Float, // dp
    val alpha: Float,  // 基础透明度
    val phase: Float,  // 闪烁相位偏移
    val speed: Float,  // 闪烁速度倍数
)

private data class MeteorEvent(
    val trigger: Float, // 0..1 在 meteorPhase 周期中的触发点
    val startX: Float,  // 0..1
    val startY: Float,  // 0..1
    val angle: Float,   // 弧度，飞行方向
    val length: Float,  // dp，尾巴长度
    val speed: Float,   // 相对速度
)
