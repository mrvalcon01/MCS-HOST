package com.example.ui.components

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.AdminPanelSettings
import androidx.compose.material.icons.filled.Casino
import androidx.compose.material.icons.filled.ContentCopy
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.PersonAdd
import androidx.compose.material.icons.filled.Security
import androidx.compose.material.icons.filled.Shield
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.FilterChip
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.example.data.model.GameEdition
import com.example.data.model.RoleGrant
import com.example.data.model.RoomMember
import com.example.data.model.ServerMode
import com.example.data.model.ServerRoom
import com.example.data.model.ServerSoftware
import com.example.data.model.StaffRole
import com.example.data.model.WorldSeedCatalog
import com.example.data.network.HostStatus
import com.example.ui.theme.AmberContainer
import com.example.ui.theme.AmberGold
import com.example.ui.theme.DiamondContainer
import com.example.ui.theme.DiamondCyan
import com.example.ui.theme.EmeraldContainer
import com.example.ui.theme.EmeraldCore
import com.example.ui.theme.EtchedBorder
import com.example.ui.theme.RedstoneContainer
import com.example.ui.theme.RedstoneCrimson
import kotlin.random.Random

@Composable
fun RoleBadge(role: StaffRole, modifier: Modifier = Modifier) {
    val (bg, fg, icon) = when (role) {
        StaffRole.OWNER -> Triple(EmeraldContainer, EmeraldCore, Icons.Filled.Shield)
        StaffRole.ADMIN -> Triple(DiamondContainer, DiamondCyan, Icons.Filled.AdminPanelSettings)
        StaffRole.MODERATOR -> Triple(AmberContainer, AmberGold, Icons.Filled.Security)
    }
    Surface(
        modifier = modifier,
        color = bg,
        shape = MaterialTheme.shapes.small,
        border = BorderStroke(1.dp, fg.copy(alpha = 0.45f))
    ) {
        Row(
            modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(4.dp)
        ) {
            Icon(icon, contentDescription = null, tint = fg, modifier = Modifier.size(14.dp))
            Text(
                text = role.label.uppercase(),
                style = MaterialTheme.typography.labelMedium,
                color = fg
            )
        }
    }
}

@Composable
fun StatusPulseChip(
    isOnline: Boolean,
    statusLabel: String,
    modifier: Modifier = Modifier
) {
    val bg = if (isOnline) EmeraldContainer else RedstoneContainer.copy(alpha = 0.65f)
    val fg = if (isOnline) EmeraldCore else RedstoneCrimson
    Surface(
        modifier = modifier,
        color = bg,
        shape = MaterialTheme.shapes.small,
        border = BorderStroke(1.dp, fg.copy(alpha = 0.5f))
    ) {
        Row(
            modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(6.dp)
        ) {
            Canvas(modifier = Modifier.size(8.dp)) {
                drawCircle(color = fg)
            }
            Text(
                text = statusLabel,
                style = MaterialTheme.typography.labelMedium,
                color = fg
            )
        }
    }
}

@Composable
fun TpsAndPingChart(
    tpsHistory: List<Float>,
    latencyHistory: List<Int>,
    modifier: Modifier = Modifier
) {
    val lineColor = EmeraldCore
    val pingColor = DiamondCyan
    val gridColor = EtchedBorder.copy(alpha = 0.4f)

    Column(modifier = modifier.fillMaxWidth()) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            Text(
                text = "TPS TELEMETRY",
                style = MaterialTheme.typography.labelSmall,
                color = EmeraldCore
            )
            Text(
                text = "PING MS",
                style = MaterialTheme.typography.labelSmall,
                color = DiamondCyan
            )
        }
        Spacer(modifier = Modifier.height(6.dp))
        Canvas(
            modifier = Modifier
                .fillMaxWidth()
                .height(68.dp)
        ) {
            val w = size.width
            val h = size.height
            drawLine(gridColor, Offset(0f, h * 0.25f), Offset(w, h * 0.25f), strokeWidth = 1f)
            drawLine(gridColor, Offset(0f, h * 0.5f), Offset(w, h * 0.5f), strokeWidth = 1f)
            drawLine(gridColor, Offset(0f, h * 0.75f), Offset(w, h * 0.75f), strokeWidth = 1f)

            val tpsPoints = if (tpsHistory.size >= 2) tpsHistory else listOf(20f, 20f)
            val tpsPath = Path()
            tpsPoints.forEachIndexed { idx, value ->
                val x = (idx.toFloat() / (tpsPoints.size - 1).coerceAtLeast(1)) * w
                val norm = (value.coerceIn(0f, 20f) / 20f)
                val y = h - (norm * (h - 8f)) - 4f
                if (idx == 0) tpsPath.moveTo(x, y) else tpsPath.lineTo(x, y)
            }
            drawPath(tpsPath, color = lineColor, style = Stroke(width = 3.5f, cap = StrokeCap.Round))

            if (latencyHistory.isNotEmpty()) {
                val maxPing = (latencyHistory.maxOrNull() ?: 100).coerceAtLeast(60).toFloat()
                val barCount = latencyHistory.size
                val slotW = w / barCount.coerceAtLeast(1)
                latencyHistory.forEachIndexed { idx, ms ->
                    val barH = ((ms.toFloat() / maxPing) * (h * 0.75f)).coerceAtLeast(4f)
                    val x = idx * slotW + slotW * 0.35f
                    drawLine(
                        color = pingColor.copy(alpha = 0.65f),
                        start = Offset(x, h),
                        end = Offset(x, h - barH),
                        strokeWidth = (slotW * 0.35f).coerceIn(3f, 10f),
                        cap = StrokeCap.Round
                    )
                }
            }
        }
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
fun CreateServerDialog(
    onDismiss: () -> Unit,
    onCreate: (
        name: String,
        motd: String,
        mode: ServerMode,
        software: ServerSoftware,
        version: String,
        host: String,
        port: Long,
        rconPort: Long,
        rconPassword: String,
        seedText: String,
        seedLabel: String
    ) -> Unit
) {
    var name by remember { mutableStateOf("Survival Realm") }
    var motd by remember { mutableStateOf("Free Survival · MCS-HOST") }
    var mode by remember { mutableStateOf(ServerMode.LOCAL_BEDROCK) }
    var software by remember { mutableStateOf(ServerSoftware.BEDROCK_HOST) }
    var version by remember { mutableStateOf("1.21.50") }
    var host by remember { mutableStateOf("play.example.net") }
    var portText by remember { mutableStateOf("19132") }
    var rconPortText by remember { mutableStateOf("25575") }
    var rconPassword by remember { mutableStateOf("") }
    var seedText by remember { mutableStateOf(WorldSeedCatalog.curatedSeeds.first().seedText) }
    var seedLabel by remember { mutableStateOf(WorldSeedCatalog.curatedSeeds.first().title) }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Create Free Server Room", style = MaterialTheme.typography.titleLarge) },
        text = {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .verticalScroll(rememberScrollState()),
                verticalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                Text(
                    text = "Hosting Mode",
                    style = MaterialTheme.typography.labelLarge,
                    color = MaterialTheme.colorScheme.primary
                )
                FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    FilterChip(
                        selected = mode == ServerMode.LOCAL_BEDROCK,
                        onClick = {
                            mode = ServerMode.LOCAL_BEDROCK
                            software = ServerSoftware.BEDROCK_HOST
                            portText = "19132"
                            version = "1.21.50"
                        },
                        label = { Text("On-Device Bedrock") },
                        modifier = Modifier.testTag("mode_local_chip")
                    )
                    FilterChip(
                        selected = mode == ServerMode.REMOTE_RCON,
                        onClick = {
                            mode = ServerMode.REMOTE_RCON
                            software = ServerSoftware.PAPER
                            portText = "25565"
                            version = "1.21.4"
                        },
                        label = { Text("Remote RCON") },
                        modifier = Modifier.testTag("mode_rcon_chip")
                    )
                }

                Text(
                    text = "Server Software",
                    style = MaterialTheme.typography.labelLarge,
                    color = MaterialTheme.colorScheme.secondary
                )
                FlowRow(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    val options = if (mode == ServerMode.LOCAL_BEDROCK) {
                        listOf(ServerSoftware.BEDROCK_HOST, ServerSoftware.BEDROCK_DEDICATED)
                    } else {
                        listOf(
                            ServerSoftware.PAPER,
                            ServerSoftware.PURPUR,
                            ServerSoftware.FABRIC,
                            ServerSoftware.FORGE,
                            ServerSoftware.VANILLA,
                            ServerSoftware.BEDROCK_DEDICATED
                        )
                    }
                    options.forEach { sw ->
                        FilterChip(
                            selected = software == sw,
                            onClick = {
                                software = sw
                                portText = if (sw.edition == GameEdition.BEDROCK) "19132" else "25565"
                            },
                            label = { Text(sw.label) }
                        )
                    }
                }

                OutlinedTextField(
                    value = name,
                    onValueChange = { name = it },
                    label = { Text("Server Name") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth().testTag("create_server_name_input")
                )
                OutlinedTextField(
                    value = motd,
                    onValueChange = { motd = it },
                    label = { Text("MOTD") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth()
                )
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    OutlinedTextField(
                        value = version,
                        onValueChange = { version = it },
                        label = { Text("Version") },
                        singleLine = true,
                        modifier = Modifier.weight(1f)
                    )
                    OutlinedTextField(
                        value = portText,
                        onValueChange = { portText = it.filter(Char::isDigit) },
                        label = { Text("Port") },
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                        singleLine = true,
                        modifier = Modifier.weight(1f)
                    )
                }

                if (mode == ServerMode.REMOTE_RCON) {
                    OutlinedTextField(
                        value = host,
                        onValueChange = { host = it },
                        label = { Text("Server Host / IP") },
                        singleLine = true,
                        modifier = Modifier.fillMaxWidth()
                    )
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
                            value = rconPassword,
                            onValueChange = { rconPassword = it },
                            label = { Text("RCON Password") },
                            visualTransformation = PasswordVisualTransformation(),
                            singleLine = true,
                            modifier = Modifier.weight(1.3f)
                        )
                    }
                }

                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    OutlinedTextField(
                        value = seedText,
                        onValueChange = {
                            seedText = it
                            seedLabel = "Custom Seed"
                        },
                        label = { Text("World Seed") },
                        singleLine = true,
                        modifier = Modifier.weight(1f)
                    )
                    IconButton(
                        onClick = {
                            seedText = Random.nextLong().toString()
                            seedLabel = "Random 64-bit Seed"
                        },
                        modifier = Modifier.size(48.dp)
                    ) {
                        Icon(Icons.Filled.Casino, contentDescription = "Random seed")
                    }
                }
            }
        },
        confirmButton = {
            Button(
                onClick = {
                    onCreate(
                        name,
                        motd,
                        mode,
                        software,
                        version,
                        host,
                        portText.toLongOrNull() ?: 19132L,
                        rconPortText.toLongOrNull() ?: 25575L,
                        rconPassword,
                        seedText,
                        seedLabel
                    )
                },
                modifier = Modifier.testTag("confirm_create_server_button")
            ) {
                Text("Create Room")
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text("Cancel") }
        }
    )
}

@Composable
fun RoomAccessDialog(
    room: ServerRoom,
    currentRole: StaffRole,
    members: List<RoomMember>,
    grants: List<RoleGrant>,
    onGenerateGrant: (StaffRole) -> Unit,
    onRevokeGrant: (String) -> Unit,
    onChangeMemberRole: (RoomMember, StaffRole) -> Unit,
    onRemoveMember: (RoomMember) -> Unit,
    onDismiss: () -> Unit
) {
    val clipboard = LocalClipboardManager.current
    val roomGrants = grants.filter { it.roomId == room.id && it.isActive }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = {
            Column {
                Text("Room Access & Staff", style = MaterialTheme.typography.titleLarge)
                Text(
                    text = "Give trusted friends Admin or Moderator control without you.",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        },
        text = {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .verticalScroll(rememberScrollState()),
                verticalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                if (currentRole == StaffRole.OWNER || currentRole == StaffRole.ADMIN) {
                    Text(
                        text = "Generate Staff Access Code",
                        style = MaterialTheme.typography.labelLarge,
                        color = MaterialTheme.colorScheme.primary
                    )
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        if (currentRole == StaffRole.OWNER) {
                            Button(
                                onClick = { onGenerateGrant(StaffRole.ADMIN) },
                                modifier = Modifier.weight(1f).testTag("gen_admin_code_button"),
                                colors = ButtonDefaults.buttonColors(
                                    containerColor = DiamondContainer,
                                    contentColor = DiamondCyan
                                )
                            ) {
                                Icon(Icons.Filled.PersonAdd, contentDescription = null, modifier = Modifier.size(16.dp))
                                Spacer(Modifier.width(4.dp))
                                Text("Admin Code")
                            }
                        }
                        Button(
                            onClick = { onGenerateGrant(StaffRole.MODERATOR) },
                            modifier = Modifier.weight(1f).testTag("gen_mod_code_button"),
                            colors = ButtonDefaults.buttonColors(
                                containerColor = AmberContainer,
                                contentColor = AmberGold
                            )
                        ) {
                            Icon(Icons.Filled.PersonAdd, contentDescription = null, modifier = Modifier.size(16.dp))
                            Spacer(Modifier.width(4.dp))
                            Text("Mod Code")
                        }
                    }
                }

                if (roomGrants.isNotEmpty()) {
                    Text("Active Invite Tokens", style = MaterialTheme.typography.labelLarge)
                    roomGrants.forEach { grant ->
                        Surface(
                            color = MaterialTheme.colorScheme.surfaceVariant,
                            shape = MaterialTheme.shapes.small,
                            border = BorderStroke(1.dp, EtchedBorder)
                        ) {
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(horizontal = 10.dp, vertical = 6.dp),
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.SpaceBetween
                            ) {
                                Column {
                                    Text(
                                        text = grant.id,
                                        style = MaterialTheme.typography.labelLarge,
                                        color = EmeraldCore
                                    )
                                    Text(
                                        text = "${grant.assignedRole} · ${grant.usedCount}/${grant.maxUses} used",
                                        style = MaterialTheme.typography.bodySmall,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant
                                    )
                                }
                                Row {
                                    IconButton(
                                        onClick = { clipboard.setText(AnnotatedString(grant.id)) },
                                        modifier = Modifier.size(48.dp)
                                    ) {
                                        Icon(Icons.Filled.ContentCopy, contentDescription = "Copy role code")
                                    }
                                    if (currentRole == StaffRole.OWNER) {
                                        IconButton(
                                            onClick = { onRevokeGrant(grant.id) },
                                            modifier = Modifier.size(48.dp)
                                        ) {
                                            Icon(Icons.Filled.Delete, contentDescription = "Revoke code", tint = RedstoneCrimson)
                                        }
                                    }
                                }
                            }
                        }
                    }
                }

                HorizontalDivider(color = EtchedBorder)
                Text("Room Staff (${members.size})", style = MaterialTheme.typography.labelLarge)
                members.forEach { member ->
                    val memberRole = StaffRole.entries.firstOrNull { it.name == member.role } ?: StaffRole.MODERATOR
                    Surface(
                        color = MaterialTheme.colorScheme.surfaceVariant,
                        shape = MaterialTheme.shapes.small,
                        border = BorderStroke(1.dp, EtchedBorder)
                    ) {
                        Column(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(10.dp),
                            verticalArrangement = Arrangement.spacedBy(6.dp)
                        ) {
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.SpaceBetween
                            ) {
                                Text(
                                    text = member.displayName,
                                    style = MaterialTheme.typography.titleSmall,
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis,
                                    modifier = Modifier.weight(1f)
                                )
                                RoleBadge(role = memberRole)
                            }
                            if (currentRole == StaffRole.OWNER && memberRole != StaffRole.OWNER) {
                                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                                    val nextRole = if (memberRole == StaffRole.ADMIN) StaffRole.MODERATOR else StaffRole.ADMIN
                                    OutlinedButton(
                                        onClick = { onChangeMemberRole(member, nextRole) },
                                        modifier = Modifier.weight(1f)
                                    ) {
                                        Text("Make ${nextRole.label}")
                                    }
                                    OutlinedButton(
                                        onClick = { onRemoveMember(member) },
                                        colors = ButtonDefaults.outlinedButtonColors(contentColor = RedstoneCrimson)
                                    ) {
                                        Text("Revoke")
                                    }
                                }
                            }
                        }
                    }
                }
            }
        },
        confirmButton = {
            TextButton(onClick = onDismiss) { Text("Done") }
        }
    )
}
