package com.example.ui.screens

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.layout.Arrangement
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
import androidx.compose.material.icons.filled.AdminPanelSettings
import androidx.compose.material.icons.filled.Block
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.PersonAdd
import androidx.compose.material.icons.filled.PersonRemove
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.PrimaryTabRow
import androidx.compose.material3.Surface
import androidx.compose.material3.Tab
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.unit.dp
import com.example.data.model.ServerRoom
import com.example.data.model.StaffRole
import com.example.data.network.ActivePeer
import com.example.data.network.HostRuntimeState
import com.example.ui.theme.BasaltElevated
import com.example.ui.theme.BasaltSurface
import com.example.ui.theme.DiamondCyan
import com.example.ui.theme.EmeraldCore
import com.example.ui.theme.EtchedBorder
import com.example.ui.theme.RedstoneCrimson

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun PlayersScreen(
    activeRoom: ServerRoom?,
    currentRole: StaffRole,
    hostRuntime: HostRuntimeState,
    onModeratePlayer: (action: String, playerName: String) -> Unit
) {
    var selectedTab by remember { mutableIntStateOf(0) }
    var usernameInput by remember { mutableStateOf("") }
    val tabs = listOf("Online", "Operators", "Whitelist", "Banned")
    val canOp = currentRole == StaffRole.OWNER || currentRole == StaffRole.ADMIN

    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        Text("Player Roster & Moderation", style = MaterialTheme.typography.titleLarge)

        PrimaryTabRow(selectedTabIndex = selectedTab) {
            tabs.forEachIndexed { idx, label ->
                Tab(
                    selected = selectedTab == idx,
                    onClick = { selectedTab = idx },
                    text = { Text(label, maxLines = 1) }
                )
            }
        }

        // Quick Add Player to Whitelist / Op / Ban
        Surface(
            color = BasaltSurface,
            shape = MaterialTheme.shapes.medium,
            border = BorderStroke(1.dp, EtchedBorder)
        ) {
            Column(
                modifier = Modifier.padding(12.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                OutlinedTextField(
                    value = usernameInput,
                    onValueChange = { usernameInput = it },
                    placeholder = { Text("Minecraft gamertag / username") },
                    singleLine = true,
                    modifier = Modifier
                        .fillMaxWidth()
                        .testTag("player_username_input")
                )
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    Button(
                        onClick = {
                            if (usernameInput.isNotBlank()) {
                                onModeratePlayer("WHITELIST_ADD", usernameInput)
                                usernameInput = ""
                            }
                        },
                        modifier = Modifier.weight(1f).height(48.dp).testTag("add_whitelist_button")
                    ) {
                        Icon(Icons.Filled.PersonAdd, contentDescription = null, modifier = Modifier.size(16.dp))
                        Spacer(Modifier.width(4.dp))
                        Text("Whitelist")
                    }
                    if (canOp) {
                        OutlinedButton(
                            onClick = {
                                if (usernameInput.isNotBlank()) {
                                    onModeratePlayer("OP_TOGGLE", usernameInput)
                                    usernameInput = ""
                                }
                            },
                            modifier = Modifier.height(48.dp).testTag("op_player_button")
                        ) {
                            Icon(Icons.Filled.AdminPanelSettings, contentDescription = null, modifier = Modifier.size(16.dp))
                            Spacer(Modifier.width(4.dp))
                            Text("Op")
                        }
                    }
                    OutlinedButton(
                        onClick = {
                            if (usernameInput.isNotBlank()) {
                                onModeratePlayer("BAN", usernameInput)
                                usernameInput = ""
                            }
                        },
                        colors = ButtonDefaults.outlinedButtonColors(contentColor = RedstoneCrimson),
                        modifier = Modifier.height(48.dp).testTag("ban_player_button")
                    ) {
                        Icon(Icons.Filled.Block, contentDescription = null, modifier = Modifier.size(16.dp))
                        Spacer(Modifier.width(4.dp))
                        Text("Ban")
                    }
                }
            }
        }

        if (activeRoom == null) {
            BoxEmptyMessage("Select or create a Server Room first.")
            return@Column
        }

        LazyColumn(
            modifier = Modifier.weight(1f),
            contentPadding = PaddingValues(vertical = 4.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            when (selectedTab) {
                0 -> {
                    val peers = hostRuntime.activePeers
                    if (peers.isEmpty()) {
                        item {
                            BoxEmptyMessage("No active RakNet players connected right now. Start the server or whitelist players above.")
                        }
                    } else {
                        items(peers, key = { it.addressKey }) { peer ->
                            OnlinePeerCard(
                                peer = peer,
                                canOp = canOp,
                                isOp = activeRoom.operators.any { it.equals(peer.displayName, true) },
                                onModerate = { action -> onModeratePlayer(action, peer.displayName) }
                            )
                        }
                    }
                }
                1 -> {
                    if (activeRoom.operators.isEmpty()) {
                        item { BoxEmptyMessage("No operators configured yet.") }
                    } else {
                        items(activeRoom.operators, key = { it }) { opName ->
                            PlayerListRow(
                                name = opName,
                                subtitle = "Server Operator",
                                accent = DiamondCyan,
                                actionLabel = if (canOp) "De-op" else null,
                                onAction = { onModeratePlayer("OP_TOGGLE", opName) }
                            )
                        }
                    }
                }
                2 -> {
                    if (activeRoom.whitelist.isEmpty()) {
                        item { BoxEmptyMessage("Whitelist is empty.") }
                    } else {
                        items(activeRoom.whitelist, key = { it }) { playerName ->
                            PlayerListRow(
                                name = playerName,
                                subtitle = "Whitelisted Player",
                                accent = EmeraldCore,
                                actionLabel = "Remove",
                                onAction = { onModeratePlayer("WHITELIST_REMOVE", playerName) }
                            )
                        }
                    }
                }
                else -> {
                    if (activeRoom.bannedPlayers.isEmpty()) {
                        item { BoxEmptyMessage("No banned players.") }
                    } else {
                        items(activeRoom.bannedPlayers, key = { it }) { bannedName ->
                            PlayerListRow(
                                name = bannedName,
                                subtitle = "Banned from server",
                                accent = RedstoneCrimson,
                                actionLabel = "Pardon",
                                onAction = { onModeratePlayer("PARDON", bannedName) }
                            )
                        }
                    }
                }
            }
        }
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun OnlinePeerCard(
    peer: ActivePeer,
    canOp: Boolean,
    isOp: Boolean,
    onModerate: (String) -> Unit
) {
    Surface(
        color = BasaltElevated,
        shape = MaterialTheme.shapes.medium,
        border = BorderStroke(1.dp, EtchedBorder),
        modifier = Modifier.fillMaxWidth()
    ) {
        Column(
            modifier = Modifier.padding(14.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Column {
                    Text(peer.displayName, style = MaterialTheme.typography.titleMedium)
                    Text(
                        text = "${peer.addressKey} · ${peer.pingMs}ms",
                        style = MaterialTheme.typography.labelMedium,
                        color = EmeraldCore
                    )
                }
                if (isOp) {
                    Text("OP", style = MaterialTheme.typography.labelLarge, color = DiamondCyan)
                }
            }
            FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                if (canOp) {
                    OutlinedButton(onClick = { onModerate("OP_TOGGLE") }, modifier = Modifier.height(48.dp)) {
                        Text(if (isOp) "De-op" else "Make Op")
                    }
                }
                OutlinedButton(onClick = { onModerate("KICK") }, modifier = Modifier.height(48.dp)) {
                    Icon(Icons.Filled.PersonRemove, contentDescription = null, modifier = Modifier.size(16.dp))
                    Spacer(Modifier.width(4.dp))
                    Text("Kick")
                }
                OutlinedButton(
                    onClick = { onModerate("BAN") },
                    colors = ButtonDefaults.outlinedButtonColors(contentColor = RedstoneCrimson),
                    modifier = Modifier.height(48.dp)
                ) {
                    Icon(Icons.Filled.Block, contentDescription = null, modifier = Modifier.size(16.dp))
                    Spacer(Modifier.width(4.dp))
                    Text("Ban")
                }
            }
        }
    }
}

@Composable
private fun PlayerListRow(
    name: String,
    subtitle: String,
    accent: androidx.compose.ui.graphics.Color,
    actionLabel: String?,
    onAction: () -> Unit
) {
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
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                Icon(Icons.Filled.CheckCircle, contentDescription = null, tint = accent)
                Column {
                    Text(name, style = MaterialTheme.typography.titleSmall)
                    Text(subtitle, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
            }
            if (actionLabel != null) {
                OutlinedButton(onClick = onAction, modifier = Modifier.height(48.dp)) {
                    Text(actionLabel)
                }
            }
        }
    }
}

@Composable
private fun BoxEmptyMessage(msg: String) {
    Surface(
        color = BasaltSurface,
        shape = MaterialTheme.shapes.medium,
        border = BorderStroke(1.dp, EtchedBorder),
        modifier = Modifier.fillMaxWidth()
    ) {
        Text(
            text = msg,
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.padding(16.dp)
        )
    }
}
