package com.example

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.Logout
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Dns
import androidx.compose.material.icons.filled.Extension
import androidx.compose.material.icons.filled.Groups
import androidx.compose.material.icons.filled.Public
import androidx.compose.material.icons.filled.Terminal
import androidx.compose.material.icons.filled.Tune
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.NavigationBarItemDefaults
import androidx.compose.material3.NavigationRail
import androidx.compose.material3.NavigationRailItem
import androidx.compose.material3.NavigationRailItemDefaults
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.credentials.CredentialManager
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import androidx.navigation.NavGraph.Companion.findStartDestination
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.currentBackStackEntryAsState
import androidx.navigation.compose.rememberNavController
import com.example.data.firebase.ServerRoomRepository
import com.example.auth.AuthHelper
import com.example.ui.components.CreateServerDialog
import com.example.ui.components.RoleBadge
import com.example.ui.components.RoomAccessDialog
import com.example.ui.screens.AuthScreen
import com.example.ui.screens.ConsoleScreen
import com.example.ui.screens.DirectoryScreen
import com.example.ui.screens.PacksAndModsScreen
import com.example.ui.screens.PlayersScreen
import com.example.ui.screens.ServerDashboardScreen
import com.example.ui.screens.WorldsAndOptionsScreen
import com.example.ui.theme.BasaltElevated
import com.example.ui.theme.BasaltSurface
import com.example.ui.theme.DiamondCyan
import com.example.ui.theme.EmeraldCore
import com.example.ui.theme.McsHostTheme
import com.example.ui.theme.ObsidianVoid
import com.example.ui.viewmodel.ServerCommandViewModel
import com.google.firebase.Firebase
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.auth.FirebaseUser
import com.google.firebase.auth.auth
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.callbackFlow

const val ROUTE_SERVER = "server"
const val ROUTE_CONSOLE = "console"
const val ROUTE_PLAYERS = "players"
const val ROUTE_PACKS = "packs"
const val ROUTE_WORLDS = "worlds"
const val ROUTE_FRIENDS = "friends"

private data class NavDestination(
    val route: String,
    val labelRes: Int,
    val icon: ImageVector,
    val tag: String
)

private val PrimaryDestinations = listOf(
    NavDestination(ROUTE_SERVER, R.string.nav_server, Icons.Filled.Dns, "nav_server"),
    NavDestination(ROUTE_CONSOLE, R.string.nav_console, Icons.Filled.Terminal, "nav_console"),
    NavDestination(ROUTE_PLAYERS, R.string.nav_players, Icons.Filled.Groups, "nav_players"),
    NavDestination(ROUTE_PACKS, R.string.nav_packs, Icons.Filled.Extension, "nav_packs"),
    NavDestination(ROUTE_WORLDS, R.string.nav_worlds, Icons.Filled.Tune, "nav_worlds")
)

private val FriendsDestination = NavDestination(
    ROUTE_FRIENDS, R.string.nav_friends, Icons.Filled.Public, "nav_friends"
)

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContent {
            McsHostTheme {
                val authUser by remember { Firebase.auth.authStateFlow() }
                    .collectAsStateWithLifecycle(initialValue = Firebase.auth.currentUser)

                if (authUser == null) {
                    AuthScreen(onAuthSuccess = {})
                } else {
                    McsHostAppShell(user = authUser!!)
                }
            }
        }
    }
}

private fun FirebaseAuth.authStateFlow(): Flow<FirebaseUser?> = callbackFlow {
    val listener = FirebaseAuth.AuthStateListener { auth ->
        trySend(auth.currentUser)
    }
    addAuthStateListener(listener)
    awaitClose { removeAuthStateListener(listener) }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun McsHostAppShell(user: FirebaseUser) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val credentialManager = remember { CredentialManager.create(context) }

    val viewModel: ServerCommandViewModel = viewModel(
        key = user.uid,
        factory = viewModelFactory {
            initializer {
                val application = this[ViewModelProvider.AndroidViewModelFactory.APPLICATION_KEY]
                    ?: error("Application key missing")
                val modrinthApi = com.example.data.network.ModrinthApiService.create(
                    cacheDir = application.cacheDir
                )
                ServerCommandViewModel(
                    repository = ServerRoomRepository(application),
                    currentUserId = user.uid,
                    modrinthApi = modrinthApi
                )
            }
        }
    )

    val roomsState by viewModel.roomsState.collectAsStateWithLifecycle()
    val activeRoom by viewModel.activeRoom.collectAsStateWithLifecycle()
    val currentRole by viewModel.currentRole.collectAsStateWithLifecycle()
    val hostRuntime by viewModel.hostRuntime.collectAsStateWithLifecycle()
    val rconConfig by viewModel.rconConfig.collectAsStateWithLifecycle()
    val rconConnected by viewModel.rconConnected.collectAsStateWithLifecycle()
    val remotePing by viewModel.remotePing.collectAsStateWithLifecycle()
    val roomMembers by viewModel.roomMembers.collectAsStateWithLifecycle()
    val roomPacks by viewModel.roomPacks.collectAsStateWithLifecycle()
    val roomBackups by viewModel.roomBackups.collectAsStateWithLifecycle()
    val roomCommands by viewModel.roomCommands.collectAsStateWithLifecycle()
    val myRoleGrants by viewModel.myRoleGrants.collectAsStateWithLifecycle()
    val publicInvites by viewModel.publicInvites.collectAsStateWithLifecycle()
    val joinedServers by viewModel.joinedServers.collectAsStateWithLifecycle()
    val lookedUpInvite by viewModel.lookedUpInvite.collectAsStateWithLifecycle()
    val lookedUpPing by viewModel.lookedUpPing.collectAsStateWithLifecycle()
    val modrinthResults by viewModel.modrinthResults.collectAsStateWithLifecycle()
    val modrinthSearching by viewModel.modrinthSearching.collectAsStateWithLifecycle()
    val modrinthBedrockOnly by viewModel.modrinthBedrockOnly.collectAsStateWithLifecycle()
    val modrinthSort by viewModel.modrinthSort.collectAsStateWithLifecycle()
    val modrinthFromCache by viewModel.modrinthFromCache.collectAsStateWithLifecycle()
    val modrinthDownloads by viewModel.modrinthDownloads.collectAsStateWithLifecycle()
    val snackbarMessage by viewModel.snackbarMessage.collectAsStateWithLifecycle()

    val snackbarHostState = remember { SnackbarHostState() }
    LaunchedEffect(snackbarMessage) {
        val msg = snackbarMessage
        if (!msg.isNullOrBlank()) {
            snackbarHostState.showSnackbar(msg)
            viewModel.clearSnackbar()
        }
    }

    var showCreateDialog by remember { mutableStateOf(false) }
    var showStaffDialog by remember { mutableStateOf(false) }

    val navController = rememberNavController()
    val backStackEntry by navController.currentBackStackEntryAsState()
    val currentRoute = backStackEntry?.destination?.route ?: ROUTE_SERVER

    val navigateTo: (String) -> Unit = { route ->
        navController.navigate(route) {
            popUpTo(navController.graph.findStartDestination().id) {
                saveState = true
            }
            launchSingleTop = true
            restoreState = true
        }
    }

    BoxWithConstraints(modifier = Modifier.fillMaxSize()) {
        val isWideScreen = maxWidth >= 600.dp

        Row(modifier = Modifier.fillMaxSize()) {
            if (isWideScreen) {
                NavigationRail(
                    containerColor = BasaltSurface,
                    contentColor = EmeraldCore
                ) {
                    Spacer(Modifier.size(12.dp))
                    (PrimaryDestinations + FriendsDestination).forEach { dest ->
                        val selected = currentRoute == dest.route
                        NavigationRailItem(
                            selected = selected,
                            onClick = { navigateTo(dest.route) },
                            icon = { Icon(dest.icon, contentDescription = stringResource(dest.labelRes)) },
                            label = {
                                Text(
                                    text = stringResource(dest.labelRes),
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis
                                )
                            },
                            colors = NavigationRailItemDefaults.colors(
                                selectedIconColor = EmeraldCore,
                                selectedTextColor = EmeraldCore,
                                indicatorColor = BasaltElevated
                            ),
                            modifier = Modifier.testTag(dest.tag)
                        )
                    }
                }
            }

            Scaffold(
                modifier = Modifier.weight(1f),
                containerColor = ObsidianVoid,
                contentWindowInsets = WindowInsets.safeDrawing,
                snackbarHost = { SnackbarHost(hostState = snackbarHostState) },
                topBar = {
                    TopAppBar(
                        colors = TopAppBarDefaults.topAppBarColors(
                            containerColor = BasaltSurface,
                            titleContentColor = MaterialTheme.colorScheme.onSurface
                        ),
                        title = {
                            Column {
                                Text(
                                    text = stringResource(id = R.string.app_name),
                                    style = MaterialTheme.typography.titleMedium,
                                    color = EmeraldCore
                                )
                                Text(
                                    text = activeRoom?.name ?: (user.displayName ?: "Multiplayer Host"),
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis
                                )
                            }
                        },
                        actions = {
                            if (activeRoom != null) {
                                RoleBadge(
                                    role = currentRole,
                                    modifier = Modifier.padding(end = 4.dp)
                                )
                            }
                            if (!isWideScreen) {
                                IconButton(
                                    onClick = { navigateTo(ROUTE_FRIENDS) },
                                    modifier = Modifier.size(48.dp).testTag("topbar_friends_button")
                                ) {
                                    Icon(
                                        Icons.Filled.Public,
                                        contentDescription = stringResource(id = R.string.nav_friends),
                                        tint = if (currentRoute == ROUTE_FRIENDS) EmeraldCore else DiamondCyan
                                    )
                                }
                            }
                            IconButton(
                                onClick = { showCreateDialog = true },
                                modifier = Modifier.size(48.dp).testTag("topbar_create_server_button")
                            ) {
                                Icon(Icons.Filled.Add, contentDescription = "New Server Room", tint = EmeraldCore)
                            }
                            IconButton(
                                onClick = {
                                    AuthHelper.signOut(credentialManager = credentialManager, scope = scope)
                                },
                                modifier = Modifier.size(48.dp).testTag("sign_out_button")
                            ) {
                                Icon(
                                    Icons.AutoMirrored.Filled.Logout,
                                    contentDescription = stringResource(id = R.string.cd_sign_out)
                                )
                            }
                        }
                    )
                },
                bottomBar = {
                    if (!isWideScreen) {
                        NavigationBar(containerColor = BasaltSurface) {
                            PrimaryDestinations.forEach { dest ->
                                val selected = currentRoute == dest.route
                                NavigationBarItem(
                                    selected = selected,
                                    onClick = { navigateTo(dest.route) },
                                    icon = { Icon(dest.icon, contentDescription = stringResource(dest.labelRes)) },
                                    label = {
                                        Text(
                                            text = stringResource(dest.labelRes),
                                            maxLines = 1,
                                            softWrap = false,
                                            overflow = TextOverflow.Ellipsis
                                        )
                                    },
                                    alwaysShowLabel = true,
                                    colors = NavigationBarItemDefaults.colors(
                                        selectedIconColor = EmeraldCore,
                                        selectedTextColor = EmeraldCore,
                                        indicatorColor = BasaltElevated
                                    ),
                                    modifier = Modifier.testTag(dest.tag)
                                )
                            }
                        }
                    }
                }
            ) { innerPadding ->
                Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(innerPadding),
                    contentAlignment = Alignment.TopCenter
                ) {
                    NavHost(
                        navController = navController,
                        startDestination = ROUTE_SERVER,
                        modifier = Modifier
                            .widthIn(max = 920.dp)
                            .fillMaxSize()
                    ) {
                        composable(ROUTE_SERVER) {
                            ServerDashboardScreen(
                                roomsState = roomsState,
                                activeRoom = activeRoom,
                                currentRole = currentRole,
                                currentUserId = user.uid,
                                hostRuntime = hostRuntime,
                                rconConnected = rconConnected,
                                remotePing = remotePing,
                                onSelectRoom = viewModel::selectRoom,
                                onOpenCreateRoom = { showCreateDialog = true },
                                onOpenManageStaff = { showStaffDialog = true },
                                onTogglePower = {
                                    val room = activeRoom ?: return@ServerDashboardScreen
                                    if (room.isLocalHost()) viewModel.toggleLocalHost(context)
                                    else viewModel.toggleRconConnection()
                                },
                                onPingServer = viewModel::pingActiveServer,
                                onPublishInvite = viewModel::publishToDirectory,
                                onJoinByRoleCode = viewModel::joinRoomByRoleCode,
                                onDeleteRoom = viewModel::deleteActiveRoom,
                                onNavigateToPacks = { navigateTo(ROUTE_PACKS) },
                                onNavigateToWorlds = { navigateTo(ROUTE_WORLDS) }
                            )
                        }
                        composable(ROUTE_CONSOLE) {
                            ConsoleScreen(
                                activeRoom = activeRoom,
                                currentRole = currentRole,
                                hostRuntime = hostRuntime,
                                roomCommands = roomCommands,
                                onSendCommand = viewModel::sendConsoleCommand
                            )
                        }
                        composable(ROUTE_PLAYERS) {
                            PlayersScreen(
                                activeRoom = activeRoom,
                                currentRole = currentRole,
                                hostRuntime = hostRuntime,
                                onModeratePlayer = viewModel::moderatePlayer
                            )
                        }
                        composable(ROUTE_PACKS) {
                            PacksAndModsScreen(
                                activeRoom = activeRoom,
                                currentRole = currentRole,
                                roomPacks = roomPacks,
                                modrinthResults = modrinthResults,
                                modrinthSearching = modrinthSearching,
                                bedrockOnly = modrinthBedrockOnly,
                                selectedSort = modrinthSort,
                                isResultFromCache = modrinthFromCache,
                                downloadStatuses = modrinthDownloads,
                                onSearchModrinth = { query, type, loader, forceRefresh ->
                                    viewModel.searchModrinth(
                                        query = query,
                                        packType = type,
                                        loader = loader,
                                        forceRefresh = forceRefresh
                                    )
                                },
                                onToggleBedrockOnly = viewModel::setModrinthBedrockOnly,
                                onChangeSort = viewModel::setModrinthSort,
                                onDownloadModrinth = { hit ->
                                    viewModel.downloadModrinthPack(context, hit)
                                },
                                onOpenInMinecraft = { completed ->
                                    viewModel.openDownloadedPackInMinecraft(context, completed)
                                },
                                onImportLocalUri = { uri, type ->
                                    viewModel.importLocalPackUri(context, uri, type)
                                },
                                onToggleEnabled = viewModel::togglePackEnabled,
                                onToggleRequired = viewModel::togglePackRequired,
                                onRemovePack = viewModel::removePack
                            )
                        }
                        composable(ROUTE_WORLDS) {
                            WorldsAndOptionsScreen(
                                activeRoom = activeRoom,
                                currentRole = currentRole,
                                backups = roomBackups,
                                rconConfig = rconConfig,
                                onUpdateOptions = viewModel::updateWorldAndOptions,
                                onApplySeed = viewModel::applyWorldSeed,
                                onSaveRconCredentials = viewModel::updateRconCredentials,
                                onCreateSnapshot = viewModel::createWorldSnapshot,
                                onRestoreSnapshot = viewModel::restoreWorldSnapshot
                            )
                        }
                        composable(ROUTE_FRIENDS) {
                            DirectoryScreen(
                                activeRoom = activeRoom,
                                currentRole = currentRole,
                                publicInvites = publicInvites,
                                joinedServers = joinedServers,
                                lookedUpInvite = lookedUpInvite,
                                lookedUpPing = lookedUpPing,
                                onJoinByRoleCode = viewModel::joinRoomByRoleCode,
                                onLookupInvite = viewModel::lookupFriendInvite,
                                onBookmarkInvite = viewModel::bookmarkInvite,
                                onRemoveJoined = viewModel::removeJoinedServer,
                                onPublishActiveRoom = viewModel::publishToDirectory
                            )
                        }
                    }
                }
            }
        }
    }

    if (showCreateDialog) {
        CreateServerDialog(
            onDismiss = { showCreateDialog = false },
            onCreate = { name, motd, mode, software, version, host, port, rconPort, rconPassword, seedText, seedLabel ->
                viewModel.createServerRoom(
                    name = name,
                    motd = motd,
                    mode = mode,
                    software = software,
                    version = version,
                    host = host,
                    port = port,
                    rconPort = rconPort,
                    rconPassword = rconPassword,
                    seedText = seedText,
                    seedLabel = seedLabel
                )
                showCreateDialog = false
            }
        )
    }

    val roomForStaff = activeRoom
    if (showStaffDialog && roomForStaff != null) {
        RoomAccessDialog(
            room = roomForStaff,
            currentRole = currentRole,
            members = roomMembers,
            grants = myRoleGrants,
            onGenerateGrant = { role -> viewModel.createRoleAccessCode(role) },
            onRevokeGrant = viewModel::revokeRoleAccessCode,
            onChangeMemberRole = viewModel::changeMemberRole,
            onRemoveMember = viewModel::revokeMember,
            onDismiss = { showStaffDialog = false }
        )
    }
}
