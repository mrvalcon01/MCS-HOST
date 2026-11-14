package com.example.ui.screens

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
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Backup
import androidx.compose.material.icons.filled.Casino
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.ContentCopy
import androidx.compose.material.icons.filled.Restore
import androidx.compose.material.icons.filled.Save
import androidx.compose.material.icons.filled.Visibility
import androidx.compose.material.icons.filled.VisibilityOff
import androidx.compose.material3.Button
import androidx.compose.material3.FilterChip
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
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
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.unit.dp
import com.example.data.model.DifficultyOption
import com.example.data.model.GameModeOption
import com.example.data.model.RoomRconConfig
import com.example.data.model.ServerRoom
import com.example.data.model.StaffRole
import com.example.data.model.WorldBackup
import com.example.data.model.WorldSeedCatalog
import com.example.ui.components.RoleBadge
import com.example.ui.theme.AmberGold
import com.example.ui.theme.BasaltElevated
import com.example.ui.theme.BasaltSurface
import com.example.ui.theme.DiamondCyan
import com.example.ui.theme.EmeraldContainer
import com.example.ui.theme.EmeraldCore
import com.example.ui.theme.EtchedBorder
import kotlin.random.Random

@OptIn(ExperimentalLayoutApi::class)
@Composable
fun WorldsAndOptionsScreen(
    activeRoom: ServerRoom?,
    currentRole: StaffRole,
    backups: List<WorldBackup>,
    rconConfig: RoomRconConfig?,
    onUpdateOptions: (ServerRoom) -> Unit,
    onApplySeed: (seedText: String, seedLabel: String) -> Unit,
    onSaveRconCredentials: (port: Long, password: String) -> Unit,
    onCreateSnapshot: (String) -> Unit,
    onRestoreSnapshot: (WorldBackup) -> Unit
) {
    if (activeRoom == null) {
        Box(modifier = Modifier.fillMaxSize().padding(24.dp), contentAlignment = Alignment.Center) {
            Text(
                text = "Select or create a Server Room to configure world seeds, gamerules, and options.",
                style = MaterialTheme.typography.bodyLarge,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
        return
    }

    val canEditWorld = currentRole == StaffRole.OWNER || currentRole == StaffRole.ADMIN
    val clipboard = LocalClipboardManager.current

    var draft by remember(activeRoom) { mutableStateOf(activeRoom) }
    var seedInput by remember(activeRoom.id, activeRoom.seed) { mutableStateOf(activeRoom.seed) }
    var seedLabelInput by remember(activeRoom.id, activeRoom.seedLabel) { mutableStateOf(activeRoom.seedLabel) }
    var maxPlayersText by remember(activeRoom.id, activeRoom.maxPlayers) { mutableStateOf(activeRoom.maxPlayers.toString()) }
    var viewDistanceText by remember(activeRoom.id, activeRoom.viewDistance) { mutableStateOf(activeRoom.viewDistance.toString()) }
    var simDistanceText by remember(activeRoom.id, activeRoom.simulationDistance) { mutableStateOf(activeRoom.simulationDistance.toString()) }
    var portText by remember(activeRoom.id, activeRoom.port) { mutableStateOf(activeRoom.port.toString()) }
    var snapshotName by remember { mutableStateOf("Pre-Boss Backup") }
    var showPropertiesPreview by remember { mutableStateOf(false) }

    var rconPortText by remember(rconConfig) { mutableStateOf((rconConfig?.rconPort ?: 25575L).toString()) }
    var rconPasswordText by remember(rconConfig) { mutableStateOf(rconConfig?.rconPassword.orEmpty()) }
    var showRconPassword by remember { mutableStateOf(false) }

    LazyColumn(
        modifier = Modifier.fillMaxSize(),
        contentPadding = PaddingValues(16.dp),
        verticalArrangement = Arrangement.spacedBy(14.dp)
    ) {
        // Active Seed Header & Working Seed Picker
        item {
            Surface(
                color = BasaltElevated,
                shape = MaterialTheme.shapes.large,
                border = BorderStroke(1.dp, EtchedBorder),
                modifier = Modifier.fillMaxWidth()
            ) {
                Column(
                    modifier = Modifier.padding(16.dp),
                    verticalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Column(modifier = Modifier.weight(1f)) {
                            Text("Working World Seeds", style = MaterialTheme.typography.titleLarge)
                            Text(
                                text = "Active: ${activeRoom.seedLabel} (${activeRoom.seedNumeric})",
                                style = MaterialTheme.typography.bodySmall,
                                color = EmeraldCore
                            )
                        }
                        RoleBadge(role = currentRole)
                    }

                    if (!canEditWorld) {
                        Text(
                            text = "Moderator View: Only Room Owner or Admin can modify world seeds and server.properties.",
                            style = MaterialTheme.typography.bodySmall,
                            color = AmberGold
                        )
                    }

                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        OutlinedTextField(
                            value = seedInput,
                            onValueChange = {
                                seedInput = it
                                seedLabelInput = "Custom World Seed"
                            },
                            enabled = canEditWorld,
                            label = { Text("Seed Text or 64-bit Number") },
                            singleLine = true,
                            modifier = Modifier.weight(1f).testTag("world_seed_input")
                        )
                        IconButton(
                            onClick = {
                                seedInput = Random.nextLong().toString()
                                seedLabelInput = "Random 64-bit Seed"
                            },
                            enabled = canEditWorld,
                            modifier = Modifier.size(48.dp).testTag("random_seed_button")
                        ) {
                            Icon(Icons.Filled.Casino, contentDescription = "Generate random 64-bit seed")
                        }
                    }

                    val previewNumeric = WorldSeedCatalog.resolveNumericSeed(seedInput)
                    Text(
                        text = "Resolved level-seed: $previewNumeric",
                        style = MaterialTheme.typography.labelMedium,
                        color = DiamondCyan
                    )

                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        Button(
                            onClick = { onApplySeed(seedInput, seedLabelInput) },
                            enabled = canEditWorld && seedInput.isNotBlank(),
                            modifier = Modifier
                                .weight(1f)
                                .height(48.dp)
                                .testTag("apply_seed_button")
                        ) {
                            Icon(Icons.Filled.Check, contentDescription = null, modifier = Modifier.size(18.dp))
                            Spacer(Modifier.width(6.dp))
                            Text("Apply Seed")
                        }
                        OutlinedButton(
                            onClick = {
                                clipboard.setText(AnnotatedString(draft.toServerPropertiesText()))
                            },
                            modifier = Modifier.height(48.dp).testTag("copy_properties_button")
                        ) {
                            Icon(Icons.Filled.ContentCopy, contentDescription = null, modifier = Modifier.size(16.dp))
                            Spacer(Modifier.width(6.dp))
                            Text("server.properties")
                        }
                    }
                }
            }
        }

        // Curated Working Seed Vault
        item {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Text("Curated Working Seed Vault", style = MaterialTheme.typography.titleMedium)
                LazyRow(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                    items(WorldSeedCatalog.curatedSeeds, key = { it.numericSeed }) { curated ->
                        val isActive = activeRoom.seedNumeric == curated.numericSeed
                        Surface(
                            color = if (isActive) EmeraldContainer else BasaltSurface,
                            shape = MaterialTheme.shapes.medium,
                            border = BorderStroke(
                                width = if (isActive) 1.5.dp else 1.dp,
                                color = if (isActive) EmeraldCore else EtchedBorder
                            ),
                            modifier = Modifier.width(260.dp)
                        ) {
                            Column(
                                modifier = Modifier.padding(12.dp),
                                verticalArrangement = Arrangement.spacedBy(6.dp)
                            ) {
                                Text(curated.title, style = MaterialTheme.typography.titleSmall, color = EmeraldCore)
                                Text(curated.biomes, style = MaterialTheme.typography.labelSmall, color = DiamondCyan)
                                Text(
                                    text = curated.description,
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                                Text(
                                    text = "Seed: ${curated.seedText}",
                                    style = MaterialTheme.typography.labelSmall
                                )
                                OutlinedButton(
                                    onClick = {
                                        seedInput = curated.seedText
                                        seedLabelInput = curated.title
                                        onApplySeed(curated.seedText, curated.title)
                                    },
                                    enabled = canEditWorld,
                                    modifier = Modifier.fillMaxWidth().height(48.dp)
                                ) {
                                    Text(if (isActive) "Active Seed" else "Use This Seed")
                                }
                            }
                        }
                    }
                }
            }
        }

        // server.properties & Gamerules Editor
        item {
            Surface(
                color = BasaltSurface,
                shape = MaterialTheme.shapes.large,
                border = BorderStroke(1.dp, EtchedBorder),
                modifier = Modifier.fillMaxWidth()
            ) {
                Column(
                    modifier = Modifier.padding(16.dp),
                    verticalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    Text("Server Options & Gamerules", style = MaterialTheme.typography.titleMedium)

                    OutlinedTextField(
                        value = draft.motd,
                        onValueChange = { draft = draft.copy(motd = it) },
                        enabled = canEditWorld,
                        label = { Text("Server MOTD") },
                        singleLine = true,
                        modifier = Modifier.fillMaxWidth()
                    )

                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        OutlinedTextField(
                            value = draft.minecraftVersion,
                            onValueChange = { draft = draft.copy(minecraftVersion = it) },
                            enabled = canEditWorld,
                            label = { Text("MC Version") },
                            singleLine = true,
                            modifier = Modifier.weight(1f)
                        )
                        OutlinedTextField(
                            value = portText,
                            onValueChange = {
                                portText = it.filter(Char::isDigit)
                                draft = draft.copy(port = portText.toLongOrNull() ?: draft.port)
                            },
                            enabled = canEditWorld,
                            label = { Text("Server Port") },
                            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                            singleLine = true,
                            modifier = Modifier.weight(1f)
                        )
                    }

                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        OutlinedTextField(
                            value = maxPlayersText,
                            onValueChange = {
                                maxPlayersText = it.filter(Char::isDigit)
                                draft = draft.copy(maxPlayers = (maxPlayersText.toLongOrNull() ?: 20L).coerceIn(1L, 500L))
                            },
                            enabled = canEditWorld,
                            label = { Text("Max Players") },
                            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                            singleLine = true,
                            modifier = Modifier.weight(1f)
                        )
                        OutlinedTextField(
                            value = viewDistanceText,
                            onValueChange = {
                                viewDistanceText = it.filter(Char::isDigit)
                                draft = draft.copy(viewDistance = (viewDistanceText.toLongOrNull() ?: 10L).coerceIn(4L, 32L))
                            },
                            enabled = canEditWorld,
                            label = { Text("View Dist") },
                            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                            singleLine = true,
                            modifier = Modifier.weight(1f)
                        )
                        OutlinedTextField(
                            value = simDistanceText,
                            onValueChange = {
                                simDistanceText = it.filter(Char::isDigit)
                                draft = draft.copy(simulationDistance = (simDistanceText.toLongOrNull() ?: 6L).coerceIn(4L, 16L))
                            },
                            enabled = canEditWorld,
                            label = { Text("Sim Dist") },
                            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                            singleLine = true,
                            modifier = Modifier.weight(1f)
                        )
                    }

                    Text("Default Gamemode", style = MaterialTheme.typography.labelLarge, color = DiamondCyan)
                    FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        GameModeOption.entries.forEach { gm ->
                            FilterChip(
                                selected = draft.gamemode == gm.name,
                                onClick = { if (canEditWorld) draft = draft.copy(gamemode = gm.name) },
                                label = { Text(gm.label) }
                            )
                        }
                    }

                    Text("Difficulty", style = MaterialTheme.typography.labelLarge, color = DiamondCyan)
                    FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        DifficultyOption.entries.forEach { diff ->
                            FilterChip(
                                selected = draft.difficulty == diff.name,
                                onClick = { if (canEditWorld) draft = draft.copy(difficulty = diff.name) },
                                label = { Text(diff.label) }
                            )
                        }
                    }

                    HorizontalDivider(color = EtchedBorder)

                    OptionSwitchRow("PvP Enabled", draft.pvp, canEditWorld) { draft = draft.copy(pvp = it) }
                    OptionSwitchRow("Hardcore Mode", draft.hardcore, canEditWorld) { draft = draft.copy(hardcore = it) }
                    OptionSwitchRow("Allow Cheats", draft.allowCheats, canEditWorld) { draft = draft.copy(allowCheats = it) }
                    OptionSwitchRow("Command Blocks", draft.commandBlocks, canEditWorld) { draft = draft.copy(commandBlocks = it) }
                    OptionSwitchRow("Online Mode (Xbox/Mojang Auth)", draft.onlineMode, canEditWorld) { draft = draft.copy(onlineMode = it) }
                    OptionSwitchRow("Enforce Whitelist", draft.whitelistEnabled, canEditWorld) { draft = draft.copy(whitelistEnabled = it) }
                    OptionSwitchRow("Require Resource Packs", draft.resourcePackRequired, canEditWorld) { draft = draft.copy(resourcePackRequired = it) }
                    OptionSwitchRow("Gamerule: keepInventory", draft.keepInventory, canEditWorld) { draft = draft.copy(keepInventory = it) }
                    OptionSwitchRow("Gamerule: mobGriefing", draft.mobGriefing, canEditWorld) { draft = draft.copy(mobGriefing = it) }
                    OptionSwitchRow("Gamerule: doDaylightCycle", draft.doDaylightCycle, canEditWorld) { draft = draft.copy(doDaylightCycle = it) }
                    OptionSwitchRow("Gamerule: doWeatherCycle", draft.doWeatherCycle, canEditWorld) { draft = draft.copy(doWeatherCycle = it) }
                    OptionSwitchRow("Gamerule: naturalRegeneration", draft.naturalRegeneration, canEditWorld) { draft = draft.copy(naturalRegeneration = it) }

                    Button(
                        onClick = { onUpdateOptions(draft) },
                        enabled = canEditWorld,
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(50.dp)
                            .testTag("save_world_options_button")
                    ) {
                        Icon(Icons.Filled.Save, contentDescription = null, modifier = Modifier.size(18.dp))
                        Spacer(Modifier.width(8.dp))
                        Text("Save & Sync Server Options")
                    }

                    OutlinedButton(
                        onClick = { showPropertiesPreview = !showPropertiesPreview },
                        modifier = Modifier.fillMaxWidth().height(48.dp)
                    ) {
                        Text(if (showPropertiesPreview) "Hide server.properties" else "Preview server.properties")
                    }

                    if (showPropertiesPreview) {
                        Surface(
                            color = BasaltElevated,
                            shape = MaterialTheme.shapes.small,
                            border = BorderStroke(1.dp, EtchedBorder)
                        ) {
                            Text(
                                text = draft.toServerPropertiesText(),
                                style = MaterialTheme.typography.bodySmall,
                                color = EmeraldCore,
                                modifier = Modifier.padding(12.dp)
                            )
                        }
                    }
                }
            }
        }

        // Private RCON Credentials (Owner & Admin ONLY)
        if (canEditWorld) {
            item {
                Surface(
                    color = BasaltSurface,
                    shape = MaterialTheme.shapes.large,
                    border = BorderStroke(1.dp, EtchedBorder),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Column(
                        modifier = Modifier.padding(16.dp),
                        verticalArrangement = Arrangement.spacedBy(10.dp)
                    ) {
                        Text("Remote RCON Credentials (Owner/Admin Only)", style = MaterialTheme.typography.titleMedium)
                        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            OutlinedTextField(
                                value = rconPortText,
                                onValueChange = { rconPortText = it.filter(Char::isDigit) },
                                label = { Text("RCON Port") },
                                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                                singleLine = true,
                                modifier = Modifier.weight(1f)
                            )
                            OutlinedTextField(
                                value = rconPasswordText,
                                onValueChange = { rconPasswordText = it },
                                label = { Text("RCON Password") },
                                singleLine = true,
                                visualTransformation = if (showRconPassword) VisualTransformation.None else PasswordVisualTransformation(),
                                trailingIcon = {
                                    IconButton(onClick = { showRconPassword = !showRconPassword }) {
                                        Icon(
                                            imageVector = if (showRconPassword) Icons.Filled.VisibilityOff else Icons.Filled.Visibility,
                                            contentDescription = "Toggle password visibility"
                                        )
                                    }
                                },
                                modifier = Modifier.weight(1.5f)
                            )
                        }
                        OutlinedButton(
                            onClick = {
                                onSaveRconCredentials(rconPortText.toLongOrNull() ?: 25575L, rconPasswordText)
                            },
                            modifier = Modifier.fillMaxWidth().height(48.dp)
                        ) {
                            Text("Save RCON Credentials")
                        }
                    }
                }
            }
        }

        // World Snapshot Backups
        item {
            Surface(
                color = BasaltSurface,
                shape = MaterialTheme.shapes.large,
                border = BorderStroke(1.dp, EtchedBorder),
                modifier = Modifier.fillMaxWidth()
            ) {
                Column(
                    modifier = Modifier.padding(16.dp),
                    verticalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    Text("Cloud World Snapshot Backups", style = MaterialTheme.typography.titleMedium)
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        OutlinedTextField(
                            value = snapshotName,
                            onValueChange = { snapshotName = it },
                            enabled = canEditWorld,
                            label = { Text("Snapshot Label") },
                            singleLine = true,
                            modifier = Modifier.weight(1f)
                        )
                        Button(
                            onClick = {
                                if (snapshotName.isNotBlank()) onCreateSnapshot(snapshotName)
                            },
                            enabled = canEditWorld && snapshotName.isNotBlank(),
                            modifier = Modifier.height(52.dp).testTag("create_snapshot_button")
                        ) {
                            Icon(Icons.Filled.Backup, contentDescription = null, modifier = Modifier.size(18.dp))
                            Spacer(Modifier.width(6.dp))
                            Text("Save")
                        }
                    }
                }
            }
        }

        items(backups, key = { it.id }) { backup ->
            Surface(
                color = BasaltElevated,
                shape = MaterialTheme.shapes.medium,
                border = BorderStroke(1.dp, EtchedBorder),
                modifier = Modifier.fillMaxWidth()
            ) {
                Row(
                    modifier = Modifier.padding(12.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Column(modifier = Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(2.dp)) {
                        Text(backup.name, style = MaterialTheme.typography.titleSmall)
                        Text(
                            text = "Seed ${backup.seedNumeric} · ${backup.gamemode}/${backup.difficulty} · ${backup.packCount} packs",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                        Text(
                            text = "Saved by ${backup.createdByName}",
                            style = MaterialTheme.typography.labelSmall,
                            color = DiamondCyan
                        )
                    }
                    OutlinedButton(
                        onClick = { onRestoreSnapshot(backup) },
                        enabled = canEditWorld,
                        modifier = Modifier.height(48.dp)
                    ) {
                        Icon(Icons.Filled.Restore, contentDescription = null, modifier = Modifier.size(16.dp))
                        Spacer(Modifier.width(4.dp))
                        Text("Restore")
                    }
                }
            }
        }
    }
}

@Composable
private fun OptionSwitchRow(
    label: String,
    checked: Boolean,
    enabled: Boolean,
    onCheckedChange: (Boolean) -> Unit
) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.SpaceBetween
    ) {
        Text(text = label, style = MaterialTheme.typography.bodyMedium, modifier = Modifier.weight(1f))
        Switch(checked = checked, onCheckedChange = onCheckedChange, enabled = enabled)
    }
}
