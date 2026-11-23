package com.example.ui.screens

import android.graphics.Bitmap
import android.graphics.Color as AndroidColor
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.PickVisualMediaRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.BookmarkAdd
import androidx.compose.material.icons.rounded.CalendarMonth
import androidx.compose.material.icons.rounded.Close
import androidx.compose.material.icons.rounded.CloudDownload
import androidx.compose.material.icons.rounded.Delete
import androidx.compose.material.icons.rounded.HighQuality
import androidx.compose.material.icons.rounded.Image
import androidx.compose.material.icons.rounded.Movie
import androidx.compose.material.icons.rounded.Palette
import androidx.compose.material.icons.rounded.Pause
import androidx.compose.material.icons.rounded.PlayArrow
import androidx.compose.material.icons.rounded.Replay
import androidx.compose.material.icons.rounded.VideoFile
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.AssistChip
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.DatePicker
import androidx.compose.material3.DatePickerDialog
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Slider
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.rememberDatePickerState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableDoubleStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.runtime.withFrameNanos
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.drawscope.drawIntoCanvas
import androidx.compose.ui.graphics.nativeCanvas
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.example.data.model.BulletinPreset
import com.example.data.model.LayerAsset
import com.example.data.model.LayerSlot
import com.example.data.model.UiState
import com.example.data.model.VideoResolution
import com.example.media.BulletinFrameRenderer
import com.example.ui.components.ColorPickerDialog
import com.example.ui.viewmodel.StudioViewModel
import java.time.Instant
import java.time.ZoneId
import kotlin.math.roundToInt

@OptIn(ExperimentalMaterial3Api::class, ExperimentalLayoutApi::class)
@Composable
fun BulletinScreen(
    viewModel: StudioViewModel,
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current
    val editor by viewModel.bulletinEditor.collectAsStateWithLifecycle()
    val overlays by viewModel.overlays.collectAsStateWithLifecycle()
    val videoLogoExtractor by viewModel.videoLogoExtractor.collectAsStateWithLifecycle()
    val exportProgress by viewModel.exportProgress.collectAsStateWithLifecycle()
    val layerAssetsState by viewModel.layerAssetsState.collectAsStateWithLifecycle()
    val presetsState by viewModel.bulletinPresetsState.collectAsStateWithLifecycle()
    val isSyncing by viewModel.isSyncingOfficial.collectAsStateWithLifecycle()

    val renderer = remember(context) { BulletinFrameRenderer(context) }
    var elapsedSec by remember { mutableDoubleStateOf(1.2) }
    var showDatePicker by remember { mutableStateOf(false) }
    var showChromaPicker by remember { mutableStateOf(false) }
    var showSavePresetDialog by remember { mutableStateOf(false) }

    val logoPickerLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.PickVisualMedia()
    ) { uri ->
        if (uri != null) {
            viewModel.pickLocalBulletinLogo(context, uri)
        }
    }

    LaunchedEffect(layerAssetsState) {
        if (layerAssetsState is UiState.Success) {
            viewModel.syncOverlayBitmapsFromAssets(context, (layerAssetsState as UiState.Success<List<LayerAsset>>).data)
        }
    }

    // Live 60Hz animation ticker for preview
    LaunchedEffect(editor.previewPlaying, editor.previewRunId, editor.totalDurationSeconds) {
        if (!editor.previewPlaying) return@LaunchedEffect
        var lastNanos = 0L
        if (editor.previewRunId > 0L) {
            elapsedSec = 0.0
        }
        while (true) {
            withFrameNanos { frameNanos ->
                if (lastNanos != 0L) {
                    val dt = ((frameNanos - lastNanos) / 1_000_000_000.0).coerceIn(0.0, 0.05)
                    val next = elapsedSec + dt
                    elapsedSec = if (next >= editor.totalDurationSeconds.toDouble()) 0.0 else next
                }
                lastNanos = frameNanos
            }
        }
    }

    val renderConfig = remember(editor) { editor.toRenderConfig() }

    BoxWithConstraints(modifier = modifier.fillMaxSize()) {
        val isWide = maxWidth >= 880.dp

        if (isWide) {
            Row(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(16.dp),
                horizontalArrangement = Arrangement.spacedBy(16.dp)
            ) {
                Column(
                    modifier = Modifier
                        .weight(1.2f)
                        .verticalScroll(rememberScrollState()),
                    verticalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    BulletinPreviewCard(
                        renderer = renderer,
                        elapsedSec = elapsedSec,
                        renderConfig = renderConfig,
                        overlays = overlays,
                        videoLogoFrame = videoLogoExtractor?.frameAt(elapsedSec),
                        editor = editor,
                        exportProgress = exportProgress,
                        onTogglePlay = viewModel::toggleBulletinPreviewPlay,
                        onRestart = viewModel::restartBulletinPreview,
                        onExport = { viewModel.exportBulletin2kVideo(context) },
                        onCancelExport = viewModel::cancelBulletinExport
                    )
                }

                Column(
                    modifier = Modifier
                        .weight(1f)
                        .verticalScroll(rememberScrollState()),
                    verticalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    BulletinControlsColumn(
                        editor = editor,
                        layerAssetsState = layerAssetsState,
                        presetsState = presetsState,
                        isSyncing = isSyncing,
                        onUpdateEditor = viewModel::updateBulletinEditor,
                        onOpenDatePicker = { showDatePicker = true },
                        onOpenChromaPicker = { showChromaPicker = true },
                        onPickLogo = {
                            logoPickerLauncher.launch(
                                PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageAndVideo)
                            )
                        },
                        onSelectFirestoreLogo = { viewModel.selectFirestoreLogoAsset(context, it) },
                        onClearLogo = viewModel::clearBulletinLogo,
                        onSyncOfficial = { viewModel.syncOfficialRoverAssets(context) },
                        onSavePresetClick = { showSavePresetDialog = true },
                        onApplyPreset = viewModel::applyBulletinPreset,
                        onDeletePreset = viewModel::deleteBulletinPreset
                    )
                }
            }
        } else {
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .verticalScroll(rememberScrollState())
                    .padding(horizontal = 14.dp, vertical = 12.dp),
                verticalArrangement = Arrangement.spacedBy(14.dp)
            ) {
                BulletinPreviewCard(
                    renderer = renderer,
                    elapsedSec = elapsedSec,
                    renderConfig = renderConfig,
                    overlays = overlays,
                    videoLogoFrame = videoLogoExtractor?.frameAt(elapsedSec),
                    editor = editor,
                    exportProgress = exportProgress,
                    onTogglePlay = viewModel::toggleBulletinPreviewPlay,
                    onRestart = viewModel::restartBulletinPreview,
                    onExport = { viewModel.exportBulletin2kVideo(context) },
                    onCancelExport = viewModel::cancelBulletinExport
                )

                BulletinControlsColumn(
                    editor = editor,
                    layerAssetsState = layerAssetsState,
                    presetsState = presetsState,
                    isSyncing = isSyncing,
                    onUpdateEditor = viewModel::updateBulletinEditor,
                    onOpenDatePicker = { showDatePicker = true },
                    onOpenChromaPicker = { showChromaPicker = true },
                    onPickLogo = {
                        logoPickerLauncher.launch(
                            PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageAndVideo)
                        )
                    },
                    onSelectFirestoreLogo = { viewModel.selectFirestoreLogoAsset(context, it) },
                    onClearLogo = viewModel::clearBulletinLogo,
                    onSyncOfficial = { viewModel.syncOfficialRoverAssets(context) },
                    onSavePresetClick = { showSavePresetDialog = true },
                    onApplyPreset = viewModel::applyBulletinPreset,
                    onDeletePreset = viewModel::deleteBulletinPreset
                )
            }
        }
    }

    if (showDatePicker) {
        val initialMillis = remember(editor.selectedDate) {
            editor.selectedDate.atStartOfDay(ZoneId.of("UTC")).toInstant().toEpochMilli()
        }
        val datePickerState = rememberDatePickerState(initialSelectedDateMillis = initialMillis)
        DatePickerDialog(
            onDismissRequest = { showDatePicker = false },
            confirmButton = {
                TextButton(
                    onClick = {
                        datePickerState.selectedDateMillis?.let { millis ->
                            val picked = Instant.ofEpochMilli(millis).atZone(ZoneId.of("UTC")).toLocalDate()
                            viewModel.updateBulletinEditor { it.copy(selectedDate = picked) }
                        }
                        showDatePicker = false
                    }
                ) { Text("সেট করুন") }
            },
            dismissButton = {
                TextButton(onClick = { showDatePicker = false }) { Text("Cancel") }
            }
        ) {
            DatePicker(state = datePickerState)
        }
    }

    if (showChromaPicker) {
        ColorPickerDialog(
            initialHex = editor.chromaHex,
            title = "Chroma Screen Background",
            onDismiss = { showChromaPicker = false },
            onColorSelected = { hex ->
                viewModel.updateBulletinEditor { it.copy(chromaHex = hex) }
                showChromaPicker = false
            }
        )
    }

    if (showSavePresetDialog) {
        var presetName by remember { mutableStateOf("") }
        AlertDialog(
            onDismissRequest = { showSavePresetDialog = false },
            title = { Text("Save Bulletin Preset") },
            text = {
                OutlinedTextField(
                    value = presetName,
                    onValueChange = { presetName = it.take(80) },
                    label = { Text("Preset Name (e.g. Thursday 7PM)") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth()
                )
            },
            confirmButton = {
                Button(
                    onClick = {
                        viewModel.saveCurrentBulletinPreset(presetName)
                        showSavePresetDialog = false
                    }
                ) { Text("Save to Cloud") }
            },
            dismissButton = {
                TextButton(onClick = { showSavePresetDialog = false }) { Text("Cancel") }
            }
        )
    }
}

@Composable
private fun BulletinPreviewCard(
    renderer: BulletinFrameRenderer,
    elapsedSec: Double,
    renderConfig: com.example.media.BulletinRenderConfig,
    overlays: com.example.media.BulletinOverlayBitmaps,
    videoLogoFrame: Bitmap?,
    editor: com.example.ui.viewmodel.BulletinEditorState,
    exportProgress: com.example.data.model.ExportProgress,
    onTogglePlay: () -> Unit,
    onRestart: () -> Unit,
    onExport: () -> Unit,
    onCancelExport: () -> Unit
) {
    Card(
        shape = MaterialTheme.shapes.large,
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
        elevation = CardDefaults.cardElevation(defaultElevation = 3.dp)
    ) {
        Column(modifier = Modifier.padding(14.dp)) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    Surface(
                        color = MaterialTheme.colorScheme.primary,
                        shape = RoundedCornerShape(6.dp)
                    ) {
                        Text(
                            text = if (editor.resolution == VideoResolution.QHD_2K) "2K QHD" else "1080p",
                            style = MaterialTheme.typography.labelSmall,
                            color = Color.White,
                            modifier = Modifier.padding(horizontal = 8.dp, vertical = 3.dp)
                        )
                    }
                    Surface(
                        color = Color(0xFF10B981),
                        shape = RoundedCornerShape(6.dp)
                    ) {
                        Text(
                            text = "${editor.targetFps} FPS",
                            style = MaterialTheme.typography.labelSmall,
                            color = Color.White,
                            modifier = Modifier.padding(horizontal = 8.dp, vertical = 3.dp)
                        )
                    }
                    Text(
                        text = "${elapsedSec.toInt()}s / ${editor.totalDurationSeconds}s",
                        style = MaterialTheme.typography.labelMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }

                Row {
                    IconButton(onClick = onTogglePlay, modifier = Modifier.testTag("preview_play_pause_button")) {
                        Icon(
                            imageVector = if (editor.previewPlaying) Icons.Rounded.Pause else Icons.Rounded.PlayArrow,
                            contentDescription = if (editor.previewPlaying) "Pause preview" else "Play preview"
                        )
                    }
                    IconButton(onClick = onRestart, modifier = Modifier.testTag("preview_restart_button")) {
                        Icon(Icons.Rounded.Replay, contentDescription = "Restart preview")
                    }
                }
            }

            Spacer(modifier = Modifier.height(8.dp))

            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .aspectRatio(16f / 9f)
                    .clip(RoundedCornerShape(12.dp))
                    .border(1.dp, MaterialTheme.colorScheme.outline, RoundedCornerShape(12.dp))
            ) {
                Canvas(modifier = Modifier.fillMaxSize()) {
                    drawIntoCanvas { canvas ->
                        renderer.drawFrame(
                            canvas = canvas.nativeCanvas,
                            width = size.width.roundToInt().coerceAtLeast(16),
                            height = size.height.roundToInt().coerceAtLeast(9),
                            elapsedSeconds = elapsedSec,
                            config = renderConfig,
                            overlays = overlays,
                            videoLogoFrame = videoLogoFrame
                        )
                    }
                }
            }

            Spacer(modifier = Modifier.height(12.dp))

            AnimatedVisibility(visible = exportProgress.running) {
                Column(modifier = Modifier.padding(bottom = 12.dp)) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(
                            text = exportProgress.statusMessage,
                            style = MaterialTheme.typography.bodySmall,
                            fontWeight = FontWeight.SemiBold,
                            modifier = Modifier.weight(1f),
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                        Text(
                            text = "${(exportProgress.progress * 100).roundToInt()}%",
                            style = MaterialTheme.typography.labelMedium,
                            color = MaterialTheme.colorScheme.primary
                        )
                    }
                    Spacer(modifier = Modifier.height(6.dp))
                    LinearProgressIndicator(
                        progress = { exportProgress.progress },
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(8.dp)
                            .clip(CircleShape)
                    )
                    Spacer(modifier = Modifier.height(6.dp))
                    TextButton(
                        onClick = onCancelExport,
                        modifier = Modifier.align(Alignment.End)
                    ) {
                        Text("Cancel Export")
                    }
                }
            }

            Button(
                onClick = onExport,
                enabled = !exportProgress.running,
                modifier = Modifier
                    .fillMaxWidth()
                    .height(52.dp)
                    .testTag("export_2k_video_button"),
                shape = MaterialTheme.shapes.medium,
                colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.primary)
            ) {
                Icon(Icons.Rounded.Movie, contentDescription = null)
                Spacer(modifier = Modifier.width(8.dp))
                Text(
                    text = if (exportProgress.running) "রেন্ডারিং চলছে..." else "রেকর্ড ও ডাউনলোড (${editor.resolution.width}×${editor.resolution.height} • ${editor.targetFps}fps MP4)",
                    style = MaterialTheme.typography.titleSmall,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
            }
        }
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun BulletinControlsColumn(
    editor: com.example.ui.viewmodel.BulletinEditorState,
    layerAssetsState: UiState<List<LayerAsset>>,
    presetsState: UiState<List<BulletinPreset>>,
    isSyncing: Boolean,
    onUpdateEditor: ((com.example.ui.viewmodel.BulletinEditorState) -> com.example.ui.viewmodel.BulletinEditorState) -> Unit,
    onOpenDatePicker: () -> Unit,
    onOpenChromaPicker: () -> Unit,
    onPickLogo: () -> Unit,
    onSelectFirestoreLogo: (LayerAsset) -> Unit,
    onClearLogo: () -> Unit,
    onSyncOfficial: () -> Unit,
    onSavePresetClick: () -> Unit,
    onApplyPreset: (BulletinPreset) -> Unit,
    onDeletePreset: (String) -> Unit
) {
    // 1. Headlines Card
    Card(
        shape = MaterialTheme.shapes.large,
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)
    ) {
        Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text("বুলেটিন শিরোনাম", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
                OutlinedButton(
                    onClick = onSavePresetClick,
                    contentPadding = PaddingValues(horizontal = 12.dp, vertical = 6.dp),
                    modifier = Modifier.testTag("save_bulletin_preset_button")
                ) {
                    Icon(Icons.Rounded.BookmarkAdd, contentDescription = null, modifier = Modifier.size(18.dp))
                    Spacer(modifier = Modifier.width(6.dp))
                    Text("Save Preset", style = MaterialTheme.typography.labelLarge)
                }
            }

            OutlinedTextField(
                value = editor.title1,
                onValueChange = { v -> onUpdateEditor { it.copy(title1 = v) } },
                label = { Text("উপরের শিরোনাম (সাদা ব্যাকগ্রাউন্ড)") },
                modifier = Modifier
                    .fillMaxWidth()
                    .testTag("bulletin_title1_input"),
                maxLines = 3
            )

            OutlinedTextField(
                value = editor.title2,
                onValueChange = { v -> onUpdateEditor { it.copy(title2 = v) } },
                label = { Text("নিচের শিরোনাম (কালো ব্যাকগ্রাউন্ড)") },
                modifier = Modifier
                    .fillMaxWidth()
                    .testTag("bulletin_title2_input"),
                maxLines = 3
            )
        }
    }

    // 2. 2K HD & Super High FPS Quality Settings Card
    Card(
        shape = MaterialTheme.shapes.large,
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)
    ) {
        Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(Icons.Rounded.HighQuality, contentDescription = null, tint = MaterialTheme.colorScheme.primary)
                Spacer(modifier = Modifier.width(8.dp))
                Text("2K HD & High FPS Export", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
            }

            Text("Resolution", style = MaterialTheme.typography.labelLarge, color = MaterialTheme.colorScheme.onSurfaceVariant)
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                VideoResolution.entries.forEach { res ->
                    FilterChip(
                        selected = editor.resolution == res,
                        onClick = { onUpdateEditor { it.copy(resolution = res) } },
                        label = { Text(res.label) },
                        modifier = Modifier.testTag("res_chip_${res.firestoreKey}")
                    )
                }
            }

            Text("Frame Rate (Smooth Hardware MP4)", style = MaterialTheme.typography.labelLarge, color = MaterialTheme.colorScheme.onSurfaceVariant)
            FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                listOf(60, 90, 120).forEach { fps ->
                    FilterChip(
                        selected = editor.targetFps == fps,
                        onClick = { onUpdateEditor { it.copy(targetFps = fps) } },
                        label = { Text("$fps FPS") },
                        modifier = Modifier.testTag("fps_chip_$fps")
                    )
                }
            }

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text("Chroma Screen Color", style = MaterialTheme.typography.labelLarge)
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.CenterVertically) {
                    AssistChip(
                        onClick = { onUpdateEditor { it.copy(chromaHex = "#0047BB") } },
                        label = { Text("Blue") }
                    )
                    AssistChip(
                        onClick = { onUpdateEditor { it.copy(chromaHex = "#00A84F") } },
                        label = { Text("Green") }
                    )
                    Box(
                        modifier = Modifier
                            .size(34.dp)
                            .clip(CircleShape)
                            .background(Color(try { AndroidColor.parseColor(editor.chromaHex) } catch (_: Exception) { AndroidColor.BLUE }))
                            .border(1.dp, MaterialTheme.colorScheme.outline, CircleShape)
                            .clickable(onClick = onOpenChromaPicker)
                    )
                }
            }
        }
    }

    // 3. Time, Date, Duration & Scroll Speed Card
    Card(
        shape = MaterialTheme.shapes.large,
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)
    ) {
        Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
            Text("সময়, তারিখ ও গতি", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)

            Row(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.CenterVertically) {
                OutlinedTextField(
                    value = editor.startTimeText,
                    onValueChange = { v -> onUpdateEditor { it.copy(startTimeText = v.take(10)) } },
                    label = { Text("শুরুর সময় (HH:MM:SS)") },
                    modifier = Modifier.weight(1.6f),
                    singleLine = true
                )
                Row(modifier = Modifier.weight(1f), horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                    listOf("AM", "PM").forEach { period ->
                        FilterChip(
                            selected = editor.ampm == period,
                            onClick = { onUpdateEditor { it.copy(ampm = period) } },
                            label = { Text(period) }
                        )
                    }
                }
            }

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Column {
                    Text("তারিখ (১০ সেকেন্ড পরপর দেখাবে)", style = MaterialTheme.typography.labelLarge)
                    Text(
                        text = BulletinFrameRenderer.formatDate(editor.selectedDate),
                        style = MaterialTheme.typography.titleMedium,
                        color = MaterialTheme.colorScheme.primary
                    )
                }
                OutlinedButton(onClick = onOpenDatePicker) {
                    Icon(Icons.Rounded.CalendarMonth, contentDescription = null, modifier = Modifier.size(18.dp))
                    Spacer(modifier = Modifier.width(6.dp))
                    Text("তারিখ")
                }
            }

            Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                OutlinedTextField(
                    value = editor.durationMinutes.toString(),
                    onValueChange = { v ->
                        val min = v.toIntOrNull()?.coerceIn(0, 10) ?: 0
                        onUpdateEditor { it.copy(durationMinutes = min) }
                    },
                    label = { Text("দৈর্ঘ্য (মিনিট)") },
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                    singleLine = true,
                    modifier = Modifier.weight(1f)
                )
                OutlinedTextField(
                    value = editor.durationSeconds.toString(),
                    onValueChange = { v ->
                        val sec = v.toIntOrNull()?.coerceIn(0, 59) ?: 0
                        onUpdateEditor { it.copy(durationSeconds = sec) }
                    },
                    label = { Text("সেকেন্ড") },
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                    singleLine = true,
                    modifier = Modifier.weight(1f)
                )
            }

            Column {
                Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                    Text("শিরোনাম স্ক্রোল গতি", style = MaterialTheme.typography.labelLarge)
                    Text("${"%.1f".format(editor.scrollSpeed)}x", style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.primary)
                }
                Slider(
                    value = editor.scrollSpeed,
                    onValueChange = { s -> onUpdateEditor { it.copy(scrollSpeed = s) } },
                    valueRange = 0.5f..6.0f
                )
            }
        }
    }

    // 4. Logo (Video / PNG) & Sync Card
    Card(
        shape = MaterialTheme.shapes.large,
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)
    ) {
        Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text("লোগো (PNG অথবা ভিডিও)", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
                TextButton(onClick = onClearLogo) { Text("Clear") }
            }

            Text(
                text = "Active: ${editor.activeLogoSourceLabel}",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.primary
            )

            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                Button(
                    onClick = onPickLogo,
                    modifier = Modifier
                        .weight(1f)
                        .testTag("pick_logo_button")
                ) {
                    Icon(Icons.Rounded.Image, contentDescription = null, modifier = Modifier.size(18.dp))
                    Spacer(modifier = Modifier.width(6.dp))
                    Text("লোগো বাছুন")
                }
                OutlinedButton(
                    onClick = onSyncOfficial,
                    enabled = !isSyncing,
                    modifier = Modifier.weight(1f)
                ) {
                    Icon(Icons.Rounded.CloudDownload, contentDescription = null, modifier = Modifier.size(18.dp))
                    Spacer(modifier = Modifier.width(6.dp))
                    Text(if (isSyncing) "Syncing..." else "Sync Layers")
                }
            }

            if (layerAssetsState is UiState.Success) {
                val logoAssets = layerAssetsState.data.filter {
                    it.slot == LayerSlot.LOGO_PNG.slotId || it.slot == LayerSlot.LOGO_VIDEO.slotId
                }
                if (logoAssets.isNotEmpty()) {
                    Text("Firestore লোগো সাজেশন:", style = MaterialTheme.typography.labelLarge)
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        logoAssets.forEach { asset ->
                            AssistChip(
                                onClick = { onSelectFirestoreLogo(asset) },
                                leadingIcon = {
                                    Icon(
                                        imageVector = if (asset.slot == LayerSlot.LOGO_VIDEO.slotId) Icons.Rounded.VideoFile else Icons.Rounded.Image,
                                        contentDescription = null,
                                        modifier = Modifier.size(16.dp)
                                    )
                                },
                                label = {
                                    Text(if (asset.slot == LayerSlot.LOGO_VIDEO.slotId) "ভিডিও লোগো" else "PNG লোগো")
                                }
                            )
                        }
                    }
                }
            }

            Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                OutlinedTextField(
                    value = editor.logoWidth.toString(),
                    onValueChange = { v -> onUpdateEditor { it.copy(logoWidth = v.toIntOrNull()?.coerceIn(40, 800) ?: 200) } },
                    label = { Text("লোগো প্রস্থ (px)") },
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                    singleLine = true,
                    modifier = Modifier.weight(1f)
                )
                OutlinedTextField(
                    value = editor.logoHeight.toString(),
                    onValueChange = { v -> onUpdateEditor { it.copy(logoHeight = v.toIntOrNull()?.coerceIn(40, 800) ?: 200) } },
                    label = { Text("লোগো উচ্চতা (px)") },
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                    singleLine = true,
                    modifier = Modifier.weight(1f)
                )
            }
        }
    }

    // 5. Saved Presets in Firestore
    if (presetsState is UiState.Success && presetsState.data.isNotEmpty()) {
        Card(
            shape = MaterialTheme.shapes.large,
            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)
        ) {
            Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Text("Saved Cloud Presets", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
                LazyRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    items(presetsState.data, key = { it.id }) { preset ->
                        Card(
                            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant),
                            modifier = Modifier.clickable { onApplyPreset(preset) }
                        ) {
                            Row(
                                modifier = Modifier.padding(horizontal = 12.dp, vertical = 8.dp),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Column {
                                    Text(preset.name, style = MaterialTheme.typography.labelLarge)
                                    Text(
                                        text = "${preset.resolution} • ${preset.targetFps}fps",
                                        style = MaterialTheme.typography.labelSmall,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant
                                    )
                                }
                                Spacer(modifier = Modifier.width(6.dp))
                                IconButton(
                                    onClick = { onDeletePreset(preset.id) },
                                    modifier = Modifier.size(28.dp)
                                ) {
                                    Icon(Icons.Rounded.Delete, contentDescription = "Delete preset", modifier = Modifier.size(16.dp))
                                }
                            }
                        }
                    }
                }
            }
        }
    }
}
