package com.example.ui.screens

import androidx.compose.animation.AnimatedVisibility
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
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.GridItemSpan
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.AddPhotoAlternate
import androidx.compose.material.icons.filled.AutoAwesome
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Clear
import androidx.compose.material.icons.filled.CreateNewFolder
import androidx.compose.material.icons.filled.Favorite
import androidx.compose.material.icons.filled.FavoriteBorder
import androidx.compose.material.icons.filled.FolderOpen
import androidx.compose.material.icons.filled.Info
import androidx.compose.material.icons.filled.MovieCreation
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Search
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FilterChipDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import coil.compose.AsyncImage
import com.example.model.StickerItem
import com.example.model.StickerSource
import com.example.ui.StickerStudioUiState
import com.example.ui.theme.AmberGold
import com.example.ui.theme.CyberTeal
import com.example.ui.theme.StudioCard
import com.example.ui.theme.StudioCardElevated
import com.example.ui.theme.WhatsAppEmerald
import com.example.ui.theme.WhatsAppNeonGreen

@Composable
fun WhatsAppStickersScreen(
    uiState: StickerStudioUiState,
    onPickWhatsAppFolder: () -> Unit,
    onImportStickerFiles: () -> Unit,
    onPopulateDemoWhatsAppFolder: () -> Unit,
    onSearchQueryChange: (String) -> Unit,
    onSelectSourceFilter: (StickerSource?) -> Unit,
    onToggleFavoritesFilter: () -> Unit,
    onToggleAnimatedFilter: () -> Unit,
    onToggleSelectSticker: (StickerItem) -> Unit,
    onConvertSingleSticker: (StickerItem) -> Unit,
    onOpenSelectedInStudio: () -> Unit,
    onToggleFavorite: (StickerItem) -> Unit
) {
    var showFolderGuideDialog by remember { mutableStateOf(false) }

    Box(modifier = Modifier.fillMaxSize()) {
        LazyVerticalGrid(
            columns = GridCells.Adaptive(minSize = 152.dp),
            modifier = Modifier
                .fillMaxSize()
                .testTag("stickers_grid"),
            contentPadding = PaddingValues(
                start = 16.dp,
                end = 16.dp,
                top = 12.dp,
                bottom = 112.dp
            ),
            horizontalArrangement = Arrangement.spacedBy(12.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            // 1. Studio Hero Banner & WhatsApp Folder Picker Card
            item(span = { GridItemSpan(maxLineSpan) }) {
                WhatsAppFolderHeroCard(
                    linkedUriString = uiState.linkedWhatsAppUri?.lastPathSegment,
                    whatsAppCount = uiState.whatsAppStickerCount,
                    totalCount = uiState.allStickers.size,
                    onPickFolderClick = onPickWhatsAppFolder,
                    onImportFilesClick = onImportStickerFiles,
                    onShowGuideClick = { showFolderGuideDialog = true }
                )
            }

            // 2. Search & Source Filter Bar
            item(span = { GridItemSpan(maxLineSpan) }) {
                SearchAndFilterSection(
                    searchQuery = uiState.searchQuery,
                    selectedSource = uiState.selectedFilterSource,
                    favoritesOnly = uiState.filterFavoritesOnly,
                    animatedOnly = uiState.filterAnimatedOnly,
                    whatsAppCount = uiState.whatsAppStickerCount,
                    onSearchChange = onSearchQueryChange,
                    onSourceSelect = onSelectSourceFilter,
                    onToggleFav = onToggleFavoritesFilter,
                    onToggleAnim = onToggleAnimatedFilter
                )
            }

            // 3. Loading or Empty State or Sticker Cards
            if (uiState.isLoadingStickers) {
                item(span = { GridItemSpan(maxLineSpan) }) {
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(vertical = 48.dp),
                        contentAlignment = Alignment.Center
                    ) {
                        Column(horizontalAlignment = Alignment.CenterHorizontally) {
                            CircularProgressIndicator(color = WhatsAppNeonGreen)
                            Spacer(modifier = Modifier.height(12.dp))
                            Text(
                                text = "جاري فحص وعرض الملصقات...",
                                style = MaterialTheme.typography.bodyLarge
                            )
                        }
                    }
                }
            } else if (uiState.filteredStickers.isEmpty()) {
                item(span = { GridItemSpan(maxLineSpan) }) {
                    EmptyStickersCard(
                        selectedSource = uiState.selectedFilterSource,
                        onPickWhatsAppFolder = onPickWhatsAppFolder,
                        onPopulateDemoFolder = onPopulateDemoWhatsAppFolder,
                        onResetFilters = { onSelectSourceFilter(null) }
                    )
                }
            } else {
                items(
                    items = uiState.filteredStickers,
                    key = { it.id }
                ) { sticker ->
                    val selectionIndex = uiState.selectedStickers.indexOfFirst { it.id == sticker.id }
                    StickerGridCard(
                        sticker = sticker,
                        selectionOrder = if (selectionIndex >= 0) selectionIndex + 1 else null,
                        onCardClick = { onToggleSelectSticker(sticker) },
                        onConvertImmediately = { onConvertSingleSticker(sticker) },
                        onToggleFavorite = { onToggleFavorite(sticker) }
                    )
                }
            }
        }

        // Floating Direct Format Conversion Bar at Bottom
        AnimatedVisibility(
            visible = uiState.selectedStickers.isNotEmpty() || uiState.isEncodingVideo,
            modifier = Modifier
                .align(Alignment.BottomCenter)
                .padding(horizontal = 16.dp, vertical = 12.dp)
        ) {
            SelectedStickersActionBar(
                selectedCount = uiState.selectedStickers.size,
                isEncoding = uiState.isEncodingVideo,
                encodingProgress = uiState.encodingProgress,
                onOpenStudio = onOpenSelectedInStudio
            )
        }
    }

    if (showFolderGuideDialog) {
        WhatsAppFolderGuideDialog(
            onDismiss = { showFolderGuideDialog = false },
            onOpenFolderPicker = {
                showFolderGuideDialog = false
                onPickWhatsAppFolder()
            },
            onCreateSampleFolder = {
                showFolderGuideDialog = false
                onPopulateDemoWhatsAppFolder()
            }
        )
    }
}

@Composable
private fun WhatsAppFolderHeroCard(
    linkedUriString: String?,
    whatsAppCount: Int,
    totalCount: Int,
    onPickFolderClick: () -> Unit,
    onImportFilesClick: () -> Unit,
    onShowGuideClick: () -> Unit
) {
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .testTag("whatsapp_folder_hero_card"),
        shape = RoundedCornerShape(24.dp),
        colors = CardDefaults.cardColors(containerColor = Color.Transparent)
    ) {
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .background(
                    brush = Brush.linearGradient(
                        colors = listOf(
                            Color(0xFF063729),
                            Color(0xFF0B252E),
                            Color(0xFF121E26)
                        )
                    )
                )
                .border(
                    width = 1.dp,
                    brush = Brush.horizontalGradient(
                        listOf(WhatsAppNeonGreen.copy(alpha = 0.5f), CyberTeal.copy(alpha = 0.3f))
                    ),
                    shape = RoundedCornerShape(24.dp)
                )
                .padding(18.dp)
        ) {
            Column {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Box(
                            modifier = Modifier
                                .size(46.dp)
                                .clip(RoundedCornerShape(14.dp))
                                .background(WhatsAppNeonGreen.copy(alpha = 0.18f)),
                            contentAlignment = Alignment.Center
                        ) {
                            Icon(
                                imageVector = Icons.Default.MovieCreation,
                                contentDescription = "أيقونة الاستوديو",
                                tint = WhatsAppNeonGreen,
                                modifier = Modifier.size(26.dp)
                            )
                        }
                        Spacer(modifier = Modifier.width(12.dp))
                        Column {
                            Text(
                                text = "مستعرض ومحول ملصقات واتساب",
                                style = MaterialTheme.typography.titleMedium,
                                color = Color.White,
                                fontWeight = FontWeight.Bold
                            )
                            Text(
                                text = if (whatsAppCount > 0) {
                                    "متصل بمجلد واتساب ($whatsAppCount ملصق واتساب • إجمالي $totalCount)"
                                } else {
                                    "اختر مجلد WhatsApp Stickers لعرض كل ملصقاتك وتحويلها لفيديو"
                                },
                                style = MaterialTheme.typography.bodyMedium,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                    }

                    IconButton(
                        onClick = onShowGuideClick,
                        modifier = Modifier.testTag("folder_guide_button")
                    ) {
                        Icon(
                            imageVector = Icons.Default.Info,
                            contentDescription = "دليل اختيار مجلد واتساب",
                            tint = CyberTeal
                        )
                    }
                }

                if (linkedUriString != null) {
                    Spacer(modifier = Modifier.height(10.dp))
                    Surface(
                        color = StudioCard.copy(alpha = 0.85f),
                        shape = RoundedCornerShape(10.dp)
                    ) {
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(horizontal = 12.dp, vertical = 6.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Icon(
                                imageVector = Icons.Default.CheckCircle,
                                contentDescription = null,
                                tint = WhatsAppNeonGreen,
                                modifier = Modifier.size(16.dp)
                            )
                            Spacer(modifier = Modifier.width(8.dp))
                            Text(
                                text = "المجلد المرتبط: $linkedUriString",
                                style = MaterialTheme.typography.labelMedium,
                                color = WhatsAppNeonGreen,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis
                            )
                        }
                    }
                }

                Spacer(modifier = Modifier.height(16.dp))

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    Button(
                        onClick = onPickFolderClick,
                        modifier = Modifier
                            .weight(1.15f)
                            .height(48.dp)
                            .testTag("pick_whatsapp_folder_button"),
                        shape = RoundedCornerShape(14.dp),
                        colors = ButtonDefaults.buttonColors(
                            containerColor = WhatsAppNeonGreen,
                            contentColor = Color(0xFF042112)
                        )
                    ) {
                        Icon(
                            imageVector = Icons.Default.FolderOpen,
                            contentDescription = null,
                            modifier = Modifier.size(20.dp)
                        )
                        Spacer(modifier = Modifier.width(6.dp))
                        Text(
                            text = "مجلد ملصقات واتساب",
                            style = MaterialTheme.typography.labelLarge,
                            maxLines = 1
                        )
                    }

                    OutlinedButton(
                        onClick = onImportFilesClick,
                        modifier = Modifier
                            .weight(0.95f)
                            .height(48.dp)
                            .testTag("import_any_sticker_button"),
                        shape = RoundedCornerShape(14.dp),
                        colors = ButtonDefaults.outlinedButtonColors(
                            contentColor = Color.White
                        )
                    ) {
                        Icon(
                            imageVector = Icons.Default.AddPhotoAlternate,
                            contentDescription = null,
                            tint = CyberTeal,
                            modifier = Modifier.size(20.dp)
                        )
                        Spacer(modifier = Modifier.width(6.dp))
                        Text(
                            text = "إضافة أي ملصق",
                            style = MaterialTheme.typography.labelLarge,
                            maxLines = 1
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun SearchAndFilterSection(
    searchQuery: String,
    selectedSource: StickerSource?,
    favoritesOnly: Boolean,
    animatedOnly: Boolean,
    whatsAppCount: Int,
    onSearchChange: (String) -> Unit,
    onSourceSelect: (StickerSource?) -> Unit,
    onToggleFav: () -> Unit,
    onToggleAnim: () -> Unit
) {
    Column(modifier = Modifier.fillMaxWidth()) {
        OutlinedTextField(
            value = searchQuery,
            onValueChange = onSearchChange,
            modifier = Modifier
                .fillMaxWidth()
                .testTag("search_stickers_input"),
            placeholder = { Text("ابحث عن ملصق بالاسم أو النوع...") },
            leadingIcon = {
                Icon(
                    imageVector = Icons.Default.Search,
                    contentDescription = "بحث",
                    tint = MaterialTheme.colorScheme.onSurfaceVariant
                )
            },
            trailingIcon = {
                if (searchQuery.isNotEmpty()) {
                    IconButton(onClick = { onSearchChange("") }) {
                        Icon(
                            imageVector = Icons.Default.Clear,
                            contentDescription = "مسح البحث"
                        )
                    }
                }
            },
            singleLine = true,
            shape = RoundedCornerShape(16.dp),
            colors = OutlinedTextFieldDefaults.colors(
                focusedContainerColor = StudioCard,
                unfocusedContainerColor = StudioCard,
                focusedBorderColor = WhatsAppNeonGreen,
                unfocusedBorderColor = Color.Transparent
            )
        )

        Spacer(modifier = Modifier.height(10.dp))

        Row(
            modifier = Modifier
                .fillMaxWidth()
                .horizontalScroll(rememberScrollState()),
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            FilterChip(
                selected = selectedSource == null && !favoritesOnly && !animatedOnly,
                onClick = { onSourceSelect(null) },
                label = { Text("الكل") },
                colors = FilterChipDefaults.filterChipColors(
                    selectedContainerColor = WhatsAppEmerald,
                    selectedLabelColor = Color.White
                )
            )

            FilterChip(
                selected = selectedSource == StickerSource.WHATSAPP_FOLDER,
                onClick = {
                    onSourceSelect(
                        if (selectedSource == StickerSource.WHATSAPP_FOLDER) null else StickerSource.WHATSAPP_FOLDER
                    )
                },
                label = {
                    Text(
                        if (whatsAppCount > 0) "ملصقات واتساب ($whatsAppCount)" else "ملصقات واتساب"
                    )
                },
                colors = FilterChipDefaults.filterChipColors(
                    selectedContainerColor = WhatsAppEmerald,
                    selectedLabelColor = Color.White
                )
            )

            FilterChip(
                selected = selectedSource == StickerSource.USER_IMPORTED,
                onClick = {
                    onSourceSelect(
                        if (selectedSource == StickerSource.USER_IMPORTED) null else StickerSource.USER_IMPORTED
                    )
                },
                label = { Text("ملصقات مستوردة") },
                colors = FilterChipDefaults.filterChipColors(
                    selectedContainerColor = WhatsAppEmerald,
                    selectedLabelColor = Color.White
                )
            )

            FilterChip(
                selected = animatedOnly,
                onClick = onToggleAnim,
                label = { Text("متحركة WebP/GIF") },
                leadingIcon = {
                    Icon(
                        imageVector = Icons.Default.AutoAwesome,
                        contentDescription = null,
                        modifier = Modifier.size(16.dp),
                        tint = if (animatedOnly) Color.White else AmberGold
                    )
                },
                colors = FilterChipDefaults.filterChipColors(
                    selectedContainerColor = WhatsAppEmerald,
                    selectedLabelColor = Color.White
                )
            )

            FilterChip(
                selected = favoritesOnly,
                onClick = onToggleFav,
                label = { Text("المفضلة") },
                leadingIcon = {
                    Icon(
                        imageVector = Icons.Default.Favorite,
                        contentDescription = null,
                        modifier = Modifier.size(16.dp),
                        tint = if (favoritesOnly) Color.White else Color(0xFFFF5252)
                    )
                },
                colors = FilterChipDefaults.filterChipColors(
                    selectedContainerColor = WhatsAppEmerald,
                    selectedLabelColor = Color.White
                )
            )
        }
    }
}

@Composable
private fun StickerGridCard(
    sticker: StickerItem,
    selectionOrder: Int?,
    onCardClick: () -> Unit,
    onConvertImmediately: () -> Unit,
    onToggleFavorite: () -> Unit
) {
    val isSelected = selectionOrder != null
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(20.dp))
            .clickable { onCardClick() }
            .border(
                width = if (isSelected) 2.5.dp else 1.dp,
                color = if (isSelected) WhatsAppNeonGreen else Color(0xFF223642),
                shape = RoundedCornerShape(20.dp)
            )
            .testTag("sticker_card_${sticker.id}"),
        shape = RoundedCornerShape(20.dp),
        colors = CardDefaults.cardColors(
            containerColor = if (isSelected) StudioCardElevated else StudioCard
        )
    ) {
        Column(modifier = Modifier.padding(10.dp)) {
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .aspectRatio(1f)
                    .clip(RoundedCornerShape(16.dp))
                    .background(
                        Brush.radialGradient(
                            colors = listOf(
                                Color(0xFF233946),
                                Color(0xFF0E1A20)
                            )
                        )
                    ),
                contentAlignment = Alignment.Center
            ) {
                AsyncImage(
                    model = sticker.uri,
                    contentDescription = sticker.name,
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(12.dp),
                    contentScale = ContentScale.Fit
                )

                // Top-Right Badge: Animated or Source
                Surface(
                    modifier = Modifier
                        .align(Alignment.TopStart)
                        .padding(6.dp),
                    shape = RoundedCornerShape(8.dp),
                    color = if (sticker.isAnimated) AmberGold.copy(alpha = 0.9f) else Color(0xCC0B141A)
                ) {
                    Text(
                        text = if (sticker.isAnimated) "متحرك" else sticker.source.labelAr,
                        style = MaterialTheme.typography.labelMedium,
                        color = if (sticker.isAnimated) Color(0xFF1F1400) else CyberTeal,
                        modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp)
                    )
                }

                // Top-Left Favorite Button
                IconButton(
                    onClick = onToggleFavorite,
                    modifier = Modifier
                        .align(Alignment.TopEnd)
                        .size(34.dp)
                        .padding(4.dp)
                ) {
                    Icon(
                        imageVector = if (sticker.isFavorite) Icons.Default.Favorite else Icons.Default.FavoriteBorder,
                        contentDescription = "مفضلة",
                        tint = if (sticker.isFavorite) Color(0xFFFF5252) else Color.White.copy(alpha = 0.7f),
                        modifier = Modifier.size(18.dp)
                    )
                }

                // Selection order badge at Bottom-End
                if (selectionOrder != null) {
                    Box(
                        modifier = Modifier
                            .align(Alignment.BottomEnd)
                            .padding(6.dp)
                            .size(26.dp)
                            .clip(CircleShape)
                            .background(WhatsAppNeonGreen),
                        contentAlignment = Alignment.Center
                    ) {
                        Text(
                            text = selectionOrder.toString(),
                            style = MaterialTheme.typography.labelLarge,
                            color = Color(0xFF042112)
                        )
                    }
                }
            }

            Spacer(modifier = Modifier.height(8.dp))

            Text(
                text = sticker.name.removeSuffix(".webp").removeSuffix(".gif"),
                style = MaterialTheme.typography.titleSmall,
                color = Color.White,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )

            Spacer(modifier = Modifier.height(6.dp))

            Button(
                onClick = onConvertImmediately,
                modifier = Modifier
                    .fillMaxWidth()
                    .height(38.dp)
                    .testTag("convert_sticker_btn_${sticker.id}"),
                shape = RoundedCornerShape(10.dp),
                contentPadding = PaddingValues(horizontal = 8.dp, vertical = 0.dp),
                colors = ButtonDefaults.buttonColors(
                    containerColor = WhatsAppEmerald,
                    contentColor = Color.White
                )
            ) {
                Icon(
                    imageVector = Icons.Default.PlayArrow,
                    contentDescription = null,
                    modifier = Modifier.size(16.dp)
                )
                Spacer(modifier = Modifier.width(4.dp))
                Text(
                    text = "تحويل لفيديو",
                    style = MaterialTheme.typography.labelMedium,
                    fontWeight = FontWeight.Bold
                )
            }
        }
    }
}

@Composable
private fun EmptyStickersCard(
    selectedSource: StickerSource?,
    onPickWhatsAppFolder: () -> Unit,
    onPopulateDemoFolder: () -> Unit,
    onResetFilters: () -> Unit
) {
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 16.dp),
        shape = RoundedCornerShape(22.dp),
        colors = CardDefaults.cardColors(containerColor = StudioCard)
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(24.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Icon(
                imageVector = Icons.Default.FolderOpen,
                contentDescription = null,
                tint = WhatsAppNeonGreen,
                modifier = Modifier.size(52.dp)
            )
            Spacer(modifier = Modifier.height(12.dp))
            Text(
                text = if (selectedSource == StickerSource.WHATSAPP_FOLDER) {
                    "لم يتم ربط مجلد ملصقات واتساب بعد"
                } else {
                    "لا توجد ملصقات مطابقة للفلتر الحالي"
                },
                style = MaterialTheme.typography.titleMedium,
                color = Color.White
            )
            Spacer(modifier = Modifier.height(6.dp))
            Text(
                text = "يمكنك اختيار مجلد ملصقات واتساب الأصلي من جهازك (Android/media/com.whatsapp/WhatsApp/Media/WhatsApp Stickers) أو توليد مجلد ملصقات واتساب تجريبي فوراً لتجربة الاستعراض والتحويل.",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            Spacer(modifier = Modifier.height(16.dp))
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                Button(
                    onClick = onPickWhatsAppFolder,
                    modifier = Modifier.weight(1f),
                    shape = RoundedCornerShape(12.dp),
                    colors = ButtonDefaults.buttonColors(
                        containerColor = WhatsAppNeonGreen,
                        contentColor = Color(0xFF042112)
                    )
                ) {
                    Text("اختيار مجلد واتساب")
                }

                OutlinedButton(
                    onClick = onPopulateDemoFolder,
                    modifier = Modifier
                        .weight(1f)
                        .testTag("populate_demo_wa_button"),
                    shape = RoundedCornerShape(12.dp)
                ) {
                    Icon(
                        imageVector = Icons.Default.CreateNewFolder,
                        contentDescription = null,
                        tint = CyberTeal,
                        modifier = Modifier.size(18.dp)
                    )
                    Spacer(modifier = Modifier.width(6.dp))
                    Text("تجهيز مجلد تجريبي", color = Color.White)
                }
            }
            if (selectedSource != null) {
                Spacer(modifier = Modifier.height(8.dp))
                TextButton(onClick = onResetFilters) {
                    Text("عرض جميع الملصقات المتاحة", color = CyberTeal)
                }
            }
        }
    }
}

@Composable
private fun SelectedStickersActionBar(
    selectedCount: Int,
    isEncoding: Boolean,
    encodingProgress: Float,
    onOpenStudio: () -> Unit
) {
    Surface(
        modifier = Modifier
            .fillMaxWidth()
            .border(1.dp, WhatsAppNeonGreen.copy(alpha = 0.6f), RoundedCornerShape(20.dp)),
        shape = RoundedCornerShape(20.dp),
        color = Color(0xF20D1F26),
        shadowElevation = 12.dp
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp, vertical = 12.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = if (isEncoding) {
                        "جاري تحويل الصيغة إلى MP4 (${(encodingProgress * 100).toInt()}%)..."
                    } else if (selectedCount == 1) {
                        "تحويل مباشر لصيغة الملصق المتحرك"
                    } else {
                        "تحويل مباشر لـ $selectedCount ملصقات إلى MP4"
                    },
                    style = MaterialTheme.typography.titleSmall,
                    color = Color.White,
                    fontWeight = FontWeight.Bold
                )
                Text(
                    text = "تحويل خام (WebP/GIF ➔ MP4) بدون أي تعديل أو إضافات",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }

            Spacer(modifier = Modifier.width(12.dp))

            Button(
                onClick = onOpenStudio,
                enabled = !isEncoding,
                modifier = Modifier
                    .height(48.dp)
                    .testTag("open_selected_in_studio_button"),
                shape = RoundedCornerShape(14.dp),
                colors = ButtonDefaults.buttonColors(
                    containerColor = WhatsAppNeonGreen,
                    contentColor = Color(0xFF042112)
                )
            ) {
                Icon(
                    imageVector = Icons.Default.MovieCreation,
                    contentDescription = null,
                    modifier = Modifier.size(20.dp)
                )
                Spacer(modifier = Modifier.width(6.dp))
                Text(
                    text = if (isEncoding) "جاري التحويل..." else "تحويل إلى MP4",
                    style = MaterialTheme.typography.labelLarge
                )
            }
        }
    }
}

@Composable
private fun WhatsAppFolderGuideDialog(
    onDismiss: () -> Unit,
    onOpenFolderPicker: () -> Unit,
    onCreateSampleFolder: () -> Unit
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        containerColor = StudioCardElevated,
        title = {
            Text(
                text = "كيفية عرض جميع ملصقات واتساب",
                style = MaterialTheme.typography.titleLarge,
                color = Color.White
            )
        },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Text(
                    text = "يقوم واتساب بحفظ جميع الملصقات التي أرسلتها أو استقبلتها بصيغة (.webp) في المسار التالي:",
                    style = MaterialTheme.typography.bodyMedium,
                    color = Color.White
                )
                Surface(
                    color = Color(0xFF0B141A),
                    shape = RoundedCornerShape(10.dp)
                ) {
                    Text(
                        text = "Android / media / com.whatsapp / WhatsApp / Media / WhatsApp Stickers",
                        style = MaterialTheme.typography.labelMedium,
                        color = WhatsAppNeonGreen,
                        modifier = Modifier.padding(10.dp)
                    )
                }
                Text(
                    text = "1. اضغط على «فتح مستعرض المجلدات» بالأسفل.\n2. اختر مجلد WhatsApp Stickers ثم اضغط «استخدام هذا المجلد».\n3. إذا لم يكن واتساب مثبتاً لديك حالياً، يمكنك الضغط على «تجهيز مجلد واتساب تجريبي» لإنشاء مجلد ملصقات واتساب فوري وتجربة التطبيق بالكامل.",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        },
        confirmButton = {
            Button(
                onClick = onOpenFolderPicker,
                colors = ButtonDefaults.buttonColors(
                    containerColor = WhatsAppNeonGreen,
                    contentColor = Color(0xFF042112)
                )
            ) {
                Text("فتح مستعرض المجلدات")
            }
        },
        dismissButton = {
            OutlinedButton(onClick = onCreateSampleFolder) {
                Text("تجهيز مجلد واتساب تجريبي", color = CyberTeal)
            }
        }
    )
}
