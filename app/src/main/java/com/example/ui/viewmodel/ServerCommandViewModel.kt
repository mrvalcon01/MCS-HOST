package com.example.ui.viewmodel

import android.content.Context
import android.content.Intent
import android.net.Uri
import android.provider.OpenableColumns
import android.util.Log
import androidx.core.net.toUri
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.data.firebase.ServerRoomRepository
import com.example.data.model.DifficultyOption
import com.example.data.model.GameEdition
import com.example.data.model.GameModeOption
import com.example.data.model.JoinedServer
import com.example.data.model.PackSource
import com.example.data.model.PackType
import com.example.data.model.RoleGrant
import com.example.data.model.RoomCommand
import com.example.data.model.RoomMember
import com.example.data.model.RoomPack
import com.example.data.model.RoomRconConfig
import com.example.data.model.ServerInvite
import com.example.data.model.ServerMode
import com.example.data.model.ServerRoom
import com.example.data.model.ServerSoftware
import com.example.data.model.StaffRole
import com.example.data.model.WorldBackup
import com.example.data.model.WorldSeedCatalog
import com.example.data.network.HostEngineController
import com.example.data.network.HostRuntimeState
import com.example.data.network.HostStatus
import com.example.data.network.LogSeverity
import com.example.data.network.ModrinthApiService
import com.example.data.network.ModrinthCatalogRepository
import com.example.data.network.ModrinthDownloadStatus
import com.example.data.network.ModrinthProjectHit
import com.example.data.network.ModrinthSortOption
import com.example.data.network.RconClient
import com.example.data.network.ServerPingResult
import com.example.data.network.ServerStatusPinger
import com.google.firebase.firestore.FirebaseFirestoreException
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.security.MessageDigest

sealed interface UiState<out T> {
    data object Loading : UiState<Nothing>
    data class Success<T>(val data: T) : UiState<T>
    data class Error(val message: String) : UiState<Nothing>
}

@OptIn(ExperimentalCoroutinesApi::class)
class ServerCommandViewModel(
    private val repository: ServerRoomRepository,
    private val currentUserId: String,
    private val modrinthApi: ModrinthApiService = ModrinthApiService.create(),
    private val modrinthCatalog: ModrinthCatalogRepository = ModrinthCatalogRepository(modrinthApi),
    private val rconClient: RconClient = RconClient()
) : ViewModel() {

    val hostRuntime: StateFlow<HostRuntimeState> = HostEngineController.state

    private val _selectedRoomId = MutableStateFlow<String?>(null)
    val selectedRoomId: StateFlow<String?> = _selectedRoomId.asStateFlow()

    private val _rconConfig = MutableStateFlow<RoomRconConfig?>(null)
    val rconConfig: StateFlow<RoomRconConfig?> = _rconConfig.asStateFlow()

    private val _rconConnected = MutableStateFlow(false)
    val rconConnected: StateFlow<Boolean> = _rconConnected.asStateFlow()

    private val _remotePing = MutableStateFlow<ServerPingResult?>(null)
    val remotePing: StateFlow<ServerPingResult?> = _remotePing.asStateFlow()

    private val _modrinthResults = MutableStateFlow<List<ModrinthProjectHit>>(emptyList())
    val modrinthResults: StateFlow<List<ModrinthProjectHit>> = _modrinthResults.asStateFlow()

    private val _modrinthSearching = MutableStateFlow(false)
    val modrinthSearching: StateFlow<Boolean> = _modrinthSearching.asStateFlow()

    private val _modrinthBedrockOnly = MutableStateFlow(true)
    val modrinthBedrockOnly: StateFlow<Boolean> = _modrinthBedrockOnly.asStateFlow()

    private val _modrinthSort = MutableStateFlow(ModrinthSortOption.DOWNLOADS)
    val modrinthSort: StateFlow<ModrinthSortOption> = _modrinthSort.asStateFlow()

    private val _modrinthFromCache = MutableStateFlow(false)
    val modrinthFromCache: StateFlow<Boolean> = _modrinthFromCache.asStateFlow()

    private val _modrinthDownloads = MutableStateFlow<Map<String, ModrinthDownloadStatus>>(emptyMap())
    val modrinthDownloads: StateFlow<Map<String, ModrinthDownloadStatus>> = _modrinthDownloads.asStateFlow()

    private val _lookedUpInvite = MutableStateFlow<ServerInvite?>(null)
    val lookedUpInvite: StateFlow<ServerInvite?> = _lookedUpInvite.asStateFlow()

    private val _lookedUpPing = MutableStateFlow<ServerPingResult?>(null)
    val lookedUpPing: StateFlow<ServerPingResult?> = _lookedUpPing.asStateFlow()

    private val _isBusy = MutableStateFlow(false)
    val isBusy: StateFlow<Boolean> = _isBusy.asStateFlow()

    private val _snackbarMessage = MutableStateFlow<String?>(null)
    val snackbarMessage: StateFlow<String?> = _snackbarMessage.asStateFlow()

    val roomsState: StateFlow<UiState<List<ServerRoom>>> = repository.observeMyServerRooms()
        .map<List<ServerRoom>, UiState<List<ServerRoom>>> { rooms -> UiState.Success(rooms) }
        .catch { error ->
            val root = unwrapFirestoreError(error) ?: error
            Log.w(TAG, "Error observing serverRooms", root)
            emit(UiState.Error(root.message ?: "Failed to load Server Rooms"))
        }
        .stateIn(
            scope = viewModelScope,
            started = SharingStarted.WhileSubscribed(STOP_TIMEOUT_MS),
            initialValue = UiState.Loading
        )

    val activeRoom: StateFlow<ServerRoom?> = combine(roomsState, _selectedRoomId) { state, selectedId ->
        val rooms = (state as? UiState.Success)?.data.orEmpty()
        rooms.firstOrNull { it.id == selectedId } ?: rooms.firstOrNull()
    }.stateIn(
        scope = viewModelScope,
        started = SharingStarted.WhileSubscribed(STOP_TIMEOUT_MS),
        initialValue = null
    )

    val currentRole: StateFlow<StaffRole> = activeRoom.map { room ->
        room?.roleFor(currentUserId) ?: StaffRole.MODERATOR
    }.stateIn(
        scope = viewModelScope,
        started = SharingStarted.WhileSubscribed(STOP_TIMEOUT_MS),
        initialValue = StaffRole.OWNER
    )

    val roomMembers: StateFlow<List<RoomMember>> = activeRoom
        .flatMapLatest { room ->
            if (room == null) flowOf(emptyList())
            else repository.observeRoomMembers(room.id).safeListCatch("members")
        }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(STOP_TIMEOUT_MS), emptyList())

    val roomPacks: StateFlow<List<RoomPack>> = activeRoom
        .flatMapLatest { room ->
            if (room == null) flowOf(emptyList())
            else repository.observeRoomPacks(room.id).safeListCatch("packs")
        }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(STOP_TIMEOUT_MS), emptyList())

    val roomBackups: StateFlow<List<WorldBackup>> = activeRoom
        .flatMapLatest { room ->
            if (room == null) flowOf(emptyList())
            else repository.observeRoomBackups(room.id).safeListCatch("backups")
        }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(STOP_TIMEOUT_MS), emptyList())

    val roomCommands: StateFlow<List<RoomCommand>> = activeRoom
        .flatMapLatest { room ->
            if (room == null) flowOf(emptyList())
            else repository.observeRoomCommands(room.id).safeListCatch("commands")
        }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(STOP_TIMEOUT_MS), emptyList())

    val myRoleGrants: StateFlow<List<RoleGrant>> = repository.observeMyRoleGrants()
        .safeListCatch("roleGrants")
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(STOP_TIMEOUT_MS), emptyList())

    val publicInvites: StateFlow<List<ServerInvite>> = repository.observePublicDirectoryInvites()
        .safeListCatch("publicInvites")
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(STOP_TIMEOUT_MS), emptyList())

    val joinedServers: StateFlow<List<JoinedServer>> = repository.observeJoinedServers()
        .safeListCatch("joinedServers")
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(STOP_TIMEOUT_MS), emptyList())

    init {
        viewModelScope.launch {
            activeRoom.collect { room ->
                if (room != null) {
                    HostEngineController.syncRoomConfig(room)
                    val role = room.roleFor(currentUserId)
                    if (role == StaffRole.OWNER || role == StaffRole.ADMIN) {
                        _rconConfig.value = repository.getRconConfig(room.id).getOrNull()
                    } else {
                        _rconConfig.value = null
                    }
                } else {
                    _rconConfig.value = null
                }
            }
        }

        // Process delegated PENDING commands from Admins/Moderators when this device is running the host
        viewModelScope.launch {
            combine(activeRoom, roomCommands, hostRuntime) { room, cmds, runtime ->
                Triple(room, cmds, runtime)
            }.collect { (room, cmds, runtime) ->
                if (room != null &&
                    runtime.status == HostStatus.RUNNING &&
                    runtime.activeRoomId == room.id &&
                    (room.roleFor(currentUserId) == StaffRole.OWNER || room.roleFor(currentUserId) == StaffRole.ADMIN)
                ) {
                    cmds.filter { it.status == "PENDING" }.forEach { pending ->
                        val response = HostEngineController.executeLocalHostCommand(
                            pending.command,
                            "${pending.senderName} (${pending.senderRole})"
                        )
                        repository.resolveRoomCommand(room.id, pending.id, "EXECUTED", response)
                    }
                }
            }
        }

        // Preload popular free Modrinth Minecraft Mobile / Bedrock packs on init
        searchModrinth("", PackType.RESOURCE_PACK, "minecraft", bedrockOnly = true, sortOption = ModrinthSortOption.DOWNLOADS)
    }

    fun clearSnackbar() {
        _snackbarMessage.value = null
    }

    fun selectRoom(roomId: String) {
        _selectedRoomId.value = roomId
        _remotePing.value = null
        viewModelScope.launch {
            if (_rconConnected.value) {
                rconClient.disconnect()
                _rconConnected.value = false
            }
        }
    }

    fun createServerRoom(
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
    ) {
        viewModelScope.launch {
            _isBusy.value = true
            val numericSeed = WorldSeedCatalog.resolveNumericSeed(seedText)
            val newRoom = ServerRoom(
                name = name.ifBlank { "My Minecraft Server" },
                motd = motd.ifBlank { "Hosted free with MCS-HOST" },
                mode = mode.name,
                edition = software.edition.name,
                software = software.name,
                minecraftVersion = version.ifBlank { if (software.edition == GameEdition.BEDROCK) "1.21.50" else "1.21.4" },
                host = if (mode == ServerMode.LOCAL_BEDROCK) ServerStatusPinger.resolveLocalIpv4Address() else host.ifBlank { "127.0.0.1" },
                port = port,
                seed = seedText.ifBlank { numericSeed.toString() },
                seedNumeric = numericSeed,
                seedLabel = seedLabel.ifBlank { "Custom World Seed" }
            )
            repository.createServerRoom(newRoom, rconPort, rconPassword)
                .onSuccess { newId ->
                    _selectedRoomId.value = newId
                    _snackbarMessage.value = "Server Room \"${newRoom.name}\" created."
                }
                .onFailure {
                    _snackbarMessage.value = it.localizedMessage ?: "Failed to create Server Room."
                }
            _isBusy.value = false
        }
    }

    fun toggleLocalHost(context: Context) {
        val room = activeRoom.value ?: return
        val role = currentRole.value
        if (role == StaffRole.MODERATOR) {
            _snackbarMessage.value = "Only Owner or Admin can start/stop the host."
            return
        }
        val runtime = hostRuntime.value
        if (runtime.status == HostStatus.RUNNING || runtime.status == HostStatus.STARTING) {
            HostEngineController.stopLocalHost(context)
            viewModelScope.launch {
                repository.updateRoomRuntimeStatus(room.id, isOnline = false, playerCount = 0L, inviteCode = room.inviteCode)
            }
        } else {
            HostEngineController.startLocalHost(context, room)
            val localIp = ServerStatusPinger.resolveLocalIpv4Address()
            viewModelScope.launch {
                repository.updateRoomRuntimeStatus(
                    roomId = room.id,
                    isOnline = true,
                    playerCount = runtime.activePeers.size.toLong(),
                    hostAddress = localIp,
                    inviteCode = room.inviteCode
                )
            }
        }
    }

    fun toggleRconConnection() {
        val room = activeRoom.value ?: return
        val role = currentRole.value
        if (role == StaffRole.MODERATOR) {
            _snackbarMessage.value = "Moderators cannot access RCON credentials directly."
            return
        }
        viewModelScope.launch {
            if (_rconConnected.value) {
                rconClient.disconnect()
                _rconConnected.value = false
                HostEngineController.appendLog(LogSeverity.RCON, "RCON", "Disconnected from ${room.host}")
                repository.updateRoomRuntimeStatus(room.id, isOnline = false, playerCount = 0L, inviteCode = room.inviteCode)
                return@launch
            }
            val cfg = _rconConfig.value ?: repository.getRconConfig(room.id).getOrNull()
            if (cfg == null) {
                _snackbarMessage.value = "Configure RCON port & password in Server Options first."
                return@launch
            }
            _isBusy.value = true
            HostEngineController.appendLog(LogSeverity.RCON, "RCON", "Connecting to ${room.host}:${cfg.rconPort}...")
            rconClient.connect(room.host, cfg.rconPort.toInt(), cfg.rconPassword)
                .onSuccess { msg ->
                    _rconConnected.value = true
                    HostEngineController.appendLog(LogSeverity.RCON, "RCON", msg)
                    _snackbarMessage.value = "RCON connected."
                    pingActiveServer()
                }
                .onFailure { err ->
                    _rconConnected.value = false
                    HostEngineController.appendLog(LogSeverity.ERROR, "RCON", err.message ?: "RCON connection failed")
                    _snackbarMessage.value = err.message ?: "RCON connection failed."
                }
            _isBusy.value = false
        }
    }

    fun pingActiveServer() {
        val room = activeRoom.value ?: return
        viewModelScope.launch {
            val targetHost = if (room.isLocalHost() && hostRuntime.value.status == HostStatus.RUNNING) {
                "127.0.0.1"
            } else {
                room.host
            }
            val targetPort = if (room.isLocalHost() && hostRuntime.value.status == HostStatus.RUNNING) {
                hostRuntime.value.boundPort
            } else {
                room.port.toInt()
            }
            val result = ServerStatusPinger.pingServer(targetHost, targetPort, room.editionEnum())
            _remotePing.value = result
            if (result.isOnline) {
                HostEngineController.recordRemotePing(result.latencyMs, result.onlinePlayers)
                HostEngineController.appendLog(
                    LogSeverity.INFO,
                    "PING",
                    "${room.editionEnum().label} ${targetHost}:$targetPort responded in ${result.latencyMs}ms (${result.onlinePlayers}/${result.maxPlayers} players)"
                )
                repository.updateRoomRuntimeStatus(
                    roomId = room.id,
                    isOnline = true,
                    playerCount = result.onlinePlayers.toLong(),
                    inviteCode = room.inviteCode
                )
            } else {
                HostEngineController.appendLog(
                    LogSeverity.WARN,
                    "PING",
                    "Ping $targetHost:$targetPort unreachable: ${result.errorMessage}"
                )
            }
        }
    }

    fun sendConsoleCommand(commandInput: String) {
        val room = activeRoom.value ?: return
        val cmd = commandInput.trim()
        if (cmd.isBlank()) return
        val role = currentRole.value

        HostEngineController.appendLog(LogSeverity.INFO, role.name, "> $cmd")
        viewModelScope.launch {
            when {
                _rconConnected.value && (role == StaffRole.OWNER || role == StaffRole.ADMIN) -> {
                    rconClient.executeCommand(cmd)
                        .onSuccess { out ->
                            HostEngineController.appendLog(LogSeverity.RCON, "RCON", out)
                        }
                        .onFailure { err ->
                            HostEngineController.appendLog(LogSeverity.ERROR, "RCON", err.message ?: "RCON command error")
                        }
                }
                room.isLocalHost() && hostRuntime.value.status == HostStatus.RUNNING -> {
                    HostEngineController.executeLocalHostCommand(cmd, role.label)
                }
                else -> {
                    // Queue into Firestore RoomCommand relay so active host or staff handles it
                    repository.dispatchRoomCommand(room.id, role, cmd)
                        .onSuccess {
                            HostEngineController.appendLog(
                                LogSeverity.INFO,
                                "RELAY",
                                "Command queued to Server Room relay."
                            )
                        }
                        .onFailure {
                            _snackbarMessage.value = it.localizedMessage ?: "Failed to queue command."
                        }
                }
            }
        }
    }

    fun updateRconCredentials(rconPort: Long, rconPassword: String) {
        val room = activeRoom.value ?: return
        if (currentRole.value == StaffRole.MODERATOR) {
            _snackbarMessage.value = "Only Owner or Admin can edit RCON settings."
            return
        }
        viewModelScope.launch {
            repository.saveRconConfig(room.id, room.ownerUid, rconPort, rconPassword)
                .onSuccess {
                    _rconConfig.value = RoomRconConfig(
                        ownerUid = room.ownerUid,
                        rconPort = rconPort,
                        rconPassword = rconPassword
                    )
                    _snackbarMessage.value = "RCON credentials saved."
                }
                .onFailure { _snackbarMessage.value = it.localizedMessage }
        }
    }

    fun updateWorldAndOptions(updated: ServerRoom) {
        if (currentRole.value == StaffRole.MODERATOR) {
            _snackbarMessage.value = "Only Owner or Admin can modify world options."
            return
        }
        viewModelScope.launch {
            _isBusy.value = true
            repository.updateServerRoomSettings(updated)
                .onSuccess {
                    HostEngineController.syncRoomConfig(updated)
                    if (_rconConnected.value) {
                        rconClient.executeCommand("difficulty ${updated.difficultyEnum().commandValue}")
                        rconClient.executeCommand("gamerule keepInventory ${updated.keepInventory}")
                        rconClient.executeCommand("gamerule mobGriefing ${updated.mobGriefing}")
                        rconClient.executeCommand("gamerule doDaylightCycle ${updated.doDaylightCycle}")
                        rconClient.executeCommand("gamerule doWeatherCycle ${updated.doWeatherCycle}")
                        rconClient.executeCommand("gamerule naturalRegeneration ${updated.naturalRegeneration}")
                    }
                    _snackbarMessage.value = "World options & seed synced."
                }
                .onFailure { _snackbarMessage.value = it.localizedMessage ?: "Failed to save options." }
            _isBusy.value = false
        }
    }

    fun applyWorldSeed(seedText: String, seedLabel: String) {
        val room = activeRoom.value ?: return
        val numeric = WorldSeedCatalog.resolveNumericSeed(seedText)
        val updated = room.copy(
            seed = seedText.trim().ifBlank { numeric.toString() },
            seedNumeric = numeric,
            seedLabel = seedLabel.ifBlank { "Custom Seed" }
        )
        updateWorldAndOptions(updated)
        HostEngineController.appendLog(
            LogSeverity.INFO,
            "SEED",
            "World seed updated -> ${updated.seedLabel} ($numeric)"
        )
    }

    fun createWorldSnapshot(snapshotName: String) {
        val room = activeRoom.value ?: return
        if (currentRole.value == StaffRole.MODERATOR) {
            _snackbarMessage.value = "Only Owner or Admin can create world snapshots."
            return
        }
        viewModelScope.launch {
            repository.createWorldBackup(room, snapshotName, roomPacks.value.size)
                .onSuccess { _snackbarMessage.value = "Snapshot \"$snapshotName\" saved." }
                .onFailure { _snackbarMessage.value = it.localizedMessage }
        }
    }

    fun restoreWorldSnapshot(backup: WorldBackup) {
        val room = activeRoom.value ?: return
        if (currentRole.value == StaffRole.MODERATOR) {
            _snackbarMessage.value = "Only Owner or Admin can restore snapshots."
            return
        }
        val restored = room.copy(
            seed = backup.seed,
            seedNumeric = backup.seedNumeric,
            seedLabel = "Restored: ${backup.name}",
            minecraftVersion = backup.minecraftVersion,
            software = backup.software,
            gamemode = backup.gamemode,
            difficulty = backup.difficulty
        )
        updateWorldAndOptions(restored)
    }

    // --- Player Moderation (Owner, Admin & Moderator) ---

    fun moderatePlayer(action: String, playerName: String) {
        val room = activeRoom.value ?: return
        val cleanName = playerName.trim()
        if (cleanName.isBlank()) return
        val role = currentRole.value

        viewModelScope.launch {
            when (action) {
                "KICK" -> {
                    sendConsoleCommand("/kick $cleanName")
                    _snackbarMessage.value = "Kicked $cleanName."
                }
                "BAN" -> {
                    val nextBanned = (room.bannedPlayers + cleanName).distinct()
                    val nextWhitelist = room.whitelist.filterNot { it.equals(cleanName, ignoreCase = true) }
                    repository.updateModerationLists(room.id, nextWhitelist, nextBanned)
                        .onSuccess {
                            sendConsoleCommand("/ban $cleanName")
                            _snackbarMessage.value = "Banned $cleanName."
                        }
                        .onFailure { _snackbarMessage.value = it.localizedMessage }
                }
                "PARDON" -> {
                    val nextBanned = room.bannedPlayers.filterNot { it.equals(cleanName, ignoreCase = true) }
                    repository.updateModerationLists(room.id, room.whitelist, nextBanned)
                        .onSuccess {
                            sendConsoleCommand("/pardon $cleanName")
                            _snackbarMessage.value = "Unbanned $cleanName."
                        }
                        .onFailure { _snackbarMessage.value = it.localizedMessage }
                }
                "WHITELIST_ADD" -> {
                    val nextWhitelist = (room.whitelist + cleanName).distinct()
                    repository.updateModerationLists(room.id, nextWhitelist, room.bannedPlayers)
                        .onSuccess {
                            sendConsoleCommand("/whitelist add $cleanName")
                            _snackbarMessage.value = "Added $cleanName to whitelist."
                        }
                        .onFailure { _snackbarMessage.value = it.localizedMessage }
                }
                "WHITELIST_REMOVE" -> {
                    val nextWhitelist = room.whitelist.filterNot { it.equals(cleanName, ignoreCase = true) }
                    repository.updateModerationLists(room.id, nextWhitelist, room.bannedPlayers)
                        .onSuccess {
                            sendConsoleCommand("/whitelist remove $cleanName")
                            _snackbarMessage.value = "Removed $cleanName from whitelist."
                        }
                        .onFailure { _snackbarMessage.value = it.localizedMessage }
                }
                "OP_TOGGLE" -> {
                    if (role == StaffRole.MODERATOR) {
                        _snackbarMessage.value = "Only Owner or Admin can grant/revoke Operator."
                        return@launch
                    }
                    val isCurrentlyOp = room.operators.any { it.equals(cleanName, ignoreCase = true) }
                    val nextOps = if (isCurrentlyOp) {
                        room.operators.filterNot { it.equals(cleanName, ignoreCase = true) }
                    } else {
                        (room.operators + cleanName).distinct()
                    }
                    repository.updateModerationLists(room.id, room.whitelist, room.bannedPlayers, nextOps)
                        .onSuccess {
                            sendConsoleCommand(if (isCurrentlyOp) "/deop $cleanName" else "/op $cleanName")
                            _snackbarMessage.value = if (isCurrentlyOp) "De-opped $cleanName." else "Opped $cleanName."
                        }
                        .onFailure { _snackbarMessage.value = it.localizedMessage }
                }
            }
        }
    }

    // --- Mods & Resource Packs (Owner, Admin & Moderator) ---

    fun searchModrinth(
        query: String,
        packType: PackType,
        loader: String,
        bedrockOnly: Boolean = _modrinthBedrockOnly.value,
        sortOption: ModrinthSortOption = _modrinthSort.value,
        forceRefresh: Boolean = false
    ) {
        viewModelScope.launch {
            _modrinthSearching.value = true
            runCatching {
                modrinthCatalog.searchCatalog(
                    query = query,
                    packType = packType,
                    loader = loader,
                    bedrockOnly = bedrockOnly,
                    sortOption = sortOption,
                    forceRefresh = forceRefresh
                )
            }.onSuccess { result ->
                _modrinthResults.value = result.hits
                _modrinthFromCache.value = result.fromCache
            }.onFailure { err ->
                Log.w(TAG, "Modrinth search failed", err)
                _snackbarMessage.value = "Catalog search error: ${err.localizedMessage}"
            }
            _modrinthSearching.value = false
        }
    }

    fun setModrinthBedrockOnly(enabled: Boolean, query: String, packType: PackType, loader: String) {
        _modrinthBedrockOnly.value = enabled
        searchModrinth(
            query = query,
            packType = packType,
            loader = loader,
            bedrockOnly = enabled,
            sortOption = _modrinthSort.value
        )
    }

    fun setModrinthSort(sortOption: ModrinthSortOption, query: String, packType: PackType, loader: String) {
        _modrinthSort.value = sortOption
        searchModrinth(
            query = query,
            packType = packType,
            loader = loader,
            bedrockOnly = _modrinthBedrockOnly.value,
            sortOption = sortOption
        )
    }

    fun downloadModrinthPack(context: Context, hit: ModrinthProjectHit) {
        val currentStatus = _modrinthDownloads.value[hit.projectId]
        if (currentStatus is ModrinthDownloadStatus.Resolving ||
            currentStatus is ModrinthDownloadStatus.Downloading
        ) {
            return
        }
        viewModelScope.launch {
            runCatching {
                modrinthCatalog.downloadMobilePackLocally(context, hit) { status ->
                    _modrinthDownloads.value = _modrinthDownloads.value + (hit.projectId to status)
                }
            }.onSuccess { completed ->
                _modrinthDownloads.value = _modrinthDownloads.value + (hit.projectId to completed)
                val checkLabel = if (completed.verifiedByModrinthHash) "SHA-verified" else "saved"
                HostEngineController.appendLog(
                    LogSeverity.INFO,
                    "MODRINTH",
                    "Downloaded ${completed.fileName} ($checkLabel)"
                )
                _snackbarMessage.value = "Saved ${completed.fileName} to Downloads."
            }.onFailure { err ->
                val message = err.localizedMessage ?: "Download failed"
                _modrinthDownloads.value = _modrinthDownloads.value + (
                    hit.projectId to ModrinthDownloadStatus.Failed(message)
                )
                HostEngineController.appendLog(LogSeverity.ERROR, "MODRINTH", message)
                _snackbarMessage.value = message
            }
        }
    }

    fun openDownloadedPackInMinecraft(context: Context, status: ModrinthDownloadStatus.Completed) {
        val uri = status.contentUriString.toUri()
        val viewIntent = ModrinthCatalogRepository.buildOpenInMinecraftIntent(uri, status.fileName)
        runCatching {
            val minecraftIntent = Intent(viewIntent).apply { setPackage("com.mojang.minecraftpe") }
            if (minecraftIntent.resolveActivity(context.packageManager) != null) {
                context.startActivity(minecraftIntent)
            } else {
                context.startActivity(Intent.createChooser(viewIntent, "Open ${status.fileName} with Minecraft").apply {
                    addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                })
            }
        }.onFailure {
            _snackbarMessage.value = "Saved in Downloads/MCS-HOST/${status.fileName}. Open via your Files app if Minecraft isn't installed."
        }
    }

    fun installModrinthPack(hit: ModrinthProjectHit, packType: PackType) {
        val room = activeRoom.value ?: return
        viewModelScope.launch {
            _isBusy.value = true
            val resolvedAsset = runCatching {
                modrinthCatalog.resolvePreferredMobilePackAsset(hit.projectId)
            }.getOrNull()
            val latestVersion = resolvedAsset?.version
                ?: runCatching { modrinthCatalog.getProjectVersionsCached(hit.projectId).firstOrNull() }.getOrNull()
            val primaryFile = resolvedAsset?.file
                ?: latestVersion?.files?.firstOrNull { it.primary }
                ?: latestVersion?.files?.firstOrNull()

            val pack = RoomPack(
                title = hit.title,
                packType = packType.name,
                source = PackSource.MODRINTH.name,
                modrinthProjectId = hit.projectId,
                versionNumber = latestVersion?.versionNumber ?: "latest",
                downloadUrl = primaryFile?.url.orEmpty(),
                iconUrl = hit.iconUrl.orEmpty(),
                fileName = primaryFile?.filename.orEmpty(),
                sha256 = primaryFile?.hashes?.get("sha512")?.take(64) ?: primaryFile?.hashes?.get("sha256").orEmpty(),
                loader = latestVersion?.loaders?.firstOrNull() ?: room.softwareEnum().modrinthLoader,
                isEnabled = true,
                isRequired = packType == PackType.RESOURCE_PACK,
                priority = (roomPacks.value.size + 1).toLong()
            )
            repository.addRoomPack(room.id, pack)
                .onSuccess {
                    HostEngineController.appendLog(LogSeverity.INFO, "PACK", "Added ${pack.title} (${pack.versionNumber}) to room.")
                    _snackbarMessage.value = "Added ${pack.title} to Server Room."
                }
                .onFailure { _snackbarMessage.value = it.localizedMessage }
            _isBusy.value = false
        }
    }

    fun importLocalPackUri(context: Context, uri: Uri, packType: PackType) {
        val room = activeRoom.value ?: return
        viewModelScope.launch {
            val metadata = withContext(Dispatchers.IO) {
                var displayName = "custom_pack.mcpack"
                context.contentResolver.query(uri, null, null, null, null)?.use { cursor ->
                    val idx = cursor.getColumnIndex(OpenableColumns.DISPLAY_NAME)
                    if (idx >= 0 && cursor.moveToFirst()) {
                        displayName = cursor.getString(idx) ?: displayName
                    }
                }
                val digest = MessageDigest.getInstance("SHA-256")
                context.contentResolver.openInputStream(uri)?.use { stream ->
                    val buf = ByteArray(8192)
                    var read: Int
                    while (stream.read(buf).also { read = it } > 0) {
                        digest.update(buf, 0, read)
                    }
                }
                val sha = digest.digest().joinToString("") { "%02x".format(it) }
                displayName to sha
            }
            val cleanTitle = metadata.first.substringBeforeLast('.').ifBlank { metadata.first }
            val pack = RoomPack(
                title = cleanTitle,
                packType = packType.name,
                source = PackSource.LOCAL_FILE.name,
                fileName = metadata.first,
                sha256 = metadata.second,
                loader = room.softwareEnum().modrinthLoader,
                isEnabled = true,
                isRequired = packType == PackType.RESOURCE_PACK,
                priority = (roomPacks.value.size + 1).toLong()
            )
            repository.addRoomPack(room.id, pack)
                .onSuccess {
                    HostEngineController.appendLog(LogSeverity.INFO, "PACK", "Imported local pack ${metadata.first}")
                    _snackbarMessage.value = "Imported ${metadata.first}."
                }
                .onFailure { _snackbarMessage.value = it.localizedMessage }
        }
    }

    fun togglePackEnabled(pack: RoomPack) {
        val room = activeRoom.value ?: return
        viewModelScope.launch {
            repository.updateRoomPackFlags(
                roomId = room.id,
                packId = pack.id,
                isEnabled = !pack.isEnabled,
                isRequired = pack.isRequired,
                priority = pack.priority
            ).onFailure { _snackbarMessage.value = it.localizedMessage }
        }
    }

    fun togglePackRequired(pack: RoomPack) {
        val room = activeRoom.value ?: return
        viewModelScope.launch {
            repository.updateRoomPackFlags(
                roomId = room.id,
                packId = pack.id,
                isEnabled = pack.isEnabled,
                isRequired = !pack.isRequired,
                priority = pack.priority
            ).onFailure { _snackbarMessage.value = it.localizedMessage }
        }
    }

    fun removePack(pack: RoomPack) {
        val room = activeRoom.value ?: return
        viewModelScope.launch {
            repository.deleteRoomPack(room.id, pack.id)
                .onSuccess { _snackbarMessage.value = "Removed ${pack.title}." }
                .onFailure { _snackbarMessage.value = it.localizedMessage }
        }
    }

    // --- Staff Delegation & Cloud Invites ---

    fun createRoleAccessCode(role: StaffRole, maxUses: Long = 5L) {
        val room = activeRoom.value ?: return
        viewModelScope.launch {
            repository.createRoleGrant(room, role, maxUses)
                .onSuccess { code ->
                    _snackbarMessage.value = "Generated ${role.label} access code: $code"
                }
                .onFailure { _snackbarMessage.value = it.localizedMessage }
        }
    }

    fun revokeRoleAccessCode(grantCode: String) {
        viewModelScope.launch {
            repository.revokeRoleGrant(grantCode)
                .onSuccess { _snackbarMessage.value = "Access code $grantCode revoked." }
                .onFailure { _snackbarMessage.value = it.localizedMessage }
        }
    }

    fun joinRoomByRoleCode(code: String) {
        viewModelScope.launch {
            _isBusy.value = true
            repository.claimRoleGrant(code)
                .onSuccess { roomId ->
                    _selectedRoomId.value = roomId
                    _snackbarMessage.value = "Joined Server Room with delegated staff access!"
                }
                .onFailure { _snackbarMessage.value = it.message ?: "Could not claim role code." }
            _isBusy.value = false
        }
    }

    fun changeMemberRole(member: RoomMember, newRole: StaffRole) {
        val room = activeRoom.value ?: return
        viewModelScope.launch {
            repository.updateMemberRole(room, member.memberUid, newRole)
                .onSuccess { _snackbarMessage.value = "${member.displayName} is now ${newRole.label}." }
                .onFailure { _snackbarMessage.value = it.localizedMessage }
        }
    }

    fun revokeMember(member: RoomMember) {
        val room = activeRoom.value ?: return
        viewModelScope.launch {
            repository.removeRoomMember(room, member.memberUid)
                .onSuccess { _snackbarMessage.value = "Revoked access for ${member.displayName}." }
                .onFailure { _snackbarMessage.value = it.localizedMessage }
        }
    }

    fun publishToDirectory(isPublic: Boolean) {
        val room = activeRoom.value ?: return
        val hostAddr = if (room.isLocalHost()) hostRuntime.value.localIpAddress else room.host
        val isOnline = if (room.isLocalHost()) hostRuntime.value.status == HostStatus.RUNNING else _rconConnected.value || room.isOnline
        val count = if (room.isLocalHost()) hostRuntime.value.activePeers.size.toLong() else room.playerCount
        viewModelScope.launch {
            repository.publishServerInvite(room, hostAddr, isPublic, isOnline, count)
                .onSuccess { code ->
                    _snackbarMessage.value = if (isPublic) "Published to directory! Code: $code" else "Invite code ready: $code"
                }
                .onFailure { _snackbarMessage.value = it.localizedMessage }
        }
    }

    fun lookupFriendInvite(code: String) {
        viewModelScope.launch {
            _isBusy.value = true
            _lookedUpPing.value = null
            repository.lookupInviteByCode(code)
                .onSuccess { invite ->
                    _lookedUpInvite.value = invite
                    if (invite == null) {
                        _snackbarMessage.value = "No server found for code \"$code\"."
                    } else {
                        val edition = GameEdition.entries.firstOrNull { it.name == invite.edition } ?: GameEdition.BEDROCK
                        _lookedUpPing.value = ServerStatusPinger.pingServer(invite.hostAddress, invite.port.toInt(), edition)
                    }
                }
                .onFailure { _snackbarMessage.value = it.localizedMessage ?: "Lookup failed." }
            _isBusy.value = false
        }
    }

    fun bookmarkInvite(invite: ServerInvite) {
        viewModelScope.launch {
            repository.bookmarkFriendServer(invite)
                .onSuccess { _snackbarMessage.value = "Saved ${invite.serverName} to your friend servers." }
                .onFailure { _snackbarMessage.value = it.localizedMessage }
        }
    }

    fun removeJoinedServer(inviteCode: String) {
        viewModelScope.launch {
            repository.removeJoinedServer(inviteCode)
                .onFailure { _snackbarMessage.value = it.localizedMessage }
        }
    }

    fun deleteActiveRoom() {
        val room = activeRoom.value ?: return
        if (currentRole.value != StaffRole.OWNER) {
            _snackbarMessage.value = "Only the Room Owner can delete this server."
            return
        }
        viewModelScope.launch {
            repository.deleteServerRoom(room.id)
                .onSuccess {
                    _selectedRoomId.value = null
                    _snackbarMessage.value = "Deleted ${room.name}."
                }
                .onFailure { _snackbarMessage.value = it.localizedMessage }
        }
    }

    private fun <T> Flow<List<T>>.safeListCatch(label: String): Flow<List<T>> = catch { error ->
        val firestoreError = unwrapFirestoreError(error)
        if (firestoreError == null && error is CancellationException) throw error
        Log.w(TAG, "Error observing $label", firestoreError ?: error)
        emit(emptyList())
    }

    private fun unwrapFirestoreError(error: Throwable): FirebaseFirestoreException? =
        generateSequence(error) { it.cause }
            .filterIsInstance<FirebaseFirestoreException>()
            .firstOrNull()

    override fun onCleared() {
        super.onCleared()
        viewModelScope.launch { rconClient.disconnect() }
    }

    private companion object {
        const val TAG = "ServerCommandVM"
        const val STOP_TIMEOUT_MS = 5000L
    }
}
