package com.example.audiosummeryapp.ui.session

import androidx.compose.animation.animateContentSize
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material3.*
import androidx.compose.material3.TabRowDefaults.tabIndicatorOffset
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.example.audiosummeryapp.ui.theme.AccentRed
import com.example.audiosummeryapp.ui.theme.BackgroundDark
import com.example.audiosummeryapp.ui.theme.SurfaceDark
import com.example.audiosummeryapp.ui.theme.TextPrimary
import com.example.audiosummeryapp.ui.theme.TextSecondary

private val Green = Color(0xFF4CAF50)
private val Orange = Color(0xFFFFA726)

// ─── Entry point ─────────────────────────────────────────────────────────────

@Composable
fun SessionDetailScreen(
    viewModel: SessionDetailViewModel = hiltViewModel(),
    onBack   : () -> Unit = {}
) {
    val session         by viewModel.session.collectAsStateWithLifecycle()
    val transcriptState by viewModel.transcriptState.collectAsStateWithLifecycle()
    val summaryState    by viewModel.summaryState.collectAsStateWithLifecycle()

    var selectedTab by remember { mutableIntStateOf(0) }

    // When user switches to Summary tab for first time → trigger generation
    LaunchedEffect(selectedTab) {
        if (selectedTab == 1) viewModel.generateSummary()
    }

    Scaffold(
        containerColor = BackgroundDark,
        topBar = {
            DetailTopBar(
                title  = session?.displayName ?: "Recording",
                onBack = onBack
            )
        }
    ) { innerPadding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding)
        ) {
            // Tab row
            TabRow(
                selectedTabIndex = selectedTab,
                containerColor   = BackgroundDark,
                contentColor     = AccentRed,
                indicator        = { tabPositions ->
                    TabRowDefaults.SecondaryIndicator(
                        modifier = Modifier.tabIndicatorOffset(tabPositions[selectedTab]),
                        color    = AccentRed
                    )
                }
            ) {
                Tab(
                    selected = selectedTab == 0,
                    onClick  = { selectedTab = 0 },
                    text = {
                        Text(
                            "Transcript",
                            color    = if (selectedTab == 0) AccentRed else TextSecondary,
                            fontSize = 14.sp,
                            fontWeight = FontWeight.Medium
                        )
                    }
                )
                Tab(
                    selected = selectedTab == 1,
                    onClick  = { selectedTab = 1 },
                    text = {
                        Text(
                            "Summary",
                            color    = if (selectedTab == 1) AccentRed else TextSecondary,
                            fontSize = 14.sp,
                            fontWeight = FontWeight.Medium
                        )
                    }
                )
            }

            // Tab content
            when (selectedTab) {
                0 -> TranscriptTab(
                    state   = transcriptState,
                    onRetry = { viewModel.retryTranscription() }
                )
                1 -> SummaryTab(
                    state   = summaryState,
                    onRetry = { viewModel.generateSummary(forceRegenerate = true) }
                )
            }
        }
    }
}

// ─── Top Bar ──────────────────────────────────────────────────────────────────

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun DetailTopBar(title: String, onBack: () -> Unit) {
    TopAppBar(
        title = {
            Text(
                text       = title,
                color      = TextPrimary,
                fontSize   = 17.sp,
                fontWeight = FontWeight.SemiBold,
                maxLines   = 1
            )
        },
        navigationIcon = {
            IconButton(onClick = onBack) {
                Icon(
                    imageVector        = Icons.Default.ArrowBack,
                    contentDescription = "Back",
                    tint               = TextPrimary
                )
            }
        },
        colors = TopAppBarDefaults.topAppBarColors(containerColor = BackgroundDark)
    )
}

// ─── Transcript Tab ───────────────────────────────────────────────────────────

@Composable
private fun TranscriptTab(
    state  : TranscriptUiState,
    onRetry: () -> Unit
) {
    Box(
        modifier         = Modifier.fillMaxSize(),
        contentAlignment = Alignment.Center
    ) {
        when (state) {
            is TranscriptUiState.Loading -> {
                Column(
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.spacedBy(16.dp)
                ) {
                    CircularProgressIndicator(color = AccentRed, strokeWidth = 2.dp)
                    Text("Transcribing audio...", color = TextSecondary, fontSize = 14.sp)
                }
            }

            is TranscriptUiState.Empty -> {
                Column(
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.spacedBy(12.dp),
                    modifier = Modifier.padding(32.dp)
                ) {
                    CircularProgressIndicator(color = Orange, strokeWidth = 2.dp)
                    Text(
                        text      = "Transcription in progress",
                        color     = TextPrimary,
                        fontSize  = 15.sp,
                        fontWeight = FontWeight.Medium
                    )
                    Text(
                        text      = "This happens automatically in the background. Check back shortly.",
                        color     = TextSecondary,
                        fontSize  = 13.sp,
                        textAlign = TextAlign.Center
                    )
                }
            }

            is TranscriptUiState.Error -> {
                Column(
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.spacedBy(16.dp),
                    modifier = Modifier.padding(32.dp)
                ) {
                    Text(
                        text      = "Transcription failed",
                        color     = TextPrimary,
                        fontSize  = 16.sp,
                        fontWeight = FontWeight.SemiBold
                    )
                    Text(
                        text      = state.message,
                        color     = TextSecondary,
                        fontSize  = 13.sp,
                        textAlign = TextAlign.Center
                    )
                    RetryButton(onClick = onRetry)
                }
            }

            is TranscriptUiState.Ready -> {
                Column(
                    modifier = Modifier.fillMaxSize()
                ) {
                    // Scrollable transcript text
                    Box(
                        modifier = Modifier
                            .weight(1f)
                            .padding(horizontal = 20.dp, vertical = 16.dp)
                            .verticalScroll(rememberScrollState())
                    ) {
                        Text(
                            text      = state.text,
                            color     = TextPrimary,
                            fontSize  = 15.sp,
                            lineHeight = 24.sp
                        )
                    }

                    // Retry bar at bottom
                    Surface(color = SurfaceDark) {
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(horizontal = 20.dp, vertical = 10.dp),
                            verticalAlignment     = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.SpaceBetween
                        ) {
                            Text(
                                text     = "Not accurate? Re-transcribe",
                                color    = TextSecondary,
                                fontSize = 13.sp
                            )
                            IconButton(onClick = onRetry, modifier = Modifier.size(36.dp)) {
                                Icon(
                                    imageVector        = Icons.Default.Refresh,
                                    contentDescription = "Re-transcribe",
                                    tint               = AccentRed,
                                    modifier           = Modifier.size(18.dp)
                                )
                            }
                        }
                    }
                }
            }
        }
    }
}

// ─── Summary Tab ──────────────────────────────────────────────────────────────

@Composable
private fun SummaryTab(
    state  : SummaryUiState,
    onRetry: () -> Unit
) {
    Box(
        modifier         = Modifier.fillMaxSize(),
        contentAlignment = Alignment.Center
    ) {
        when (state) {
            is SummaryUiState.Idle -> {
                // Will trigger generation via LaunchedEffect — show spinner
                Column(
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.spacedBy(16.dp)
                ) {
                    CircularProgressIndicator(color = AccentRed, strokeWidth = 2.dp)
                    Text("Preparing summary...", color = TextSecondary, fontSize = 14.sp)
                }
            }

            is SummaryUiState.Generating -> {
                Column(
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.spacedBy(16.dp)
                ) {
                    CircularProgressIndicator(color = AccentRed, strokeWidth = 2.dp)
                    Text("Generating summary...", color = TextSecondary, fontSize = 14.sp)
                }
            }

            is SummaryUiState.Streaming -> {
                Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(20.dp)
                        .verticalScroll(rememberScrollState())
                ) {
                    Text(
                        text      = state.partialText,
                        color     = TextPrimary,
                        fontSize  = 15.sp,
                        lineHeight = 24.sp,
                        modifier  = Modifier.animateContentSize()
                    )
                }
            }

            is SummaryUiState.Error -> {
                Column(
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.spacedBy(16.dp),
                    modifier = Modifier.padding(32.dp)
                ) {
                    Text(
                        text       = "Summary generation failed",
                        color      = TextPrimary,
                        fontSize   = 16.sp,
                        fontWeight = FontWeight.SemiBold
                    )
                    Text(
                        text      = state.message,
                        color     = TextSecondary,
                        fontSize  = 13.sp,
                        textAlign = TextAlign.Center
                    )
                    RetryButton(onClick = onRetry)
                }
            }

            is SummaryUiState.Ready -> {
                Column(
                    modifier = Modifier
                        .fillMaxSize()
                        .verticalScroll(rememberScrollState())
                        .padding(horizontal = 20.dp, vertical = 16.dp),
                    verticalArrangement = Arrangement.spacedBy(16.dp)
                ) {
                    // Title
                    Text(
                        text       = state.data.title,
                        color      = TextPrimary,
                        fontSize   = 20.sp,
                        fontWeight = FontWeight.Bold
                    )

                    // Summary paragraph
                    SummarySection(title = "Summary") {
                        Text(
                            text      = state.data.summary,
                            color     = TextPrimary,
                            fontSize  = 15.sp,
                            lineHeight = 24.sp
                        )
                    }

                    // Action Items
                    if (state.data.actionItems.isNotEmpty()) {
                        SummarySection(title = "Action Items") {
                            state.data.actionItems.forEach { item ->
                                BulletRow(text = item, bulletColor = AccentRed)
                            }
                        }
                    }

                    // Key Points
                    if (state.data.keyPoints.isNotEmpty()) {
                        SummarySection(title = "Key Points") {
                            state.data.keyPoints.forEach { point ->
                                BulletRow(text = point, bulletColor = Green)
                            }
                        }
                    }

                    Spacer(Modifier.height(8.dp))

                    // Regenerate button at bottom
                    OutlinedButton(
                        onClick  = onRetry,
                        modifier = Modifier.fillMaxWidth(),
                        colors   = ButtonDefaults.outlinedButtonColors(contentColor = AccentRed),
                        border   = androidx.compose.foundation.BorderStroke(1.dp, AccentRed.copy(alpha = 0.5f))
                    ) {
                        Icon(
                            Icons.Default.Refresh, null,
                            modifier = Modifier.size(16.dp)
                        )
                        Spacer(Modifier.width(8.dp))
                        Text("Regenerate Summary", fontSize = 14.sp)
                    }

                    Spacer(Modifier.height(32.dp))
                }
            }
        }
    }
}

// ─── Reusable components ──────────────────────────────────────────────────────

@Composable
private fun SummarySection(
    title  : String,
    content: @Composable ColumnScope.() -> Unit
) {
    Surface(
        color = SurfaceDark,
        shape = RoundedCornerShape(12.dp)
    ) {
        Column(
            modifier = Modifier.padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            Text(
                text       = title,
                color      = AccentRed,
                fontSize   = 12.sp,
                fontWeight = FontWeight.SemiBold,
                letterSpacing = 0.8.sp
            )
            content()
        }
    }
}

@Composable
private fun BulletRow(text: String, bulletColor: Color) {
    Row(
        verticalAlignment    = Alignment.Top,
        horizontalArrangement = Arrangement.spacedBy(10.dp)
    ) {
        Box(
            modifier = Modifier
                .padding(top = 7.dp)
                .size(6.dp)
                .background(bulletColor, CircleShape)
        )
        Text(
            text      = text,
            color     = TextPrimary,
            fontSize  = 14.sp,
            lineHeight = 22.sp,
            modifier  = Modifier.weight(1f)
        )
    }
}

@Composable
private fun RetryButton(onClick: () -> Unit) {
    Button(
        onClick = onClick,
        colors  = ButtonDefaults.buttonColors(containerColor = AccentRed)
    ) {
        Icon(Icons.Default.Refresh, null, modifier = Modifier.size(16.dp))
        Spacer(Modifier.width(8.dp))
        Text("Retry")
    }
}