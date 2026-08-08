package com.agro.interviewer.presentation.results

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.agro.interviewer.data.repository.SessionRepository
import com.agro.interviewer.domain.model.SessionResult
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import javax.inject.Inject

data class ResultsUiState(
    val result: SessionResult? = null,
    val isLoading: Boolean = true
)

@HiltViewModel
class ResultsViewModel @Inject constructor(
    private val sessionRepository: SessionRepository
) : ViewModel() {

    private val _state = MutableStateFlow(ResultsUiState())
    val state: StateFlow<ResultsUiState> = _state.asStateFlow()

    fun loadResult(sessionId: String) {
        viewModelScope.launch {
            val result = sessionRepository.getSessionResult(sessionId)
            _state.update { it.copy(result = result, isLoading = false) }
        }
    }
}
