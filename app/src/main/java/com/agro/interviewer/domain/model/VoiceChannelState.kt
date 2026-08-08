package com.agro.interviewer.domain.model

data class VoiceChannelState(
    val connectionState: ConnectionState = ConnectionState.IDLE,
    val isMicMuted: Boolean = false,
    val isSpeakerOn: Boolean = true,
    val localAudioLevel: Int = 0,
    val remoteAudioLevel: Int = 0,
    val channelName: String = "",
    val localUid: Int = 0,
    val remoteUids: Set<Int> = emptySet(),
    val errorMessage: String? = null,
    val elapsedSeconds: Int = 0,
    val agentState: AgentState = AgentState(),
    val transcript: List<TranscriptMessage> = emptyList(),
    val isAgentSpeaking: Boolean = false,
    val sessionTopic: String = "Android Development",
    val sessionDifficulty: Difficulty = Difficulty.MID,
    val networkStatus: NetworkStatus = NetworkStatus.AVAILABLE,
    val isReconnecting: Boolean = false,
    val sessionSaved: Boolean = false,
    val savedQuestionCount: Int = 0,
    val showNetworkWarning: Boolean = false
)

enum class NetworkStatus { AVAILABLE, LOSING, LOST, UNAVAILABLE }

enum class Difficulty { JUNIOR, MID, SENIOR }

enum class ConnectionState {
    IDLE,
    CONNECTING,
    CONNECTED,
    RECONNECTING,
    DISCONNECTING,
    FAILED
}

sealed class VoiceChannelEvent {
    data object JoinedChannel : VoiceChannelEvent()
    data object LeftChannel : VoiceChannelEvent()
    data class RemoteUserJoined(val uid: Int) : VoiceChannelEvent()
    data class RemoteUserLeft(val uid: Int) : VoiceChannelEvent()
    data class Error(val code: Int, val message: String) : VoiceChannelEvent()
    data class AudioVolumeIndication(
        val localLevel: Int,
        val remoteLevel: Int
    ) : VoiceChannelEvent()
    data class NetworkQualityChanged(val quality: NetworkQuality) : VoiceChannelEvent()
    data class TranscriptReceived(val message: TranscriptMessage) : VoiceChannelEvent()
    data class AgentStateChanged(val isSpeaking: Boolean) : VoiceChannelEvent()
    data class StreamMessageReceived(val uid: Int, val data: ByteArray) : VoiceChannelEvent()

    // Phase 4
    data class ConnectionStateChanged(val isReconnecting: Boolean) : VoiceChannelEvent()
}

enum class NetworkQuality { EXCELLENT, GOOD, POOR, BAD, UNKNOWN }
