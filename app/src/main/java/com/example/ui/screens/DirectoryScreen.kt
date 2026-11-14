package com.example.ui.screens

import androidx.compose.foundation.BorderStroke
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
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.BookmarkAdd
import androidx.compose.material.icons.filled.ContentCopy
import androidx.compose.material.icons.filled.DeleteOutline
import androidx.compose.material.icons.filled.Key
import androidx.compose.material.icons.filled.Public
import androidx.compose.material.icons.filled.Search
import androidx.compose.material3.Button
import androidx.compose.material3.HorizontalDivider
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
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.example.data.model.JoinedServer
import com.example.data.model.ServerInvite
import com.example.data.model.ServerRoom
import com.example.data.model.StaffRole
import com.example.data.network.ServerPingResult
import com.example.ui.components.StatusPulseChip
import com.example.ui.theme.BasaltElevated
import com.example.ui.theme.BasaltSurface
import com.example.ui.theme.DiamondCyan
import com.example.ui.theme.EmeraldCore
import com.example.ui.theme.EtchedBorder
import com.example.ui.theme.RedstoneCrimson

@Composable
fun DirectoryScreen(
    activeRoom: ServerRoom?,
    currentRole: StaffRole,
    publicInvites: List<ServerInvite>,
    joinedServers: List<JoinedServer>,
    lookedUpInvite: ServerInvite?,
    lookedUpPing: ServerPingResult?,
    onJoinByRoleCode: (String) -> Unit,
    onLookupInvite: (String) -> Unit,
    onBookmarkInvite: (ServerInvite) -> Unit,
    onRemoveJoined: (String) -> Unit,
    onPublishActiveRoom: (Boolean) -> Unit
) {
    val clipboard = LocalClipboardManager.current
    var roleCodeInput by remember { mutableStateOf("") }
    var inviteCodeInput by remember { mutableStateOf("") }

    LazyColumn(
        modifier = Modifier.fillMaxSize(),
        contentPadding = PaddingValues(16.dp),
        verticalArrangement = Arrangement.spacedBy(14.dp)
    ) {
        // Join Staff Room with Admin / Moderator Token
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
                    Text("Claim Admin / Moderator Room Access", style = MaterialTheme.typography.titleMedium, color = DiamondCyan)
                    Text(
                        text = "Enter an Admin (ADM-...) or Moderator (MOD-...) code from a friend to co-manage their server even when they are offline.",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        OutlinedTextField(
                            value = roleCodeInput,
                            onValueChange = { roleCodeInput = it.uppercase() },
                            placeholder = { Text("ADM-XXXX or MOD-XXXX") },
                            singleLine = true,
                            modifier = Modifier.weight(1f).testTag("directory_role_code_input")
                        )
                        Button(
                            onClick = {
                                if (roleCodeInput.isNotBlank()) {
                                    onJoinByRoleCode(roleCodeInput)
                                    roleCodeInput = ""
                                }
                            },
                            modifier = Modifier.height(52.dp).testTag("directory_claim_role_button")
                        ) {
                            Icon(Icons.Filled.Key, contentDescription = null, modifier = Modifier.size(18.dp))
                            Spacer(Modifier.width(6.dp))
                            Text("Claim")
                        }
                    }
                }
            }
        }

        // Lookup Friend Invite Code + Live Ping
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
                    Text("Find Friend Server by Invite Code", style = MaterialTheme.typography.titleMedium)
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        OutlinedTextField(
                            value = inviteCodeInput,
                            onValueChange = { inviteCodeInput = it.uppercase() },
                            placeholder = { Text("MCS-XXXX") },
                            singleLine = true,
                            modifier = Modifier.weight(1f).testTag("invite_lookup_input")
                        )
                        Button(
                            onClick = {
                                if (inviteCodeInput.isNotBlank()) {
                                    onLookupInvite(inviteCodeInput)
                                }
                            },
                            modifier = Modifier.height(52.dp).testTag("invite_lookup_button")
                        ) {
                            Icon(Icons.Filled.Search, contentDescription = "Lookup invite")
                        }
                    }

                    if (lookedUpInvite != null) {
                        InviteCard(
                            invite = lookedUpInvite,
                            livePing = lookedUpPing,
                            onCopyAddress = { addr -> clipboard.setText(AnnotatedString(addr)) },
                            onBookmark = { onBookmarkInvite(lookedUpInvite) }
                        )
                    }
                }
            }
        }

        // Publish Active Room
        if (activeRoom != null && currentRole != StaffRole.MODERATOR) {
            item {
                Surface(
                    color = BasaltSurface,
                    shape = MaterialTheme.shapes.medium,
                    border = BorderStroke(1.dp, EtchedBorder),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Row(
                        modifier = Modifier.padding(14.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        Column(modifier = Modifier.weight(1f)) {
                            Text("Share \"${activeRoom.name}\"", style = MaterialTheme.typography.titleSmall)
                            Text(
                                text = if (activeRoom.inviteCode.isNotBlank()) {
                                    "Invite Code: ${activeRoom.inviteCode}"
                                } else {
                                    "Generate a free invite code & list in public directory"
                                },
                                style = MaterialTheme.typography.bodySmall,
                                color = EmeraldCore
                            )
                        }
                        Spacer(Modifier.width(8.dp))
                        Button(
                            onClick = { onPublishActiveRoom(!activeRoom.isPublicInDirectory) },
                            modifier = Modifier.height(48.dp)
                        ) {
                            Icon(Icons.Filled.Public, contentDescription = null, modifier = Modifier.size(16.dp))
                            Spacer(Modifier.width(6.dp))
                            Text(if (activeRoom.isPublicInDirectory) "Unlist" else "Publish")
                        }
                    }
                }
            }
        }

        // Saved Friend Servers
        item {
            Text("Saved Friend Servers (${joinedServers.size})", style = MaterialTheme.typography.titleMedium)
        }
        if (joinedServers.isEmpty()) {
            item {
                Text(
                    text = "Bookmark any friend server via its MCS-XXXX code or the free directory below.",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        } else {
            items(joinedServers, key = { it.id }) { saved ->
                val address = "${saved.hostAddress}:${saved.port}"
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
                            Text(saved.serverName, style = MaterialTheme.typography.titleSmall, maxLines = 1, overflow = TextOverflow.Ellipsis)
                            Text(
                                text = "${saved.edition} · $address · ${saved.inviteCode}",
                                style = MaterialTheme.typography.bodySmall,
                                color = EmeraldCore
                            )
                        }
                        IconButton(
                            onClick = { clipboard.setText(AnnotatedString(address)) },
                            modifier = Modifier.size(48.dp)
                        ) {
                            Icon(Icons.Filled.ContentCopy, contentDescription = "Copy server address")
                        }
                        IconButton(
                            onClick = { onRemoveJoined(saved.inviteCode) },
                            modifier = Modifier.size(48.dp)
                        ) {
                            Icon(Icons.Filled.DeleteOutline, contentDescription = "Remove saved server", tint = RedstoneCrimson)
                        }
                    }
                }
            }
        }

        item { HorizontalDivider(color = EtchedBorder) }

        // Public Cloud Server Directory
        item {
            Text("Public Free Server Directory (${publicInvites.size})", style = MaterialTheme.typography.titleMedium)
        }
        items(publicInvites, key = { it.id }) { invite ->
            InviteCard(
                invite = invite,
                livePing = null,
                onCopyAddress = { addr -> clipboard.setText(AnnotatedString(addr)) },
                onBookmark = { onBookmarkInvite(invite) }
            )
        }
    }
}

@Composable
private fun InviteCard(
    invite: ServerInvite,
    livePing: ServerPingResult?,
    onCopyAddress: (String) -> Unit,
    onBookmark: () -> Unit
) {
    val online = livePing?.isOnline ?: invite.isOnline
    val addr = "${invite.hostAddress}:${invite.port}"
    Surface(
        color = BasaltElevated,
        shape = MaterialTheme.shapes.medium,
        border = BorderStroke(1.dp, if (online) EmeraldCore.copy(alpha = 0.55f) else EtchedBorder),
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
                Column(modifier = Modifier.weight(1f)) {
                    Text(invite.serverName, style = MaterialTheme.typography.titleSmall, maxLines = 1, overflow = TextOverflow.Ellipsis)
                    Text(
                        text = "${invite.edition} ${invite.minecraftVersion} · Host: ${invite.ownerName}",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
                StatusPulseChip(
                    isOnline = online,
                    statusLabel = if (livePing != null && livePing.isOnline) "${livePing.latencyMs}ms" else if (online) "ONLINE" else "OFFLINE"
                )
            }
            if (invite.motd.isNotBlank()) {
                Text(invite.motd, style = MaterialTheme.typography.bodySmall)
            }
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = "$addr · ${invite.id}",
                    style = MaterialTheme.typography.labelMedium,
                    color = DiamondCyan,
                    modifier = Modifier.weight(1f)
                )
                Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    OutlinedButton(
                        onClick = { onCopyAddress(addr) },
                        modifier = Modifier.height(48.dp)
                    ) {
                        Icon(Icons.Filled.ContentCopy, contentDescription = null, modifier = Modifier.size(16.dp))
                        Spacer(Modifier.width(4.dp))
                        Text("Copy IP")
                    }
                    Button(
                        onClick = onBookmark,
                        modifier = Modifier.height(48.dp)
                    ) {
                        Icon(Icons.Filled.BookmarkAdd, contentDescription = null, modifier = Modifier.size(16.dp))
                        Spacer(Modifier.width(4.dp))
                        Text("Save")
                    }
                }
            }
        }
    }
}
