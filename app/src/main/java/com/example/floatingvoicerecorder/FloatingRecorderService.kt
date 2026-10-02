package com.example.floatingvoicerecorder

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.content.Context
import android.content.Intent
import android.graphics.PixelFormat
import android.media.MediaRecorder
import android.os.Build
import android.os.Handler
import android.os.IBinder
import android.os.Looper
import android.os.VibrationEffect
import android.os.Vibrator
import android.os.VibratorManager
import android.view.Gravity
import android.view.LayoutInflater
import android.view.MotionEvent
import android.view.View
import android.view.WindowManager
import android.widget.ImageButton
import android.widget.ImageView
import android.widget.TextView
import android.widget.Toast
import androidx.core.app.NotificationCompat
import com.example.R
import java.io.File
import java.util.Locale

class FloatingRecorderService : Service() {

    private var windowManager: WindowManager? = null
    private var overlayView: View? = null
    private var layoutParams: WindowManager.LayoutParams? = null

    private var mediaRecorder: MediaRecorder? = null
    private var currentRecordingFile: File? = null
    private var isRecording = false
    private var isPaused = false

    private var recordStartTime = 0L
    private var accumulatedTime = 0L
    private val timerHandler = Handler(Looper.getMainLooper())
    private val timerRunnable = object : Runnable {
        override fun run() {
            if (isRecording && !isPaused) {
                val elapsed = accumulatedTime + (System.currentTimeMillis() - recordStartTime)
                updateTimerDisplay(elapsed)
                timerHandler.postDelayed(this, 500)
            }
        }
    }

    private lateinit var tvTimer: TextView
    private lateinit var viewStatusIndicator: View
    private lateinit var btnRecordToggle: ImageButton
    private lateinit var btnStop: ImageButton
    private lateinit var btnClose: ImageButton
    private lateinit var btnDrag: ImageView

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onCreate() {
        super.onCreate()
        isRunning = true
        createNotificationChannel()
        startForeground(NOTIFICATION_ID, buildNotification("المسجل العائم جاهز", false))
        initOverlayWindow()
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        when (intent?.action) {
            ACTION_STOP_SERVICE -> {
                stopSelf()
            }
            ACTION_TOGGLE_RECORD -> {
                handleRecordToggle()
            }
            ACTION_STOP_RECORD -> {
                stopRecording()
            }
        }
        return START_NOT_STICKY
    }

    private fun initOverlayWindow() {
        windowManager = getSystemService(Context.WINDOW_SERVICE) as WindowManager
        val inflater = LayoutInflater.from(this)
        overlayView = inflater.inflate(R.layout.floating_recorder_overlay, null)

        val windowType = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY
        } else {
            @Suppress("DEPRECATION")
            WindowManager.LayoutParams.TYPE_PHONE
        }

        layoutParams = WindowManager.LayoutParams(
            WindowManager.LayoutParams.WRAP_CONTENT,
            WindowManager.LayoutParams.WRAP_CONTENT,
            windowType,
            WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE or
                    WindowManager.LayoutParams.FLAG_LAYOUT_NO_LIMITS,
            PixelFormat.TRANSLUCENT
        ).apply {
            gravity = Gravity.TOP or Gravity.START
            x = 100
            y = 200
        }

        bindViews()
        setupListeners()

        try {
            windowManager?.addView(overlayView, layoutParams)
        } catch (e: Exception) {
            e.printStackTrace()
            Toast.makeText(this, "فشل في عرض النافذة العائمة. يرجى التحقق من إذن الظهور.", Toast.LENGTH_LONG).show()
            stopSelf()
        }
    }

    private fun bindViews() {
        overlayView?.let { view ->
            tvTimer = view.findViewById(R.id.tvTimer)
            viewStatusIndicator = view.findViewById(R.id.viewStatusIndicator)
            btnRecordToggle = view.findViewById(R.id.btnRecordToggle)
            btnStop = view.findViewById(R.id.btnStop)
            btnClose = view.findViewById(R.id.btnClose)
            btnDrag = view.findViewById(R.id.btnDrag)
        }
    }

    private fun setupListeners() {
        // Drag logic
        var initialX = 0
        var initialY = 0
        var initialTouchX = 0f
        var initialTouchY = 0f

        val dragTouchListener = View.OnTouchListener { _, event ->
            when (event.action) {
                MotionEvent.ACTION_DOWN -> {
                    initialX = layoutParams?.x ?: 0
                    initialY = layoutParams?.y ?: 0
                    initialTouchX = event.rawX
                    initialTouchY = event.rawY
                    true
                }
                MotionEvent.ACTION_MOVE -> {
                    layoutParams?.x = initialX + (event.rawX - initialTouchX).toInt()
                    layoutParams?.y = initialY + (event.rawY - initialTouchY).toInt()
                    try {
                        windowManager?.updateViewLayout(overlayView, layoutParams)
                    } catch (e: Exception) {
                        e.printStackTrace()
                    }
                    true
                }
                else -> false
            }
        }

        btnDrag.setOnTouchListener(dragTouchListener)

        btnRecordToggle.setOnClickListener {
            handleRecordToggle()
        }

        btnStop.setOnClickListener {
            stopRecording()
        }

        btnClose.setOnClickListener {
            if (isRecording) {
                stopRecording()
            }
            stopSelf()
        }
    }

    private fun handleRecordToggle() {
        if (!isRecording) {
            startRecording()
        } else if (isRecording && !isPaused) {
            pauseRecording()
        } else if (isRecording && isPaused) {
            resumeRecording()
        }
    }

    private fun startRecording() {
        try {
            val repository = RecordingRepository.getInstance(this)
            currentRecordingFile = repository.getNewRecordingFile()

            @Suppress("DEPRECATION")
            mediaRecorder = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
                MediaRecorder(this)
            } else {
                MediaRecorder()
            }.apply {
                setAudioSource(MediaRecorder.AudioSource.MIC)
                setOutputFormat(MediaRecorder.OutputFormat.MPEG_4)
                setAudioEncoder(MediaRecorder.AudioEncoder.AAC)
                setAudioEncodingBitRate(128000)
                setAudioSamplingRate(44100)
                setOutputFile(currentRecordingFile?.absolutePath)
                prepare()
                start()
            }

            isRecording = true
            isPaused = false
            recordStartTime = System.currentTimeMillis()
            accumulatedTime = 0L

            vibrateDevice(50)
            updateNotification("جاري التسجيل الصوتي...", true)

            // UI updates
            viewStatusIndicator.setBackgroundResource(R.drawable.bg_indicator_recording)
            btnRecordToggle.setImageResource(R.drawable.ic_pause)
            btnStop.visibility = View.VISIBLE

            timerHandler.post(timerRunnable)
            Toast.makeText(this, "بدأ التسجيل", Toast.LENGTH_SHORT).show()
        } catch (e: Exception) {
            e.printStackTrace()
            Toast.makeText(this, "خطأ أثناء بدء التسجيل: ${e.localizedMessage}", Toast.LENGTH_SHORT).show()
            resetRecordingState()
        }
    }

    private fun pauseRecording() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.N && isRecording && !isPaused) {
            try {
                mediaRecorder?.pause()
                accumulatedTime += (System.currentTimeMillis() - recordStartTime)
                isPaused = true
                timerHandler.removeCallbacks(timerRunnable)

                viewStatusIndicator.setBackgroundResource(R.drawable.bg_indicator_ready)
                btnRecordToggle.setImageResource(R.drawable.ic_play)
                updateNotification("التسجيل متوقف مؤقتاً", false)
                vibrateDevice(30)
            } catch (e: Exception) {
                e.printStackTrace()
            }
        }
    }

    private fun resumeRecording() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.N && isRecording && isPaused) {
            try {
                mediaRecorder?.resume()
                recordStartTime = System.currentTimeMillis()
                isPaused = false

                viewStatusIndicator.setBackgroundResource(R.drawable.bg_indicator_recording)
                btnRecordToggle.setImageResource(R.drawable.ic_pause)
                timerHandler.post(timerRunnable)
                updateNotification("جاري التسجيل الصوتي...", true)
                vibrateDevice(30)
            } catch (e: Exception) {
                e.printStackTrace()
            }
        }
    }

    private fun stopRecording() {
        if (isRecording) {
            try {
                mediaRecorder?.stop()
                mediaRecorder?.release()
                vibrateDevice(60)
                Toast.makeText(this, "تم حفظ التسجيل بنجاح", Toast.LENGTH_SHORT).show()
                RecordingRepository.getInstance(this).refreshRecordings()
            } catch (e: Exception) {
                e.printStackTrace()
            }
        }
        resetRecordingState()
    }

    private fun resetRecordingState() {
        mediaRecorder = null
        isRecording = false
        isPaused = false
        accumulatedTime = 0L
        timerHandler.removeCallbacks(timerRunnable)

        tvTimer.text = "00:00"
        viewStatusIndicator.setBackgroundResource(R.drawable.bg_indicator_ready)
        btnRecordToggle.setImageResource(R.drawable.ic_mic)
        btnStop.visibility = View.GONE
        updateNotification("المسجل العائم جاهز", false)
    }

    private fun updateTimerDisplay(ms: Long) {
        val totalSec = ms / 1000
        val min = totalSec / 60
        val sec = totalSec % 60
        tvTimer.text = String.format(Locale.getDefault(), "%02d:%02d", min, sec)
    }

    private fun vibrateDevice(durationMs: Long) {
        try {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
                val vibratorManager = getSystemService(Context.VIBRATOR_MANAGER_SERVICE) as? VibratorManager
                vibratorManager?.defaultVibrator?.vibrate(
                    VibrationEffect.createOneShot(durationMs, VibrationEffect.DEFAULT_AMPLITUDE)
                )
            } else {
                @Suppress("DEPRECATION")
                val vibrator = getSystemService(Context.VIBRATOR_SERVICE) as? Vibrator
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                    vibrator?.vibrate(VibrationEffect.createOneShot(durationMs, VibrationEffect.DEFAULT_AMPLITUDE))
                } else {
                    @Suppress("DEPRECATION")
                    vibrator?.vibrate(durationMs)
                }
            }
        } catch (e: Exception) {
            e.printStackTrace()
        }
    }

    private fun createNotificationChannel() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val channel = NotificationChannel(
                CHANNEL_ID,
                "خدمة التسجيل العائم",
                NotificationManager.IMPORTANCE_LOW
            ).apply {
                description = "تحكم في مسجل الصوت العائم"
            }
            val manager = getSystemService(NotificationManager::class.java)
            manager.createNotificationChannel(channel)
        }
    }

    private fun buildNotification(content: String, recording: Boolean): Notification {
        val appIntent = packageManager.getLaunchIntentForPackage(packageName)
        val pendingIntent = PendingIntent.getActivity(
            this,
            0,
            appIntent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        val toggleIntent = Intent(this, FloatingRecorderService::class.java).apply {
            action = ACTION_TOGGLE_RECORD
        }
        val togglePendingIntent = PendingIntent.getService(
            this,
            1,
            toggleIntent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        val stopIntent = Intent(this, FloatingRecorderService::class.java).apply {
            action = ACTION_STOP_SERVICE
        }
        val stopPendingIntent = PendingIntent.getService(
            this,
            2,
            stopIntent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        val builder = NotificationCompat.Builder(this, CHANNEL_ID)
            .setSmallIcon(R.drawable.ic_mic)
            .setContentTitle("مسجل الصوت العائم")
            .setContentText(content)
            .setContentIntent(pendingIntent)
            .setOngoing(true)
            .setPriority(NotificationCompat.PRIORITY_LOW)

        if (recording) {
            builder.addAction(R.drawable.ic_pause, "إيقاف مؤقت", togglePendingIntent)
            builder.addAction(R.drawable.ic_stop, "إيقاف وحفظ", togglePendingIntent)
        } else {
            builder.addAction(R.drawable.ic_mic, "بدء التسجيل", togglePendingIntent)
            builder.addAction(R.drawable.ic_close, "إغلاق", stopPendingIntent)
        }

        return builder.build()
    }

    private fun updateNotification(content: String, recording: Boolean) {
        val manager = getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
        manager.notify(NOTIFICATION_ID, buildNotification(content, recording))
    }

    override fun onDestroy() {
        super.onDestroy()
        isRunning = false
        timerHandler.removeCallbacks(timerRunnable)
        if (isRecording) {
            stopRecording()
        }
        if (overlayView != null) {
            try {
                windowManager?.removeView(overlayView)
            } catch (e: Exception) {
                e.printStackTrace()
            }
            overlayView = null
        }
    }

    companion object {
        const val CHANNEL_ID = "floating_recorder_channel"
        const val NOTIFICATION_ID = 1001

        const val ACTION_STOP_SERVICE = "action_stop_service"
        const val ACTION_TOGGLE_RECORD = "action_toggle_record"
        const val ACTION_STOP_RECORD = "action_stop_record"

        var isRunning = false
            private set

        fun start(context: Context) {
            val intent = Intent(context, FloatingRecorderService::class.java)
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                context.startForegroundService(intent)
            } else {
                context.startService(intent)
            }
        }

        fun stop(context: Context) {
            val intent = Intent(context, FloatingRecorderService::class.java).apply {
                action = ACTION_STOP_SERVICE
            }
            context.startService(intent)
        }
    }
}
