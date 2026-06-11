package com.kippu.trace.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Shadow
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import coil.compose.AsyncImage
import com.kippu.trace.R
import com.kippu.trace.model.DateEvent
import com.kippu.trace.utils.TimeUtils

/**
 * 时间轴事件卡片
 *
 * 统一基础尺寸（最小高度 80dp），高度根据标题文字量动态伸缩。
 * 标题最多 3 行，超出省略。
 */
@Composable
fun TimelineEventCard(
    event: DateEvent,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val context = LocalContext.current
    val relative = TimeUtils.getRelativeTime(event.targetDate)
    val timeDesc = TimeUtils.formatRelativeTime(context, relative)
    val prefix = if (event.isFuture) stringResource(R.string.label_until) else stringResource(R.string.label_since)
    val hasBg = event.backgroundUri != null

    val cardShape = RoundedCornerShape(18.dp)

    Card(
        onClick = onClick,
        modifier = modifier
            .fillMaxWidth()
            .heightIn(min = 80.dp),
        shape = cardShape,
        colors = CardDefaults.cardColors(
            containerColor = if (hasBg) Color.Transparent else MaterialTheme.colorScheme.surface
        ),
        elevation = CardDefaults.cardElevation(defaultElevation = 0.dp),
    ) {
        // 统一尺寸容器：确保有背景/无背景卡片高度一致
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .heightIn(min = 80.dp),
        ) {
            // 背景图层 — 先声明，在文字下面（Z-order: first = bottom）
            if (hasBg) {
                AsyncImage(
                    model = event.backgroundUri,
                    contentDescription = null,
                    modifier = Modifier
                        .matchParentSize()
                        .clip(cardShape),
                    contentScale = ContentScale.Crop,
                )
                // 轻量全局遮罩
                Box(
                    Modifier
                        .matchParentSize()
                        .clip(cardShape)
                        .background(Color.Black.copy(alpha = 0.10f)),
                )
                // 底部渐变遮罩
                Box(
                    Modifier
                        .matchParentSize()
                        .clip(cardShape)
                        .background(
                            Brush.verticalGradient(
                                colors = listOf(
                                    Color.Transparent,
                                    Color.Transparent,
                                    Color.Black.copy(alpha = 0.15f),
                                    Color.Black.copy(alpha = 0.45f),
                                ),
                                startY = 0f,
                                endY = Float.POSITIVE_INFINITY,
                            ),
                        ),
                )
                // 描边
                Box(
                    Modifier
                        .matchParentSize()
                        .clip(cardShape)
                        .border(0.5.dp, Color.White.copy(alpha = 0.18f), cardShape),
                )
            }

            // 文字内容 — 后声明，在背景上面（Z-order: last = top），由文字量决定卡片高度
            Column(
                modifier = Modifier
                    .padding(horizontal = 14.dp, vertical = 12.dp)
                    .heightIn(min = 56.dp), // 内容区最小高度，配合卡片最小 80dp
            ) {
                // 标题：支持多行，根据文字量动态撑开卡片
                Text(
                    event.title,
                    style = MaterialTheme.typography.bodyLarge.copy(
                        fontWeight = FontWeight.SemiBold,
                        color = if (hasBg) Color.White.copy(alpha = 0.95f)
                                else MaterialTheme.colorScheme.onSurface,
                        fontSize = 15.sp,
                        shadow = if (hasBg) Shadow(
                            color = Color.Black.copy(alpha = 0.45f),
                            blurRadius = 6f,
                        ) else Shadow.None,
                    ),
                    maxLines = 3,
                    overflow = TextOverflow.Ellipsis,
                )
                Spacer(Modifier.height(4.dp))
                // 时间描述
                Text(
                    "$prefix $timeDesc",
                    style = MaterialTheme.typography.labelSmall.copy(
                        color = if (hasBg) Color.White.copy(alpha = 0.75f)
                                else MaterialTheme.colorScheme.secondary,
                        fontSize = 12.sp,
                        shadow = if (hasBg) Shadow(
                            color = Color.Black.copy(alpha = 0.35f),
                            blurRadius = 4f,
                        ) else Shadow.None,
                    ),
                )
            }
        }
    }
}
