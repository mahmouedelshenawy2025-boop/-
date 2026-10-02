package com.example.floatingvoicerecorder

import android.content.Context
import android.media.MediaPlayer
import android.media.PlaybackParams
import android.os.Build
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import java.io.File

data class PlayerState(
    val currentRecording: AudioRecording? = null,
    val isPlaying: Boolean = false,
    val currentPositionMs: Long = 0L,
    val durationMs: Long = 0L,
    val playbackSpeed: Float = 1.0f
)

class AudioPlayerManager(private val context: Context) {

    private var mediaPlayer: MediaPlayer? = null
    private val scope = CoroutineScope(Dispatchers.Main)
    private var progressJob: Job? = null

    private val _playerState = MutableStateFlow(PlayerState())
    val playerState: StateFlow<PlayerState> = _playerState.asStateFlow()

    fun play(recording: AudioRecording) {
        val file = File(recording.filePath)
        if (!file.exists()) return

        if (_playerState.value.currentRecording?.id == recording.id && mediaPlayer != null) {
            if (!_playerState.value.isPlaying) {
                mediaPlayer?.start()
                _playerState.value = _playerState.value.copy(isPlaying = true)
                startProgressTracker()
            }
            return
        }

        stop()

        try {
            mediaPlayer = MediaPlayer().apply {
                setDataSource(file.absolutePath)
                prepare()
                setSpeedInternal(_playerState.value.playbackSpeed)
                start()

                setOnCompletionListener {
                    _playerState.value = _playerState.value.copy(
                        isPlaying = false,
                        currentPositionMs = duration.toLong()
                    )
                    progressJob?.cancel()
                }
            }

            _playerState.value = _playerState.value.copy(
                currentRecording = recording,
                isPlaying = true,
                currentPositionMs = 0L,
                durationMs = mediaPlayer?.duration?.toLong() ?: recording.durationMs
            )

            startProgressTracker()
        } catch (e: Exception) {
            e.printStackTrace()
            stop()
        }
    }

    fun pause() {
        mediaPlayer?.let {
            if (it.isPlaying) {
                it.pause()
                _playerState.value = _playerState.value.copy(isPlaying = false)
                progressJob?.cancel()
            }
        }
    }

    fun seekTo(positionMs: Long) {
        mediaPlayer?.let {
            it.seekTo(positionMs.toInt())
            _playerState.value = _playerState.value.copy(currentPositionMs = positionMs)
        }
    }

    fun setPlaybackSpeed(speed: Float) {
        _playerState.value = _playerState.value.copy(playbackSpeed = speed)
        setSpeedInternal(speed)
    }

    private fun setSpeedInternal(speed: Float) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
            try {
                mediaPlayer?.let {
                    val params = it.playbackParams
                    params.speed = speed
                    it.playbackParams = params
                }
            } catch (e: Exception) {
                e.printStackTrace()
            }
        }
    }

    fun stop() {
        progressJob?.cancel()
        try {
            mediaPlayer?.stop()
            mediaPlayer?.release()
        } catch (e: Exception) {
            e.printStackTrace()
        }
        mediaPlayer = null
        _playerState.value = _playerState.value.copy(
            isPlaying = false,
            currentPositionMs = 0L
        )
    }

    private fun startProgressTracker() {
        progressJob?.cancel()
        progressJob = scope.launch {
            while (isActive) {
                mediaPlayer?.let { player ->
                    if (player.isPlaying) {
                        _playerState.value = _playerState.value.copy(
                            currentPositionMs = player.currentPosition.toLong(),
                            durationMs = player.duration.toLong()
                        )
                    }
                }
                delay(200)
            }
        }
    }

    fun release() {
        stop()
    }
}
