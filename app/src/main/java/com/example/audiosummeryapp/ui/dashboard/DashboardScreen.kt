package com.example.audiosummeryapp.ui.dashboard

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.slideInVertically
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Mic
import androidx.compose.material.icons.filled.MicNone
import androidx.compose.material.icons.outlined.AudioFile
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.example.audiosummeryapp.model.RecordingSession
import com.example.audiosummeryapp.ui.theme.AccentRed
import com.example.audiosummeryapp.ui.theme.BackgroundDark
import com.example.audiosummeryapp.ui.theme.SurfaceDark
import com.example.audiosummeryapp.ui.theme.TextPrimary
import com.example.audiosummeryapp.ui.theme.TextSecondary
import java.io.File

// Entry point
@Composable
fun DashboardScreen(
    viewModel          : DashboardViewModel = hiltViewModel(),
    onSessionClick     : (RecordingSession) -> Unit = {},
    onNewRecordingClick: () -> Unit = {}
) {
    val sessions by viewModel.sessions.collectAsStateWithLifecycle()

    DashboardContent(
        sessions           = sessions,
        onSessionClick     = onSessionClick,
        onNewRecordingClick = onNewRecordingClick
    )
}


@Composable
private fun DashboardContent(
    sessions           : List<RecordingSession>,
    onSessionClick     : (RecordingSession) -> Unit,
    onNewRecordingClick: () -> Unit
) {
    Scaffold(
        containerColor = BackgroundDark,
        floatingActionButton = {
            FloatingActionButton(
                onClick          = onNewRecordingClick,
                containerColor   = AccentRed,
                contentColor     = Color.White,
                shape            = CircleShape
            ) {
                Icon(
                    imageVector        = Icons.Default.Mic,
                    contentDescription = "New Recording",
                    modifier           = Modifier.size(26.dp)
                )
            }
        }
    ) { innerPadding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding)
                .padding(horizontal = 20.dp)
        ) {
            Spacer(Modifier.height(24.dp))
            Text(
                text       = "Recordings",
                color      = TextPrimary,
                fontSize   = 26.sp,
                fontWeight = FontWeight.Bold
            )
            Text(
                text     = "${sessions.size} completed",
                color    = TextSecondary,
                fontSize = 13.sp,
                modifier = Modifier.padding(top = 2.dp, bottom = 20.dp)
            )

            if (sessions.isEmpty()) {
                EmptyState()
            } else {
                LazyColumn(
                    verticalArrangement = Arrangement.spacedBy(12.dp),
                    contentPadding      = PaddingValues(bottom = 100.dp)
                ) {
                    itemsIndexed(sessions, key = { _, s -> s.id }) { index, session ->
                        AnimatedVisibility(
                            visible       = true,
                            enter         = fadeIn() + slideInVertically { it / 4 }
                        ) {
                            SessionCard(
                                session  = session,
                                onClick  = { onSessionClick(session) }
                            )
                        }
                    }
                }
            }
        }
    }
}

//Session card
@Composable
private fun SessionCard(
    session: RecordingSession,
    onClick: () -> Unit
) {
    Surface(
        modifier      = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(16.dp))
            .clickable(onClick = onClick),
        color         = SurfaceDark,
        tonalElevation = 2.dp,
        shape          = RoundedCornerShape(16.dp)
    ) {
        Row(
            modifier          = Modifier.padding(horizontal = 16.dp, vertical = 14.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            // Icon badge
            Box(
                modifier          = Modifier
                    .size(44.dp)
                    .background(AccentRed.copy(alpha = 0.12f), RoundedCornerShape(12.dp)),
                contentAlignment  = Alignment.Center
            ) {
                Icon(
                    imageVector        = Icons.Outlined.AudioFile,
                    contentDescription = null,
                    tint               = AccentRed,
                    modifier           = Modifier.size(22.dp)
                )
            }

            Spacer(Modifier.width(14.dp))

            // Text block
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text       = session.displayName,
                    color      = TextPrimary,
                    fontSize   = 15.sp,
                    fontWeight = FontWeight.SemiBold,
                    maxLines   = 1,
                    overflow   = TextOverflow.Ellipsis
                )
                Spacer(Modifier.height(3.dp))
                Text(
                    text     = session.formattedDate,
                    color    = TextSecondary,
                    fontSize = 12.sp
                )
            }

            Spacer(Modifier.width(12.dp))

            // Duration chip
            DurationChip(session.formattedDuration)
        }
    }
}

@Composable
private fun DurationChip(label: String) {
    Surface(
        color  = AccentRed.copy(alpha = 0.10f),
        shape  = RoundedCornerShape(8.dp)
    ) {
        Text(
            text     = label,
            color    = AccentRed,
            fontSize = 12.sp,
            fontWeight = FontWeight.Medium,
            modifier = Modifier.padding(horizontal = 10.dp, vertical = 4.dp)
        )
    }
}


@Composable
private fun EmptyState() {
    Box(
        modifier          = Modifier.fillMaxSize(),
        contentAlignment  = Alignment.Center
    ) {
        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            Box(
                modifier         = Modifier
                    .size(72.dp)
                    .background(SurfaceDark, CircleShape),
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    imageVector        = Icons.Default.MicNone,
                    contentDescription = null,
                    tint               = TextSecondary,
                    modifier           = Modifier.size(34.dp)
                )
            }
            Text(
                text       = "No recordings yet",
                color      = TextPrimary,
                fontSize   = 17.sp,
                fontWeight = FontWeight.SemiBold
            )
            Text(
                text     = "Tap the mic button to start your first recording",
                color    = TextSecondary,
                fontSize = 13.sp
            )
        }
    }
}

//Preview
@Preview(showBackground = true, backgroundColor = 0xFF0D0D0D)
@Composable
private fun DashboardPreview() {
    val fakeSessions = listOf(
        RecordingSession(
            id           = "s1",
            displayName  = "Recording · Jan 5, 3:22 PM",
            chunkFiles   = listOf(File("chunk_0000.wav"), File("chunk_0001.wav")),
            createdAt    = System.currentTimeMillis() - 3_600_000,
            durationSecs = 72
        ),
        RecordingSession(
            id           = "s2",
            displayName  = "Recording · Jan 4, 10:05 AM",
            chunkFiles   = listOf(File("chunk_0000.wav")),
            createdAt    = System.currentTimeMillis() - 86_400_000,
            durationSecs = 30
        )
    )
    DashboardContent(
        sessions            = fakeSessions,
        onSessionClick      = {},
        onNewRecordingClick = {}
    )
}

@Preview(showBackground = true, backgroundColor = 0xFF0D0D0D)
@Composable
private fun DashboardEmptyPreview() {
    DashboardContent(
        sessions            = emptyList(),
        onSessionClick      = {},
        onNewRecordingClick = {}
    )
}