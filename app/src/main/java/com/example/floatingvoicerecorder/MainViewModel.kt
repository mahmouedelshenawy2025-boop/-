package com.example.floatingvoicerecorder

import android.app.Application
import android.content.Context
import android.content.pm.PackageManager
import android.media.MediaRecorder
import android.os.Build
import android.provider.Settings
import androidx.core.content.ContextCompat
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import java.io.File

data class PermissionsState(
    val hasMic: Boolean = false,
    val hasOverlay: Boolean = false,
    val hasNotification: Boolean = false
) {
    val canStartFloating: Boolean
        get() = hasMic && hasOverlay
}

data class InAppRecorderState(
    val isRecording: Boolean = false,
    val isPaused: Boolean = false,
    val durationMs: Long = 0L,
    val waveAmplitudes: List<Float> = emptyList()
)

class MainViewModel(application: Application) : AndroidViewModel(application) {

    private val repository = RecordingRepository.getInstance(application)
    val playerManager = AudioPlayerManager(application)

    private val _permissionsState = MutableStateFlow(PermissionsState())
    val permissionsState: StateFlow<PermissionsState> = _permissionsState.asStateFlow()

    private val _isFloatingServiceRunning = MutableStateFlow(false)
    val isFloatingServiceRunning: StateFlow<Boolean> = _isFloatingServiceRunning.asStateFlow()

    private val _inAppRecorderState = MutableStateFlow(InAppRecorderState())
    val inAppRecorderState: StateFlow<InAppRecorderState> = _inAppRecorderState.asStateFlow()

    private val _searchQuery = MutableStateFlow("")
    val searchQuery: StateFlow<String> = _searchQuery.asStateFlow()

    val filteredRecordings: StateFlow<List<AudioRecording>> = combine(
        repository.recordings,
        _searchQuery
    ) { recordings, query ->
        if (query.isBlank()) {
            recordings
        } else {
            recordings.filter { it.title.contains(query, ignoreCase = true) }
        }
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    val playerState: StateFlow<PlayerState> = playerManager.playerState

    // In-app recording fields
    private var inAppMediaRecorder: MediaRecorder? = null
    private var inAppFile: File? = null
    private var recordingStartTime = 0L
    private var accumulatedDuration = 0L
    private var recordingTimerJob: Job? = null

    init {
        refreshState()
    }

    fun refreshState() {
        checkPermissions()
        _isFloatingServiceRunning.value = FloatingRecorderService.isRunning
        repository.refreshRecordings()
    }

    fun updateSearchQuery(query: String) {
        _searchQuery.value = query
    }

    fun checkPermissions() {
        val context = getApplication<Application>()
        val hasMic = ContextCompat.checkSelfPermission(
            context,
            android.Manifest.permission.RECORD_AUDIO
        ) == PackageManager.PERMISSION_GRANTED

        val hasOverlay = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
            Settings.canDrawOverlays(context)
        } else {
            true
        }

        val hasNotification = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            ContextCompat.checkSelfPermission(
                context,
                android.Manifest.permission.POST_NOTIFICATIONS
            ) == PackageManager.PERMISSION_GRANTED
        } else {
            true
        }

        _permissionsState.value = PermissionsState(
            hasMic = hasMic,
            hasOverlay = hasOverlay,
            hasNotification = hasNotification
        )
    }

    fun startFloatingService() {
        val context = getApplication<Application>()
        FloatingRecorderService.start(context)
        _isFloatingServiceRunning.value = true
    }

    fun stopFloatingService() {
        val context = getApplication<Application>()
        FloatingRecorderService.stop(context)
        _isFloatingServiceRunning.value = false
    }

    // --- In-App Recording Controls ---
    fun startInAppRecording() {
        if (!_permissionsState.value.hasMic) return
        val context = getApplication<Application>()

        try {
            inAppFile = repository.getNewRecordingFile()

            @Suppress("DEPRECATION")
            inAppMediaRecorder = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
                MediaRecorder(context)
            } else {
                MediaRecorder()
            }.apply {
                setAudioSource(MediaRecorder.AudioSource.MIC)
                setOutputFormat(MediaRecorder.OutputFormat.MPEG_4)
                setAudioEncoder(MediaRecorder.AudioEncoder.AAC)
                setAudioEncodingBitRate(128000)
                setAudioSamplingRate(44100)
                setOutputFile(inAppFile?.absolutePath)
                prepare()
                start()
            }

            recordingStartTime = System.currentTimeMillis()
            accumulatedDuration = 0L

            _inAppRecorderState.value = InAppRecorderState(
                isRecording = true,
                isPaused = false,
                durationMs = 0L,
                waveAmplitudes = emptyList()
            )

            startRecordingJob()
        } catch (e: Exception) {
            e.printStackTrace()
            resetInAppRecording()
        }
    }

    fun pauseInAppRecording() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.N &&
            _inAppRecorderState.value.isRecording && !_inAppRecorderState.value.isPaused
        ) {
            try {
                inAppMediaRecorder?.pause()
                accumulatedDuration += System.currentTimeMillis() - recordingStartTime
                recordingTimerJob?.cancel()
                _inAppRecorderState.value = _inAppRecorderState.value.copy(
                    isPaused = true,
                    durationMs = accumulatedDuration
                )
            } catch (e: Exception) {
                e.printStackTrace()
            }
        }
    }

    fun resumeInAppRecording() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.N &&
            _inAppRecorderState.value.isRecording && _inAppRecorderState.value.isPaused
        ) {
            try {
                inAppMediaRecorder?.resume()
                recordingStartTime = System.currentTimeMillis()
                _inAppRecorderState.value = _inAppRecorderState.value.copy(isPaused = false)
                startRecordingJob()
            } catch (e: Exception) {
                e.printStackTrace()
            }
        }
    }

    fun stopInAppRecording() {
        if (_inAppRecorderState.value.isRecording) {
            try {
                inAppMediaRecorder?.stop()
                inAppMediaRecorder?.release()
                repository.refreshRecordings()
            } catch (e: Exception) {
                e.printStackTrace()
            }
        }
        resetInAppRecording()
    }

    private fun resetInAppRecording() {
        inAppMediaRecorder = null
        inAppFile = null
        recordingTimerJob?.cancel()
        accumulatedDuration = 0L
        _inAppRecorderState.value = InAppRecorderState()
    }

    private fun startRecordingJob() {
        recordingTimerJob?.cancel()
        recordingTimerJob = viewModelScope.launch {
            while (isActive) {
                val elapsed = accumulatedDuration + (System.currentTimeMillis() - recordingStartTime)
                val amp = try {
                    val maxAmp = inAppMediaRecorder?.maxAmplitude ?: 0
                    (maxAmp / 32767f).coerceIn(0.05f, 1f)
                } catch (e: Exception) {
                    0.1f
                }

                val currentAmps = _inAppRecorderState.value.waveAmplitudes.toMutableList()
                currentAmps.add(amp)
                if (currentAmps.size > 40) {
                    currentAmps.removeAt(0)
                }

                _inAppRecorderState.value = _inAppRecorderState.value.copy(
                    durationMs = elapsed,
                    waveAmplitudes = currentAmps
                )
                delay(100)
            }
        }
    }

    // --- Audio Player Controls ---
    fun playAudio(recording: AudioRecording) {
        playerManager.play(recording)
    }

    fun pauseAudio() {
        playerManager.pause()
    }

    fun seekAudio(positionMs: Long) {
        playerManager.seekTo(positionMs)
    }

    fun setSpeed(speed: Float) {
        playerManager.setPlaybackSpeed(speed)
    }

    // --- Recording Management ---
    fun renameRecording(recording: AudioRecording, newTitle: String): Boolean {
        return repository.renameRecording(recording, newTitle)
    }

    fun deleteRecording(recording: AudioRecording): Boolean {
        if (playerState.value.currentRecording?.id == recording.id) {
            playerManager.stop()
        }
        return repository.deleteRecording(recording)
    }

    fun shareRecording(recording: AudioRecording) {
        repository.shareRecording(recording)
    }

    override fun onCleared() {
        super.onCleared()
        playerManager.release()
        resetInAppRecording()
    }
}
