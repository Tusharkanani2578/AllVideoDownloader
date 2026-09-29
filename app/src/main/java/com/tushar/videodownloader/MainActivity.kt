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
import androidx.compose.runtime.getValue
import androidx.core.content.getSystemService
import androidx.lifecycle.compose.LifecycleResumeEffect
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.tushar.videodownloader.ui.HomeScreen
import com.tushar.videodownloader.ui.HomeViewModel
import com.tushar.videodownloader.ui.theme.AllVideoDownloaderTheme

class MainActivity : ComponentActivity() {

    private val viewModel: HomeViewModel by viewModels()

    /**
     * Notification permission is requested, not required.
     *
     * Downloads run either way; without the permission the user simply loses the
     * progress notification, so a denial must never block the feature.
     */
    private val notificationPermission =
        registerForActivityResult(ActivityResultContracts.RequestPermission()) { /* optional */ }

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
                val state by viewModel.uiState.collectAsStateWithLifecycle()

                // Clipboard is read only while the app is focused — Android 10+ blocks
                // background reads, and doing it on focus is also the honest moment: the
                // user has just returned from copying a link.
                LifecycleResumeEffect(Unit) {
                    viewModel.onClipboardChanged(readClipboardText())
                    onPauseOrDispose { }
                }

                HomeScreen(
                    state = state,
                    onUrlChanged = viewModel::onUrlChanged,
                    onFetch = viewModel::onFetchClicked,
                    onPasteAndFetch = viewModel::onPasteAndFetch,
                    onUseClipboardSuggestion = viewModel::onUseClipboardSuggestion,
                    onQualitySelected = viewModel::onQualitySelected,
                    onDownload = viewModel::onDownloadClicked,
                    onCancel = viewModel::onCancelClicked,
                    onRetry = viewModel::onRetry,
                    onDismissResult = viewModel::onDismissResult,
                )
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
