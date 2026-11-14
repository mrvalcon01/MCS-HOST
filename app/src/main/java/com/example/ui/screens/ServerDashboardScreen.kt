package com.example.ui.screens

import android.Manifest
import android.content.pm.PackageManager
import android.os.Build
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.clickable
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
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.ContentCopy
import androidx.compose.material.icons.filled.DeleteOutline
import androidx.compose.material.icons.filled.GroupAdd
import androidx.compose.material.icons.filled.Key
import androidx.compose.material.icons.filled.NetworkPing
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Public
import androidx.compose.material.icons.filled.Stop
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.core.content.ContextCompat
import com.example.data.model.ServerRoom
import com.example.data.model.StaffRole
import com.example.data.network.HostRuntimeState
import com.example.data.network.HostStatus
import com.example.data.network.ServerPingResult
import com.example.ui.components.RoleBadge
import com.example.ui.components.StatusPulseChip
import com.example.ui.components.TpsAndPingChart
import com.example.ui.theme.BasaltElevated
import com.example.ui.theme.BasaltSurface
import com.example.ui.theme.DiamondCyan
import com.example.ui.theme.EmeraldContainer
import com.example.ui.theme.EmeraldCore
import com.example.ui.theme.EtchedBorder
import com.example.ui.theme.ObsidianVoid
import com.example.ui.theme.RedstoneCrimson
import com.example.ui.viewmodel.UiState

@OptIn(ExperimentalLayoutApi::class)
@Composable
fun ServerDashboardScreen(
    roomsState: UiState<List<ServerRoom>>,
    activeRoom: ServerRoom?,
    currentRole: StaffRole,
    currentUserId: String,
    hostRuntime: HostRuntimeState,
    rconConnected: Boolean,
    remotePing: ServerPingResult?,
    onSelectRoom: (String) -> Unit,
    onOpenCreateRoom: () -> Unit,
    onOpenManageStaff: () -> Unit,
    onTogglePower: () -> Unit,
    onPingServer: () -> Unit,
    onPublishInvite: (Boolean) -> Unit,
    onJoinByRoleCode: (String) -> Unit,
    onDeleteRoom: () -> Unit,
    onNavigateToPacks: () -> Unit,
    onNavigateToWorlds: () -> Unit
) {
    val context = LocalContext.current
    val clipboard = LocalClipboardManager.current
    var roleCodeInput by remember { mutableStateOf("") }

    val notificationLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.RequestPermission(),
        onResult = { onTogglePower() }
    )

    val triggerPower = {
        if (activeRoom?.isLocalHost() == true &&
            Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU &&
            ContextCompat.checkSelfPermission(context, Manifest.permission.POST_NOTIFICATIONS) != PackageManager.PERMISSION_GRANTED
        ) {
            notificationLauncher.launch(Manifest.permission.POST_NOTIFICATIONS)
        } else {
            onTogglePower()
        }
    }

    LazyColumn(
        modifier = Modifier.fillMaxSize(),
        contentPadding = PaddingValues(16.dp),
        verticalArrangement = Arrangement.spacedBy(14.dp)
    ) {
        item {
            when (roomsState) {
                is UiState.Loading -> {
                    Surface(
                        color = BasaltSurface,
                        shape = MaterialTheme.shapes.large,
                        border = BorderStroke(1.dp, EtchedBorder),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Row(
                            modifier = Modifier.padding(24.dp),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(12.dp)
                        ) {
                            CircularProgressIndicator(modifier = Modifier.size(24.dp), color = EmeraldCore)
                            Text("Syncing your Server Rooms...", style = MaterialTheme.typography.bodyLarge)
                        }
                    }
                }
                is UiState.Error -> {
                    Surface(
                        color = MaterialTheme.colorScheme.errorContainer,
                        shape = MaterialTheme.shapes.large,
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Text(
                            text = roomsState.message,
                            color = MaterialTheme.colorScheme.onErrorContainer,
                            modifier = Modifier.padding(16.dp)
                        )
                    }
                }
                is UiState.Success -> {
                    if (activeRoom == null) {
                        EmptyRoomsHero(onOpenCreateRoom = onOpenCreateRoom)
                    } else {
                        ActiveRoomHeroCard(
                            room = activeRoom,
                            role = currentRole,
                            hostRuntime = hostRuntime,
                            rconConnected = rconConnected,
                            remotePing = remotePing,
                            onTogglePower = triggerPower,
                            onPingServer = onPingServer,
                            onOpenManageStaff = onOpenManageStaff,
                            onPublishInvite = { onPublishInvite(!activeRoom.isPublicInDirectory) },
                            onCopyAddress = { addr -> clipboard.setText(AnnotatedString(addr)) },
                            onDeleteRoom = onDeleteRoom,
                            onNavigateToPacks = onNavigateToPacks,
                            onNavigateToWorlds = onNavigateToWorlds
                        )
                    }
                }
            }
        }

        // Quick Join by Role Access Code
        item {
            Surface(
                color = BasaltSurface,
                shape = MaterialTheme.shapes.medium,
                border = BorderStroke(1.dp, EtchedBorder),
                modifier = Modifier.fillMaxWidth()
            ) {
                Column(
                    modifier = Modifier.padding(14.dp),
                    verticalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    Text(
                        text = "Join Shared Server Room (Admin / Mod Code)",
                        style = MaterialTheme.typography.titleSmall,
                        color = DiamondCyan
                    )
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        OutlinedTextField(
                            value = roleCodeInput,
                            onValueChange = { roleCodeInput = it.uppercase() },
                            placeholder = { Text("e.g. ADM-9X4L or MOD-3K7P") },
                            singleLine = true,
                            modifier = Modifier
                                .weight(1f)
                                .testTag("dashboard_role_code_input")
                        )
                        Button(
                            onClick = {
                                if (roleCodeInput.isNotBlank()) {
                                    onJoinByRoleCode(roleCodeInput)
                                    roleCodeInput = ""
                                }
                            },
                            modifier = Modifier
                                .height(52.dp)
                                .testTag("dashboard_join_role_button")
                        ) {
                            Icon(Icons.Filled.Key, contentDescription = null, modifier = Modifier.size(18.dp))
                            Spacer(Modifier.width(6.dp))
                            Text("Claim")
                        }
                    }
                }
            }
        }

        // Server Rooms Roster Header
        item {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = "Your Server Rooms",
                    style = MaterialTheme.typography.titleLarge
                )
                Button(
                    onClick = onOpenCreateRoom,
                    modifier = Modifier.testTag("new_server_room_button")
                ) {
                    Icon(Icons.Filled.Add, contentDescription = null, modifier = Modifier.size(18.dp))
                    Spacer(Modifier.width(4.dp))
                    Text("New Server")
                }
            }
        }

        val rooms = (roomsState as? UiState.Success)?.data.orEmpty()
        items(rooms, key = { it.id }) { room ->
            val isSelected = room.id == activeRoom?.id
            val roomRole = room.roleFor(currentUserId)
            Surface(
                color = if (isSelected) BasaltElevated else BasaltSurface,
                shape = MaterialTheme.shapes.medium,
                border = BorderStroke(
                    width = if (isSelected) 1.5.dp else 1.dp,
                    color = if (isSelected) EmeraldCore else EtchedBorder
                ),
                modifier = Modifier
                    .fillMaxWidth()
                    .clickable { onSelectRoom(room.id) }
                    .testTag("server_room_card_${room.id}")
            ) {
                Row(
                    modifier = Modifier.padding(14.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Column(modifier = Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                        Text(
                            text = room.name,
                            style = MaterialTheme.typography.titleMedium,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                        Text(
                            text = "${room.softwareEnum().label} · ${room.minecraftVersion} · Port ${room.port}",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                    Spacer(Modifier.width(8.dp))
                    RoleBadge(role = roomRole)
                }
            }
        }
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun ActiveRoomHeroCard(
    room: ServerRoom,
    role: StaffRole,
    hostRuntime: HostRuntimeState,
    rconConnected: Boolean,
    remotePing: ServerPingResult?,
    onTogglePower: () -> Unit,
    onPingServer: () -> Unit,
    onOpenManageStaff: () -> Unit,
    onPublishInvite: () -> Unit,
    onCopyAddress: (String) -> Unit,
    onDeleteRoom: () -> Unit,
    onNavigateToPacks: () -> Unit,
    onNavigateToWorlds: () -> Unit
) {
    val isLocalActive = room.isLocalHost() &&
        hostRuntime.status == HostStatus.RUNNING &&
        hostRuntime.activeRoomId == room.id
    val isOnline = if (room.isLocalHost()) isLocalActive else rconConnected || (remotePing?.isOnline == true)
    val displayAddress = if (room.isLocalHost()) {
        "${hostRuntime.localIpAddress}:${if (isLocalActive) hostRuntime.boundPort else room.port}"
    } else {
        "${room.host}:${room.port}"
    }
    val latestPingMs = if (room.isLocalHost()) {
        hostRuntime.latencyHistory.lastOrNull() ?: if (isLocalActive) 4 else 0
    } else {
        remotePing?.latencyMs ?: hostRuntime.latencyHistory.lastOrNull() ?: 0
    }
    val canControlPower = role == StaffRole.OWNER || role == StaffRole.ADMIN

    Surface(
        color = BasaltElevated,
        shape = MaterialTheme.shapes.large,
        border = BorderStroke(1.dp, if (isOnline) EmeraldCore.copy(alpha = 0.7f) else EtchedBorder),
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
                StatusPulseChip(
                    isOnline = isOnline,
                    statusLabel = when {
                        room.isLocalHost() && hostRuntime.status == HostStatus.STARTING -> "STARTING"
                        isOnline -> "ONLINE"
                        else -> "OFFLINE"
                    }
                )
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    RoleBadge(role = role)
                    if (role == StaffRole.OWNER) {
                        IconButton(
                            onClick = onDeleteRoom,
                            modifier = Modifier.size(48.dp)
                        ) {
                            Icon(
                                Icons.Filled.DeleteOutline,
                                contentDescription = "Delete Server Room",
                                tint = RedstoneCrimson
                            )
                        }
                    }
                }
            }

            Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
                Text(
                    text = room.name,
                    style = MaterialTheme.typography.headlineMedium,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
                Text(
                    text = "${room.softwareEnum().label} ${room.minecraftVersion} · ${room.editionEnum().label} · Seed ${room.seedNumeric}",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }

            // Address & Invite copy strip
            Surface(
                color = BasaltSurface,
                shape = MaterialTheme.shapes.small,
                border = BorderStroke(1.dp, EtchedBorder)
            ) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 12.dp, vertical = 6.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Column(modifier = Modifier.weight(1f)) {
                        Text(
                            text = "SERVER ADDRESS",
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                        Text(
                            text = displayAddress,
                            style = MaterialTheme.typography.labelLarge,
                            color = EmeraldCore
                        )
                        if (room.inviteCode.isNotBlank()) {
                            Text(
                                text = "Invite Code: ${room.inviteCode}",
                                style = MaterialTheme.typography.labelMedium,
                                color = DiamondCyan
                            )
                        }
                    }
                    IconButton(
                        onClick = { onCopyAddress(displayAddress) },
                        modifier = Modifier.size(48.dp).testTag("copy_address_button")
                    ) {
                        Icon(Icons.Filled.ContentCopy, contentDescription = "Copy server address")
                    }
                }
            }

            // Metrics row
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                MetricTile(
                    label = "TPS",
                    value = "%.1f".format(hostRuntime.tps),
                    modifier = Modifier.weight(1f)
                )
                MetricTile(
                    label = "PING",
                    value = if (latestPingMs > 0) "${latestPingMs}ms" else "--",
                    modifier = Modifier.weight(1f)
                )
                MetricTile(
                    label = "PLAYERS",
                    value = "${if (room.isLocalHost()) hostRuntime.activePeers.size else remotePing?.onlinePlayers ?: room.playerCount}/${room.maxPlayers}",
                    modifier = Modifier.weight(1f)
                )
            }

            TpsAndPingChart(
                tpsHistory = hostRuntime.tpsHistory,
                latencyHistory = hostRuntime.latencyHistory
            )

            // Primary Power & Ping row
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                Button(
                    onClick = onTogglePower,
                    enabled = canControlPower,
                    colors = ButtonDefaults.buttonColors(
                        containerColor = if (isOnline) RedstoneCrimson else EmeraldCore,
                        contentColor = if (isOnline) MaterialTheme.colorScheme.onError else ObsidianVoid
                    ),
                    modifier = Modifier
                        .weight(1.4f)
                        .height(48.dp)
                        .testTag("power_toggle_button")
                ) {
                    Icon(
                        imageVector = if (isOnline) Icons.Filled.Stop else Icons.Filled.PlayArrow,
                        contentDescription = null
                    )
                    Spacer(Modifier.width(6.dp))
                    Text(
                        text = when {
                            room.isLocalHost() && isOnline -> "Stop Server"
                            room.isLocalHost() -> "Start Server"
                            rconConnected -> "Disconnect RCON"
                            else -> "Connect RCON"
                        },
                        style = MaterialTheme.typography.labelLarge
                    )
                }

                OutlinedButton(
                    onClick = onPingServer,
                    modifier = Modifier
                        .weight(1f)
                        .height(48.dp)
                        .testTag("ping_server_button")
                ) {
                    Icon(Icons.Filled.NetworkPing, contentDescription = null, modifier = Modifier.size(18.dp))
                    Spacer(Modifier.width(6.dp))
                    Text("Ping")
                }
            }

            // Secondary tool shortcuts (Staff, Directory, Packs, Worlds)
            FlowRow(
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                OutlinedButton(
                    onClick = onOpenManageStaff,
                    modifier = Modifier.height(48.dp).testTag("manage_staff_button")
                ) {
                    Icon(Icons.Filled.GroupAdd, contentDescription = null, modifier = Modifier.size(16.dp))
                    Spacer(Modifier.width(6.dp))
                    Text("Access & Roles")
                }
                if (canControlPower) {
                    OutlinedButton(
                        onClick = onPublishInvite,
                        modifier = Modifier.height(48.dp).testTag("publish_invite_button")
                    ) {
                        Icon(Icons.Filled.Public, contentDescription = null, modifier = Modifier.size(16.dp))
                        Spacer(Modifier.width(6.dp))
                        Text(if (room.isPublicInDirectory) "Unlist" else "Share Free")
                    }
                }
                OutlinedButton(onClick = onNavigateToPacks, modifier = Modifier.height(48.dp)) {
                    Text("Mods & Packs")
                }
                OutlinedButton(onClick = onNavigateToWorlds, modifier = Modifier.height(48.dp)) {
                    Text("Seeds & Options")
                }
            }
        }
    }
}

@Composable
private fun MetricTile(label: String, value: String, modifier: Modifier = Modifier) {
    Surface(
        modifier = modifier,
        color = BasaltSurface,
        shape = MaterialTheme.shapes.small,
        border = BorderStroke(1.dp, EtchedBorder)
    ) {
        Column(modifier = Modifier.padding(horizontal = 10.dp, vertical = 8.dp)) {
            Text(
                text = label,
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            Text(
                text = value,
                style = MaterialTheme.typography.titleMedium,
                color = MaterialTheme.colorScheme.onSurface
            )
        }
    }
}

@Composable
private fun EmptyRoomsHero(onOpenCreateRoom: () -> Unit) {
    Surface(
        color = BasaltElevated,
        shape = MaterialTheme.shapes.large,
        border = BorderStroke(1.dp, EtchedBorder),
        modifier = Modifier.fillMaxWidth()
    ) {
        Column(
            modifier = Modifier.padding(24.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            Surface(color = EmeraldContainer, shape = MaterialTheme.shapes.medium) {
                Icon(
                    imageVector = Icons.Filled.PlayArrow,
                    contentDescription = null,
                    tint = EmeraldCore,
                    modifier = Modifier.padding(14.dp).size(32.dp)
                )
            }
            Text(
                text = "Launch Your First Free Server",
                style = MaterialTheme.typography.headlineSmall
            )
            Text(
                text = "Host an on-device Bedrock multiplayer server or connect a remote Java/Bedrock server via RCON—100% free with working seeds, open Modrinth mods & resource packs, and Admin/Moderator room delegation.",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            Button(
                onClick = onOpenCreateRoom,
                modifier = Modifier
                    .fillMaxWidth()
                    .height(50.dp)
                    .testTag("empty_create_server_button")
            ) {
                Icon(Icons.Filled.Add, contentDescription = null)
                Spacer(Modifier.width(8.dp))
                Text("Create Free Server Room")
            }
        }
    }
}
