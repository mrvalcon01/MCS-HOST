package com.example.data.network

import android.content.ContentValues
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Build
import android.os.Environment
import android.provider.MediaStore
import androidx.core.content.FileProvider
import com.example.data.model.PackType
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import java.io.File
import java.io.FileOutputStream
import java.io.OutputStream
import java.security.MessageDigest

enum class ModrinthSortOption(val label: String, val apiIndex: String) {
    DOWNLOADS("Downloads", "downloads"),
    RELEVANCE("Relevance", "relevance"),
    UPDATED("Updated", "updated")
}

sealed interface ModrinthDownloadStatus {
    data object Idle : ModrinthDownloadStatus
    data class Resolving(val projectTitle: String) : ModrinthDownloadStatus
    data class Downloading(
        val fileName: String,
        val bytesRead: Long,
        val totalBytes: Long
    ) : ModrinthDownloadStatus
    data class Completed(
        val fileName: String,
        val contentUriString: String,
        val fileSizeBytes: Long,
        val sha256: String,
        val verifiedByModrinthHash: Boolean
    ) : ModrinthDownloadStatus
    data class Failed(val message: String) : ModrinthDownloadStatus
}

data class ModrinthCatalogSearchResult(
    val hits: List<ModrinthProjectHit>,
    val fromCache: Boolean,
    val cachedAtMs: Long
)

data class ModrinthResolvedAsset(
    val version: ModrinthVersion,
    val file: ModrinthFile,
    val isBedrockNativeArchive: Boolean
)

class ModrinthCatalogRepository(
    private val api: ModrinthApiService,
    private val cacheTtlMs: Long = DEFAULT_CACHE_TTL_MS,
    private val timeProvider: () -> Long = { System.currentTimeMillis() }
) {
    private data class SearchCacheKey(
        val query: String,
        val packType: PackType,
        val loader: String,
        val bedrockOnly: Boolean,
        val sortOption: ModrinthSortOption
    )

    private data class CachedSearchEntry(
        val hits: List<ModrinthProjectHit>,
        val timestampMs: Long
    )

    private data class CachedVersionEntry(
        val versions: List<ModrinthVersion>,
        val timestampMs: Long
    )

    private val mutex = Mutex()
    private val searchCache = LinkedHashMap<SearchCacheKey, CachedSearchEntry>(MAX_CACHE_ENTRIES, 0.75f, true)
    private val versionsCache = LinkedHashMap<String, CachedVersionEntry>(MAX_CACHE_ENTRIES, 0.75f, true)

    suspend fun searchCatalog(
        query: String,
        packType: PackType,
        loader: String,
        bedrockOnly: Boolean,
        sortOption: ModrinthSortOption,
        forceRefresh: Boolean = false
    ): ModrinthCatalogSearchResult {
        val normalizedQuery = query.trim()
        val key = SearchCacheKey(
            query = normalizedQuery.lowercase(),
            packType = packType,
            loader = loader.trim().lowercase(),
            bedrockOnly = bedrockOnly,
            sortOption = sortOption
        )

        val now = timeProvider()
        if (!forceRefresh) {
            mutex.withLock {
                val existing = searchCache[key]
                if (existing != null && (now - existing.timestampMs) < cacheTtlMs) {
                    return ModrinthCatalogSearchResult(
                        hits = existing.hits,
                        fromCache = true,
                        cachedAtMs = existing.timestampMs
                    )
                }
            }
        }

        val effectiveQuery = buildEffectiveQuery(normalizedQuery, bedrockOnly)
        val facetsJson = buildFacetsJson(packType, loader, bedrockOnly)
        val response = withContext(Dispatchers.IO) {
            api.searchProjects(
                query = effectiveQuery,
                facets = facetsJson,
                index = sortOption.apiIndex,
                offset = 0,
                limit = 25
            )
        }

        val orderedHits = if (bedrockOnly) {
            // Keep Modrinth relevance/downloads order while gently boosting explicit Bedrock/mcpack/mcaddon matches
            val (bedrockMatches, others) = response.hits.partition { it.mentionsBedrockOrMobile() }
            bedrockMatches + others
        } else {
            response.hits
        }

        mutex.withLock {
            if (searchCache.size >= MAX_CACHE_ENTRIES) {
                val eldest = searchCache.keys.firstOrNull()
                if (eldest != null) searchCache.remove(eldest)
            }
            searchCache[key] = CachedSearchEntry(orderedHits, timeProvider())
        }

        return ModrinthCatalogSearchResult(
            hits = orderedHits,
            fromCache = false,
            cachedAtMs = timeProvider()
        )
    }

    suspend fun getProjectVersionsCached(
        projectId: String,
        forceRefresh: Boolean = false
    ): List<ModrinthVersion> {
        val now = timeProvider()
        if (!forceRefresh) {
            mutex.withLock {
                val cached = versionsCache[projectId]
                if (cached != null && (now - cached.timestampMs) < cacheTtlMs) {
                    return cached.versions
                }
            }
        }
        val versions = withContext(Dispatchers.IO) {
            api.getProjectVersions(projectIdOrSlug = projectId, includeChangelog = false)
        }
        mutex.withLock {
            if (versionsCache.size >= MAX_CACHE_ENTRIES) {
                val eldest = versionsCache.keys.firstOrNull()
                if (eldest != null) versionsCache.remove(eldest)
            }
            versionsCache[projectId] = CachedVersionEntry(versions, timeProvider())
        }
        return versions
    }

    suspend fun resolvePreferredMobilePackAsset(
        projectId: String,
        forceRefresh: Boolean = false
    ): ModrinthResolvedAsset {
        val versions = getProjectVersionsCached(projectId, forceRefresh = forceRefresh)
        if (versions.isEmpty()) {
            throw IllegalStateException("No downloadable release versions found on Modrinth for this project.")
        }

        // Flatten candidate files across versions, rejecting Java .jar / executable binaries for mobile Minecraft
        val scoredCandidates = versions.flatMapIndexed { verIndex, version ->
            version.files.mapNotNull { file ->
                val ext = file.filename.substringAfterLast('.', "").lowercase()
                if (ext in BLOCKED_EXECUTABLE_EXTENSIONS) {
                    null
                } else {
                    val priority = when (ext) {
                        "mcaddon" -> 0
                        "mcpack" -> 1
                        "mcworld" -> 2
                        "zip" -> if (file.primary) 3 else 4
                        else -> if (ext in ALLOWED_ARCHIVE_EXTENSIONS) 5 else 99
                    }
                    if (priority >= 99) null else Triple(priority * 100 + verIndex, version, file)
                }
            }
        }.sortedBy { it.first }

        val best = scoredCandidates.firstOrNull()
            ?: throw IllegalStateException("No Minecraft Mobile archive (.mcpack, .mcaddon, or .zip) is available for this project.")

        val ext = best.third.filename.substringAfterLast('.', "").lowercase()
        return ModrinthResolvedAsset(
            version = best.second,
            file = best.third,
            isBedrockNativeArchive = ext == "mcpack" || ext == "mcaddon" || ext == "mcworld"
        )
    }

    suspend fun downloadMobilePackLocally(
        context: Context,
        project: ModrinthProjectHit,
        onProgress: (ModrinthDownloadStatus) -> Unit
    ): ModrinthDownloadStatus.Completed = withContext(Dispatchers.IO) {
        onProgress(ModrinthDownloadStatus.Resolving(project.title))
        val resolved = resolvePreferredMobilePackAsset(project.projectId)
        val fileMeta = resolved.file
        val safeFileName = sanitizeFileName(fileMeta.filename)
        val expectedSha512 = fileMeta.hashes["sha512"]?.lowercase()
        val expectedSha1 = fileMeta.hashes["sha1"]?.lowercase()

        val tempDir = File(context.cacheDir, "packs").apply { mkdirs() }
        val tempFile = File(tempDir, "dl_${System.currentTimeMillis()}_$safeFileName")

        var verified = false
        val sha256Hex: String
        var totalBytesWritten = 0L

        try {
            val responseBody = api.downloadPackFile(fileMeta.url)
            val contentLength = responseBody.contentLength().takeIf { it > 0L } ?: fileMeta.size

            val sha256 = MessageDigest.getInstance("SHA-256")
            val sha512 = if (expectedSha512 != null) MessageDigest.getInstance("SHA-512") else null
            val sha1 = if (expectedSha1 != null) MessageDigest.getInstance("SHA-1") else null

            responseBody.byteStream().use { input ->
                FileOutputStream(tempFile).use { output ->
                    val buffer = ByteArray(8192)
                    var read: Int
                    var lastEmitBytes = 0L
                    onProgress(ModrinthDownloadStatus.Downloading(safeFileName, 0L, contentLength))

                    while (input.read(buffer).also { read = it } != -1) {
                        output.write(buffer, 0, read)
                        sha256.update(buffer, 0, read)
                        sha512?.update(buffer, 0, read)
                        sha1?.update(buffer, 0, read)
                        totalBytesWritten += read

                        if (totalBytesWritten - lastEmitBytes >= 32 * 1024 || totalBytesWritten == contentLength) {
                            lastEmitBytes = totalBytesWritten
                            onProgress(ModrinthDownloadStatus.Downloading(safeFileName, totalBytesWritten, contentLength))
                        }
                    }
                    output.flush()
                }
            }

            if (expectedSha512 != null && sha512 != null) {
                val actual512 = sha512.digest().toHex()
                check(actual512 == expectedSha512) {
                    "Modrinth SHA-512 verification failed for $safeFileName"
                }
                verified = true
            } else if (expectedSha1 != null && sha1 != null) {
                val actual1 = sha1.digest().toHex()
                check(actual1 == expectedSha1) {
                    "Modrinth SHA-1 verification failed for $safeFileName"
                }
                verified = true
            }

            sha256Hex = sha256.digest().toHex()
            val savedUri = saveFileToDeviceDownloads(context, tempFile, safeFileName)
            val completed = ModrinthDownloadStatus.Completed(
                fileName = safeFileName,
                contentUriString = savedUri.toString(),
                fileSizeBytes = totalBytesWritten,
                sha256 = sha256Hex,
                verifiedByModrinthHash = verified
            )
            onProgress(completed)
            completed
        } finally {
            if (tempFile.exists()) {
                tempFile.delete()
            }
        }
    }

    private fun saveFileToDeviceDownloads(
        context: Context,
        sourceFile: File,
        fileName: String
    ): Uri {
        val mimeType = mimeTypeForPack(fileName)
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            val resolver = context.contentResolver
            val values = ContentValues().apply {
                put(MediaStore.Downloads.DISPLAY_NAME, fileName)
                put(MediaStore.Downloads.MIME_TYPE, mimeType)
                put(MediaStore.Downloads.RELATIVE_PATH, "${Environment.DIRECTORY_DOWNLOADS}/MCS-HOST")
                put(MediaStore.Downloads.IS_PENDING, 1)
            }
            val uri = resolver.insert(MediaStore.Downloads.EXTERNAL_CONTENT_URI, values)
                ?: throw IllegalStateException("Could not create Downloads entry for $fileName")
            try {
                val stream: OutputStream = resolver.openOutputStream(uri)
                    ?: throw IllegalStateException("Unable to open Downloads output stream")
                stream.use { out ->
                    sourceFile.inputStream().use { input -> input.copyTo(out) }
                }
                val finishValues = ContentValues().apply {
                    put(MediaStore.Downloads.IS_PENDING, 0)
                }
                resolver.update(uri, finishValues, null, null)
                return uri
            } catch (e: Exception) {
                resolver.delete(uri, null, null)
                throw e
            }
        } else {
            val targetDir = context.getExternalFilesDir(Environment.DIRECTORY_DOWNLOADS)
                ?: File(context.filesDir, "Download")
            if (!targetDir.exists()) targetDir.mkdirs()
            val destination = File(targetDir, fileName)
            sourceFile.inputStream().use { input ->
                FileOutputStream(destination).use { out -> input.copyTo(out) }
            }
            return FileProvider.getUriForFile(
                context,
                "${context.packageName}.fileprovider",
                destination
            )
        }
    }

    companion object {
        const val DEFAULT_CACHE_TTL_MS = 5 * 60 * 1000L
        private const val MAX_CACHE_ENTRIES = 40
        private val ALLOWED_ARCHIVE_EXTENSIONS = setOf("mcpack", "mcaddon", "mcworld", "zip")
        private val BLOCKED_EXECUTABLE_EXTENSIONS = setOf("jar", "dex", "so", "exe", "msi", "apk", "sh")

        fun buildEffectiveQuery(rawQuery: String, bedrockOnly: Boolean): String {
            if (!bedrockOnly) return rawQuery
            return when {
                rawQuery.isBlank() -> "bedrock"
                rawQuery.contains("bedrock", ignoreCase = true) ||
                    rawQuery.contains("mcpack", ignoreCase = true) ||
                    rawQuery.contains("mcaddon", ignoreCase = true) -> rawQuery
                else -> "$rawQuery bedrock"
            }
        }

        fun buildFacetsJson(packType: PackType, loader: String, bedrockOnly: Boolean): String {
            val facets = mutableListOf<List<String>>()
            if (bedrockOnly && (packType == PackType.MOD || packType == PackType.PLUGIN)) {
                facets.add(listOf("project_type:datapack", "project_type:resourcepack"))
            } else {
                facets.add(listOf("project_type:${packType.modrinthProjectType}"))
                if ((packType == PackType.MOD || packType == PackType.PLUGIN) &&
                    loader.isNotBlank() &&
                    loader != "minecraft"
                ) {
                    facets.add(listOf("categories:$loader"))
                }
            }
            return facets.joinToString(separator = ",", prefix = "[", postfix = "]") { group ->
                group.joinToString(separator = ",", prefix = "[", postfix = "]") { "\"$it\"" }
            }
        }

        fun buildOpenInMinecraftIntent(contentUri: Uri, fileName: String): Intent {
            val mimeType = mimeTypeForPack(fileName)
            return Intent(Intent.ACTION_VIEW).apply {
                setDataAndType(contentUri, mimeType)
                addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
                addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            }
        }

        fun mimeTypeForPack(fileName: String): String {
            return when (fileName.substringAfterLast('.', "").lowercase()) {
                "zip" -> "application/zip"
                else -> "application/octet-stream"
            }
        }

        private fun sanitizeFileName(raw: String): String {
            val cleaned = raw.trim().replace(Regex("[^a-zA-Z0-9._\\-]"), "_")
            return cleaned.ifBlank { "minecraft_bedrock_pack.mcpack" }
        }

        private fun ByteArray.toHex(): String = joinToString("") { "%02x".format(it) }
    }
}
