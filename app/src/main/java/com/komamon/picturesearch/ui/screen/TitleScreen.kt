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
    CHARS_FLYING,  // キャラが飛び出す (全13枚・0.15秒ずつ)
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
    // 画面幅から文字サイズを決定 (45%)
    val screenWidthDp = LocalConfiguration.current.screenWidthDp.toFloat()
    val charSizeDp    = screenWidthDp * 0.45f
    // 箱の中心 (箱の高さ130dpの半分=65dp) にキャラを配置するための初期Yオフセット
    // align(BottomCenter) → キャラ中心 = 250 - charSize/2; 箱中心 = 250 - 65 = 185
    // 必要なオフセット = 185 - (250 - charSize/2) = charSize/2 - 65
    val charInitialY  = charSizeDp / 2f - 65f   // 正値=下方向(箱中心に合わせる)

    var phase by remember { mutableStateOf(TitlePhase.IDLE) }

    // ── Lid アニメーション ─────────────────────────────────────────────────
    val lidOffsetY   = remember { Animatable(0f) }
    val lidRotationX = remember { Animatable(0f) }

    // ── 全13枚のキャラアニメーション ───────────────────────────────────────
    val allChars    = QuizRepository.questions   // 固定順(char_01〜char_13)

    // X方向: 放物線の水平成分 (LinearEasing)
    val charOffsetX = remember { List(CHAR_COUNT) { Animatable(0f) } }
    // Y方向: 放物線の垂直成分 (FastOutSlowInEasing = 初速大・頂点でゆっくり)
    val charOffsetY = remember { List(CHAR_COUNT) { Animatable(0f) } }
    val charAlpha   = remember { List(CHAR_COUNT) { Animatable(0f) } }

    // 各キャラの飛び先をランダムに決定 (remember で1回だけ生成)
    val charTargetX = remember {
        List(CHAR_COUNT) { Random.nextFloat() * 240f - 120f }  // -120 〜 +120 dp
    }
    val charTargetY = remember {
        List(CHAR_COUNT) { -(Random.nextFloat() * 100f + 250f) }  // -250 〜 -350 dp
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
                // ふたを上方向に回転させて開く (0.5秒)
                coroutineScope {
                    launch { lidOffsetY.animateTo(-70f,  tween(500)) }
                    launch { lidRotationX.animateTo(-80f, tween(500)) }
                }
                phase = TitlePhase.CHARS_FLYING
            }

            TitlePhase.CHARS_FLYING -> {
                // 全13枚を0.15秒ずつずらして放物線で飛び出す
                // X: LinearEasing (等速水平移動) + Y: FastOutSlowInEasing (初速大・頂点でゆっくり)
                // → 組み合わせで放物線の軌跡を表現
                coroutineScope {
                    allChars.indices.forEach { i ->
                        launch {
                            delay(i * 150L)  // 0.15秒間隔
                            // フェードイン (短め)
                            launch { charAlpha[i].animateTo(1f, tween(150)) }
                            // 水平方向 (等速・直線)
                            launch {
                                charOffsetX[i].animateTo(
                                    charTargetX[i],
                                    tween(600, easing = LinearEasing)
                                )
                            }
                            // 垂直方向 (放物線の頂点まで加速→減速)
                            charOffsetY[i].animateTo(
                                charTargetY[i],
                                tween(600, easing = FastOutSlowInEasing)
                            )
                        }
                    }
                }
                // 全キャラが停止したらCONGRATSへ
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

        // ② タイトルテキスト (IDLE のみ)
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

        // ③ おめでとうテキスト (CONGRATS + READY)
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

        // ④ プレゼント箱エリア (画面中央より少し上に固定)
        Box(
            modifier         = Modifier
                .size(280.dp, 250.dp)
                .align(Alignment.Center)
                .offset(y = (-20).dp),
            contentAlignment = Alignment.BottomCenter
        ) {

            // ── キャラクター画像 (全13枚・箱の背後に待機→放物線で飛び出す) ──
            // 描画順: キャラ → 箱本体 → ふた (箱が手前でキャラを隠す)
            allChars.forEachIndexed { i, question ->
                Image(
                    painter            = painterResource(question.imageRes),
                    contentDescription = null,
                    contentScale       = ContentScale.Fit,
                    modifier           = Modifier
                        .size(charSizeDp.dp)  // 画面幅の45%
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
                // 箱本体 (赤)
                drawRoundRect(
                    color        = Color(0xFFE53935),
                    cornerRadius = CornerRadius(16.dp.toPx())
                )
                // 縦リボン (黄)
                drawRect(
                    color   = Color(0xFFFFEB3B),
                    topLeft = Offset(size.width * 0.42f, 0f),
                    size    = Size(size.width * 0.16f, size.height)
                )
                // 底部の影
                drawRoundRect(
                    color        = Color(0x40B71C1C),
                    topLeft      = Offset(0f, size.height * 0.75f),
                    size         = Size(size.width, size.height * 0.25f),
                    cornerRadius = CornerRadius(16.dp.toPx())
                )
            }

            // ── ふた (graphicsLayer でアニメーション) ────────────────────
            // 上28dp=蝶結びエリア / 下52dp=ふた本体
            // transformOrigin(0.5f, 1f) = 下辺を軸に回転 → ふたが開く動き
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

                    // 蝶結び 左ループ
                    drawOval(
                        color   = yellow,
                        topLeft = Offset(cx - 56.dp.toPx(), 1.dp.toPx()),
                        size    = Size(50.dp.toPx(), 26.dp.toPx())
                    )
                    // 蝶結び 右ループ
                    drawOval(
                        color   = yellow,
                        topLeft = Offset(cx + 6.dp.toPx(), 1.dp.toPx()),
                        size    = Size(50.dp.toPx(), 26.dp.toPx())
                    )
                    // 蝶結び 中央ノット
                    drawCircle(
                        color  = Color(0xFFFF6F00),
                        radius = 11.dp.toPx(),
                        center = Offset(cx, bowH - 2.dp.toPx())
                    )
                    // ふた本体
                    drawRoundRect(
                        color        = Color(0xFFEF5350),
                        topLeft      = Offset(0f, bowH),
                        size         = Size(size.width, size.height - bowH),
                        cornerRadius = CornerRadius(12.dp.toPx())
                    )
                    // ふたの縦リボン
                    drawRect(
                        color   = yellow,
                        topLeft = Offset(size.width * 0.42f, bowH),
                        size    = Size(size.width * 0.16f, size.height - bowH)
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

        // ⑥ ボタン (READY のみ・フェードイン)
        if (phase == TitlePhase.READY) {
            Column(
                modifier            = Modifier
                    .align(Alignment.BottomCenter)
                    .padding(bottom = 64.dp)
                    .alpha(buttonAlpha.value),
                horizontalAlignment = Alignment.CenterHorizontally
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
