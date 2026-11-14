package com.example.data.firebase

import com.example.base.FirestoreEmulatorTestBase
import com.example.data.model.DifficultyOption
import com.example.data.model.PackSource
import com.example.data.model.PackType
import com.example.data.model.RoomPack
import com.example.data.model.ServerRoom
import com.example.data.model.StaffRole
import com.google.firebase.firestore.FirebaseFirestoreException
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withTimeout
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.util.UUID

class ServerRoomRepositoryRuleTest : FirestoreEmulatorTestBase() {

    @Test
    fun createRoom_claimAdminAndModeratorGrants_enforcesPermissionsAndAllowsPackChanges() = runBlocking {
        val ownerUid = signInTestUser("owner_${UUID.randomUUID()}@test.com")
        val ownerRepo = ServerRoomRepository(firestore, auth)

        val roomId = withTimeout(DEFAULT_TIMEOUT_MS) {
            ownerRepo.createServerRoom(
                room = ServerRoom(name = "Aternos Style Free Hub"),
                rconPort = 25575L,
                rconPassword = "secret-rcon-${UUID.randomUUID()}"
            ).getOrThrow()
        }
        assertTrue(roomId.isNotBlank())

        val ownerRooms = withTimeout(FLOW_TIMEOUT_MS) {
            ownerRepo.observeMyServerRooms().first { list -> list.any { it.id == roomId } }
        }
        val createdRoom = ownerRooms.first { it.id == roomId }
        assertEquals(ownerUid, createdRoom.ownerUid)

        // Issue ADMIN and MODERATOR RoleGrants
        val adminGrant = withTimeout(DEFAULT_TIMEOUT_MS) {
            ownerRepo.createRoleGrant(createdRoom, StaffRole.ADMIN, maxUses = 1L).getOrThrow()
        }
        val modGrant = withTimeout(DEFAULT_TIMEOUT_MS) {
            ownerRepo.createRoleGrant(createdRoom, StaffRole.MODERATOR, maxUses = 2L).getOrThrow()
        }

        // Sign in as Admin and claim grant
        val adminUid = signInTestUser("admin_${UUID.randomUUID()}@test.com")
        val adminRepo = ServerRoomRepository(firestore, auth)
        val claimedAdminRoomId = withTimeout(DEFAULT_TIMEOUT_MS) {
            adminRepo.claimRoleGrant(adminGrant).getOrThrow()
        }
        assertEquals(roomId, claimedAdminRoomId)

        // Admin can read RCON config and change world seed independently
        val rconConfig = withTimeout(DEFAULT_TIMEOUT_MS) {
            adminRepo.getRconConfig(roomId).getOrThrow()
        }
        assertNotNull(rconConfig)
        assertEquals(25575L, rconConfig?.rconPort)

        val adminRooms = withTimeout(FLOW_TIMEOUT_MS) {
            adminRepo.observeMyServerRooms().first { list -> list.any { it.id == roomId } }
        }
        val updatedByAdmin = adminRooms.first { it.id == roomId }.copy(
            difficulty = DifficultyOption.HARD.name,
            seed = "4167794076839297600",
            seedNumeric = 4167794076839297600L
        )
        withTimeout(DEFAULT_TIMEOUT_MS) {
            adminRepo.updateServerRoomSettings(updatedByAdmin).getOrThrow()
        }

        // Sign in as Moderator and claim grant
        val modUid = signInTestUser("mod_${UUID.randomUUID()}@test.com")
        val modRepo = ServerRoomRepository(firestore, auth)
        withTimeout(DEFAULT_TIMEOUT_MS) {
            modRepo.claimRoleGrant(modGrant).getOrThrow()
        }

        // Moderator CAN add and toggle a resource pack / mod
        val packId = withTimeout(DEFAULT_TIMEOUT_MS) {
            modRepo.addRoomPack(
                roomId = roomId,
                pack = RoomPack(
                    title = "Faithful 32x Resource Pack",
                    packType = PackType.RESOURCE_PACK.name,
                    source = PackSource.MODRINTH.name,
                    modrinthProjectId = "faithful32x",
                    versionNumber = "1.21.4",
                    loader = "minecraft",
                    isEnabled = true,
                    isRequired = true,
                    priority = 1L
                )
            ).getOrThrow()
        }
        assertTrue(packId.isNotBlank())

        withTimeout(DEFAULT_TIMEOUT_MS) {
            modRepo.updateRoomPackFlags(roomId, packId, isEnabled = false, isRequired = true, priority = 2L).getOrThrow()
        }

        // Moderator CANNOT read RCON config
        val rconFailure = runCatching {
            withTimeout(DEFAULT_TIMEOUT_MS) { modRepo.getRconConfig(roomId).getOrThrow() }
        }.exceptionOrNull()?.unwrapFirestoreException()
        assertNotNull(rconFailure)
        assertEquals(FirebaseFirestoreException.Code.PERMISSION_DENIED, rconFailure?.code)

        // Outsider CANNOT observe or read the room
        signInTestUser("outsider_${UUID.randomUUID()}@test.com")
        val outsiderRepo = ServerRoomRepository(firestore, auth)
        val outsiderPacksError = runCatching {
            withTimeout(FLOW_TIMEOUT_MS) { outsiderRepo.observeRoomPacks(roomId).first() }
        }.exceptionOrNull()?.unwrapFirestoreException()
        assertNotNull(outsiderPacksError)
        assertEquals(FirebaseFirestoreException.Code.PERMISSION_DENIED, outsiderPacksError?.code)
        assertTrue(modUid.isNotBlank() && adminUid.isNotBlank())
    }

    @Test
    fun observeMyServerRooms_unauthenticatedUser_throwsOrDenied() = runBlocking {
        auth.signOut()
        val repo = ServerRoomRepository(firestore, auth)
        val error = runCatching {
            withTimeout(FLOW_TIMEOUT_MS) { repo.observeMyServerRooms().first() }
        }.exceptionOrNull()
        assertNotNull(error)
    }

    private companion object {
        const val DEFAULT_TIMEOUT_MS = 8000L
        const val FLOW_TIMEOUT_MS = 5000L
    }
}
