package com.example.floatingvoicerecorder

data class AudioRecording(
    val id: String,
    val title: String,
    val filePath: String,
    val timestamp: Long,
    val durationMs: Long,
    val fileSizeBytes: Long
)
