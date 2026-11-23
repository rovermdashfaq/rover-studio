package com.example.data.model

import androidx.annotation.Keep
import com.google.firebase.Timestamp
import com.google.firebase.firestore.DocumentId
import com.google.firebase.firestore.IgnoreExtraProperties
import com.google.firebase.firestore.ServerTimestamp

sealed interface UiState<out T> {
    data object Loading : UiState<Nothing>
    data class Success<T>(val data: T) : UiState<T>
    data class Error(val message: String) : UiState<Nothing>
}

enum class VideoResolution(val label: String, val width: Int, val height: Int, val firestoreKey: String) {
    QHD_2K("2K QHD (2560×1440)", 2560, 1440, "2K_QHD"),
    FHD_1080("Full HD (1920×1080)", 1920, 1080, "FHD_1080");

    companion object {
        fun fromKey(raw: String): VideoResolution =
            entries.firstOrNull { it.firestoreKey == raw } ?: QHD_2K
    }
}

enum class LayerSlot(val slotId: String, val displayLabel: String) {
    LAYER_1("layer1", "লেয়ার ১ (শিরোনাম ব্যাকগ্রাউন্ড)"),
    LAYER_2("layer2", "লেয়ার ২ (বুলেটিন ব্যাজ PNG)"),
    LAYER_3("layer3", "লেয়ার ৩ (টাইমার ও তারিখ বক্স PNG)"),
    LOGO_PNG("logo_png", "লোগো সাজেশন (PNG)"),
    LOGO_VIDEO("logo_video", "লোগো সাজেশন (ভিডিও)");

    companion object {
        fun fromSlotId(id: String): LayerSlot? = entries.firstOrNull { it.slotId == id }
    }
}

@Keep
@IgnoreExtraProperties
data class LayerAsset(
    @DocumentId val id: String = "",
    val userId: String = "",
    val slot: String = "",
    val label: String = "",
    val sourceType: String = "url",
    val content: String = "",
    @ServerTimestamp val createdAt: Timestamp? = null,
    @ServerTimestamp val updatedAt: Timestamp? = null
)

@Keep
@IgnoreExtraProperties
data class BulletinPreset(
    @DocumentId val id: String = "",
    val userId: String = "",
    val name: String = "Default Bulletin",
    val title1: String = "বাংলাদেশ স্কাউটস রোভার অঞ্চলের নতুন আপডেট...",
    val title2: String = "বিস্তারিত জানতে চোখ রাখুন আমাদের পরবর্তী বুলেটিনে...",
    val startTime: String = "07:00:00",
    val ampm: String = "PM",
    val durationSeconds: Long = 30L,
    val scrollSpeed: Double = 2.5,
    val logoWidth: Long = 200L,
    val logoHeight: Long = 200L,
    val targetFps: Long = 60L,
    val resolution: String = "2K_QHD",
    val chromaHex: String = "#0047BB",
    @ServerTimestamp val createdAt: Timestamp? = null,
    @ServerTimestamp val updatedAt: Timestamp? = null
)

@Keep
@IgnoreExtraProperties
data class NameplatePreset(
    @DocumentId val id: String = "",
    val userId: String = "",
    val nameText: String = "",
    val unitText: String = "",
    val accentColorHex: String = "#FF2B4A",
    @ServerTimestamp val createdAt: Timestamp? = null,
    @ServerTimestamp val updatedAt: Timestamp? = null
)

@Keep
@IgnoreExtraProperties
data class CreditSection(
    val title: String = "",
    val namesText: String = "",
    val titleColorHex: String = "#DC2626",
    val nameColorHex: String = "#111827",
    val titleSize: Long = 48L,
    val nameSize: Long = 40L
) {
    fun parsedNames(): List<String> =
        namesText.split(',', '，', '\n')
            .map { it.trim() }
            .filter { it.isNotEmpty() }

    fun toMap(): Map<String, Any> = mapOf(
        "title" to title.ifBlank { "শিরোনাম" },
        "namesText" to namesText,
        "titleColorHex" to titleColorHex,
        "nameColorHex" to nameColorHex,
        "titleSize" to titleSize,
        "nameSize" to nameSize
    )

    companion object {
        fun fromMap(map: Map<*, *>): CreditSection = CreditSection(
            title = (map["title"] as? String).orEmpty(),
            namesText = (map["namesText"] as? String).orEmpty(),
            titleColorHex = (map["titleColorHex"] as? String) ?: "#DC2626",
            nameColorHex = (map["nameColorHex"] as? String) ?: "#111827",
            titleSize = (map["titleSize"] as? Number)?.toLong() ?: 48L,
            nameSize = (map["nameSize"] as? Number)?.toLong() ?: 40L
        )
    }
}

@Keep
@IgnoreExtraProperties
data class CreditProject(
    @DocumentId val id: String = "",
    val userId: String = "",
    val projectTitle: String = "",
    val sections: List<Map<String, Any>> = emptyList(),
    @ServerTimestamp val createdAt: Timestamp? = null,
    @ServerTimestamp val updatedAt: Timestamp? = null
)

@Keep
@IgnoreExtraProperties
data class QuickLink(
    @DocumentId val id: String = "",
    val userId: String = "",
    val title: String = "",
    val url: String = "",
    val thumbnailUrl: String? = null,
    @ServerTimestamp val createdAt: Timestamp? = null,
    @ServerTimestamp val updatedAt: Timestamp? = null
)

data class ExportProgress(
    val running: Boolean = false,
    val progress: Float = 0f,
    val currentFrame: Int = 0,
    val totalFrames: Int = 0,
    val statusMessage: String = ""
)
