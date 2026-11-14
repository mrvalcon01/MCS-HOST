const {
  initializeTestEnvironment,
  assertFails,
  assertSucceeds,
} = require("@firebase/rules-unit-testing");
const { test, before, after, beforeEach } = require("node:test");
const fs = require("node:fs");

let testEnv;
const PROJECT_ID = process.env.GCP_PROJECT || "demo-no-project";
const OWNER_UID = "owner_alice";
const ADMIN_UID = "admin_bob";
const MOD_UID = "mod_carol";
const OUTSIDER_UID = "outsider_eve";

const [emulatorHost, emulatorPortStr] = (process.env.FIRESTORE_EMULATOR_HOST || "127.0.0.1:8085").split(":");
const emulatorPort = parseInt(emulatorPortStr, 10);

function baseRoom(ownerUid) {
  return {
    ownerUid,
    staffUids: [ownerUid],
    staffRoles: { [ownerUid]: "OWNER" },
    name: "Emerald Survival",
    motd: "Free Survival Server",
    mode: "LOCAL_BEDROCK",
    edition: "BEDROCK",
    software: "BEDROCK_HOST",
    minecraftVersion: "1.21.50",
    host: "192.168.1.10",
    port: 19132,
    seed: "CHERRY_VALLEY",
    seedNumeric: 123456789,
    seedLabel: "Cherry Blossom Crater",
    gamemode: "SURVIVAL",
    difficulty: "NORMAL",
    maxPlayers: 20,
    viewDistance: 10,
    simulationDistance: 6,
    pvp: true,
    hardcore: false,
    allowCheats: false,
    commandBlocks: false,
    onlineMode: true,
    whitelistEnabled: false,
    resourcePackRequired: true,
    keepInventory: false,
    mobGriefing: true,
    doDaylightCycle: true,
    doWeatherCycle: true,
    naturalRegeneration: true,
    operators: [],
    whitelist: [],
    bannedPlayers: [],
    inviteCode: "",
    isPublicInDirectory: false,
    isOnline: false,
    playerCount: 0,
    createdAt: new Date(),
    updatedAt: new Date(),
  };
}

before(async () => {
  const rules = fs.readFileSync("./firestore.rules", "utf8");
  testEnv = await initializeTestEnvironment({
    projectId: PROJECT_ID,
    firestore: {
      rules,
      host: emulatorHost,
      port: emulatorPort,
    },
  });
});

after(async () => {
  if (testEnv) {
    await testEnv.cleanup();
  }
});

beforeEach(async () => {
  if (testEnv) {
    await testEnv.clearFirestore();
  }
});

test("Unauthenticated user cannot list or create serverRooms", async () => {
  const unauthDb = testEnv.unauthenticatedContext().firestore();
  await assertFails(unauthDb.collection("serverRooms").get());
  await assertFails(unauthDb.collection("serverRooms").doc("room_1").set(baseRoom(OWNER_UID)));
});

test("Owner can create room, query owned rooms, and store isolated RCON config", async () => {
  const ownerDb = testEnv.authenticatedContext(OWNER_UID).firestore();
  const roomRef = ownerDb.collection("serverRooms").doc("room_1");
  await assertSucceeds(roomRef.set(baseRoom(OWNER_UID)));
  await assertSucceeds(
    ownerDb.collection("serverRooms").where("staffUids", "array-contains", OWNER_UID).get()
  );
  await assertSucceeds(
    roomRef.collection("adminConfig").doc("rcon").set({
      ownerUid: OWNER_UID,
      rconPort: 25575,
      rconPassword: "super-secret-rcon-password",
      updatedAt: new Date(),
    })
  );
});

test("Shadow update with ghost field is rejected on serverRooms", async () => {
  const ownerDb = testEnv.authenticatedContext(OWNER_UID).firestore();
  const room = baseRoom(OWNER_UID);
  room.isVerifiedGhost = true;
  await assertFails(ownerDb.collection("serverRooms").doc("room_ghost").set(room));
});

test("Outsider cannot read or list another owner's serverRooms or unfiltered query", async () => {
  const ownerDb = testEnv.authenticatedContext(OWNER_UID).firestore();
  await assertSucceeds(ownerDb.collection("serverRooms").doc("room_1").set(baseRoom(OWNER_UID)));

  const eveDb = testEnv.authenticatedContext(OUTSIDER_UID).firestore();
  await assertFails(eveDb.collection("serverRooms").doc("room_1").get());
  await assertFails(eveDb.collection("serverRooms").get());
});

test("Atomic RoleGrant claim grants ADMIN and MODERATOR access with strict permission boundaries", async () => {
  const ownerDb = testEnv.authenticatedContext(OWNER_UID).firestore();
  const roomRef = ownerDb.collection("serverRooms").doc("room_rbac");
  await assertSucceeds(roomRef.set(baseRoom(OWNER_UID)));
  await assertSucceeds(
    roomRef.collection("adminConfig").doc("rcon").set({
      ownerUid: OWNER_UID,
      rconPort: 25575,
      rconPassword: "secret-rcon-password",
      updatedAt: new Date(),
    })
  );

  // Owner issues ADMIN and MODERATOR roleGrants
  await assertSucceeds(
    ownerDb.collection("roleGrants").doc("ADM-1111").set({
      roomId: "room_rbac",
      roomName: "Emerald Survival",
      assignedRole: "ADMIN",
      ownerUid: OWNER_UID,
      createdByUid: OWNER_UID,
      isActive: true,
      maxUses: 1,
      usedCount: 0,
      createdAt: new Date(),
      updatedAt: new Date(),
    })
  );

  await assertSucceeds(
    ownerDb.collection("roleGrants").doc("MOD-2222").set({
      roomId: "room_rbac",
      roomName: "Emerald Survival",
      assignedRole: "MODERATOR",
      ownerUid: OWNER_UID,
      createdByUid: OWNER_UID,
      isActive: true,
      maxUses: 2,
      usedCount: 0,
      createdAt: new Date(),
      updatedAt: new Date(),
    })
  );

  // Admin Bob claims ADM-1111 atomically
  const adminDb = testEnv.authenticatedContext(ADMIN_UID).firestore();
  const adminBatch = adminDb.batch();
  adminBatch.update(adminDb.collection("roleGrants").doc("ADM-1111"), {
    usedCount: 1,
    updatedAt: new Date(),
  });
  adminBatch.set(adminDb.collection("serverRooms").doc("room_rbac").collection("members").doc(ADMIN_UID), {
    memberUid: ADMIN_UID,
    displayName: "Bob Admin",
    role: "ADMIN",
    grantCode: "ADM-1111",
    createdAt: new Date(),
    updatedAt: new Date(),
  });
  adminBatch.update(adminDb.collection("serverRooms").doc("room_rbac"), {
    staffUids: [OWNER_UID, ADMIN_UID],
    staffRoles: { [OWNER_UID]: "OWNER", [ADMIN_UID]: "ADMIN" },
    updatedAt: new Date(),
  });
  await assertSucceeds(adminBatch.commit());

  // Moderator Carol claims MOD-2222 atomically
  const modDb = testEnv.authenticatedContext(MOD_UID).firestore();
  const modBatch = modDb.batch();
  modBatch.update(modDb.collection("roleGrants").doc("MOD-2222"), {
    usedCount: 1,
    updatedAt: new Date(),
  });
  modBatch.set(modDb.collection("serverRooms").doc("room_rbac").collection("members").doc(MOD_UID), {
    memberUid: MOD_UID,
    displayName: "Carol Mod",
    role: "MODERATOR",
    grantCode: "MOD-2222",
    createdAt: new Date(),
    updatedAt: new Date(),
  });
  modBatch.update(modDb.collection("serverRooms").doc("room_rbac"), {
    staffUids: [OWNER_UID, ADMIN_UID, MOD_UID],
    staffRoles: { [OWNER_UID]: "OWNER", [ADMIN_UID]: "ADMIN", [MOD_UID]: "MODERATOR" },
    updatedAt: new Date(),
  });
  await assertSucceeds(modBatch.commit());

  // Admin CAN read RCON config, update world/seed, and create backups without Owner
  await assertSucceeds(
    adminDb.collection("serverRooms").doc("room_rbac").collection("adminConfig").doc("rcon").get()
  );
  await assertSucceeds(
    adminDb.collection("serverRooms").doc("room_rbac").update({
      difficulty: "HARD",
      seed: "NEW_SEED_99",
      updatedAt: new Date(),
    })
  );
  // Admin CANNOT delete room or promote self to OWNER
  await assertFails(adminDb.collection("serverRooms").doc("room_rbac").delete());
  await assertFails(
    adminDb.collection("serverRooms").doc("room_rbac").update({
      staffRoles: { [OWNER_UID]: "OWNER", [ADMIN_UID]: "OWNER", [MOD_UID]: "MODERATOR" },
      updatedAt: new Date(),
    })
  );

  // Moderator CANNOT read RCON config or modify difficulty/seed
  await assertFails(
    modDb.collection("serverRooms").doc("room_rbac").collection("adminConfig").doc("rcon").get()
  );
  await assertFails(
    modDb.collection("serverRooms").doc("room_rbac").update({
      difficulty: "PEACEFUL",
      updatedAt: new Date(),
    })
  );

  // Moderator CAN moderate whitelist/ban lists, dispatch commands, and add/toggle resource packs & mods
  await assertSucceeds(
    modDb.collection("serverRooms").doc("room_rbac").update({
      whitelist: ["Steve", "Alex"],
      updatedAt: new Date(),
    })
  );
  await assertSucceeds(
    modDb.collection("serverRooms").doc("room_rbac").collection("packs").doc("pack_1").set({
      title: "Sodium Optimization",
      packType: "MOD",
      source: "MODRINTH",
      modrinthProjectId: "AANobbMI",
      versionNumber: "0.6.0",
      loader: "fabric",
      isEnabled: true,
      isRequired: false,
      priority: 1,
      addedByUid: MOD_UID,
      addedByName: "Carol Mod",
      createdAt: new Date(),
      updatedAt: new Date(),
    })
  );
  await assertSucceeds(
    modDb.collection("serverRooms").doc("room_rbac").collection("packs").doc("pack_1").update({
      isEnabled: false,
      updatedAt: new Date(),
    })
  );
  await assertSucceeds(
    modDb.collection("serverRooms").doc("room_rbac").collection("commands").doc("cmd_1").set({
      senderUid: MOD_UID,
      senderName: "Carol Mod",
      senderRole: "MODERATOR",
      command: "/kick Griefer99",
      status: "PENDING",
      createdAt: new Date(),
      updatedAt: new Date(),
    })
  );
});

test("Outsider cannot self-assign ADMIN role without valid RoleGrant", async () => {
  const ownerDb = testEnv.authenticatedContext(OWNER_UID).firestore();
  await assertSucceeds(ownerDb.collection("serverRooms").doc("room_sec").set(baseRoom(OWNER_UID)));

  const eveDb = testEnv.authenticatedContext(OUTSIDER_UID).firestore();
  await assertFails(
    eveDb.collection("serverRooms").doc("room_sec").update({
      staffUids: [OWNER_UID, OUTSIDER_UID],
      staffRoles: { [OWNER_UID]: "OWNER", [OUTSIDER_UID]: "ADMIN" },
      updatedAt: new Date(),
    })
  );
});

test("Invites and joinedServers enforce ownership and directory visibility", async () => {
  const ownerDb = testEnv.authenticatedContext(OWNER_UID).firestore();
  await assertSucceeds(ownerDb.collection("serverRooms").doc("room_inv").set(baseRoom(OWNER_UID)));
  await assertSucceeds(
    ownerDb.collection("invites").doc("MCS-8F2K").set({
      roomId: "room_inv",
      ownerUid: OWNER_UID,
      ownerName: "Alice",
      serverName: "Emerald Survival",
      motd: "Join us!",
      edition: "BEDROCK",
      software: "BEDROCK_HOST",
      minecraftVersion: "1.21.50",
      hostAddress: "192.168.1.10",
      port: 19132,
      isOnline: true,
      isPublic: true,
      playerCount: 3,
      maxPlayers: 20,
      gamemode: "SURVIVAL",
      difficulty: "NORMAL",
      createdAt: new Date(),
      updatedAt: new Date(),
    })
  );

  const eveDb = testEnv.authenticatedContext(OUTSIDER_UID).firestore();
  await assertSucceeds(eveDb.collection("invites").doc("MCS-8F2K").get());
  await assertSucceeds(eveDb.collection("invites").where("isPublic", "==", true).get());
  await assertFails(eveDb.collection("invites").doc("MCS-8F2K").delete());

  await assertSucceeds(
    eveDb.collection("users").doc(OUTSIDER_UID).collection("joinedServers").doc("MCS-8F2K").set({
      userId: OUTSIDER_UID,
      inviteCode: "MCS-8F2K",
      serverName: "Emerald Survival",
      edition: "BEDROCK",
      hostAddress: "192.168.1.10",
      port: 19132,
      ownerName: "Alice",
      createdAt: new Date(),
      updatedAt: new Date(),
    })
  );
  await assertFails(
    ownerDb.collection("users").doc(OUTSIDER_UID).collection("joinedServers").get()
  );
});
