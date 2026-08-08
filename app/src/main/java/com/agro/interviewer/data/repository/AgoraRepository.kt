package com.agro.interviewer.data.repository

import android.content.Context
import com.agro.interviewer.BuildConfig
import com.agro.interviewer.data.remote.TokenApiRequest
import com.agro.interviewer.data.remote.TokenApiService
import com.agro.interviewer.domain.model.NetworkQuality
import com.agro.interviewer.domain.model.VoiceChannelEvent
import dagger.hilt.android.qualifiers.ApplicationContext
import io.agora.rtc2.ChannelMediaOptions
import io.agora.rtc2.Constants
import io.agora.rtc2.DataStreamConfig
import io.agora.rtc2.IRtcEngineEventHandler
import io.agora.rtc2.RtcEngine
import io.agora.rtc2.RtcEngineConfig
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.launch
import timber.log.Timber
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class AgoraRepository @Inject constructor(
    @ApplicationContext private val context: Context,
    private val tokenApiService: TokenApiService
) {
    private var rtcEngine: RtcEngine? = null
    private var dataStreamId: Int = -1

    // SupervisorJob so a failed renewal doesn't cancel other coroutines
    private val repositoryScope = CoroutineScope(SupervisorJob() + Dispatchers.IO)

    private var currentChannelName: String = ""

    var localUid: Int = 0
        private set

    private val _events = MutableSharedFlow<VoiceChannelEvent>(extraBufferCapacity = 64)
    val events: Flow<VoiceChannelEvent> = _events.asSharedFlow()

    fun initEngine(overrideAppId: String? = null) {
        if (rtcEngine != null) return

        val appId = overrideAppId.takeUnless { it.isNullOrBlank() || it == "your_agora_app_id_here" }
            ?: BuildConfig.AGORA_APP_ID.takeUnless { it.isNullOrBlank() || it == "your_agora_app_id_here" }
            ?: ""

        if (appId.isBlank()) {
            Timber.w("Agora App ID is not configured")
            return
        }

        val config = RtcEngineConfig().apply {
            mAppId = appId
            mContext = context
            mEventHandler = buildEventHandler()
        }

        try {
            val engine = RtcEngine.create(config)
                ?: RtcEngine.create(context, appId, buildEventHandler())
                ?: throw IllegalStateException("RtcEngine.create returned null. Check your Agora App ID.")

            engine.setAudioProfile(Constants.AUDIO_PROFILE_SPEECH_STANDARD, Constants.AUDIO_SCENARIO_DEFAULT)
            engine.enableAudioVolumeIndication(500, 3, true)
            engine.setEnableSpeakerphone(true)

            rtcEngine = engine
            Timber.d("RtcEngine initialized (AppId: %s)", appId)
        } catch (e: Exception) {
            Timber.e(e, "Failed to initialize RtcEngine")
            throw e
        }
    }

    suspend fun joinChannel(channelName: String, uid: Int = 0): Result<Unit> {
        return try {
            currentChannelName = channelName
            val response = try {
                tokenApiService.getToken(TokenApiRequest(channelName, uid))
            } catch (e: Exception) {
                Timber.w(e, "Token server fetch failed (${e.message}), using fallback App ID")
                com.agro.interviewer.data.remote.TokenApiResponse(
                    token = "",
                    appId = BuildConfig.AGORA_APP_ID.ifBlank { "35e6d8a8830141bdae00671c569bddcb" },
                    channelName = channelName,
                    uid = uid
                )
            }
            val targetAppId = response.appId?.takeIf { it.isNotBlank() }
                ?: BuildConfig.AGORA_APP_ID.ifBlank { "35e6d8a8830141bdae00671c569bddcb" }

            initEngine(targetAppId)

            val options = ChannelMediaOptions().apply {
                clientRoleType = Constants.CLIENT_ROLE_BROADCASTER
                channelProfile = Constants.CHANNEL_PROFILE_COMMUNICATION
                publishMicrophoneTrack = true
                autoSubscribeAudio = true
            }

            val engine = rtcEngine
                ?: return Result.failure(Exception("Agora engine could not be created."))

            val status = engine.joinChannel(response.token, channelName, response.uid, options)
            if (status == 0) Result.success(Unit)
            else Result.failure(Exception("joinChannel failed with error code $status"))
        } catch (e: Exception) {
            Timber.e(e, "joinChannel error")
            Result.failure(e)
        }
    }

    private fun renewTokenInBackground() {
        if (currentChannelName.isEmpty() || localUid == 0) return

        repositoryScope.launch {
            try {
                Timber.d("AgoraRepository: Renewing token for channel=$currentChannelName")
                val response = tokenApiService.getToken(TokenApiRequest(currentChannelName, localUid))
                rtcEngine?.renewToken(response.token)
                Timber.d("AgoraRepository: Token renewed successfully")
            } catch (e: Exception) {
                Timber.e(e, "AgoraRepository: Token renewal failed")
                _events.tryEmit(
                    VoiceChannelEvent.Error(
                        code = -1,
                        message = "Session token expired. Please restart the interview."
                    )
                )
            }
        }
    }

    fun createDataStream(): Int {
        val config = DataStreamConfig().apply {
            syncWithAudio = true
            ordered = true
        }
        dataStreamId = rtcEngine?.createDataStream(config) ?: -1
        Timber.d("DataStream created id=%d", dataStreamId)
        return dataStreamId
    }

    fun sendStreamMessage(message: String) {
        if (dataStreamId < 0) return
        rtcEngine?.sendStreamMessage(dataStreamId, message.toByteArray(Charsets.UTF_8))
    }

    fun leaveChannel() {
        rtcEngine?.leaveChannel()
        localUid = 0
        currentChannelName = ""
    }

    fun muteMicrophone(mute: Boolean) {
        rtcEngine?.muteLocalAudioStream(mute)
    }

    fun enableLocalAudioCapture(enabled: Boolean) {
        rtcEngine?.enableLocalAudio(enabled)
    }

    fun setSpeakerOn(speakerOn: Boolean) {
        rtcEngine?.setEnableSpeakerphone(speakerOn)
    }

    fun destroy() {
        RtcEngine.destroy()
        rtcEngine = null
        dataStreamId = -1
        localUid = 0
    }

    private fun buildEventHandler() = object : IRtcEngineEventHandler() {
        override fun onJoinChannelSuccess(channel: String, uid: Int, elapsed: Int) {
            localUid = uid
            createDataStream()
            _events.tryEmit(VoiceChannelEvent.JoinedChannel)
        }

        override fun onLeaveChannel(stats: RtcStats) {
            localUid = 0
            _events.tryEmit(VoiceChannelEvent.LeftChannel)
        }

        override fun onRejoinChannelSuccess(channel: String, uid: Int, elapsed: Int) {
            Timber.d("onRejoinChannelSuccess uid=$uid")
            _events.tryEmit(VoiceChannelEvent.JoinedChannel)
        }

        override fun onUserJoined(uid: Int, elapsed: Int) {
            _events.tryEmit(VoiceChannelEvent.RemoteUserJoined(uid))
        }

        override fun onUserOffline(uid: Int, reason: Int) {
            _events.tryEmit(VoiceChannelEvent.RemoteUserLeft(uid))
        }

        override fun onError(err: Int) {
            val msg = RtcEngine.getErrorDescription(err) ?: "Agora error $err"
            Timber.e("onError code=$err msg=$msg")
            _events.tryEmit(VoiceChannelEvent.Error(err, msg))
        }

        override fun onConnectionStateChanged(state: Int, reason: Int) {
            Timber.d("onConnectionStateChanged state=$state reason=$reason")
            val mapped = when (state) {
                Constants.CONNECTION_STATE_RECONNECTING ->
                    VoiceChannelEvent.ConnectionStateChanged(isReconnecting = true)
                Constants.CONNECTION_STATE_CONNECTED ->
                    VoiceChannelEvent.ConnectionStateChanged(isReconnecting = false)
                Constants.CONNECTION_STATE_FAILED ->
                    VoiceChannelEvent.Error(state, "Connection failed — check your network")
                else -> null
            }
            mapped?.let { _events.tryEmit(it) }
        }

        override fun onAudioVolumeIndication(speakers: Array<out AudioVolumeInfo>, totalVolume: Int) {
            var local = 0
            var remote = 0
            speakers.forEach { s ->
                if (s.uid == 0) local = s.volume
                else remote = maxOf(remote, s.volume)
            }
            _events.tryEmit(VoiceChannelEvent.AudioVolumeIndication(local, remote))
        }

        override fun onStreamMessage(uid: Int, streamId: Int, data: ByteArray) {
            _events.tryEmit(VoiceChannelEvent.StreamMessageReceived(uid, data))
        }

        override fun onStreamMessageError(uid: Int, streamId: Int, error: Int, missed: Int, cached: Int) {
            Timber.e("onStreamMessageError uid=$uid error=$error")
        }

        override fun onNetworkQuality(uid: Int, txQuality: Int, rxQuality: Int) {
            if (uid != 0) return
            val quality = when (minOf(txQuality, rxQuality)) {
                Constants.QUALITY_EXCELLENT -> NetworkQuality.EXCELLENT
                Constants.QUALITY_GOOD -> NetworkQuality.GOOD
                Constants.QUALITY_POOR -> NetworkQuality.POOR
                Constants.QUALITY_BAD, Constants.QUALITY_VBAD -> NetworkQuality.BAD
                else -> NetworkQuality.UNKNOWN
            }
            _events.tryEmit(VoiceChannelEvent.NetworkQualityChanged(quality))
        }

        override fun onTokenPrivilegeWillExpire(token: String) {
            Timber.w("onTokenPrivilegeWillExpire — renewing silently")
            renewTokenInBackground()
        }

        override fun onRequestToken() {
            Timber.e("onRequestToken — token fully expired")
            _events.tryEmit(
                VoiceChannelEvent.Error(
                    code = Constants.ERR_TOKEN_EXPIRED,
                    message = "Session expired. Please start a new interview."
                )
            )
        }
    }
}
