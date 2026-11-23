package com.example.data.repository

import android.content.Context
import com.example.R
import com.example.data.model.BulletinPreset
import com.example.data.model.CreditProject
import com.example.data.model.CreditSection
import com.example.data.model.LayerAsset
import com.example.data.model.NameplatePreset
import com.example.data.model.QuickLink
import com.example.data.remote.OperationType
import com.example.data.remote.handleFirestoreError
import com.google.firebase.Firebase
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.auth.auth
import com.google.firebase.firestore.DocumentSnapshot
import com.google.firebase.firestore.FieldValue
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.FirebaseFirestoreException
import com.google.firebase.firestore.snapshots
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.emitAll
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.tasks.await

class StudioRepository(
    private val db: FirebaseFirestore,
    private val auth: FirebaseAuth = Firebase.auth
) {
    constructor(context: Context) : this(
        FirebaseFirestore.getInstance(
            context.applicationContext.getString(R.string.firestore_database_id)
        )
    )

    private fun requireUserId(): String {
        return auth.currentUser?.uid
            ?: throw IllegalStateException("User must be signed in with Google before accessing Firestore.")
    }

    private fun validateId(id: String): String {
        val clean = id.replace(Regex("[^a-zA-Z0-9_\\-]"), "_")
        require(clean.isNotBlank() && clean.length <= 128) { "Invalid document ID" }
        return clean
    }

    // =========================================================================
    // LAYER ASSETS
    // =========================================================================

    fun observeLayerAssets(userId: String): Flow<List<LayerAsset>> = flow {
        require(userId.isNotBlank()) { "User ID must not be blank" }
        val path = "users/$userId/layer_assets"
        emitAll(
            db.collection("users").document(userId).collection("layer_assets")
                .snapshots()
                .map { snap ->
                    snap.toObjects(LayerAsset::class.java, DocumentSnapshot.ServerTimestampBehavior.ESTIMATE)
                }
                .catch { error ->
                    logStreamError(error, OperationType.LIST, path, userId)
                    throw error
                }
        )
    }

    suspend fun saveLayerAsset(
        slot: String,
        label: String,
        sourceType: String,
        content: String
    ): Result<Unit> {
        val safeSlot = slot.trim()
        var path = "users/${auth.currentUser?.uid ?: "unauthenticated"}/layer_assets/$safeSlot"
        return try {
            val uid = requireUserId()
            require(safeSlot in listOf("layer1", "layer2", "layer3", "logo_png", "logo_video")) {
                "Unsupported layer slot: $safeSlot"
            }
            require(sourceType in listOf("url", "data_uri")) { "Invalid sourceType" }
            val cleanLabel = label.trim().take(120)
            require(cleanLabel.isNotEmpty()) { "Label must not be empty" }
            val cleanContent = content.trim()
            require(cleanContent.isNotEmpty() && cleanContent.length <= 800_000) {
                "Asset size exceeds Firestore 800KB limit"
            }

            val docRef = db.collection("users").document(uid)
                .collection("layer_assets").document(safeSlot)
            path = docRef.path

            val existing = docRef.get().await()
            if (existing.exists()) {
                docRef.update(
                    mapOf(
                        "label" to cleanLabel,
                        "sourceType" to sourceType,
                        "content" to cleanContent,
                        "updatedAt" to FieldValue.serverTimestamp()
                    )
                ).await()
            } else {
                docRef.set(
                    mapOf(
                        "userId" to uid,
                        "slot" to safeSlot,
                        "label" to cleanLabel,
                        "sourceType" to sourceType,
                        "content" to cleanContent,
                        "createdAt" to FieldValue.serverTimestamp(),
                        "updatedAt" to FieldValue.serverTimestamp()
                    )
                ).await()
            }
            Result.success(Unit)
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            handleFirestoreError(e, OperationType.WRITE, path)
            Result.failure(e)
        }
    }

    suspend fun deleteLayerAsset(slot: String): Result<Unit> {
        var path = "users/${auth.currentUser?.uid ?: "unauthenticated"}/layer_assets/$slot"
        return try {
            val uid = requireUserId()
            val safeSlot = validateId(slot)
            val docRef = db.collection("users").document(uid)
                .collection("layer_assets").document(safeSlot)
            path = docRef.path
            docRef.delete().await()
            Result.success(Unit)
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            handleFirestoreError(e, OperationType.DELETE, path)
            Result.failure(e)
        }
    }

    // =========================================================================
    // BULLETIN PRESETS
    // =========================================================================

    fun observeBulletinPresets(userId: String): Flow<List<BulletinPreset>> = flow {
        require(userId.isNotBlank()) { "User ID must not be blank" }
        val path = "users/$userId/bulletin_presets"
        emitAll(
            db.collection("users").document(userId).collection("bulletin_presets")
                .snapshots()
                .map { snap ->
                    snap.toObjects(BulletinPreset::class.java, DocumentSnapshot.ServerTimestampBehavior.ESTIMATE)
                        .sortedByDescending { it.updatedAt ?: it.createdAt }
                }
                .catch { error ->
                    logStreamError(error, OperationType.LIST, path, userId)
                    throw error
                }
        )
    }

    suspend fun saveBulletinPreset(preset: BulletinPreset): Result<Unit> {
        var path = "users/${auth.currentUser?.uid ?: "unauthenticated"}/bulletin_presets"
        return try {
            val uid = requireUserId()
            val cleanName = preset.name.trim().take(100)
            val cleanTitle1 = preset.title1.trim().take(500)
            val cleanTitle2 = preset.title2.trim().take(500)
            val cleanTime = preset.startTime.trim().take(20)
            val cleanAmPm = if (preset.ampm == "AM") "AM" else "PM"
            val duration = preset.durationSeconds.coerceIn(3L, 600L)
            val speed = preset.scrollSpeed.coerceIn(0.5, 10.0)
            val lW = preset.logoWidth.coerceIn(40L, 800L)
            val lH = preset.logoHeight.coerceIn(40L, 800L)
            val fps = preset.targetFps.coerceIn(30L, 120L)
            val res = if (preset.resolution == "FHD_1080") "FHD_1080" else "2K_QHD"
            val chroma = preset.chromaHex.trim().take(12).ifBlank { "#0047BB" }

            require(cleanName.isNotEmpty() && cleanTitle1.isNotEmpty() && cleanTitle2.isNotEmpty() && cleanTime.length >= 4) {
                "Preset name, headlines, and start time are required"
            }

            val collectionRef = db.collection("users").document(uid).collection("bulletin_presets")
            val docRef = if (preset.id.isNotBlank()) {
                collectionRef.document(validateId(preset.id))
            } else {
                collectionRef.document()
            }
            path = docRef.path

            if (preset.id.isNotBlank()) {
                docRef.update(
                    mapOf(
                        "name" to cleanName,
                        "title1" to cleanTitle1,
                        "title2" to cleanTitle2,
                        "startTime" to cleanTime,
                        "ampm" to cleanAmPm,
                        "durationSeconds" to duration,
                        "scrollSpeed" to speed,
                        "logoWidth" to lW,
                        "logoHeight" to lH,
                        "targetFps" to fps,
                        "resolution" to res,
                        "chromaHex" to chroma,
                        "updatedAt" to FieldValue.serverTimestamp()
                    )
                ).await()
            } else {
                docRef.set(
                    mapOf(
                        "userId" to uid,
                        "name" to cleanName,
                        "title1" to cleanTitle1,
                        "title2" to cleanTitle2,
                        "startTime" to cleanTime,
                        "ampm" to cleanAmPm,
                        "durationSeconds" to duration,
                        "scrollSpeed" to speed,
                        "logoWidth" to lW,
                        "logoHeight" to lH,
                        "targetFps" to fps,
                        "resolution" to res,
                        "chromaHex" to chroma,
                        "createdAt" to FieldValue.serverTimestamp(),
                        "updatedAt" to FieldValue.serverTimestamp()
                    )
                ).await()
            }
            Result.success(Unit)
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            handleFirestoreError(e, OperationType.WRITE, path)
            Result.failure(e)
        }
    }

    suspend fun deleteBulletinPreset(presetId: String): Result<Unit> {
        var path = "users/${auth.currentUser?.uid ?: "unauthenticated"}/bulletin_presets/$presetId"
        return try {
            val uid = requireUserId()
            val safeId = validateId(presetId)
            val docRef = db.collection("users").document(uid).collection("bulletin_presets").document(safeId)
            path = docRef.path
            docRef.delete().await()
            Result.success(Unit)
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            handleFirestoreError(e, OperationType.DELETE, path)
            Result.failure(e)
        }
    }

    // =========================================================================
    // NAMEPLATE PRESETS
    // =========================================================================

    fun observeNameplatePresets(userId: String): Flow<List<NameplatePreset>> = flow {
        require(userId.isNotBlank()) { "User ID must not be blank" }
        val path = "users/$userId/nameplate_presets"
        emitAll(
            db.collection("users").document(userId).collection("nameplate_presets")
                .snapshots()
                .map { snap ->
                    snap.toObjects(NameplatePreset::class.java, DocumentSnapshot.ServerTimestampBehavior.ESTIMATE)
                        .sortedByDescending { it.updatedAt ?: it.createdAt }
                }
                .catch { error ->
                    logStreamError(error, OperationType.LIST, path, userId)
                    throw error
                }
        )
    }

    suspend fun saveNameplatePreset(nameText: String, unitText: String, accentColorHex: String): Result<Unit> {
        var path = "users/${auth.currentUser?.uid ?: "unauthenticated"}/nameplate_presets"
        return try {
            val uid = requireUserId()
            val cleanName = nameText.trim().take(200)
            val cleanUnit = unitText.trim().take(250)
            val cleanColor = accentColorHex.trim().take(12).ifBlank { "#FF2B4A" }
            require(cleanName.isNotEmpty() && cleanUnit.isNotEmpty()) {
                "Name and unit are required"
            }

            val docRef = db.collection("users").document(uid).collection("nameplate_presets").document()
            path = docRef.path
            docRef.set(
                mapOf(
                    "userId" to uid,
                    "nameText" to cleanName,
                    "unitText" to cleanUnit,
                    "accentColorHex" to cleanColor,
                    "createdAt" to FieldValue.serverTimestamp(),
                    "updatedAt" to FieldValue.serverTimestamp()
                )
            ).await()
            Result.success(Unit)
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            handleFirestoreError(e, OperationType.CREATE, path)
            Result.failure(e)
        }
    }

    suspend fun deleteNameplatePreset(presetId: String): Result<Unit> {
        var path = "users/${auth.currentUser?.uid ?: "unauthenticated"}/nameplate_presets/$presetId"
        return try {
            val uid = requireUserId()
            val safeId = validateId(presetId)
            val docRef = db.collection("users").document(uid).collection("nameplate_presets").document(safeId)
            path = docRef.path
            docRef.delete().await()
            Result.success(Unit)
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            handleFirestoreError(e, OperationType.DELETE, path)
            Result.failure(e)
        }
    }

    // =========================================================================
    // CREDIT PROJECTS
    // =========================================================================

    fun observeCreditProjects(userId: String): Flow<List<CreditProject>> = flow {
        require(userId.isNotBlank()) { "User ID must not be blank" }
        val path = "users/$userId/credit_projects"
        emitAll(
            db.collection("users").document(userId).collection("credit_projects")
                .snapshots()
                .map { snap ->
                    snap.toObjects(CreditProject::class.java, DocumentSnapshot.ServerTimestampBehavior.ESTIMATE)
                        .sortedByDescending { it.updatedAt ?: it.createdAt }
                }
                .catch { error ->
                    logStreamError(error, OperationType.LIST, path, userId)
                    throw error
                }
        )
    }

    suspend fun saveCreditProject(projectTitle: String, sections: List<CreditSection>): Result<Unit> {
        var path = "users/${auth.currentUser?.uid ?: "unauthenticated"}/credit_projects"
        return try {
            val uid = requireUserId()
            val cleanTitle = projectTitle.trim().take(120)
            require(cleanTitle.isNotEmpty()) { "Project title is required" }
            val boundedSections = sections.take(30).map { it.toMap() }
            require(boundedSections.isNotEmpty()) { "At least one credit section is required" }

            val docRef = db.collection("users").document(uid).collection("credit_projects").document()
            path = docRef.path
            docRef.set(
                mapOf(
                    "userId" to uid,
                    "projectTitle" to cleanTitle,
                    "sections" to boundedSections,
                    "createdAt" to FieldValue.serverTimestamp(),
                    "updatedAt" to FieldValue.serverTimestamp()
                )
            ).await()
            Result.success(Unit)
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            handleFirestoreError(e, OperationType.CREATE, path)
            Result.failure(e)
        }
    }

    suspend fun deleteCreditProject(projectId: String): Result<Unit> {
        var path = "users/${auth.currentUser?.uid ?: "unauthenticated"}/credit_projects/$projectId"
        return try {
            val uid = requireUserId()
            val safeId = validateId(projectId)
            val docRef = db.collection("users").document(uid).collection("credit_projects").document(safeId)
            path = docRef.path
            docRef.delete().await()
            Result.success(Unit)
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            handleFirestoreError(e, OperationType.DELETE, path)
            Result.failure(e)
        }
    }

    // =========================================================================
    // QUICK LINKS
    // =========================================================================

    fun observeQuickLinks(userId: String): Flow<List<QuickLink>> = flow {
        require(userId.isNotBlank()) { "User ID must not be blank" }
        val path = "users/$userId/quick_links"
        emitAll(
            db.collection("users").document(userId).collection("quick_links")
                .snapshots()
                .map { snap ->
                    snap.toObjects(QuickLink::class.java, DocumentSnapshot.ServerTimestampBehavior.ESTIMATE)
                }
                .catch { error ->
                    logStreamError(error, OperationType.LIST, path, userId)
                    throw error
                }
        )
    }

    suspend fun saveQuickLink(
        docId: String? = null,
        title: String,
        url: String,
        thumbnailUrl: String? = null
    ): Result<Unit> {
        var path = "users/${auth.currentUser?.uid ?: "unauthenticated"}/quick_links"
        return try {
            val uid = requireUserId()
            val cleanTitle = title.trim().take(120)
            val cleanUrl = url.trim().take(500)
            val cleanThumb = thumbnailUrl?.trim()?.take(500)?.takeIf { it.isNotEmpty() }
            require(cleanTitle.isNotEmpty() && cleanUrl.isNotEmpty()) {
                "Link title and URL are required"
            }

            val col = db.collection("users").document(uid).collection("quick_links")
            val docRef = if (!docId.isNullOrBlank()) col.document(validateId(docId)) else col.document()
            path = docRef.path

            val exists = if (!docId.isNullOrBlank()) docRef.get().await().exists() else false
            if (exists) {
                val updateMap = mutableMapOf<String, Any>(
                    "title" to cleanTitle,
                    "url" to cleanUrl,
                    "updatedAt" to FieldValue.serverTimestamp()
                )
                if (cleanThumb != null) updateMap["thumbnailUrl"] = cleanThumb
                docRef.update(updateMap).await()
            } else {
                val payload = mutableMapOf<String, Any>(
                    "userId" to uid,
                    "title" to cleanTitle,
                    "url" to cleanUrl,
                    "createdAt" to FieldValue.serverTimestamp(),
                    "updatedAt" to FieldValue.serverTimestamp()
                )
                if (cleanThumb != null) payload["thumbnailUrl"] = cleanThumb
                docRef.set(payload).await()
            }
            Result.success(Unit)
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            handleFirestoreError(e, OperationType.WRITE, path)
            Result.failure(e)
        }
    }

    suspend fun deleteQuickLink(linkId: String): Result<Unit> {
        var path = "users/${auth.currentUser?.uid ?: "unauthenticated"}/quick_links/$linkId"
        return try {
            val uid = requireUserId()
            val safeId = validateId(linkId)
            val docRef = db.collection("users").document(uid).collection("quick_links").document(safeId)
            path = docRef.path
            docRef.delete().await()
            Result.success(Unit)
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            handleFirestoreError(e, OperationType.DELETE, path)
            Result.failure(e)
        }
    }

    private fun logStreamError(
        error: Throwable,
        operationType: OperationType,
        path: String,
        userId: String
    ) {
        val firestoreError = generateSequence(error) { it.cause }
            .filterIsInstance<FirebaseFirestoreException>()
            .firstOrNull()
        if (firestoreError == null && error is CancellationException) throw error
        val ex = firestoreError ?: (error as? Exception)
        if (ex != null && auth.currentUser?.uid == userId) {
            handleFirestoreError(ex, operationType, path)
        }
    }
}
