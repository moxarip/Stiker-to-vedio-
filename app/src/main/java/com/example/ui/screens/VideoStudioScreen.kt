package com.example.ui.screens

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
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
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.AutoAwesome
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.MovieCreation
import androidx.compose.material.icons.filled.Pause
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FilterChipDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.SwitchDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.runtime.withFrameNanos
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.nativeCanvas
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import coil.compose.AsyncImage
import com.example.model.MotionEffect
import com.example.model.ParticleOverlay
import com.example.model.StickerItem
import com.example.model.VideoAspectRatio
import com.example.model.VideoBackgroundStyle
import com.example.model.VideoStudioConfig
import com.example.ui.StickerStudioUiState
import com.example.ui.theme.CyberTeal
import com.example.ui.theme.StudioCard
import com.example.ui.theme.StudioCardElevated
import com.example.ui.theme.WhatsAppEmerald
import com.example.ui.theme.WhatsAppNeonGreen
import com.example.video.LoadedStickerAsset
import com.example.video.StickerVideoEncoder
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.isActive
import kotlinx.coroutines.withContext

@Composable
fun VideoStudioScreen(
    uiState: StickerStudioUiState,
    onUpdateConfig: ((VideoStudioConfig) -> VideoStudioConfig) -> Unit,
    onRemoveStickerFromSelection: (StickerItem) -> Unit,
    onAddMoreStickersClick: () -> Unit,
    onStartEncodingMp4: () -> Unit
) {
    val context = LocalContext.current
    val config = uiState.studioConfig
    val selectedStickers = uiState.selectedStickers

    var loadedAssets by remember { mutableStateOf<List<LoadedStickerAsset>>(emptyList()) }
    var isPreviewPlaying by remember { mutableStateOf(true) }
    var previewTimeSec by remember { mutableFloatStateOf(0f) }

    // Load selected sticker bitmaps/movies whenever selection changes
    LaunchedEffect(selectedStickers) {
        loadedAssets = withContext(Dispatchers.IO) {
            selectedStickers.mapNotNull { StickerVideoEncoder.loadStickerAsset(context, it) }
        }
    }

    // 60fps animation clock for live preview canvas
    LaunchedEffect(isPreviewPlaying, config.durationSeconds) {
        if (!isPreviewPlaying) return@LaunchedEffect
        var lastNanos = 0L
        while (isActive) {
            withFrameNanos { frameNanos ->
                if (lastNanos != 0L) {
                    val deltaSec = (frameNanos - lastNanos) / 1_000_000_000f
                    val totalDur = config.durationSeconds.coerceAtLeast(1).toFloat()
                    previewTimeSec = (previewTimeSec + deltaSec) % totalDur
                }
                lastNanos = frameNanos
            }
        }
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(horizontal = 16.dp, vertical = 12.dp)
            .testTag("video_studio_screen"),
        verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        // 1. Live Interactive Studio Canvas Preview Card
        Card(
            modifier = Modifier.fillMaxWidth(),
            shape = RoundedCornerShape(24.dp),
            colors = CardDefaults.cardColors(containerColor = StudioCard)
        ) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(14.dp),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Icon(
                            imageVector = Icons.Default.AutoAwesome,
                            contentDescription = null,
                            tint = WhatsAppNeonGreen,
                            modifier = Modifier.size(20.dp)
                        )
                        Spacer(modifier = Modifier.width(8.dp))
                        Text(
                            text = "معاينة حية للفيديو (${config.aspectRatio.badgeText})",
                            style = MaterialTheme.typography.titleMedium,
                            color = Color.White
                        )
                    }

                    Surface(
                        color = StudioCardElevated,
                        shape = RoundedCornerShape(10.dp)
                    ) {
                        Row(
                            modifier = Modifier.padding(horizontal = 10.dp, vertical = 4.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Text(
                                text = "${"%.1f".format(previewTimeSec)}s / ${config.durationSeconds}s",
                                style = MaterialTheme.typography.labelMedium,
                                color = CyberTeal
                            )
                            Spacer(modifier = Modifier.width(6.dp))
                            IconButton(
                                onClick = { isPreviewPlaying = !isPreviewPlaying },
                                modifier = Modifier.size(26.dp)
                            ) {
                                Icon(
                                    imageVector = if (isPreviewPlaying) Icons.Default.Pause else Icons.Default.PlayArrow,
                                    contentDescription = if (isPreviewPlaying) "إيقاف مؤقت" else "تشغيل المعاينة",
                                    tint = WhatsAppNeonGreen,
                                    modifier = Modifier.size(18.dp)
                                )
                            }
                        }
                    }
                }

                Spacer(modifier = Modifier.height(12.dp))

                val previewRatio = config.aspectRatio.widthPx.toFloat() / config.aspectRatio.heightPx.toFloat()
                Box(
                    modifier = Modifier
                        .heightIn(max = 290.dp)
                        .aspectRatio(previewRatio)
                        .clip(RoundedCornerShape(18.dp))
                        .border(1.5.dp, WhatsAppNeonGreen.copy(alpha = 0.45f), RoundedCornerShape(18.dp)),
                    contentAlignment = Alignment.Center
                ) {
                    Canvas(
                        modifier = Modifier
                            .fillMaxSize()
                            .testTag("studio_preview_canvas")
                    ) {
                        val canvasWidth = size.width.toInt().coerceAtLeast(1)
                        val canvasHeight = size.height.toInt().coerceAtLeast(1)
                        StickerVideoEncoder.drawStudioFrame(
                            canvas = drawContext.canvas.nativeCanvas,
                            width = canvasWidth,
                            height = canvasHeight,
                            assets = loadedAssets,
                            config = config,
                            timeSeconds = previewTimeSec,
                            totalDurationSeconds = config.durationSeconds.toFloat()
                        )
                    }
                }
            }
        }

        // 2. Primary Convert & Export MP4 Action Card (with live progress bar)
        ExportMp4ActionCard(
            isEncoding = uiState.isEncodingVideo,
            progress = uiState.encodingProgress,
            stickerCount = selectedStickers.size,
            config = config,
            onStartEncoding = onStartEncodingMp4
        )

        // 3. Selected Stickers Sequence Strip
        SelectedStickersStripCard(
            stickers = selectedStickers,
            onRemoveSticker = onRemoveStickerFromSelection,
            onAddMoreClick = onAddMoreStickersClick
        )

        // 4. Motion Effect Selector
        StudioSectionCard(title = "حركة الملصق داخل الفيديو") {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .horizontalScroll(rememberScrollState()),
                horizontalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                MotionEffect.entries.forEach { effect ->
                    val selected = config.motionEffect == effect
                    Surface(
                        modifier = Modifier
                            .width(142.dp)
                            .clip(RoundedCornerShape(16.dp))
                            .clickable { onUpdateConfig { it.copy(motionEffect = effect) } }
                            .border(
                                width = if (selected) 2.dp else 1.dp,
                                color = if (selected) WhatsAppNeonGreen else Color.Transparent,
                                shape = RoundedCornerShape(16.dp)
                            ),
                        shape = RoundedCornerShape(16.dp),
                        color = if (selected) StudioCardElevated else Color(0xFF101D24)
                    ) {
                        Column(modifier = Modifier.padding(12.dp)) {
                            Text(
                                text = effect.titleAr,
                                style = MaterialTheme.typography.titleSmall,
                                color = if (selected) WhatsAppNeonGreen else Color.White,
                                fontWeight = FontWeight.Bold
                            )
                            Spacer(modifier = Modifier.height(4.dp))
                            Text(
                                text = effect.subtitleAr,
                                style = MaterialTheme.typography.bodyMedium,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                                maxLines = 2
                            )
                        }
                    }
                }
            }
        }

        // 5. Video Aspect Ratio & Duration Selector
        StudioSectionCard(title = "أبعاد الفيديو والمدة الزمنية") {
            Text(
                text = "مقاس الفيديو:",
                style = MaterialTheme.typography.labelLarge,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            Spacer(modifier = Modifier.height(8.dp))
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                VideoAspectRatio.entries.forEach { ratio ->
                    val selected = config.aspectRatio == ratio
                    FilterChip(
                        selected = selected,
                        onClick = { onUpdateConfig { it.copy(aspectRatio = ratio) } },
                        modifier = Modifier.weight(1f),
                        label = {
                            Column(
                                modifier = Modifier.padding(vertical = 4.dp),
                                horizontalAlignment = Alignment.CenterHorizontally
                            ) {
                                Text(
                                    text = ratio.badgeText,
                                    style = MaterialTheme.typography.titleSmall,
                                    fontWeight = FontWeight.Bold
                                )
                                Text(
                                    text = ratio.titleAr,
                                    style = MaterialTheme.typography.labelMedium,
                                    maxLines = 1
                                )
                            }
                        },
                        colors = FilterChipDefaults.filterChipColors(
                            selectedContainerColor = WhatsAppEmerald,
                            selectedLabelColor = Color.White
                        )
                    )
                }
            }

            Spacer(modifier = Modifier.height(14.dp))

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = "مدة الفيديو (بالثواني):",
                    style = MaterialTheme.typography.labelLarge,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    listOf(3, 4, 6, 8, 10).forEach { sec ->
                        val selected = config.durationSeconds == sec
                        FilterChip(
                            selected = selected,
                            onClick = { onUpdateConfig { it.copy(durationSeconds = sec) } },
                            label = { Text("${sec}s") },
                            colors = FilterChipDefaults.filterChipColors(
                                selectedContainerColor = WhatsAppNeonGreen,
                                selectedLabelColor = Color(0xFF042112)
                            )
                        )
                    }
                }
            }

            Spacer(modifier = Modifier.height(8.dp))

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = "سرعة الحركة:",
                    style = MaterialTheme.typography.labelLarge,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    listOf(0.75f to "0.75x", 1.0f to "1x", 1.5f to "1.5x", 2.0f to "2x").forEach { (spd, label) ->
                        val selected = config.speedMultiplier == spd
                        FilterChip(
                            selected = selected,
                            onClick = { onUpdateConfig { it.copy(speedMultiplier = spd) } },
                            label = { Text(label) },
                            colors = FilterChipDefaults.filterChipColors(
                                selectedContainerColor = CyberTeal,
                                selectedLabelColor = Color(0xFF00262B)
                            )
                        )
                    }
                }
            }
        }

        // 6. Background Style & Particle Overlay Selector
        StudioSectionCard(title = "خلفية الفيديو والمؤثرات البصرية") {
            Text(
                text = "خلفية الفيديو:",
                style = MaterialTheme.typography.labelLarge,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            Spacer(modifier = Modifier.height(8.dp))
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .horizontalScroll(rememberScrollState()),
                horizontalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                VideoBackgroundStyle.entries.forEach { bg ->
                    val selected = config.backgroundStyle == bg
                    Column(
                        horizontalAlignment = Alignment.CenterHorizontally,
                        modifier = Modifier
                            .clip(RoundedCornerShape(14.dp))
                            .clickable { onUpdateConfig { it.copy(backgroundStyle = bg) } }
                            .padding(6.dp)
                    ) {
                        Box(
                            modifier = Modifier
                                .size(54.dp)
                                .clip(CircleShape)
                                .background(
                                    Brush.linearGradient(
                                        listOf(Color(bg.primaryColorHex), Color(bg.secondaryColorHex))
                                    )
                                )
                                .border(
                                    width = if (selected) 3.dp else 1.dp,
                                    color = if (selected) WhatsAppNeonGreen else Color.White.copy(alpha = 0.3f),
                                    shape = CircleShape
                                )
                        )
                        Spacer(modifier = Modifier.height(4.dp))
                        Text(
                            text = bg.titleAr,
                            style = MaterialTheme.typography.labelMedium,
                            color = if (selected) WhatsAppNeonGreen else Color.White
                        )
                    }
                }
            }

            Spacer(modifier = Modifier.height(14.dp))

            Text(
                text = "مؤثرات الجزيئات المتطايرة:",
                style = MaterialTheme.typography.labelLarge,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            Spacer(modifier = Modifier.height(8.dp))
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .horizontalScroll(rememberScrollState()),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                ParticleOverlay.entries.forEach { particle ->
                    FilterChip(
                        selected = config.particleOverlay == particle,
                        onClick = { onUpdateConfig { it.copy(particleOverlay = particle) } },
                        label = { Text(particle.titleAr) },
                        colors = FilterChipDefaults.filterChipColors(
                            selectedContainerColor = WhatsAppEmerald,
                            selectedLabelColor = Color.White
                        )
                    )
                }
            }
        }

        // 7. Custom Caption Text & Sticker Glow Toggle
        StudioSectionCard(title = "كتابة نص على الفيديو والتوهج") {
            OutlinedTextField(
                value = config.captionText,
                onValueChange = { newText ->
                    onUpdateConfig { it.copy(captionText = newText.take(40)) }
                },
                modifier = Modifier
                    .fillMaxWidth()
                    .testTag("video_caption_input"),
                label = { Text("نص إضافي يظهر أسفل الفيديو (اختياري)") },
                placeholder = { Text("مثال: جمعة مباركة، صباح الخير، عاش يا بطل...") },
                singleLine = true,
                shape = RoundedCornerShape(14.dp),
                colors = OutlinedTextFieldDefaults.colors(
                    focusedBorderColor = WhatsAppNeonGreen,
                    unfocusedBorderColor = Color(0xFF283C48)
                )
            )

            Spacer(modifier = Modifier.height(12.dp))

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = "هالة ضوئية حول الملصق",
                        style = MaterialTheme.typography.titleSmall,
                        color = Color.White
                    )
                    Text(
                        text = "إبراز الملصق بتوهج ناعم يتفاعل مع الحركة",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
                Switch(
                    checked = config.showStickerGlowBorder,
                    onCheckedChange = { checked ->
                        onUpdateConfig { it.copy(showStickerGlowBorder = checked) }
                    },
                    colors = SwitchDefaults.colors(
                        checkedThumbColor = Color(0xFF042112),
                        checkedTrackColor = WhatsAppNeonGreen
                    )
                )
            }
        }

        Spacer(modifier = Modifier.height(32.dp))
    }
}

@Composable
private fun ExportMp4ActionCard(
    isEncoding: Boolean,
    progress: Float,
    stickerCount: Int,
    config: VideoStudioConfig,
    onStartEncoding: () -> Unit
) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(22.dp),
        colors = CardDefaults.cardColors(containerColor = StudioCardElevated)
    ) {
        Column(modifier = Modifier.padding(16.dp)) {
            if (isEncoding) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = "جاري تحويل الملصقات إلى فيديو MP4...",
                        style = MaterialTheme.typography.titleMedium,
                        color = WhatsAppNeonGreen
                    )
                    Text(
                        text = "${(progress * 100).toInt()}%",
                        style = MaterialTheme.typography.titleMedium,
                        color = Color.White,
                        fontWeight = FontWeight.Bold
                    )
                }
                Spacer(modifier = Modifier.height(10.dp))
                LinearProgressIndicator(
                    progress = { progress },
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(10.dp)
                        .clip(RoundedCornerShape(5.dp)),
                    color = WhatsAppNeonGreen,
                    trackColor = Color(0xFF0E1B22)
                )
                Spacer(modifier = Modifier.height(6.dp))
                Text(
                    text = "يتم ترميز الإطارات بدقة ${config.aspectRatio.widthPx}x${config.aspectRatio.heightPx} بمعدل ${config.fps} إطار/ثانية (H.264 AVC)",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            } else {
                Button(
                    onClick = onStartEncoding,
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
                        text = if (stickerCount > 1) {
                            "تحويل وتصدير الفيديو ($stickerCount ملصقات • MP4)"
                        } else {
                            "تحويل الملصق إلى فيديو (MP4)"
                        },
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Bold
                    )
                }
            }
        }
    }
}

@Composable
private fun SelectedStickersStripCard(
    stickers: List<StickerItem>,
    onRemoveSticker: (StickerItem) -> Unit,
    onAddMoreClick: () -> Unit
) {
    StudioSectionCard(title = "الملصقات المدمجة في الفيديو (${stickers.size})") {
        LazyRow(
            contentPadding = PaddingValues(vertical = 4.dp),
            horizontalArrangement = Arrangement.spacedBy(10.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            itemsIndexed(stickers, key = { idx, item -> "${item.id}_$idx" }) { index, sticker ->
                Box(
                    modifier = Modifier
                        .size(76.dp)
                        .clip(RoundedCornerShape(16.dp))
                        .background(Color(0xFF101D24))
                        .border(1.dp, WhatsAppNeonGreen.copy(alpha = 0.5f), RoundedCornerShape(16.dp))
                ) {
                    AsyncImage(
                        model = sticker.uri,
                        contentDescription = sticker.name,
                        modifier = Modifier
                            .fillMaxSize()
                            .padding(8.dp),
                        contentScale = ContentScale.Fit
                    )
                    // Sequence badge
                    Box(
                        modifier = Modifier
                            .align(Alignment.BottomStart)
                            .padding(4.dp)
                            .size(20.dp)
                            .clip(CircleShape)
                            .background(WhatsAppEmerald),
                        contentAlignment = Alignment.Center
                    ) {
                        Text(
                            text = "${index + 1}",
                            style = MaterialTheme.typography.labelMedium,
                            color = Color.White
                        )
                    }
                    // Remove button if more than 1
                    if (stickers.size > 1) {
                        Box(
                            modifier = Modifier
                                .align(Alignment.TopEnd)
                                .padding(3.dp)
                                .size(20.dp)
                                .clip(CircleShape)
                                .background(Color(0xCC000000))
                                .clickable { onRemoveSticker(sticker) },
                            contentAlignment = Alignment.Center
                        ) {
                            Icon(
                                imageVector = Icons.Default.Close,
                                contentDescription = "إزالة",
                                tint = Color.White,
                                modifier = Modifier.size(13.dp)
                            )
                        }
                    }
                }
            }

            item {
                Box(
                    modifier = Modifier
                        .size(76.dp)
                        .clip(RoundedCornerShape(16.dp))
                        .background(StudioCardElevated)
                        .border(1.dp, CyberTeal.copy(alpha = 0.5f), RoundedCornerShape(16.dp))
                        .clickable { onAddMoreClick() },
                    contentAlignment = Alignment.Center
                ) {
                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                        Icon(
                            imageVector = Icons.Default.Add,
                            contentDescription = "إضافة ملصق",
                            tint = CyberTeal
                        )
                        Text(
                            text = "إضافة",
                            style = MaterialTheme.typography.labelMedium,
                            color = CyberTeal
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun StudioSectionCard(
    title: String,
    content: @Composable () -> Unit
) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(20.dp),
        colors = CardDefaults.cardColors(containerColor = StudioCard)
    ) {
        Column(modifier = Modifier.padding(16.dp)) {
            Text(
                text = title,
                style = MaterialTheme.typography.titleMedium,
                color = Color.White,
                fontWeight = FontWeight.Bold
            )
            Spacer(modifier = Modifier.height(12.dp))
            content()
        }
    }
}
