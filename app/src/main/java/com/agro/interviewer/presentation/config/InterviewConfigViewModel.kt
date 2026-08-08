package com.agro.interviewer.presentation.config

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.agro.interviewer.data.local.datastore.UserPreferences
import com.agro.interviewer.data.repository.SessionRepository
import com.agro.interviewer.domain.model.Difficulty
import com.agro.interviewer.domain.model.InterviewSession
import com.agro.interviewer.domain.model.QuestionCategory
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import javax.inject.Inject

data class ConfigUiState(
    val selectedCategory: QuestionCategory = QuestionCategory.KOTLIN,
    val selectedDifficulty: Difficulty = Difficulty.MID,
    val questionCount: Int = 5,
    val isCreatingSession: Boolean = false
)

@HiltViewModel
class InterviewConfigViewModel @Inject constructor(
    private val sessionRepository: SessionRepository,
    private val userPreferences: UserPreferences
) : ViewModel() {

    private val _state = MutableStateFlow(ConfigUiState())
    val state: StateFlow<ConfigUiState> = _state.asStateFlow()

    init {
        viewModelScope.launch {
            val cat = userPreferences.lastCategory.first()
            val diff = userPreferences.lastDifficulty.first()
            val count = userPreferences.lastQuestionCount.first()
            _state.update {
                it.copy(selectedCategory = cat, selectedDifficulty = diff, questionCount = count)
            }
        }
    }

    fun selectCategory(category: QuestionCategory) {
        _state.update { it.copy(selectedCategory = category) }
        viewModelScope.launch { userPreferences.saveLastCategory(category) }
    }

    fun selectDifficulty(difficulty: Difficulty) {
        _state.update { it.copy(selectedDifficulty = difficulty) }
        viewModelScope.launch { userPreferences.saveLastDifficulty(difficulty) }
    }

    fun selectQuestionCount(count: Int) {
        _state.update { it.copy(questionCount = count) }
        viewModelScope.launch { userPreferences.saveLastQuestionCount(count) }
    }

    fun startSession(onSessionCreated: (String) -> Unit) {
        viewModelScope.launch {
            _state.update { it.copy(isCreatingSession = true) }
            val session = InterviewSession(
                topic = _state.value.selectedCategory.displayName,
                category = _state.value.selectedCategory,
                difficulty = _state.value.selectedDifficulty,
                totalQuestions = _state.value.questionCount,
                channelName = "interview_${System.currentTimeMillis() / 1000}"
            )
            val sessionId = sessionRepository.createSession(session)
            _state.update { it.copy(isCreatingSession = false) }
            onSessionCreated(sessionId)
        }
    }
}
