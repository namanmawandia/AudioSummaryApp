package com.example.audiosummeryapp.ui

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.audiosummeryapp.db.SessionRepository
import com.example.audiosummeryapp.db.RecordingSessionEntity
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.stateIn
import javax.inject.Inject

@HiltViewModel
class DashboardViewModel @Inject constructor(
    private val repository: SessionRepository
) : ViewModel() {

    /** Live list of completed sessions from Room — auto-updates when DB changes. */
    val sessions: StateFlow<List<RecordingSessionEntity>> =
        repository.observeCompletedSessions()
            .stateIn(
                scope        = viewModelScope,
                started      = SharingStarted.WhileSubscribed(5_000),
                initialValue = emptyList()
            )
}