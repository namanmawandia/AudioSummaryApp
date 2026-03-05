package com.example.audiosummeryapp

import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Mic
import androidx.compose.material.icons.filled.Pause
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Stop
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.IconButtonDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.scale
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.example.audiosummeryapp.ui.theme.AccentRed
import com.example.audiosummeryapp.ui.theme.AccentRedDim
import com.example.audiosummeryapp.ui.theme.BackgroundDark
import com.example.audiosummeryapp.ui.theme.SurfaceDark
import com.example.audiosummeryapp.ui.theme.TextPrimary
import com.example.audiosummeryapp.ui.theme.TextSecondary
import com.example.audiosummeryapp.ui.theme.WaveformColor
import kotlinx.coroutines.delay
import kotlin.random.Random




@Composable
fun RecordingScreen(
    viewModel: RecordingViewModel = hiltViewModel(),
    onNavigateToDashboard: () -> Unit = {}
) {
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()

    // Simulate amplitude ticks while recording (replace with real AudioRecord data later)
    LaunchedEffect(uiState.status) {
        if (uiState.status == RecordingStatus.RECORDING) {
            while (true) {
                viewModel.updateAmplitude(Random.nextFloat())
                delay(120)
            }
        } else {
            viewModel.updateAmplitude(0f)
        }
    }

    RecordingContent(
        uiState    = uiState,
        onRecord   = { viewModel.startRecording() },
        onPause    = { viewModel.pauseRecording() },
        onResume   = { viewModel.resumeRecording() },
        onStop     = {
            viewModel.stopRecording()
            onNavigateToDashboard()
        }
    )
}

@Composable
private fun RecordingContent(
    uiState  : RecordingUiState,
    onRecord : () -> Unit,
    onPause  : () -> Unit,
    onResume : () -> Unit,
    onStop   : () -> Unit,
) {
    Scaffold(
        containerColor = BackgroundDark
    ) { innerPadding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding)
                .padding(horizontal = 24.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.SpaceBetween
        ) {
            TopBar()

            Column(
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                StatusBadge(uiState.status, uiState.statusMessage)
                Spacer(Modifier.height(32.dp))
                TimerDisplay(uiState.elapsedSeconds)
                Spacer(Modifier.height(40.dp))
                WaveformVisualizer(
                    amplitudeLevel = uiState.amplitudeLevel,
                    isActive       = uiState.status == RecordingStatus.RECORDING
                )
            }

            ControlBar(
                status = uiState.status,
                onRecord = onRecord,
                onPause = onPause,
                onResume = onResume,
                onStop = onStop,
                modifier = Modifier.padding(bottom = 48.dp)
            )
        }
    }
}

@Composable
private fun TopBar() {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(top = 20.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Icon(
            imageVector        = Icons.Default.Mic,
            contentDescription = null,
            tint               = AccentRed,
            modifier           = Modifier.size(20.dp)
        )
        Text(
            text      = " Audio Summary App",
            color     = TextPrimary,
            fontSize  = 18.sp,
            fontWeight = FontWeight.SemiBold
        )
    }
}

@Composable
private fun StatusBadge(status: RecordingStatus, message: String) {
    val badgeColor by animateColorAsState(
        targetValue = when (status) {
            RecordingStatus.RECORDING -> AccentRed.copy(alpha = 0.15f)
            RecordingStatus.PAUSED    -> Color(0xFFFFA500).copy(alpha = 0.15f)
            else                      -> SurfaceDark
        },
        label = "badgeColor"
    )
    val textColor by animateColorAsState(
        targetValue = when (status) {
            RecordingStatus.RECORDING -> AccentRed
            RecordingStatus.PAUSED    -> Color(0xFFFFA500)
            else                      -> TextSecondary
        },
        label = "textColor"
    )

    // Blinking dot when recording
    val infiniteTransition = rememberInfiniteTransition(label = "blink")
    val dotAlpha by infiniteTransition.animateFloat(
        initialValue = if (status == RecordingStatus.RECORDING) 1f else 0f,
        targetValue  = if (status == RecordingStatus.RECORDING) 0f else 0f,
        animationSpec = infiniteRepeatable(
            animation  = tween(700, easing = LinearEasing),
            repeatMode = RepeatMode.Reverse
        ),
        label = "dotAlpha"
    )

    Surface(
        color  = badgeColor,
        shape  = MaterialTheme.shapes.small,
    ) {
        Row(
            modifier            = Modifier.padding(horizontal = 14.dp, vertical = 6.dp),
            verticalAlignment   = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(6.dp)
        ) {
            if (status == RecordingStatus.RECORDING) {
                Box(
                    modifier = Modifier
                        .size(7.dp)
                        .background(AccentRed.copy(alpha = 1f - dotAlpha), CircleShape)
                )
            }
            Text(
                text     = message,
                color    = textColor,
                fontSize = 13.sp,
                fontWeight = FontWeight.Medium
            )
        }
    }
}

@Composable
private fun TimerDisplay(elapsedSeconds: Int) {
    val hours   = elapsedSeconds / 3600
    val minutes = (elapsedSeconds % 3600) / 60
    val seconds = elapsedSeconds % 60

    val timeString = if (hours > 0) {
        "%02d:%02d:%02d".format(hours, minutes, seconds)
    } else {
        "%02d:%02d".format(minutes, seconds)
    }

    Text(
        text       = timeString,
        color      = TextPrimary,
        fontSize   = 64.sp,
        fontWeight = FontWeight.Thin,
        fontFamily = FontFamily.Monospace,
        letterSpacing = 4.sp
    )
}

@Composable
private fun WaveformVisualizer(
    amplitudeLevel: Float,
    isActive: Boolean
) {
    val barCount = 32
    // Hold a list of recent amplitude values to scroll the waveform
    val amplitudes = remember { ArrayDeque<Float>(barCount).also { dq -> repeat(barCount) { dq.add(0f) } } }

    LaunchedEffect(amplitudeLevel) {
        if (amplitudes.size >= barCount) amplitudes.removeFirst()
        amplitudes.addLast(amplitudeLevel)
    }

    Row(
        modifier              = Modifier
            .fillMaxWidth()
            .height(80.dp),
        horizontalArrangement = Arrangement.spacedBy(3.dp, Alignment.CenterHorizontally),
        verticalAlignment     = Alignment.CenterVertically
    ) {
        amplitudes.forEachIndexed { index, amp ->
            val targetHeight by animateFloatAsState(
                targetValue   = if (isActive) (amp * 72f).coerceIn(6f, 72f) else 6f,
                animationSpec = tween(100),
                label         = "bar$index"
            )
            Box(
                modifier = Modifier
                    .size(width = 3.dp, height = targetHeight.dp)
                    .background(
                        color = WaveformColor.copy(alpha = 0.4f + amp * 0.6f),
                        shape = CircleShape
                    )
            )
        }
    }
}

@Composable
private fun ControlBar(
    status: RecordingStatus,
    onRecord: () -> Unit,
    onPause: () -> Unit,
    onResume: () -> Unit,
    onStop: () -> Unit,
    modifier: Modifier = Modifier
) {
    Row(
        modifier              = modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.Center,
        verticalAlignment     = Alignment.CenterVertically
    ) {
        when (status) {

            RecordingStatus.IDLE -> {
                PulsingRecordButton(onClick = onRecord)
            }

            RecordingStatus.RECORDING -> {
                Row(
                    horizontalArrangement = Arrangement.spacedBy(32.dp),
                    verticalAlignment     = Alignment.CenterVertically
                ) {
                    // Stop button (smaller, outlined)
                    ActionButton(
                        icon            = Icons.Default.Stop,
                        contentDesc     = "Stop",
                        containerColor  = SurfaceDark,
                        iconTint        = TextSecondary,
                        size            = 56.dp,
                        onClick         = onStop
                    )
                    // Pause button (primary)
                    ActionButton(
                        icon            = Icons.Default.Pause,
                        contentDesc     = "Pause",
                        containerColor  = AccentRedDim,
                        iconTint        = AccentRed,
                        size            = 72.dp,
                        onClick         = onPause
                    )
                }
            }

            RecordingStatus.PAUSED -> {
                Row(
                    horizontalArrangement = Arrangement.spacedBy(32.dp),
                    verticalAlignment     = Alignment.CenterVertically
                ) {
                    ActionButton(
                        icon           = Icons.Default.Stop,
                        contentDesc    = "Stop",
                        containerColor = SurfaceDark,
                        iconTint       = TextSecondary,
                        size           = 56.dp,
                        onClick        = onStop
                    )
                    ActionButton(
                        icon           = Icons.Default.PlayArrow,
                        contentDesc    = "Resume",
                        containerColor = Color(0xFF1A3A1A),
                        iconTint       = Color(0xFF4CAF50),
                        size           = 72.dp,
                        onClick        = onResume
                    )
                }
            }

            // Stopped: nothing navigate away
            RecordingStatus.STOPPED -> { /* navigating away */ }
        }
    }
}

@Composable
private fun PulsingRecordButton(onClick: () -> Unit) {
    val infiniteTransition = rememberInfiniteTransition(label = "pulse")
    val scale by infiniteTransition.animateFloat(
        initialValue  = 1f,
        targetValue   = 1.08f,
        animationSpec = infiniteRepeatable(
            animation  = tween(900, easing = LinearEasing),
            repeatMode = RepeatMode.Reverse
        ),
        label = "pulseScale"
    )

    Box(contentAlignment = Alignment.Center) {
        // Outer pulse ring
        Box(
            modifier = Modifier
                .size(100.dp)
                .scale(scale)
                .background(AccentRed.copy(alpha = 0.15f), CircleShape)
        )
        // Inner button
        IconButton(
            onClick = onClick,
            modifier = Modifier.size(80.dp),
            colors = IconButtonDefaults.iconButtonColors(
                containerColor = AccentRed
            )
        ) {
            Icon(
                imageVector        = Icons.Default.Mic,
                contentDescription = "Start Recording",
                tint               = Color.White,
                modifier           = Modifier.size(36.dp)
            )
        }
    }
}

@Composable
private fun ActionButton(
    icon           : ImageVector,
    contentDesc    : String,
    containerColor : Color,
    iconTint       : Color,
    size           : Dp,
    onClick        : () -> Unit
) {
    IconButton(
        onClick  = onClick,
        modifier = Modifier.size(size),
        colors   = IconButtonDefaults.iconButtonColors(containerColor = containerColor)
    ) {
        Icon(
            imageVector        = icon,
            contentDescription = contentDesc,
            tint               = iconTint,
            modifier           = Modifier.size(size * 0.45f)
        )
    }
}

// Preview -------

@Preview(showBackground = true, backgroundColor = 0xFF0D0D0D)
@Composable
private fun RecordingIdlePreview() {
    RecordingContent(
        uiState  = RecordingUiState(status = RecordingStatus.IDLE),
        onRecord = {}, onPause = {}, onResume = {}, onStop = {}
    )
}

@Preview(showBackground = true, backgroundColor = 0xFF0D0D0D)
@Composable
private fun RecordingActivePreview() {
    RecordingContent(
        uiState  = RecordingUiState(
            status         = RecordingStatus.RECORDING,
            elapsedSeconds = 125,
            statusMessage  = "Recording...",
            amplitudeLevel = 0.6f
        ),
        onRecord = {}, onPause = {}, onResume = {}, onStop = {}
    )
}

@Preview(showBackground = true, backgroundColor = 0xFF0D0D0D)
@Composable
private fun RecordingPausedPreview() {
    RecordingContent(
        uiState  = RecordingUiState(
            status        = RecordingStatus.PAUSED,
            elapsedSeconds = 125,
            statusMessage = "Paused"
        ),
        onRecord = {}, onPause = {}, onResume = {}, onStop = {}
    )
}