package com.example.ui.components

import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.ui.theme.AeroBackground
import com.example.ui.theme.AeroBorder
import com.example.ui.theme.AeroCard
import com.example.ui.theme.AeroEmerald
import com.example.ui.theme.AeroEmeraldSubtle
import com.example.ui.theme.AeroSecondaryGreen
import com.example.ui.theme.AeroTextPrimary
import com.example.ui.theme.AeroTextSecondary

@Composable
fun AeroCardBox(
    modifier: Modifier = Modifier,
    backgroundColor: Color = AeroCard,
    borderColor: Color = AeroBorder,
    shape: Shape = RoundedCornerShape(16.dp),
    content: @Composable BoxScope.() -> Unit
) {
    Box(
        modifier = modifier
            .clip(shape)
            .background(backgroundColor)
            .border(1.dp, borderColor, shape),
        content = content
    )
}

@Composable
fun RadarPulseEffect(
    modifier: Modifier = Modifier,
    primaryColor: Color = AeroEmerald
) {
    val infiniteTransition = rememberInfiniteTransition(label = "RadarTransition")
    val scale1 by infiniteTransition.animateFloat(
        initialValue = 0.2f,
        targetValue = 1.0f,
        animationSpec = infiniteRepeatable(
            animation = tween(2400, easing = FastOutSlowInEasing),
            repeatMode = RepeatMode.Restart
        ),
        label = "Scale1"
    )
    val alpha1 by infiniteTransition.animateFloat(
        initialValue = 0.8f,
        targetValue = 0.0f,
        animationSpec = infiniteRepeatable(
            animation = tween(2400, easing = FastOutSlowInEasing),
            repeatMode = RepeatMode.Restart
        ),
        label = "Alpha1"
    )

    Canvas(modifier = modifier) {
        val center = Offset(size.width / 2, size.height / 2)
        val maxRadius = size.minDimension / 2

        drawCircle(
            color = primaryColor.copy(alpha = alpha1),
            radius = maxRadius * scale1,
            center = center,
            style = Stroke(width = 2.dp.toPx())
        )
        drawCircle(
            color = primaryColor.copy(alpha = 0.15f),
            radius = maxRadius * 0.3f,
            center = center
        )
    }
}

@Composable
fun StylizedQrMatrix(
    modifier: Modifier = Modifier,
    ipText: String = "192.168.49.1:8829"
) {
    AeroCardBox(
        modifier = modifier.padding(16.dp),
        backgroundColor = AeroCard,
        borderColor = AeroBorder,
        shape = RoundedCornerShape(20.dp)
    ) {
        Column(
            modifier = Modifier.padding(20.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Canvas(modifier = Modifier.size(200.dp)) {
                val step = size.width / 15
                for (r in 0 until 15) {
                    for (c in 0 until 15) {
                        val isCorner = (r < 4 && c < 4) || (r < 4 && c > 10) || (r > 10 && c < 4)
                        val isPattern = isCorner || ((r * 7 + c * 13) % 3 == 0)
                        if (isPattern) {
                            val color = if (isCorner) AeroEmerald else AeroSecondaryGreen.copy(alpha = 0.85f)
                            drawRect(
                                color = color,
                                topLeft = Offset(c * step, r * step),
                                size = Size(step * 0.85f, step * 0.85f)
                            )
                        }
                    }
                }
            }
            Spacer(modifier = Modifier.height(14.dp))
            Text(
                text = ipText,
                color = AeroTextPrimary,
                fontWeight = FontWeight.Bold,
                fontSize = 13.sp
            )
            Text(
                text = "Wi-Fi Direct SAS Code: 8829",
                color = AeroSecondaryGreen,
                fontSize = 11.sp
            )
        }
    }
}
