package com.example.ui.screens

import android.net.Uri
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.DeleteOutline
import androidx.compose.material.icons.filled.Download
import androidx.compose.material.icons.filled.Extension
import androidx.compose.material.icons.filled.FileOpen
import androidx.compose.material.icons.automirrored.filled.OpenInNew
import androidx.compose.material.icons.filled.PhoneAndroid
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Search
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FilterChipDefaults
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import coil.compose.AsyncImage
import com.example.data.model.PackSource
import com.example.data.model.PackType
import com.example.data.model.RoomPack
import com.example.data.model.ServerRoom
import com.example.data.model.StaffRole
import com.example.data.network.ModrinthDownloadStatus
import com.example.data.network.ModrinthProjectHit
import com.example.data.network.ModrinthSortOption
import com.example.ui.components.RoleBadge
import com.example.ui.theme.AmberContainer
import com.example.ui.theme.AmberGold
import com.example.ui.theme.BasaltElevated
import com.example.ui.theme.BasaltSurface
import com.example.ui.theme.DiamondContainer
import com.example.ui.theme.DiamondCyan
import com.example.ui.theme.EmeraldContainer
import com.example.ui.theme.EmeraldCore
import com.example.ui.theme.EtchedBorder
import com.example.ui.theme.ObsidianVoid
import com.example.ui.theme.RedstoneCrimson

@OptIn(ExperimentalLayoutApi::class)
@Composable
fun PacksAndModsScreen(
    activeRoom: ServerRoom?,
    currentRole: StaffRole,
    roomPacks: List<RoomPack>,
    modrinthResults: List<ModrinthProjectHit>,
    modrinthSearching: Boolean,
    bedrockOnly: Boolean,
    selectedSort: ModrinthSortOption,
    isResultFromCache: Boolean,
    downloadStatuses: Map<String, ModrinthDownloadStatus>,
    onSearchModrinth: (query: String, packType: PackType, loader: String, forceRefresh: Boolean) -> Unit,
    onToggleBedrockOnly: (enabled: Boolean, query: String, packType: PackType, loader: String) -> Unit,
    onChangeSort: (sort: ModrinthSortOption, query: String, packType: PackType, loader: String) -> Unit,
    onDownloadModrinth: (ModrinthProjectHit) -> Unit,
    onOpenInMinecraft: (ModrinthDownloadStatus.Completed) -> Unit,
    onImportLocalUri: (Uri, PackType) -> Unit,
    onToggleEnabled: (RoomPack) -> Unit,
    onToggleRequired: (RoomPack) -> Unit,
    onRemovePack: (RoomPack) -> Unit
) {
    var selectedType by remember { mutableStateOf(PackType.RESOURCE_PACK) }
    var selectedLoader by remember(activeRoom?.id) {
        mutableStateOf(activeRoom?.softwareEnum()?.modrinthLoader ?: "minecraft")
    }
    var searchQuery by remember { mutableStateOf("") }

    val localPackPicker = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.OpenDocument()
    ) { uri ->
        if (uri != null) {
            onImportLocalUri(uri, selectedType)
        }
    }

    LazyColumn(
        modifier = Modifier.fillMaxSize(),
        contentPadding = PaddingValues(16.dp),
        verticalArrangement = Arrangement.spacedBy(14.dp)
    ) {
        // Active Server Room Packs section (if user has a room selected)
        if (activeRoom != null) {
            item {
                Surface(
                    color = BasaltElevated,
                    shape = MaterialTheme.shapes.large,
                    border = BorderStroke(1.dp, EtchedBorder),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Column(
                        modifier = Modifier.padding(16.dp),
                        verticalArrangement = Arrangement.spacedBy(10.dp)
                    ) {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Column(modifier = Modifier.weight(1f)) {
                                Text("Server Room Packs (${roomPacks.size})", style = MaterialTheme.typography.titleLarge)
                                Text(
                                    text = "${activeRoom.name} · ${activeRoom.softwareEnum().label}",
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            }
                            RoleBadge(role = currentRole)
                        }
                        Text(
                            text = "Owner, Admin, and Moderator roles can import local .mcpack / .mcaddon / .zip packs into the room or download mobile packs below.",
                            style = MaterialTheme.typography.bodySmall,
                            color = EmeraldCore
                        )
                        OutlinedButton(
                            onClick = {
                                localPackPicker.launch(
                                    arrayOf(
                                        "application/zip",
                                        "application/octet-stream",
                                        "*/*"
                                    )
                                )
                            },
                            modifier = Modifier
                                .fillMaxWidth()
                                .height(48.dp)
                                .testTag("import_local_pack_button")
                        ) {
                            Icon(Icons.Filled.FileOpen, contentDescription = null, modifier = Modifier.size(18.dp))
                            Spacer(Modifier.width(8.dp))
                            Text("Import Local ${selectedType.label} (.mcpack / .mcaddon)")
                        }
                    }
                }
            }

            if (roomPacks.isNotEmpty()) {
                items(roomPacks, key = { it.id }) { pack ->
                    InstalledPackCard(
                        pack = pack,
                        onToggleEnabled = { onToggleEnabled(pack) },
                        onToggleRequired = { onToggleRequired(pack) },
                        onRemove = { onRemovePack(pack) }
                    )
                }
            }

            item { HorizontalDivider(color = EtchedBorder) }
        }

        // Modrinth Mobile Bedrock Catalog Header, Toggle & Sort Controls
        item {
            Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Column(modifier = Modifier.weight(1f)) {
                        Text(
                            text = "Modrinth Mobile Packs & Add-ons",
                            style = MaterialTheme.typography.titleLarge,
                            color = DiamondCyan
                        )
                        Text(
                            text = "Download verified .mcpack & .mcaddon files directly to your phone.",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                    IconButton(
                        onClick = { onSearchModrinth(searchQuery, selectedType, selectedLoader, true) },
                        modifier = Modifier.size(48.dp).testTag("modrinth_refresh_button")
                    ) {
                        Icon(
                            Icons.Filled.Refresh,
                            contentDescription = "Refresh Modrinth catalog",
                            tint = EmeraldCore
                        )
                    }
                }

                // Bedrock toggle + Cache badge + Sort chips
                FlowRow(
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    verticalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    FilterChip(
                        selected = bedrockOnly,
                        onClick = {
                            onToggleBedrockOnly(!bedrockOnly, searchQuery, selectedType, selectedLoader)
                        },
                        leadingIcon = {
                            Icon(Icons.Filled.PhoneAndroid, contentDescription = null, modifier = Modifier.size(16.dp))
                        },
                        label = { Text(if (bedrockOnly) "Bedrock / Mobile: ON" else "All Packs") },
                        colors = FilterChipDefaults.filterChipColors(
                            selectedContainerColor = EmeraldContainer,
                            selectedLabelColor = EmeraldCore,
                            selectedLeadingIconColor = EmeraldCore
                        ),
                        modifier = Modifier.testTag("bedrock_toggle_chip")
                    )

                    ModrinthSortOption.entries.forEach { sortOpt ->
                        FilterChip(
                            selected = selectedSort == sortOpt,
                            onClick = {
                                onChangeSort(sortOpt, searchQuery, selectedType, selectedLoader)
                            },
                            label = { Text("Sort: ${sortOpt.label}") },
                            modifier = Modifier.testTag("sort_chip_${sortOpt.apiIndex}")
                        )
                    }

                    if (isResultFromCache) {
                        Surface(
                            color = DiamondContainer,
                            shape = MaterialTheme.shapes.small,
                            modifier = Modifier.align(Alignment.CenterVertically)
                        ) {
                            Text(
                                text = "CACHED 5M",
                                style = MaterialTheme.typography.labelSmall,
                                color = DiamondCyan,
                                modifier = Modifier.padding(horizontal = 8.dp, vertical = 6.dp)
                            )
                        }
                    }
                }

                // Category chips
                FlowRow(
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    verticalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    val visibleTypes = if (bedrockOnly) {
                        listOf(PackType.RESOURCE_PACK, PackType.BEHAVIOR_PACK, PackType.DATAPACK)
                    } else {
                        PackType.entries
                    }
                    visibleTypes.forEach { type ->
                        val chipLabel = if (bedrockOnly && type == PackType.BEHAVIOR_PACK) {
                            "Bedrock Add-on"
                        } else {
                            type.label
                        }
                        FilterChip(
                            selected = selectedType == type,
                            onClick = {
                                selectedType = type
                                onSearchModrinth(searchQuery, type, selectedLoader, false)
                            },
                            label = { Text(chipLabel) },
                            modifier = Modifier.testTag("pack_type_chip_${type.name.lowercase()}")
                        )
                    }
                }

                if (!bedrockOnly && (selectedType == PackType.MOD || selectedType == PackType.PLUGIN)) {
                    val loaders = listOf("minecraft" to "Any Loader", "paper" to "Paper", "fabric" to "Fabric", "forge" to "Forge", "purpur" to "Purpur")
                    FlowRow(
                        horizontalArrangement = Arrangement.spacedBy(6.dp),
                        verticalArrangement = Arrangement.spacedBy(4.dp)
                    ) {
                        loaders.forEach { (loaderKey, loaderLabel) ->
                            FilterChip(
                                selected = selectedLoader == loaderKey,
                                onClick = {
                                    selectedLoader = loaderKey
                                    onSearchModrinth(searchQuery, selectedType, loaderKey, false)
                                },
                                label = { Text(loaderLabel) }
                            )
                        }
                    }
                }

                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    OutlinedTextField(
                        value = searchQuery,
                        onValueChange = { searchQuery = it },
                        placeholder = {
                            Text(
                                if (bedrockOnly) "Search Bedrock textures, shaders, add-ons..."
                                else "Search free ${selectedType.label.lowercase()}s..."
                            )
                        },
                        singleLine = true,
                        modifier = Modifier
                            .weight(1f)
                            .testTag("modrinth_search_input")
                    )
                    Button(
                        onClick = { onSearchModrinth(searchQuery, selectedType, selectedLoader, false) },
                        modifier = Modifier
                            .height(52.dp)
                            .testTag("modrinth_search_button")
                    ) {
                        Icon(Icons.Filled.Search, contentDescription = "Search Modrinth")
                    }
                }
            }
        }

        if (modrinthSearching) {
            item {
                Row(
                    modifier = Modifier.fillMaxWidth().padding(16.dp),
                    horizontalArrangement = Arrangement.Center,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    CircularProgressIndicator(modifier = Modifier.size(22.dp), color = EmeraldCore)
                    Spacer(Modifier.width(10.dp))
                    Text("Fetching Modrinth mobile packs...", style = MaterialTheme.typography.bodyMedium)
                }
            }
        } else if (modrinthResults.isEmpty()) {
            item {
                Surface(
                    color = BasaltSurface,
                    shape = MaterialTheme.shapes.medium,
                    border = BorderStroke(1.dp, EtchedBorder),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Column(
                        modifier = Modifier.padding(16.dp),
                        verticalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        Text("No matching packs found.", style = MaterialTheme.typography.titleSmall)
                        Text(
                            text = if (bedrockOnly) {
                                "Try switching off the Bedrock / Mobile toggle to browse all Modrinth resource packs and data packs."
                            } else {
                                "Try a different keyword or refresh the catalog."
                            },
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }
            }
        }

        items(modrinthResults, key = { it.projectId }) { hit ->
            ModrinthHitCard(
                hit = hit,
                downloadStatus = downloadStatuses[hit.projectId] ?: ModrinthDownloadStatus.Idle,
                onDownload = { onDownloadModrinth(hit) },
                onOpenInMinecraft = onOpenInMinecraft
            )
        }
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun InstalledPackCard(
    pack: RoomPack,
    onToggleEnabled: () -> Unit,
    onToggleRequired: () -> Unit,
    onRemove: () -> Unit
) {
    val typeEnum = PackType.entries.firstOrNull { it.name == pack.packType } ?: PackType.RESOURCE_PACK
    Surface(
        color = BasaltElevated,
        shape = MaterialTheme.shapes.medium,
        border = BorderStroke(1.dp, if (pack.isEnabled) EmeraldCore.copy(alpha = 0.55f) else EtchedBorder),
        modifier = Modifier.fillMaxWidth().testTag("installed_pack_${pack.id}")
    ) {
        Column(
            modifier = Modifier.padding(12.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                if (pack.iconUrl.isNotBlank()) {
                    AsyncImage(
                        model = pack.iconUrl,
                        contentDescription = "${pack.title} icon",
                        contentScale = ContentScale.Crop,
                        modifier = Modifier.size(44.dp).clip(MaterialTheme.shapes.small)
                    )
                } else {
                    Surface(
                        color = DiamondContainer,
                        shape = MaterialTheme.shapes.small,
                        modifier = Modifier.size(44.dp)
                    ) {
                        Box(contentAlignment = Alignment.Center) {
                            Icon(Icons.Filled.Extension, contentDescription = null, tint = DiamondCyan)
                        }
                    }
                }

                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = pack.title,
                        style = MaterialTheme.typography.titleSmall,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                    Text(
                        text = "${typeEnum.label} · ${pack.versionNumber.ifBlank { pack.fileName }}",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                }

                Switch(
                    checked = pack.isEnabled,
                    onCheckedChange = { onToggleEnabled() },
                    modifier = Modifier.testTag("pack_enabled_switch_${pack.id}")
                )

                IconButton(
                    onClick = onRemove,
                    modifier = Modifier.size(48.dp).testTag("remove_pack_${pack.id}")
                ) {
                    Icon(Icons.Filled.DeleteOutline, contentDescription = "Remove pack", tint = RedstoneCrimson)
                }
            }

            FlowRow(
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                verticalArrangement = Arrangement.Center
            ) {
                Surface(
                    color = if (pack.source == PackSource.MODRINTH.name) EmeraldContainer else AmberContainer,
                    shape = MaterialTheme.shapes.extraSmall
                ) {
                    Text(
                        text = if (pack.source == PackSource.MODRINTH.name) "MODRINTH FREE" else "LOCAL FILE",
                        style = MaterialTheme.typography.labelSmall,
                        color = if (pack.source == PackSource.MODRINTH.name) EmeraldCore else AmberGold,
                        modifier = Modifier.padding(horizontal = 6.dp, vertical = 3.dp)
                    )
                }
                FilterChip(
                    selected = pack.isRequired,
                    onClick = onToggleRequired,
                    label = { Text(if (pack.isRequired) "Required by Clients" else "Optional") }
                )
            }
        }
    }
}

@Composable
private fun ModrinthHitCard(
    hit: ModrinthProjectHit,
    downloadStatus: ModrinthDownloadStatus,
    onDownload: () -> Unit,
    onOpenInMinecraft: (ModrinthDownloadStatus.Completed) -> Unit
) {
    val isBedrockTag = remember(hit.projectId) { hit.mentionsBedrockOrMobile() }

    Surface(
        color = BasaltSurface,
        shape = MaterialTheme.shapes.medium,
        border = BorderStroke(
            width = 1.dp,
            color = if (downloadStatus is ModrinthDownloadStatus.Completed) EmeraldCore.copy(alpha = 0.7f) else EtchedBorder
        ),
        modifier = Modifier.fillMaxWidth()
    ) {
        Column(
            modifier = Modifier.padding(12.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                if (!hit.iconUrl.isNullOrBlank()) {
                    AsyncImage(
                        model = hit.iconUrl,
                        contentDescription = "${hit.title} icon",
                        contentScale = ContentScale.Crop,
                        modifier = Modifier.size(48.dp).clip(MaterialTheme.shapes.small)
                    )
                } else {
                    Surface(
                        color = BasaltElevated,
                        shape = MaterialTheme.shapes.small,
                        modifier = Modifier.size(48.dp)
                    ) {
                        Box(contentAlignment = Alignment.Center) {
                            Icon(Icons.Filled.Extension, contentDescription = null, tint = EmeraldCore)
                        }
                    }
                }

                Column(modifier = Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(3.dp)) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(6.dp)
                    ) {
                        Text(
                            text = hit.title,
                            style = MaterialTheme.typography.titleSmall,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                            modifier = Modifier.weight(1f, fill = false)
                        )
                        if (isBedrockTag) {
                            Surface(
                                color = AmberContainer,
                                shape = MaterialTheme.shapes.extraSmall
                            ) {
                                Text(
                                    text = "BEDROCK",
                                    style = MaterialTheme.typography.labelSmall,
                                    color = AmberGold,
                                    modifier = Modifier.padding(horizontal = 5.dp, vertical = 2.dp)
                                )
                            }
                        }
                    }
                    Text(
                        text = "by ${hit.author} · ${formatDownloads(hit.downloads)} downloads",
                        style = MaterialTheme.typography.labelSmall,
                        color = DiamondCyan
                    )
                    Text(
                        text = hit.description,
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        maxLines = 2,
                        overflow = TextOverflow.Ellipsis
                    )
                }
            }

            when (downloadStatus) {
                is ModrinthDownloadStatus.Idle -> {
                    Button(
                        onClick = onDownload,
                        colors = ButtonDefaults.buttonColors(
                            containerColor = EmeraldCore,
                            contentColor = ObsidianVoid
                        ),
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(48.dp)
                            .testTag("download_modrinth_${hit.projectId}")
                    ) {
                        Icon(Icons.Filled.Download, contentDescription = null, modifier = Modifier.size(18.dp))
                        Spacer(Modifier.width(8.dp))
                        Text("Download .mcpack / .mcaddon")
                    }
                }
                is ModrinthDownloadStatus.Resolving -> {
                    Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                        Text(
                            text = "Checking Modrinth files for ${downloadStatus.projectTitle}...",
                            style = MaterialTheme.typography.labelSmall,
                            color = DiamondCyan
                        )
                        LinearProgressIndicator(modifier = Modifier.fillMaxWidth(), color = DiamondCyan)
                    }
                }
                is ModrinthDownloadStatus.Downloading -> {
                    val progress = if (downloadStatus.totalBytes > 0L) {
                        (downloadStatus.bytesRead.toFloat() / downloadStatus.totalBytes.toFloat()).coerceIn(0f, 1f)
                    } else {
                        null
                    }
                    Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                        val sizeText = if (downloadStatus.totalBytes > 0L) {
                            "${formatBytes(downloadStatus.bytesRead)} / ${formatBytes(downloadStatus.totalBytes)}"
                        } else {
                            formatBytes(downloadStatus.bytesRead)
                        }
                        Text(
                            text = "Downloading ${downloadStatus.fileName} ($sizeText)",
                            style = MaterialTheme.typography.labelSmall,
                            color = EmeraldCore,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                        if (progress != null) {
                            LinearProgressIndicator(
                                progress = { progress },
                                modifier = Modifier.fillMaxWidth(),
                                color = EmeraldCore
                            )
                        } else {
                            LinearProgressIndicator(modifier = Modifier.fillMaxWidth(), color = EmeraldCore)
                        }
                    }
                }
                is ModrinthDownloadStatus.Completed -> {
                    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(6.dp)
                        ) {
                            Icon(
                                Icons.Filled.CheckCircle,
                                contentDescription = null,
                                tint = EmeraldCore,
                                modifier = Modifier.size(16.dp)
                            )
                            Text(
                                text = "Saved ${downloadStatus.fileName} (${formatBytes(downloadStatus.fileSizeBytes)})" +
                                    if (downloadStatus.verifiedByModrinthHash) " · Verified" else "",
                                style = MaterialTheme.typography.labelSmall,
                                color = EmeraldCore,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis
                            )
                        }
                        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            Button(
                                onClick = { onOpenInMinecraft(downloadStatus) },
                                colors = ButtonDefaults.buttonColors(
                                    containerColor = EmeraldCore,
                                    contentColor = ObsidianVoid
                                ),
                                modifier = Modifier
                                    .weight(1f)
                                    .height(48.dp)
                                    .testTag("open_mc_${hit.projectId}")
                            ) {
                                Icon(Icons.AutoMirrored.Filled.OpenInNew, contentDescription = null, modifier = Modifier.size(18.dp))
                                Spacer(Modifier.width(6.dp))
                                Text("Open in Minecraft")
                            }
                            OutlinedButton(
                                onClick = onDownload,
                                modifier = Modifier.height(48.dp)
                            ) {
                                Icon(Icons.Filled.Download, contentDescription = "Re-download pack", modifier = Modifier.size(18.dp))
                            }
                        }
                    }
                }
                is ModrinthDownloadStatus.Failed -> {
                    Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                        Text(
                            text = downloadStatus.message,
                            style = MaterialTheme.typography.bodySmall,
                            color = RedstoneCrimson
                        )
                        OutlinedButton(
                            onClick = onDownload,
                            modifier = Modifier.fillMaxWidth().height(48.dp)
                        ) {
                            Icon(Icons.Filled.Refresh, contentDescription = null, modifier = Modifier.size(16.dp))
                            Spacer(Modifier.width(6.dp))
                            Text("Retry Download")
                        }
                    }
                }
            }
        }
    }
}

private fun formatDownloads(count: Long): String = when {
    count >= 1_000_000L -> "%.1fM".format(count / 1_000_000.0)
    count >= 1_000L -> "%.1fK".format(count / 1_000.0)
    else -> count.toString()
}

private fun formatBytes(bytes: Long): String = when {
    bytes >= 1_048_576L -> "%.1f MB".format(bytes / 1_048_576.0)
    bytes >= 1024L -> "%.0f KB".format(bytes / 1024.0)
    else -> "$bytes B"
}
