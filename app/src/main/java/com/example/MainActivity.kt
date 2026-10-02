package com.example

import android.content.Intent
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.provider.Settings
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.result.contract.ActivityResultContracts
import androidx.activity.viewModels
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.Layers
import androidx.compose.material.icons.filled.Mic
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material.icons.filled.Pause
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.Share
import androidx.compose.material.icons.filled.Speed
import androidx.compose.material.icons.filled.Stop
import androidx.compose.material.icons.filled.VolumeUp
import androidx.compose.material.icons.outlined.CheckCircle
import androidx.compose.material.icons.outlined.WarningAmber
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FilterChipDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Slider
import androidx.compose.material3.SliderDefaults
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner
import com.example.floatingvoicerecorder.AudioRecording
import com.example.floatingvoicerecorder.MainViewModel
import com.example.floatingvoicerecorder.PermissionsState
import com.example.floatingvoicerecorder.PlayerState
import com.example.ui.theme.MyApplicationTheme
import com.example.ui.theme.ReadyGreen
import com.example.ui.theme.RecordingRed
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

class MainActivity : ComponentActivity() {

    private val viewModel: MainViewModel by viewModels()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()

        setContent {
            MyApplicationTheme {
                val lifecycleOwner = LocalLifecycleOwner.current
                DisposableEffect(lifecycleOwner) {
                    val observer = LifecycleEventObserver { _, event ->
                        if (event == Lifecycle.Event.ON_RESUME) {
                            viewModel.refreshState()
                        }
                    }
                    lifecycleOwner.lifecycle.addObserver(observer)
                    onDispose {
                        lifecycleOwner.lifecycle.removeObserver(observer)
                    }
                }

                FloatingRecorderApp(viewModel = viewModel)
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun FloatingRecorderApp(viewModel: MainViewModel) {
    val context = LocalContext.current
    val permissionsState by viewModel.permissionsState.collectAsState()
    val isFloatingRunning by viewModel.isFloatingServiceRunning.collectAsState()
    val inAppState by viewModel.inAppRecorderState.collectAsState()
    val recordings by viewModel.filteredRecordings.collectAsState()
    val playerState by viewModel.playerState.collectAsState()
    val searchQuery by viewModel.searchQuery.collectAsState()

    // Permission Launchers
    val requestPermissionsLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.RequestMultiplePermissions()
    ) {
        viewModel.checkPermissions()
    }

    val overlayPermissionLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.StartActivityForResult()
    ) {
        viewModel.checkPermissions()
    }

    fun requestAppPermissions() {
        val permissions = mutableListOf(android.Manifest.permission.RECORD_AUDIO)
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            permissions.add(android.Manifest.permission.POST_NOTIFICATIONS)
        }
        requestPermissionsLauncher.launch(permissions.toTypedArray())
    }

    fun openOverlaySettings() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
            val intent = Intent(
                Settings.ACTION_MANAGE_OVERLAY_PERMISSION,
                Uri.parse("package:${context.packageName}")
            )
            overlayPermissionLauncher.launch(intent)
        }
    }

    var recordingToDelete by remember { mutableStateOf<AudioRecording?>(null) }
    var recordingToRename by remember { mutableStateOf<AudioRecording?>(null) }
    var renameNewTitle by remember { mutableStateOf("") }

    Scaffold(
        modifier = Modifier.fillMaxSize(),
        topBar = {
            TopAppBar(
                title = {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Surface(
                            shape = CircleShape,
                            color = MaterialTheme.colorScheme.primaryContainer,
                            modifier = Modifier.size(38.dp)
                        ) {
                            Box(contentAlignment = Alignment.Center) {
                                Icon(
                                    imageVector = Icons.Default.Mic,
                                    contentDescription = null,
                                    tint = MaterialTheme.colorScheme.onPrimaryContainer,
                                    modifier = Modifier.size(22.dp)
                                )
                            }
                        }
                        Spacer(modifier = Modifier.width(10.dp))
                        Column {
                            Text(
                                text = stringResource(id = R.string.app_name_ar),
                                fontWeight = FontWeight.Bold,
                                fontSize = 18.sp
                            )
                            Text(
                                text = "Floating Voice Recorder",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = MaterialTheme.colorScheme.surface
                )
            )
        }
    ) { innerPadding ->
        LazyColumn(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding),
            contentPadding = PaddingValues(16.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {

            // Permission Warning Card if missing permissions
            if (!permissionsState.hasMic || !permissionsState.hasOverlay) {
                item {
                    PermissionsCard(
                        permissionsState = permissionsState,
                        onRequestMic = { requestAppPermissions() },
                        onRequestOverlay = { openOverlaySettings() }
                    )
                }
            }

            // Floating Recorder Card
            item {
                FloatingControllerCard(
                    isRunning = isFloatingRunning,
                    canStart = permissionsState.canStartFloating,
                    onToggleService = {
                        if (isFloatingRunning) {
                            viewModel.stopFloatingService()
                        } else {
                            if (!permissionsState.hasOverlay) {
                                Toast.makeText(
                                    context,
                                    "يرجى تفعيل إذن الظهور فوق التطبيقات أولاً",
                                    Toast.LENGTH_SHORT
                                ).show()
                                openOverlaySettings()
                            } else if (!permissionsState.hasMic) {
                                Toast.makeText(
                                    context,
                                    "يرجى تفعيل إذن الميكروفون أولاً",
                                    Toast.LENGTH_SHORT
                                ).show()
                                requestAppPermissions()
                            } else {
                                viewModel.startFloatingService()
                            }
                        }
                    }
                )
            }

            // In-App Instant Recorder Card
            item {
                InAppRecorderCard(
                    isRecording = inAppState.isRecording,
                    isPaused = inAppState.isPaused,
                    durationMs = inAppState.durationMs,
                    amplitudes = inAppState.waveAmplitudes,
                    canRecord = permissionsState.hasMic,
                    onStart = {
                        if (permissionsState.hasMic) {
                            viewModel.startInAppRecording()
                        } else {
                            requestAppPermissions()
                        }
                    },
                    onPause = { viewModel.pauseInAppRecording() },
                    onResume = { viewModel.resumeInAppRecording() },
                    onStop = { viewModel.stopInAppRecording() }
                )
            }

            // Audio Player Bar (when audio is active/playing)
            if (playerState.currentRecording != null) {
                item {
                    AudioPlayerBar(
                        playerState = playerState,
                        onPlayPause = {
                            if (playerState.isPlaying) {
                                viewModel.pauseAudio()
                            } else {
                                playerState.currentRecording?.let { viewModel.playAudio(it) }
                            }
                        },
                        onSeek = { viewModel.seekAudio(it) },
                        onSpeedSelected = { viewModel.setSpeed(it) }
                    )
                }
            }

            // Recordings Section Header & Search
            item {
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(
                            text = stringResource(id = R.string.recordings_title),
                            style = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.Bold
                        )
                        Surface(
                            shape = CircleShape,
                            color = MaterialTheme.colorScheme.primary.copy(alpha = 0.12f)
                        ) {
                            Text(
                                text = "${recordings.size}",
                                modifier = Modifier.padding(horizontal = 10.dp, vertical = 4.dp),
                                style = MaterialTheme.typography.labelMedium,
                                color = MaterialTheme.colorScheme.primary,
                                fontWeight = FontWeight.Bold
                            )
                        }
                    }

                    // Search Field
                    OutlinedTextField(
                        value = searchQuery,
                        onValueChange = { viewModel.updateSearchQuery(it) },
                        modifier = Modifier
                            .fillMaxWidth()
                            .testTag("search_recordings_input"),
                        placeholder = { Text("بحث في التسجيلات الصوتية...") },
                        leadingIcon = {
                            Icon(Icons.Default.Search, contentDescription = "Search")
                        },
                        trailingIcon = {
                            if (searchQuery.isNotEmpty()) {
                                IconButton(onClick = { viewModel.updateSearchQuery("") }) {
                                    Icon(Icons.Default.Close, contentDescription = "Clear search")
                                }
                            }
                        },
                        shape = RoundedCornerShape(14.dp),
                        singleLine = true
                    )
                }
            }

            // Recordings List
            if (recordings.isEmpty()) {
                item {
                    Card(
                        modifier = Modifier.fillMaxWidth(),
                        colors = CardDefaults.cardColors(
                            containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f)
                        ),
                        shape = RoundedCornerShape(16.dp)
                    ) {
                        Column(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(32.dp),
                            horizontalAlignment = Alignment.CenterHorizontally
                        ) {
                            Icon(
                                imageVector = Icons.Default.Mic,
                                contentDescription = null,
                                tint = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.5f),
                                modifier = Modifier.size(48.dp)
                            )
                            Spacer(modifier = Modifier.height(12.dp))
                            Text(
                                text = if (searchQuery.isNotEmpty()) "لا توجد نتائج مطابقة لبحثك" else stringResource(id = R.string.no_recordings),
                                style = MaterialTheme.typography.bodyMedium,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                    }
                }
            } else {
                items(recordings, key = { it.id }) { recording ->
                    val isCurrentTrack = playerState.currentRecording?.id == recording.id
                    RecordingItemCard(
                        recording = recording,
                        isCurrentTrack = isCurrentTrack,
                        isPlaying = isCurrentTrack && playerState.isPlaying,
                        onPlayClick = {
                            if (isCurrentTrack && playerState.isPlaying) {
                                viewModel.pauseAudio()
                            } else {
                                viewModel.playAudio(recording)
                            }
                        },
                        onRenameClick = {
                            recordingToRename = recording
                            renameNewTitle = recording.title
                        },
                        onShareClick = { viewModel.shareRecording(recording) },
                        onDeleteClick = { recordingToDelete = recording }
                    )
                }
            }
        }
    }

    // Delete Confirmation Dialog
    if (recordingToDelete != null) {
        AlertDialog(
            onDismissRequest = { recordingToDelete = null },
            title = { Text(stringResource(id = R.string.delete_confirm_title)) },
            text = { Text(stringResource(id = R.string.delete_confirm_msg)) },
            confirmButton = {
                Button(
                    onClick = {
                        recordingToDelete?.let { viewModel.deleteRecording(it) }
                        recordingToDelete = null
                    },
                    colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.error)
                ) {
                    Text(stringResource(id = R.string.delete))
                }
            },
            dismissButton = {
                TextButton(onClick = { recordingToDelete = null }) {
                    Text(stringResource(id = R.string.cancel))
                }
            }
        )
    }

    // Rename Dialog
    if (recordingToRename != null) {
        AlertDialog(
            onDismissRequest = { recordingToRename = null },
            title = { Text(stringResource(id = R.string.rename)) },
            text = {
                OutlinedTextField(
                    value = renameNewTitle,
                    onValueChange = { renameNewTitle = it },
                    label = { Text("اسم التسجيل الجديد") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth()
                )
            },
            confirmButton = {
                Button(
                    onClick = {
                        recordingToRename?.let {
                            viewModel.renameRecording(it, renameNewTitle)
                        }
                        recordingToRename = null
                    }
                ) {
                    Text(stringResource(id = R.string.save))
                }
            },
            dismissButton = {
                TextButton(onClick = { recordingToRename = null }) {
                    Text(stringResource(id = R.string.cancel))
                }
            }
        )
    }
}

@Composable
fun PermissionsCard(
    permissionsState: PermissionsState,
    onRequestMic: () -> Unit,
    onRequestOverlay: () -> Unit
) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.errorContainer.copy(alpha = 0.35f)
        ),
        shape = RoundedCornerShape(16.dp)
    ) {
        Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(
                    imageVector = Icons.Outlined.WarningAmber,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.error
                )
                Spacer(modifier = Modifier.width(8.dp))
                Text(
                    text = "أذونات مطلوبة لتشغيل التطبيق",
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.error
                )
            }

            if (!permissionsState.hasOverlay) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Column(modifier = Modifier.weight(1f)) {
                        Text(
                            text = stringResource(id = R.string.perm_overlay_title),
                            fontWeight = FontWeight.SemiBold,
                            fontSize = 14.sp
                        )
                        Text(
                            text = stringResource(id = R.string.perm_overlay_desc),
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                    Button(
                        onClick = onRequestOverlay,
                        modifier = Modifier.testTag("grant_overlay_permission_button")
                    ) {
                        Text(stringResource(id = R.string.grant_permission))
                    }
                }
            }

            if (!permissionsState.hasMic) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Column(modifier = Modifier.weight(1f)) {
                        Text(
                            text = stringResource(id = R.string.perm_mic_title),
                            fontWeight = FontWeight.SemiBold,
                            fontSize = 14.sp
                        )
                        Text(
                            text = stringResource(id = R.string.perm_mic_desc),
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                    Button(
                        onClick = onRequestMic,
                        modifier = Modifier.testTag("grant_mic_permission_button")
                    ) {
                        Text(stringResource(id = R.string.grant_permission))
                    }
                }
            }
        }
    }
}

@Composable
fun FloatingControllerCard(
    isRunning: Boolean,
    canStart: Boolean,
    onToggleService: () -> Unit
) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(20.dp),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.35f)
        )
    ) {
        Column(
            modifier = Modifier.padding(18.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Surface(
                        shape = CircleShape,
                        color = if (isRunning) ReadyGreen.copy(alpha = 0.2f) else MaterialTheme.colorScheme.surfaceVariant,
                        modifier = Modifier.size(42.dp)
                    ) {
                        Box(contentAlignment = Alignment.Center) {
                            Icon(
                                imageVector = Icons.Default.Layers,
                                contentDescription = null,
                                tint = if (isRunning) ReadyGreen else MaterialTheme.colorScheme.primary,
                                modifier = Modifier.size(24.dp)
                            )
                        }
                    }
                    Spacer(modifier = Modifier.width(12.dp))
                    Column {
                        Text(
                            text = "المسجل العائم (Overlay)",
                            fontWeight = FontWeight.Bold,
                            fontSize = 16.sp
                        )
                        Text(
                            text = if (isRunning) "النافذة العائمة نشطة الآن على الشاشة" else "نافذة عائمة تطفو فوق جميع التطبيقات",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }

                Surface(
                    shape = RoundedCornerShape(12.dp),
                    color = if (isRunning) ReadyGreen.copy(alpha = 0.2f) else MaterialTheme.colorScheme.surfaceVariant
                ) {
                    Text(
                        text = if (isRunning) "نشط ✓" else "متوقف",
                        color = if (isRunning) ReadyGreen else MaterialTheme.colorScheme.onSurfaceVariant,
                        fontWeight = FontWeight.Bold,
                        fontSize = 12.sp,
                        modifier = Modifier.padding(horizontal = 10.dp, vertical = 4.dp)
                    )
                }
            }

            Text(
                text = "تتيح لك النافذة العائمة بدء وإيقاف التسجيل الصوتي بلمسة واحدة دون الحاجة إلى مغادرة التطبيقات الأخرى مثل واتساب، تليجرام، أو المتصفح.",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                lineHeight = 18.sp
            )

            Button(
                onClick = onToggleService,
                modifier = Modifier
                    .fillMaxWidth()
                    .height(48.dp)
                    .testTag("toggle_floating_service_button"),
                colors = ButtonDefaults.buttonColors(
                    containerColor = if (isRunning) RecordingRed else MaterialTheme.colorScheme.primary
                ),
                shape = RoundedCornerShape(14.dp)
            ) {
                Icon(
                    imageVector = if (isRunning) Icons.Default.Close else Icons.Default.Layers,
                    contentDescription = null,
                    modifier = Modifier.size(20.dp)
                )
                Spacer(modifier = Modifier.width(8.dp))
                Text(
                    text = if (isRunning) stringResource(id = R.string.btn_stop_floating) else stringResource(id = R.string.btn_start_floating),
                    fontWeight = FontWeight.Bold
                )
            }
        }
    }
}

@Composable
fun InAppRecorderCard(
    isRecording: Boolean,
    isPaused: Boolean,
    durationMs: Long,
    amplitudes: List<Float>,
    canRecord: Boolean,
    onStart: () -> Unit,
    onPause: () -> Unit,
    onResume: () -> Unit,
    onStop: () -> Unit
) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(20.dp),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.45f)
        )
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(20.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = stringResource(id = R.string.quick_record_title),
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold
                )

                if (isRecording) {
                    Surface(
                        shape = RoundedCornerShape(12.dp),
                        color = if (isPaused) Color(0xFFFF9800).copy(alpha = 0.2f) else RecordingRed.copy(alpha = 0.2f)
                    ) {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp)
                        ) {
                            Box(
                                modifier = Modifier
                                    .size(8.dp)
                                    .background(
                                        if (isPaused) Color(0xFFFF9800) else RecordingRed,
                                        CircleShape
                                    )
                            )
                            Spacer(modifier = Modifier.width(6.dp))
                            Text(
                                text = if (isPaused) "متوقف مؤقتاً" else "جاري التسجيل",
                                fontSize = 12.sp,
                                fontWeight = FontWeight.Bold,
                                color = if (isPaused) Color(0xFFFF9800) else RecordingRed
                            )
                        }
                    }
                }
            }

            // Timer Display
            val formattedTime = formatDuration(durationMs)
            Text(
                text = formattedTime,
                fontSize = 42.sp,
                fontWeight = FontWeight.Bold,
                color = if (isRecording && !isPaused) RecordingRed else MaterialTheme.colorScheme.onSurface
            )

            // Live Audio Wave Visualizer
            if (isRecording) {
                AudioWaveVisualizer(
                    amplitudes = amplitudes,
                    isPaused = isPaused,
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(60.dp)
                )
            }

            // Controls
            Row(
                horizontalArrangement = Arrangement.spacedBy(16.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                if (!isRecording) {
                    // Big Start Button
                    val infiniteTransition = rememberInfiniteTransition(label = "pulse")
                    val pulseScale by infiniteTransition.animateFloat(
                        initialValue = 1.0f,
                        targetValue = 1.08f,
                        animationSpec = infiniteRepeatable(
                            animation = tween(1000, easing = FastOutSlowInEasing),
                            repeatMode = RepeatMode.Reverse
                        ),
                        label = "pulseScale"
                    )

                    Button(
                        onClick = onStart,
                        modifier = Modifier
                            .size(72.dp)
                            .testTag("in_app_start_record_button"),
                        shape = CircleShape,
                        colors = ButtonDefaults.buttonColors(containerColor = RecordingRed),
                        elevation = ButtonDefaults.buttonElevation(defaultElevation = 6.dp)
                    ) {
                        Icon(
                            imageVector = Icons.Default.Mic,
                            contentDescription = "Start Recording",
                            modifier = Modifier.size(32.dp),
                            tint = Color.White
                        )
                    }
                } else {
                    // Pause / Resume Button
                    OutlinedButton(
                        onClick = if (isPaused) onResume else onPause,
                        modifier = Modifier
                            .size(54.dp)
                            .testTag("in_app_pause_record_button"),
                        shape = CircleShape,
                        contentPadding = PaddingValues(0.dp)
                    ) {
                        Icon(
                            imageVector = if (isPaused) Icons.Default.PlayArrow else Icons.Default.Pause,
                            contentDescription = if (isPaused) "Resume" else "Pause",
                            modifier = Modifier.size(26.dp)
                        )
                    }

                    // Stop and Save Button
                    Button(
                        onClick = onStop,
                        modifier = Modifier
                            .size(64.dp)
                            .testTag("in_app_stop_record_button"),
                        shape = CircleShape,
                        colors = ButtonDefaults.buttonColors(containerColor = RecordingRed)
                    ) {
                        Icon(
                            imageVector = Icons.Default.Stop,
                            contentDescription = "Stop and Save",
                            modifier = Modifier.size(30.dp),
                            tint = Color.White
                        )
                    }
                }
            }
        }
    }
}

@Composable
fun AudioWaveVisualizer(
    amplitudes: List<Float>,
    isPaused: Boolean,
    modifier: Modifier = Modifier
) {
    val barColor = if (isPaused) MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.5f) else MaterialTheme.colorScheme.primary

    Canvas(modifier = modifier) {
        val count = amplitudes.size.coerceAtLeast(1)
        val barWidth = (size.width / (count * 1.5f)).coerceIn(4f, 16f)
        val spacing = barWidth * 0.5f
        val totalWidth = count * (barWidth + spacing)
        val startX = (size.width - totalWidth) / 2f

        amplitudes.forEachIndexed { index, amp ->
            val barHeight = (size.height * amp).coerceIn(8f, size.height)
            val left = startX + index * (barWidth + spacing)
            val top = (size.height - barHeight) / 2f

            drawRoundRect(
                color = barColor,
                topLeft = Offset(left, top),
                size = Size(barWidth, barHeight),
                cornerRadius = CornerRadius(barWidth / 2f, barWidth / 2f)
            )
        }
    }
}

@Composable
fun AudioPlayerBar(
    playerState: PlayerState,
    onPlayPause: () -> Unit,
    onSeek: (Long) -> Unit,
    onSpeedSelected: (Float) -> Unit
) {
    val recording = playerState.currentRecording ?: return

    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(18.dp),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.45f)
        ),
        elevation = CardDefaults.cardElevation(defaultElevation = 2.dp)
    ) {
        Column(
            modifier = Modifier.padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Row(
                    modifier = Modifier.weight(1f),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Icon(
                        imageVector = Icons.Default.VolumeUp,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.primary
                    )
                    Spacer(modifier = Modifier.width(8.dp))
                    Column {
                        Text(
                            text = recording.title,
                            fontWeight = FontWeight.Bold,
                            fontSize = 15.sp,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                        Text(
                            text = "${formatDuration(playerState.currentPositionMs)} / ${formatDuration(playerState.durationMs)}",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }

                // Play / Pause Button
                IconButton(
                    onClick = onPlayPause,
                    modifier = Modifier
                        .size(46.dp)
                        .background(MaterialTheme.colorScheme.primary, CircleShape)
                        .testTag("audio_player_play_pause_button")
                ) {
                    Icon(
                        imageVector = if (playerState.isPlaying) Icons.Default.Pause else Icons.Default.PlayArrow,
                        contentDescription = "Play or Pause",
                        tint = Color.White
                    )
                }
            }

            // Progress Slider
            val maxPos = if (playerState.durationMs > 0) playerState.durationMs.toFloat() else 1f
            Slider(
                value = playerState.currentPositionMs.toFloat().coerceIn(0f, maxPos),
                onValueChange = { onSeek(it.toLong()) },
                valueRange = 0f..maxPos,
                modifier = Modifier.fillMaxWidth(),
                colors = SliderDefaults.colors(
                    thumbColor = MaterialTheme.colorScheme.primary,
                    activeTrackColor = MaterialTheme.colorScheme.primary
                )
            )

            // Speed Selector Chips
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = "السرعة:",
                    style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                listOf(1.0f, 1.25f, 1.5f, 2.0f).forEach { speed ->
                    val isSelected = playerState.playbackSpeed == speed
                    FilterChip(
                        selected = isSelected,
                        onClick = { onSpeedSelected(speed) },
                        label = { Text("${speed}x", fontSize = 12.sp) },
                        colors = FilterChipDefaults.filterChipColors(
                            selectedContainerColor = MaterialTheme.colorScheme.primary,
                            selectedLabelColor = Color.White
                        )
                    )
                }
            }
        }
    }
}

@Composable
fun RecordingItemCard(
    recording: AudioRecording,
    isCurrentTrack: Boolean,
    isPlaying: Boolean,
    onPlayClick: () -> Unit,
    onRenameClick: () -> Unit,
    onShareClick: () -> Unit,
    onDeleteClick: () -> Unit
) {
    var menuExpanded by remember { mutableStateOf(false) }

    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(16.dp),
        colors = CardDefaults.cardColors(
            containerColor = if (isCurrentTrack) {
                MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.25f)
            } else {
                MaterialTheme.colorScheme.surface
            }
        ),
        elevation = CardDefaults.cardElevation(defaultElevation = 1.dp)
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(14.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            // Play Button
            Surface(
                shape = CircleShape,
                color = if (isPlaying) RecordingRed else MaterialTheme.colorScheme.primaryContainer,
                modifier = Modifier
                    .size(44.dp)
                    .clickable { onPlayClick() }
            ) {
                Box(contentAlignment = Alignment.Center) {
                    Icon(
                        imageVector = if (isPlaying) Icons.Default.Pause else Icons.Default.PlayArrow,
                        contentDescription = "Play Recording",
                        tint = if (isPlaying) Color.White else MaterialTheme.colorScheme.onPrimaryContainer,
                        modifier = Modifier.size(24.dp)
                    )
                }
            }

            Spacer(modifier = Modifier.width(12.dp))

            // Info
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = recording.title,
                    fontWeight = FontWeight.SemiBold,
                    fontSize = 15.sp,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
                Spacer(modifier = Modifier.height(2.dp))
                Row(
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = formatDuration(recording.durationMs),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.primary,
                        fontWeight = FontWeight.Bold
                    )
                    Text(
                        text = "•",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    Text(
                        text = formatFileSize(recording.fileSizeBytes),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    Text(
                        text = "•",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    Text(
                        text = formatDate(recording.timestamp),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }

            // Overflow Menu
            Box {
                IconButton(onClick = { menuExpanded = true }) {
                    Icon(
                        imageVector = Icons.Default.MoreVert,
                        contentDescription = "Options"
                    )
                }

                DropdownMenu(
                    expanded = menuExpanded,
                    onDismissRequest = { menuExpanded = false }
                ) {
                    DropdownMenuItem(
                        text = { Text("مشاركة") },
                        leadingIcon = { Icon(Icons.Default.Share, contentDescription = null) },
                        onClick = {
                            menuExpanded = false
                            onShareClick()
                        }
                    )
                    DropdownMenuItem(
                        text = { Text("إعادة تسمية") },
                        leadingIcon = { Icon(Icons.Default.Edit, contentDescription = null) },
                        onClick = {
                            menuExpanded = false
                            onRenameClick()
                        }
                    )
                    DropdownMenuItem(
                        text = { Text("حذف", color = MaterialTheme.colorScheme.error) },
                        leadingIcon = {
                            Icon(
                                Icons.Default.Delete,
                                contentDescription = null,
                                tint = MaterialTheme.colorScheme.error
                            )
                        },
                        onClick = {
                            menuExpanded = false
                            onDeleteClick()
                        }
                    )
                }
            }
        }
    }
}

fun formatDuration(ms: Long): String {
    val totalSec = ms / 1000
    val min = totalSec / 60
    val sec = totalSec % 60
    return String.format(Locale.getDefault(), "%02d:%02d", min, sec)
}

fun formatFileSize(bytes: Long): String {
    return when {
        bytes >= 1024 * 1024 -> String.format(Locale.getDefault(), "%.1f MB", bytes / (1024.0 * 1024.0))
        bytes >= 1024 -> String.format(Locale.getDefault(), "%.0f KB", bytes / 1024.0)
        else -> "$bytes B"
    }
}

fun formatDate(timestamp: Long): String {
    val sdf = SimpleDateFormat("dd MMM, HH:mm", Locale.getDefault())
    return sdf.format(Date(timestamp))
}
