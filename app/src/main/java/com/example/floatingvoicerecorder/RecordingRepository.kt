package com.example.floatingvoicerecorder

import android.content.Context
import android.content.Intent
import android.media.MediaMetadataRetriever
import android.net.Uri
import androidx.core.content.FileProvider
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import java.io.File
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

class RecordingRepository private constructor(private val context: Context) {

    private val _recordings = MutableStateFlow<List<AudioRecording>>(emptyList())
    val recordings: StateFlow<List<AudioRecording>> = _recordings.asStateFlow()

    private val recordingsDir: File
        get() {
            val dir = File(context.getExternalFilesDir(null), "Recordings")
            if (!dir.exists()) {
                dir.mkdirs()
            }
            return dir
        }

    init {
        refreshRecordings()
    }

    fun getNewRecordingFile(): File {
        val dateFormat = SimpleDateFormat("yyyyMMdd_HHmmss", Locale.getDefault())
        val fileName = "REC_${dateFormat.format(Date())}.m4a"
        return File(recordingsDir, fileName)
    }

    fun getTempRecordingFile(): File {
        val tempDir = File(context.cacheDir, "temp_recordings")
        if (!tempDir.exists()) {
            tempDir.mkdirs()
        }
        return File(tempDir, "temp_rec_${System.currentTimeMillis()}.m4a")
    }

    fun saveTempRecording(tempFile: File?): File? {
        if (tempFile == null || !tempFile.exists() || tempFile.length() == 0L) {
            return null
        }
        val targetFile = getNewRecordingFile()
        val success = tempFile.renameTo(targetFile) || try {
            tempFile.copyTo(targetFile, overwrite = true)
            tempFile.delete()
            true
        } catch (e: Exception) {
            false
        }
        if (success) {
            refreshRecordings()
            return targetFile
        }
        return null
    }

    fun discardTempRecording(tempFile: File?) {
        try {
            if (tempFile != null && tempFile.exists()) {
                tempFile.delete()
            }
        } catch (e: Exception) {
            e.printStackTrace()
        }
    }

    fun refreshRecordings() {
        val files = recordingsDir.listFiles { file ->
            file.isFile && (file.extension.equals("m4a", ignoreCase = true) ||
                    file.extension.equals("mp3", ignoreCase = true) ||
                    file.extension.equals("wav", ignoreCase = true) ||
                    file.extension.equals("3gp", ignoreCase = true))
        } ?: emptyArray()

        val list = files.map { file ->
            val duration = getFileDuration(file)
            val nameWithoutExt = file.nameWithoutExtension
            AudioRecording(
                id = file.absolutePath,
                title = nameWithoutExt,
                filePath = file.absolutePath,
                timestamp = file.lastModified(),
                durationMs = duration,
                fileSizeBytes = file.length()
            )
        }.sortedByDescending { it.timestamp }

        _recordings.value = list
    }

    private fun getFileDuration(file: File): Long {
        return try {
            val retriever = MediaMetadataRetriever()
            retriever.setDataSource(file.absolutePath)
            val timeString = retriever.extractMetadata(MediaMetadataRetriever.METADATA_KEY_DURATION)
            retriever.release()
            timeString?.toLongOrNull() ?: 0L
        } catch (e: Exception) {
            0L
        }
    }

    fun deleteRecording(recording: AudioRecording): Boolean {
        val file = File(recording.filePath)
        val deleted = if (file.exists()) file.delete() else false
        if (deleted) {
            refreshRecordings()
        }
        return deleted
    }

    fun renameRecording(recording: AudioRecording, newTitle: String): Boolean {
        val trimmed = newTitle.trim()
        if (trimmed.isEmpty()) return false
        val oldFile = File(recording.filePath)
        if (!oldFile.exists()) return false

        val extension = oldFile.extension
        val newFileName = if (extension.isNotEmpty()) "$trimmed.$extension" else trimmed
        val newFile = File(oldFile.parentFile, newFileName)
        if (newFile.exists()) return false

        val success = oldFile.renameTo(newFile)
        if (success) {
            refreshRecordings()
        }
        return success
    }

    fun shareRecording(recording: AudioRecording) {
        val file = File(recording.filePath)
        if (!file.exists()) return

        val uri: Uri = FileProvider.getUriForFile(
            context,
            "${context.packageName}.provider",
            file
        )

        val intent = Intent(Intent.ACTION_SEND).apply {
            type = "audio/*"
            putExtra(Intent.EXTRA_STREAM, uri)
            addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
            addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        }

        val chooser = Intent.createChooser(intent, "مشاركة التسجيل الصوتي").apply {
            addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        }
        context.startActivity(chooser)
    }

    companion object {
        @Volatile
        private var instance: RecordingRepository? = null

        fun getInstance(context: Context): RecordingRepository {
            return instance ?: synchronized(this) {
                instance ?: RecordingRepository(context.applicationContext).also { instance = it }
            }
        }
    }
}
