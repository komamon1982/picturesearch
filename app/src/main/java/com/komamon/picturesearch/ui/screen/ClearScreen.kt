package com.komamon.picturesearch.ui.screen

import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.rotate
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlin.random.Random

private data class ConfettiParticle(
    val x: Float,
    val startY: Float,
    val size: Float,
    val color: Color,
    val rotation: Float,
    val speed: Float
)

private val confettiColors = listOf(
    Color(0xFFFF6B6B),
    Color(0xFFFFE66D),
    Color(0xFF6BCB77),
    Color(0xFF4D96FF),
    Color(0xFFFF6FC8),
    Color(0xFFFFA500)
)

@Composable
fun ClearScreen(onReset: () -> Unit) {
    val particles = remember {
        List(40) { i ->
            ConfettiParticle(
                x = Random.nextFloat(),
                startY = Random.nextFloat(),
                size = Random.nextFloat() * 14f + 8f,
                color = confettiColors[i % confettiColors.size],
                rotation = Random.nextFloat() * 360f,
                speed = Random.nextFloat() * 0.5f + 0.7f
            )
        }
    }

    val infiniteTransition = rememberInfiniteTransition(label = "confetti")
    val progress by infiniteTransition.animateFloat(
        initialValue = 0f,
        targetValue = 1f,
        animationSpec = infiniteRepeatable(tween(3000, easing = LinearEasing)),
        label = "progress"
    )

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(
                Brush.verticalGradient(
                    listOf(Color(0xFFE1BEE7), Color(0xFFF8BBD0))
                )
            ),
        contentAlignment = Alignment.Center
    ) {
        // 紙吹雪
        Canvas(modifier = Modifier.fillMaxSize()) {
            particles.forEach { p ->
                val currentY = (progress * p.speed + p.startY) % 1f
                val x = p.x * size.width
                val y = currentY * (size.height + p.size * 2) - p.size

                rotate(degrees = p.rotation + progress * 360f, pivot = Offset(x, y)) {
                    drawRect(
                        color = p.color,
                        topLeft = Offset(x - p.size / 2, y - p.size / 2),
                        size = Size(p.size, p.size * 0.55f)
                    )
                }
            }
        }

        // お祝いメッセージ
        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            modifier = Modifier.padding(24.dp)
        ) {
            Text(text = "🎉", fontSize = 80.sp)
            Spacer(modifier = Modifier.height(16.dp))
            Text(
                text = "にゅうがく",
                fontSize = 42.sp,
                fontWeight = FontWeight.Bold,
                color = Color(0xFF4A148C)
            )
            Text(
                text = "おめでとう！",
                fontSize = 46.sp,
                fontWeight = FontWeight.Bold,
                color = Color(0xFF4A148C)
            )
            Spacer(modifier = Modifier.height(16.dp))
            Text(
                text = "ぜんもんせいかい！",
                fontSize = 24.sp,
                fontWeight = FontWeight.Bold,
                color = Color(0xFF7B1FA2)
            )
            Spacer(modifier = Modifier.height(48.dp))
            Button(
                onClick = onReset,
                shape = RoundedCornerShape(50),
                colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF7B1FA2))
            ) {
                Text(
                    text = "もういちど",
                    fontSize = 20.sp,
                    fontWeight = FontWeight.Bold,
                    modifier = Modifier.padding(vertical = 8.dp, horizontal = 16.dp)
                )
            }
        }
    }
}
