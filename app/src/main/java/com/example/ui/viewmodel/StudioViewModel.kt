package com.example.ui.viewmodel

import android.content.Context
import android.graphics.Bitmap
import android.graphics.Color
import android.net.Uri
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.data.model.BulletinPreset
import com.example.data.model.CreditProject
import com.example.data.model.CreditSection
import com.example.data.model.ExportProgress
import com.example.data.model.LayerAsset
import com.example.data.model.LayerSlot
import com.example.data.model.NameplatePreset
import com.example.data.model.QuickLink
import com.example.data.model.UiState
import com.example.data.model.VideoResolution
import com.example.data.remote.RoverPublicSyncService
import com.example.data.repository.StudioRepository
import com.example.media.BulletinFrameRenderer
import com.example.media.BulletinOverlayBitmaps
import com.example.media.BulletinRenderConfig
import com.example.media.BulletinVideoEncoder
import com.example.media.CreditRenderer
import com.example.media.MediaExportHelper
import com.example.media.NameplateRenderer
import com.example.media.SavedMediaResult
import com.example.media.VideoLogoFrameExtractor
import com.google.firebase.firestore.FirebaseFirestoreException
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.time.LocalDate

data class BulletinEditorState(
    val title1: String = "বাংলাদেশ স্কাউটস রোভার অঞ্চলের নতুন আপডেট...",
    val title2: String = "বিস্তারিত জানতে চোখ রাখুন আমাদের পরবর্তী বুলেটিনে...",
    val startTimeText: String = "07:00:00",
    val ampm: String = "PM",
    val selectedDate: LocalDate = BulletinFrameRenderer.nextThursdayDate(),
    val durationMinutes: Int = 0,
    val durationSeconds: Int = 30,
    val logoWidth: Int = 200,
    val logoHeight: Int = 200,
    val scrollSpeed: Float = 2.5f,
    val resolution: VideoResolution = VideoResolution.QHD_2K,
    val targetFps: Int = 60,
    val chromaHex: String = "#0047BB",
    val activeLogoSourceLabel: String = "Default",
    val isVideoLogo: Boolean = false,
    val previewPlaying: Boolean = true,
    val previewRunId: Long = 0L
) {
    val totalDurationSeconds: Int
        get() = ((durationMinutes.coerceAtLeast(0) * 60) + durationSeconds.coerceIn(0, 59)).coerceAtLeast(3)

    fun toRenderConfig(): BulletinRenderConfig {
        val parts = startTimeText.split(":")
        val h = parts.getOrNull(0)?.toIntOrNull()?.coerceIn(1, 12) ?: 7
        val m = parts.getOrNull(1)?.toIntOrNull()?.coerceIn(0, 59) ?: 0
        val s = parts.getOrNull(2)?.toIntOrNull()?.coerceIn(0, 59) ?: 0
        val chromaInt = try {
            Color.parseColor(chromaHex)
        } catch (_: Exception) {
            Color.rgb(0, 71, 187)
        }
        return BulletinRenderConfig(
            title1 = title1,
            title2 = title2,
            startHours12 = h,
            startMinutes = m,
            startSeconds = s,
            isPm = ampm == "PM",
            dateString = BulletinFrameRenderer.formatDate(selectedDate),
            totalDurationSeconds = totalDurationSeconds.toDouble(),
            scrollSpeed = scrollSpeed.coerceIn(0.5f, 8.0f),
            logoWidth = logoWidth.toFloat().coerceIn(40f, 800f),
            logoHeight = logoHeight.toFloat().coerceIn(40f, 800f),
            chromaColorInt = chromaInt
        )
    }
}

data class NameplateEditorState(
    val nameText: String = "আশফাকুর রহমান তানিম",
    val unitText: String = "PR Marketing & ICT Taskforce",
    val accentColorHex: String = "#FF2B4A",
    val showLogo: Boolean = false,
    val exportScale: Int = 2 // 2x = 3840x2160 (4K), 3x = 5760x3240 (6K)
)

data class ExportedVideoDialogData(
    val saved: SavedMediaResult,
    val width: Int,
    val height: Int,
    val fps: Int,
    val durationSec: Double
)

data class ExportedImageDialogData(
    val saved: SavedMediaResult,
    val width: Int,
    val height: Int,
    val isTransparent: Boolean
)

class StudioViewModel(
    private val repository: StudioRepository,
    private val syncService: RoverPublicSyncService,
    private val currentUserId: String
) : ViewModel() {

    val layerAssetsState: StateFlow<UiState<List<LayerAsset>>> = repository.observeLayerAssets(currentUserId)
        .map<List<LayerAsset>, UiState<List<LayerAsset>>> { UiState.Success(it) }
        .catch { error ->
            emit(UiState.Error(unwrapFirestoreError(error, "Failed to observe layer assets")))
        }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000L), UiState.Loading)

    val bulletinPresetsState: StateFlow<UiState<List<BulletinPreset>>> = repository.observeBulletinPresets(currentUserId)
        .map<List<BulletinPreset>, UiState<List<BulletinPreset>>> { UiState.Success(it) }
        .catch { error ->
            emit(UiState.Error(unwrapFirestoreError(error, "Failed to observe bulletin presets")))
        }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000L), UiState.Loading)

    val nameplatePresetsState: StateFlow<UiState<List<NameplatePreset>>> = repository.observeNameplatePresets(currentUserId)
        .map<List<NameplatePreset>, UiState<List<NameplatePreset>>> { UiState.Success(it) }
        .catch { error ->
            emit(UiState.Error(unwrapFirestoreError(error, "Failed to observe nameplates")))
        }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000L), UiState.Loading)

    val creditProjectsState: StateFlow<UiState<List<CreditProject>>> = repository.observeCreditProjects(currentUserId)
        .map<List<CreditProject>, UiState<List<CreditProject>>> { UiState.Success(it) }
        .catch { error ->
            emit(UiState.Error(unwrapFirestoreError(error, "Failed to observe credit projects")))
        }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000L), UiState.Loading)

    val quickLinksState: StateFlow<UiState<List<QuickLink>>> = repository.observeQuickLinks(currentUserId)
        .map<List<QuickLink>, UiState<List<QuickLink>>> { UiState.Success(it) }
        .catch { error ->
            emit(UiState.Error(unwrapFirestoreError(error, "Failed to observe quick links")))
        }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000L), UiState.Loading)

    private val _bulletinEditor = MutableStateFlow(BulletinEditorState())
    val bulletinEditor: StateFlow<BulletinEditorState> = _bulletinEditor.asStateFlow()

    private val _overlays = MutableStateFlow(BulletinOverlayBitmaps())
    val overlays: StateFlow<BulletinOverlayBitmaps> = _overlays.asStateFlow()

    private val _videoLogoExtractor = MutableStateFlow<VideoLogoFrameExtractor?>(null)
    val videoLogoExtractor: StateFlow<VideoLogoFrameExtractor?> = _videoLogoExtractor.asStateFlow()

    private val _nameplateEditor = MutableStateFlow(NameplateEditorState())
    val nameplateEditor: StateFlow<NameplateEditorState> = _nameplateEditor.asStateFlow()

    private val _nameplateLogoBitmap = MutableStateFlow<Bitmap?>(null)
    val nameplateLogoBitmap: StateFlow<Bitmap?> = _nameplateLogoBitmap.asStateFlow()

    private val _creditSections = MutableStateFlow(defaultCreditSections())
    val creditSections: StateFlow<List<CreditSection>> = _creditSections.asStateFlow()

    private val _exportProgress = MutableStateFlow(ExportProgress())
    val exportProgress: StateFlow<ExportProgress> = _exportProgress.asStateFlow()

    private val _isSyncingOfficial = MutableStateFlow(false)
    val isSyncingOfficial: StateFlow<Boolean> = _isSyncingOfficial.asStateFlow()

    private val _exportedVideoDialog = MutableStateFlow<ExportedVideoDialogData?>(null)
    val exportedVideoDialog: StateFlow<ExportedVideoDialogData?> = _exportedVideoDialog.asStateFlow()

    private val _exportedImageDialog = MutableStateFlow<ExportedImageDialogData?>(null)
    val exportedImageDialog: StateFlow<ExportedImageDialogData?> = _exportedImageDialog.asStateFlow()

    private val _feedbackMessage = MutableStateFlow<String?>(null)
    val feedbackMessage: StateFlow<String?> = _feedbackMessage.asStateFlow()

    private var exportJob: Job? = null
    private var lastLoadedLayerSignatures = mapOf<String, String>()

    fun clearFeedbackMessage() {
        _feedbackMessage.value = null
    }

    fun dismissExportedVideoDialog() {
        _exportedVideoDialog.value = null
    }

    fun dismissExportedImageDialog() {
        _exportedImageDialog.value = null
    }

    // =========================================================================
    // BULLETIN EDITOR UPDATES & OVERLAY DECODING
    // =========================================================================

    fun updateBulletinEditor(transform: (BulletinEditorState) -> BulletinEditorState) {
        _bulletinEditor.update(transform)
    }

    fun restartBulletinPreview() {
        _bulletinEditor.update { it.copy(previewPlaying = true, previewRunId = it.previewRunId + 1L) }
    }

    fun toggleBulletinPreviewPlay() {
        _bulletinEditor.update { it.copy(previewPlaying = !it.previewPlaying) }
    }

    fun applyBulletinPreset(preset: BulletinPreset) {
        _bulletinEditor.update {
            it.copy(
                title1 = preset.title1,
                title2 = preset.title2,
                startTimeText = preset.startTime,
                ampm = preset.ampm,
                durationMinutes = (preset.durationSeconds / 60).toInt(),
                durationSeconds = (preset.durationSeconds % 60).toInt(),
                logoWidth = preset.logoWidth.toInt(),
                logoHeight = preset.logoHeight.toInt(),
                scrollSpeed = preset.scrollSpeed.toFloat(),
                resolution = VideoResolution.fromKey(preset.resolution),
                targetFps = preset.targetFps.toInt(),
                chromaHex = preset.chromaHex,
                previewRunId = it.previewRunId + 1L
            )
        }
        _feedbackMessage.value = "Loaded preset: ${preset.name}"
    }

    fun syncOverlayBitmapsFromAssets(context: Context, assets: List<LayerAsset>) {
        val currentMap = assets.associate { it.slot to it.content }
        if (currentMap == lastLoadedLayerSignatures) return
        lastLoadedLayerSignatures = currentMap

        viewModelScope.launch {
            val l1 = currentMap[LayerSlot.LAYER_1.slotId]?.let {
                MediaExportHelper.decodeBitmapFromSource(context, it, 2560)
            }
            val l2 = currentMap[LayerSlot.LAYER_2.slotId]?.let {
                MediaExportHelper.decodeBitmapFromSource(context, it, 2560)
            }
            val l3 = currentMap[LayerSlot.LAYER_3.slotId]?.let {
                MediaExportHelper.decodeBitmapFromSource(context, it, 2560)
            }
            val logoPng = currentMap[LayerSlot.LOGO_PNG.slotId]?.let {
                MediaExportHelper.decodeBitmapFromSource(context, it, 1024)
            }
            _overlays.update { prev ->
                prev.copy(
                    layer1 = l1 ?: prev.layer1,
                    layer2 = l2 ?: prev.layer2,
                    layer3 = l3 ?: prev.layer3,
                    staticLogo = if (_bulletinEditor.value.isVideoLogo) prev.staticLogo else (logoPng ?: prev.staticLogo)
                )
            }
            if (logoPng != null && !_bulletinEditor.value.isVideoLogo && _overlays.value.staticLogo == null) {
                _bulletinEditor.update { it.copy(activeLogoSourceLabel = "Firestore PNG Logo") }
            }
        }
    }

    fun selectFirestoreLogoAsset(context: Context, asset: LayerAsset) {
        viewModelScope.launch {
            if (asset.slot == LayerSlot.LOGO_VIDEO.slotId) {
                _feedbackMessage.value = "Loading video logo frames..."
                val st = _bulletinEditor.value
                val extractor = VideoLogoFrameExtractor.load(
                    context = context,
                    source = asset.content,
                    targetWidth = st.logoWidth,
                    targetHeight = st.logoHeight
                )
                if (extractor != null) {
                    _videoLogoExtractor.value?.release()
                    _videoLogoExtractor.value = extractor
                    _bulletinEditor.update {
                        it.copy(isVideoLogo = true, activeLogoSourceLabel = asset.label)
                    }
                    _feedbackMessage.value = "Video logo selected"
                } else {
                    _feedbackMessage.value = "Could not load video logo stream"
                }
            } else {
                val bmp = MediaExportHelper.decodeBitmapFromSource(context, asset.content, 1024)
                if (bmp != null) {
                    _videoLogoExtractor.value?.release()
                    _videoLogoExtractor.value = null
                    _overlays.update { it.copy(staticLogo = bmp) }
                    _bulletinEditor.update {
                        it.copy(isVideoLogo = false, activeLogoSourceLabel = asset.label)
                    }
                    _feedbackMessage.value = "PNG logo selected"
                } else {
                    _feedbackMessage.value = "Could not decode PNG logo"
                }
            }
        }
    }

    fun pickLocalBulletinLogo(context: Context, uri: Uri) {
        viewModelScope.launch {
            val mime = context.contentResolver.getType(uri).orEmpty()
            if (mime.startsWith("video/")) {
                _feedbackMessage.value = "Extracting video logo frames..."
                val st = _bulletinEditor.value
                val extractor = VideoLogoFrameExtractor.load(
                    context = context,
                    source = uri.toString(),
                    targetWidth = st.logoWidth,
                    targetHeight = st.logoHeight
                )
                if (extractor != null) {
                    _videoLogoExtractor.value?.release()
                    _videoLogoExtractor.value = extractor
                    _bulletinEditor.update {
                        it.copy(isVideoLogo = true, activeLogoSourceLabel = "Local Video Logo")
                    }
                    _feedbackMessage.value = "Video logo ready for 2K render"
                } else {
                    _feedbackMessage.value = "Could not decode selected video"
                }
            } else {
                val bmp = MediaExportHelper.decodeBitmapFromSource(context, uri.toString(), 1024)
                if (bmp != null) {
                    _videoLogoExtractor.value?.release()
                    _videoLogoExtractor.value = null
                    _overlays.update { it.copy(staticLogo = bmp) }
                    _bulletinEditor.update {
                        it.copy(isVideoLogo = false, activeLogoSourceLabel = "Local PNG Logo")
                    }
                    _feedbackMessage.value = "PNG logo applied"
                } else {
                    _feedbackMessage.value = "Could not load selected image"
                }
            }
        }
    }

    fun clearBulletinLogo() {
        _videoLogoExtractor.value?.release()
        _videoLogoExtractor.value = null
        _overlays.update { it.copy(staticLogo = null) }
        _bulletinEditor.update { it.copy(isVideoLogo = false, activeLogoSourceLabel = "None") }
    }

    fun uploadLayerImageToFirestore(context: Context, slot: LayerSlot, uri: Uri) {
        viewModelScope.launch {
            val dataUri = MediaExportHelper.compressImageUriToDataUri(context, uri, 1920)
            if (dataUri == null) {
                _feedbackMessage.value = "Image is too large even after compression"
                return@launch
            }
            val result = repository.saveLayerAsset(
                slot = slot.slotId,
                label = slot.displayLabel,
                sourceType = "data_uri",
                content = dataUri
            )
            _feedbackMessage.value = if (result.isSuccess) {
                "Saved ${slot.displayLabel} to Cloud Firestore"
            } else {
                result.exceptionOrNull()?.localizedMessage ?: "Failed saving layer"
            }
        }
    }

    fun saveLayerUrlToFirestore(slot: LayerSlot, url: String) {
        viewModelScope.launch {
            val result = repository.saveLayerAsset(
                slot = slot.slotId,
                label = slot.displayLabel,
                sourceType = "url",
                content = url.trim()
            )
            _feedbackMessage.value = if (result.isSuccess) {
                "Saved ${slot.displayLabel} URL to Firestore"
            } else {
                result.exceptionOrNull()?.localizedMessage ?: "Invalid URL or permission error"
            }
        }
    }

    fun deleteLayerFromFirestore(slot: LayerSlot) {
        viewModelScope.launch {
            val res = repository.deleteLayerAsset(slot.slotId)
            if (res.isSuccess) {
                _overlays.update {
                    when (slot) {
                        LayerSlot.LAYER_1 -> it.copy(layer1 = null)
                        LayerSlot.LAYER_2 -> it.copy(layer2 = null)
                        LayerSlot.LAYER_3 -> it.copy(layer3 = null)
                        LayerSlot.LOGO_PNG -> it.copy(staticLogo = null)
                        LayerSlot.LOGO_VIDEO -> it
                    }
                }
                _feedbackMessage.value = "Removed ${slot.displayLabel}"
            } else {
                _feedbackMessage.value = res.exceptionOrNull()?.localizedMessage ?: "Delete failed"
            }
        }
    }

    fun syncOfficialRoverAssets(context: Context) {
        if (_isSyncingOfficial.value) return
        viewModelScope.launch {
            _isSyncingOfficial.value = true
            val fetched = syncService.fetchOfficialRoverAssets()
            fetched.fold(
                onSuccess = { assets ->
                    var savedCount = 0
                    assets.layer1DataOrUrl?.let {
                        if (repository.saveLayerAsset(LayerSlot.LAYER_1.slotId, LayerSlot.LAYER_1.displayLabel, if (it.startsWith("http")) "url" else "data_uri", it).isSuccess) savedCount++
                    }
                    assets.layer2DataOrUrl?.let {
                        if (repository.saveLayerAsset(LayerSlot.LAYER_2.slotId, LayerSlot.LAYER_2.displayLabel, if (it.startsWith("http")) "url" else "data_uri", it).isSuccess) savedCount++
                    }
                    assets.layer3DataOrUrl?.let {
                        if (repository.saveLayerAsset(LayerSlot.LAYER_3.slotId, LayerSlot.LAYER_3.displayLabel, if (it.startsWith("http")) "url" else "data_uri", it).isSuccess) savedCount++
                    }
                    assets.logoPngDataOrUrl?.let {
                        if (repository.saveLayerAsset(LayerSlot.LOGO_PNG.slotId, LayerSlot.LOGO_PNG.displayLabel, if (it.startsWith("http")) "url" else "data_uri", it).isSuccess) savedCount++
                    }
                    assets.logoVideoUrl?.let {
                        if (repository.saveLayerAsset(LayerSlot.LOGO_VIDEO.slotId, LayerSlot.LOGO_VIDEO.displayLabel, "url", it).isSuccess) savedCount++
                    }
                    val linksToSave = assets.otherLinks.ifEmpty { RoverPublicSyncService.DEFAULT_LINKS }
                    linksToSave.forEachIndexed { idx, link ->
                        repository.saveQuickLink(
                            docId = "official_link_$idx",
                            title = link.title,
                            url = link.url,
                            thumbnailUrl = link.thumbnailUrl
                        )
                    }
                    _feedbackMessage.value = if (savedCount > 0) {
                        "Synced $savedCount official Rover layers & ${linksToSave.size} links to Firestore!"
                    } else {
                        "Synced ${linksToSave.size} links (no remote layer images found)"
                    }
                },
                onFailure = { err ->
                    // Still seed default Rover links cleanly
                    RoverPublicSyncService.DEFAULT_LINKS.forEachIndexed { idx, link ->
                        repository.saveQuickLink(
                            docId = "official_link_$idx",
                            title = link.title,
                            url = link.url,
                            thumbnailUrl = link.thumbnailUrl
                        )
                    }
                    _feedbackMessage.value = "Could not reach remote layer server (${err.localizedMessage}). Built-in broadcast graphics remain active."
                }
            )
            _isSyncingOfficial.value = false
        }
    }

    fun saveCurrentBulletinPreset(presetName: String) {
        viewModelScope.launch {
            val st = _bulletinEditor.value
            val preset = BulletinPreset(
                name = presetName.ifBlank { "Bulletin ${st.resolution.name}" },
                title1 = st.title1,
                title2 = st.title2,
                startTime = st.startTimeText,
                ampm = st.ampm,
                durationSeconds = st.totalDurationSeconds.toLong(),
                scrollSpeed = st.scrollSpeed.toDouble(),
                logoWidth = st.logoWidth.toLong(),
                logoHeight = st.logoHeight.toLong(),
                targetFps = st.targetFps.toLong(),
                resolution = st.resolution.firestoreKey,
                chromaHex = st.chromaHex
            )
            val res = repository.saveBulletinPreset(preset)
            _feedbackMessage.value = if (res.isSuccess) "Bulletin preset saved to Firestore"
            else res.exceptionOrNull()?.localizedMessage ?: "Failed saving preset"
        }
    }

    fun deleteBulletinPreset(presetId: String) {
        viewModelScope.launch {
            val res = repository.deleteBulletinPreset(presetId)
            _feedbackMessage.value = if (res.isSuccess) "Preset deleted"
            else res.exceptionOrNull()?.localizedMessage ?: "Delete failed"
        }
    }

    fun exportBulletin2kVideo(context: Context) {
        if (_exportProgress.value.running) return
        val state = _bulletinEditor.value
        val config = state.toRenderConfig()
        val res = state.resolution

        exportJob = viewModelScope.launch {
            _exportProgress.value = ExportProgress(
                running = true,
                progress = 0f,
                statusMessage = "Preparing ${res.label} @ ${state.targetFps}fps..."
            )
            val encoder = BulletinVideoEncoder(context)
            val result = encoder.encodeBulletinToMp4(
                requestedWidth = res.width,
                requestedHeight = res.height,
                requestedFps = state.targetFps,
                config = config,
                overlays = _overlays.value,
                videoLogoExtractor = if (state.isVideoLogo) _videoLogoExtractor.value else null,
                onProgress = { cur, total, msg ->
                    _exportProgress.value = ExportProgress(
                        running = true,
                        progress = if (total > 0) cur.toFloat() / total else 0f,
                        currentFrame = cur,
                        totalFrames = total,
                        statusMessage = msg
                    )
                }
            )

            result.fold(
                onSuccess = { report ->
                    _exportProgress.value = ExportProgress(
                        running = true,
                        progress = 1f,
                        currentFrame = report.totalFrames,
                        totalFrames = report.totalFrames,
                        statusMessage = "Saving MP4 to Movies/RoverStudio..."
                    )
                    val saveRes = MediaExportHelper.saveMp4ToGallery(
                        context = context,
                        sourceFile = report.file,
                        fileNamePrefix = "Rover_Bulletin_${report.actualWidth}x${report.actualHeight}_${report.actualFps}fps"
                    )
                    report.file.delete()
                    _exportProgress.value = ExportProgress(running = false, progress = 1f, statusMessage = "Export complete!")
                    saveRes.fold(
                        onSuccess = { saved ->
                            _exportedVideoDialog.value = ExportedVideoDialogData(
                                saved = saved,
                                width = report.actualWidth,
                                height = report.actualHeight,
                                fps = report.actualFps,
                                durationSec = report.durationSeconds
                            )
                        },
                        onFailure = { err ->
                            _feedbackMessage.value = "Save failed: ${err.localizedMessage}"
                        }
                    )
                },
                onFailure = { err ->
                    _exportProgress.value = ExportProgress(running = false, statusMessage = "Export failed")
                    _feedbackMessage.value = err.localizedMessage ?: "Video export failed"
                }
            )
        }
    }

    fun cancelBulletinExport() {
        exportJob?.cancel()
        exportJob = null
        _exportProgress.value = ExportProgress(running = false, statusMessage = "Cancelled")
    }

    // =========================================================================
    // NAMEPLATE STUDIO
    // =========================================================================

    fun loadDefaultNameplateLogo(context: Context) {
        if (_nameplateLogoBitmap.value != null) return
        viewModelScope.launch {
            val bmp = MediaExportHelper.decodeBitmapFromSource(
                context,
                RoverPublicSyncService.DEFAULT_ROVER_LOGO_URL,
                512
            )
            if (bmp != null) _nameplateLogoBitmap.value = bmp
        }
    }

    fun updateNameplateEditor(transform: (NameplateEditorState) -> NameplateEditorState) {
        _nameplateEditor.update(transform)
    }

    fun pickNameplateLogo(context: Context, uri: Uri) {
        viewModelScope.launch {
            val bmp = MediaExportHelper.decodeBitmapFromSource(context, uri.toString(), 512)
            if (bmp != null) {
                _nameplateLogoBitmap.value = bmp
                _nameplateEditor.update { it.copy(showLogo = true) }
                _feedbackMessage.value = "Updated nameplate badge logo"
            }
        }
    }

    fun saveNameplatePreset() {
        val st = _nameplateEditor.value
        if (st.nameText.isBlank() || st.unitText.isBlank()) {
            _feedbackMessage.value = "Please enter both Name and Unit before saving"
            return
        }
        viewModelScope.launch {
            val res = repository.saveNameplatePreset(st.nameText, st.unitText, st.accentColorHex)
            _feedbackMessage.value = if (res.isSuccess) "Nameplate saved to Firestore"
            else res.exceptionOrNull()?.localizedMessage ?: "Failed saving nameplate"
        }
    }

    fun applyNameplatePreset(preset: NameplatePreset) {
        _nameplateEditor.update {
            it.copy(
                nameText = preset.nameText,
                unitText = preset.unitText,
                accentColorHex = preset.accentColorHex
            )
        }
    }

    fun deleteNameplatePreset(presetId: String) {
        viewModelScope.launch {
            repository.deleteNameplatePreset(presetId)
        }
    }

    fun exportNameplatePng(context: Context) {
        val st = _nameplateEditor.value
        val w = 1920 * st.exportScale
        val h = 1080 * st.exportScale
        viewModelScope.launch {
            _feedbackMessage.value = "Rendering ${w}×${h} Transparent PNG..."
            val bmp = withContext(Dispatchers.Default) {
                val colorInt = CreditRenderer.parseColorSafe(st.accentColorHex, Color.rgb(255, 43, 74))
                NameplateRenderer(context).renderToBitmap(
                    width = w,
                    height = h,
                    nameText = st.nameText,
                    unitText = st.unitText,
                    topBarColorInt = colorInt,
                    logoBitmap = _nameplateLogoBitmap.value,
                    showLogo = st.showLogo
                )
            }
            val saved = MediaExportHelper.saveBitmapAsPng(
                context = context,
                bitmap = bmp,
                fileNamePrefix = st.nameText.ifBlank { "Rover_Nameplate" }
            )
            bmp.recycle()
            saved.fold(
                onSuccess = {
                    _exportedImageDialog.value = ExportedImageDialogData(it, w, h, isTransparent = true)
                },
                onFailure = { err ->
                    _feedbackMessage.value = "Export failed: ${err.localizedMessage}"
                }
            )
        }
    }

    // =========================================================================
    // CREDIT IMAGE STUDIO
    // =========================================================================

    fun addCreditSection() {
        _creditSections.update { list ->
            if (list.size >= 30) return@update list
            list + CreditSection(title = "নতুন টাইটেল", namesText = "নতুন নাম")
        }
    }

    fun removeCreditSection(index: Int) {
        _creditSections.update { list ->
            if (list.size <= 1 || index !in list.indices) return@update list
            list.toMutableList().apply { removeAt(index) }
        }
    }

    fun moveCreditSection(from: Int, to: Int) {
        _creditSections.update { list ->
            if (from !in list.indices || to !in list.indices) return@update list
            list.toMutableList().apply {
                val item = removeAt(from)
                add(to, item)
            }
        }
    }

    fun updateCreditSection(index: Int, updated: CreditSection) {
        _creditSections.update { list ->
            if (index !in list.indices) return@update list
            list.toMutableList().apply { set(index, updated) }
        }
    }

    fun applyBatchTitleStyle(colorHex: String, size: Long) {
        _creditSections.update { list ->
            list.map { it.copy(titleColorHex = colorHex, titleSize = size) }
        }
        _feedbackMessage.value = "Applied title style to all sections"
    }

    fun applyBatchNameStyle(colorHex: String, size: Long) {
        _creditSections.update { list ->
            list.map { it.copy(nameColorHex = colorHex, nameSize = size) }
        }
        _feedbackMessage.value = "Applied name style to all sections"
    }

    fun saveCreditProject(projectTitle: String) {
        val sections = _creditSections.value
        viewModelScope.launch {
            val res = repository.saveCreditProject(projectTitle.ifBlank { "Rover Credits" }, sections)
            _feedbackMessage.value = if (res.isSuccess) "Credit project saved to Firestore"
            else res.exceptionOrNull()?.localizedMessage ?: "Save failed"
        }
    }

    fun loadCreditProject(project: CreditProject) {
        val parsed = project.sections.map { CreditSection.fromMap(it) }
        if (parsed.isNotEmpty()) {
            _creditSections.value = parsed
            _feedbackMessage.value = "Loaded: ${project.projectTitle}"
        }
    }

    fun deleteCreditProject(projectId: String) {
        viewModelScope.launch {
            repository.deleteCreditProject(projectId)
        }
    }

    fun exportCreditPng(context: Context, transparent: Boolean, scale: Int = 2) {
        val sections = _creditSections.value
        viewModelScope.launch {
            _feedbackMessage.value = "Rendering ${1920 * scale}px Credit PNG..."
            val bmp = withContext(Dispatchers.Default) {
                CreditRenderer(context).renderToBitmap(
                    sections = sections,
                    baseWidth = 1920,
                    scale = scale,
                    transparentBackground = transparent
                )
            }
            val w = bmp.width
            val h = bmp.height
            val prefix = if (transparent) "Rover_Credits_Transparent" else "Rover_Credits_White"
            val saved = MediaExportHelper.saveBitmapAsPng(context, bmp, prefix)
            bmp.recycle()
            saved.fold(
                onSuccess = {
                    _exportedImageDialog.value = ExportedImageDialogData(it, w, h, isTransparent = transparent)
                },
                onFailure = { err ->
                    _feedbackMessage.value = "Export failed: ${err.localizedMessage}"
                }
            )
        }
    }

    // =========================================================================
    // QUICK LINKS
    // =========================================================================

    fun addQuickLink(title: String, url: String) {
        viewModelScope.launch {
            val res = repository.saveQuickLink(title = title, url = url)
            _feedbackMessage.value = if (res.isSuccess) "Link saved to Firestore"
            else res.exceptionOrNull()?.localizedMessage ?: "Failed saving link"
        }
    }

    fun deleteQuickLink(linkId: String) {
        viewModelScope.launch {
            repository.deleteQuickLink(linkId)
        }
    }

    override fun onCleared() {
        super.onCleared()
        exportJob?.cancel()
        _videoLogoExtractor.value?.release()
    }

    private fun unwrapFirestoreError(error: Throwable, fallback: String): String {
        val firestoreError = generateSequence(error) { it.cause }
            .filterIsInstance<FirebaseFirestoreException>()
            .firstOrNull()
        if (firestoreError == null && error is CancellationException) throw error
        return (firestoreError ?: error).message ?: fallback
    }

    companion object {
        fun defaultCreditSections(): List<CreditSection> = listOf(
            CreditSection("পরিকল্পনায়", "রাকিবুল হাসান রাফি, আশিকুর রহমান, মোঃ সাব্বির হোসেন"),
            CreditSection("নির্দেশনায়", "শুভ চন্দ্র দত্ত"),
            CreditSection("পরিচালনায়", ""),
            CreditSection("কনটেন্ট", ""),
            CreditSection("উপস্থাপনায়", ""),
            CreditSection("সম্পাদনায়", ""),
            CreditSection("ডিজাইনার", ""),
            CreditSection("সার্বিক সহযোগিতায়", "মোঃ ইকবাল হোসেন")
        )
    }
}
