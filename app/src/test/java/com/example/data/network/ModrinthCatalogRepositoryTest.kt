package com.example.data.network

import android.content.Context
import android.content.Intent
import androidx.test.core.app.ApplicationProvider
import com.example.data.model.PackType
import kotlinx.coroutines.runBlocking
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.ResponseBody
import okhttp3.ResponseBody.Companion.toResponseBody
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Assert.fail
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import java.security.MessageDigest

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [36])
class ModrinthCatalogRepositoryTest {

    private class FakeModrinthApiService : ModrinthApiService {
        var searchCallCount = 0
        var versionsCallCount = 0
        var lastQuery: String? = null
        var lastFacets: String? = null
        var lastIndex: String? = null

        var searchHitsToReturn: List<ModrinthProjectHit> = emptyList()
        var versionsToReturn: List<ModrinthVersion> = emptyList()
        var payloadBytes: ByteArray = "mock-mcaddon-zip-content".toByteArray()

        override suspend fun searchProjects(
            query: String,
            facets: String?,
            index: String,
            offset: Int,
            limit: Int
        ): ModrinthSearchResponse {
            searchCallCount++
            lastQuery = query
            lastFacets = facets
            lastIndex = index
            return ModrinthSearchResponse(
                hits = searchHitsToReturn,
                offset = offset,
                limit = limit,
                totalHits = searchHitsToReturn.size
            )
        }

        override suspend fun getProjectDetails(projectIdOrSlug: String): ModrinthProjectDetails {
            return ModrinthProjectDetails(id = projectIdOrSlug, title = projectIdOrSlug)
        }

        override suspend fun getProjectVersions(
            projectIdOrSlug: String,
            loadersJson: String?,
            gameVersionsJson: String?,
            featured: Boolean?,
            includeChangelog: Boolean
        ): List<ModrinthVersion> {
            versionsCallCount++
            return versionsToReturn
        }

        override suspend fun getCategories(): List<ModrinthTagItem> = emptyList()

        override suspend fun getLoaders(): List<ModrinthTagItem> = emptyList()

        override suspend fun downloadPackFile(fileUrl: String): ResponseBody {
            return payloadBytes.toResponseBody("application/octet-stream".toMediaType())
        }
    }

    @Test
    fun searchCatalog_usesFiveMinuteTtlCacheAndSupportsForceRefresh() = runBlocking {
        var nowMs = 1_000_000L
        val fakeApi = FakeModrinthApiService().apply {
            searchHitsToReturn = listOf(
                ModrinthProjectHit(
                    projectId = "p1",
                    slug = "faithful-bedrock",
                    title = "Faithful Texture Pack",
                    description = "Works great on Java & Bedrock",
                    downloads = 5000
                ),
                ModrinthProjectHit(
                    projectId = "p2",
                    slug = "vanilla-tweaks",
                    title = "Vanilla Resource Pack",
                    description = "Clean textures",
                    downloads = 9000
                )
            )
        }
        val repo = ModrinthCatalogRepository(
            api = fakeApi,
            cacheTtlMs = 5 * 60 * 1000L,
            timeProvider = { nowMs }
        )

        val first = repo.searchCatalog(
            query = "faithful",
            packType = PackType.RESOURCE_PACK,
            loader = "minecraft",
            bedrockOnly = true,
            sortOption = ModrinthSortOption.DOWNLOADS
        )
        assertFalse(first.fromCache)
        assertEquals(1, fakeApi.searchCallCount)
        assertEquals("faithful bedrock", fakeApi.lastQuery)
        assertEquals("downloads", fakeApi.lastIndex)
        assertEquals("p1", first.hits.first().projectId)

        // Within 5-minute TTL -> served from cache
        nowMs += 4 * 60 * 1000L
        val second = repo.searchCatalog(
            query = "  Faithful ",
            packType = PackType.RESOURCE_PACK,
            loader = "minecraft",
            bedrockOnly = true,
            sortOption = ModrinthSortOption.DOWNLOADS
        )
        assertTrue(second.fromCache)
        assertEquals(1, fakeApi.searchCallCount)

        // Past 5-minute TTL -> hits network again
        nowMs += 2 * 60 * 1000L
        val third = repo.searchCatalog(
            query = "faithful",
            packType = PackType.RESOURCE_PACK,
            loader = "minecraft",
            bedrockOnly = true,
            sortOption = ModrinthSortOption.UPDATED
        )
        assertFalse(third.fromCache)
        assertEquals(2, fakeApi.searchCallCount)
        assertEquals("updated", fakeApi.lastIndex)

        // Force refresh -> bypasses fresh cache
        val fourth = repo.searchCatalog(
            query = "faithful",
            packType = PackType.RESOURCE_PACK,
            loader = "minecraft",
            bedrockOnly = true,
            sortOption = ModrinthSortOption.UPDATED,
            forceRefresh = true
        )
        assertFalse(fourth.fromCache)
        assertEquals(3, fakeApi.searchCallCount)
    }

    @Test
    fun buildQueryAndFacets_togglesBedrockAndAllPacksProperly() = runBlocking {
        val fakeApi = FakeModrinthApiService()
        val repo = ModrinthCatalogRepository(api = fakeApi)

        repo.searchCatalog(
            query = "",
            packType = PackType.MOD,
            loader = "fabric",
            bedrockOnly = false,
            sortOption = ModrinthSortOption.RELEVANCE
        )
        assertEquals("", fakeApi.lastQuery)
        assertEquals("relevance", fakeApi.lastIndex)
        assertEquals("""[["project_type:mod"],["categories:fabric"]]""", fakeApi.lastFacets)

        repo.searchCatalog(
            query = "",
            packType = PackType.MOD,
            loader = "fabric",
            bedrockOnly = true,
            sortOption = ModrinthSortOption.DOWNLOADS
        )
        assertEquals("bedrock", fakeApi.lastQuery)
        assertEquals("""[["project_type:datapack","project_type:resourcepack"]]""", fakeApi.lastFacets)
    }

    @Test
    fun resolvePreferredMobilePackAsset_blocksExecutablesAndPrefersMcaddon() = runBlocking {
        val fakeApi = FakeModrinthApiService().apply {
            versionsToReturn = listOf(
                ModrinthVersion(
                    id = "v1",
                    projectId = "proj-1",
                    versionNumber = "1.2.0",
                    files = listOf(
                        ModrinthFile(
                            url = "https://cdn.modrinth.com/data/proj-1/mod.jar",
                            filename = "mod.jar",
                            primary = true,
                            size = 100L
                        ),
                        ModrinthFile(
                            url = "https://cdn.modrinth.com/data/proj-1/pack.mcpack",
                            filename = "pack.mcpack",
                            primary = false,
                            size = 200L
                        ),
                        ModrinthFile(
                            url = "https://cdn.modrinth.com/data/proj-1/addon.mcaddon",
                            filename = "addon.mcaddon",
                            primary = false,
                            size = 300L
                        )
                    )
                )
            )
        }
        val repo = ModrinthCatalogRepository(api = fakeApi)

        val resolved = repo.resolvePreferredMobilePackAsset("proj-1")
        assertEquals("addon.mcaddon", resolved.file.filename)
        assertTrue(resolved.isBedrockNativeArchive)

        // Verify cached version lookup
        repo.resolvePreferredMobilePackAsset("proj-1")
        assertEquals(1, fakeApi.versionsCallCount)

        // Reject project containing only .jar files
        fakeApi.versionsToReturn = listOf(
            ModrinthVersion(
                id = "v2",
                projectId = "proj-jar-only",
                files = listOf(
                    ModrinthFile(
                        url = "https://cdn.modrinth.com/data/proj-jar-only/forge.jar",
                        filename = "forge.jar",
                        primary = true,
                        size = 400L
                    )
                )
            )
        )
        try {
            repo.resolvePreferredMobilePackAsset("proj-jar-only", forceRefresh = true)
            fail("Expected IllegalStateException when only .jar files exist")
        } catch (expected: IllegalStateException) {
            assertTrue(expected.message.orEmpty().contains("Minecraft Mobile archive"))
        }
    }

    @Test
    @Config(sdk = [28])
    fun downloadMobilePackLocally_verifiesSha512AndWritesPackFile() = runBlocking {
        val context = ApplicationProvider.getApplicationContext<Context>()
        val payload = "PK\u0003\u0004bedrock-manifest-json-content".toByteArray()
        val expected512 = MessageDigest.getInstance("SHA-512").digest(payload)
            .joinToString("") { "%02x".format(it) }
        val expected256 = MessageDigest.getInstance("SHA-256").digest(payload)
            .joinToString("") { "%02x".format(it) }

        val fakeApi = FakeModrinthApiService().apply {
            payloadBytes = payload
            versionsToReturn = listOf(
                ModrinthVersion(
                    id = "v10",
                    projectId = "bedrock-pack",
                    files = listOf(
                        ModrinthFile(
                            url = "https://cdn.modrinth.com/data/bedrock-pack/mobile.mcaddon",
                            filename = "mobile.mcaddon",
                            primary = true,
                            size = payload.size.toLong(),
                            hashes = mapOf("sha512" to expected512)
                        )
                    )
                )
            )
        }
        val repo = ModrinthCatalogRepository(api = fakeApi)
        val project = ModrinthProjectHit(
            projectId = "bedrock-pack",
            title = "Bedrock Survival Addon"
        )

        val statuses = mutableListOf<ModrinthDownloadStatus>()
        val completed = repo.downloadMobilePackLocally(context, project) { statuses += it }

        assertEquals("mobile.mcaddon", completed.fileName)
        assertEquals(payload.size.toLong(), completed.fileSizeBytes)
        assertEquals(expected256, completed.sha256)
        assertTrue(completed.verifiedByModrinthHash)
        assertTrue(statuses.any { it is ModrinthDownloadStatus.Resolving })
        assertTrue(statuses.any { it is ModrinthDownloadStatus.Downloading })
        assertTrue(statuses.last() is ModrinthDownloadStatus.Completed)

        val openIntent = ModrinthCatalogRepository.buildOpenInMinecraftIntent(
            android.net.Uri.parse(completed.contentUriString),
            completed.fileName
        )
        assertEquals(Intent.ACTION_VIEW, openIntent.action)
        assertTrue((openIntent.flags and Intent.FLAG_GRANT_READ_URI_PERMISSION) != 0)
    }

    @Test
    @Config(sdk = [28])
    fun downloadMobilePackLocally_failsOnCorruptedSha512Hash() = runBlocking {
        val context = ApplicationProvider.getApplicationContext<Context>()
        val fakeApi = FakeModrinthApiService().apply {
            payloadBytes = "corrupted-bytes".toByteArray()
            versionsToReturn = listOf(
                ModrinthVersion(
                    id = "v-bad",
                    projectId = "bad-hash",
                    files = listOf(
                        ModrinthFile(
                            url = "https://cdn.modrinth.com/data/bad-hash/broken.mcpack",
                            filename = "broken.mcpack",
                            primary = true,
                            size = 15L,
                            hashes = mapOf("sha512" to "0".repeat(128))
                        )
                    )
                )
            )
        }
        val repo = ModrinthCatalogRepository(api = fakeApi)
        val project = ModrinthProjectHit(projectId = "bad-hash", title = "Broken Pack")

        try {
            repo.downloadMobilePackLocally(context, project) {}
            fail("Expected SHA-512 verification check failure")
        } catch (expected: IllegalStateException) {
            assertTrue(expected.message.orEmpty().contains("SHA-512 verification failed"))
        }
    }
}
