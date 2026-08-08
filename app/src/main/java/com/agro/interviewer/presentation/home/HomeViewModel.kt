package com.agro.interviewer.presentation.home

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.agro.interviewer.data.repository.SessionRepository
import com.agro.interviewer.domain.model.InterviewSession
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import javax.inject.Inject

data class HomeUiState(
    val recentSessions: List<InterviewSession> = emptyList(),
    val averageScore: Float = 0f,
    val totalSessionsCompleted: Int = 0,
    val isLoading: Boolean = true
)

@HiltViewModel
class HomeViewModel @Inject constructor(
    private val sessionRepository: SessionRepository
) : ViewModel() {

    private val _state = MutableStateFlow(HomeUiState())
    val state: StateFlow<HomeUiState> = _state.asStateFlow()

    init {
        loadData()
    }

    fun loadData() {
        viewModelScope.launch {
            sessionRepository.getRecentSessions().collect { sessions ->
                val avgScore = sessionRepository.getAverageScore()
                val count = sessionRepository.getCompletedSessionCount()
                _state.update {
                    it.copy(
                        recentSessions = sessions,
                        averageScore = avgScore,
                        totalSessionsCompleted = count,
                        isLoading = false
                    )
                }
            }
        }
    }
}
