package com.example.model

import android.net.Uri
import java.io.File

enum class StickerSource(val labelAr: String) {
    WHATSAPP_FOLDER("مجلد واتساب"),
    USER_IMPORTED("مستورد"),
    BUILTIN_PACK("حزمة جاهزة")
}

data class StickerItem(
    val id: String,
    val name: String,
    val uri: Uri,
    val source: StickerSource,
    val mimeType: String = "image/webp",
    val sizeBytes: Long = 0L,
    val lastModified: Long = System.currentTimeMillis(),
    val isAnimated: Boolean = false,
    val isFavorite: Boolean = false
)

enum class MotionEffect(val titleAr: String, val subtitleAr: String) {
    BOUNCE("ارتداد حيوي", "قفز مرن على إيقاع الفيديو"),
    PULSE_GLOW("نبض متوهج", "تكبير وتصغير ناعم مع هالة ضوئية"),
    ZOOM_CINEMATIC("تقريب سينمائي", "دخول تدريجي مع دوران خفيف"),
    SWING_DANCE("رقصة متأرجحة", "تمايل يميناً ويساراً بحيوية"),
    SPIN_3D("دوران احتفالي", "حركة دائرية استعراضية"),
    FLOAT_WAVE("طفو انسيابي", "حركة موجية هادئة للأعلى والأسفل"),
    NONE("حركة أصلية فقط", "بدون حركة إضافية للملصق")
}

enum class VideoBackgroundStyle(
    val titleAr: String,
    val primaryColorHex: Int,
    val secondaryColorHex: Int
) {
    WHATSAPP_DARK("دردشة واتساب", 0xFF0B141A.toInt(), 0xFF13262F.toInt()),
    EMERALD_GLOW("زمردي متوهج", 0xFF04291E.toInt(), 0xFF00A884.toInt()),
    CYBER_NEON("نيون سايبر", 0xFF090D26.toInt(), 0xFF007B99.toInt()),
    SUNSET_GOLD("غروب ذهبي", 0xFF2B1018.toInt(), 0xFF8A4B08.toInt()),
    PURE_BLACK("أسود نقي OLED", 0xFF000000.toInt(), 0xFF080808.toInt()),
    GREEN_SCREEN("كروما خضراء", 0xFF00FF00.toInt(), 0xFF00CC00.toInt())
}

enum class ParticleOverlay(val titleAr: String) {
    NONE("بدون مؤثرات"),
    SPARKLES("نجوم متلألئة"),
    CONFETTI("قصاصات احتفال"),
    HEARTS("قلوب متطايرة"),
    FIRE_EMBERS("شرارات حماسية")
}

enum class VideoAspectRatio(
    val titleAr: String,
    val badgeText: String,
    val widthPx: Int,
    val heightPx: Int
) {
    SQUARE_1_1("مربع (ملصق/مشاركة)", "1:1", 720, 720),
    PORTRAIT_9_16("حالة واتساب / ريلز", "9:16", 720, 1280),
    LANDSCAPE_16_9("فيديو عريض HD", "16:9", 1280, 720)
}

data class VideoStudioConfig(
    val motionEffect: MotionEffect = MotionEffect.BOUNCE,
    val backgroundStyle: VideoBackgroundStyle = VideoBackgroundStyle.WHATSAPP_DARK,
    val particleOverlay: ParticleOverlay = ParticleOverlay.SPARKLES,
    val aspectRatio: VideoAspectRatio = VideoAspectRatio.SQUARE_1_1,
    val durationSeconds: Int = 4,
    val speedMultiplier: Float = 1.0f,
    val captionText: String = "",
    val showStickerGlowBorder: Boolean = true,
    val fps: Int = 30
)

data class ExportedVideoItem(
    val id: String,
    val file: File,
    val title: String,
    val durationSec: Int,
    val resolution: String,
    val sizeBytes: Long,
    val createdAt: Long,
    val stickerCount: Int
)

data class DebugApkInfo(
    val appName: String,
    val packageName: String,
    val versionName: String,
    val versionCode: Int,
    val sourceApkPath: String,
    val localDebugCopyFile: File,
    val sizeBytes: Long,
    val lastUpdatedMillis: Long,
    val sha256Short: String,
    val autoSyncEnabled: Boolean = true
)
