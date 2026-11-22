package com.example.ui

import android.app.Application
import androidx.activity.compose.BackHandler
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.consumeWindowInsets
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.systemBars
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.Logout
import androidx.compose.material.icons.filled.AccountCircle
import androidx.compose.material.icons.filled.Code
import androidx.compose.material.icons.filled.History
import androidx.compose.material.icons.filled.Hub
import androidx.compose.material.icons.filled.Psychology
import androidx.compose.material.icons.filled.Terminal
import androidx.compose.material.icons.outlined.Code
import androidx.compose.material.icons.outlined.History
import androidx.compose.material.icons.outlined.Hub
import androidx.compose.material.icons.outlined.Language
import androidx.compose.material.icons.outlined.Psychology
import androidx.compose.material.icons.outlined.Terminal
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
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
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.material3.minimumInteractiveComponentSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.credentials.CredentialManager
import androidx.lifecycle.ViewModelProvider.AndroidViewModelFactory.Companion.APPLICATION_KEY
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import coil.compose.AsyncImage
import com.example.R
import com.example.core.AppLanguage
import com.example.core.ArabicStrings
import com.example.core.EnglishStrings
import com.example.core.LocalAppLanguage
import com.example.core.LocalCoreStrings
import com.example.data.CognitiveAiRepository
import com.example.data.FirestoreSyncRepository
import com.example.domain.ReasoningLevel
import com.example.ui.auth.AuthScreen
import com.example.ui.auth.signOutUser
import com.example.ui.components.ReasoningLevelSheet
import com.example.ui.components.ReasoningTierPill
import com.example.ui.screens.AgentsScreen
import com.example.ui.screens.CodeMathScreen
import com.example.ui.screens.HistoryScreen
import com.example.ui.screens.IdeBridgeScreen
import com.example.ui.screens.WorkspaceScreen
import com.example.ui.theme.CoreMindTheme
import com.example.ui.theme.CrimsonAlert
import com.example.ui.theme.LocalCyberPalette
import com.example.ui.viewmodel.AgentsViewModel
import com.example.ui.viewmodel.HistoryViewModel
import com.example.ui.viewmodel.IdeBridgeViewModel
import com.example.ui.viewmodel.WorkspaceViewModel
import com.google.firebase.Firebase
import com.google.firebase.FirebaseApp
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.auth.FirebaseUser
import com.google.firebase.auth.auth
import com.google.firebase.firestore.FirebaseFirestore
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.callbackFlow

enum class AppTab {
    WORKSPACE,
    IDE_BRIDGE,
    AGENTS,
    CODE_LAB,
    HISTORY
}

private fun FirebaseAuth.authStateFlow(): Flow<FirebaseUser?> = callbackFlow {
    val listener = FirebaseAuth.AuthStateListener { auth ->
        trySend(auth.currentUser)
    }
    addAuthStateListener(listener)
    awaitClose { removeAuthStateListener(listener) }
}

@Composable
fun CoreMindApp(auth: FirebaseAuth = Firebase.auth) {
    var language by rememberSaveable { mutableStateOf(AppLanguage.AR) }
    val strings = if (language == AppLanguage.AR) ArabicStrings else EnglishStrings

    val currentUser by auth.authStateFlow().collectAsStateWithLifecycle(initialValue = auth.currentUser)

    CoreMindTheme {
        CompositionLocalProvider(
            LocalAppLanguage provides language,
            LocalCoreStrings provides strings,
            LocalLayoutDirection provides language.layoutDirection
        ) {
            val user = currentUser
            if (user == null) {
                AuthScreen(
                    onToggleLanguage = {
                        language = if (language == AppLanguage.AR) AppLanguage.EN else AppLanguage.AR
                    },
                    onAuthSuccess = {}
                )
            } else {
                key(user.uid) {
                    AuthenticatedCoreShell(
                        user = user,
                        onToggleLanguage = {
                            language = if (language == AppLanguage.AR) AppLanguage.EN else AppLanguage.AR
                        }
                    )
                }
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun AuthenticatedCoreShell(
    user: FirebaseUser,
    onToggleLanguage: () -> Unit
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val credentialManager = remember(context) { CredentialManager.create(context) }
    val strings = LocalCoreStrings.current
    val language = LocalAppLanguage.current

    val uid = user.uid
    val workspaceViewModel: WorkspaceViewModel = viewModel(
        key = "workspace_$uid",
        factory = viewModelFactory {
            initializer {
                val app = checkNotNull(this[APPLICATION_KEY]) as Application
                val db = FirebaseFirestore.getInstance(app.getString(R.string.firestore_database_id))
                WorkspaceViewModel(
                    syncRepository = FirestoreSyncRepository(db),
                    aiRepository = CognitiveAiRepository(),
                    currentUserId = uid
                )
            }
        }
    )

    val ideBridgeViewModel: IdeBridgeViewModel = viewModel(
        key = "ide_$uid",
        factory = viewModelFactory {
            initializer {
                val app = checkNotNull(this[APPLICATION_KEY]) as Application
                val dbId = app.getString(R.string.firestore_database_id)
                val db = FirebaseFirestore.getInstance(dbId)
                val projId = FirebaseApp.getInstance().options.projectId.orEmpty()
                IdeBridgeViewModel(
                    syncRepository = FirestoreSyncRepository(db),
                    aiRepository = CognitiveAiRepository(),
                    currentUserId = uid,
                    databaseId = dbId,
                    projectId = projId
                )
            }
        }
    )

    val agentsViewModel: AgentsViewModel = viewModel(
        key = "agents_$uid",
        factory = viewModelFactory {
            initializer {
                val app = checkNotNull(this[APPLICATION_KEY]) as Application
                val db = FirebaseFirestore.getInstance(app.getString(R.string.firestore_database_id))
                AgentsViewModel(
                    syncRepository = FirestoreSyncRepository(db),
                    aiRepository = CognitiveAiRepository(),
                    currentUserId = uid
                )
            }
        }
    )

    val historyViewModel: HistoryViewModel = viewModel(
        key = "history_$uid",
        factory = viewModelFactory {
            initializer {
                val app = checkNotNull(this[APPLICATION_KEY]) as Application
                val db = FirebaseFirestore.getInstance(app.getString(R.string.firestore_database_id))
                HistoryViewModel(syncRepository = FirestoreSyncRepository(db))
            }
        }
    )

    var selectedTab by rememberSaveable { mutableStateOf(AppTab.WORKSPACE) }
    var showReasoningSheet by remember { mutableStateOf(false) }
    var showAccountDialog by remember { mutableStateOf(false) }

    val activeReasoningLevel by workspaceViewModel.reasoningLevel.collectAsStateWithLifecycle()

    BackHandler(enabled = selectedTab != AppTab.WORKSPACE) {
        selectedTab = AppTab.WORKSPACE
    }

    val navItems = listOf(
        NavTabItem(AppTab.WORKSPACE, strings.navWorkspace, Icons.Filled.Psychology, Icons.Outlined.Psychology),
        NavTabItem(AppTab.IDE_BRIDGE, strings.navIdeBridge, Icons.Filled.Terminal, Icons.Outlined.Terminal),
        NavTabItem(AppTab.AGENTS, strings.navAgents, Icons.Filled.Hub, Icons.Outlined.Hub),
        NavTabItem(AppTab.CODE_LAB, strings.navCodeLab, Icons.Filled.Code, Icons.Outlined.Code),
        NavTabItem(AppTab.HISTORY, strings.navHistory, Icons.Filled.History, Icons.Outlined.History)
    )

    CoreMindTheme(reasoningLevel = activeReasoningLevel) {
        val cyber = LocalCyberPalette.current

        BoxWithConstraints(modifier = Modifier.fillMaxSize()) {
            val isWideScreen = maxWidth >= 680.dp

            Scaffold(
                modifier = Modifier.fillMaxSize(),
                containerColor = cyber.background,
                contentWindowInsets = WindowInsets.systemBars,
                topBar = {
                    TopAppBar(
                        colors = TopAppBarDefaults.topAppBarColors(
                            containerColor = cyber.surface,
                            titleContentColor = MaterialTheme.colorScheme.onSurface
                        ),
                    title = {
                        Column {
                            Text(
                                text = strings.appTitle,
                                style = MaterialTheme.typography.titleLarge,
                                fontWeight = FontWeight.Bold,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis
                            )
                            Text(
                                text = strings.appSubtitle,
                                style = MaterialTheme.typography.labelSmall,
                                color = cyber.accent,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis
                            )
                        }
                    },
                    actions = {
                        ReasoningTierPill(
                            level = activeReasoningLevel,
                            onClick = { showReasoningSheet = true }
                        )
                        Spacer(Modifier.width(6.dp))
                        Surface(
                            onClick = onToggleLanguage,
                            modifier = Modifier
                                .minimumInteractiveComponentSize()
                                .testTag("shell_language_toggle"),
                            shape = RoundedCornerShape(50),
                            color = cyber.surfaceElevated,
                            border = BorderStroke(1.dp, cyber.border)
                        ) {
                            Row(
                                modifier = Modifier.padding(horizontal = 10.dp, vertical = 6.dp),
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(4.dp)
                            ) {
                                Icon(
                                    imageVector = Icons.Outlined.Language,
                                    contentDescription = language.toggleLabel,
                                    tint = cyber.accent,
                                    modifier = Modifier.size(15.dp)
                                )
                                Text(
                                    text = language.toggleLabel,
                                    style = MaterialTheme.typography.labelMedium,
                                    color = cyber.accent
                                )
                            }
                        }
                        IconButton(
                            onClick = { showAccountDialog = true },
                            modifier = Modifier.testTag("account_menu_button")
                        ) {
                            if (user.photoUrl != null) {
                                AsyncImage(
                                    model = user.photoUrl,
                                    contentDescription = user.displayName ?: "Account",
                                    contentScale = ContentScale.Crop,
                                    modifier = Modifier
                                        .size(30.dp)
                                        .clip(CircleShape)
                                )
                            } else {
                                Icon(
                                    imageVector = Icons.Filled.AccountCircle,
                                    contentDescription = user.displayName ?: "Account",
                                    tint = cyber.accent
                                )
                            }
                        }
                    }
                )
            },
            bottomBar = {
                if (!isWideScreen) {
                    NavigationBar(
                        containerColor = cyber.surface,
                        tonalElevation = 0.dp
                    ) {
                        navItems.forEach { item ->
                            val selected = selectedTab == item.tab
                            NavigationBarItem(
                                selected = selected,
                                onClick = { selectedTab = item.tab },
                                icon = {
                                    Icon(
                                        imageVector = if (selected) item.selectedIcon else item.unselectedIcon,
                                        contentDescription = item.label
                                    )
                                },
                                label = {
                                    Text(
                                        text = item.label,
                                        maxLines = 1,
                                        overflow = TextOverflow.Ellipsis
                                    )
                                },
                                modifier = Modifier.testTag("nav_tab_${item.tab.name.lowercase()}"),
                                colors = NavigationBarItemDefaults.colors(
                                    selectedIconColor = cyber.accent,
                                    selectedTextColor = cyber.accent,
                                    indicatorColor = cyber.accentContainer,
                                    unselectedIconColor = MaterialTheme.colorScheme.onSurfaceVariant,
                                    unselectedTextColor = MaterialTheme.colorScheme.onSurfaceVariant
                                )
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
                    .consumeWindowInsets(innerPadding)
                    .imePadding()
            ) {
                if (isWideScreen) {
                    NavigationRail(
                        modifier = Modifier.fillMaxHeight(),
                        containerColor = cyber.surface
                    ) {
                        navItems.forEach { item ->
                            val selected = selectedTab == item.tab
                            NavigationRailItem(
                                selected = selected,
                                onClick = { selectedTab = item.tab },
                                icon = {
                                    Icon(
                                        imageVector = if (selected) item.selectedIcon else item.unselectedIcon,
                                        contentDescription = item.label
                                    )
                                },
                                label = { Text(item.label, maxLines = 1) },
                                colors = NavigationRailItemDefaults.colors(
                                    selectedIconColor = cyber.accent,
                                    selectedTextColor = cyber.accent,
                                    indicatorColor = cyber.accentContainer
                                )
                            )
                        }
                    }
                }

                Box(modifier = Modifier.weight(1f)) {
                    when (selectedTab) {
                        AppTab.WORKSPACE -> WorkspaceScreen(
                            viewModel = workspaceViewModel,
                            onOpenReasoningPicker = { showReasoningSheet = true }
                        )
                        AppTab.IDE_BRIDGE -> IdeBridgeScreen(
                            viewModel = ideBridgeViewModel,
                            activeReasoningLevel = activeReasoningLevel,
                            onFallbackToHigh = { workspaceViewModel.setReasoningLevel(ReasoningLevel.HIGH) }
                        )
                        AppTab.AGENTS -> AgentsScreen(
                            viewModel = agentsViewModel,
                            activeReasoningLevel = activeReasoningLevel,
                            onFallbackToHigh = { workspaceViewModel.setReasoningLevel(ReasoningLevel.HIGH) }
                        )
                        AppTab.CODE_LAB -> CodeMathScreen(
                            viewModel = workspaceViewModel
                        )
                        AppTab.HISTORY -> HistoryScreen(
                            workspaceViewModel = workspaceViewModel,
                            agentsViewModel = agentsViewModel,
                            historyViewModel = historyViewModel,
                            onOpenSessionInWorkspace = { session ->
                                workspaceViewModel.selectSession(session)
                                selectedTab = AppTab.WORKSPACE
                            }
                        )
                    }
                }
            }
        }
    }

        if (showReasoningSheet) {
            ReasoningLevelSheet(
                selectedLevel = activeReasoningLevel,
                onSelect = { workspaceViewModel.setReasoningLevel(it) },
                onDismiss = { showReasoningSheet = false }
            )
        }

        if (showAccountDialog) {
            AlertDialog(
                onDismissRequest = { showAccountDialog = false },
                containerColor = cyber.surface,
                title = {
                    Text(
                        text = user.displayName ?: user.email ?: strings.appTitle,
                        style = MaterialTheme.typography.titleLarge
                    )
                },
                text = {
                    Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                        user.email?.let {
                            Text(text = it, style = MaterialTheme.typography.bodyMedium, color = cyber.accent)
                        }
                        Text(
                            text = "UID: ${user.uid.take(12)}...",
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                },
                confirmButton = {
                    Button(
                        onClick = {
                            showAccountDialog = false
                            signOutUser(credentialManager = credentialManager, scope = scope)
                        },
                        colors = ButtonDefaults.buttonColors(containerColor = CrimsonAlert, contentColor = cyber.background),
                        modifier = Modifier.testTag("sign_out_button")
                    ) {
                        Icon(Icons.AutoMirrored.Outlined.Logout, contentDescription = strings.signOut, modifier = Modifier.size(16.dp))
                        Spacer(Modifier.width(6.dp))
                        Text(strings.signOut, fontWeight = FontWeight.Bold)
                    }
                },
                dismissButton = {
                    TextButton(onClick = { showAccountDialog = false }) {
                        Text(strings.close)
                    }
                }
            )
        }
    }
}

private data class NavTabItem(
    val tab: AppTab,
    val label: String,
    val selectedIcon: ImageVector,
    val unselectedIcon: ImageVector
)
