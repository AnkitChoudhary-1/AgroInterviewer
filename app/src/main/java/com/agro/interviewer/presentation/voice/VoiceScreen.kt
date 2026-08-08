package com.agro.interviewer.presentation.voice

import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.EaseInOutSine
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.animation.expandVertically
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.shrinkVertically
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.automirrored.filled.Chat
import androidx.compose.material.icons.automirrored.filled.Send
import androidx.compose.material.icons.automirrored.filled.VolumeOff
import androidx.compose.material.icons.automirrored.filled.VolumeUp
import androidx.compose.material.icons.filled.CallEnd
import androidx.compose.material.icons.filled.Mic
import androidx.compose.material.icons.filled.MicOff
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.WifiOff
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.ui.text.input.ImeAction
import kotlinx.coroutines.delay
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.scale
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.stateDescription
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.agro.interviewer.domain.model.AgentStatus
import com.agro.interviewer.domain.model.ConnectionState
import com.agro.interviewer.domain.model.MessageSender
import com.agro.interviewer.domain.model.NetworkStatus
import com.agro.interviewer.domain.model.TranscriptMessage
import com.agro.interviewer.domain.model.VoiceChannelState
import com.agro.interviewer.utils.hasAllPermissions
import com.agro.interviewer.utils.rememberPermissionState
import kotlin.math.abs
import kotlin.math.sin

private val BackgroundDark = Color(0xFF0A0A0F)
private val AccentBlue = Color(0xFF4A9EFF)
private val AccentPurple = Color(0xFF8B5CF6)
private val AccentGreen = Color(0xFF34D399)
private val AccentRed = Color(0xFFFF4757)
private val TextPrimary = Color(0xFFFFFFFF)
private val TextSecondary = Color(0xFF8A8A9A)
private val TextTertiary = Color(0xFF3A3A4A)
private val SurfaceWhite = Color(0x0FFFFFFF)

@Composable
fun VoiceScreen(
    sessionId: String,
    onSessionComplete: (String) -> Unit,
    onBack: () -> Unit,
    viewModel: VoiceViewModel = hiltViewModel()
) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    val uiEvent by viewModel.uiEvents.collectAsStateWithLifecycle()
    val context = LocalContext.current
    var showEndDialog by remember { mutableStateOf(false) }
    var showReconnectDialog by remember { mutableStateOf(false) }
    var controlsVisible by remember { mutableStateOf(true) }
    var showPermissionDeniedDialog by remember { mutableStateOf(false) }
    var toastMessage by remember { mutableStateOf<String?>(null) }

    val permissionState = rememberPermissionState(
        onGranted = { viewModel.joinChannel() },
        onDenied = { showPermissionDeniedDialog = true }
    )

    LaunchedEffect(sessionId) {
        if (sessionId.isNotEmpty()) viewModel.initSession(sessionId)
    }

    LaunchedEffect(uiEvent) {
        uiEvent?.let { event ->
            when (event) {
                is VoiceUiEvent.ShowMessage -> {
                    toastMessage = event.message
                    delay(3500L)
                    toastMessage = null
                    viewModel.dismissUiEvent()
                }
                is VoiceUiEvent.ShowReconnectDialog -> {
                    showReconnectDialog = true
                    viewModel.dismissUiEvent()
                }
                is VoiceUiEvent.NavigateToResults -> {
                    onSessionComplete(event.sessionId)
                    viewModel.dismissUiEvent()
                }
            }
        }
    }

    if (showPermissionDeniedDialog) {
        AlertDialog(
            onDismissRequest = { showPermissionDeniedDialog = false },
            title = { Text("Microphone Permission Required", fontWeight = FontWeight.Bold) },
            text = { Text("Agro Interviewer requires microphone access to conduct voice technical interviews and record your spoken answers.") },
            confirmButton = {
                Button(
                    onClick = {
                        showPermissionDeniedDialog = false
                        permissionState.requestPermissions()
                    },
                    colors = ButtonDefaults.buttonColors(containerColor = AccentBlue)
                ) {
                    Text("Grant Permission")
                }
            },
            dismissButton = {
                OutlinedButton(
                    onClick = {
                        showPermissionDeniedDialog = false
                        onBack()
                    }
                ) {
                    Text("Cancel")
                }
            }
        )
    }

    if (showEndDialog) {
        EndSessionDialog(
            onConfirm = {
                showEndDialog = false
                viewModel.endSession(onSessionComplete)
            },
            onDismiss = { showEndDialog = false }
        )
    }

    if (showReconnectDialog) {
        ReconnectDialog(
            onReconnect = {
                showReconnectDialog = false
                viewModel.forceReconnect()
            },
            onEnd = {
                showReconnectDialog = false
                viewModel.endSession(onSessionComplete)
            }
        )
    }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .pointerInput(Unit) {
                detectTapGestures { controlsVisible = !controlsVisible }
            }
    ) {
        AnimatedBackground(
            isAgentSpeaking = state.isAgentSpeaking,
            agentStatus = state.agentState.status,
            connectionState = state.connectionState
        )

        AnimatedContent(
            targetState = state.connectionState,
            transitionSpec = { fadeIn(tween(500)) togetherWith fadeOut(tween(500)) },
            label = "screen_transition"
        ) { connectionState ->
            when (connectionState) {
                ConnectionState.IDLE,
                ConnectionState.FAILED -> IdleScreen(
                    state = state,
                    onStart = {
                        if (context.hasAllPermissions()) viewModel.joinChannel()
                        else permissionState.requestPermissions()
                    }
                )

                ConnectionState.CONNECTING -> ConnectingScreen()

                ConnectionState.DISCONNECTING -> DisconnectingScreen()

                ConnectionState.CONNECTED,
                ConnectionState.RECONNECTING -> ActiveScreen(
                    state = state,
                    controlsVisible = controlsVisible,
                    onMicToggle = viewModel::toggleMicrophone,
                    onSpeakerToggle = viewModel::toggleSpeaker,
                    onSubmitAnswer = viewModel::submitAnswer,
                    onRetryAgent = viewModel::retryAgentStart,
                    onEndSession = { showEndDialog = true }
                )
            }
        }

        AnimatedVisibility(
            visible = state.showNetworkWarning,
            enter = slideInVertically { -it } + fadeIn(),
            exit = slideOutVertically { -it } + fadeOut(),
            modifier = Modifier
                .align(Alignment.TopCenter)
                .statusBarsPadding()
                .padding(top = 16.dp)
        ) {
            NetworkPill()
        }

        AnimatedVisibility(
            visible = toastMessage != null,
            enter = slideInVertically { -it } + fadeIn(),
            exit = slideOutVertically { -it } + fadeOut(),
            modifier = Modifier
                .align(Alignment.TopCenter)
                .statusBarsPadding()
                .padding(top = 56.dp)
        ) {
            toastMessage?.let { msg ->
                Box(
                    modifier = Modifier
                        .clip(RoundedCornerShape(20.dp))
                        .background(Color(0xFF2A2A3C))
                        .padding(horizontal = 16.dp, vertical = 8.dp)
                ) {
                    Text(
                        text = msg,
                        fontSize = 12.sp,
                        color = Color.White,
                        fontWeight = FontWeight.Medium
                    )
                }
            }
        }
    }
}

@Composable
private fun AnimatedBackground(
    isAgentSpeaking: Boolean,
    agentStatus: AgentStatus,
    connectionState: ConnectionState
) {
    val isActive = connectionState == ConnectionState.CONNECTED &&
            agentStatus == AgentStatus.ACTIVE

    val accentColor by animateColorAsState(
        targetValue = when {
            !isActive -> AccentBlue.copy(alpha = 0.08f)
            isAgentSpeaking -> AccentPurple.copy(alpha = 0.15f)
            else -> AccentBlue.copy(alpha = 0.10f)
        },
        animationSpec = tween(800),
        label = "bg_accent"
    )

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(BackgroundDark)
    ) {
        Canvas(modifier = Modifier.fillMaxSize()) {
            drawCircle(
                brush = Brush.radialGradient(
                    colors = listOf(accentColor, Color.Transparent),
                    center = Offset(size.width / 2f, size.height * 0.38f),
                    radius = size.width * 0.75f
                ),
                radius = size.width * 0.75f,
                center = Offset(size.width / 2f, size.height * 0.38f)
            )
        }
    }
}

@Composable
private fun IdleScreen(
    state: VoiceChannelState,
    onStart: () -> Unit
) {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .statusBarsPadding()
            .navigationBarsPadding()
            .padding(horizontal = 32.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center
    ) {
        val infinite = rememberInfiniteTransition(label = "idle_orb")
        val pulseAlpha by infinite.animateFloat(
            initialValue = 0.3f, targetValue = 0.7f,
            animationSpec = infiniteRepeatable(
                tween(1400, easing = EaseInOutSine), RepeatMode.Reverse
            ), label = "orb_alpha"
        )
        val pulseScale by infinite.animateFloat(
            initialValue = 1f, targetValue = 1.08f,
            animationSpec = infiniteRepeatable(
                tween(1400, easing = EaseInOutSine), RepeatMode.Reverse
            ), label = "orb_scale"
        )

        Box(
            modifier = Modifier
                .size(160.dp)
                .scale(pulseScale)
                .clip(CircleShape)
                .background(AccentBlue.copy(alpha = pulseAlpha * 0.15f)),
            contentAlignment = Alignment.Center
        ) {
            Box(
                modifier = Modifier
                    .size(100.dp)
                    .clip(CircleShape)
                    .background(
                        Brush.radialGradient(
                            listOf(
                                AccentBlue.copy(alpha = 0.9f),
                                AccentPurple.copy(alpha = 0.7f)
                            )
                        )
                    ),
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    imageVector = Icons.Default.Mic,
                    contentDescription = null,
                    tint = TextPrimary,
                    modifier = Modifier.size(40.dp)
                )
            }
        }

        Spacer(modifier = Modifier.height(48.dp))

        Text(
            text = "Ready to Practice?",
            fontSize = 28.sp,
            fontWeight = FontWeight.Bold,
            color = TextPrimary,
            textAlign = TextAlign.Center
        )

        Spacer(modifier = Modifier.height(12.dp))

        Text(
            text = "${state.sessionTopic}  ·  ${state.sessionDifficulty.name}",
            fontSize = 14.sp,
            color = TextSecondary,
            letterSpacing = 1.5.sp
        )

        Spacer(modifier = Modifier.height(8.dp))

        Text(
            text = "${state.agentState.totalQuestions} questions",
            fontSize = 13.sp,
            color = TextTertiary
        )

        state.errorMessage?.let { err ->
            Spacer(modifier = Modifier.height(20.dp))
            Text(
                text = err,
                fontSize = 13.sp,
                color = AccentRed,
                textAlign = TextAlign.Center
            )
        }

        Spacer(modifier = Modifier.height(56.dp))

        Box(
            modifier = Modifier
                .fillMaxWidth()
                .height(56.dp)
                .clip(RoundedCornerShape(28.dp))
                .background(
                    Brush.horizontalGradient(listOf(AccentBlue, AccentPurple))
                )
                .pointerInput(Unit) { detectTapGestures { onStart() } },
            contentAlignment = Alignment.Center
        ) {
            Text(
                text = if (state.connectionState == ConnectionState.FAILED)
                    "Retry" else "Begin Interview",
                fontSize = 16.sp,
                fontWeight = FontWeight.SemiBold,
                color = TextPrimary
            )
        }
    }
}

@Composable
private fun ConnectingScreen() {
    Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            CircularProgressIndicator(
                color = AccentBlue,
                strokeWidth = 2.dp,
                modifier = Modifier.size(36.dp)
            )
            Spacer(modifier = Modifier.height(20.dp))
            Text("Joining session", fontSize = 15.sp, color = TextSecondary)
        }
    }
}

@Composable
private fun DisconnectingScreen() {
    Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            CircularProgressIndicator(
                color = AccentBlue,
                strokeWidth = 2.dp,
                modifier = Modifier.size(36.dp)
            )
            Spacer(modifier = Modifier.height(20.dp))
            Text("Saving session", fontSize = 15.sp, color = TextSecondary)
        }
    }
}

@Composable
private fun ActiveScreen(
    state: VoiceChannelState,
    controlsVisible: Boolean,
    onMicToggle: () -> Unit,
    onSpeakerToggle: () -> Unit,
    onSubmitAnswer: (String) -> Unit,
    onRetryAgent: () -> Unit,
    onEndSession: () -> Unit
) {
    Box(modifier = Modifier.fillMaxSize()) {
        FullScreenTranscript(
            transcript = state.transcript,
            modifier = Modifier.fillMaxSize()
        )

        Box(
            modifier = Modifier
                .fillMaxWidth()
                .fillMaxHeight(0.35f)
                .align(Alignment.TopCenter)
                .background(
                    Brush.verticalGradient(
                        listOf(BackgroundDark, Color.Transparent)
                    )
                )
        )

        Box(
            modifier = Modifier
                .fillMaxWidth()
                .fillMaxHeight(0.40f)
                .align(Alignment.BottomCenter)
                .background(
                    Brush.verticalGradient(
                        listOf(Color.Transparent, BackgroundDark)
                    )
                )
        )

        val dimAlpha by animateFloatAsState(
            targetValue = 0.0f,
            animationSpec = tween(300),
            label = "dim_alpha"
        )

        TopStatusBar(
            state = state,
            controlsVisible = controlsVisible,
            modifier = Modifier
                .align(Alignment.TopCenter)
                .statusBarsPadding()
        )

        val avatarAlpha by animateFloatAsState(
            targetValue = if (state.transcript.isEmpty() || !controlsVisible) 1f else 0.0f,
            animationSpec = tween(400),
            label = "avatar_alpha"
        )

        if (avatarAlpha > 0.01f) {
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .align(Alignment.Center)
                    .alpha(avatarAlpha),
                contentAlignment = Alignment.Center
            ) {
                CenterAiOrb(
                    state = state,
                    onRetryAgent = onRetryAgent
                )
            }
        }

        AnimatedVisibility(
            visible = controlsVisible,
            enter = slideInVertically { it } + fadeIn(tween(300)),
            exit = slideOutVertically { it } + fadeOut(tween(300)),
            modifier = Modifier
                .align(Alignment.BottomCenter)
                .navigationBarsPadding()
        ) {
            BottomControls(
                state = state,
                onMicToggle = onMicToggle,
                onSpeakerToggle = onSpeakerToggle,
                onSubmitAnswer = onSubmitAnswer,
                onEndSession = onEndSession
            )
        }
    }
}

@Composable
private fun FullScreenTranscript(
    transcript: List<TranscriptMessage>,
    modifier: Modifier = Modifier
) {
    val listState = rememberLazyListState()

    LaunchedEffect(transcript.size) {
        if (transcript.isNotEmpty()) {
            listState.animateScrollToItem(transcript.size - 1)
        }
    }

    LazyColumn(
        state = listState,
        modifier = modifier,
        contentPadding = PaddingValues(
            start = 20.dp,
            end = 20.dp,
            top = 120.dp,
            bottom = 180.dp
        ),
        verticalArrangement = Arrangement.spacedBy(4.dp)
    ) {
        items(
            items = transcript,
            key = { it.id }
        ) { message ->
            TranscriptLine(message = message)
        }
    }
}

@Composable
private fun TranscriptLine(message: TranscriptMessage) {
    val isAgent = message.sender == MessageSender.AGENT

    val targetAlpha by animateFloatAsState(
        targetValue = if (message.isFinal) 1f else 0.6f,
        animationSpec = tween(300),
        label = "line_alpha"
    )

    Row(
        modifier = Modifier
            .fillMaxWidth()
            .alpha(targetAlpha)
            .padding(vertical = 2.dp),
        horizontalArrangement = if (isAgent) Arrangement.Start else Arrangement.End,
        verticalAlignment = Alignment.Bottom
    ) {
        if (isAgent) {
            Column(horizontalAlignment = Alignment.Start) {
                if (message.isFinal) {
                    Text(
                        text = "INTERVIEWER",
                        fontSize = 9.sp,
                        fontWeight = FontWeight.Bold,
                        color = AccentBlue.copy(alpha = 0.7f),
                        letterSpacing = 1.2.sp,
                        modifier = Modifier.padding(start = 2.dp, bottom = 2.dp)
                    )
                }
                Box(
                    modifier = Modifier
                        .widthIn(max = 300.dp)
                        .clip(
                            RoundedCornerShape(
                                topStart = 4.dp,
                                topEnd = 16.dp,
                                bottomStart = 16.dp,
                                bottomEnd = 16.dp
                            )
                        )
                        .background(SurfaceWhite)
                        .padding(horizontal = 14.dp, vertical = 10.dp)
                        .semantics {
                            contentDescription = "Interviewer: ${message.text}"
                        }
                ) {
                    Text(
                        text = message.text,
                        fontSize = 15.sp,
                        color = TextPrimary,
                        lineHeight = 22.sp
                    )
                }
                if (!message.isFinal) {
                    Spacer(Modifier.height(4.dp))
                    StreamingDots()
                }
            }
        } else {
            Column(horizontalAlignment = Alignment.End) {
                if (message.isFinal) {
                    Text(
                        text = "YOU",
                        fontSize = 9.sp,
                        fontWeight = FontWeight.Bold,
                        color = AccentGreen.copy(alpha = 0.7f),
                        letterSpacing = 1.2.sp,
                        modifier = Modifier.padding(end = 2.dp, bottom = 2.dp)
                    )
                }
                Box(
                    modifier = Modifier
                        .widthIn(max = 300.dp)
                        .clip(
                            RoundedCornerShape(
                                topStart = 16.dp,
                                topEnd = 4.dp,
                                bottomStart = 16.dp,
                                bottomEnd = 16.dp
                            )
                        )
                        .background(AccentGreen.copy(alpha = 0.12f))
                        .padding(horizontal = 14.dp, vertical = 10.dp)
                        .semantics {
                            contentDescription = "You: ${message.text}"
                        }
                ) {
                    Text(
                        text = message.text,
                        fontSize = 15.sp,
                        color = TextPrimary.copy(alpha = 0.9f),
                        lineHeight = 22.sp
                    )
                }
            }
        }
    }
}

@Composable
private fun StreamingDots() {
    val infinite = rememberInfiniteTransition(label = "dots")
    val phase by infinite.animateFloat(
        initialValue = 0f, targetValue = 1f,
        animationSpec = infiniteRepeatable(tween(1200, easing = LinearEasing)),
        label = "dot_phase"
    )

    Row(
        horizontalArrangement = Arrangement.spacedBy(4.dp),
        modifier = Modifier.padding(start = 4.dp)
    ) {
        repeat(3) { i ->
            val dotAlpha = ((phase + i / 3f) % 1f).let {
                if (it < 0.5f) it * 2f else (1f - it) * 2f
            }.coerceIn(0.2f, 1f)

            Box(
                modifier = Modifier
                    .size(5.dp)
                    .alpha(dotAlpha)
                    .clip(CircleShape)
                    .background(AccentBlue)
            )
        }
    }
}

@Composable
private fun TopStatusBar(
    state: VoiceChannelState,
    controlsVisible: Boolean,
    modifier: Modifier = Modifier
) {
    val alpha by animateFloatAsState(
        targetValue = if (controlsVisible) 1f else 0f,
        animationSpec = tween(300),
        label = "top_bar_alpha"
    )

    Row(
        modifier = modifier
            .fillMaxWidth()
            .alpha(alpha)
            .padding(horizontal = 20.dp, vertical = 12.dp),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically
    ) {
        Column {
            Text(
                text = state.sessionTopic,
                fontSize = 14.sp,
                fontWeight = FontWeight.SemiBold,
                color = TextPrimary
            )
            Text(
                text = state.sessionDifficulty.name.lowercase()
                    .replaceFirstChar { it.uppercase() },
                fontSize = 12.sp,
                color = TextSecondary
            )
        }

        Row(verticalAlignment = Alignment.CenterVertically) {
            val dotColor by animateColorAsState(
                targetValue = when (state.networkStatus) {
                    NetworkStatus.AVAILABLE -> AccentGreen
                    NetworkStatus.LOSING -> Color(0xFFFFB547)
                    else -> AccentRed
                },
                label = "net_dot"
            )
            Box(
                modifier = Modifier
                    .size(6.dp)
                    .clip(CircleShape)
                    .background(dotColor)
            )

            Spacer(Modifier.width(8.dp))

            AnimatedContent(
                targetState = formatDuration(state.elapsedSeconds),
                transitionSpec = {
                    slideInVertically { -it } + fadeIn() togetherWith
                            slideOutVertically { it } + fadeOut()
                },
                label = "timer_flip"
            ) { time ->
                Text(text = time, fontSize = 13.sp, color = TextSecondary)
            }

            Spacer(Modifier.width(12.dp))

            Box(
                modifier = Modifier
                    .clip(RoundedCornerShape(20.dp))
                    .background(SurfaceWhite)
                    .padding(horizontal = 10.dp, vertical = 4.dp)
            ) {
                Text(
                    text = "${state.agentState.currentQuestion}/${state.agentState.totalQuestions}",
                    fontSize = 12.sp,
                    fontWeight = FontWeight.SemiBold,
                    color = AccentBlue
                )
            }
        }
    }
}

@Composable
private fun CenterAiOrb(
    state: VoiceChannelState,
    onRetryAgent: () -> Unit
) {
    val agentStatus = state.agentState.status
    val isAgentSpeaking = state.isAgentSpeaking
    val audioLevel = state.remoteAudioLevel

    val infinite = rememberInfiniteTransition(label = "orb_anim")

    val breathScale by infinite.animateFloat(
        initialValue = 1f, targetValue = 1.05f,
        animationSpec = infiniteRepeatable(
            tween(1800, easing = EaseInOutSine), RepeatMode.Reverse
        ), label = "breath"
    )

    val speakScale by animateFloatAsState(
        targetValue = when {
            isAgentSpeaking -> 1f + (audioLevel / 255f) * 0.2f
            else -> 1f
        },
        animationSpec = spring(stiffness = Spring.StiffnessLow),
        label = "speak_scale"
    )

    val wavePhase by infinite.animateFloat(
        initialValue = 0f, targetValue = (2 * Math.PI).toFloat(),
        animationSpec = infiniteRepeatable(tween(900, easing = LinearEasing)),
        label = "wave"
    )

    val ringAlpha by infinite.animateFloat(
        initialValue = 0.08f, targetValue = 0.25f,
        animationSpec = infiniteRepeatable(
            tween(700, easing = EaseInOutSine), RepeatMode.Reverse
        ), label = "ring_alpha"
    )

    val coreColor1 by animateColorAsState(
        targetValue = when {
            agentStatus == AgentStatus.FAILED -> AccentRed
            agentStatus == AgentStatus.STARTING -> AccentBlue.copy(alpha = 0.5f)
            isAgentSpeaking -> AccentPurple
            else -> AccentBlue
        },
        animationSpec = tween(500),
        label = "core1"
    )
    val coreColor2 by animateColorAsState(
        targetValue = when {
            agentStatus == AgentStatus.FAILED -> Color(0xFFFF8C94)
            agentStatus == AgentStatus.STARTING -> AccentBlue.copy(alpha = 0.3f)
            isAgentSpeaking -> AccentBlue
            else -> AccentPurple
        },
        animationSpec = tween(500),
        label = "core2"
    )

    val finalScale = when {
        agentStatus != AgentStatus.ACTIVE -> 0.9f
        isAgentSpeaking -> speakScale
        else -> breathScale
    }

    Column(
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center
    ) {
        Box(
            modifier = Modifier.size(220.dp),
            contentAlignment = Alignment.Center
        ) {
            if (isAgentSpeaking && agentStatus == AgentStatus.ACTIVE) {
                Box(
                    modifier = Modifier
                        .size(220.dp)
                        .scale(finalScale)
                        .clip(CircleShape)
                        .background(coreColor1.copy(alpha = ringAlpha * 0.5f))
                )
                Box(
                    modifier = Modifier
                        .size(176.dp)
                        .scale(finalScale)
                        .clip(CircleShape)
                        .background(coreColor1.copy(alpha = ringAlpha * 0.8f))
                )
            }

            Box(
                modifier = Modifier
                    .size(130.dp)
                    .scale(finalScale)
                    .clip(CircleShape)
                    .background(
                        Brush.radialGradient(listOf(coreColor1, coreColor2))
                    )
                    .semantics {
                        contentDescription = when {
                            agentStatus == AgentStatus.STARTING -> "AI connecting"
                            isAgentSpeaking -> "AI speaking"
                            agentStatus == AgentStatus.ACTIVE -> "AI listening"
                            agentStatus == AgentStatus.FAILED -> "AI connection failed"
                            else -> "AI orb"
                        }
                    },
                contentAlignment = Alignment.Center
            ) {
                when (agentStatus) {
                    AgentStatus.STARTING -> {
                        CircularProgressIndicator(
                            color = TextPrimary,
                            strokeWidth = 1.5.dp,
                            modifier = Modifier.size(32.dp)
                        )
                    }

                    AgentStatus.ACTIVE -> {
                        if (isAgentSpeaking) {
                            OrbWaveform(
                                phase = wavePhase,
                                level = audioLevel / 255f
                            )
                        } else {
                            Icon(
                                imageVector = Icons.Default.Mic,
                                contentDescription = null,
                                tint = TextPrimary.copy(alpha = 0.6f),
                                modifier = Modifier.size(32.dp)
                            )
                        }
                    }

                    AgentStatus.FAILED -> {
                        IconButton(onClick = onRetryAgent) {
                            Icon(
                                Icons.Default.Refresh,
                                contentDescription = "Retry AI connection",
                                tint = TextPrimary,
                                modifier = Modifier.size(32.dp)
                            )
                        }
                    }

                    else -> {
                        Icon(
                            imageVector = Icons.Default.Mic,
                            contentDescription = null,
                            tint = TextPrimary.copy(alpha = 0.4f),
                            modifier = Modifier.size(28.dp)
                        )
                    }
                }
            }
        }

        Spacer(Modifier.height(16.dp))

        val statusText = when {
            agentStatus == AgentStatus.STARTING -> "Connecting interviewer..."
            agentStatus == AgentStatus.FAILED -> "Tap to retry"
            isAgentSpeaking -> "Speaking"
            agentStatus == AgentStatus.ACTIVE -> "Listening"
            else -> ""
        }

        AnimatedContent(
            targetState = statusText,
            transitionSpec = { fadeIn(tween(300)) togetherWith fadeOut(tween(300)) },
            label = "status_text"
        ) { text ->
            Text(
                text = text,
                fontSize = 13.sp,
                color = TextSecondary,
                letterSpacing = 0.5.sp
            )
        }

        Spacer(Modifier.height(8.dp))
        UserMicLine(
            audioLevel = state.localAudioLevel,
            isMuted = state.isMicMuted
        )
    }
}

@Composable
private fun OrbWaveform(phase: Float, level: Float) {
    Canvas(
        modifier = Modifier
            .size(80.dp, 36.dp)
            .semantics { contentDescription = "Audio waveform" }
    ) {
        drawWaveformBars(phase = phase, level = level, color = TextPrimary)
    }
}

private fun DrawScope.drawWaveformBars(phase: Float, level: Float, color: Color) {
    val barCount = 11
    val spacing = size.width / barCount
    val barWidth = spacing * 0.45f
    val maxH = size.height * 0.85f
    val minH = size.height * 0.12f

    for (i in 0 until barCount) {
        val x = spacing * (i + 0.5f)
        val positionFactor = 1f - abs((i - barCount / 2f) / (barCount / 2f)) * 0.4f
        val sinVal = sin(phase.toDouble() + i * 0.55).toFloat()
        val barH = (minH + (maxH - minH) * ((sinVal + 1) / 2f) * (0.3f + level * 0.7f)) *
                positionFactor

        drawLine(
            color = color.copy(alpha = 0.85f + (sinVal + 1) / 2f * 0.15f),
            start = Offset(x, center.y + barH / 2),
            end = Offset(x, center.y - barH / 2),
            strokeWidth = barWidth,
            cap = StrokeCap.Round
        )
    }
}

@Composable
private fun UserMicLine(audioLevel: Int, isMuted: Boolean) {
    val animatedLevel by animateFloatAsState(
        targetValue = if (isMuted) 0f else audioLevel / 255f,
        animationSpec = tween(80),
        label = "mic_line"
    )

    val lineColor by animateColorAsState(
        targetValue = when {
            isMuted -> AccentRed.copy(alpha = 0.6f)
            animatedLevel > 0.6f -> AccentGreen
            animatedLevel > 0.2f -> AccentBlue
            else -> TextTertiary
        },
        animationSpec = tween(200),
        label = "line_color"
    )

    Column(
        horizontalAlignment = Alignment.CenterHorizontally,
        modifier = Modifier.semantics {
            contentDescription = if (isMuted) "Microphone muted"
            else "Microphone level ${(animatedLevel * 100).toInt()} percent"
        }
    ) {
        Box(
            modifier = Modifier
                .width(120.dp)
                .height(2.dp)
                .clip(RoundedCornerShape(1.dp))
                .background(TextTertiary)
        ) {
            Box(
                modifier = Modifier
                    .fillMaxWidth(animatedLevel)
                    .height(2.dp)
                    .clip(RoundedCornerShape(1.dp))
                    .background(lineColor)
            )
        }

        Spacer(Modifier.height(6.dp))

        Text(
            text = if (isMuted) "mic off" else "your mic",
            fontSize = 10.sp,
            color = if (isMuted) AccentRed.copy(alpha = 0.7f) else TextTertiary,
            letterSpacing = 0.5.sp
        )
    }
}

@Composable
private fun BottomControls(
    state: VoiceChannelState,
    onMicToggle: () -> Unit,
    onSpeakerToggle: () -> Unit,
    onSubmitAnswer: (String) -> Unit,
    onEndSession: () -> Unit
) {
    var textInput by remember { mutableStateOf("") }
    var showTextInput by remember { mutableStateOf(false) }

    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(bottom = 24.dp, start = 16.dp, end = 16.dp),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        AnimatedVisibility(visible = state.isReconnecting) {
            Text(
                text = "reconnecting...",
                fontSize = 11.sp,
                color = Color(0xFFFFB547),
                letterSpacing = 0.5.sp,
                modifier = Modifier.padding(bottom = 12.dp)
            )
        }

        AnimatedVisibility(
            visible = showTextInput,
            enter = fadeIn(tween(250)) + expandVertically(tween(250)),
            exit = fadeOut(tween(200)) + shrinkVertically(tween(200))
        ) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(bottom = 14.dp)
                    .clip(RoundedCornerShape(24.dp))
                    .background(SurfaceWhite)
                    .padding(horizontal = 14.dp, vertical = 2.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                OutlinedTextField(
                    value = textInput,
                    onValueChange = { textInput = it },
                    placeholder = { Text("Type your answer...", fontSize = 14.sp, color = TextSecondary) },
                    colors = OutlinedTextFieldDefaults.colors(
                        focusedBorderColor = Color.Transparent,
                        unfocusedBorderColor = Color.Transparent,
                        focusedContainerColor = Color.Transparent,
                        unfocusedContainerColor = Color.Transparent,
                        focusedTextColor = TextPrimary,
                        unfocusedTextColor = TextPrimary
                    ),
                    singleLine = true,
                    keyboardOptions = KeyboardOptions(imeAction = ImeAction.Send),
                    keyboardActions = KeyboardActions(onSend = {
                        if (textInput.isNotBlank()) {
                            onSubmitAnswer(textInput.trim())
                            textInput = ""
                            showTextInput = false
                        }
                    }),
                    modifier = Modifier.weight(1f)
                )

                IconButton(
                    onClick = {
                        if (textInput.isNotBlank()) {
                            onSubmitAnswer(textInput.trim())
                            textInput = ""
                            showTextInput = false
                        }
                    },
                    enabled = textInput.isNotBlank()
                ) {
                    Icon(
                        imageVector = Icons.AutoMirrored.Filled.Send,
                        contentDescription = "Send answer",
                        tint = if (textInput.isNotBlank()) AccentBlue else TextTertiary
                    )
                }
            }
        }

        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceEvenly,
            verticalAlignment = Alignment.CenterVertically
        ) {
            ControlButton(
                icon = if (state.isSpeakerOn) Icons.AutoMirrored.Filled.VolumeUp else Icons.AutoMirrored.Filled.VolumeOff,
                label = if (state.isSpeakerOn) "speaker" else "earpiece",
                tint = if (state.isSpeakerOn) TextPrimary else TextSecondary,
                background = SurfaceWhite,
                size = 50.dp,
                onClick = onSpeakerToggle,
                contentDescription = if (state.isSpeakerOn) "Disable speaker" else "Enable speaker"
            )

            ControlButton(
                icon = Icons.AutoMirrored.Filled.Chat,
                label = if (showTextInput) "close text" else "chat",
                tint = if (showTextInput) AccentBlue else TextPrimary,
                background = if (showTextInput) AccentBlue.copy(alpha = 0.18f) else SurfaceWhite,
                size = 50.dp,
                onClick = { showTextInput = !showTextInput },
                contentDescription = if (showTextInput) "Close text input" else "Open text input"
            )

            ControlButton(
                icon = Icons.Default.CallEnd,
                label = "end",
                tint = TextPrimary,
                background = AccentRed.copy(alpha = 0.85f),
                size = 64.dp,
                iconSize = 28.dp,
                onClick = onEndSession,
                contentDescription = "End interview session"
            )

            val micScale by animateFloatAsState(
                targetValue = if (state.isMicMuted) 0.92f else 1f,
                animationSpec = spring(stiffness = Spring.StiffnessMedium),
                label = "mic_scale"
            )
            ControlButton(
                icon = if (state.isMicMuted) Icons.Default.MicOff else Icons.Default.Mic,
                label = if (state.isMicMuted) "unmute" else "mute",
                tint = if (state.isMicMuted) AccentRed else TextPrimary,
                background = if (state.isMicMuted)
                    AccentRed.copy(alpha = 0.15f)
                else
                    SurfaceWhite,
                size = 50.dp,
                scale = micScale,
                onClick = onMicToggle,
                contentDescription = if (state.isMicMuted) "Unmute microphone" else "Mute microphone",
                stateDesc = if (state.isMicMuted) "Muted" else "Active"
            )
        }

        Spacer(Modifier.height(8.dp))

        Text(
            text = "tap screen to toggle controls",
            fontSize = 10.sp,
            color = TextTertiary,
            letterSpacing = 0.3.sp
        )
    }
}

@Composable
private fun ControlButton(
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    label: String,
    tint: Color,
    background: Color,
    size: androidx.compose.ui.unit.Dp,
    iconSize: androidx.compose.ui.unit.Dp = 22.dp,
    scale: Float = 1f,
    onClick: () -> Unit,
    contentDescription: String,
    stateDesc: String? = null
) {
    Column(
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(6.dp)
    ) {
        Box(
            modifier = Modifier
                .size(size)
                .scale(scale)
                .clip(CircleShape)
                .background(background)
                .pointerInput(Unit) { detectTapGestures { onClick() } }
                .semantics {
                    this.contentDescription = contentDescription
                    stateDesc?.let { stateDescription = it }
                },
            contentAlignment = Alignment.Center
        ) {
            Icon(
                imageVector = icon,
                contentDescription = null,
                tint = tint,
                modifier = Modifier.size(iconSize)
            )
        }

        Text(
            text = label,
            fontSize = 10.sp,
            color = TextTertiary,
            letterSpacing = 0.3.sp
        )
    }
}

@Composable
private fun NetworkPill() {
    Box(
        modifier = Modifier
            .clip(RoundedCornerShape(20.dp))
            .background(AccentRed.copy(alpha = 0.15f))
            .padding(horizontal = 16.dp, vertical = 8.dp)
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Icon(
                imageVector = Icons.Default.WifiOff,
                contentDescription = null,
                tint = AccentRed,
                modifier = Modifier.size(14.dp)
            )
            Spacer(Modifier.width(6.dp))
            Text(
                text = "No connection",
                fontSize = 12.sp,
                color = AccentRed,
                fontWeight = FontWeight.Medium
            )
        }
    }
}

@Composable
private fun EndSessionDialog(onConfirm: () -> Unit, onDismiss: () -> Unit) {
    AlertDialog(
        onDismissRequest = onDismiss,
        containerColor = Color(0xFF1A1A2E),
        titleContentColor = TextPrimary,
        textContentColor = TextSecondary,
        title = { Text("End Interview?", fontWeight = FontWeight.SemiBold) },
        text = { Text("Your progress will be saved and you'll see your results.") },
        confirmButton = {
            Button(
                onClick = onConfirm,
                colors = ButtonDefaults.buttonColors(containerColor = AccentRed)
            ) { Text("End Session", color = TextPrimary) }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text("Keep Going", color = AccentBlue)
            }
        }
    )
}

@Composable
private fun ReconnectDialog(onReconnect: () -> Unit, onEnd: () -> Unit) {
    AlertDialog(
        onDismissRequest = {},
        containerColor = Color(0xFF1A1A2E),
        titleContentColor = TextPrimary,
        textContentColor = TextSecondary,
        title = { Text("Connection Lost", fontWeight = FontWeight.SemiBold) },
        text = { Text("We couldn't reconnect automatically. What would you like to do?") },
        confirmButton = {
            Button(
                onClick = onReconnect,
                colors = ButtonDefaults.buttonColors(containerColor = AccentBlue)
            ) {
                Icon(Icons.Default.Refresh, null, Modifier.size(14.dp))
                Spacer(Modifier.width(6.dp))
                Text("Reconnect", color = TextPrimary)
            }
        },
        dismissButton = {
            OutlinedButton(onClick = onEnd) {
                Text("End Session", color = TextSecondary)
            }
        }
    )
}

private fun formatDuration(seconds: Int): String {
    val m = seconds / 60
    val s = seconds % 60
    return "%02d:%02d".format(m, s)
}
