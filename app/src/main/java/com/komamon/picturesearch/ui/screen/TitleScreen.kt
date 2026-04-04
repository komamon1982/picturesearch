package com.komamon.picturesearch.ui.screen

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.TransformOrigin
import androidx.compose.ui.graphics.drawscope.rotate
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.komamon.picturesearch.data.QuizRepository
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlin.random.Random

// ─── アニメーションフェーズ ───────────────────────────────────────────────────
private enum class TitlePhase {
    IDLE,          // プレゼント箱 + おしてね！
    LID_OPENING,   // ふたが開く (0.5秒)
    CHARS_FLYING,  // キャラが飛び出す (1.5秒)
    CONGRATS,      // おめでとうテキスト + 紙吹雪 (1秒)
    READY          // ボタン表示
}

// ─── キャラクター横位置 (5枚) ─────────────────────────────────────────────────
private val CHAR_X_SPREADS = listOf(-80f, -38f, 0f, 38f, 80f)

// ─── 紙吹雪データ ──────────────────────────────────────────────────────────────
private data class Confetti(
    val x: Float,
    val startY: Float,
    val size: Float,
    val color: Color,
    val rotation: Float,
    val speed: Float
)

private val CONFETTI_COLORS = listOf(
    Color(0xFFFF6B6B), Color(0xFFFFE66D), Color(0xFF6BCB77),
    Color(0xFF4D96FF), Color(0xFFFF6FC8), Color(0xFFFFA500)
)

// ─── TitleScreen ──────────────────────────────────────────────────────────────
@Composable
fun TitleScreen(
    savedProgress: Int,
    onStartFromBeginning: () -> Unit,
    onContinue: () -> Unit
) {
    var phase by remember { mutableStateOf(TitlePhase.IDLE) }

    // ── アニメーション変数 ──────────────────────────────────────────────────
    val lidOffsetY   = remember { Animatable(0f) }
    val lidRotationX = remember { Animatable(0f) }

    val selectedChars = remember { QuizRepository.questions.shuffled().take(5) }
    val charOffsetY   = remember { List(5) { Animatable(0f) } }
    val charAlpha     = remember { List(5) { Animatable(0f) } }

    val congratsScale = remember { Animatable(0f) }
    val buttonAlpha   = remember { Animatable(0f) }

    // ── 無限トランジション (バウンス・紙吹雪) ──────────────────────────────
    val infiniteTransition = rememberInfiniteTransition(label = "title_infinite")

    val bounceOffset by infiniteTransition.animateFloat(
        initialValue = 0f,
        targetValue  = -10f,
        animationSpec = infiniteRepeatable(
            tween(500, easing = FastOutSlowInEasing),
            RepeatMode.Reverse
        ),
        label = "bounce"
    )

    val confettiProgress by infiniteTransition.animateFloat(
        initialValue = 0f,
        targetValue  = 1f,
        animationSpec = infiniteRepeatable(tween(3000, easing = LinearEasing)),
        label = "confetti"
    )

    val confettiParticles = remember {
        List(40) { i ->
            Confetti(
                x        = Random.nextFloat(),
                startY   = Random.nextFloat(),
                size     = Random.nextFloat() * 14f + 8f,
                color    = CONFETTI_COLORS[i % CONFETTI_COLORS.size],
                rotation = Random.nextFloat() * 360f,
                speed    = Random.nextFloat() * 0.5f + 0.7f
            )
        }
    }

    // ── フェーズ遷移ロジック ────────────────────────────────────────────────
    LaunchedEffect(phase) {
        when (phase) {

            TitlePhase.LID_OPENING -> {
                // ふたを上に回転させながら持ち上げる (0.5秒)
                coroutineScope {
                    launch { lidOffsetY.animateTo(-70f,  tween(500)) }
                    launch { lidRotationX.animateTo(-80f, tween(500)) }
                }
                phase = TitlePhase.CHARS_FLYING
            }

            TitlePhase.CHARS_FLYING -> {
                // キャラをずらしながら飛び出させる (合計 ~1.5秒)
                coroutineScope {
                    selectedChars.indices.forEach { i ->
                        launch {
                            delay(i * 220L)
                            launch { charAlpha[i].animateTo(1f, tween(220)) }
                            charOffsetY[i].animateTo(
                                -220f,
                                tween(580, easing = FastOutSlowInEasing)
                            )
                        }
                    }
                }
                phase = TitlePhase.CONGRATS
            }

            TitlePhase.CONGRATS -> {
                // おめでとうテキストがスプリングで登場
                congratsScale.animateTo(
                    1f,
                    spring(Spring.DampingRatioMediumBouncy, Spring.StiffnessLow)
                )
                delay(800)
                // ボタンがフェードイン
                buttonAlpha.animateTo(1f, tween(600))
                phase = TitlePhase.READY
            }

            else -> Unit
        }
    }

    // ── UI ─────────────────────────────────────────────────────────────────
    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(Brush.verticalGradient(listOf(Color(0xFFFFF9C4), Color(0xFFFFE082))))
            .clickable(enabled = phase == TitlePhase.IDLE) {
                phase = TitlePhase.LID_OPENING
            }
    ) {

        // ①紙吹雪レイヤー
        if (phase == TitlePhase.CONGRATS || phase == TitlePhase.READY) {
            Canvas(modifier = Modifier.fillMaxSize()) {
                confettiParticles.forEach { p ->
                    val cy = (confettiProgress * p.speed + p.startY) % 1f
                    val x  = p.x * size.width
                    val y  = cy * (size.height + p.size * 2f) - p.size
                    rotate(p.rotation + confettiProgress * 360f, Offset(x, y)) {
                        drawRect(
                            color    = p.color,
                            topLeft  = Offset(x - p.size / 2f, y - p.size / 2f),
                            size     = Size(p.size, p.size * 0.55f)
                        )
                    }
                }
            }
        }

        // ②タイトルテキスト (IDLE のみ)
        if (phase == TitlePhase.IDLE) {
            Text(
                text       = "えあてクイズ",
                fontSize   = 40.sp,
                fontWeight = FontWeight.Bold,
                color      = Color(0xFF5D4037),
                modifier   = Modifier
                    .align(Alignment.TopCenter)
                    .padding(top = 72.dp)
            )
        }

        // ③おめでとうテキスト (CONGRATS + READY)
        if (phase == TitlePhase.CONGRATS || phase == TitlePhase.READY) {
            Text(
                text       = "にゅうがく\nおめでとう！",
                fontSize   = 38.sp,
                fontWeight = FontWeight.Bold,
                color      = Color(0xFF4A148C),
                textAlign  = TextAlign.Center,
                modifier   = Modifier
                    .align(Alignment.TopCenter)
                    .padding(top = 56.dp)
                    .graphicsLayer {
                        scaleX = congratsScale.value
                        scaleY = congratsScale.value
                        alpha  = congratsScale.value
                    }
            )
        }

        // ④プレゼント箱エリア (画面中央より少し上に固定)
        Box(
            modifier          = Modifier
                .size(280.dp, 250.dp)
                .align(Alignment.Center)
                .offset(y = (-20).dp),
            contentAlignment  = Alignment.BottomCenter
        ) {

            // ── キャラクター画像 (箱の中心から飛び出す) ──────────────────
            // 初期Y: -25dp → 箱の中心と一致 (計算: 箱高130dp/2=65dp から箱底=0基準)
            selectedChars.forEachIndexed { i, question ->
                Image(
                    painter            = painterResource(question.imageRes),
                    contentDescription = null,
                    contentScale       = ContentScale.Fit,
                    modifier           = Modifier
                        .size(80.dp)
                        .align(Alignment.BottomCenter)
                        .offset(
                            x = CHAR_X_SPREADS[i].dp,
                            y = (-25f + charOffsetY[i].value).dp
                        )
                        .alpha(charAlpha[i].value)
                )
            }

            // ── 箱本体 (Canvas) ─────────────────────────────────────────
            Canvas(
                modifier = Modifier
                    .size(200.dp, 130.dp)
                    .align(Alignment.BottomCenter)
            ) {
                // 箱本体 (赤)
                drawRoundRect(
                    color        = Color(0xFFE53935),
                    cornerRadius = CornerRadius(16.dp.toPx())
                )
                // 縦リボン (黄)
                drawRect(
                    color    = Color(0xFFFFEB3B),
                    topLeft  = Offset(size.width * 0.42f, 0f),
                    size     = Size(size.width * 0.16f, size.height)
                )
                // 底部の影
                drawRoundRect(
                    color        = Color(0x40B71C1C),
                    topLeft      = Offset(0f, size.height * 0.75f),
                    size         = Size(size.width, size.height * 0.25f),
                    cornerRadius = CornerRadius(16.dp.toPx())
                )
            }

            // ── ふた (Canvas + graphicsLayer でアニメーション) ──────────
            // サイズ: 幅220dp × 高さ80dp (上28dpがリボン/蝶結びエリア, 下52dpがふた本体)
            // transform origin = bottom-center = ふたと箱本体の境界部分を軸に回転
            Box(
                modifier = Modifier
                    .size(220.dp, 80.dp)
                    .align(Alignment.BottomCenter)
                    .offset(y = (-130).dp)           // 箱本体の高さ分だけ上にずらす
                    .graphicsLayer {
                        translationY    = lidOffsetY.value
                        rotationX       = lidRotationX.value
                        transformOrigin = TransformOrigin(0.5f, 1f)  // 下辺を軸に回転
                        cameraDistance  = 10f * density
                    }
            ) {
                Canvas(modifier = Modifier.fillMaxSize()) {
                    val bowH   = 28.dp.toPx()
                    val yellow = Color(0xFFFFEB3B)
                    val cx     = size.width / 2f

                    // 蝶結び 左ループ
                    drawOval(
                        color    = yellow,
                        topLeft  = Offset(cx - 56.dp.toPx(), 1.dp.toPx()),
                        size     = Size(50.dp.toPx(), 26.dp.toPx())
                    )
                    // 蝶結び 右ループ
                    drawOval(
                        color    = yellow,
                        topLeft  = Offset(cx + 6.dp.toPx(), 1.dp.toPx()),
                        size     = Size(50.dp.toPx(), 26.dp.toPx())
                    )
                    // 蝶結び 中央ノット
                    drawCircle(
                        color  = Color(0xFFFF6F00),
                        radius = 11.dp.toPx(),
                        center = Offset(cx, bowH - 2.dp.toPx())
                    )

                    // ふた本体 (赤・角丸)
                    drawRoundRect(
                        color        = Color(0xFFEF5350),
                        topLeft      = Offset(0f, bowH),
                        size         = Size(size.width, size.height - bowH),
                        cornerRadius = CornerRadius(12.dp.toPx())
                    )
                    // ふたの縦リボン
                    drawRect(
                        color    = yellow,
                        topLeft  = Offset(size.width * 0.42f, bowH),
                        size     = Size(size.width * 0.16f, size.height - bowH)
                    )
                }
            }
        }

        // ⑤「おしてね！」テキスト (IDLE のみ・バウンス)
        if (phase == TitlePhase.IDLE) {
            Text(
                text       = "おしてね！",
                fontSize   = 26.sp,
                fontWeight = FontWeight.Bold,
                color      = Color(0xFF5D4037),
                modifier   = Modifier
                    .align(Alignment.Center)
                    .offset(y = (155 + bounceOffset).dp)
            )
        }

        // ⑥ボタン (READY のみ・フェードイン)
        if (phase == TitlePhase.READY) {
            Column(
                modifier              = Modifier
                    .align(Alignment.BottomCenter)
                    .padding(bottom = 64.dp)
                    .alpha(buttonAlpha.value),
                horizontalAlignment   = Alignment.CenterHorizontally
            ) {
                if (savedProgress > 0) {
                    Button(
                        onClick = onContinue,
                        shape   = RoundedCornerShape(50),
                        colors  = ButtonDefaults.buttonColors(containerColor = Color(0xFF4CAF50))
                    ) {
                        Text(
                            text       = "つづきから（${savedProgress + 1}もんめ）",
                            fontSize   = 18.sp,
                            fontWeight = FontWeight.Bold,
                            modifier   = Modifier.padding(vertical = 6.dp, horizontal = 8.dp)
                        )
                    }
                    Spacer(modifier = Modifier.height(12.dp))
                    Button(
                        onClick = onStartFromBeginning,
                        shape   = RoundedCornerShape(50),
                        colors  = ButtonDefaults.buttonColors(containerColor = Color(0xFFFF9800))
                    ) {
                        Text(
                            text       = "はじめから",
                            fontSize   = 18.sp,
                            fontWeight = FontWeight.Bold,
                            modifier   = Modifier.padding(vertical = 6.dp, horizontal = 12.dp)
                        )
                    }
                } else {
                    Button(
                        onClick = onStartFromBeginning,
                        shape   = RoundedCornerShape(50),
                        colors  = ButtonDefaults.buttonColors(containerColor = Color(0xFFFF9800))
                    ) {
                        Text(
                            text       = "クイズをはじめる！",
                            fontSize   = 22.sp,
                            fontWeight = FontWeight.Bold,
                            modifier   = Modifier.padding(vertical = 8.dp, horizontal = 16.dp)
                        )
                    }
                }
            }
        }
    }
}
