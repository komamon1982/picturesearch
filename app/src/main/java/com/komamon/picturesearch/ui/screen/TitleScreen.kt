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
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
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
import androidx.compose.ui.platform.LocalConfiguration
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

// ─── 子どもの名前（ここを変更してください）───────────────────────────────────
private const val CHILD_NAME = "いちか"

// ─── フェーズ ────────────────────────────────────────────────────────────────
private enum class TitlePhase {
    IDLE,
    LID_OPENING,
    CHARS_FLYING,     // 箱から飛び出す（大きな放物線）
    CHARS_ARRANGING,  // グリッド位置へスライド整列
    CONGRATS,
    READY
}

// ─── 紙吹雪 ──────────────────────────────────────────────────────────────────
private data class Confetti(
    val x: Float, val startY: Float, val size: Float,
    val color: Color, val rotation: Float, val speed: Float
)
private val CONFETTI_COLORS = listOf(
    Color(0xFFFF6B6B), Color(0xFFFFE66D), Color(0xFF6BCB77),
    Color(0xFF4D96FF), Color(0xFFFF6FC8), Color(0xFFFFA500)
)

private const val CHAR_COUNT = 13

// ─── プレゼント箱（Column 内の最終表示用）────────────────────────────────────────
@Composable
private fun PresentBox(lidOffsetY: Float, lidRotationX: Float) {
    Box(modifier = Modifier.size(220.dp, 195.dp), contentAlignment = Alignment.BottomCenter) {
        Canvas(modifier = Modifier.size(160.dp, 105.dp).align(Alignment.BottomCenter)) {
            drawRoundRect(Color(0xFFE53935), cornerRadius = CornerRadius(14.dp.toPx()))
            drawRect(Color(0xFFFFEB3B), Offset(size.width * 0.42f, 0f), Size(size.width * 0.16f, size.height))
            drawRoundRect(Color(0x40B71C1C), Offset(0f, size.height * 0.75f),
                Size(size.width, size.height * 0.25f), CornerRadius(14.dp.toPx()))
        }
        Box(
            modifier = Modifier
                .size(176.dp, 64.dp)
                .align(Alignment.BottomCenter)
                .offset(y = (-105).dp)
                .graphicsLayer {
                    translationY    = lidOffsetY
                    rotationX       = lidRotationX
                    transformOrigin = TransformOrigin(0.5f, 1f)
                    cameraDistance  = 10f * density
                }
        ) {
            Canvas(modifier = Modifier.fillMaxSize()) {
                val bowH   = 22.dp.toPx()
                val yellow = Color(0xFFFFEB3B)
                val cx     = size.width / 2f
                drawOval(yellow, Offset(cx - 44.dp.toPx(), 1.dp.toPx()), Size(40.dp.toPx(), 20.dp.toPx()))
                drawOval(yellow, Offset(cx +  6.dp.toPx(), 1.dp.toPx()), Size(40.dp.toPx(), 20.dp.toPx()))
                drawCircle(Color(0xFFFF6F00),  9.dp.toPx(), Offset(cx, bowH - 2.dp.toPx()))
                drawRoundRect(Color(0xFFEF5350), Offset(0f, bowH),
                    Size(size.width, size.height - bowH), CornerRadius(10.dp.toPx()))
                drawRect(yellow, Offset(size.width * 0.42f, bowH),
                    Size(size.width * 0.16f, size.height - bowH))
            }
        }
    }
}

// ─── TitleScreen ─────────────────────────────────────────────────────────────
@Composable
fun TitleScreen(
    savedProgress: Int,
    onStartFromBeginning: () -> Unit,
    onContinue: () -> Unit
) {
    var phase by remember { mutableStateOf(TitlePhase.IDLE) }

    // シャッフル済み順番（この起動中は固定）
    val shuffledChars = remember { QuizRepository.questions.shuffled() }

    // ── 画面・レイアウトの定数 ───────────────────────────────────────────
    val W = LocalConfiguration.current.screenWidthDp.toFloat()
    val H = LocalConfiguration.current.screenHeightDp.toFloat()

    val flyCharSize  = W * 0.45f   // 飛び出しアニメ中のキャラサイズ (dp)
    val boxCenterX   = W / 2f     // プレゼント箱の中心X (dp)
    val boxCenterY   = H / 2f - 20f

    // ── アニメーション変数 ───────────────────────────────────────────────
    val lidOffsetY   = remember { Animatable(0f) }
    val lidRotationX = remember { Animatable(0f) }

    val charOffsetX = remember { List(CHAR_COUNT) { Animatable(0f) } }
    val charOffsetY = remember { List(CHAR_COUNT) { Animatable(0f) } }
    val charAlpha   = remember { List(CHAR_COUNT) { Animatable(0f) } }

    // 飛び出し時のランダムターゲット
    val charFlyX   = remember { List(CHAR_COUNT) { Random.nextFloat() * 200f - 100f } } // ±100dp
    // 着地Y: boxBottom(+125) + charHalf + 余白40dp を最低保証した上でランダム加算
    val charLandY  = remember(flyCharSize) { List(CHAR_COUNT) { 105f + flyCharSize / 2f + 40f + Random.nextFloat() * 80f } }

    val congratsScale = remember { Animatable(0f) }
    val buttonAlpha   = remember { Animatable(0f) }

    // ── 無限アニメーション ───────────────────────────────────────────────
    val inf = rememberInfiniteTransition(label = "title_inf")
    val bounceOffset by inf.animateFloat(
        0f, -10f,
        animationSpec = infiniteRepeatable(tween(500, easing = FastOutSlowInEasing), RepeatMode.Reverse),
        label = "bounce"
    )
    val confettiProg by inf.animateFloat(
        0f, 1f,
        animationSpec = infiniteRepeatable(tween(3000, easing = LinearEasing)),
        label = "confetti"
    )
    val confettiList = remember {
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

    // ── フェーズ遷移 ─────────────────────────────────────────────────────
    LaunchedEffect(phase) {
        when (phase) {

            TitlePhase.LID_OPENING -> {
                coroutineScope {
                    launch { lidOffsetY.animateTo(-70f,  tween(500)) }
                    launch { lidRotationX.animateTo(-80f, tween(500)) }
                }
                phase = TitlePhase.CHARS_FLYING
            }

            TitlePhase.CHARS_FLYING -> {
                // シャッフル順に 0.15秒ずつずらして放物線で飛び出す
                coroutineScope {
                    shuffledChars.indices.forEach { i ->
                        launch {
                            delay(i * 150L)
                            launch { charAlpha[i].animateTo(1f, tween(200)) }
                            launch {
                                charOffsetX[i].animateTo(charFlyX[i], tween(850, easing = LinearEasing))
                            }
                            charOffsetY[i].animateTo(-320f, tween(450, easing = FastOutSlowInEasing))
                            charOffsetY[i].animateTo(charLandY[i], tween(400, easing = FastOutSlowInEasing))
                        }
                    }
                }
                // 全キャラ着地後にフェードアウト（アニメ用レイヤーを消す）
                coroutineScope {
                    shuffledChars.indices.forEach { i ->
                        launch { charAlpha[i].animateTo(0f, tween(400)) }
                    }
                }
                phase = TitlePhase.CONGRATS
            }

            TitlePhase.CONGRATS -> {
                congratsScale.animateTo(
                    1f,
                    spring(Spring.DampingRatioMediumBouncy, Spring.StiffnessLow)
                )
                delay(800)
                buttonAlpha.animateTo(1f, tween(600))
                phase = TitlePhase.READY
            }

            else -> Unit
        }
    }

    // ── UI ───────────────────────────────────────────────────────────────
    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(Brush.verticalGradient(listOf(Color(0xFFFFF9C4), Color(0xFFFFE082))))
            .clickable(enabled = phase == TitlePhase.IDLE) {
                phase = TitlePhase.LID_OPENING
            }
    ) {

        // ① アニメ用キャラ（飛び出し中のみ表示・着地後フェードアウト）
        if (phase == TitlePhase.LID_OPENING || phase == TitlePhase.CHARS_FLYING) {
            shuffledChars.forEachIndexed { i, question ->
                Image(
                    painter            = painterResource(question.imageRes),
                    contentDescription = null,
                    contentScale       = ContentScale.Fit,
                    modifier           = Modifier
                        .size(flyCharSize.dp)
                        .offset(
                            x = (boxCenterX - flyCharSize / 2f + charOffsetX[i].value).dp,
                            y = (boxCenterY - flyCharSize / 2f + charOffsetY[i].value).dp
                        )
                        .alpha(charAlpha[i].value)
                )
            }
        }

        // ② プレゼント箱（IDLE・アニメ中のみ・最終表示は Column 内の PresentBox() で描画）
        if (phase != TitlePhase.CONGRATS && phase != TitlePhase.READY) {
            Box(
                modifier         = Modifier
                    .size(280.dp, 250.dp)
                    .align(Alignment.Center)
                    .offset(y = (-20).dp),
                contentAlignment = Alignment.BottomCenter
            ) {
                Canvas(modifier = Modifier.size(200.dp, 130.dp).align(Alignment.BottomCenter)) {
                    drawRoundRect(color = Color(0xFFE53935), cornerRadius = CornerRadius(16.dp.toPx()))
                    drawRect(color = Color(0xFFFFEB3B),
                        topLeft = Offset(size.width * 0.42f, 0f),
                        size    = Size(size.width * 0.16f, size.height))
                    drawRoundRect(color = Color(0x40B71C1C),
                        topLeft      = Offset(0f, size.height * 0.75f),
                        size         = Size(size.width, size.height * 0.25f),
                        cornerRadius = CornerRadius(16.dp.toPx()))
                }
                Box(
                    modifier = Modifier
                        .size(220.dp, 80.dp)
                        .align(Alignment.BottomCenter)
                        .offset(y = (-130).dp)
                        .graphicsLayer {
                            translationY    = lidOffsetY.value
                            rotationX       = lidRotationX.value
                            transformOrigin = TransformOrigin(0.5f, 1f)
                            cameraDistance  = 10f * density
                        }
                ) {
                    Canvas(modifier = Modifier.fillMaxSize()) {
                        val bowH   = 28.dp.toPx()
                        val yellow = Color(0xFFFFEB3B)
                        val cx     = size.width / 2f
                        drawOval(yellow, Offset(cx - 56.dp.toPx(), 1.dp.toPx()), Size(50.dp.toPx(), 26.dp.toPx()))
                        drawOval(yellow, Offset(cx +  6.dp.toPx(), 1.dp.toPx()), Size(50.dp.toPx(), 26.dp.toPx()))
                        drawCircle(Color(0xFFFF6F00), 11.dp.toPx(), Offset(cx, bowH - 2.dp.toPx()))
                        drawRoundRect(Color(0xFFEF5350), Offset(0f, bowH),
                            Size(size.width, size.height - bowH), CornerRadius(12.dp.toPx()))
                        drawRect(yellow, Offset(size.width * 0.42f, bowH),
                            Size(size.width * 0.16f, size.height - bowH))
                    }
                }
            }
        }

        // ③ IDLE 表示（タイトル・おしてね）
        if (phase == TitlePhase.IDLE) {
            Text(
                text       = "えあてクイズ",
                fontSize   = 40.sp,
                fontWeight = FontWeight.Bold,
                color      = Color(0xFF5D4037),
                modifier   = Modifier.align(Alignment.TopCenter).padding(top = 120.dp)
            )
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

        // ④ 最終表示（CONGRATS / READY）: Column レイアウト
        if (phase == TitlePhase.CONGRATS || phase == TitlePhase.READY) {

            // 紙吹雪（背面）
            Canvas(modifier = Modifier.fillMaxSize()) {
                confettiList.forEach { p ->
                    val cy = (confettiProg * p.speed + p.startY) % 1f
                    val x  = p.x * size.width
                    val y  = cy * (size.height + p.size * 2f) - p.size
                    rotate(p.rotation + confettiProg * 360f, Offset(x, y)) {
                        drawRect(p.color, Offset(x - p.size / 2f, y - p.size / 2f),
                            Size(p.size, p.size * 0.55f))
                    }
                }
            }

            // Column（スクロール対応）
            Column(
                modifier            = Modifier
                    .fillMaxSize()
                    .verticalScroll(rememberScrollState())
                    .padding(horizontal = 16.dp, vertical = 8.dp),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                Spacer(Modifier.height(112.dp))

                // にゅうがくおめでとう！
                Text(
                    text       = "にゅうがく\nおめでとう！",
                    fontSize   = 30.sp,
                    fontWeight = FontWeight.Bold,
                    color      = Color(0xFF4A148C),
                    textAlign  = TextAlign.Center,
                    modifier   = Modifier.graphicsLayer {
                        scaleX = congratsScale.value
                        scaleY = congratsScale.value
                        alpha  = congratsScale.value
                    }
                )

                Spacer(Modifier.height(8.dp))

                // ○○○ちゃん
                Text(
                    text       = "${CHILD_NAME}ちゃん",
                    fontSize   = 34.sp,
                    fontWeight = FontWeight.Bold,
                    color      = Color(0xFFE91E63),
                    modifier   = Modifier.graphicsLayer {
                        scaleX = congratsScale.value
                        scaleY = congratsScale.value
                        alpha  = congratsScale.value
                    }
                )

                Spacer(Modifier.height(16.dp))

                // プレゼント箱（蓋が開いた状態）
                PresentBox(lidOffsetY = lidOffsetY.value, lidRotationX = lidRotationX.value)

                Spacer(Modifier.height(8.dp))

                // キャラクター一覧（1行目：5枚、2行目：5枚、3行目：3枚）
                val charGridSize = (W - 32f) / 5f
                Row(
                    modifier              = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.Center
                ) {
                    shuffledChars.take(5).forEach { q ->
                        Image(
                            painter            = painterResource(q.imageRes),
                            contentDescription = null,
                            contentScale       = ContentScale.Fit,
                            modifier           = Modifier.size(charGridSize.dp)
                        )
                    }
                }
                Row(
                    modifier              = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.Center
                ) {
                    shuffledChars.drop(5).take(5).forEach { q ->
                        Image(
                            painter            = painterResource(q.imageRes),
                            contentDescription = null,
                            contentScale       = ContentScale.Fit,
                            modifier           = Modifier.size(charGridSize.dp)
                        )
                    }
                }
                Row(
                    modifier              = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.Center
                ) {
                    shuffledChars.drop(10).forEach { q ->
                        Image(
                            painter            = painterResource(q.imageRes),
                            contentDescription = null,
                            contentScale       = ContentScale.Fit,
                            modifier           = Modifier.size(charGridSize.dp)
                        )
                    }
                }

                Spacer(Modifier.height(12.dp))

                // ボタン（フェードイン）
                Column(
                    modifier            = Modifier.alpha(buttonAlpha.value),
                    horizontalAlignment = Alignment.CenterHorizontally
                ) {
                    Button(
                        onClick = onStartFromBeginning,
                        shape   = RoundedCornerShape(50),
                        colors  = ButtonDefaults.buttonColors(containerColor = Color(0xFFFF9800))
                    ) {
                        Text(
                            text       = "クイズをはじめる！",
                            fontSize   = 20.sp,
                            fontWeight = FontWeight.Bold,
                            modifier   = Modifier.padding(vertical = 7.dp, horizontal = 16.dp)
                        )
                    }
                    if (savedProgress > 0) {
                        Spacer(Modifier.height(8.dp))
                        Button(
                            onClick = onContinue,
                            shape   = RoundedCornerShape(50),
                            colors  = ButtonDefaults.buttonColors(containerColor = Color(0xFF78909C))
                        ) {
                            Text(
                                text       = "つづきからはじめる（${savedProgress + 1}もんめ）",
                                fontSize   = 14.sp,
                                fontWeight = FontWeight.Bold,
                                modifier   = Modifier.padding(vertical = 3.dp, horizontal = 8.dp)
                            )
                        }
                    }
                }

                Spacer(Modifier.height(8.dp))
            }
        }
    }
}
