package com.tushar.videodownloader.status

/**
 * Everything the status screen renders, in one immutable snapshot.
 *
 * @param savingUri the item currently being copied, so only its tile shows a spinner.
 * @param savedNames what has already been saved this session, so those tiles read
 *   "Saved" instead of offering the same download again.
 */
data class StatusUiState(
    val hasAccess: Boolean = false,
    val isLoading: Boolean = false,
    val statuses: List<StatusItem> = emptyList(),
    val savingUri: String? = null,
    val savedNames: Set<String> = emptySet(),
    val message: String? = null,
) {
    /** Access granted, load finished, and WhatsApp has nothing cached right now. */
    val isEmpty: Boolean get() = hasAccess && !isLoading && statuses.isEmpty()
}
