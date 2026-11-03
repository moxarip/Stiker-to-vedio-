package com.example

import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.provider.DocumentsContract
import androidx.activity.ComponentActivity
import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.result.contract.ActivityResultContracts
import androidx.activity.viewModels
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Android
import androidx.compose.material.icons.filled.AutoAwesomeMotion
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.FolderSpecial
import androidx.compose.material.icons.filled.MovieCreation
import androidx.compose.material.icons.filled.VideoLibrary
import androidx.compose.material.icons.outlined.Android
import androidx.compose.material.icons.outlined.FolderSpecial
import androidx.compose.material.icons.outlined.MovieCreation
import androidx.compose.material.icons.outlined.VideoLibrary
import androidx.compose.material3.Badge
import androidx.compose.material3.BadgedBox
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.NavigationBarItemDefaults
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.example.ui.MainStudioTab
import com.example.ui.StickerStudioViewModel
import com.example.ui.screens.DebugApkScreen
import com.example.ui.screens.ExportedVideosScreen
import com.example.ui.screens.VideoStudioScreen
import com.example.ui.screens.WhatsAppStickersScreen
import com.example.ui.theme.CyberTeal
import com.example.ui.theme.MyApplicationTheme
import com.example.ui.theme.StudioCard
import com.example.ui.theme.StudioCardElevated
import com.example.ui.theme.StudioObsidian
import com.example.ui.theme.StudioSurface
import com.example.ui.theme.WhatsAppEmerald
import com.example.ui.theme.WhatsAppNeonGreen

class OpenWhatsAppFolderContract : ActivityResultContracts.OpenDocumentTree() {
    override fun createIntent(context: Context, input: Uri?): Intent {
        val intent = super.createIntent(context, input)
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O && input != null) {
            intent.putExtra(DocumentsContract.EXTRA_INITIAL_URI, input)
        }
        intent.addFlags(
            Intent.FLAG_GRANT_READ_URI_PERMISSION or
                Intent.FLAG_GRANT_PERSISTABLE_URI_PERMISSION
        )
        return intent
    }
}

class MainActivity : ComponentActivity() {

    private val viewModel: StickerStudioViewModel by viewModels()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        handleIncomingShareIntent(intent)

        setContent {
            MyApplicationTheme {
                StickerVideoStudioApp(viewModel = viewModel)
            }
        }
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        handleIncomingShareIntent(intent)
    }

    private fun handleIncomingShareIntent(intent: Intent?) {
        if (intent == null) return
        when (intent.action) {
            Intent.ACTION_SEND -> {
                @Suppress("DEPRECATION")
                val uri = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                    intent.getParcelableExtra(Intent.EXTRA_STREAM, Uri::class.java)
                } else {
                    intent.getParcelableExtra(Intent.EXTRA_STREAM) as? Uri
                }
                if (uri != null) {
                    viewModel.onStickersImported(listOf(uri))
                    viewModel.selectTab(MainStudioTab.VIDEO_STUDIO)
                }
            }
            Intent.ACTION_SEND_MULTIPLE -> {
                @Suppress("DEPRECATION")
                val uris = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                    intent.getParcelableArrayListExtra(Intent.EXTRA_STREAM, Uri::class.java)
                } else {
                    intent.getParcelableArrayListExtra(Intent.EXTRA_STREAM)
                }
                if (!uris.isNullOrEmpty()) {
                    viewModel.onStickersImported(uris)
                    viewModel.selectTab(MainStudioTab.VIDEO_STUDIO)
                }
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun StickerVideoStudioApp(viewModel: StickerStudioViewModel) {
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()

    // BackHandler for secondary tabs to return to WHATSAPP_STICKERS
    BackHandler(enabled = uiState.activeTab != MainStudioTab.WHATSAPP_STICKERS) {
        viewModel.selectTab(MainStudioTab.WHATSAPP_STICKERS)
    }

    // Launcher for selecting the WhatsApp Stickers folder via SAF
    val folderPickerLauncher = rememberLauncherForActivityResult(
        contract = OpenWhatsAppFolderContract()
    ) { treeUri ->
        viewModel.onWhatsAppFolderPicked(treeUri)
    }

    // Launcher for importing any sticker (.webp, .gif, .png, image/*)
    val stickerDocumentPickerLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.OpenMultipleDocuments()
    ) { uris ->
        if (uris.isNotEmpty()) {
            viewModel.onStickersImported(uris)
        }
    }

    Scaffold(
        modifier = Modifier.fillMaxSize(),
        containerColor = StudioObsidian,
        contentWindowInsets = WindowInsets.safeDrawing,
        topBar = {
            TopAppBar(
                title = {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Box(
                            modifier = Modifier
                                .size(36.dp)
                                .clip(RoundedCornerShape(10.dp))
                                .background(WhatsAppEmerald),
                            contentAlignment = Alignment.Center
                        ) {
                            Icon(
                                imageVector = Icons.Default.AutoAwesomeMotion,
                                contentDescription = null,
                                tint = Color.White,
                                modifier = Modifier.size(20.dp)
                            )
                        }
                        Spacer(modifier = Modifier.width(10.dp))
                        Column {
                            Text(
                                text = "StickerVideo Studio",
                                style = MaterialTheme.typography.titleMedium,
                                color = Color.White,
                                fontWeight = FontWeight.Bold
                            )
                            Text(
                                text = "محول ملصقات واتساب وأي ملصق إلى فيديو MP4",
                                style = MaterialTheme.typography.labelMedium,
                                color = CyberTeal
                            )
                        }
                    }
                },
                actions = {
                    Surface(
                        onClick = { viewModel.selectTab(MainStudioTab.DEBUG_APK) },
                        shape = RoundedCornerShape(12.dp),
                        color = StudioCardElevated,
                        modifier = Modifier
                            .padding(end = 12.dp)
                            .testTag("top_debug_apk_badge")
                    ) {
                        Row(
                            modifier = Modifier.padding(horizontal = 10.dp, vertical = 6.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Icon(
                                imageVector = Icons.Default.Android,
                                contentDescription = "نسخة Debug APK",
                                tint = WhatsAppNeonGreen,
                                modifier = Modifier.size(16.dp)
                            )
                            Spacer(modifier = Modifier.width(4.dp))
                            Text(
                                text = "debug.apk",
                                style = MaterialTheme.typography.labelMedium,
                                color = WhatsAppNeonGreen,
                                fontWeight = FontWeight.Bold
                            )
                        }
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = StudioSurface
                )
            )
        },
        bottomBar = {
            NavigationBar(
                containerColor = StudioSurface,
                tonalElevation = 8.dp
            ) {
                MainStudioTab.entries.forEach { tab ->
                    val selected = uiState.activeTab == tab
                    NavigationBarItem(
                        selected = selected,
                        onClick = { viewModel.selectTab(tab) },
                        modifier = Modifier.testTag("nav_tab_${tab.name.lowercase()}"),
                        icon = {
                            BadgedBox(
                                badge = {
                                    if (tab == MainStudioTab.EXPORTED_VIDEOS && uiState.exportedVideos.isNotEmpty()) {
                                        Badge(containerColor = WhatsAppNeonGreen, contentColor = Color(0xFF042112)) {
                                            Text(uiState.exportedVideos.size.toString())
                                        }
                                    } else if (tab == MainStudioTab.VIDEO_STUDIO && uiState.selectedStickers.size > 1) {
                                        Badge(containerColor = CyberTeal, contentColor = Color(0xFF00262B)) {
                                            Text(uiState.selectedStickers.size.toString())
                                        }
                                    }
                                }
                            ) {
                                Icon(
                                    imageVector = when (tab) {
                                        MainStudioTab.WHATSAPP_STICKERS ->
                                            if (selected) Icons.Filled.FolderSpecial else Icons.Outlined.FolderSpecial
                                        MainStudioTab.VIDEO_STUDIO ->
                                            if (selected) Icons.Filled.MovieCreation else Icons.Outlined.MovieCreation
                                        MainStudioTab.EXPORTED_VIDEOS ->
                                            if (selected) Icons.Filled.VideoLibrary else Icons.Outlined.VideoLibrary
                                        MainStudioTab.DEBUG_APK ->
                                            if (selected) Icons.Filled.Android else Icons.Outlined.Android
                                    },
                                    contentDescription = tab.titleAr
                                )
                            }
                        },
                        label = {
                            Text(
                                text = tab.titleAr,
                                style = MaterialTheme.typography.labelMedium,
                                maxLines = 1
                            )
                        },
                        colors = NavigationBarItemDefaults.colors(
                            selectedIconColor = Color(0xFF042112),
                            selectedTextColor = WhatsAppNeonGreen,
                            indicatorColor = WhatsAppNeonGreen,
                            unselectedIconColor = MaterialTheme.colorScheme.onSurfaceVariant,
                            unselectedTextColor = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    )
                }
            }
        }
    ) { innerPadding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding)
        ) {
            // Dismissible Status Toast Banner
            AnimatedVisibility(
                visible = uiState.statusMessage != null,
                enter = fadeIn(),
                exit = fadeOut()
            ) {
                uiState.statusMessage?.let { message ->
                    Surface(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 16.dp, vertical = 8.dp)
                            .testTag("status_banner"),
                        shape = RoundedCornerShape(14.dp),
                        color = StudioCardElevated
                    ) {
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(horizontal = 14.dp, vertical = 10.dp),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.SpaceBetween
                        ) {
                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                modifier = Modifier.weight(1f)
                            ) {
                                Icon(
                                    imageVector = Icons.Default.CheckCircle,
                                    contentDescription = null,
                                    tint = WhatsAppNeonGreen,
                                    modifier = Modifier.size(20.dp)
                                )
                                Spacer(modifier = Modifier.width(8.dp))
                                Text(
                                    text = message,
                                    style = MaterialTheme.typography.bodyMedium,
                                    color = Color.White
                                )
                            }
                            IconButton(
                                onClick = { viewModel.dismissStatusMessage() },
                                modifier = Modifier.size(28.dp)
                            ) {
                                Icon(
                                    imageVector = Icons.Default.Close,
                                    contentDescription = "إغلاق",
                                    tint = Color.White.copy(alpha = 0.7f),
                                    modifier = Modifier.size(16.dp)
                                )
                            }
                        }
                    }
                }
            }

            Box(modifier = Modifier.weight(1f)) {
                when (uiState.activeTab) {
                    MainStudioTab.WHATSAPP_STICKERS -> {
                        WhatsAppStickersScreen(
                            uiState = uiState,
                            onPickWhatsAppFolder = {
                                folderPickerLauncher.launch(viewModel.getInitialWhatsAppUri())
                            },
                            onImportStickerFiles = {
                                stickerDocumentPickerLauncher.launch(arrayOf("image/*"))
                            },
                            onPopulateDemoWhatsAppFolder = {
                                viewModel.populateSampleWhatsAppFolder()
                            },
                            onSearchQueryChange = viewModel::setSearchQuery,
                            onSelectSourceFilter = viewModel::setSourceFilter,
                            onToggleFavoritesFilter = viewModel::toggleFavoritesFilter,
                            onToggleAnimatedFilter = viewModel::toggleAnimatedFilter,
                            onToggleSelectSticker = viewModel::toggleStickerSelection,
                            onConvertSingleSticker = viewModel::openSingleStickerInStudio,
                            onOpenSelectedInStudio = viewModel::openSelectedStickersInStudio,
                            onToggleFavorite = viewModel::toggleFavorite
                        )
                    }
                    MainStudioTab.VIDEO_STUDIO -> {
                        VideoStudioScreen(
                            uiState = uiState,
                            onUpdateConfig = viewModel::updateStudioConfig,
                            onRemoveStickerFromSelection = viewModel::toggleStickerSelection,
                            onAddMoreStickersClick = {
                                viewModel.selectTab(MainStudioTab.WHATSAPP_STICKERS)
                            },
                            onStartEncodingMp4 = viewModel::convertToMp4Video
                        )
                    }
                    MainStudioTab.EXPORTED_VIDEOS -> {
                        ExportedVideosScreen(
                            uiState = uiState,
                            onSelectVideo = viewModel::setActivePreviewVideo,
                            onSaveToGallery = viewModel::saveVideoToGallery,
                            onShareVideo = viewModel::shareVideo,
                            onDeleteVideo = viewModel::deleteExportedVideo,
                            onGoToStudio = { viewModel.selectTab(MainStudioTab.VIDEO_STUDIO) }
                        )
                    }
                    MainStudioTab.DEBUG_APK -> {
                        DebugApkScreen(
                            debugApkInfo = uiState.debugApkInfo,
                            isSyncing = uiState.isSyncingDebugApk,
                            onSyncNow = viewModel::syncDebugApkNow,
                            onExportToDownloads = viewModel::exportDebugApkToDeviceDownloads,
                            onShareApk = viewModel::shareDebugApkFile
                        )
                    }
                }
            }
        }
    }
}
