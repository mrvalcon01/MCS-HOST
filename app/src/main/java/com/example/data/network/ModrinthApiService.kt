package com.example.data.network

import com.squareup.moshi.Json
import com.squareup.moshi.JsonClass
import com.squareup.moshi.Moshi
import com.squareup.moshi.kotlin.reflect.KotlinJsonAdapterFactory
import okhttp3.Cache
import okhttp3.OkHttpClient
import okhttp3.ResponseBody
import retrofit2.Retrofit
import retrofit2.converter.moshi.MoshiConverterFactory
import retrofit2.http.GET
import retrofit2.http.Path
import retrofit2.http.Query
import retrofit2.http.Streaming
import retrofit2.http.Url
import java.io.File
import java.util.concurrent.TimeUnit

@JsonClass(generateAdapter = true)
data class ModrinthSearchResponse(
    val hits: List<ModrinthProjectHit> = emptyList(),
    val offset: Int = 0,
    val limit: Int = 20,
    @param:Json(name = "total_hits") val totalHits: Int = 0
)

@JsonClass(generateAdapter = true)
data class ModrinthProjectHit(
    @param:Json(name = "project_id") val projectId: String = "",
    val slug: String = "",
    val title: String = "",
    val description: String = "",
    val author: String = "",
    @param:Json(name = "project_type") val projectType: String = "resourcepack",
    @param:Json(name = "icon_url") val iconUrl: String? = null,
    val downloads: Long = 0L,
    val follows: Long = 0L,
    val categories: List<String> = emptyList(),
    @param:Json(name = "display_categories") val displayCategories: List<String> = emptyList(),
    val versions: List<String> = emptyList(),
    val license: String? = null,
    @param:Json(name = "client_side") val clientSide: String? = null,
    @param:Json(name = "server_side") val serverSide: String? = null,
    @param:Json(name = "date_created") val dateCreated: String? = null,
    @param:Json(name = "date_modified") val dateModified: String? = null,
    @param:Json(name = "latest_version") val latestVersion: String? = null
) {
    fun mentionsBedrockOrMobile(): Boolean {
        val text = "$title $description $slug ${categories.joinToString(" ")}".lowercase()
        return text.contains("bedrock") ||
            text.contains("mcpack") ||
            text.contains("mcaddon") ||
            text.contains("mcpe") ||
            text.contains("pocket")
    }
}

@JsonClass(generateAdapter = true)
data class ModrinthProjectDetails(
    val id: String = "",
    val slug: String = "",
    val title: String = "",
    val description: String = "",
    val body: String = "",
    val categories: List<String> = emptyList(),
    @param:Json(name = "additional_categories") val additionalCategories: List<String> = emptyList(),
    @param:Json(name = "client_side") val clientSide: String? = null,
    @param:Json(name = "server_side") val serverSide: String? = null,
    val downloads: Long = 0L,
    val followers: Long = 0L,
    @param:Json(name = "icon_url") val iconUrl: String? = null,
    val versions: List<String> = emptyList(),
    @param:Json(name = "game_versions") val gameVersions: List<String> = emptyList(),
    val loaders: List<String> = emptyList(),
    val license: ModrinthLicense? = null
)

@JsonClass(generateAdapter = true)
data class ModrinthLicense(
    val id: String = "",
    val name: String = "",
    val url: String? = null
)

@JsonClass(generateAdapter = true)
data class ModrinthVersion(
    val id: String = "",
    @param:Json(name = "project_id") val projectId: String = "",
    val name: String = "",
    @param:Json(name = "version_number") val versionNumber: String = "",
    @param:Json(name = "version_type") val versionType: String = "release",
    @param:Json(name = "game_versions") val gameVersions: List<String> = emptyList(),
    val loaders: List<String> = emptyList(),
    val featured: Boolean = false,
    val downloads: Long = 0L,
    @param:Json(name = "date_published") val datePublished: String? = null,
    val files: List<ModrinthFile> = emptyList()
)

@JsonClass(generateAdapter = true)
data class ModrinthFile(
    val url: String = "",
    val filename: String = "",
    val primary: Boolean = false,
    val size: Long = 0L,
    @param:Json(name = "file_type") val fileType: String? = null,
    val hashes: Map<String, String> = emptyMap()
)

@JsonClass(generateAdapter = true)
data class ModrinthTagItem(
    val name: String = "",
    val icon: String? = null,
    @param:Json(name = "project_type") val projectType: String? = null,
    @param:Json(name = "supported_project_types") val supportedProjectTypes: List<String> = emptyList()
)

interface ModrinthApiService {
    @GET("search")
    suspend fun searchProjects(
        @Query("query") query: String = "",
        @Query("facets") facets: String? = null,
        @Query("index") index: String = "downloads",
        @Query("offset") offset: Int = 0,
        @Query("limit") limit: Int = 20
    ): ModrinthSearchResponse

    @GET("project/{id}")
    suspend fun getProjectDetails(
        @Path("id") projectIdOrSlug: String
    ): ModrinthProjectDetails

    @GET("project/{id}/version")
    suspend fun getProjectVersions(
        @Path("id") projectIdOrSlug: String,
        @Query("loaders") loadersJson: String? = null,
        @Query("game_versions") gameVersionsJson: String? = null,
        @Query("featured") featured: Boolean? = null,
        @Query("include_changelog") includeChangelog: Boolean = false
    ): List<ModrinthVersion>

    @GET("tag/category")
    suspend fun getCategories(): List<ModrinthTagItem>

    @GET("tag/loader")
    suspend fun getLoaders(): List<ModrinthTagItem>

    @Streaming
    @GET
    suspend fun downloadPackFile(
        @Url fileUrl: String
    ): ResponseBody

    companion object {
        private const val BASE_URL = "https://api.modrinth.com/v2/"
        private const val CACHE_SIZE_BYTES = 10L * 1024L * 1024L
        private const val USER_AGENT = "MCS-HOST/1.0 (Android Minecraft Mobile Manager)"

        fun create(
            cacheDir: File? = null,
            baseUrl: String = BASE_URL
        ): ModrinthApiService {
            val clientBuilder = OkHttpClient.Builder()
                .connectTimeout(15, TimeUnit.SECONDS)
                .readTimeout(30, TimeUnit.SECONDS)
                .addInterceptor { chain ->
                    val request = chain.request().newBuilder()
                        .header("User-Agent", USER_AGENT)
                        .build()
                    chain.proceed(request)
                }

            if (cacheDir != null) {
                val httpCacheDir = File(cacheDir, "modrinth_http_cache")
                clientBuilder.cache(Cache(httpCacheDir, CACHE_SIZE_BYTES))
            }

            val moshi = Moshi.Builder()
                .add(KotlinJsonAdapterFactory())
                .build()

            return Retrofit.Builder()
                .baseUrl(baseUrl)
                .client(clientBuilder.build())
                .addConverterFactory(MoshiConverterFactory.create(moshi))
                .build()
                .create(ModrinthApiService::class.java)
        }
    }
}
