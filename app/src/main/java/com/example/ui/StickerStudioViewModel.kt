package com.example.ui

import android.app.Application
import android.net.Uri
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.example.data.StickerRepository
import com.example.model.DebugApkInfo
import com.example.model.ExportedVideoItem
import com.example.model.StickerItem
import com.example.model.StickerSource
import com.example.model.VideoStudioConfig
import com.example.video.StickerVideoEncoder
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

enum class MainStudioTab(val titleAr: String) {
    WHATSAPP_STICKERS("ملصقات واتساب"),
    VIDEO_STUDIO("محول الصيغة"),
    EXPORTED_VIDEOS("الفيديوهات"),
    DEBUG_APK("نسخة Debug APK")
}

data class StickerStudioUiState(
    val activeTab: MainStudioTab = MainStudioTab.WHATSAPP_STICKERS,
    val allStickers: List<StickerItem> = emptyList(),
    val selectedFilterSource: StickerSource? = null,
    val filterFavoritesOnly: Boolean = false,
    val filterAnimatedOnly: Boolean = false,
    val searchQuery: String = "",
    val linkedWhatsAppUri: Uri? = null,
    val isLoadingStickers: Boolean = true,
    val selectedStickers: List<StickerItem> = emptyList(),
    val studioConfig: VideoStudioConfig = VideoStudioConfig(),
    val isEncodingVideo: Boolean = false,
    val encodingProgress: Float = 0f,
    val exportedVideos: List<ExportedVideoItem> = emptyList(),
    val activePreviewVideo: ExportedVideoItem? = null,
    val debugApkInfo: DebugApkInfo? = null,
    val isSyncingDebugApk: Boolean = false,
    val statusMessage: String? = null
) {
    val filteredStickers: List<StickerItem>
        get() = allStickers.filter { item ->
            val matchesSource = selectedFilterSource == null || item.source == selectedFilterSource
            val matchesFav = !filterFavoritesOnly || item.isFavorite
            val matchesAnim = !filterAnimatedOnly || item.isAnimated
            val matchesQuery = searchQuery.isBlank() ||
                item.name.contains(searchQuery, ignoreCase = true) ||
                item.source.labelAr.contains(searchQuery, ignoreCase = true)
            matchesSource && matchesFav && matchesAnim && matchesQuery
        }

    val whatsAppStickerCount: Int
        get() = allStickers.count { it.source == StickerSource.WHATSAPP_FOLDER }
}

class StickerStudioViewModel(application: Application) : AndroidViewModel(application) {

    private val repository = StickerRepository(application.applicationContext)

    private val _uiState = MutableStateFlow(StickerStudioUiState())
    val uiState: StateFlow<StickerStudioUiState> = _uiState.asStateFlow()

    init {
        refreshAllData()
    }

    fun refreshAllData() {
        viewModelScope.launch {
            _uiState.update {
                it.copy(
                    isLoadingStickers = true,
                    linkedWhatsAppUri = repository.getSavedWhatsAppFolderUri()
                )
            }
            val stickers = repository.loadAllStickers()
            val videos = repository.loadExportedVideos()
            val debugInfo = repository.syncAndGetDebugApkInfo()

            _uiState.update { current ->
                val defaultSelected = if (current.selectedStickers.isEmpty() && stickers.isNotEmpty()) {
                    listOf(stickers.first())
                } else {
                    current.selectedStickers
                }
                current.copy(
                    allStickers = stickers,
                    selectedStickers = defaultSelected,
                    exportedVideos = videos,
                    debugApkInfo = debugInfo,
                    isLoadingStickers = false
                )
            }
        }
    }

    fun selectTab(tab: MainStudioTab) {
        _uiState.update { it.copy(activeTab = tab) }
    }

    fun setSourceFilter(source: StickerSource?) {
        _uiState.update { it.copy(selectedFilterSource = source) }
    }

    fun toggleFavoritesFilter() {
        _uiState.update { it.copy(filterFavoritesOnly = !it.filterFavoritesOnly) }
    }

    fun toggleAnimatedFilter() {
        _uiState.update { it.copy(filterAnimatedOnly = !it.filterAnimatedOnly) }
    }

    fun setSearchQuery(query: String) {
        _uiState.update { it.copy(searchQuery = query) }
    }

    fun dismissStatusMessage() {
        _uiState.update { it.copy(statusMessage = null) }
    }

    fun getInitialWhatsAppUri(): Uri = repository.getInitialWhatsAppStickersUri()

    fun onWhatsAppFolderPicked(treeUri: Uri?) {
        if (treeUri == null) return
        viewModelScope.launch {
            _uiState.update { it.copy(isLoadingStickers = true) }
            repository.saveWhatsAppFolderUri(treeUri)
            val all = repository.loadAllStickers()
            val waCount = all.count { it.source == StickerSource.WHATSAPP_FOLDER }
            _uiState.update { current ->
                val updatedSelected = if (waCount > 0) {
                    listOf(all.first { it.source == StickerSource.WHATSAPP_FOLDER })
                } else {
                    current.selectedStickers
                }
                current.copy(
                    linkedWhatsAppUri = treeUri,
                    allStickers = all,
                    selectedStickers = updatedSelected,
                    selectedFilterSource = if (waCount > 0) StickerSource.WHATSAPP_FOLDER else null,
                    isLoadingStickers = false,
                    statusMessage = if (waCount > 0) {
                        "تم استيراد $waCount ملصق من مجلد واتساب بنجاح!"
                    } else {
                        "تم ربط المجلد، لم يتم العثور على ملفات .webp بداخله بعد."
                    }
                )
            }
        }
    }

    fun populateSampleWhatsAppFolder() {
        viewModelScope.launch {
            _uiState.update { it.copy(isLoadingStickers = true) }
            val count = repository.populateDemoWhatsAppStickersFolder()
            val all = repository.loadAllStickers()
            _uiState.update { current ->
                current.copy(
                    allStickers = all,
                    selectedFilterSource = StickerSource.WHATSAPP_FOLDER,
                    selectedStickers = all.filter { it.source == StickerSource.WHATSAPP_FOLDER }.take(2)
                        .ifEmpty { current.selectedStickers },
                    isLoadingStickers = false,
                    statusMessage = "تم تجهيز وعرض $count ملصقات واتساب(.webp) داخل مجلد واتساب بنجاح!"
                )
            }
        }
    }

    fun onStickersImported(uris: List<Uri>) {
        if (uris.isEmpty()) return
        viewModelScope.launch {
            _uiState.update { it.copy(isLoadingStickers = true) }
            val imported = repository.importStickersFromUris(uris)
            val all = repository.loadAllStickers()
            _uiState.update { current ->
                current.copy(
                    allStickers = all,
                    selectedStickers = imported.ifEmpty { current.selectedStickers },
                    isLoadingStickers = false,
                    statusMessage = if (imported.isNotEmpty()) {
                        "تمت إضافة ${imported.size} ملصق جاهز للتحويل إلى فيديو!"
                    } else {
                        "تعذر قراءة الملف المحدد"
                    }
                )
            }
        }
    }

    fun toggleStickerSelection(item: StickerItem) {
        _uiState.update { current ->
            val existing = current.selectedStickers.toMutableList()
            val index = existing.indexOfFirst { it.id == item.id }
            if (index >= 0) {
                if (existing.size > 1) {
                    existing.removeAt(index)
                }
            } else {
                if (existing.size < 8) {
                    existing.add(item)
                }
            }
            current.copy(selectedStickers = existing)
        }
    }

    fun openSingleStickerInStudio(item: StickerItem) {
        _uiState.update { current ->
            current.copy(selectedStickers = listOf(item))
        }
        convertToMp4Video()
    }

    fun openSelectedStickersInStudio() {
        val selected = _uiState.value.selectedStickers.ifEmpty {
            _uiState.value.allStickers.take(1)
        }
        _uiState.update { current ->
            current.copy(selectedStickers = selected)
        }
        convertToMp4Video()
    }

    fun toggleFavorite(item: StickerItem) {
        val updatedFavs = repository.toggleFavorite(item.id)
        _uiState.update { current ->
            val updatedList = current.allStickers.map { stk ->
                if (stk.id == item.id) stk.copy(isFavorite = updatedFavs.contains(stk.id)) else stk
            }
            current.copy(allStickers = updatedList)
        }
    }

    fun updateStudioConfig(updater: (VideoStudioConfig) -> VideoStudioConfig) {
        _uiState.update { current ->
            current.copy(studioConfig = updater(current.studioConfig))
        }
    }

    fun convertToMp4Video() {
        val state = _uiState.value
        if (state.isEncodingVideo) return
        val stickersToEncode = state.selectedStickers.ifEmpty { state.allStickers.take(1) }
        if (stickersToEncode.isEmpty()) return

        viewModelScope.launch {
            _uiState.update {
                it.copy(
                    isEncodingVideo = true,
                    encodingProgress = 0.02f,
                    statusMessage = null
                )
            }

            val result = StickerVideoEncoder.encodeStickersToMp4(
                context = getApplication(),
                stickers = stickersToEncode,
                config = state.studioConfig,
                outputDir = repository.getExportedVideosDir(),
                onProgress = { progress ->
                    _uiState.update { it.copy(encodingProgress = progress) }
                }
            )

            val allVideos = repository.loadExportedVideos()
            result.fold(
                onSuccess = { exportedItem ->
                    _uiState.update {
                        it.copy(
                            isEncodingVideo = false,
                            encodingProgress = 1f,
                            exportedVideos = allVideos,
                            activePreviewVideo = exportedItem,
                            activeTab = MainStudioTab.EXPORTED_VIDEOS,
                            statusMessage = "تم تحويل الملصق إلى فيديو MP4 بنجاح (${exportedItem.resolution})!"
                        )
                    }
                },
                onFailure = { error ->
                    _uiState.update {
                        it.copy(
                            isEncodingVideo = false,
                            encodingProgress = 0f,
                            statusMessage = "حدث خطأ أثناء تصدير الفيديو: ${error.localizedMessage ?: "حاول مجدداً"}"
                        )
                    }
                }
            )
        }
    }

    fun setActivePreviewVideo(item: ExportedVideoItem?) {
        _uiState.update { it.copy(activePreviewVideo = item) }
    }

    fun saveVideoToGallery(item: ExportedVideoItem) {
        viewModelScope.launch {
            val uri = StickerVideoEncoder.saveVideoToPublicGallery(getApplication(), item.file)
            _uiState.update {
                it.copy(
                    statusMessage = if (uri != null) {
                        "تم حفظ الفيديو في معرض الهاتف (Movies/StickerVideoStudio) بنجاح!"
                    } else {
                        "تعذر حفظ الفيديو في المعرض"
                    }
                )
            }
        }
    }

    fun shareVideo(item: ExportedVideoItem, toWhatsApp: Boolean = false) {
        StickerVideoEncoder.shareFileViaProvider(
            context = getApplication(),
            file = item.file,
            mimeType = "video/mp4",
            chooserTitle = "مشاركة فيديو الملصق",
            targetPackage = if (toWhatsApp) "com.whatsapp" else null
        )
    }

    fun deleteExportedVideo(item: ExportedVideoItem) {
        viewModelScope.launch {
            repository.deleteExportedVideo(item)
            val updated = repository.loadExportedVideos()
            _uiState.update { current ->
                current.copy(
                    exportedVideos = updated,
                    activePreviewVideo = if (current.activePreviewVideo?.id == item.id) {
                        updated.firstOrNull()
                    } else {
                        current.activePreviewVideo
                    },
                    statusMessage = "تم حذف الفيديو"
                )
            }
        }
    }

    fun syncDebugApkNow() {
        viewModelScope.launch {
            _uiState.update { it.copy(isSyncingDebugApk = true) }
            val info = repository.syncAndGetDebugApkInfo()
            _uiState.update {
                it.copy(
                    debugApkInfo = info,
                    isSyncingDebugApk = false,
                    statusMessage = "تم تحديث نسخة debug/debug.apk بأحدث التغييرات بنجاح!"
                )
            }
        }
    }

    fun exportDebugApkToDeviceDownloads() {
        viewModelScope.launch {
            _uiState.update { it.copy(isSyncingDebugApk = true) }
            val path = repository.exportDebugApkToDownloads()
            val info = repository.syncAndGetDebugApkInfo()
            _uiState.update {
                it.copy(
                    debugApkInfo = info,
                    isSyncingDebugApk = false,
                    statusMessage = if (path != null) {
                        "تم نسخ وحفظ ملف debug.apk في مجلد التنزيلات ($path) بنجاح!"
                    } else {
                        "تعذر تصدير ملف debug.apk"
                    }
                )
            }
        }
    }

    fun shareDebugApkFile() {
        val info = _uiState.value.debugApkInfo ?: return
        if (info.localDebugCopyFile.exists()) {
            StickerVideoEncoder.shareFileViaProvider(
                context = getApplication(),
                file = info.localDebugCopyFile,
                mimeType = "application/vnd.android.package-archive",
                chooserTitle = "مشاركة نسخة التطبيق (debug.apk)"
            )
        }
    }
}
