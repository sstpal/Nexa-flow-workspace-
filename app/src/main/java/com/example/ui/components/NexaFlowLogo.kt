package com.example.ui.components

import androidx.compose.animation.core.*
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Language
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

val NexaPurple = Color(0xFF6750A4)
val NexaViolet = Color(0xFF7C3AED)
val NexaSky = Color(0xFF38BDF8)
val NexaRose = Color(0xFFF43F5E)
val NexaIndigo = Color(0xFF4F46E5)

@Composable
fun NexaFlowLogoBadge(
    modifier: Modifier = Modifier,
    size: Dp = 42.dp,
    showGlow: Boolean = true
) {
    val infiniteTransition = rememberInfiniteTransition(label = "logo_anim")
    val pulse by infiniteTransition.animateFloat(
        initialValue = 0.95f,
        targetValue = 1.05f,
        animationSpec = infiniteRepeatable(
            animation = tween(2000, easing = FastOutSlowInEasing),
            repeatMode = RepeatMode.Reverse
        ),
        label = "pulse"
    )

    Box(
        modifier = modifier
            .size(size)
            .shadow(
                elevation = if (showGlow) 6.dp else 0.dp,
                shape = RoundedCornerShape(12.dp),
                spotColor = NexaViolet
            )
            .clip(RoundedCornerShape(12.dp))
            .background(
                Brush.linearGradient(
                    colors = listOf(
                        Color(0xFF581C87),
                        Color(0xFF6750A4),
                        Color(0xFF3B0764)
                    )
                )
            ),
        contentAlignment = Alignment.Center
    ) {
        Canvas(modifier = Modifier.size(size * 0.75f)) {
            val w = this.size.width
            val h = this.size.height

            // Outer ring
            drawCircle(
                color = Color.White.copy(alpha = 0.15f),
                radius = w * 0.45f,
                style = Stroke(width = 2.dp.toPx())
            )

            // Flow Ribbon Path (Geometric N)
            val path = Path().apply {
                moveTo(w * 0.22f, h * 0.75f)
                lineTo(w * 0.22f, h * 0.25f)
                lineTo(w * 0.50f, h * 0.65f)
                lineTo(w * 0.78f, h * 0.25f)
                lineTo(w * 0.78f, h * 0.75f)
            }

            drawPath(
                path = path,
                color = Color.White,
                style = Stroke(
                    width = 3.dp.toPx(),
                    cap = StrokeCap.Round,
                    join = StrokeJoin.Round
                )
            )

            // Center highlight node
            drawCircle(
                color = NexaSky,
                radius = 3.dp.toPx(),
                center = Offset(w * 0.50f, h * 0.65f)
            )

            // Accent Top nodes
            drawCircle(
                color = Color(0xFFE9D5FF),
                radius = 2.5.dp.toPx(),
                center = Offset(w * 0.22f, h * 0.25f)
            )
            drawCircle(
                color = NexaSky,
                radius = 2.5.dp.toPx(),
                center = Offset(w * 0.78f, h * 0.25f)
            )
        }
    }
}

@Composable
fun NexaFlowHeaderTitle(
    modifier: Modifier = Modifier,
    subtitle: String = "Multi-Profile Isolated Browser"
) {
    Row(
        modifier = modifier,
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        NexaFlowLogoBadge(size = 44.dp)
        Column {
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                Text(
                    text = "Nexa Flow",
                    style = MaterialTheme.typography.titleMedium.copy(
                        fontWeight = FontWeight.ExtraBold,
                        fontSize = 18.sp,
                        letterSpacing = (-0.3).sp
                    ),
                    color = MaterialTheme.colorScheme.onSurface
                )
                Box(
                    modifier = Modifier
                        .clip(RoundedCornerShape(6.dp))
                        .background(MaterialTheme.colorScheme.primaryContainer)
                        .padding(horizontal = 6.dp, vertical = 2.dp)
                ) {
                    Text(
                        text = "GECKO v129",
                        fontSize = 9.sp,
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.onPrimaryContainer
                    )
                }
            }
            Text(
                text = subtitle,
                fontSize = 11.sp,
                fontWeight = FontWeight.Medium,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
    }
}
