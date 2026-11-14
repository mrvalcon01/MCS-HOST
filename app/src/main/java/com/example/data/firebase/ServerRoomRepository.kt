package com.example.data.firebase

import android.content.Context
import com.example.R
import com.example.data.model.GameEdition
import com.example.data.model.GameModeOption
import com.example.data.model.JoinedServer
import com.example.data.model.PackSource
import com.example.data.model.PackType
import com.example.data.model.RoleGrant
import com.example.data.model.WorldBackup
import com.example.data.model.RoomCommand
import com.example.data.model.RoomMember
import com.example.data.model.RoomPack
import com.example.data.model.RoomRconConfig
import com.example.data.model.ServerInvite
import com.example.data.model.ServerMode
import com.example.data.model.ServerRoom
import com.example.data.model.ServerSoftware
import com.example.data.model.StaffRole
import com.example.data.model.DifficultyOption
import com.google.firebase.Firebase
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.auth.auth
import com.google.firebase.firestore.DocumentSnapshot.ServerTimestampBehavior
import com.google.firebase.firestore.FieldValue
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.FirebaseFirestoreException
import com.google.firebase.firestore.snapshots
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.emitAll
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.tasks.await

class ServerRoomRepository(
    private val db: FirebaseFirestore,
    private val auth: FirebaseAuth = Firebase.auth
) {
    constructor(context: Context) : this(
        FirebaseFirestore.getInstance(
            context.applicationContext.getString(R.string.firestore_database_id)
        ),
        Firebase.auth
    )

    private fun requireUserId(): String =
        auth.currentUser?.uid
            ?: throw IllegalStateException("User must be signed in with Google before accessing Firestore.")

    private fun currentDisplayName(): String =
        auth.currentUser?.displayName?.takeIf { it.isNotBlank() }?.take(60)
            ?: auth.currentUser?.email?.substringBefore('@')?.take(60)
            ?: "Player"

    private fun <T> Flow<T>.catchFirestore(
        operationType: OperationType,
        path: String,
        uid: String
    ): Flow<T> = catch { error ->
        val firestoreError = generateSequence(error) { it.cause }
            .filterIsInstance<FirebaseFirestoreException>()
            .firstOrNull()
        if (firestoreError == null && error is CancellationException) throw error
        val exceptionToLog = firestoreError ?: (error as? Exception)
        if (exceptionToLog != null && auth.currentUser?.uid == uid) {
            handleFirestoreError(exceptionToLog, operationType, path)
        }
        throw error
    }

    fun observeMyServerRooms(): Flow<List<ServerRoom>> = flow {
        val uid = requireUserId()
        val path = "serverRooms"
        emitAll(
            db.collection(path)
                .whereArrayContains("staffUids", uid)
                .snapshots()
                .map { snap ->
                    snap.toObjects(ServerRoom::class.java, ServerTimestampBehavior.ESTIMATE)
                        .sortedByDescending { it.updatedAt?.seconds ?: 0L }
                }
                .catchFirestore(OperationType.LIST, path, uid)
        )
    }

    fun observeRoomMembers(roomId: String): Flow<List<RoomMember>> = flow {
        val uid = requireUserId()
        val path = "serverRooms/$roomId/members"
        emitAll(
            db.collection("serverRooms").document(roomId).collection("members")
                .snapshots()
                .map { snap ->
                    snap.toObjects(RoomMember::class.java, ServerTimestampBehavior.ESTIMATE)
                        .sortedBy { it.role }
                }
                .catchFirestore(OperationType.LIST, path, uid)
        )
    }

    fun observeRoomPacks(roomId: String): Flow<List<RoomPack>> = flow {
        val uid = requireUserId()
        val path = "serverRooms/$roomId/packs"
        emitAll(
            db.collection("serverRooms").document(roomId).collection("packs")
                .snapshots()
                .map { snap ->
                    snap.toObjects(RoomPack::class.java, ServerTimestampBehavior.ESTIMATE)
                        .sortedBy { it.priority }
                }
                .catchFirestore(OperationType.LIST, path, uid)
        )
    }

    fun observeRoomBackups(roomId: String): Flow<List<WorldBackup>> = flow {
        val uid = requireUserId()
        val path = "serverRooms/$roomId/backups"
        emitAll(
            db.collection("serverRooms").document(roomId).collection("backups")
                .snapshots()
                .map { snap ->
                    snap.toObjects(WorldBackup::class.java, ServerTimestampBehavior.ESTIMATE)
                        .sortedByDescending { it.createdAt?.seconds ?: 0L }
                }
                .catchFirestore(OperationType.LIST, path, uid)
        )
    }

    fun observeRoomCommands(roomId: String): Flow<List<RoomCommand>> = flow {
        val uid = requireUserId()
        val path = "serverRooms/$roomId/commands"
        emitAll(
            db.collection("serverRooms").document(roomId).collection("commands")
                .snapshots()
                .map { snap ->
                    snap.toObjects(RoomCommand::class.java, ServerTimestampBehavior.ESTIMATE)
                        .sortedByDescending { it.createdAt?.seconds ?: 0L }
                }
                .catchFirestore(OperationType.LIST, path, uid)
        )
    }

    fun observeMyRoleGrants(): Flow<List<RoleGrant>> = flow {
        val uid = requireUserId()
        val path = "roleGrants"
        emitAll(
            db.collection(path)
                .whereEqualTo("ownerUid", uid)
                .snapshots()
                .map { snap ->
                    snap.toObjects(RoleGrant::class.java, ServerTimestampBehavior.ESTIMATE)
                        .sortedByDescending { it.createdAt?.seconds ?: 0L }
                }
                .catchFirestore(OperationType.LIST, path, uid)
        )
    }

    fun observePublicDirectoryInvites(): Flow<List<ServerInvite>> = flow {
        val uid = requireUserId()
        val path = "invites"
        emitAll(
            db.collection(path)
                .whereEqualTo("isPublic", true)
                .snapshots()
                .map { snap ->
                    snap.toObjects(ServerInvite::class.java, ServerTimestampBehavior.ESTIMATE)
                        .sortedByDescending { it.updatedAt?.seconds ?: 0L }
                }
                .catchFirestore(OperationType.LIST, path, uid)
        )
    }

    fun observeJoinedServers(): Flow<List<JoinedServer>> = flow {
        val uid = requireUserId()
        val path = "users/$uid/joinedServers"
        emitAll(
            db.collection("users").document(uid).collection("joinedServers")
                .snapshots()
                .map { snap ->
                    snap.toObjects(JoinedServer::class.java, ServerTimestampBehavior.ESTIMATE)
                        .sortedByDescending { it.updatedAt?.seconds ?: 0L }
                }
                .catchFirestore(OperationType.LIST, path, uid)
        )
    }

    suspend fun createServerRoom(
        room: ServerRoom,
        rconPort: Long = 25575L,
        rconPassword: String = ""
    ): Result<String> = runCatching {
        val uid = requireUserId()
        val roomRef = db.collection("serverRooms").document()
        val memberRef = roomRef.collection("members").document(uid)
        val rconRef = roomRef.collection("adminConfig").document("rcon")

        val roomPayload = mapOf(
            "ownerUid" to uid,
            "staffUids" to listOf(uid),
            "staffRoles" to mapOf(uid to StaffRole.OWNER.name),
            "name" to room.name.trim().take(60).ifBlank { "My Minecraft Server" },
            "motd" to room.motd.trim().take(120),
            "mode" to room.mode,
            "edition" to room.edition,
            "software" to room.software,
            "minecraftVersion" to room.minecraftVersion.trim().take(24).ifBlank { "1.21.50" },
            "host" to room.host.trim().take(128).ifBlank { "0.0.0.0" },
            "port" to room.port.coerceIn(1L, 65535L),
            "seed" to room.seed.trim().take(64),
            "seedNumeric" to room.seedNumeric,
            "seedLabel" to room.seedLabel.trim().take(80),
            "gamemode" to room.gamemode,
            "difficulty" to room.difficulty,
            "maxPlayers" to room.maxPlayers.coerceIn(1L, 200L),
            "viewDistance" to room.viewDistance.coerceIn(4L, 32L),
            "simulationDistance" to room.simulationDistance.coerceIn(4L, 16L),
            "pvp" to room.pvp,
            "hardcore" to room.hardcore,
            "allowCheats" to room.allowCheats,
            "commandBlocks" to room.commandBlocks,
            "onlineMode" to room.onlineMode,
            "whitelistEnabled" to room.whitelistEnabled,
            "resourcePackRequired" to room.resourcePackRequired,
            "keepInventory" to room.keepInventory,
            "mobGriefing" to room.mobGriefing,
            "doDaylightCycle" to room.doDaylightCycle,
            "doWeatherCycle" to room.doWeatherCycle,
            "naturalRegeneration" to room.naturalRegeneration,
            "operators" to room.operators.take(50),
            "whitelist" to room.whitelist.take(100),
            "bannedPlayers" to room.bannedPlayers.take(100),
            "inviteCode" to room.inviteCode.trim().take(16),
            "isPublicInDirectory" to room.isPublicInDirectory,
            "isOnline" to false,
            "playerCount" to 0L,
            "createdAt" to FieldValue.serverTimestamp(),
            "updatedAt" to FieldValue.serverTimestamp()
        )

        val memberPayload = mapOf(
            "memberUid" to uid,
            "displayName" to currentDisplayName(),
            "role" to StaffRole.OWNER.name,
            "grantCode" to "",
            "createdAt" to FieldValue.serverTimestamp(),
            "updatedAt" to FieldValue.serverTimestamp()
        )

        val rconPayload = mapOf(
            "ownerUid" to uid,
            "rconPort" to rconPort.coerceIn(1L, 65535L),
            "rconPassword" to rconPassword.take(128),
            "updatedAt" to FieldValue.serverTimestamp()
        )

        try {
            roomRef.set(roomPayload).await()
            val batch = db.batch()
            batch.set(memberRef, memberPayload)
            batch.set(rconRef, rconPayload)
            batch.commit().await()
            roomRef.id
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            handleFirestoreError(e, OperationType.CREATE, roomRef.path)
            throw e
        }
    }

    suspend fun updateServerRoomSettings(room: ServerRoom): Result<Unit> = runCatching {
        requireUserId()
        require(room.id.isNotBlank()) { "Room ID required" }
        val roomRef = db.collection("serverRooms").document(room.id)
        val updates = mapOf(
            "name" to room.name.trim().take(60).ifBlank { "My Minecraft Server" },
            "motd" to room.motd.trim().take(120),
            "mode" to room.mode,
            "edition" to room.edition,
            "software" to room.software,
            "minecraftVersion" to room.minecraftVersion.trim().take(24),
            "host" to room.host.trim().take(128),
            "port" to room.port.coerceIn(1L, 65535L),
            "seed" to room.seed.trim().take(64),
            "seedNumeric" to room.seedNumeric,
            "seedLabel" to room.seedLabel.trim().take(80),
            "gamemode" to room.gamemode,
            "difficulty" to room.difficulty,
            "maxPlayers" to room.maxPlayers.coerceIn(1L, 200L),
            "viewDistance" to room.viewDistance.coerceIn(4L, 32L),
            "simulationDistance" to room.simulationDistance.coerceIn(4L, 16L),
            "pvp" to room.pvp,
            "hardcore" to room.hardcore,
            "allowCheats" to room.allowCheats,
            "commandBlocks" to room.commandBlocks,
            "onlineMode" to room.onlineMode,
            "whitelistEnabled" to room.whitelistEnabled,
            "resourcePackRequired" to room.resourcePackRequired,
            "keepInventory" to room.keepInventory,
            "mobGriefing" to room.mobGriefing,
            "doDaylightCycle" to room.doDaylightCycle,
            "doWeatherCycle" to room.doWeatherCycle,
            "naturalRegeneration" to room.naturalRegeneration,
            "operators" to room.operators.take(50),
            "whitelist" to room.whitelist.take(100),
            "bannedPlayers" to room.bannedPlayers.take(100),
            "updatedAt" to FieldValue.serverTimestamp()
        )
        try {
            roomRef.update(updates).await()
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            handleFirestoreError(e, OperationType.UPDATE, roomRef.path)
            throw e
        }
    }

    suspend fun updateModerationLists(
        roomId: String,
        whitelist: List<String>,
        bannedPlayers: List<String>,
        operators: List<String>? = null
    ): Result<Unit> = runCatching {
        requireUserId()
        val roomRef = db.collection("serverRooms").document(roomId)
        val updates = mutableMapOf<String, Any>(
            "whitelist" to whitelist.distinct().take(100),
            "bannedPlayers" to bannedPlayers.distinct().take(100),
            "updatedAt" to FieldValue.serverTimestamp()
        )
        if (operators != null) {
            updates["operators"] = operators.distinct().take(50)
        }
        try {
            roomRef.update(updates).await()
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            handleFirestoreError(e, OperationType.UPDATE, roomRef.path)
            throw e
        }
    }

    suspend fun updateRoomRuntimeStatus(
        roomId: String,
        isOnline: Boolean,
        playerCount: Long,
        hostAddress: String? = null,
        inviteCode: String? = null
    ): Result<Unit> = runCatching {
        requireUserId()
        val roomRef = db.collection("serverRooms").document(roomId)
        val updates = mutableMapOf<String, Any>(
            "isOnline" to isOnline,
            "playerCount" to playerCount.coerceIn(0L, 200L),
            "updatedAt" to FieldValue.serverTimestamp()
        )
        if (!hostAddress.isNullOrBlank()) {
            updates["host"] = hostAddress.take(128)
        }
        try {
            roomRef.update(updates).await()
            if (!inviteCode.isNullOrBlank()) {
                val inviteRef = db.collection("invites").document(inviteCode)
                val inviteUpdates = mutableMapOf<String, Any>(
                    "isOnline" to isOnline,
                    "playerCount" to playerCount.coerceIn(0L, 200L),
                    "updatedAt" to FieldValue.serverTimestamp()
                )
                if (!hostAddress.isNullOrBlank()) {
                    inviteUpdates["hostAddress"] = hostAddress.take(128)
                }
                inviteRef.update(inviteUpdates).await()
            }
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            handleFirestoreError(e, OperationType.UPDATE, roomRef.path)
            throw e
        }
    }

    suspend fun deleteServerRoom(roomId: String): Result<Unit> = runCatching {
        requireUserId()
        val roomRef = db.collection("serverRooms").document(roomId)
        try {
            roomRef.delete().await()
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            handleFirestoreError(e, OperationType.DELETE, roomRef.path)
            throw e
        }
    }

    suspend fun getRconConfig(roomId: String): Result<RoomRconConfig?> = runCatching {
        requireUserId()
        val docRef = db.collection("serverRooms").document(roomId)
            .collection("adminConfig").document("rcon")
        try {
            docRef.get().await().toObject(RoomRconConfig::class.java, ServerTimestampBehavior.ESTIMATE)
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            handleFirestoreError(e, OperationType.GET, docRef.path)
            throw e
        }
    }

    suspend fun saveRconConfig(
        roomId: String,
        ownerUid: String,
        rconPort: Long,
        rconPassword: String
    ): Result<Unit> = runCatching {
        requireUserId()
        val docRef = db.collection("serverRooms").document(roomId)
            .collection("adminConfig").document("rcon")
        val payload = mapOf(
            "ownerUid" to ownerUid,
            "rconPort" to rconPort.coerceIn(1L, 65535L),
            "rconPassword" to rconPassword.take(128),
            "updatedAt" to FieldValue.serverTimestamp()
        )
        try {
            docRef.set(payload).await()
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            handleFirestoreError(e, OperationType.WRITE, docRef.path)
            throw e
        }
    }

    suspend fun createRoleGrant(
        room: ServerRoom,
        assignedRole: StaffRole,
        maxUses: Long = 5L
    ): Result<String> = runCatching {
        val uid = requireUserId()
        require(assignedRole == StaffRole.ADMIN || assignedRole == StaffRole.MODERATOR) {
            "Role grant must be ADMIN or MODERATOR"
        }
        val prefix = if (assignedRole == StaffRole.ADMIN) "ADM" else "MOD"
        val code = "$prefix-${randomAlphaNum(4)}"
        val grantRef = db.collection("roleGrants").document(code)
        val payload = mapOf(
            "roomId" to room.id,
            "roomName" to room.name.take(60),
            "assignedRole" to assignedRole.name,
            "ownerUid" to room.ownerUid,
            "createdByUid" to uid,
            "isActive" to true,
            "maxUses" to maxUses.coerceIn(1L, 50L),
            "usedCount" to 0L,
            "createdAt" to FieldValue.serverTimestamp(),
            "updatedAt" to FieldValue.serverTimestamp()
        )
        try {
            grantRef.set(payload).await()
            code
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            handleFirestoreError(e, OperationType.CREATE, grantRef.path)
            throw e
        }
    }

    suspend fun revokeRoleGrant(grantCode: String): Result<Unit> = runCatching {
        requireUserId()
        val grantRef = db.collection("roleGrants").document(grantCode)
        try {
            grantRef.update(
                mapOf(
                    "isActive" to false,
                    "updatedAt" to FieldValue.serverTimestamp()
                )
            ).await()
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            handleFirestoreError(e, OperationType.UPDATE, grantRef.path)
            throw e
        }
    }

    suspend fun claimRoleGrant(grantCodeInput: String): Result<String> = runCatching {
        val uid = requireUserId()
        val grantCode = grantCodeInput.trim().uppercase()
        require(grantCode.matches(Regex("^[A-Z0-9\\-_]{4,32}$"))) {
            "Invalid role access code format."
        }
        val grantRef = db.collection("roleGrants").document(grantCode)
        val grantSnap = try {
            grantRef.get().await()
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            handleFirestoreError(e, OperationType.GET, grantRef.path)
            throw e
        }
        val grant = grantSnap.toObject(RoleGrant::class.java, ServerTimestampBehavior.ESTIMATE)
            ?: throw IllegalArgumentException("Role access code not found.")

        require(grant.isActive) { "This role access code has been revoked." }
        require(grant.usedCount < grant.maxUses) { "This role access code has reached its max uses." }

        val roomRef = db.collection("serverRooms").document(grant.roomId)
        val memberRef = roomRef.collection("members").document(uid)

        val batch = db.batch()
        batch.update(
            grantRef,
            mapOf(
                "usedCount" to FieldValue.increment(1L),
                "updatedAt" to FieldValue.serverTimestamp()
            )
        )
        batch.set(
            memberRef,
            mapOf(
                "memberUid" to uid,
                "displayName" to currentDisplayName(),
                "role" to grant.assignedRole,
                "grantCode" to grantCode,
                "createdAt" to FieldValue.serverTimestamp(),
                "updatedAt" to FieldValue.serverTimestamp()
            )
        )
        batch.update(
            roomRef,
            mapOf(
                "staffUids" to FieldValue.arrayUnion(uid),
                "staffRoles.$uid" to grant.assignedRole,
                "updatedAt" to FieldValue.serverTimestamp()
            )
        )

        try {
            batch.commit().await()
            grant.roomId
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            handleFirestoreError(e, OperationType.WRITE, roomRef.path)
            throw e
        }
    }

    suspend fun updateMemberRole(room: ServerRoom, targetUid: String, newRole: StaffRole): Result<Unit> = runCatching {
        requireUserId()
        require(targetUid != room.ownerUid) { "Cannot change Owner role." }
        require(newRole == StaffRole.ADMIN || newRole == StaffRole.MODERATOR) {
            "Staff role must be ADMIN or MODERATOR."
        }
        val roomRef = db.collection("serverRooms").document(room.id)
        val memberRef = roomRef.collection("members").document(targetUid)
        val batch = db.batch()
        batch.update(
            memberRef,
            mapOf(
                "role" to newRole.name,
                "updatedAt" to FieldValue.serverTimestamp()
            )
        )
        batch.update(
            roomRef,
            mapOf(
                "staffRoles.$targetUid" to newRole.name,
                "updatedAt" to FieldValue.serverTimestamp()
            )
        )
        try {
            batch.commit().await()
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            handleFirestoreError(e, OperationType.UPDATE, memberRef.path)
            throw e
        }
    }

    suspend fun removeRoomMember(room: ServerRoom, targetUid: String): Result<Unit> = runCatching {
        requireUserId()
        require(targetUid != room.ownerUid) { "Cannot remove the room Owner." }
        val roomRef = db.collection("serverRooms").document(room.id)
        val memberRef = roomRef.collection("members").document(targetUid)
        val batch = db.batch()
        batch.delete(memberRef)
        batch.update(
            roomRef,
            mapOf(
                "staffUids" to FieldValue.arrayRemove(targetUid),
                "staffRoles.$targetUid" to FieldValue.delete(),
                "updatedAt" to FieldValue.serverTimestamp()
            )
        )
        try {
            batch.commit().await()
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            handleFirestoreError(e, OperationType.DELETE, memberRef.path)
            throw e
        }
    }

    suspend fun addRoomPack(roomId: String, pack: RoomPack): Result<String> = runCatching {
        val uid = requireUserId()
        val packRef = db.collection("serverRooms").document(roomId).collection("packs").document()
        val payload = mapOf(
            "title" to pack.title.trim().take(100).ifBlank { "Custom Pack" },
            "packType" to pack.packType,
            "source" to pack.source,
            "modrinthProjectId" to pack.modrinthProjectId.take(48),
            "versionNumber" to pack.versionNumber.take(48),
            "downloadUrl" to pack.downloadUrl.take(512),
            "iconUrl" to pack.iconUrl.take(512),
            "fileName" to pack.fileName.take(128),
            "sha256" to pack.sha256.take(64),
            "loader" to pack.loader.take(32),
            "isEnabled" to pack.isEnabled,
            "isRequired" to pack.isRequired,
            "priority" to pack.priority.coerceIn(0L, 1000L),
            "addedByUid" to uid,
            "addedByName" to currentDisplayName(),
            "createdAt" to FieldValue.serverTimestamp(),
            "updatedAt" to FieldValue.serverTimestamp()
        )
        try {
            packRef.set(payload).await()
            packRef.id
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            handleFirestoreError(e, OperationType.CREATE, packRef.path)
            throw e
        }
    }

    suspend fun updateRoomPackFlags(
        roomId: String,
        packId: String,
        isEnabled: Boolean,
        isRequired: Boolean,
        priority: Long
    ): Result<Unit> = runCatching {
        requireUserId()
        val packRef = db.collection("serverRooms").document(roomId)
            .collection("packs").document(packId)
        try {
            packRef.update(
                mapOf(
                    "isEnabled" to isEnabled,
                    "isRequired" to isRequired,
                    "priority" to priority.coerceIn(0L, 1000L),
                    "updatedAt" to FieldValue.serverTimestamp()
                )
            ).await()
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            handleFirestoreError(e, OperationType.UPDATE, packRef.path)
            throw e
        }
    }

    suspend fun deleteRoomPack(roomId: String, packId: String): Result<Unit> = runCatching {
        requireUserId()
        val packRef = db.collection("serverRooms").document(roomId)
            .collection("packs").document(packId)
        try {
            packRef.delete().await()
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            handleFirestoreError(e, OperationType.DELETE, packRef.path)
            throw e
        }
    }

    suspend fun createWorldBackup(room: ServerRoom, backupName: String, packCount: Int): Result<String> = runCatching {
        val uid = requireUserId()
        val backupRef = db.collection("serverRooms").document(room.id)
            .collection("backups").document()
        val payload = mapOf(
            "name" to backupName.trim().take(60).ifBlank { "Snapshot" },
            "seed" to room.seed.take(64),
            "seedNumeric" to room.seedNumeric,
            "minecraftVersion" to room.minecraftVersion.take(24),
            "software" to room.software.take(32),
            "gamemode" to room.gamemode,
            "difficulty" to room.difficulty,
            "packCount" to packCount.toLong().coerceIn(0L, 500L),
            "createdByUid" to uid,
            "createdByName" to currentDisplayName(),
            "createdAt" to FieldValue.serverTimestamp()
        )
        try {
            backupRef.set(payload).await()
            backupRef.id
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            handleFirestoreError(e, OperationType.CREATE, backupRef.path)
            throw e
        }
    }

    suspend fun deleteWorldBackup(roomId: String, backupId: String): Result<Unit> = runCatching {
        requireUserId()
        val ref = db.collection("serverRooms").document(roomId)
            .collection("backups").document(backupId)
        try {
            ref.delete().await()
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            handleFirestoreError(e, OperationType.DELETE, ref.path)
            throw e
        }
    }

    suspend fun dispatchRoomCommand(
        roomId: String,
        senderRole: StaffRole,
        command: String
    ): Result<String> = runCatching {
        val uid = requireUserId()
        val cmdRef = db.collection("serverRooms").document(roomId)
            .collection("commands").document()
        val payload = mapOf(
            "senderUid" to uid,
            "senderName" to currentDisplayName(),
            "senderRole" to senderRole.name,
            "command" to command.trim().take(256),
            "status" to "PENDING",
            "response" to "",
            "createdAt" to FieldValue.serverTimestamp(),
            "updatedAt" to FieldValue.serverTimestamp()
        )
        try {
            cmdRef.set(payload).await()
            cmdRef.id
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            handleFirestoreError(e, OperationType.CREATE, cmdRef.path)
            throw e
        }
    }

    suspend fun resolveRoomCommand(
        roomId: String,
        commandId: String,
        status: String,
        response: String
    ): Result<Unit> = runCatching {
        requireUserId()
        val cmdRef = db.collection("serverRooms").document(roomId)
            .collection("commands").document(commandId)
        try {
            cmdRef.update(
                mapOf(
                    "status" to status,
                    "response" to response.take(512),
                    "updatedAt" to FieldValue.serverTimestamp()
                )
            ).await()
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            handleFirestoreError(e, OperationType.UPDATE, cmdRef.path)
            throw e
        }
    }

    suspend fun publishServerInvite(
        room: ServerRoom,
        hostAddress: String,
        isPublic: Boolean,
        isOnline: Boolean,
        playerCount: Long
    ): Result<String> = runCatching {
        requireUserId()
        val code = room.inviteCode.ifBlank { "MCS-${randomAlphaNum(4)}" }
        val inviteRef = db.collection("invites").document(code)
        val roomRef = db.collection("serverRooms").document(room.id)
        val payload = mapOf(
            "roomId" to room.id,
            "ownerUid" to room.ownerUid,
            "ownerName" to currentDisplayName(),
            "serverName" to room.name.take(60),
            "motd" to room.motd.take(120),
            "edition" to room.edition,
            "software" to room.software,
            "minecraftVersion" to room.minecraftVersion.take(24),
            "hostAddress" to hostAddress.ifBlank { room.host }.take(128),
            "port" to room.port.coerceIn(1L, 65535L),
            "isOnline" to isOnline,
            "isPublic" to isPublic,
            "playerCount" to playerCount.coerceIn(0L, 200L),
            "maxPlayers" to room.maxPlayers.coerceIn(1L, 200L),
            "gamemode" to room.gamemode,
            "difficulty" to room.difficulty,
            "createdAt" to FieldValue.serverTimestamp(),
            "updatedAt" to FieldValue.serverTimestamp()
        )
        try {
            val batch = db.batch()
            batch.set(inviteRef, payload)
            batch.update(
                roomRef,
                mapOf(
                    "inviteCode" to code,
                    "isPublicInDirectory" to isPublic,
                    "updatedAt" to FieldValue.serverTimestamp()
                )
            )
            batch.commit().await()
            code
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            handleFirestoreError(e, OperationType.WRITE, inviteRef.path)
            throw e
        }
    }

    suspend fun lookupInviteByCode(inviteCodeInput: String): Result<ServerInvite?> = runCatching {
        requireUserId()
        val code = inviteCodeInput.trim().uppercase()
        require(code.matches(Regex("^[A-Z0-9\\-_]{3,16}$"))) { "Invalid invite code." }
        val docRef = db.collection("invites").document(code)
        try {
            docRef.get().await().toObject(ServerInvite::class.java, ServerTimestampBehavior.ESTIMATE)
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            handleFirestoreError(e, OperationType.GET, docRef.path)
            throw e
        }
    }

    suspend fun bookmarkFriendServer(invite: ServerInvite): Result<Unit> = runCatching {
        val uid = requireUserId()
        val docRef = db.collection("users").document(uid)
            .collection("joinedServers").document(invite.id)
        val payload = mapOf(
            "userId" to uid,
            "inviteCode" to invite.id,
            "serverName" to invite.serverName.take(60),
            "edition" to invite.edition,
            "hostAddress" to invite.hostAddress.take(128),
            "port" to invite.port.coerceIn(1L, 65535L),
            "ownerName" to invite.ownerName.take(60),
            "createdAt" to FieldValue.serverTimestamp(),
            "updatedAt" to FieldValue.serverTimestamp()
        )
        try {
            docRef.set(payload).await()
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            handleFirestoreError(e, OperationType.WRITE, docRef.path)
            throw e
        }
    }

    suspend fun removeJoinedServer(inviteCode: String): Result<Unit> = runCatching {
        val uid = requireUserId()
        val docRef = db.collection("users").document(uid)
            .collection("joinedServers").document(inviteCode)
        try {
            docRef.delete().await()
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            handleFirestoreError(e, OperationType.DELETE, docRef.path)
            throw e
        }
    }

    private fun randomAlphaNum(length: Int): String {
        val chars = "ABCDEFGHJKLMNPQRSTUVWXYZ23456789"
        return (1..length).map { chars.random() }.joinToString("")
    }
}
