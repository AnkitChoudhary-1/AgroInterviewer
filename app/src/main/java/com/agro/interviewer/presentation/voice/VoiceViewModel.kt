package com.agro.interviewer.presentation.voice

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.agro.interviewer.data.parser.TranscriptParser
import com.agro.interviewer.data.repository.AgentRepository
import com.agro.interviewer.data.repository.AgoraRepository
import com.agro.interviewer.data.repository.QuestionRepository
import com.agro.interviewer.data.repository.SessionRepository
import com.agro.interviewer.domain.model.AgentStatus
import com.agro.interviewer.domain.model.ConnectionState
import com.agro.interviewer.domain.model.Difficulty
import com.agro.interviewer.domain.model.MessageSender
import com.agro.interviewer.domain.model.NetworkQuality
import com.agro.interviewer.domain.model.NetworkStatus
import com.agro.interviewer.domain.model.Question
import com.agro.interviewer.domain.model.QuestionAnswer
import com.agro.interviewer.domain.model.TranscriptMessage
import com.agro.interviewer.domain.model.VoiceChannelEvent
import com.agro.interviewer.domain.model.VoiceChannelState
import com.agro.interviewer.utils.NetworkMonitor
import com.agro.interviewer.utils.SpeechEvent
import com.agro.interviewer.utils.SpeechManager
import com.agro.interviewer.utils.TranscriptAnalyzer
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import timber.log.Timber
import javax.inject.Inject

@HiltViewModel
class VoiceViewModel @Inject constructor(
    private val agoraRepository: AgoraRepository,
    private val agentRepository: AgentRepository,
    private val questionRepository: QuestionRepository,
    private val sessionRepository: SessionRepository,
    private val networkMonitor: NetworkMonitor,
    private val speechManager: SpeechManager
) : ViewModel() {

    private val _state = MutableStateFlow(VoiceChannelState())
    val state: StateFlow<VoiceChannelState> = _state.asStateFlow()

    private val _uiEvents = MutableStateFlow<VoiceUiEvent?>(null)
    val uiEvents: StateFlow<VoiceUiEvent?> = _uiEvents.asStateFlow()

    private var timerJob: Job? = null
    private var autoSaveJob: Job? = null
    private var reconnectJob: Job? = null

    private var activeSessionId: String = ""
    private val channelName = "interview_${System.currentTimeMillis() / 1000}"

    private var sessionQuestions: List<Question> = emptyList()
    private var currentQuestionIndex: Int = 0
    private var hasAskedFirstQuestion: Boolean = false

    init {
        try {
            agoraRepository.initEngine()
        } catch (e: Exception) {
            _state.update {
                it.copy(
                    connectionState = ConnectionState.FAILED,
                    errorMessage = "Engine initialization failed: ${e.message}"
                )
            }
        }
        observeAgoraEvents()
        observeSpeechEvents()
        observeNetworkStatus()
    }

    fun initSession(sessionId: String) {
        if (activeSessionId == sessionId) return
        activeSessionId = sessionId
        viewModelScope.launch {
            sessionRepository.getSessionById(sessionId)?.let { session ->
                _state.update {
                    it.copy(
                        sessionTopic = session.topic,
                        sessionDifficulty = try { Difficulty.valueOf(session.difficulty) } catch (e: Exception) { Difficulty.JUNIOR }
                    )
                }
                loadQuestionsAndAskFirst()
            }
        }
    }

    private fun observeAgoraEvents() {
        viewModelScope.launch {
            agoraRepository.events.collect { event -> handleAgoraEvent(event) }
        }
    }

    private fun observeSpeechEvents() {
        viewModelScope.launch {
            speechManager.events.collect { event ->
                when (event) {
                    is SpeechEvent.SpeechRecognized -> {
                        val userMsg = TranscriptMessage(
                            id = "user_${System.currentTimeMillis()}",
                            sender = MessageSender.USER,
                            text = event.text,
                            timestamp = System.currentTimeMillis(),
                            isFinal = event.isFinal,
                            turnId = currentQuestionIndex + 1
                        )
                        updateOrAppendUserTranscript(userMsg)
                        if (event.isFinal) processCandidateInput(event.text)
                    }
                    is SpeechEvent.ListeningStarted -> {
                        _state.update { it.copy(agentState = it.agentState.copy(isListening = true)) }
                    }
                    is SpeechEvent.ListeningStopped -> {
                        _state.update { it.copy(agentState = it.agentState.copy(isListening = false)) }
                        agoraRepository.enableLocalAudioCapture(true)
                    }
                    is SpeechEvent.Error -> {
                        Timber.w("SpeechRecognizer error: ${event.message}")
                        agoraRepository.enableLocalAudioCapture(true)
                        _uiEvents.value = VoiceUiEvent.ShowMessage("Couldn't hear clearly — try typing or speak again")
                    }
                }
            }
        }
    }

    private fun observeNetworkStatus() {
        viewModelScope.launch {
            networkMonitor.networkStatus.collect { status ->
                val domainStatus = when (status) {
                    com.agro.interviewer.utils.NetworkStatus.AVAILABLE -> NetworkStatus.AVAILABLE
                    com.agro.interviewer.utils.NetworkStatus.LOSING -> NetworkStatus.LOSING
                    com.agro.interviewer.utils.NetworkStatus.LOST -> NetworkStatus.LOST
                    com.agro.interviewer.utils.NetworkStatus.UNAVAILABLE -> NetworkStatus.UNAVAILABLE
                }
                _state.update { it.copy(networkStatus = domainStatus) }
                handleNetworkChange(domainStatus)
            }
        }
    }

    private fun handleNetworkChange(status: NetworkStatus) {
        when (status) {
            NetworkStatus.LOST, NetworkStatus.UNAVAILABLE -> {
                if (_state.value.connectionState == ConnectionState.CONNECTED) {
                    _state.update { it.copy(showNetworkWarning = true) }
                    _uiEvents.value = VoiceUiEvent.ShowMessage("Network lost — trying to reconnect...")
                }
            }
            NetworkStatus.AVAILABLE -> {
                if (_state.value.showNetworkWarning) {
                    _state.update { it.copy(showNetworkWarning = false) }
                    scheduleReconnectFallback()
                }
            }
            else -> Unit
        }
    }

    private fun scheduleReconnectFallback() {
        reconnectJob?.cancel()
        reconnectJob = viewModelScope.launch {
            delay(10_000L)
            if (_state.value.isReconnecting || _state.value.connectionState == ConnectionState.RECONNECTING) {
                _uiEvents.value = VoiceUiEvent.ShowReconnectDialog
            }
        }
    }

    private fun handleAgoraEvent(event: VoiceChannelEvent) {
        when (event) {
            is VoiceChannelEvent.JoinedChannel -> {
                _state.update {
                    it.copy(
                        connectionState = ConnectionState.CONNECTED,
                        channelName = channelName,
                        localUid = agoraRepository.localUid,
                        isReconnecting = false,
                        errorMessage = null
                    )
                }
                startTimer()
                loadQuestionsAndAskFirst()
                startAgent()
                startAutoSave()
            }
            is VoiceChannelEvent.LeftChannel -> {
                stopTimer()
                stopAutoSave()
                speechManager.stopSpeaking()
                speechManager.stopListening()
                _state.update {
                    it.copy(
                        connectionState = ConnectionState.IDLE,
                        remoteUids = emptySet(),
                        localAudioLevel = 0,
                        remoteAudioLevel = 0,
                        elapsedSeconds = 0,
                        transcript = emptyList()
                    )
                }
            }
            is VoiceChannelEvent.RemoteUserJoined -> {
                val isAgent = _state.value.agentState.agentUid != 0 &&
                        event.uid == _state.value.agentState.agentUid
                _state.update {
                    it.copy(
                        remoteUids = it.remoteUids + event.uid,
                        agentState = if (isAgent) it.agentState.copy(status = AgentStatus.ACTIVE) else it.agentState
                    )
                }
            }
            is VoiceChannelEvent.RemoteUserLeft -> {
                val isAgent = event.uid == _state.value.agentState.agentUid
                _state.update {
                    it.copy(
                        remoteUids = it.remoteUids - event.uid,
                        agentState = if (isAgent) it.agentState.copy(status = AgentStatus.STOPPED) else it.agentState
                    )
                }
                // Restart the agent if it dropped unexpectedly
                if (isAgent && _state.value.agentState.status != AgentStatus.STOPPING &&
                    _state.value.connectionState == ConnectionState.CONNECTED
                ) {
                    viewModelScope.launch {
                        delay(2000L)
                        startAgent()
                    }
                }
            }
            is VoiceChannelEvent.AudioVolumeIndication -> {
                val agentSpeaking = event.remoteLevel > 15
                _state.update {
                    it.copy(
                        localAudioLevel = event.localLevel,
                        remoteAudioLevel = event.remoteLevel,
                        isAgentSpeaking = agentSpeaking,
                        agentState = it.agentState.copy(
                            isSpeaking = agentSpeaking,
                            isListening = !agentSpeaking && event.localLevel > 15
                        )
                    )
                }
            }
            is VoiceChannelEvent.StreamMessageReceived -> {
                handleStreamMessage(event.uid, event.data)
            }
            is VoiceChannelEvent.ConnectionStateChanged -> {
                _state.update {
                    it.copy(
                        isReconnecting = event.isReconnecting,
                        connectionState = if (event.isReconnecting) ConnectionState.RECONNECTING else ConnectionState.CONNECTED
                    )
                }
                if (event.isReconnecting) {
                    _uiEvents.value = VoiceUiEvent.ShowMessage("Reconnecting to session...")
                } else {
                    _uiEvents.value = VoiceUiEvent.ShowMessage("Reconnected ✓")
                }
            }
            is VoiceChannelEvent.NetworkQualityChanged -> {
                val msg = when (event.quality) {
                    NetworkQuality.BAD -> "⚠️ Poor network quality"
                    NetworkQuality.POOR -> "Network quality degraded"
                    else -> null
                }
                msg?.let { _uiEvents.value = VoiceUiEvent.ShowMessage(it) }
            }
            is VoiceChannelEvent.Error -> {
                Timber.e("Agora error ${event.code}: ${event.message}")
                _state.update {
                    it.copy(connectionState = ConnectionState.FAILED, errorMessage = event.message)
                }
                stopTimer()
                stopAutoSave()
                // Persist whatever we have before the session dies
                if (activeSessionId.isNotEmpty()) {
                    viewModelScope.launch { persistQAPairsFromTranscript() }
                }
            }
            is VoiceChannelEvent.TranscriptReceived -> appendTranscript(event.message)
            is VoiceChannelEvent.AgentStateChanged -> {
                _state.update { it.copy(isAgentSpeaking = event.isSpeaking) }
            }
        }
    }

    private fun processCandidateInput(input: String) {
        agoraRepository.enableLocalAudioCapture(true)
        val trimmed = input.trim()
        val isQuestionForAi = trimmed.endsWith("?") ||
                listOf("what", "why", "how", "explain", "can you", "tell me")
                    .any { trimmed.lowercase().startsWith(it) }

        if (isQuestionForAi) {
            handleCandidateQuestionToAi(trimmed)
        } else {
            saveCandidateAnswer(trimmed)
            viewModelScope.launch {
                delay(1500L)
                nextQuestion()
            }
        }
    }

    private fun handleCandidateQuestionToAi(userQuestion: String) {
        viewModelScope.launch {
            _state.update { it.copy(agentState = it.agentState.copy(isSpeaking = true, status = AgentStatus.ACTIVE)) }
            agentRepository.askAi(userQuestion, _state.value.sessionTopic).fold(
                onSuccess = { aiAnswer ->
                    val aiMsg = TranscriptMessage(
                        id = "ai_ans_${System.currentTimeMillis()}",
                        sender = MessageSender.AGENT,
                        text = "AI Answer: $aiAnswer",
                        timestamp = System.currentTimeMillis(),
                        isFinal = true,
                        turnId = currentQuestionIndex + 1
                    )
                    appendTranscript(aiMsg)
                    speechManager.speak(aiAnswer) {
                        _state.update { it.copy(agentState = it.agentState.copy(isSpeaking = false)) }
                        startListeningForAnswer()
                    }
                },
                onFailure = {
                    speechManager.speak("Let us continue with the next interview question.") {
                        startListeningForAnswer()
                    }
                }
            )
        }
    }

    private fun loadQuestionsAndAskFirst() {
        if (hasAskedFirstQuestion) return
        viewModelScope.launch {
            if (hasAskedFirstQuestion) return@launch
            val topic = _state.value.sessionTopic
            val difficulty = _state.value.sessionDifficulty
            val count = _state.value.agentState.totalQuestions

            agentRepository.generateQuestions(topic, difficulty, count).fold(
                onSuccess = { generated ->
                    if (generated.isNotEmpty() && !hasAskedFirstQuestion) {
                        sessionQuestions = generated
                        hasAskedFirstQuestion = true
                        askQuestion(0)
                        return@launch
                    }
                },
                onFailure = { Timber.w("AI question generation unavailable, using local bank") }
            )

            if (!hasAskedFirstQuestion) {
                val category = topic.toCategory()
                sessionQuestions = questionRepository.getQuestions(category, difficulty, count)
                if (sessionQuestions.isNotEmpty()) {
                    hasAskedFirstQuestion = true
                    askQuestion(0)
                }
            }
        }
    }

    private fun askQuestion(index: Int) {
        if (index >= sessionQuestions.size) {
            leaveChannel()
            return
        }
        currentQuestionIndex = index
        val q = sessionQuestions[index]

        val agentMessage = TranscriptMessage(
            id = "agent_${System.currentTimeMillis()}",
            sender = MessageSender.AGENT,
            text = "Question ${index + 1} of ${sessionQuestions.size}: ${q.text}",
            timestamp = System.currentTimeMillis(),
            isFinal = true,
            turnId = index + 1
        )

        _state.update {
            it.copy(
                transcript = it.transcript + agentMessage,
                agentState = it.agentState.copy(
                    currentQuestion = index + 1,
                    status = AgentStatus.ACTIVE,
                    isSpeaking = true
                )
            )
        }

        speechManager.speak(q.text) {
            _state.update { it.copy(agentState = it.agentState.copy(isSpeaking = false)) }
            startListeningForAnswer()
        }
    }

    fun startListeningForAnswer() {
        agoraRepository.enableLocalAudioCapture(false)
        viewModelScope.launch {
            delay(300L)
            speechManager.startListening()
        }
    }

    fun submitAnswer(text: String) {
        if (text.isBlank()) return
        val userMsg = TranscriptMessage(
            id = "user_${System.currentTimeMillis()}",
            sender = MessageSender.USER,
            text = text,
            timestamp = System.currentTimeMillis(),
            isFinal = true,
            turnId = currentQuestionIndex + 1
        )
        updateOrAppendUserTranscript(userMsg)
        processCandidateInput(text)
    }

    fun nextQuestion() {
        speechManager.stopListening()
        agoraRepository.enableLocalAudioCapture(true)
        if (currentQuestionIndex + 1 < sessionQuestions.size) {
            askQuestion(currentQuestionIndex + 1)
        } else {
            leaveChannel()
        }
    }

    private fun saveCandidateAnswer(answerText: String) {
        if (activeSessionId.isEmpty() || currentQuestionIndex >= sessionQuestions.size) return
        val currentQ = sessionQuestions[currentQuestionIndex]
        viewModelScope.launch {
            val eval = agentRepository.evaluateAnswer(
                questionText = currentQ.text,
                modelAnswer = currentQ.modelAnswer,
                userAnswer = answerText,
                difficulty = _state.value.sessionDifficulty
            )
            val record = QuestionAnswer(
                sessionId = activeSessionId,
                questionId = currentQ.id,
                questionText = currentQ.text,
                userAnswer = answerText,
                aiFeedback = eval.feedback,
                score = eval.score
            )
            sessionRepository.saveQuestionAnswer(record)
        }
    }

    // Periodic auto-save every 30 seconds using transcript analysis as backup
    private fun startAutoSave() {
        autoSaveJob?.cancel()
        autoSaveJob = viewModelScope.launch {
            while (true) {
                delay(30_000L)
                persistQAPairsFromTranscript()
            }
        }
    }

    private fun stopAutoSave() {
        autoSaveJob?.cancel()
        autoSaveJob = null
    }

    private suspend fun persistQAPairsFromTranscript() {
        if (activeSessionId.isEmpty()) return
        val transcript = _state.value.transcript
        val pairs = TranscriptAnalyzer.extractQAPairs(transcript, activeSessionId)
        val savedCount = _state.value.savedQuestionCount

        // Only save newly extracted pairs beyond what we've already saved
        val newPairs = pairs.drop(savedCount)
        newPairs.forEach { qa ->
            try {
                sessionRepository.saveQuestionAnswer(qa)
            } catch (e: Exception) {
                Timber.e(e, "Auto-save failed for Q&A")
            }
        }

        if (newPairs.isNotEmpty()) {
            _state.update { it.copy(savedQuestionCount = it.savedQuestionCount + newPairs.size) }
            Timber.d("Auto-saved ${newPairs.size} new Q&A pairs")
        }
    }

    private fun updateOrAppendUserTranscript(incoming: TranscriptMessage) {
        _state.update { currentState ->
            val index = currentState.transcript.indexOfLast {
                !it.isFinal && it.turnId == incoming.turnId && it.sender == MessageSender.USER
            }
            val list = if (index >= 0) {
                currentState.transcript.toMutableList().also { it[index] = incoming }
            } else {
                currentState.transcript + incoming
            }
            currentState.copy(transcript = list)
        }
    }

    private fun handleStreamMessage(uid: Int, data: ByteArray) {
        val agentUid = _state.value.agentState.agentUid
        val message = TranscriptParser.parse(uid, data, agentUid) ?: return
        if (!message.isFinal) updateStreamingTranscript(message) else finalizeTranscript(message)
    }

    private fun updateStreamingTranscript(incoming: TranscriptMessage) {
        _state.update { s ->
            val idx = s.transcript.indexOfLast {
                !it.isFinal && it.turnId == incoming.turnId && it.sender == incoming.sender
            }
            val list = if (idx >= 0)
                s.transcript.toMutableList().also { it[idx] = incoming }
            else
                s.transcript + incoming
            s.copy(transcript = list)
        }
    }

    private fun finalizeTranscript(incoming: TranscriptMessage) {
        _state.update { s ->
            val idx = s.transcript.indexOfLast {
                !it.isFinal && it.turnId == incoming.turnId && it.sender == incoming.sender
            }
            val list = if (idx >= 0)
                s.transcript.toMutableList().also { it[idx] = incoming }
            else
                s.transcript + incoming
            s.copy(transcript = list)
        }
    }

    private fun appendTranscript(message: TranscriptMessage) {
        _state.update { it.copy(transcript = it.transcript + message) }
    }

    private fun startAgent() {
        viewModelScope.launch {
            val currentState = _state.value
            _state.update { it.copy(agentState = it.agentState.copy(status = AgentStatus.STARTING)) }

            val questionContext = questionRepository.buildQuestionContext(
                category = currentState.sessionTopic.toCategory(),
                difficulty = currentState.sessionDifficulty,
                count = currentState.agentState.totalQuestions
            )

            agentRepository.startAgent(
                channelName = channelName,
                userUid = agoraRepository.localUid,
                topic = "${currentState.sessionTopic}\n\n$questionContext",
                difficulty = currentState.sessionDifficulty,
                totalQuestions = currentState.agentState.totalQuestions
            ).fold(
                onSuccess = { agentState -> _state.update { it.copy(agentState = agentState) } },
                onFailure = { err -> Timber.e(err, "Failed to start AI Agent") }
            )
        }
    }

    private fun stopAgent() {
        val taskId = _state.value.agentState.taskId
        if (taskId.isEmpty()) return
        viewModelScope.launch {
            _state.update { it.copy(agentState = it.agentState.copy(status = AgentStatus.STOPPING)) }
            agentRepository.stopAgent(taskId)
        }
    }

    private fun String.toCategory(): com.agro.interviewer.domain.model.QuestionCategory {
        return com.agro.interviewer.domain.model.QuestionCategory.entries.find { it.displayName == this }
            ?: com.agro.interviewer.domain.model.QuestionCategory.KOTLIN
    }

    fun joinChannel() {
        if (_state.value.connectionState == ConnectionState.CONNECTING ||
            _state.value.connectionState == ConnectionState.CONNECTED
        ) return

        viewModelScope.launch {
            _state.update { it.copy(connectionState = ConnectionState.CONNECTING, channelName = channelName, errorMessage = null) }
            agoraRepository.joinChannel(channelName).fold(
                onSuccess = {
                    _state.update {
                        it.copy(
                            connectionState = ConnectionState.CONNECTED,
                            channelName = channelName,
                            localUid = agoraRepository.localUid,
                            isReconnecting = false,
                            errorMessage = null
                        )
                    }
                    startTimer()
                    loadQuestionsAndAskFirst()
                    startAgent()
                    startAutoSave()
                },
                onFailure = { err ->
                    _state.update { it.copy(connectionState = ConnectionState.FAILED, errorMessage = err.message) }
                }
            )
        }
    }

    fun leaveChannel(onSessionComplete: ((String) -> Unit)? = null) {
        if (_state.value.connectionState == ConnectionState.IDLE) return
        _state.update { it.copy(connectionState = ConnectionState.DISCONNECTING) }

        viewModelScope.launch {
            stopAgent()
            speechManager.stopSpeaking()
            speechManager.stopListening()
            delay(300)
            agoraRepository.leaveChannel()

            if (activeSessionId.isNotEmpty()) {
                sessionRepository.completeSession(activeSessionId)
                onSessionComplete?.invoke(activeSessionId)
                _uiEvents.value = VoiceUiEvent.NavigateToResults(activeSessionId)
            }
        }
    }

    fun endSession(onSessionComplete: (String) -> Unit) {
        leaveChannel(onSessionComplete)
    }

    fun retryAgentStart() {
        startAgent()
    }

    fun forceReconnect() {
        if (_state.value.connectionState == ConnectionState.CONNECTED) return
        viewModelScope.launch {
            _state.update { it.copy(connectionState = ConnectionState.CONNECTING, errorMessage = null) }
            agoraRepository.joinChannel(channelName).fold(
                onSuccess = {
                    _state.update {
                        it.copy(
                            connectionState = ConnectionState.CONNECTED,
                            channelName = channelName,
                            localUid = agoraRepository.localUid,
                            isReconnecting = false,
                            errorMessage = null
                        )
                    }
                    startTimer()
                    loadQuestionsAndAskFirst()
                    startAgent()
                    startAutoSave()
                },
                onFailure = { err ->
                    _state.update { it.copy(connectionState = ConnectionState.FAILED, errorMessage = err.message) }
                }
            )
        }
    }

    fun toggleMicrophone() {
        val muted = !_state.value.isMicMuted
        agoraRepository.muteMicrophone(muted)
        _state.update { it.copy(isMicMuted = muted) }
    }

    fun toggleSpeaker() {
        val speakerOn = !_state.value.isSpeakerOn
        agoraRepository.setSpeakerOn(speakerOn)
        _state.update { it.copy(isSpeakerOn = speakerOn) }
    }

    fun dismissUiEvent() {
        _uiEvents.value = null
    }

    private fun startTimer() {
        timerJob?.cancel()
        timerJob = viewModelScope.launch {
            while (true) {
                delay(1000L)
                _state.update { it.copy(elapsedSeconds = it.elapsedSeconds + 1) }
            }
        }
    }

    private fun stopTimer() {
        timerJob?.cancel()
        timerJob = null
        _state.update { it.copy(elapsedSeconds = 0) }
    }

    override fun onCleared() {
        super.onCleared()
        agoraRepository.leaveChannel()
        agoraRepository.destroy()
        speechManager.destroy()
        timerJob?.cancel()
        autoSaveJob?.cancel()
        reconnectJob?.cancel()
    }
}

sealed class VoiceUiEvent {
    data class ShowMessage(val message: String) : VoiceUiEvent()
    data object ShowReconnectDialog : VoiceUiEvent()
    data class NavigateToResults(val sessionId: String) : VoiceUiEvent()
}
