package com.ssafy.lanterns.ui.screens.ondevice

import android.Manifest
import android.app.Activity
import android.content.Context
import android.content.pm.PackageManager
import android.os.Build
import android.util.Log
import android.view.WindowManager
import android.widget.Toast
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.ActivityResultLauncher
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.DrawScope // DrawScope import 추가
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.toArgb // toArgb import 추가
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import androidx.core.content.ContextCompat
import androidx.core.view.WindowCompat
import androidx.hilt.navigation.compose.hiltViewModel
import com.ssafy.lanterns.ui.theme.LanternCoreYellow
import com.ssafy.lanterns.ui.theme.LanternGlowOrange
// import com.ssafy.lanterns.ui.theme.LanternParticleColor // 사용되지 않으므로 제거 가능
import com.ssafy.lanterns.ui.theme.LanternShadeDark // 사용되지 않으므로 제거 가능 (VoiceModal 삭제됨)
import com.ssafy.lanterns.ui.theme.LanternWarmWhite
import kotlin.math.PI
import kotlin.math.abs
import kotlin.math.cos
import kotlin.math.floor
import kotlin.math.sin
import kotlin.random.Random // Random import 추가


// Perlin Noise 구현 (SimplePerlinNoise 클래스는 이전과 동일하게 유지)
class SimplePerlinNoise {
    private val permutation = IntArray(512)
    init {
        val p = IntArray(256) { it }
        p.shuffle(Random(System.currentTimeMillis())) // kotlin.random.Random 사용
        for (i in 0 until 512) {
            permutation[i] = p[i and 255]
        }
    }
    private fun fade(t: Float): Float = t * t * t * (t * (t * 6 - 15) + 10)
    private fun lerp(a: Float, b: Float, t: Float): Float = a + t * (b - a)
    private fun grad(hash: Int, x: Float, y: Float): Float {
        val h = hash and 3
        val u = if (h < 2) x else y
        val v = if (h < 2) y else x
        return ((h and 1).toFloat() * 2 - 1) * u + ((h and 2).toFloat() - 1) * v
    }
    fun noise2D(x: Float, y: Float): Float {
        val xi = floor(x).toInt() and 255
        val yi = floor(y).toInt() and 255
        val xf = x - floor(x)
        val yf = y - floor(y)
        val u = fade(xf)
        val v = fade(yf)
        val aa = permutation[permutation[xi] + yi]
        val ab = permutation[permutation[xi] + yi + 1]
        val ba = permutation[permutation[xi + 1] + yi]
        val bb = permutation[permutation[xi + 1] + yi + 1]
        val x1 = lerp(grad(aa, xf, yf), grad(ba, xf - 1, yf), u)
        val x2 = lerp(grad(ab, xf, yf - 1), grad(bb, xf - 1, yf - 1), u)
        return (lerp(x1, x2, v) + 1) / 2
    }
}

@Composable
private fun VoiceWaveEffect(
    modifier: Modifier = Modifier,
    isListening: Boolean
) {
    val infiniteTransition = rememberInfiniteTransition(label = "VoiceWaveTransition")

    val waveProgress = if (isListening) {
        infiniteTransition.animateFloat(
            initialValue = 0f,
            targetValue = 1f,
            animationSpec = infiniteRepeatable(
                animation = tween(1500, easing = FastOutSlowInEasing),
                repeatMode = RepeatMode.Restart
            ),
            label = "WaveProgress"
        ).value
    } else 0f

    val rotationAngle = if (isListening) {
        infiniteTransition.animateFloat(
            initialValue = 0f,
            targetValue = 360f,
            animationSpec = infiniteRepeatable(
                animation = tween(25000, easing = LinearEasing),
                repeatMode = RepeatMode.Restart
            ),
            label = "RotationAngle"
        ).value
    } else 0f

    val numWaves = 5

    Canvas(modifier = modifier.alpha(if (isListening) 1f else 0f)) {
        val center = this.center
        val minDimension = size.minDimension / 2f * 0.85f

        for (i in 0 until numWaves) {
            val normalizedIndex = i / numWaves.toFloat()
            val radiusFactor = 1f - normalizedIndex * 0.5f
            val radius = minDimension * radiusFactor * (0.7f + 0.3f * waveProgress)
            val alpha = (1f - normalizedIndex) * 0.08f * (0.4f + 0.6f * waveProgress)
            drawCircle(
                color = Color.White.copy(alpha = alpha.coerceIn(0.01f, 0.08f)),
                radius = radius,
                center = center,
                style = Stroke(
                    width = (0.7.dp + (0.6.dp * (1f - normalizedIndex))).toPx()
                )
            )
        }

        val rotationRad = rotationAngle * (PI / 180f).toFloat()
        val numRotatingWaves = 3

        for (i in 0 until numRotatingWaves) {
            val angleStep = 360f / numRotatingWaves
            val currentAngle = rotationRad + angleStep * i * (PI / 180f).toFloat()
            val waveAmplitude = minDimension * 0.08f * (0.2f + 0.8f * sin(waveProgress * PI.toFloat() * 2f + i * PI.toFloat() / 2f))
            val baseRadius = minDimension * 0.70f
            val x1 = center.x + cos(currentAngle) * (baseRadius - waveAmplitude)
            val y1 = center.y + sin(currentAngle) * (baseRadius - waveAmplitude)
            val x2 = center.x + cos(currentAngle) * (baseRadius + waveAmplitude)
            val y2 = center.y + sin(currentAngle) * (baseRadius + waveAmplitude)
            val alpha = (0.08f - 0.04f * abs(cos(waveProgress * PI.toFloat() * 2f + i * PI.toFloat() / 2f))) * 0.7f
            drawLine(
                color = Color.White.copy(alpha = alpha.coerceIn(0.005f, 0.06f)),
                start = Offset(x1, y1),
                end = Offset(x2, y2),
                strokeWidth = 0.7.dp.toPx()
            )
        }
    }
}

@Composable
private fun LanternOrbEffect(
    modifier: Modifier = Modifier,
    aiState: AiState
) {
    val infiniteTransition = rememberInfiniteTransition(label = "OrbTransition")

    val pulseProgress = infiniteTransition.animateFloat(
        initialValue = 0.97f,
        targetValue = 1.03f,
        animationSpec = infiniteRepeatable(
            animation = tween(2200, easing = FastOutSlowInEasing),
            repeatMode = RepeatMode.Reverse
        ),
        label = "PulseAnimation"
    ).value

    val rotationProgress = infiniteTransition.animateFloat(
        initialValue = 0f,
        targetValue = 360f,
        animationSpec = infiniteRepeatable(
            animation = tween(45000, easing = LinearEasing),
            repeatMode = RepeatMode.Restart
        ),
        label = "RotationAnimation"
    ).value

    val baseOrbColor = when (aiState) {
        AiState.PREPARING_STT, AiState.ACTIVATING -> LanternWarmWhite.copy(alpha = 0.5f)
        AiState.LISTENING -> LanternWarmWhite.copy(alpha = 0.65f)
        AiState.COMMAND_RECOGNIZED -> LanternGlowOrange.copy(alpha = 0.75f)
        AiState.PROCESSING -> LanternCoreYellow.copy(alpha = 0.6f)
        AiState.SPEAKING -> LanternCoreYellow.copy(alpha = 0.75f)
        AiState.ERROR -> Color(0xFFD32F2F).copy(alpha = 0.6f)
        else -> LanternWarmWhite.copy(alpha = 0.35f)
    }

    val orbColor = baseOrbColor.copy(alpha = baseOrbColor.alpha * calculatePulseAlpha(pulseProgress, aiState))

    val glowColor = orbColor.copy(alpha = orbColor.alpha * 0.35f)

    val particleCount = when (aiState) {
        AiState.SPEAKING, AiState.COMMAND_RECOGNIZED -> 12
        AiState.PROCESSING -> 10
        else -> 8
    }


    Canvas(modifier = modifier) {
        val center = this.center
        val radius = size.minDimension / 2f * 0.50f

        drawCircle(
            color = orbColor,
            radius = radius * pulseProgress,
            center = center
        )

        drawCircle(
            brush = Brush.radialGradient(
                colors = listOf(glowColor, Color.Transparent),
                center = center,
                radius = radius * (1.15f + 0.15f * pulseProgress)
            ),
            radius = radius * (1.15f + 0.15f * pulseProgress),
            center = center
        )
        if (aiState == AiState.SPEAKING || aiState == AiState.COMMAND_RECOGNIZED || aiState == AiState.PROCESSING || aiState == AiState.LISTENING) {
            for (i in 0 until particleCount) {
                val angleRad = ((rotationProgress * (if (i % 2 == 0) 0.9f else -0.7f) + i * 360f / particleCount) % 360f) * (PI / 180f).toFloat()
                val distance = radius * (1.15f + 0.12f * sin(i * 0.45f + pulseProgress * PI.toFloat()))
                val particleX = center.x + distance * cos(angleRad)
                val particleY = center.y + distance * sin(angleRad)
                val particleAlpha = 0.45f * (0.35f + 0.65f * sin(i * 0.25f + pulseProgress * PI.toFloat()))
                val particleColorVariation = Color.White.copy(alpha = particleAlpha.coerceIn(0.08f, 0.45f))
                val particleRadius = 0.7.dp.toPx() * (1f + (i % 2) * 0.4f)
                drawCircle(
                    color = particleColorVariation,
                    radius = particleRadius,
                    center = Offset(particleX, particleY)
                )
            }
        }
    }
}

private fun calculatePulseAlpha(pulseProgress: Float, aiState: AiState): Float { // 함수명 변경
    return when (aiState) {
        AiState.LISTENING, AiState.SPEAKING -> pulseProgress
        else -> 1.0f
    }
}


@Composable
fun DynamicStatusText(
    text: String,
    aiState: AiState,
    modifier: Modifier = Modifier
) {
    val infiniteTransition = rememberInfiniteTransition(label = "TextEffectTransition")

    val animatedTextColor by animateColorAsState(
        targetValue = when (aiState) {
            AiState.PREPARING_STT, AiState.ACTIVATING -> LanternWarmWhite.copy(alpha = 0.9f)
            AiState.LISTENING -> LanternWarmWhite
            AiState.COMMAND_RECOGNIZED -> LanternGlowOrange
            AiState.PROCESSING -> Color(0xFF81D4FA) // 밝은 블루
            AiState.SPEAKING -> LanternCoreYellow
            AiState.ERROR -> Color(0xFFEF9A9A) // 밝은 레드
            else -> Color.White.copy(alpha = 0.7f) // IDLE
        },
        animationSpec = tween(400, easing = FastOutSlowInEasing),
        label = "TextColorAnimation"
    )

    val glowAlpha by infiniteTransition.animateFloat(
        initialValue = 0.05f,
        targetValue = if (aiState == AiState.SPEAKING || aiState == AiState.COMMAND_RECOGNIZED || aiState == AiState.LISTENING) 0.25f else 0.05f,
        animationSpec = infiniteRepeatable(
            animation = tween(1100, easing = FastOutSlowInEasing),
            repeatMode = RepeatMode.Reverse
        ),
        label = "GlowAlphaAnimation"
    )

    val scale by animateFloatAsState(
        targetValue = if (aiState == AiState.SPEAKING) 1.05f else 1f,
        animationSpec = tween(durationMillis = 350, easing = FastOutSlowInEasing),
        label = "TextScale"
    )

    val floatOffset by animateFloatAsState(
        targetValue = when (aiState) {
            AiState.PREPARING_STT, AiState.ACTIVATING -> -2f
            AiState.LISTENING -> -3f
            AiState.SPEAKING -> 3f
            else -> 0f
        },
        animationSpec = infiniteRepeatable(
            animation = tween(1100, easing = FastOutSlowInEasing),
            repeatMode = RepeatMode.Reverse
        ),
        label = "FloatingAnimation"
    )

    val alpha by animateFloatAsState(
        targetValue = if (aiState == AiState.PROCESSING || aiState == AiState.PREPARING_STT || aiState == AiState.ACTIVATING) 0.7f else 1f,
        animationSpec = infiniteRepeatable(
            animation = tween(350, easing = LinearEasing),
            repeatMode = RepeatMode.Reverse
        ),
        label = "BlinkingAnimation"
    )

    val letterSpacingMultiplier by animateFloatAsState(
        targetValue = if (aiState == AiState.SPEAKING) 1.15f else 1f,
        animationSpec = infiniteRepeatable(
            animation = tween(700, easing = FastOutSlowInEasing),
            repeatMode = RepeatMode.Reverse
        ),
        label = "LetterSpacingAnimation"
    )

    val horizontalShake by animateFloatAsState(
        targetValue = if (aiState == AiState.ERROR) 1.5f else 0f,
        animationSpec = infiniteRepeatable(
            animation = tween(90, easing = LinearEasing),
            repeatMode = RepeatMode.Reverse
        ),
        label = "ShakeAnimation"
    )

    val baseTextStyle = TextStyle(
        fontSize = 26.sp,
        fontWeight = FontWeight.Normal,
        fontFamily = FontFamily.SansSerif,
        lineHeight = 34.sp,
        textAlign = TextAlign.Center,
        letterSpacing = if (aiState == AiState.SPEAKING) 0.15.sp * letterSpacingMultiplier else 0.15.sp
    )

    val displayText = text

    Box(
        modifier = modifier,
        contentAlignment = Alignment.Center
    ) {
        // TextStatusGlowEffectRender와 TextContentWithShadowRender는 이전 답변과 동일하게 유지
        TextStatusGlowEffectRender(aiState, animatedTextColor, glowAlpha)
        TextContentWithShadowRender(
            displayText = displayText,
            baseTextStyle = baseTextStyle,
            animatedTextColor = animatedTextColor,
            aiState = aiState,
            alpha = if (aiState == AiState.IDLE && displayText.isEmpty()) 0f else alpha, // IDLE이고 메시지 없으면 완전히 투명
            scale = scale,
            floatOffset = floatOffset,
            horizontalShake = horizontalShake
        )
    }
}


@Composable
private fun TextStatusGlowEffectRender( // 함수명 변경
    aiState: AiState,
    animatedTextColor: Color,
    glowAlpha: Float
) {
    Canvas(modifier = Modifier.fillMaxSize()) {
        if (aiState == AiState.SPEAKING || aiState == AiState.COMMAND_RECOGNIZED || aiState == AiState.LISTENING) {
            val currentGlowAlpha = if (aiState == AiState.LISTENING) glowAlpha * 0.5f else glowAlpha
            drawCircle(
                brush = Brush.radialGradient(
                    colors = listOf(
                        animatedTextColor.copy(alpha = currentGlowAlpha),
                        Color.Transparent
                    ),
                    radius = this.size.minDimension * 0.35f
                ),
                radius = this.size.minDimension * 0.35f,
                center = center.copy(y = center.y + size.height * 0.08f)
            )
        }
    }
}


@Composable
private fun TextContentWithShadowRender( // 함수명 변경
    displayText: String,
    baseTextStyle: TextStyle,
    animatedTextColor: Color,
    aiState: AiState,
    alpha: Float,
    scale: Float,
    floatOffset: Float,
    horizontalShake: Float
) {
    if (displayText.isNotEmpty()) {
        Text(
            text = displayText,
            style = baseTextStyle,
            color = Color.Black.copy(alpha = 0.25f),
            modifier = Modifier
                .offset(
                    x = (1.5.dp + if (aiState == AiState.ERROR) horizontalShake.dp else 0.dp),
                    y = 1.5.dp + floatOffset.dp
                )
                .alpha(alpha * 0.8f)
                .graphicsLayer {
                    scaleX = scale
                    scaleY = scale
                }
        )

        Text(
            text = displayText,
            style = baseTextStyle,
            color = animatedTextColor,
            modifier = Modifier
                .offset(
                    x = if (aiState == AiState.ERROR) horizontalShake.dp else 0.dp,
                    y = floatOffset.dp
                )
                .alpha(alpha)
                .graphicsLayer {
                    scaleX = scale
                    scaleY = scale
                    if (aiState == AiState.COMMAND_RECOGNIZED) {
                        rotationX = 1.5f * sin(System.currentTimeMillis() / 280f)
                    }
                }
        )

        if (aiState == AiState.SPEAKING) {
            val currentTime = System.currentTimeMillis()
            val glowFactor = (1f + sin(currentTime / 450f)) / 2f
            Text(
                text = displayText,
                style = baseTextStyle,
                color = animatedTextColor.copy(alpha = 0.25f * glowFactor),
                modifier = Modifier
                    .offset(y = floatOffset.dp)
                    .graphicsLayer(
                        scaleX = scale * 1.03f,
                        scaleY = scale * 1.03f,
                        alpha = 0.65f * alpha
                    )
            )
        }
    }
}

@Composable
fun OnDeviceAIScreen(
    modifier: Modifier = Modifier,
    onDismiss: () -> Unit = {},
    viewModel: OnDeviceAIViewModel = hiltViewModel()
) {
    val uiState by viewModel.uiState.collectAsState()
    val context = LocalContext.current
    val view = LocalView.current

    DisposableEffect(Unit) {
        val activity = view.context as? Activity ?: return@DisposableEffect onDispose {}
        val window = activity.window
        val originalStatusBarColor = window.statusBarColor
        val originalNavBarColor = window.navigationBarColor
        val decorFitsSystemWindows = WindowCompat.getInsetsController(window, view).isAppearanceLightStatusBars

        WindowCompat.setDecorFitsSystemWindows(window, false)
        window.statusBarColor = Color.Transparent.toArgb()
        window.navigationBarColor = Color.Transparent.toArgb()
        WindowCompat.getInsetsController(window, view).isAppearanceLightStatusBars = false
        WindowCompat.getInsetsController(window, view).isAppearanceLightNavigationBars = false

        val originalKeepScreenOn = window.attributes.flags and WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON != 0
        if (!originalKeepScreenOn) {
            window.addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
        }

        onDispose {
            window.statusBarColor = originalStatusBarColor
            window.navigationBarColor = originalNavBarColor
            WindowCompat.setDecorFitsSystemWindows(window, decorFitsSystemWindows)
            WindowCompat.getInsetsController(window, view).isAppearanceLightStatusBars = decorFitsSystemWindows
            WindowCompat.getInsetsController(window, view).isAppearanceLightNavigationBars = decorFitsSystemWindows

            if (window.attributes.flags and WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON != 0 && !originalKeepScreenOn) {
                window.clearFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
            }
        }
    }

    val permissionLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.RequestPermission()
    ) { isGranted ->
        if (isGranted) {
            Log.d("OnDeviceAIScreen", "마이크 권한 허용됨. AI 활성화 시도.")
            viewModel.activateAI()
        } else {
            Log.w("OnDeviceAIScreen", "마이크 권한 거부됨.")
            viewModel.showErrorAndPrepareToClose("음성 인식을 위해 마이크 권한이 필요합니다.")
            Toast.makeText(context, "마이크 권한이 거부되어 AI 기능을 사용할 수 없습니다.", Toast.LENGTH_LONG).show()
            onDismiss()
        }
    }

    val previousAiState = remember { mutableStateOf(AiState.IDLE) }

    LaunchedEffect(key1 = uiState.currentAiState) {
        if (uiState.currentAiState == AiState.IDLE && previousAiState.value != AiState.IDLE) {
            Log.d("OnDeviceAIScreen", "AI 상태가 IDLE로 '변경'됨. 화면 닫기 요청. 이전 상태: ${previousAiState.value}")
            onDismiss()
        }
        // ACTIVATING 상태는 ViewModel의 activateAI 내부에서 PREPARING_STT로 빠르게 전환되므로,
        // 여기서 ACTIVATING 상태를 기준으로 권한 체크를 다시 호출할 필요는 없을 수 있습니다.
        // 최초 진입 시 권한 체크 및 활성화는 아래 key1 = Unit LaunchedEffect에서 처리합니다.
        previousAiState.value = uiState.currentAiState
    }

    LaunchedEffect(key1 = Unit) {
        Log.d("OnDeviceAIScreen", "OnDeviceAIScreen 최초 실행. AI 활성화 로직 시작.")
        checkPermissionAndActivateAI(context, viewModel, permissionLauncher)
    }


    val infiniteTransition = rememberInfiniteTransition(label = "BackgroundTransition")
    val time = infiniteTransition.animateFloat(
        initialValue = 0f,
        targetValue = 1000f,
        animationSpec = infiniteRepeatable(
            animation = tween(1000 * 200, easing = LinearEasing),
            repeatMode = RepeatMode.Restart
        ),
        label = "TimeAnimation"
    ).value

    val backgroundColor by animateColorAsState(
        targetValue = when (uiState.currentAiState) {
            AiState.ACTIVATING, AiState.PREPARING_STT -> Color(0xFF051020)
            AiState.LISTENING -> Color(0xFF0A192F)
            AiState.COMMAND_RECOGNIZED -> Color(0xFF2C1D00)
            AiState.PROCESSING -> Color(0xFF001B2E)
            AiState.SPEAKING -> Color(0xFF1E1A00)
            AiState.ERROR -> Color(0xFF3B0000)
            else -> Color(0xFF010A13)
        },
        animationSpec = tween(700),
        label = "BackgroundColorAnimation"
    )

    val gradientColor by animateColorAsState(
        targetValue = when (uiState.currentAiState) {
            AiState.ACTIVATING, AiState.PREPARING_STT -> Color(0xFF102040)
            AiState.LISTENING -> Color(0xFF173A5E)
            AiState.COMMAND_RECOGNIZED -> Color(0xFF6F4200)
            AiState.PROCESSING -> Color(0xFF003052)
            AiState.SPEAKING -> Color(0xFF4A3F00)
            AiState.ERROR -> Color(0xFF6B0000)
            else -> Color(0xFF0A192F)
        },
        animationSpec = tween(700),
        label = "GradientColorAnimation"
    )

    val (particleDensity, particleSpeed, particleSize) = when (uiState.currentAiState) {
        AiState.ACTIVATING, AiState.PREPARING_STT -> Triple(15, 0.003f, 1.5f)
        AiState.LISTENING -> Triple(22, 0.0045f, 1.8f)
        AiState.COMMAND_RECOGNIZED -> Triple(30, 0.007f, 2.2f)
        AiState.PROCESSING -> Triple(28, 0.0055f, 1.7f)
        AiState.SPEAKING -> Triple(28, 0.0055f, 2.0f)
        else -> Triple(18, 0.0035f, 1.6f)
    }

    val primaryParticleColor = when (uiState.currentAiState) {
        AiState.ACTIVATING, AiState.PREPARING_STT -> Color.White.copy(alpha = 0.25f)
        AiState.LISTENING -> Color.White.copy(alpha = 0.35f)
        AiState.COMMAND_RECOGNIZED -> LanternGlowOrange.copy(alpha = 0.45f)
        AiState.SPEAKING -> LanternCoreYellow.copy(alpha = 0.45f)
        AiState.ERROR -> Color(0xFFF48FB1).copy(alpha = 0.3f)
        else -> Color.White.copy(alpha = 0.2f)
    }

    val secondaryParticleColor = when (uiState.currentAiState) {
        AiState.COMMAND_RECOGNIZED -> LanternCoreYellow.copy(alpha = 0.25f)
        AiState.SPEAKING -> LanternWarmWhite.copy(alpha = 0.25f)
        else -> LanternGlowOrange.copy(alpha = 0.15f)
    }

    Box(
        modifier = modifier
            .fillMaxSize()
            .background(Color.Transparent),
        contentAlignment = Alignment.Center
    ) {
        // BackgroundEffects 함수를 호출하도록 수정
        BackgroundEffectsRender( // 함수명 변경
            time = time,
            backgroundColor = backgroundColor,
            gradientColor = gradientColor,
            aiState = uiState.currentAiState,
            particleDensity = particleDensity,
            particleSpeed = particleSpeed,
            particleSize = particleSize,
            primaryParticleColor = primaryParticleColor,
            secondaryParticleColor = secondaryParticleColor
        )

        val closeButtonVisible = uiState.currentAiState != AiState.PROCESSING && uiState.currentAiState != AiState.ACTIVATING

        Box(
            modifier = Modifier
                .fillMaxWidth()
                .aspectRatio(1f)
                .align(Alignment.Center),
            contentAlignment = Alignment.Center
        ) {
            VoiceWaveEffect(
                modifier = Modifier.fillMaxSize(0.80f),
                isListening = uiState.currentAiState == AiState.LISTENING || uiState.currentAiState == AiState.PREPARING_STT
            )
            LanternOrbEffect(
                modifier = Modifier.fillMaxSize(0.60f),
                aiState = uiState.currentAiState
            )
        }

        DynamicStatusText(
            text = uiState.statusMessage, // ViewModel의 통합된 statusMessage 사용
            aiState = uiState.currentAiState,
            modifier = Modifier
                .align(Alignment.BottomCenter)
                .navigationBarsPadding()
                .imePadding()
                .padding(bottom = 32.dp, start = 24.dp, end = 24.dp)
        )
    }
}

// BackgroundEffects 함수 정의 (이전에 제공된 코드를 기반으로 하되, Unresolved reference를 피하기 위해 Render 접미사 추가)
@Composable
private fun BackgroundEffectsRender(
    time: Float,
    backgroundColor: Color,
    gradientColor: Color,
    aiState: AiState,
    particleDensity: Int,
    particleSpeed: Float,
    particleSize: Float,
    primaryParticleColor: Color,
    secondaryParticleColor: Color
) {
    val perlinNoise = remember(aiState) { SimplePerlinNoise() }

    Canvas(modifier = Modifier.fillMaxSize()) {
        drawRect(
            brush = Brush.radialGradient(
                colors = listOf(
                    gradientColor.copy(alpha = 0.5f),
                    backgroundColor.copy(alpha = 0.7f)
                ),
                center = center,
                radius = size.maxDimension * 1.2f
            ),
            size = size
        )

        drawParticleEffectsRender( // 함수명 변경
            aiState = aiState,
            time = time,
            particleDensity = particleDensity,
            particleSpeed = particleSpeed,
            particleSize = particleSize,
            primaryParticleColor = primaryParticleColor,
            secondaryParticleColor = secondaryParticleColor,
            perlinNoise = perlinNoise
        )
    }
}

// drawParticleEffects 함수 정의 (이전에 제공된 코드를 기반으로 하되, Unresolved reference를 피하기 위해 Render 접미사 추가)
private fun DrawScope.drawParticleEffectsRender( // 함수명 변경
    aiState: AiState,
    time: Float,
    particleDensity: Int,
    particleSpeed: Float,
    particleSize: Float,
    primaryParticleColor: Color,
    secondaryParticleColor: Color,
    perlinNoise: SimplePerlinNoise
) {
    val numParticles = particleDensity

    for (i in 0 until numParticles) {
        val particleX = size.width * ((sin(i * 0.05f + time * particleSpeed * 0.7f + i * 0.1f) + 1f) / 2f)
        val particleY = size.height * ((cos(i * 0.05f + time * particleSpeed * 0.5f + i * 0.15f) + 1f) / 2f)
        val noiseValue = if (i % 4 == 0) {
            perlinNoise.noise2D(time * 0.003f + i * 0.02f, i * 0.1f)
        } else {
            0.6f
        }
        val sizeFactor = (particleSize - 0.5f) * noiseValue + 0.5f
        val particleSizePx = sizeFactor * 0.8.dp.toPx()
        val brightness = when (aiState) {
            AiState.COMMAND_RECOGNIZED -> 0.8f
            AiState.SPEAKING -> 0.7f
            else -> 0.6f
        }
        val particleColor = when {
            i % 6 == 0 -> primaryParticleColor.copy(alpha = primaryParticleColor.alpha * brightness * 0.8f)
            i % 4 == 0 -> secondaryParticleColor.copy(alpha = secondaryParticleColor.alpha * brightness * 0.7f)
            else -> Color.White.copy(alpha = 0.2f * brightness)
        }
        drawCircle(
            color = particleColor,
            radius = particleSizePx,
            center = Offset(particleX, particleY)
        )
        if (aiState == AiState.COMMAND_RECOGNIZED && i % 15 == 0) {
            drawParticleTrailRender( // 함수명 변경
                particleX = particleX,
                particleY = particleY,
                particleSizePx = particleSizePx * 0.8f,
                particleColor = particleColor.copy(alpha = particleColor.alpha * 0.5f),
                particleSpeed = particleSpeed * 0.8f,
                time = time,
                index = i,
                perlinNoise = perlinNoise
            )
        }
        if (aiState == AiState.SPEAKING && i % 20 == 0) {
            drawCircle(
                color = particleColor.copy(alpha = particleColor.alpha * 0.15f),
                radius = particleSizePx * 2f,
                center = Offset(particleX, particleY)
            )
        }
    }
}

// drawParticleTrail 함수 정의 (이전에 제공된 코드를 기반으로 하되, Unresolved reference를 피하기 위해 Render 접미사 추가)
private fun DrawScope.drawParticleTrailRender( // 함수명 변경
    particleX: Float,
    particleY: Float,
    particleSizePx: Float,
    particleColor: Color,
    particleSpeed: Float,
    time: Float,
    index: Int,
    perlinNoise: SimplePerlinNoise
) {
    val trailPoints = 3
    for (t in 0 until trailPoints) {
        val trailOffsetFactor = t * 0.1f * (1f + 0.2f * sin(time * 0.1f + index * 0.3f))
        val trailX = particleX - (sin(index * 0.1f + time * particleSpeed * 1.2f) * trailOffsetFactor * 50f)
        val trailY = particleY - (cos(index * 0.1f + time * particleSpeed * 1.2f) * trailOffsetFactor * 50f)
        val trailAlpha = 0.5f * (1f - (t.toFloat() / trailPoints)) * (0.8f + 0.2f * perlinNoise.noise2D(time*0.01f + index*0.05f + t*0.1f, particleX*0.01f))
        val particleColorVariation = when (t) {
            0 -> particleColor.copy(alpha = particleColor.alpha * trailAlpha.coerceIn(0.1f, 0.8f))
            else -> LanternGlowOrange.copy(alpha = trailAlpha.coerceIn(0.05f, 0.5f))
        }
        val trailSize = particleSizePx * (1f - (t.toFloat() / (trailPoints + 1))) * (0.9f + 0.2f * cos(time * 0.05f + index * 0.2f + t*0.15f))
        drawCircle(
            color = particleColorVariation,
            radius = trailSize.coerceAtLeast(0.5.dp.toPx()),
            center = Offset(trailX, trailY)
        )
    }
}


private fun checkPermissionAndActivateAI(
    context: Context,
    viewModel: OnDeviceAIViewModel,
    permissionLauncher: ActivityResultLauncher<String>
) {
    Log.d("OnDeviceAIScreen", "checkPermissionAndActivateAI 호출됨.")
    when {
        ContextCompat.checkSelfPermission(
            context,
            Manifest.permission.RECORD_AUDIO
        ) == PackageManager.PERMISSION_GRANTED -> {
            Log.d("OnDeviceAIScreen", "마이크 권한 이미 허용됨. viewModel.activateAI() 호출.")
            viewModel.activateAI()
        }
        else -> {
            Log.d("OnDeviceAIScreen", "마이크 권한 요청 실행.")
            permissionLauncher.launch(Manifest.permission.RECORD_AUDIO)
        }
    }
}

@Composable
fun OnDeviceAIDialog(
    onDismiss: () -> Unit,
    viewModel: OnDeviceAIViewModel = hiltViewModel()
) {
    Dialog(
        onDismissRequest = {
            Log.d("OnDeviceAIDialog", "Dialog onDismissRequest 호출됨. viewModel.deactivateAI() 실행.")
            viewModel.deactivateAI()
            onDismiss()
        },
        properties = DialogProperties(
            usePlatformDefaultWidth = false,
            decorFitsSystemWindows = false,
            dismissOnBackPress = true,
            dismissOnClickOutside = true
        )
    ) {
        OnDeviceAIScreen(
            modifier = Modifier.fillMaxSize(),
            onDismiss = {
                Log.d("OnDeviceAIDialog", "OnDeviceAIScreen의 onDismiss 콜백 호출됨.")
                viewModel.deactivateAI()
                onDismiss()
            },
            viewModel = viewModel
        )
    }
}