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

// ─── アニメーションフェーズ ───────────────────────────────────────────────────
private enum class TitlePhase {
    IDLE,          // プレゼント箱 + おしてね！
    LID_OPENING,   // ふたが開く (0.5秒)
    CHARS_FLYING,  // キャラが飛び出す → 下部に着地
    CONGRATS,      // おめでとうテキスト + 紙吹雪
    READY          // ボタン表示
}

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

private const val CHAR_COUNT = 13

// ─── TitleScreen ──────────────────────────────────────────────────────────────
@Composable
fun TitleScreen(
    savedProgress: Int,
    onStartFromBeginning: () -> Unit,
    onContinue: () -> Unit
) {
    // 画面幅からキャラクターサイズを決定 (45%)
    val screenWidthDp = LocalConfiguration.current.screenWidthDp.toFloat()
    val charSizeDp    = screenWidthDp * 0.45f

    // 着地Y計算のメモ:
    //   align(BottomCenter) + offset(y=charInitialY+charOffsetY) のとき、
    //   キャラ中心 (親Box座標の上端からの距離) = 185 + charOffsetY
    //   (charSizeDpに関わらず成立: charInitialY = charSizeDp/2 - 65 がキャンセル)
    //   親Box下端 = 250dp から、
    //   85～180dp 下に着地 → charOffsetY = (250+85～180) - 185 + charSizeDp/2
    //   = 150+charSizeDp/2 ～ 245+charSizeDp/2

    var phase by remember { mutableStateOf(TitlePhase.IDLE) }

    // ── Lid アニメーション ─────────────────────────────────────────────────
    val lidOffsetY   = remember { Animatable(0f) }
    val lidRotationX = remember { Animatable(0f) }

    // ── 全13枚のキャラアニメーション ───────────────────────────────────────
    val allChars    = QuizRepository.questions
    val charOffsetX = remember { List(CHAR_COUNT) { Animatable(0f) } }
    val charOffsetY = remember { List(CHAR_COUNT) { Animatable(0f) } }
    val charAlpha   = remember { List(CHAR_COUNT) { Animatable(0f) } }

    // 横方向の散らばり目標 (±120dp)
    val charTargetX = remember {
        List(CHAR_COUNT) { Random.nextFloat() * 240f - 120f }
    }
    // 着地目標Y: 親Box下端から 85〜175dp 下に着地
    //   charOffsetY = 150 + charSizeDp/2 + [0〜90] (dp)
    val charLandingY = remember(charSizeDp) {
        List(CHAR_COUNT) { 150f + charSizeDp / 2f + Random.nextFloat() * 90f }
    }

    // ── おめでとうアニメーション ───────────────────────────────────────────
    val congratsScale = remember { Animatable(0f) }
    val buttonAlpha   = remember { Animatable(0f) }

    // ── 無限トランジション (バウンス・紙吹雪) ──────────────────────────────
    val infiniteTransition = rememberInfiniteTransition(label = "title_infinite")

    val bounceOffset by infiniteTransition.animateFloat(
        initialValue  = 0f,
        targetValue   = -10f,
        animationSpec = infiniteRepeatable(
            tween(500, easing = FastOutSlowInEasing),
            RepeatMode.Reverse
        ),
        label = "bounce"
    )
    val confettiProgress by infiniteTransition.animateFloat(
        initialValue  = 0f,
        targetValue   = 1f,
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
                coroutineScope {
                    launch { lidOffsetY.animateTo(-70f,  tween(500)) }
                    launch { lidRotationX.animateTo(-80f, tween(500)) }
                }
                phase = TitlePhase.CHARS_FLYING
            }

            TitlePhase.CHARS_FLYING -> {
                // 全13枚を 0.12秒ずつずらして放物線で飛び出し → 下部に着地
                // X: 等速水平移動 (LinearEasing, 700ms)
                // Y Phase1: 上に射出 (300ms, FastOutSlowIn)
                // Y Phase2: 下の着地点に落下 (400ms, FastOutSlowIn)
                coroutineScope {
                    allChars.indices.forEach { i ->
                        launch {
                            delay(i * 120L)
                            launch { charAlpha[i].animateTo(1f, tween(150)) }
                            launch {
                                charOffsetX[i].animateTo(
                                    charTargetX[i],
                                    tween(700, easing = LinearEasing)
                                )
                            }
                            charOffsetY[i].animateTo(-120f, tween(300, easing = FastOutSlowInEasing))
                            charOffsetY[i].animateTo(charLandingY[i], tween(400, easing = FastOutSlowInEasing))
                        }
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

    // ── UI ─────────────────────────────────────────────────────────────────
    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(Brush.verticalGradient(listOf(Color(0xFFFFF9C4), Color(0xFFFFE082))))
            .clickable(enabled = phase == TitlePhase.IDLE) {
                phase = TitlePhase.LID_OPENING
            }
    ) {

        // ① 紙吹雪レイヤー (CONGRATS 以降)
        if (phase == TitlePhase.CONGRATS || phase == TitlePhase.READY) {
            Canvas(modifier = Modifier.fillMaxSize()) {
                confettiParticles.forEach { p ->
                    val cy = (confettiProgress * p.speed + p.startY) % 1f
                    val x  = p.x * size.width
                    val y  = cy * (size.height + p.size * 2f) - p.size
                    rotate(p.rotation + confettiProgress * 360f, Offset(x, y)) {
                        drawRect(
                            color   = p.color,
                            topLeft = Offset(x - p.size / 2f, y - p.size / 2f),
                            size    = Size(p.size, p.size * 0.55f)
                        )
                    }
                }
            }
        }

        // ② 上部コンテンツ：タイトル(IDLE) または おめでとう+ボタン(CONGRATS/READY)
        //    レイアウト優先順位: ボタン上 → プレゼント箱中央 → キャラ下
        Column(
            modifier            = Modifier
                .align(Alignment.TopCenter)
                .padding(top = 48.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            when (phase) {
                TitlePhase.IDLE, TitlePhase.LID_OPENING -> {
                    Text(
                        text       = "えあてクイズ",
                        fontSize   = 40.sp,
                        fontWeight = FontWeight.Bold,
                        color      = Color(0xFF5D4037)
                    )
                }
                TitlePhase.CONGRATS, TitlePhase.READY -> {
                    // おめでとうテキスト
                    Text(
                        text       = "にゅうがく\nおめでとう！",
                        fontSize   = 36.sp,
                        fontWeight = FontWeight.Bold,
                        color      = Color(0xFF4A148C),
                        textAlign  = TextAlign.Center,
                        modifier   = Modifier.graphicsLayer {
                            scaleX = congratsScale.value
                            scaleY = congratsScale.value
                            alpha  = congratsScale.value
                        }
                    )
                    // ボタン: プレゼント箱の上に配置 (キャラとの重なり防止)
                    if (phase == TitlePhase.READY) {
                        Spacer(modifier = Modifier.height(14.dp))
                        Column(
                            modifier            = Modifier.alpha(buttonAlpha.value),
                            horizontalAlignment = Alignment.CenterHorizontally
                        ) {
                            if (savedProgress > 0) {
                                Button(
                                    onClick = onContinue,
                                    shape   = RoundedCornerShape(50),
                                    colors  = ButtonDefaults.buttonColors(
                                        containerColor = Color(0xFF4CAF50)
                                    )
                                ) {
                                    Text(
                                        text       = "つづきから（${savedProgress + 1}もんめ）",
                                        fontSize   = 17.sp,
                                        fontWeight = FontWeight.Bold,
                                        modifier   = Modifier.padding(
                                            vertical   = 5.dp,
                                            horizontal = 8.dp
                                        )
                                    )
                                }
                                Spacer(modifier = Modifier.height(10.dp))
                                Button(
                                    onClick = onStartFromBeginning,
                                    shape   = RoundedCornerShape(50),
                                    colors  = ButtonDefaults.buttonColors(
                                        containerColor = Color(0xFFFF9800)
                                    )
                                ) {
                                    Text(
                                        text       = "はじめから",
                                        fontSize   = 17.sp,
                                        fontWeight = FontWeight.Bold,
                                        modifier   = Modifier.padding(
                                            vertical   = 5.dp,
                                            horizontal = 12.dp
                                        )
                                    )
                                }
                            } else {
                                Button(
                                    onClick = onStartFromBeginning,
                                    shape   = RoundedCornerShape(50),
                                    colors  = ButtonDefaults.buttonColors(
                                        containerColor = Color(0xFFFF9800)
                                    )
                                ) {
                                    Text(
                                        text       = "クイズをはじめる！",
                                        fontSize   = 20.sp,
                                        fontWeight = FontWeight.Bold,
                                        modifier   = Modifier.padding(
                                            vertical   = 7.dp,
                                            horizontal = 16.dp
                                        )
                                    )
                                }
                            }
                        }
                    }
                }
                else -> Unit
            }
        }

        // ③ プレゼント箱エリア (画面中央固定・フェーズ変化で動かない)
        Box(
            modifier         = Modifier
                .size(280.dp, 250.dp)
                .align(Alignment.Center)
                .offset(y = (-20).dp),
            contentAlignment = Alignment.BottomCenter
        ) {
            // ── キャラクター画像 ─────────────────────────────────────────
            // 描画順: キャラ(背面) → 箱本体 → ふた(前面)
            // 初期: charOffsetY=0 → 箱中心に待機 (alpha=0で不可視)
            // 飛び出し: 上昇後、charLandingY (正値=下方向) の位置に着地
            //           → 親Boxを大きく超えて画面下部に着地
            allChars.forEachIndexed { i, question ->
                val charInitialY = charSizeDp / 2f - 65f  // 箱中心に合わせる初期オフセット
                Image(
                    painter            = painterResource(question.imageRes),
                    contentDescription = null,
                    contentScale       = ContentScale.Fit,
                    modifier           = Modifier
                        .size(charSizeDp.dp)
                        .align(Alignment.BottomCenter)
                        .offset(
                            x = charOffsetX[i].value.dp,
                            y = (charInitialY + charOffsetY[i].value).dp
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
                drawRoundRect(
                    color        = Color(0xFFE53935),
                    cornerRadius = CornerRadius(16.dp.toPx())
                )
                drawRect(
                    color   = Color(0xFFFFEB3B),
                    topLeft = Offset(size.width * 0.42f, 0f),
                    size    = Size(size.width * 0.16f, size.height)
                )
                drawRoundRect(
                    color        = Color(0x40B71C1C),
                    topLeft      = Offset(0f, size.height * 0.75f),
                    size         = Size(size.width, size.height * 0.25f),
                    cornerRadius = CornerRadius(16.dp.toPx())
                )
            }

            // ── ふた (graphicsLayer でアニメーション) ────────────────────
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
                    drawOval(
                        color   = yellow,
                        topLeft = Offset(cx - 56.dp.toPx(), 1.dp.toPx()),
                        size    = Size(50.dp.toPx(), 26.dp.toPx())
                    )
                    drawOval(
                        color   = yellow,
                        topLeft = Offset(cx + 6.dp.toPx(), 1.dp.toPx()),
                        size    = Size(50.dp.toPx(), 26.dp.toPx())
                    )
                    drawCircle(
                        color  = Color(0xFFFF6F00),
                        radius = 11.dp.toPx(),
                        center = Offset(cx, bowH - 2.dp.toPx())
                    )
                    drawRoundRect(
                        color        = Color(0xFFEF5350),
                        topLeft      = Offset(0f, bowH),
                        size         = Size(size.width, size.height - bowH),
                        cornerRadius = CornerRadius(12.dp.toPx())
                    )
                    drawRect(
                        color   = yellow,
                        topLeft = Offset(size.width * 0.42f, bowH),
                        size    = Size(size.width * 0.16f, size.height - bowH)
                    )
                }
            }
        }

        // ④「おしてね！」テキスト (IDLE のみ・バウンス)
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
    }
}
