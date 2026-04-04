package com.komamon.picturesearch.ui.component

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Image
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.unit.dp

@Composable
fun ChoiceImageCard(
    imageRes: Int,
    isShaking: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    val offsetX = remember { Animatable(0f) }

    LaunchedEffect(isShaking) {
        if (isShaking) {
            repeat(4) {
                offsetX.animateTo(10f, tween(60))
                offsetX.animateTo(-10f, tween(60))
            }
            offsetX.animateTo(0f, tween(60))
        } else {
            offsetX.snapTo(0f)
        }
    }

    val shape = RoundedCornerShape(12.dp)

    Card(
        onClick = onClick,
        shape = shape,
        elevation = CardDefaults.cardElevation(defaultElevation = 4.dp),
        colors = CardDefaults.cardColors(containerColor = Color.White),
        modifier = modifier
            .offset(x = offsetX.value.dp)
            .border(
                width = if (isShaking) 4.dp else 0.dp,
                color = if (isShaking) Color.Red else Color.Transparent,
                shape = shape
            )
    ) {
        Box(modifier = Modifier.fillMaxSize()) {
            Image(
                painter = painterResource(imageRes),
                contentDescription = null,
                modifier = Modifier
                    .fillMaxSize()
                    .padding(6.dp),
                contentScale = ContentScale.Fit
            )
        }
    }
}
