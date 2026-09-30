package com.tushar.videodownloader.status

import android.app.Application
import android.net.Uri
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
                _uiState.update { state ->
                    state.copy(message = "Couldn't keep access to that folder. Please try again.")
                }
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
                            message = "Saved to your gallery as $savedName",
                        )
                    }
                }
                .onFailure { error ->
                    _uiState.update {
                        it.copy(
                            savingUri = null,
                            message = "Couldn't save ${item.name}. ${error.message.orEmpty()}".trim(),
                        )
                    }
                }
        }
    }

    fun onMessageShown() {
        _uiState.update { it.copy(message = null) }
    }
}
