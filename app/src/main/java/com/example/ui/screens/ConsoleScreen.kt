package com.example.ui.screens

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
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
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.rememberScrollState
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.Send
import androidx.compose.material.icons.filled.DeleteSweep
import androidx.compose.material3.AssistChip
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.unit.dp
import com.example.data.model.RoomCommand
import com.example.data.model.ServerRoom
import com.example.data.model.StaffRole
import com.example.data.network.HostEngineController
import com.example.data.network.HostRuntimeState
import com.example.data.network.LogSeverity
import com.example.ui.components.RoleBadge
import com.example.ui.theme.AmberGold
import com.example.ui.theme.BasaltElevated
import com.example.ui.theme.BasaltSurface
import com.example.ui.theme.DiamondCyan
import com.example.ui.theme.EmeraldCore
import com.example.ui.theme.EtchedBorder
import com.example.ui.theme.RedstoneCrimson

@Composable
fun ConsoleScreen(
    activeRoom: ServerRoom?,
    currentRole: StaffRole,
    hostRuntime: HostRuntimeState,
    roomCommands: List<RoomCommand>,
    onSendCommand: (String) -> Unit
) {
    var commandInput by remember { mutableStateOf("") }
    var selectedFilter by remember { mutableStateOf<LogSeverity?>(null) }
    val listState = rememberLazyListState()

    val filteredLogs = remember(hostRuntime.consoleLogs, selectedFilter) {
        if (selectedFilter == null) hostRuntime.consoleLogs
        else hostRuntime.consoleLogs.filter { it.severity == selectedFilter }
    }

    LaunchedEffect(filteredLogs.size) {
        if (filteredLogs.isNotEmpty()) {
            listState.animateScrollToItem(filteredLogs.lastIndex)
        }
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(10.dp)
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Column {
                Text("Live Server Console", style = MaterialTheme.typography.titleLarge)
                Text(
                    text = activeRoom?.name ?: "No server selected",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
            Row(verticalAlignment = Alignment.CenterVertically) {
                RoleBadge(role = currentRole)
                IconButton(
                    onClick = { HostEngineController.clearLogs() },
                    modifier = Modifier.size(48.dp)
                ) {
                    Icon(Icons.Filled.DeleteSweep, contentDescription = "Clear console logs")
                }
            }
        }

        // Severity filters
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .horizontalScroll(rememberScrollState()),
            horizontalArrangement = Arrangement.spacedBy(6.dp)
        ) {
            FilterChip(
                selected = selectedFilter == null,
                onClick = { selectedFilter = null },
                label = { Text("ALL") }
            )
            LogSeverity.entries.forEach { sev ->
                FilterChip(
                    selected = selectedFilter == sev,
                    onClick = { selectedFilter = if (selectedFilter == sev) null else sev },
                    label = { Text(sev.name) }
                )
            }
        }

        // Terminal viewport
        Surface(
            color = BasaltSurface,
            shape = MaterialTheme.shapes.medium,
            border = BorderStroke(1.dp, EtchedBorder),
            modifier = Modifier
                .weight(1f)
                .fillMaxWidth()
        ) {
            if (filteredLogs.isEmpty() && roomCommands.isEmpty()) {
                Column(
                    modifier = Modifier.fillMaxSize().padding(24.dp),
                    verticalArrangement = Arrangement.Center,
                    horizontalAlignment = Alignment.CenterHorizontally
                ) {
                    Text(
                        text = "Console Ready",
                        style = MaterialTheme.typography.titleMedium,
                        color = EmeraldCore
                    )
                    Spacer(Modifier.height(4.dp))
                    Text(
                        text = "Start the Bedrock host, connect RCON, or dispatch a command below.",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            } else {
                LazyColumn(
                    state = listState,
                    contentPadding = PaddingValues(12.dp),
                    verticalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    items(filteredLogs, key = { it.id }) { line ->
                        val tagColor = when (line.severity) {
                            LogSeverity.INFO -> EmeraldCore
                            LogSeverity.WARN -> AmberGold
                            LogSeverity.ERROR -> RedstoneCrimson
                            LogSeverity.RCON -> DiamondCyan
                            LogSeverity.RAKNET -> EmeraldCore
                        }
                        Text(
                            text = "[${line.timestamp}] [${line.tag}] ${line.message}",
                            style = MaterialTheme.typography.labelMedium,
                            color = tagColor.copy(alpha = 0.92f)
                        )
                    }
                    if (roomCommands.isNotEmpty()) {
                        item {
                            Spacer(Modifier.height(6.dp))
                            Text(
                                text = "--- DELEGATED ROOM RELAY ---",
                                style = MaterialTheme.typography.labelSmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                        items(roomCommands.take(10), key = { it.id }) { cmd ->
                            Text(
                                text = "[${cmd.senderRole}] ${cmd.senderName}: ${cmd.command} (${cmd.status}) ${cmd.response}",
                                style = MaterialTheme.typography.labelMedium,
                                color = DiamondCyan
                            )
                        }
                    }
                }
            }
        }

        // Quick command chips
        val quickCommands = if (currentRole == StaffRole.MODERATOR) {
            listOf("/list", "/say Server check!", "/kick ", "/whitelist list")
        } else {
            listOf("/list", "/save-all", "/time set day", "/weather clear", "/say Welcome!")
        }

        Row(
            modifier = Modifier
                .fillMaxWidth()
                .horizontalScroll(rememberScrollState()),
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            quickCommands.forEach { preset ->
                AssistChip(
                    onClick = {
                        if (preset.endsWith(" ")) {
                            commandInput = preset
                        } else {
                            onSendCommand(preset)
                        }
                    },
                    label = { Text(preset, style = MaterialTheme.typography.labelMedium) }
                )
            }
        }

        // Command input bar
        Surface(
            color = BasaltElevated,
            shape = MaterialTheme.shapes.medium,
            border = BorderStroke(1.dp, EtchedBorder)
        ) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(8.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                OutlinedTextField(
                    value = commandInput,
                    onValueChange = { commandInput = it },
                    placeholder = { Text("Enter /command...") },
                    singleLine = true,
                    modifier = Modifier
                        .weight(1f)
                        .testTag("console_command_input")
                )
                Spacer(Modifier.width(8.dp))
                IconButton(
                    onClick = {
                        if (commandInput.isNotBlank()) {
                            onSendCommand(commandInput)
                            commandInput = ""
                        }
                    },
                    modifier = Modifier
                        .size(48.dp)
                        .testTag("send_command_button")
                ) {
                    Icon(
                        Icons.AutoMirrored.Filled.Send,
                        contentDescription = "Send command",
                        tint = EmeraldCore
                    )
                }
            }
        }
    }
}
