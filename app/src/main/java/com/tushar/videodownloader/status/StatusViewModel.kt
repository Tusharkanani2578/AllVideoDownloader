package com.tushar.videodownloader.status

import android.app.Application
import android.net.Uri
import com.tushar.videodownloader.R
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.tushar.videodownloader.ServiceLocator
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

/** Drives the status screen: grant access, list what WhatsApp cached, save a copy. */
class StatusViewModel(application: Application) : AndroidViewModel(application) {

    private val repository = ServiceLocator.statusRepository(application)

    private val _uiState = MutableStateFlow(StatusUiState())
    val uiState: StateFlow<StatusUiState> = _uiState.asStateFlow()

    /**
     * Refreshes on every visit to the screen.
     *
     * WhatsApp writes a status the moment it is viewed and clears it about a day later,
     * so the folder changes while the app is in the background — a cached list would go
     * stale almost immediately.
     */
    fun refresh() {
        val hasAccess = repository.hasAccess()
        _uiState.update { it.copy(hasAccess = hasAccess, isLoading = hasAccess) }
        if (!hasAccess) return

        viewModelScope.launch {
            val statuses = repository.loadStatuses()
            _uiState.update { it.copy(statuses = statuses, isLoading = false) }
        }
    }

    fun initialFolderUri() = repository.initialFolderUri()

    fun onAccessGranted(treeUri: Uri) {
        runCatching { repository.onAccessGranted(treeUri) }
            .onSuccess { refresh() }
            .onFailure {
                _uiState.update { state -> state.copy(message = string(R.string.status_access_failed)) }
            }
    }

    fun onSaveClicked(item: StatusItem) {
        if (_uiState.value.savingUri != null) return
        _uiState.update { it.copy(savingUri = item.uri.toString(), message = null) }

        viewModelScope.launch {
            repository.save(item)
                .onSuccess { savedName ->
                    _uiState.update {
                        it.copy(
                            savingUri = null,
                            savedNames = it.savedNames + item.name,
                            message = string(R.string.status_save_success, savedName),
                        )
                    }
                }
                .onFailure {
                    // Deliberately not the exception's own text: the download path routes
                    // every failure through DownloadError so a raw IO message never reaches
                    // the screen, and this path should read no differently.
                    _uiState.update {
                        it.copy(
                            savingUri = null,
                            message = string(R.string.status_save_failed, item.name),
                        )
                    }
                }
        }
    }

    fun onMessageShown() {
        _uiState.update { it.copy(message = null) }
    }

    private fun string(resId: Int, vararg args: Any): String =
        getApplication<Application>().getString(resId, *args)
}
