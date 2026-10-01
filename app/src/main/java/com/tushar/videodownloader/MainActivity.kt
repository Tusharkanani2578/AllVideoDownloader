package com.tushar.videodownloader

import android.Manifest
import android.content.ClipboardManager
import android.content.Intent
import android.os.Build
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.result.contract.ActivityResultContracts
import androidx.activity.viewModels
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Tab
import androidx.compose.material3.TabRow
import androidx.compose.material3.Text
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.core.content.getSystemService
import androidx.lifecycle.compose.LifecycleResumeEffect
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.tushar.videodownloader.status.StatusScreen
import com.tushar.videodownloader.status.StatusViewModel
import com.tushar.videodownloader.ui.HomeScreen
import com.tushar.videodownloader.ui.HomeViewModel
import com.tushar.videodownloader.ui.theme.AllVideoDownloaderTheme

class MainActivity : ComponentActivity() {

    private val viewModel: HomeViewModel by viewModels()
    private val statusViewModel: StatusViewModel by viewModels()

    /**
     * Notification permission is requested, not required.
     *
     * Downloads run either way; without the permission the user simply loses the
     * progress notification, so a denial must never block the feature.
     */
    private val notificationPermission =
        registerForActivityResult(ActivityResultContracts.RequestPermission()) { /* optional */ }

    /**
     * Folder access for the status screen.
     *
     * WhatsApp's status folder is hidden and, from Android 11, unreachable by path, so
     * the user points the system picker at it once and the grant is persisted.
     */
    private val statusFolderAccess =
        registerForActivityResult(ActivityResultContracts.OpenDocumentTree()) { treeUri ->
            treeUri?.let(statusViewModel::onAccessGranted)
        }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()

        requestNotificationPermissionIfNeeded()

        // Only on a genuine launch. The ViewModel outlives a configuration change while
        // getIntent() still returns the original share, so replaying it on every
        // recreation would wipe a resolved result the moment the user rotated.
        if (savedInstanceState == null) handleSharedLink(intent)

        setContent {
            AllVideoDownloaderTheme {
                var selectedTab by rememberSaveable { mutableIntStateOf(0) }
                val snackbarHost = remember { SnackbarHostState() }

                val homeState by viewModel.uiState.collectAsStateWithLifecycle()
                val statusState by statusViewModel.uiState.collectAsStateWithLifecycle()

                LifecycleResumeEffect(Unit) {
                    // Clipboard is read only while the app is focused — Android 10+ blocks
                    // background reads, and it is also the honest moment: the user has
                    // just returned from copying a link.
                    viewModel.onClipboardChanged(readClipboardText())
                    // WhatsApp writes statuses as they are viewed and clears them after a
                    // day, so the folder is re-read on every return rather than cached.
                    statusViewModel.refresh()
                    onPauseOrDispose { }
                }

                statusState.message?.let { message ->
                    LaunchedEffect(message) {
                        snackbarHost.showSnackbar(message)
                        statusViewModel.onMessageShown()
                    }
                }

                // The tab row is now the topmost element, so it owns the status bar
                // inset. Screens below it consume none of their own top inset.
                Column(
                    Modifier
                        .fillMaxSize()
                        .background(MaterialTheme.colorScheme.surface)
                        .statusBarsPadding(),
                ) {
                    TabRow(
                        selectedTabIndex = selectedTab,
                        containerColor = MaterialTheme.colorScheme.surface,
                    ) {
                        Tab(
                            selected = selectedTab == 0,
                            onClick = { selectedTab = 0 },
                            text = { Text(stringResource(R.string.tab_download)) },
                        )
                        Tab(
                            selected = selectedTab == 1,
                            onClick = { selectedTab = 1 },
                            text = { Text(stringResource(R.string.tab_status)) },
                        )
                    }

                    when (selectedTab) {
                        0 -> HomeScreen(
                            state = homeState,
                            onUrlChanged = viewModel::onUrlChanged,
                            onFetch = viewModel::onFetchClicked,
                            onPasteAndFetch = viewModel::onPasteAndFetch,
                            onUseClipboardSuggestion = viewModel::onUseClipboardSuggestion,
                            onSelect = viewModel::onSelect,
                            onDownload = viewModel::onDownloadClicked,
                            onCancel = viewModel::onCancelClicked,
                            onRetry = viewModel::onRetry,
                            onDismissResult = viewModel::onDismissResult,
                        )

                        else -> Scaffold(
                            snackbarHost = { SnackbarHost(snackbarHost) },
                        ) { padding ->
                            StatusScreen(
                                state = statusState,
                                onGrantAccess = {
                                    statusFolderAccess.launch(statusViewModel.initialFolderUri())
                                },
                                onSave = statusViewModel::onSaveClicked,
                                modifier = Modifier.padding(padding),
                            )
                        }
                    }
                }
            }
        }
    }

    /** Handles "Share to this app" while it is already running. */
    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        // Without this, getIntent() keeps returning the original launch intent, so a
        // later recreation would replay that one instead of the link just shared.
        setIntent(intent)
        handleSharedLink(intent)
    }

    private fun handleSharedLink(intent: Intent?) {
        if (intent?.action != Intent.ACTION_SEND) return
        val shared = intent.getStringExtra(Intent.EXTRA_TEXT) ?: return
        // Consumed, so no other path can apply the same link twice.
        intent.removeExtra(Intent.EXTRA_TEXT)
        viewModel.onUrlChanged(shared)
    }

    /** Returns the clipboard's plain text, or null when it holds nothing usable. */
    private fun readClipboardText(): String? {
        val clipboard = getSystemService<ClipboardManager>() ?: return null
        val clip = clipboard.primaryClip ?: return null
        if (clip.itemCount == 0) return null
        return clip.getItemAt(0).coerceToText(this)?.toString()
    }

    private fun requestNotificationPermissionIfNeeded() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            notificationPermission.launch(Manifest.permission.POST_NOTIFICATIONS)
        }
    }
}
