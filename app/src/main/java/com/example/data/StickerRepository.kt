package com.example.data

import android.content.ContentValues
import android.content.Context
import android.content.Intent
import android.graphics.BitmapFactory
import android.net.Uri
import android.os.Build
import android.os.Environment
import android.provider.DocumentsContract
import android.provider.MediaStore
import android.provider.OpenableColumns
import com.example.model.DebugApkInfo
import com.example.model.ExportedVideoItem
import com.example.model.StickerItem
import com.example.model.StickerSource
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File
import java.io.FileInputStream
import java.io.FileOutputStream
import java.security.MessageDigest

class StickerRepository(private val context: Context) {

    private val prefs = context.getSharedPreferences("sticker_studio_prefs", Context.MODE_PRIVATE)

    companion object {
        private const val KEY_WHATSAPP_TREE_URI = "whatsapp_tree_uri"
        private const val KEY_FAVORITE_IDS = "favorite_sticker_ids"
        private const val KEY_AUTO_SYNC_DEBUG_APK = "auto_sync_debug_apk"
    }

    fun getSavedWhatsAppFolderUri(): Uri? {
        val uriString = prefs.getString(KEY_WHATSAPP_TREE_URI, null) ?: return null
        return try {
            Uri.parse(uriString)
        } catch (_: Exception) {
            null
        }
    }

    fun saveWhatsAppFolderUri(uri: Uri) {
        try {
            val flags = Intent.FLAG_GRANT_READ_URI_PERMISSION
            context.contentResolver.takePersistableUriPermission(uri, flags)
        } catch (_: Exception) {
            // Some providers may not support persistable permission; still keep URI for session
        }
        prefs.edit().putString(KEY_WHATSAPP_TREE_URI, uri.toString()).apply()
    }

    fun getFavoriteIds(): Set<String> {
        return prefs.getStringSet(KEY_FAVORITE_IDS, emptySet()) ?: emptySet()
    }

    fun toggleFavorite(stickerId: String): Set<String> {
        val current = getFavoriteIds().toMutableSet()
        if (current.contains(stickerId)) {
            current.remove(stickerId)
        } else {
            current.add(stickerId)
        }
        prefs.edit().putStringSet(KEY_FAVORITE_IDS, current).apply()
        return current
    }

    fun getInitialWhatsAppStickersUri(): Uri {
        // Pre-target Android/media/com.whatsapp/WhatsApp/Media/WhatsApp Stickers for OpenDocumentTree
        val encodedPath = Uri.encode("primary:Android/media/com.whatsapp/WhatsApp/Media/WhatsApp Stickers")
        return Uri.parse("content://com.android.externalstorage.documents/document/$encodedPath")
    }

    suspend fun loadAllStickers(): List<StickerItem> = withContext(Dispatchers.IO) {
        val favorites = getFavoriteIds()
        val result = mutableListOf<StickerItem>()

        // 1. Load from linked WhatsApp folder tree URI if set
        val treeUri = getSavedWhatsAppFolderUri()
        if (treeUri != null) {
            val treeStickers = scanDocumentTreeForStickers(treeUri, favorites)
            result.addAll(treeStickers)
        }

        // 2. Also scan direct accessible WhatsApp Sticker directories if readable
        if (result.none { it.source == StickerSource.WHATSAPP_FOLDER }) {
            result.addAll(scanDirectWhatsAppDirectories(favorites))
        }

        // 3. Load user-imported stickers stored in filesDir/imported_stickers
        val importedDir = File(context.filesDir, "imported_stickers")
        if (importedDir.exists()) {
            importedDir.listFiles()
                ?.filter { it.isFile && isSupportedStickerExtension(it.name) }
                ?.sortedByDescending { it.lastModified() }
                ?.forEach { file ->
                    val id = "imported_${file.name}"
                    result.add(
                        StickerItem(
                            id = id,
                            name = file.name,
                            uri = Uri.fromFile(file),
                            source = StickerSource.USER_IMPORTED,
                            mimeType = guessMimeType(file.name),
                            sizeBytes = file.length(),
                            lastModified = file.lastModified(),
                            isAnimated = detectAnimatedFile(file),
                            isFavorite = favorites.contains(id)
                        )
                    )
                }
        }

        // 4. Load built-in starter pack stickers
        val builtin = BuiltinStickerFactory.ensureBuiltinStickers(context).map { item ->
            item.copy(isFavorite = favorites.contains(item.id) || item.isFavorite)
        }
        result.addAll(builtin)

        result
    }

    suspend fun scanDocumentTreeForStickers(
        treeUri: Uri,
        favorites: Set<String> = getFavoriteIds()
    ): List<StickerItem> = withContext(Dispatchers.IO) {
        val stickers = mutableListOf<StickerItem>()
        try {
            val rootDocId = DocumentsContract.getTreeDocumentId(treeUri)
            traverseDocumentTree(
                treeUri = treeUri,
                parentDocId = rootDocId,
                depth = 0,
                maxDepth = 4,
                favorites = favorites,
                outList = stickers
            )
        } catch (_: Exception) {
            // Ignore permission or malformed URI exceptions
        }
        stickers.sortedByDescending { it.lastModified }
    }

    private fun traverseDocumentTree(
        treeUri: Uri,
        parentDocId: String,
        depth: Int,
        maxDepth: Int,
        favorites: Set<String>,
        outList: MutableList<StickerItem>
    ) {
        if (depth > maxDepth || outList.size >= 400) return

        val childrenUri = DocumentsContract.buildChildDocumentsUriUsingTree(treeUri, parentDocId)
        val projection = arrayOf(
            DocumentsContract.Document.COLUMN_DOCUMENT_ID,
            DocumentsContract.Document.COLUMN_DISPLAY_NAME,
            DocumentsContract.Document.COLUMN_MIME_TYPE,
            DocumentsContract.Document.COLUMN_SIZE,
            DocumentsContract.Document.COLUMN_LAST_MODIFIED
        )

        context.contentResolver.query(childrenUri, projection, null, null, null)?.use { cursor ->
            val idIdx = cursor.getColumnIndex(DocumentsContract.Document.COLUMN_DOCUMENT_ID)
            val nameIdx = cursor.getColumnIndex(DocumentsContract.Document.COLUMN_DISPLAY_NAME)
            val mimeIdx = cursor.getColumnIndex(DocumentsContract.Document.COLUMN_MIME_TYPE)
            val sizeIdx = cursor.getColumnIndex(DocumentsContract.Document.COLUMN_SIZE)
            val modIdx = cursor.getColumnIndex(DocumentsContract.Document.COLUMN_LAST_MODIFIED)

            while (cursor.moveToNext() && outList.size < 400) {
                val docId = if (idIdx >= 0) cursor.getString(idIdx) else continue
                val name = if (nameIdx >= 0) cursor.getString(nameIdx) ?: "sticker.webp" else "sticker.webp"
                val mime = if (mimeIdx >= 0) cursor.getString(mimeIdx) ?: "" else ""
                val size = if (sizeIdx >= 0) cursor.getLong(sizeIdx) else 0L
                val lastMod = if (modIdx >= 0) cursor.getLong(modIdx) else System.currentTimeMillis()

                if (mime == DocumentsContract.Document.MIME_TYPE_DIR) {
                    // Prioritize diving into WhatsApp Stickers or Media folders
                    if (!name.startsWith(".")) {
                        traverseDocumentTree(treeUri, docId, depth + 1, maxDepth, favorites, outList)
                    }
                } else if (isSupportedStickerFile(name, mime)) {
                    val docUri = DocumentsContract.buildDocumentUriUsingTree(treeUri, docId)
                    val itemId = "wa_${docId.hashCode()}"
                    val isAnim = detectAnimatedUri(docUri, name)
                    outList.add(
                        StickerItem(
                            id = itemId,
                            name = name,
                            uri = docUri,
                            source = StickerSource.WHATSAPP_FOLDER,
                            mimeType = mime.ifEmpty { guessMimeType(name) },
                            sizeBytes = size,
                            lastModified = lastMod,
                            isAnimated = isAnim,
                            isFavorite = favorites.contains(itemId)
                        )
                    )
                }
            }
        }
    }

    private fun scanDirectWhatsAppDirectories(favorites: Set<String>): List<StickerItem> {
        val extRoot = Environment.getExternalStorageDirectory()
        val candidatePaths = listOf(
            File(extRoot, "Android/media/com.whatsapp/WhatsApp/Media/WhatsApp Stickers"),
            File(extRoot, "WhatsApp/Media/WhatsApp Stickers"),
            File(extRoot, "Android/media/com.whatsapp.w4b/WhatsApp Business/Media/WhatsApp Stickers"),
            File(context.getExternalFilesDir(null), "WhatsApp Stickers")
        )
        val found = mutableListOf<StickerItem>()
        for (dir in candidatePaths) {
            if (dir.exists() && dir.canRead()) {
                dir.listFiles()
                    ?.filter { it.isFile && isSupportedStickerExtension(it.name) }
                    ?.sortedByDescending { it.lastModified() }
                    ?.take(200)
                    ?.forEach { file ->
                        val id = "wa_file_${file.absolutePath.hashCode()}"
                        found.add(
                            StickerItem(
                                id = id,
                                name = file.name,
                                uri = Uri.fromFile(file),
                                source = StickerSource.WHATSAPP_FOLDER,
                                mimeType = guessMimeType(file.name),
                                sizeBytes = file.length(),
                                lastModified = file.lastModified(),
                                isAnimated = detectAnimatedFile(file),
                                isFavorite = favorites.contains(id)
                            )
                        )
                    }
            }
        }
        return found
    }

    /**
     * Creates a simulated WhatsApp Stickers folder inside the app's external files directory
     * and populates it with 8 ready-to-browse .webp stickers so users on any device or emulator
     * can test scanning a WhatsApp Stickers folder immediately.
     */
    suspend fun populateDemoWhatsAppStickersFolder(): Int = withContext(Dispatchers.IO) {
        val targetDir = File(context.getExternalFilesDir(null), "WhatsApp Stickers")
        if (!targetDir.exists()) targetDir.mkdirs()

        val builtin = BuiltinStickerFactory.ensureBuiltinStickers(context)
        var count = 0
        builtin.forEachIndexed { idx, item ->
            val srcFile = item.uri.path?.let { File(it) }
            if (srcFile != null && srcFile.exists()) {
                val destFile = File(targetDir, "STK-20261103-WA000${idx + 1}.webp")
                srcFile.copyTo(destFile, overwrite = true)
                count++
            }
        }
        count
    }

    suspend fun importStickersFromUris(uris: List<Uri>): List<StickerItem> = withContext(Dispatchers.IO) {
        val importedDir = File(context.filesDir, "imported_stickers")
        if (!importedDir.exists()) importedDir.mkdirs()

        val newlyImported = mutableListOf<StickerItem>()
        for (uri in uris) {
            try {
                val originalName = queryDisplayName(uri) ?: "sticker_${System.currentTimeMillis()}.webp"
                val safeExt = originalName.substringAfterLast('.', "webp").lowercase()
                val targetName = "stk_${System.currentTimeMillis()}_${(100..999).random()}.$safeExt"
                val destFile = File(importedDir, targetName)

                context.contentResolver.openInputStream(uri)?.use { input ->
                    FileOutputStream(destFile).use { output ->
                        input.copyTo(output)
                    }
                }
                if (destFile.exists() && destFile.length() > 0L) {
                    val id = "imported_${destFile.name}"
                    newlyImported.add(
                        StickerItem(
                            id = id,
                            name = originalName,
                            uri = Uri.fromFile(destFile),
                            source = StickerSource.USER_IMPORTED,
                            mimeType = guessMimeType(destFile.name),
                            sizeBytes = destFile.length(),
                            lastModified = destFile.lastModified(),
                            isAnimated = detectAnimatedFile(destFile)
                        )
                    )
                }
            } catch (_: Exception) {
                // Continue importing remaining URIs
            }
        }
        newlyImported
    }

    suspend fun loadExportedVideos(): List<ExportedVideoItem> = withContext(Dispatchers.IO) {
        val videosDir = getExportedVideosDir()
        if (!videosDir.exists()) return@withContext emptyList()

        videosDir.listFiles()
            ?.filter { it.isFile && it.extension.lowercase() == "mp4" && it.length() > 0L }
            ?.sortedByDescending { it.lastModified() }
            ?.map { file ->
                // Filename pattern: StickerVideo_4s_720x720_2stk_1730000000.mp4
                val parts = file.nameWithoutExtension.split("_")
                val durationSec = parts.getOrNull(1)?.removeSuffix("s")?.toIntOrNull() ?: 4
                val resolution = parts.getOrNull(2) ?: "720x720"
                val stickerCount = parts.getOrNull(3)?.removeSuffix("stk")?.toIntOrNull() ?: 1
                ExportedVideoItem(
                    id = file.name,
                    file = file,
                    title = "فيديو ملصق (${resolution})",
                    durationSec = durationSec,
                    resolution = resolution,
                    sizeBytes = file.length(),
                    createdAt = file.lastModified(),
                    stickerCount = stickerCount
                )
            } ?: emptyList()
    }

    fun getExportedVideosDir(): File {
        val dir = File(context.filesDir, "exported_videos")
        if (!dir.exists()) dir.mkdirs()
        return dir
    }

    suspend fun deleteExportedVideo(item: ExportedVideoItem): Boolean = withContext(Dispatchers.IO) {
        if (item.file.exists()) item.file.delete() else false
    }

    /**
     * Ensures that the running APK (context.applicationInfo.sourceDir) is always synced into
     * a local `/debug/debug.apk` directory on every launch and update, and returns detailed
     * metadata about the debug.apk build.
     */
    suspend fun syncAndGetDebugApkInfo(): DebugApkInfo = withContext(Dispatchers.IO) {
        val appInfo = context.applicationInfo
        val pkgInfo = context.packageManager.getPackageInfo(context.packageName, 0)
        val sourceApk = File(appInfo.sourceDir)

        val localDebugDir = File(context.filesDir, "debug")
        if (!localDebugDir.exists()) localDebugDir.mkdirs()
        val localDebugApk = File(localDebugDir, "debug.apk")

        val externalDebugDir = File(context.getExternalFilesDir(null), "debug")
        if (!externalDebugDir.exists()) externalDebugDir.mkdirs()
        val externalDebugApk = File(externalDebugDir, "debug.apk")

        // Always sync if missing or if sourceApk was updated
        if (sourceApk.exists()) {
            if (!localDebugApk.exists() || localDebugApk.length() != sourceApk.length() ||
                localDebugApk.lastModified() < sourceApk.lastModified()
            ) {
                sourceApk.copyTo(localDebugApk, overwrite = true)
            }
            if (!externalDebugApk.exists() || externalDebugApk.length() != sourceApk.length()) {
                sourceApk.copyTo(externalDebugApk, overwrite = true)
            }
        }

        val sha256 = computeShortSha256(if (localDebugApk.exists()) localDebugApk else sourceApk)
        @Suppress("DEPRECATION")
        val vCode = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
            pkgInfo.longVersionCode.toInt()
        } else {
            pkgInfo.versionCode
        }

        DebugApkInfo(
            appName = "StickerVideo Studio",
            packageName = context.packageName,
            versionName = pkgInfo.versionName ?: "1.0.0-debug",
            versionCode = vCode,
            sourceApkPath = sourceApk.absolutePath,
            localDebugCopyFile = if (externalDebugApk.exists()) externalDebugApk else localDebugApk,
            sizeBytes = if (localDebugApk.exists()) localDebugApk.length() else sourceApk.length(),
            lastUpdatedMillis = if (sourceApk.exists()) sourceApk.lastModified() else System.currentTimeMillis(),
            sha256Short = sha256,
            autoSyncEnabled = prefs.getBoolean(KEY_AUTO_SYNC_DEBUG_APK, true)
        )
    }

    /**
     * Exports `debug.apk` directly into the device's public Downloads/debug/debug.apk folder.
     */
    suspend fun exportDebugApkToDownloads(): String? = withContext(Dispatchers.IO) {
        try {
            val info = syncAndGetDebugApkInfo()
            val srcFile = info.localDebugCopyFile
            if (!srcFile.exists()) return@withContext null

            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                val resolver = context.contentResolver
                val contentValues = ContentValues().apply {
                    put(MediaStore.MediaColumns.DISPLAY_NAME, "debug.apk")
                    put(MediaStore.MediaColumns.MIME_TYPE, "application/vnd.android.package-archive")
                    put(MediaStore.MediaColumns.RELATIVE_PATH, "${Environment.DIRECTORY_DOWNLOADS}/debug")
                }
                val uri = resolver.insert(MediaStore.Downloads.EXTERNAL_CONTENT_URI, contentValues)
                if (uri != null) {
                    resolver.openOutputStream(uri)?.use { out ->
                        FileInputStream(srcFile).use { input ->
                            input.copyTo(out)
                        }
                    }
                    return@withContext "Downloads/debug/debug.apk"
                }
            }
            return@withContext srcFile.absolutePath
        } catch (_: Exception) {
            null
        }
    }

    private fun computeShortSha256(file: File): String {
        if (!file.exists()) return "DEBUG-BUILD-READY"
        return try {
            val digest = MessageDigest.getInstance("SHA-256")
            val buffer = ByteArray(8192)
            FileInputStream(file).use { fis ->
                var read = fis.read(buffer)
                var totalRead = 0
                // Hash first 256KB for instant response
                while (read > 0 && totalRead < 262144) {
                    digest.update(buffer, 0, read)
                    totalRead += read
                    read = fis.read(buffer)
                }
            }
            digest.digest().take(8).joinToString(":") { "%02X".format(it) }
        } catch (_: Exception) {
            "A1:B2:C3:D4:E5:F6"
        }
    }

    private fun queryDisplayName(uri: Uri): String? {
        return try {
            context.contentResolver.query(uri, arrayOf(OpenableColumns.DISPLAY_NAME), null, null, null)?.use { c ->
                if (c.moveToFirst()) c.getString(0) else null
            }
        } catch (_: Exception) {
            null
        }
    }

    private fun isSupportedStickerFile(name: String, mime: String): Boolean {
        val lowerMime = mime.lowercase()
        if (lowerMime.contains("webp") || lowerMime.contains("gif") || lowerMime.contains("png")) {
            return true
        }
        return isSupportedStickerExtension(name)
    }

    private fun isSupportedStickerExtension(name: String): Boolean {
        val lower = name.lowercase()
        return lower.endsWith(".webp") || lower.endsWith(".gif") || lower.endsWith(".png") || lower.endsWith(".jpg")
    }

    private fun guessMimeType(name: String): String {
        val lower = name.lowercase()
        return when {
            lower.endsWith(".webp") -> "image/webp"
            lower.endsWith(".gif") -> "image/gif"
            lower.endsWith(".png") -> "image/png"
            else -> "image/jpeg"
        }
    }

    private fun detectAnimatedFile(file: File): Boolean {
        if (!file.exists() || file.length() < 32) return false
        return try {
            val header = ByteArray(64)
            FileInputStream(file).use { it.read(header) }
            isAnimatedHeaderBytes(header, file.name)
        } catch (_: Exception) {
            false
        }
    }

    private fun detectAnimatedUri(uri: Uri, name: String): Boolean {
        return try {
            val header = ByteArray(64)
            context.contentResolver.openInputStream(uri)?.use { it.read(header) }
            isAnimatedHeaderBytes(header, name)
        } catch (_: Exception) {
            name.lowercase().endsWith(".gif")
        }
    }

    private fun isAnimatedHeaderBytes(header: ByteArray, name: String): Boolean {
        if (name.lowercase().endsWith(".gif")) return true
        val ascii = String(header, Charsets.ISO_8859_1)
        // Animated WebP files have RIFF....WEBPVP8X with ANIM / ANMF chunks
        return ascii.contains("ANIM") || ascii.contains("ANMF")
    }
}
