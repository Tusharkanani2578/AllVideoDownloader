package com.tushar.videodownloader.ui

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.tushar.videodownloader.ServiceLocator
import com.tushar.videodownloader.core.DownloadError
import com.tushar.videodownloader.core.UrlValidator
import com.tushar.videodownloader.core.ValidationException
import com.tushar.videodownloader.download.DownloadProgress
import com.tushar.videodownloader.download.DownloadService
import com.tushar.videodownloader.resolver.ResolveException
import com.tushar.videodownloader.resolver.MediaOption
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

/**
 * Drives the paste → fetch → select → download flow. The transfer itself runs in
 * [DownloadService], which is what lets it outlive this ViewModel.
 */
class HomeViewModel(application: Application) : AndroidViewModel(application) {

    private val _uiState = MutableStateFlow(HomeUiState())
    val uiState: StateFlow<HomeUiState> = _uiState.asStateFlow()

    private val registry = ServiceLocator.resolverRegistry
    private val networkMonitor = ServiceLocator.networkMonitor(application)

    /** The in-flight resolve, so a newer one can cancel it. */
    private var resolveJob: Job? = null

    init {
        observeDownloadProgress()
    }

    private fun observeDownloadProgress() {
        viewModelScope.launch {
            DownloadService.progress.collect { progress ->
                if (progress == null) return@collect

                _uiState.update { state ->
                    when (progress) {
                        is DownloadProgress.Completed -> state.copy(
                            stage = HomeUiState.Stage.Done,
                            download = progress,
                            error = null,
                        )

                        is DownloadProgress.Failed -> state.copy(
                            stage = HomeUiState.Stage.Resolved,
                            download = null,
                            error = progress.error,
                        )

                        else -> state.copy(
                            stage = HomeUiState.Stage.Downloading,
                            download = progress,
                            error = null,
                        )
                    }
                }
            }
        }
    }

    fun onUrlChanged(value: String) {
        // Editing clears the previous result entirely — including a finished download.
        // Leaving it would strand a "saved as …" card, with its Open and Share actions
        // still pointing at the previous video, above a freshly pasted link.
        resolveJob?.cancel()
        clearFinishedDownload()
        _uiState.update {
            it.copy(
                urlInput = value,
                error = null,
                media = null,
                preview = null,
                download = null,
                stage = HomeUiState.Stage.Idle,
            )
        }
    }

    /**
     * Drops a completed or failed download from the shared service state.
     *
     * The service holds the last result in a process-wide StateFlow, so without this a
     * new ViewModel — after a rotation, say — would adopt the previous download as if
     * it had just finished. An in-flight download is left untouched.
     */
    private fun clearFinishedDownload() {
        val current = DownloadService.progress.value
        if (current is DownloadProgress.Completed || current is DownloadProgress.Failed) {
            DownloadService.clear()
        }
    }

    fun onFetchClicked() {
        val url = UrlValidator.validate(_uiState.value.urlInput).getOrElse { throwable ->
            _uiState.update { it.copy(error = (throwable as ValidationException).error) }
            return
        }

        if (!networkMonitor.isOnline()) {
            _uiState.update { it.copy(error = DownloadError.NoNetwork) }
            return
        }

        // Resolving a new link retires the previous download's result card.
        clearFinishedDownload()
        _uiState.update {
            it.copy(stage = HomeUiState.Stage.Fetching, error = null, download = null)
        }

        // A second fetch while one is in flight would otherwise race: the older request
        // can land last and overwrite the newer result, leaving the user looking at one
        // link and downloading another.
        resolveJob?.cancel()
        resolveJob = viewModelScope.launch {
            registry.resolve(url)
                .onSuccess { media ->
                    _uiState.update {
                        it.copy(
                            stage = HomeUiState.Stage.Resolved,
                            media = media,
                            preview = null,
                            selection = Selection.One(media.defaultOption),
                            error = null,
                        )
                    }
                }
                .onFailure { throwable ->
                    val failure = throwable as? ResolveException
                    _uiState.update {
                        it.copy(
                            stage = HomeUiState.Stage.Idle,
                            media = null,
                            // Keep whatever the resolver read, so an undownloadable
                            // link still shows what it points at.
                            preview = failure?.preview,
                            error = failure?.error ?: DownloadError.Unexpected(throwable),
                        )
                    }
                }
        }
    }

    /**
     * Offers a clipboard link if it is one the app could act on. Suppressed when it is
     * already in the field or a result is on screen.
     */
    fun onClipboardChanged(clipboardText: String?) {
        val text = clipboardText?.trim().orEmpty()
        val state = _uiState.value

        val isUsable = text.isNotBlank() &&
            UrlValidator.validate(text).isSuccess &&
            text != state.urlInput &&
            state.media == null &&
            state.stage == HomeUiState.Stage.Idle

        _uiState.update { it.copy(clipboardSuggestion = if (isUsable) text else null) }
    }

    fun onUseClipboardSuggestion() {
        val suggestion = _uiState.value.clipboardSuggestion ?: return
        _uiState.update { it.copy(urlInput = suggestion, clipboardSuggestion = null, error = null) }
        onFetchClicked()
    }

    /** Paste and resolve together — pasting then pressing Fetch has no decision in it. */
    fun onPasteAndFetch(clipboardText: String?) {
        val text = clipboardText?.trim().orEmpty()
        if (text.isBlank()) return

        clearFinishedDownload()
        _uiState.update {
            it.copy(
                urlInput = text,
                clipboardSuggestion = null,
                error = null,
                media = null,
                preview = null,
                download = null,
            )
        }
        onFetchClicked()
    }

    fun onSelect(selection: Selection) {
        _uiState.update { it.copy(selection = selection, error = null) }
    }

    fun onDownloadClicked() {
        val state = _uiState.value
        val media = state.media ?: return

        val chosen = when (val selection = state.selection) {
            is Selection.One -> listOf(selection.option)
            Selection.All -> media.options
            null -> return
        }
        DownloadService.start(getApplication(), media, chosen)
    }

    fun onCancelClicked() {
        DownloadService.cancel(getApplication())
    }

    fun onDismissResult() {
        DownloadService.clear()
        _uiState.update {
            it.copy(
                urlInput = "",
                stage = HomeUiState.Stage.Idle,
                media = null,
                preview = null,
                selection = null,
                download = null,
                error = null,
                clipboardSuggestion = null,
            )
        }
    }

    fun onRetry() {
        _uiState.update { it.copy(error = null) }
        if (_uiState.value.media == null) onFetchClicked() else onDownloadClicked()
    }
}
