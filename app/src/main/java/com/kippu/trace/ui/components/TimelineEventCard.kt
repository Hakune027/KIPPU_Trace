package com.kippu.trace.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
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
import com.kippu.trace.ui.theme.AccentColor
import com.kippu.trace.utils.TimeUtils

@Composable
fun TimelineEventCard(
    event: DateEvent,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val context = LocalContext.current
    val relative = TimeUtils.getRelativeTime(event.targetDate)
    val timeDesc = TimeUtils.formatRelativeTime(context, relative)
    val prefix = if (TimeUtils.isFuture(event.targetDate)) {
        stringResource(R.string.label_until)
    } else {
        stringResource(R.string.label_since)
    }
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
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .heightIn(min = 80.dp),
        ) {
            if (hasBg) {
                AsyncImage(
                    model = event.backgroundUri,
                    contentDescription = null,
                    modifier = Modifier
                        .matchParentSize()
                        .clip(cardShape),
                    contentScale = ContentScale.Crop,
                )
                // 三段式渐变遮罩
                Box(
                    Modifier
                        .matchParentSize()
                        .clip(cardShape)
                        .background(
                            Brush.verticalGradient(
                                colors = listOf(
                                    Color.Black.copy(alpha = 0.08f),
                                    Color.Transparent,
                                    Color.Black.copy(alpha = 0.20f),
                                    Color.Black.copy(alpha = 0.50f),
                                ),
                            ),
                        ),
                )
                // 描边
                Box(
                    Modifier
                        .matchParentSize()
                        .clip(cardShape)
                        .border(0.5.dp, AccentColor.copy(alpha = 0.25f), cardShape),
                )
            } else {
                // 无背景图卡片：极细边框
                Box(
                    Modifier
                        .matchParentSize()
                        .clip(cardShape)
                        .border(0.5.dp, MaterialTheme.colorScheme.outline.copy(alpha = 0.20f), cardShape),
                )
            }

            // 左侧点缀色条
            Box(
                modifier = Modifier
                    .fillMaxHeight()
                    .width(3.dp)
                    .padding(top = 8.dp, bottom = 8.dp)
                    .clip(RoundedCornerShape(topStart = 2.dp, bottomStart = 2.dp))
                    .background(AccentColor.copy(alpha = if (hasBg) 0.70f else 0.45f)),
            )

            // 文字内容
            Column(
                modifier = Modifier
                    .padding(start = 22.dp, end = 14.dp, top = 12.dp, bottom = 12.dp)
                    .heightIn(min = 56.dp),
            ) {
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
                Spacer(Modifier.height(6.dp))
                // 时间描述 — 徽章样式
                Box(
                    modifier = Modifier
                        .clip(RoundedCornerShape(6.dp))
                        .background(
                            if (hasBg) Color.White.copy(alpha = 0.12f)
                            else MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.60f)
                        )
                        .padding(horizontal = 8.dp, vertical = 3.dp),
                ) {
                    Text(
                        "$prefix $timeDesc",
                        style = MaterialTheme.typography.labelSmall.copy(
                            color = if (hasBg) Color.White.copy(alpha = 0.75f)
                                    else MaterialTheme.colorScheme.secondary,
                            fontSize = 11.sp,
                            shadow = if (hasBg) Shadow(
                                color = Color.Black.copy(alpha = 0.35f),
                                blurRadius = 4f,
                            ) else Shadow.None,
                            fontWeight = FontWeight.Medium,
                        ),
                    )
                }
            }
        }
    }
}
