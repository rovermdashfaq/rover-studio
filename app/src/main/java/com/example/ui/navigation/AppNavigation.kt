package com.example.ui.navigation

import androidx.compose.foundation.Image
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
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.FormatListBulleted
import androidx.compose.material.icons.automirrored.rounded.FormatListBulleted
import androidx.compose.material.icons.outlined.Badge
import androidx.compose.material.icons.outlined.CloudSync
import androidx.compose.material.icons.outlined.MovieCreation
import androidx.compose.material.icons.rounded.Badge
import androidx.compose.material.icons.rounded.CloudSync
import androidx.compose.material.icons.rounded.MovieCreation
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.NavigationRail
import androidx.compose.material3.NavigationRailItem
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.credentials.CredentialManager
import androidx.lifecycle.ViewModelProvider.AndroidViewModelFactory.Companion.APPLICATION_KEY
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import androidx.navigation.NavGraph.Companion.findStartDestination
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.currentBackStackEntryAsState
import androidx.navigation.compose.rememberNavController
import com.example.R
import com.example.auth.AuthScreen
import com.example.auth.authStateFlow
import com.example.auth.signOutUser
import com.example.data.remote.RoverPublicSyncService
import com.example.data.repository.StudioRepository
import com.example.ui.components.ExportedImagePreviewDialog
import com.example.ui.components.ExportedVideoPreviewDialog
import com.example.ui.screens.AssetsSyncScreen
import com.example.ui.screens.BulletinScreen
import com.example.ui.screens.CreditScreen
import com.example.ui.screens.NameplateScreen
import com.example.ui.viewmodel.StudioViewModel
import com.google.firebase.Firebase
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.auth.FirebaseUser
import com.google.firebase.auth.auth
import com.google.firebase.firestore.FirebaseFirestore

const val ROUTE_BULLETIN = "bulletin_studio"
const val ROUTE_NAMEPLATE = "nameplate_studio"
const val ROUTE_CREDITS = "credit_studio"
const val ROUTE_ASSETS = "assets_sync"

private data class StudioTab(
    val route: String,
    val label: String,
    val selectedIcon: ImageVector,
    val unselectedIcon: ImageVector,
    val testTag: String
)

private val STUDIO_TABS = listOf(
    StudioTab(
        route = ROUTE_BULLETIN,
        label = "Bulletin",
        selectedIcon = Icons.Rounded.MovieCreation,
        unselectedIcon = Icons.Outlined.MovieCreation,
        testTag = "nav_bulletin"
    ),
    StudioTab(
        route = ROUTE_NAMEPLATE,
        label = "Nameplate",
        selectedIcon = Icons.Rounded.Badge,
        unselectedIcon = Icons.Outlined.Badge,
        testTag = "nav_nameplate"
    ),
    StudioTab(
        route = ROUTE_CREDITS,
        label = "Credits",
        selectedIcon = Icons.AutoMirrored.Rounded.FormatListBulleted,
        unselectedIcon = Icons.AutoMirrored.Outlined.FormatListBulleted,
        testTag = "nav_credits"
    ),
    StudioTab(
        route = ROUTE_ASSETS,
        label = "Assets",
        selectedIcon = Icons.Rounded.CloudSync,
        unselectedIcon = Icons.Outlined.CloudSync,
        testTag = "nav_assets"
    )
)

@Composable
fun AppNavigation(auth: FirebaseAuth = Firebase.auth) {
    val currentUser by auth.authStateFlow()
        .collectAsStateWithLifecycle(initialValue = auth.currentUser)

    val user = currentUser
    if (user == null) {
        AuthScreen(onAuthSuccess = {})
    } else {
        key(user.uid) {
            StudioMainShell(user = user)
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun StudioMainShell(user: FirebaseUser) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val credentialManager = remember(context) { CredentialManager.create(context) }

    val viewModel: StudioViewModel = viewModel(
        key = user.uid,
        factory = viewModelFactory {
            initializer {
                val app = checkNotNull(this[APPLICATION_KEY]) {
                    "APPLICATION_KEY missing from CreationExtras"
                }
                val databaseId = app.getString(R.string.firestore_database_id)
                val db = FirebaseFirestore.getInstance(databaseId)
                StudioViewModel(
                    repository = StudioRepository(db),
                    syncService = RoverPublicSyncService(),
                    currentUserId = user.uid
                )
            }
        }
    )

    val navController = rememberNavController()
    val backStackEntry by navController.currentBackStackEntryAsState()
    val currentRoute = backStackEntry?.destination?.route ?: ROUTE_BULLETIN

    val snackbarHostState = remember { SnackbarHostState() }
    val feedbackMessage by viewModel.feedbackMessage.collectAsStateWithLifecycle()
    val exportedVideo by viewModel.exportedVideoDialog.collectAsStateWithLifecycle()
    val exportedImage by viewModel.exportedImageDialog.collectAsStateWithLifecycle()

    LaunchedEffect(feedbackMessage) {
        val msg = feedbackMessage ?: return@LaunchedEffect
        snackbarHostState.showSnackbar(msg)
        viewModel.clearFeedbackMessage()
    }

    val onNavigateTab: (String) -> Unit = { route ->
        navController.navigate(route) {
            popUpTo(navController.graph.findStartDestination().id) {
                saveState = true
            }
            launchSingleTop = true
            restoreState = true
        }
    }

    BoxWithConstraints(modifier = Modifier.fillMaxSize()) {
        val useNavRail = maxWidth >= 680.dp

        Scaffold(
            contentWindowInsets = WindowInsets.safeDrawing,
            snackbarHost = { SnackbarHost(hostState = snackbarHostState) },
            topBar = {
                TopAppBar(
                    colors = TopAppBarDefaults.topAppBarColors(
                        containerColor = MaterialTheme.colorScheme.primary,
                        titleContentColor = MaterialTheme.colorScheme.onPrimary
                    ),
                    title = {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Image(
                                painter = painterResource(id = R.drawable.rover_bulletin_logo),
                                contentDescription = "Rover Studio Emblem",
                                contentScale = ContentScale.Crop,
                                modifier = Modifier
                                    .size(36.dp)
                                    .clip(CircleShape)
                            )
                            Spacer(modifier = Modifier.width(10.dp))
                            Column {
                                Text(
                                    text = "রোভার স্টুডিও • Bangladesh Scouts",
                                    style = MaterialTheme.typography.titleMedium,
                                    fontWeight = FontWeight.Bold
                                )
                                Text(
                                    text = "Rover Region Broadcast Suite (2K • High FPS)",
                                    style = MaterialTheme.typography.labelSmall,
                                    color = MaterialTheme.colorScheme.onPrimary.copy(alpha = 0.85f)
                                )
                            }
                        }
                    }
                )
            },
            bottomBar = {
                if (!useNavRail) {
                    NavigationBar {
                        STUDIO_TABS.forEach { tab ->
                            val selected = currentRoute == tab.route
                            NavigationBarItem(
                                selected = selected,
                                onClick = { onNavigateTab(tab.route) },
                                icon = {
                                    Icon(
                                        imageVector = if (selected) tab.selectedIcon else tab.unselectedIcon,
                                        contentDescription = tab.label
                                    )
                                },
                                label = { Text(tab.label) },
                                modifier = Modifier.testTag(tab.testTag)
                            )
                        }
                    }
                }
            }
        ) { innerPadding ->
            Row(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(innerPadding)
            ) {
                if (useNavRail) {
                    NavigationRail {
                        STUDIO_TABS.forEach { tab ->
                            val selected = currentRoute == tab.route
                            NavigationRailItem(
                                selected = selected,
                                onClick = { onNavigateTab(tab.route) },
                                icon = {
                                    Icon(
                                        imageVector = if (selected) tab.selectedIcon else tab.unselectedIcon,
                                        contentDescription = tab.label
                                    )
                                },
                                label = { Text(tab.label) },
                                modifier = Modifier.testTag(tab.testTag)
                            )
                        }
                    }
                }

                Box(modifier = Modifier.weight(1f).fillMaxSize()) {
                    NavHost(
                        navController = navController,
                        startDestination = ROUTE_BULLETIN
                    ) {
                        composable(ROUTE_BULLETIN) {
                            BulletinScreen(viewModel = viewModel)
                        }
                        composable(ROUTE_NAMEPLATE) {
                            NameplateScreen(viewModel = viewModel)
                        }
                        composable(ROUTE_CREDITS) {
                            CreditScreen(viewModel = viewModel)
                        }
                        composable(ROUTE_ASSETS) {
                            AssetsSyncScreen(
                                viewModel = viewModel,
                                userEmail = user.email,
                                userDisplayName = user.displayName,
                                onSignOutClick = {
                                    signOutUser(
                                        credentialManager = credentialManager,
                                        scope = scope
                                    )
                                }
                            )
                        }
                    }
                }
            }
        }
    }

    exportedVideo?.let { videoData ->
        ExportedVideoPreviewDialog(
            data = videoData,
            onDismiss = viewModel::dismissExportedVideoDialog
        )
    }

    exportedImage?.let { imageData ->
        ExportedImagePreviewDialog(
            data = imageData,
            onDismiss = viewModel::dismissExportedImageDialog
        )
    }
}
