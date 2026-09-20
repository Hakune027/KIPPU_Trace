package com.kippu.trace.ui.components

import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.animateDpAsState
import androidx.compose.animation.core.spring
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp

data class SlidingSegmentOption(
    val label: String,
    val icon: ImageVector? = null,
)

@Composable
fun SlidingSegmentedControl(
    options: List<SlidingSegmentOption>,
    selectedIndex: Int,
    onSelected: (Int) -> Unit,
    modifier: Modifier = Modifier,
) {
    require(options.isNotEmpty()) { "SlidingSegmentedControl requires at least one option" }
    val safeSelectedIndex = selectedIndex.coerceIn(options.indices)

    Box(
        modifier = modifier
            .fillMaxWidth()
            .height(52.dp)
            .background(
                MaterialTheme.colorScheme.onSurface.copy(alpha = 0.05f),
                RoundedCornerShape(26.dp),
            )
            .padding(4.dp),
    ) {
        BoxWithConstraints(modifier = Modifier.fillMaxSize()) {
            val segmentWidth = maxWidth / options.size
            val indicatorOffset by animateDpAsState(
                targetValue = segmentWidth * safeSelectedIndex,
                animationSpec = spring(stiffness = Spring.StiffnessMedium),
                label = "segmentIndicator",
            )

            Box(
                modifier = Modifier
                    .offset { IntOffset(indicatorOffset.roundToPx(), 0) }
                    .width(segmentWidth)
                    .fillMaxHeight()
                    .background(MaterialTheme.colorScheme.primary, RoundedCornerShape(22.dp)),
            )

            Row(modifier = Modifier.fillMaxSize()) {
                options.forEachIndexed { index, option ->
                    val selected = index == safeSelectedIndex
                    val contentColor = if (selected) {
                        MaterialTheme.colorScheme.onPrimary
                    } else {
                        MaterialTheme.colorScheme.onSurface
                    }

                    Box(
                        modifier = Modifier
                            .weight(1f)
                            .fillMaxHeight()
                            .clip(RoundedCornerShape(22.dp))
                            .clickable { onSelected(index) },
                        contentAlignment = Alignment.Center,
                    ) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            option.icon?.let { icon ->
                                Icon(
                                    imageVector = icon,
                                    contentDescription = null,
                                    tint = contentColor,
                                    modifier = Modifier.size(18.dp),
                                )
                                Spacer(modifier = Modifier.width(8.dp))
                            }
                            Text(
                                text = option.label,
                                style = MaterialTheme.typography.labelLarge,
                                fontWeight = if (selected) FontWeight.Bold else FontWeight.Normal,
                                color = contentColor,
                            )
                        }
                    }
                }
            }
        }
    }
}
