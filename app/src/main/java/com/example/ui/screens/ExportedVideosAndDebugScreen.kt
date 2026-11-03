package com.example.ui.screens

import android.widget.VideoView
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Android
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.DeleteOutline
import androidx.compose.material.icons.filled.Download
import androidx.compose.material.icons.filled.MovieCreation
import androidx.compose.material.icons.filled.PlayCircleFilled
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Share
import androidx.compose.material.icons.filled.Verified
import androidx.compose.material.icons.filled.VideoLibrary
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import com.example.model.DebugApkInfo
import com.example.model.ExportedVideoItem
import com.example.ui.StickerStudioUiState
import com.example.ui.theme.AmberGold
import com.example.ui.theme.CoralPink
import com.example.ui.theme.CyberTeal
import com.example.ui.theme.StudioCard
import com.example.ui.theme.StudioCardElevated
import com.example.ui.theme.WhatsAppEmerald
import com.example.ui.theme.WhatsAppNeonGreen
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

@Composable
fun ExportedVideosScreen(
    uiState: StickerStudioUiState,
    onSelectVideo: (ExportedVideoItem) -> Unit,
    onSaveToGallery: (ExportedVideoItem) -> Unit,
    onShareVideo: (ExportedVideoItem, Boolean) -> Unit,
    onDeleteVideo: (ExportedVideoItem) -> Unit,
    onGoToStudio: () -> Unit
) {
    val activeVideo = uiState.activePreviewVideo ?: uiState.exportedVideos.firstOrNull()

    if (uiState.exportedVideos.isEmpty()) {
        Box(
            modifier = Modifier
                .fillMaxSize()
                .padding(24.dp)
                .testTag("empty_exported_videos"),
            contentAlignment = Alignment.Center
        ) {
            Card(
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(24.dp),
                colors = CardDefaults.cardColors(containerColor = StudioCard)
            ) {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(28.dp),
                    horizontalAlignment = Alignment.CenterHorizontally
                ) {
                    Icon(
                        imageVector = Icons.Default.VideoLibrary,
                        contentDescription = null,
                        tint = WhatsAppNeonGreen,
                        modifier = Modifier.size(56.dp)
                    )
                    Spacer(modifier = Modifier.height(14.dp))
                    Text(
                        text = "لا توجد فيديوهات مصدرة بعد",
                        style = MaterialTheme.typography.titleLarge,
                        color = Color.White
                    )
                    Spacer(modifier = Modifier.height(6.dp))
                    Text(
                        text = "اختر أي ملصق واتساب واضغط «تحويل إلى فيديو MP4» ليظهر هنا مع إمكانية تشغيله ومشاركته فوراً.",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    Spacer(modifier = Modifier.height(18.dp))
                    Button(
                        onClick = onGoToStudio,
                        shape = RoundedCornerShape(14.dp),
                        colors = ButtonDefaults.buttonColors(
                            containerColor = WhatsAppNeonGreen,
                            contentColor = Color(0xFF042112)
                        )
                    ) {
                        Icon(imageVector = Icons.Default.MovieCreation, contentDescription = null)
                        Spacer(modifier = Modifier.width(8.dp))
                        Text("الانتقال إلى استوديو التحويل")
                    }
                }
            }
        }
        return
    }

    LazyColumn(
        modifier = Modifier
            .fillMaxSize()
            .testTag("exported_videos_list"),
        contentPadding = PaddingValues(16.dp),
        verticalArrangement = Arrangement.spacedBy(14.dp)
    ) {
        // Active MP4 Video Player Card
        if (activeVideo != null && activeVideo.file.exists()) {
            item {
                ActiveVideoPlayerCard(
                    video = activeVideo,
                    onSaveToGallery = { onSaveToGallery(activeVideo) },
                    onShareWhatsApp = { onShareVideo(activeVideo, true) },
                    onShareGeneral = { onShareVideo(activeVideo, false) }
                )
            }
        }

        item {
            Text(
                text = "سجل الفيديوهات المحولة (${uiState.exportedVideos.size})",
                style = MaterialTheme.typography.titleMedium,
                color = Color.White,
                fontWeight = FontWeight.Bold
            )
        }

        items(uiState.exportedVideos, key = { it.id }) { item ->
            val isSelected = activeVideo?.id == item.id
            ExportedVideoRowCard(
                item = item,
                isSelected = isSelected,
                onClick = { onSelectVideo(item) },
                onSaveToGallery = { onSaveToGallery(item) },
                onShare = { onShareVideo(item, false) },
                onDelete = { onDeleteVideo(item) }
            )
        }
    }
}

@Composable
private fun ActiveVideoPlayerCard(
    video: ExportedVideoItem,
    onSaveToGallery: () -> Unit,
    onShareWhatsApp: () -> Unit,
    onShareGeneral: () -> Unit
) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(24.dp),
        colors = CardDefaults.cardColors(containerColor = StudioCardElevated)
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Column {
                    Text(
                        text = video.title,
                        style = MaterialTheme.typography.titleMedium,
                        color = Color.White,
                        fontWeight = FontWeight.Bold
                    )
                    Text(
                        text = "المدة: ${video.durationSec} ثوانٍ • الحجم: ${formatFileSize(video.sizeBytes)} • MP4 H.264",
                        style = MaterialTheme.typography.bodyMedium,
                        color = CyberTeal
                    )
                }
                Surface(
                    color = WhatsAppEmerald,
                    shape = RoundedCornerShape(8.dp)
                ) {
                    Text(
                        text = "جاهز للمشاركة",
                        style = MaterialTheme.typography.labelMedium,
                        color = Color.White,
                        modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp)
                    )
                }
            }

            Spacer(modifier = Modifier.height(12.dp))

            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(240.dp)
                    .clip(RoundedCornerShape(18.dp))
                    .background(Color.Black)
                    .border(1.5.dp, WhatsAppNeonGreen.copy(alpha = 0.5f), RoundedCornerShape(18.dp)),
                contentAlignment = Alignment.Center
            ) {
                AndroidView(
                    factory = { ctx ->
                        VideoView(ctx).apply {
                            setOnPreparedListener { mp ->
                                mp.isLooping = true
                                start()
                            }
                            setOnErrorListener { _, _, _ ->
                                // Prevent default framework AlertDialog crash on playback error
                                true
                            }
                        }
                    },
                    update = { videoView ->
                        try {
                            if (videoView.tag != video.file.absolutePath) {
                                videoView.tag = video.file.absolutePath
                                videoView.setVideoPath(video.file.absolutePath)
                                videoView.start()
                            }
                        } catch (_: Throwable) {}
                    },
                    modifier = Modifier.fillMaxSize()
                )
            }

            Spacer(modifier = Modifier.height(14.dp))

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                Button(
                    onClick = onShareWhatsApp,
                    modifier = Modifier
                        .weight(1.1f)
                        .height(46.dp)
                        .testTag("share_whatsapp_video_button"),
                    shape = RoundedCornerShape(12.dp),
                    colors = ButtonDefaults.buttonColors(
                        containerColor = WhatsAppNeonGreen,
                        contentColor = Color(0xFF042112)
                    )
                ) {
                    Icon(
                        imageVector = Icons.Default.Share,
                        contentDescription = null,
                        modifier = Modifier.size(18.dp)
                    )
                    Spacer(modifier = Modifier.width(6.dp))
                    Text("إرسال لواتساب", style = MaterialTheme.typography.labelLarge)
                }

                OutlinedButton(
                    onClick = onSaveToGallery,
                    modifier = Modifier
                        .weight(1f)
                        .height(46.dp)
                        .testTag("save_gallery_video_button"),
                    shape = RoundedCornerShape(12.dp)
                ) {
                    Icon(
                        imageVector = Icons.Default.Download,
                        contentDescription = null,
                        tint = CyberTeal,
                        modifier = Modifier.size(18.dp)
                    )
                    Spacer(modifier = Modifier.width(6.dp))
                    Text("حفظ بالمعرض", color = Color.White, style = MaterialTheme.typography.labelLarge)
                }

                IconButton(
                    onClick = onShareGeneral,
                    modifier = Modifier
                        .size(46.dp)
                        .clip(RoundedCornerShape(12.dp))
                        .background(StudioCard)
                ) {
                    Icon(
                        imageVector = Icons.Default.Share,
                        contentDescription = "مشاركة عامة",
                        tint = Color.White
                    )
                }
            }
        }
    }
}

@Composable
private fun ExportedVideoRowCard(
    item: ExportedVideoItem,
    isSelected: Boolean,
    onClick: () -> Unit,
    onSaveToGallery: () -> Unit,
    onShare: () -> Unit,
    onDelete: () -> Unit
) {
    val dateFormat = remember { SimpleDateFormat("yyyy/MM/dd HH:mm", Locale.getDefault()) }

    Card(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(18.dp))
            .clickable { onClick() }
            .border(
                width = if (isSelected) 2.dp else 1.dp,
                color = if (isSelected) WhatsAppNeonGreen else Color.Transparent,
                shape = RoundedCornerShape(18.dp)
            ),
        shape = RoundedCornerShape(18.dp),
        colors = CardDefaults.cardColors(containerColor = StudioCard)
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(14.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                modifier = Modifier.weight(1f)
            ) {
                Box(
                    modifier = Modifier
                        .size(48.dp)
                        .clip(RoundedCornerShape(14.dp))
                        .background(WhatsAppEmerald.copy(alpha = 0.22f)),
                    contentAlignment = Alignment.Center
                ) {
                    Icon(
                        imageVector = Icons.Default.PlayCircleFilled,
                        contentDescription = null,
                        tint = WhatsAppNeonGreen,
                        modifier = Modifier.size(28.dp)
                    )
                }
                Spacer(modifier = Modifier.width(12.dp))
                Column {
                    Text(
                        text = "${item.title} • ${item.durationSec}s",
                        style = MaterialTheme.typography.titleSmall,
                        color = Color.White,
                        fontWeight = FontWeight.Bold,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                    Text(
                        text = "${formatFileSize(item.sizeBytes)} • ${item.stickerCount} ملصق • ${dateFormat.format(Date(item.createdAt))}",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }

            Row(verticalAlignment = Alignment.CenterVertically) {
                IconButton(onClick = onSaveToGallery) {
                    Icon(
                        imageVector = Icons.Default.Download,
                        contentDescription = "حفظ في المعرض",
                        tint = CyberTeal
                    )
                }
                IconButton(onClick = onShare) {
                    Icon(
                        imageVector = Icons.Default.Share,
                        contentDescription = "مشاركة",
                        tint = WhatsAppNeonGreen
                    )
                }
                IconButton(onClick = onDelete) {
                    Icon(
                        imageVector = Icons.Default.DeleteOutline,
                        contentDescription = "حذف",
                        tint = CoralPink
                    )
                }
            }
        }
    }
}

@Composable
fun DebugApkScreen(
    debugApkInfo: DebugApkInfo?,
    isSyncing: Boolean,
    onSyncNow: () -> Unit,
    onExportToDownloads: () -> Unit,
    onShareApk: () -> Unit
) {
    val dateFormat = remember { SimpleDateFormat("yyyy-MM-dd HH:mm:ss", Locale.getDefault()) }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(16.dp)
            .testTag("debug_apk_screen"),
        verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        // Hero Card for /debug/debug.apk Auto-Sync Status
        Card(
            modifier = Modifier.fillMaxWidth(),
            shape = RoundedCornerShape(24.dp),
            colors = CardDefaults.cardColors(containerColor = Color.Transparent)
        ) {
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .background(
                        Brush.linearGradient(
                            colors = listOf(
                                Color(0xFF063729),
                                Color(0xFF0E242D),
                                Color(0xFF152229)
                            )
                        )
                    )
                    .border(
                        width = 1.5.dp,
                        color = WhatsAppNeonGreen.copy(alpha = 0.55f),
                        shape = RoundedCornerShape(24.dp)
                    )
                    .padding(20.dp)
            ) {
                Column {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Box(
                            modifier = Modifier
                                .size(54.dp)
                                .clip(CircleShape)
                                .background(WhatsAppNeonGreen.copy(alpha = 0.2f)),
                            contentAlignment = Alignment.Center
                        ) {
                            Icon(
                                imageVector = Icons.Default.Android,
                                contentDescription = null,
                                tint = WhatsAppNeonGreen,
                                modifier = Modifier.size(32.dp)
                            )
                        }
                        Spacer(modifier = Modifier.width(14.dp))
                        Column {
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Text(
                                    text = "ملف debug / debug.apk",
                                    style = MaterialTheme.typography.titleLarge,
                                    color = Color.White,
                                    fontWeight = FontWeight.Bold
                                )
                                Spacer(modifier = Modifier.width(8.dp))
                                Icon(
                                    imageVector = Icons.Default.Verified,
                                    contentDescription = "مفعل",
                                    tint = WhatsAppNeonGreen,
                                    modifier = Modifier.size(20.dp)
                                )
                            }
                            Text(
                                text = "مزامنة تلقائية مستمرة مع كل بناء أو تحديث مستقبلي",
                                style = MaterialTheme.typography.bodyMedium,
                                color = CyberTeal
                            )
                        }
                    }

                    Spacer(modifier = Modifier.height(16.dp))

                    Surface(
                        color = Color(0xFF081216),
                        shape = RoundedCornerShape(14.dp)
                    ) {
                        Column(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(14.dp),
                            verticalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            DebugMetaRow(
                                label = "مسار النسخة في المشروع:",
                                value = "/debug/debug.apk"
                            )
                            DebugMetaRow(
                                label = "مسار النسخة على الجهاز:",
                                value = debugApkInfo?.localDebugCopyFile?.absolutePath ?: "debug/debug.apk"
                            )
                            DebugMetaRow(
                                label = "الإصدار الحالي:",
                                value = "${debugApkInfo?.versionName ?: "1.0.0-debug"} (Build ${debugApkInfo?.versionCode ?: 1})"
                            )
                            DebugMetaRow(
                                label = "حجم ملف debug.apk:",
                                value = formatFileSize(debugApkInfo?.sizeBytes ?: 0L)
                            )
                            DebugMetaRow(
                                label = "آخر تحديث للنسخة:",
                                value = debugApkInfo?.lastUpdatedMillis?.let { dateFormat.format(Date(it)) } ?: "الآن"
                            )
                            DebugMetaRow(
                                label = "بصمة التوقيع (SHA-256):",
                                value = debugApkInfo?.sha256Short ?: "READY"
                            )
                        }
                    }

                    Spacer(modifier = Modifier.height(16.dp))

                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(10.dp)
                    ) {
                        Button(
                            onClick = onExportToDownloads,
                            enabled = !isSyncing,
                            modifier = Modifier
                                .weight(1.1f)
                                .height(48.dp)
                                .testTag("export_debug_apk_button"),
                            shape = RoundedCornerShape(14.dp),
                            colors = ButtonDefaults.buttonColors(
                                containerColor = WhatsAppNeonGreen,
                                contentColor = Color(0xFF042112)
                            )
                        ) {
                            Icon(
                                imageVector = Icons.Default.Download,
                                contentDescription = null,
                                modifier = Modifier.size(18.dp)
                            )
                            Spacer(modifier = Modifier.width(6.dp))
                            Text(
                                text = "حفظ في Downloads/debug",
                                style = MaterialTheme.typography.labelLarge
                            )
                        }

                        OutlinedButton(
                            onClick = onShareApk,
                            modifier = Modifier
                                .weight(0.9f)
                                .height(48.dp)
                                .testTag("share_debug_apk_button"),
                            shape = RoundedCornerShape(14.dp)
                        ) {
                            Icon(
                                imageVector = Icons.Default.Share,
                                contentDescription = null,
                                tint = CyberTeal,
                                modifier = Modifier.size(18.dp)
                            )
                            Spacer(modifier = Modifier.width(6.dp))
                            Text(
                                text = "مشاركة debug.apk",
                                color = Color.White,
                                style = MaterialTheme.typography.labelLarge
                            )
                        }
                    }

                    Spacer(modifier = Modifier.height(10.dp))

                    OutlinedButton(
                        onClick = onSyncNow,
                        enabled = !isSyncing,
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(44.dp)
                            .testTag("sync_debug_apk_now_button"),
                        shape = RoundedCornerShape(12.dp)
                    ) {
                        Icon(
                            imageVector = Icons.Default.Refresh,
                            contentDescription = null,
                            tint = AmberGold,
                            modifier = Modifier.size(18.dp)
                        )
                        Spacer(modifier = Modifier.width(8.dp))
                        Text(
                            text = if (isSyncing) "جاري تحديث نسخة debug.apk..." else "تحديث ومزامنة نسخة debug.apk الآن",
                            color = Color.White
                        )
                    }
                }
            }
        }

        // Explanation Card of Gradle & Runtime Auto-Update Pipeline
        Card(
            modifier = Modifier.fillMaxWidth(),
            shape = RoundedCornerShape(20.dp),
            colors = CardDefaults.cardColors(containerColor = StudioCard)
        ) {
            Column(
                modifier = Modifier.padding(18.dp),
                verticalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(
                        imageVector = Icons.Default.CheckCircle,
                        contentDescription = null,
                        tint = WhatsAppNeonGreen,
                        modifier = Modifier.size(20.dp)
                    )
                    Spacer(modifier = Modifier.width(8.dp))
                    Text(
                        text = "آلية التحديث التلقائي الدائم لملف debug/debug.apk",
                        style = MaterialTheme.typography.titleMedium,
                        color = Color.White,
                        fontWeight = FontWeight.Bold
                    )
                }

                Text(
                    text = "• على مستوى بناء المشروع (Gradle Build): تمت برمجة مهمة تلقائية (syncDebugApkToRoot) في ملف app/build.gradle.kts مرتبطة بعمليات assembleDebug و packageDebug، بحيث يتم نسخ أحدث حزمة APK فوراً إلى المجلد /debug/debug.apk مع كل تحديث أو تعديل مستقبلي للكود.",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                Text(
                    text = "• على مستوى التشغيل داخل الهاتف (Runtime Auto-Sync): يقوم التطبيق عند كل تشغيل بمقارنة حزمة التثبيت الحالية وتحديث ملف debug/debug.apk تلقائياً ليكون جاهزاً للاستخراج أو المشاركة المباشرة.",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }
    }
}

@Composable
private fun DebugMetaRow(label: String, value: String) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text(
            text = label,
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.weight(0.42f)
        )
        Text(
            text = value,
            style = MaterialTheme.typography.labelLarge,
            color = WhatsAppNeonGreen,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            modifier = Modifier.weight(0.58f)
        )
    }
}

private fun formatFileSize(bytes: Long): String {
    if (bytes <= 0L) return "0 KB"
    val kb = bytes / 1024.0
    return if (kb < 1024.0) {
        "${"%.1f".format(kb)} KB"
    } else {
        "${"%.2f".format(kb / 1024.0)} MB"
    }
}
