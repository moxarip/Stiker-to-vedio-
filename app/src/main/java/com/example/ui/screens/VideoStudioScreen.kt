package com.example.ui.screens

import android.graphics.Bitmap
import android.graphics.Canvas as AndroidCanvas
import android.graphics.Color as AndroidColor
import android.graphics.PorterDuff
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.FolderOpen
import androidx.compose.material.icons.filled.MovieCreation
import androidx.compose.material.icons.filled.Pause
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.SwapHoriz
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.example.model.StickerItem
import com.example.model.VideoStudioConfig
import com.example.ui.StickerStudioUiState
import com.example.ui.theme.CyberTeal
import com.example.ui.theme.StudioCard
import com.example.ui.theme.StudioCardElevated
import com.example.ui.theme.WhatsAppNeonGreen
import com.example.video.LoadedStickerAsset
import com.example.video.StickerVideoEncoder
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import kotlinx.coroutines.withContext

@Composable
fun VideoStudioScreen(
    uiState: StickerStudioUiState,
    @Suppress("UNUSED_PARAMETER") onUpdateConfig: ((VideoStudioConfig) -> VideoStudioConfig) -> Unit,
    @Suppress("UNUSED_PARAMETER") onRemoveStickerFromSelection: (StickerItem) -> Unit,
    onAddMoreStickersClick: () -> Unit,
    onStartEncodingMp4: () -> Unit
) {
    val context = LocalContext.current
    val config = uiState.studioConfig
    val selectedStickers = uiState.selectedStickers

    var loadedAssets by remember { mutableStateOf<List<LoadedStickerAsset>>(emptyList()) }
    var isPreviewPlaying by remember { mutableStateOf(true) }
    var previewTimeSec by remember { mutableFloatStateOf(0f) }

    LaunchedEffect(selectedStickers) {
        loadedAssets = withContext(Dispatchers.IO) {
            selectedStickers.mapNotNull { StickerVideoEncoder.loadStickerAsset(context, it) }
        }
    }

    val totalLoopDurationSec = remember(loadedAssets) {
        val ms = loadedAssets.sumOf { it.totalDurationMs }.coerceAtLeast(1000)
        ms / 1000f
    }

    LaunchedEffect(isPreviewPlaying, totalLoopDurationSec, uiState.isEncodingVideo) {
        if (!isPreviewPlaying || uiState.isEncodingVideo) return@LaunchedEffect
        while (isActive) {
            delay(40L)
            previewTimeSec = (previewTimeSec + 0.04f) % totalLoopDurationSec.coerceAtLeast(0.5f)
        }
    }

    val offscreenBuffer = remember {
        Bitmap.createBitmap(384, 384, Bitmap.Config.ARGB_8888)
    }

    val previewBitmap = remember(
        offscreenBuffer,
        loadedAssets,
        previewTimeSec
    ) {
        val softCanvas = AndroidCanvas(offscreenBuffer)
        softCanvas.drawColor(AndroidColor.BLACK, PorterDuff.Mode.CLEAR)
        StickerVideoEncoder.drawStudioFrame(
            canvas = softCanvas,
            width = offscreenBuffer.width,
            height = offscreenBuffer.height,
            assets = loadedAssets,
            config = config,
            timeSeconds = previewTimeSec,
            totalDurationSeconds = totalLoopDurationSec
        )
        offscreenBuffer.asImageBitmap()
    }

    val primaryAsset = loadedAssets.firstOrNull()

    Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(horizontal = 16.dp, vertical = 12.dp)
            .testTag("video_studio_screen"),
        verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        // Pure Format Conversion Preview Card (WebP / GIF -> MP4 without any modification)
        Card(
            modifier = Modifier.fillMaxWidth(),
            shape = RoundedCornerShape(24.dp),
            colors = CardDefaults.cardColors(containerColor = StudioCard)
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
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Icon(
                            imageVector = Icons.Default.SwapHoriz,
                            contentDescription = null,
                            tint = WhatsAppNeonGreen,
                            modifier = Modifier.size(22.dp)
                        )
                        Spacer(modifier = Modifier.width(8.dp))
                        Text(
                            text = "تحويل الصيغة المباشر (WebP / GIF ➔ MP4)",
                            style = MaterialTheme.typography.titleMedium,
                            color = Color.White,
                            fontWeight = FontWeight.Bold
                        )
                    }

                    IconButton(
                        onClick = { isPreviewPlaying = !isPreviewPlaying },
                        modifier = Modifier.size(32.dp)
                    ) {
                        Icon(
                            imageVector = if (isPreviewPlaying) Icons.Default.Pause else Icons.Default.PlayArrow,
                            contentDescription = if (isPreviewPlaying) "إيقاف مؤقت" else "تشغيل",
                            tint = WhatsAppNeonGreen
                        )
                    }
                }

                Spacer(modifier = Modifier.height(6.dp))

                Text(
                    text = "يتم استخراج جميع إطارات الملصق المتحرك الأصلية وتحويلها كما هي تماماً إلى صيغة فيديو MP4 بدون أي تعديل أو إضافات.",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )

                Spacer(modifier = Modifier.height(14.dp))

                Box(
                    modifier = Modifier
                        .size(240.dp)
                        .clip(RoundedCornerShape(18.dp))
                        .background(Color.Black)
                        .border(1.5.dp, WhatsAppNeonGreen.copy(alpha = 0.45f), RoundedCornerShape(18.dp)),
                    contentAlignment = Alignment.Center
                ) {
                    Image(
                        bitmap = previewBitmap,
                        contentDescription = "معاينة الملصق المتحرك الأصلي",
                        modifier = Modifier
                            .fillMaxSize()
                            .testTag("studio_preview_canvas"),
                        contentScale = ContentScale.Fit
                    )
                }

                Spacer(modifier = Modifier.height(14.dp))

                // Technical format specification summary
                Surface(
                    color = Color(0xFF0B141A),
                    shape = RoundedCornerShape(14.dp),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Column(
                        modifier = Modifier.padding(14.dp),
                        verticalArrangement = Arrangement.spacedBy(6.dp)
                    ) {
                        FormatInfoRow(
                            label = "الملف المحدد:",
                            value = primaryAsset?.item?.name ?: "لم يتم التحديد"
                        )
                        FormatInfoRow(
                            label = "الصيغة الأصلية:",
                            value = if (primaryAsset?.isAnimated == true) "ملصق متحرك أصلي (Animated WebP / GIF)" else "ملصق WebP"
                        )
                        FormatInfoRow(
                            label = "عدد الإطارات الأصلية:",
                            value = "${primaryAsset?.frameCount ?: 1} إطار أصلي"
                        )
                        FormatInfoRow(
                            label = "الأبعاد الأصلية:",
                            value = "${primaryAsset?.width ?: 512} × ${primaryAsset?.height ?: 512} بكسل"
                        )
                        FormatInfoRow(
                            label = "الصيغة الناتجة:",
                            value = "فيديو MP4 (H.264 AVC) خام بدون أي تعديل"
                        )
                    }
                }
            }
        }

        // Direct Conversion Action Card
        Card(
            modifier = Modifier.fillMaxWidth(),
            shape = RoundedCornerShape(22.dp),
            colors = CardDefaults.cardColors(containerColor = StudioCardElevated)
        ) {
            Column(modifier = Modifier.padding(16.dp)) {
                if (uiState.isEncodingVideo) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(
                            text = "جاري تحويل صيغة الملصق المتحرك إلى MP4...",
                            style = MaterialTheme.typography.titleMedium,
                            color = WhatsAppNeonGreen
                        )
                        Text(
                            text = "${(uiState.encodingProgress * 100).toInt()}%",
                            style = MaterialTheme.typography.titleMedium,
                            color = Color.White,
                            fontWeight = FontWeight.Bold
                        )
                    }
                    Spacer(modifier = Modifier.height(10.dp))
                    LinearProgressIndicator(
                        progress = { uiState.encodingProgress },
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(10.dp)
                            .clip(RoundedCornerShape(5.dp)),
                        color = WhatsAppNeonGreen,
                        trackColor = Color(0xFF0E1B22)
                    )
                } else {
                    Button(
                        onClick = onStartEncodingMp4,
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(54.dp)
                            .testTag("start_mp4_encoding_button"),
                        shape = RoundedCornerShape(16.dp),
                        colors = ButtonDefaults.buttonColors(
                            containerColor = WhatsAppNeonGreen,
                            contentColor = Color(0xFF042112)
                        )
                    ) {
                        Icon(
                            imageVector = Icons.Default.MovieCreation,
                            contentDescription = null,
                            modifier = Modifier.size(22.dp)
                        )
                        Spacer(modifier = Modifier.width(8.dp))
                        Text(
                            text = "تحويل الصيغة إلى MP4 الآن (بدون تعديل)",
                            style = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.Bold
                        )
                    }

                    Spacer(modifier = Modifier.height(10.dp))

                    OutlinedButton(
                        onClick = onAddMoreStickersClick,
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(46.dp),
                        shape = RoundedCornerShape(14.dp)
                    ) {
                        Icon(
                            imageVector = Icons.Default.FolderOpen,
                            contentDescription = null,
                            tint = CyberTeal,
                            modifier = Modifier.size(18.dp)
                        )
                        Spacer(modifier = Modifier.width(8.dp))
                        Text(
                            text = "اختيار ملصق آخر من ملصقات واتساب",
                            color = Color.White
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun FormatInfoRow(label: String, value: String) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceBetween
    ) {
        Text(
            text = label,
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
        Text(
            text = value,
            style = MaterialTheme.typography.labelLarge,
            color = CyberTeal
        )
    }
}
