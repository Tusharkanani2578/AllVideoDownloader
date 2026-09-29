package com.tushar.videodownloader.ui

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.expandVertically
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.shrinkVertically
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.ContentPaste
import androidx.compose.material.icons.filled.Download
import androidx.compose.material.icons.filled.ErrorOutline
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.outlined.ContentCopy
import androidx.compose.material.icons.outlined.OpenInNew
import androidx.compose.material.icons.outlined.Share
import androidx.compose.material3.AssistChip
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FilterChipDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import coil.compose.AsyncImage
import com.tushar.videodownloader.R
import com.tushar.videodownloader.core.DownloadError
import com.tushar.videodownloader.core.toReadableSize
import com.tushar.videodownloader.core.toReadableSpeed
import com.tushar.videodownloader.download.DownloadProgress
import com.tushar.videodownloader.resolver.MediaPreview
import com.tushar.videodownloader.resolver.ResolvedMedia
import com.tushar.videodownloader.resolver.VideoQuality

private val CardShape = RoundedCornerShape(20.dp)

/**
 * The single screen of the app: paste a link, pick a quality, download.
 *
 * Stateless — it renders [state] and reports intent upward, so it can be previewed and
 * tested without a ViewModel.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun HomeScreen(
    state: HomeUiState,
    onUrlChanged: (String) -> Unit,
    onFetch: () -> Unit,
    onPasteAndFetch: (String?) -> Unit,
    onUseClipboardSuggestion: () -> Unit,
    onQualitySelected: (VideoQuality) -> Unit,
    onDownload: () -> Unit,
    onCancel: () -> Unit,
    onRetry: () -> Unit,
    onDismissResult: () -> Unit,
) {
    val clipboard = LocalClipboardManager.current
    val scrollState = rememberScrollState()

    // Results render below the input card, which on a short screen puts them under the
    // fold. Scrolling to them means the user never has to hunt for what just happened.
    LaunchedEffect(state.stage, state.error) {
        if (state.media != null || state.error != null || state.download != null) {
            scrollState.animateScrollTo(scrollState.maxValue)
        }
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = {
                    Column {
                        Text(
                            text = stringResource(R.string.app_name),
                            fontWeight = FontWeight.Bold,
                        )
                        Text(
                            text = stringResource(R.string.app_tagline),
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = MaterialTheme.colorScheme.surface,
                ),
            )
        },
    ) { padding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .verticalScroll(scrollState)
                .padding(horizontal = 16.dp, vertical = 12.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            AnimatedVisibility(
                visible = state.clipboardSuggestion != null,
                enter = fadeIn() + expandVertically(),
                exit = fadeOut() + shrinkVertically(),
            ) {
                state.clipboardSuggestion?.let { suggestion ->
                    ClipboardSuggestionCard(suggestion, onUseClipboardSuggestion)
                }
            }

            UrlInputCard(
                value = state.urlInput,
                isFetching = state.stage == HomeUiState.Stage.Fetching,
                canFetch = state.canFetch,
                onValueChange = onUrlChanged,
                onPaste = { onPasteAndFetch(clipboard.getText()?.text) },
                onFetch = onFetch,
            )

            state.error?.let { error ->
                ErrorCard(
                    error = error,
                    showRetry = state.isRetryable,
                    onRetry = onRetry,
                    onDismiss = onDismissResult,
                )
            }

            state.preview?.let { preview -> UnavailablePreviewCard(preview) }

            state.media?.let { media ->
                MediaCard(
                    media = media,
                    selectedQuality = state.selectedQuality,
                    enabled = !state.isDownloading,
                    onQualitySelected = onQualitySelected,
                )
            }

            when (val download = state.download) {
                is DownloadProgress.Preparing -> ProgressCard(null, onCancel)
                is DownloadProgress.Running -> ProgressCard(download, onCancel)
                is DownloadProgress.Completed -> CompletedCard(download, onDismissResult)
                else -> Unit
            }

            AnimatedVisibility(
                visible = state.media != null &&
                    !state.isDownloading &&
                    state.stage != HomeUiState.Stage.Done,
                enter = fadeIn() + expandVertically(),
                exit = fadeOut() + shrinkVertically(),
            ) {
                Button(
                    onClick = onDownload,
                    shape = CardShape,
                    modifier = Modifier.fillMaxWidth().height(54.dp),
                ) {
                    Icon(Icons.Default.Download, contentDescription = null)
                    Spacer(Modifier.size(10.dp))
                    Text(
                        text = stringResource(R.string.action_download),
                        fontWeight = FontWeight.SemiBold,
                    )
                }
            }

            if (state.media == null && state.preview == null && state.error == null &&
                state.stage == HomeUiState.Stage.Idle
            ) {
                EmptyState()
            }
        }
    }
}

// ------------------------------------------------------------------------- input

@Composable
private fun ClipboardSuggestionCard(suggestion: String, onUse: () -> Unit) {
    Card(
        shape = CardShape,
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.primaryContainer,
        ),
    ) {
        Row(
            modifier = Modifier.padding(start = 16.dp, end = 8.dp, top = 10.dp, bottom = 10.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Icon(
                imageVector = Icons.Outlined.ContentCopy,
                contentDescription = null,
                modifier = Modifier.size(18.dp),
                tint = MaterialTheme.colorScheme.onPrimaryContainer,
            )
            Spacer(Modifier.size(12.dp))
            Column(Modifier.weight(1f)) {
                Text(
                    text = stringResource(R.string.clipboard_detected),
                    style = MaterialTheme.typography.labelLarge,
                    fontWeight = FontWeight.SemiBold,
                    color = MaterialTheme.colorScheme.onPrimaryContainer,
                )
                Text(
                    text = suggestion,
                    style = MaterialTheme.typography.bodySmall,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    color = MaterialTheme.colorScheme.onPrimaryContainer,
                )
            }
            TextButton(onClick = onUse) { Text(stringResource(R.string.action_use)) }
        }
    }
}

@Composable
private fun UrlInputCard(
    value: String,
    isFetching: Boolean,
    canFetch: Boolean,
    onValueChange: (String) -> Unit,
    onPaste: () -> Unit,
    onFetch: () -> Unit,
) {
    Card(shape = CardShape) {
        Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
            OutlinedTextField(
                value = value,
                onValueChange = onValueChange,
                label = { Text(stringResource(R.string.label_paste_url)) },
                placeholder = { Text(stringResource(R.string.placeholder_url)) },
                shape = RoundedCornerShape(14.dp),
                singleLine = false,
                maxLines = 3,
                modifier = Modifier.fillMaxWidth(),
            )

            Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                // Paste resolves immediately. Pasting and then asking the user to press
                // a second button is a step with no decision in it.
                FilledTonalButton(
                    onClick = onPaste,
                    shape = RoundedCornerShape(14.dp),
                    modifier = Modifier.weight(1f).height(48.dp),
                ) {
                    Icon(Icons.Default.ContentPaste, contentDescription = null, modifier = Modifier.size(18.dp))
                    Spacer(Modifier.size(8.dp))
                    Text(stringResource(R.string.action_paste))
                }

                Button(
                    onClick = onFetch,
                    enabled = canFetch,
                    shape = RoundedCornerShape(14.dp),
                    modifier = Modifier.weight(1f).height(48.dp),
                ) {
                    if (isFetching) {
                        CircularProgressIndicator(
                            modifier = Modifier.size(18.dp),
                            strokeWidth = 2.dp,
                            color = MaterialTheme.colorScheme.onPrimary,
                        )
                    } else {
                        Text(stringResource(R.string.action_fetch), fontWeight = FontWeight.SemiBold)
                    }
                }
            }
        }
    }
}

// ------------------------------------------------------------------------ result

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun MediaCard(
    media: ResolvedMedia,
    selectedQuality: VideoQuality?,
    enabled: Boolean,
    onQualitySelected: (VideoQuality) -> Unit,
) {
    Card(shape = CardShape) {
        Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
            Thumbnail(media.thumbnailUrl, dimmed = false)

            Row(verticalAlignment = Alignment.CenterVertically) {
                AssistChip(onClick = {}, label = { Text(media.platform.displayName) })
                Spacer(Modifier.size(8.dp))
                Text(
                    text = stringResource(R.string.quality_count, media.qualities.size),
                    style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }

            Text(
                text = media.title,
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.SemiBold,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis,
            )

            Text(
                text = stringResource(R.string.label_select_quality),
                style = MaterialTheme.typography.labelLarge,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )

            // A source can advertise many renditions — an HLS master playlist routinely
            // carries five — so the chips wrap instead of overflowing off-screen.
            FlowRow(
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                verticalArrangement = Arrangement.spacedBy(4.dp),
            ) {
                media.qualities.forEach { quality ->
                    FilterChip(
                        selected = quality == selectedQuality,
                        onClick = { onQualitySelected(quality) },
                        enabled = enabled,
                        shape = CircleShape,
                        colors = FilterChipDefaults.filterChipColors(),
                        label = {
                            Text(
                                quality.sizeBytes
                                    ?.let { "${quality.label} · ${it.toReadableSize()}" }
                                    ?: quality.label
                            )
                        },
                    )
                }
            }
        }
    }
}

/**
 * Shown when a link resolved but its video is not publicly downloadable.
 *
 * The user still sees what the link points at. A dimmed thumbnail signals "found, but
 * unavailable" without pretending the download is about to work.
 */
@Composable
private fun UnavailablePreviewCard(preview: MediaPreview) {
    Card(shape = CardShape) {
        Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
            Thumbnail(preview.thumbnailUrl, dimmed = true)

            AssistChip(onClick = {}, label = { Text(preview.platform.displayName) })

            Text(
                text = preview.title,
                style = MaterialTheme.typography.bodyMedium,
                maxLines = 3,
                overflow = TextOverflow.Ellipsis,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}

@Composable
private fun Thumbnail(url: String?, dimmed: Boolean) {
    if (url == null) return

    Box(
        modifier = Modifier
            .fillMaxWidth()
            .aspectRatio(16f / 9f)
            .clip(RoundedCornerShape(14.dp))
            .background(MaterialTheme.colorScheme.surfaceVariant),
        contentAlignment = Alignment.Center,
    ) {
        AsyncImage(
            model = url,
            contentDescription = null,
            contentScale = ContentScale.Crop,
            alpha = if (dimmed) 0.45f else 1f,
            modifier = Modifier.fillMaxSize(),
        )
        // Marks the thumbnail as video rather than a still image.
        Box(
            modifier = Modifier
                .size(48.dp)
                .clip(CircleShape)
                .background(Color.Black.copy(alpha = 0.45f)),
            contentAlignment = Alignment.Center,
        ) {
            Icon(
                imageVector = Icons.Default.PlayArrow,
                contentDescription = null,
                tint = Color.White,
                modifier = Modifier.size(28.dp),
            )
        }
    }
}

// ---------------------------------------------------------------------- progress

@Composable
private fun ProgressCard(progress: DownloadProgress.Running?, onCancel: () -> Unit) {
    val percent = progress?.percent
    val animatedFraction by animateFloatAsState(
        targetValue = (percent ?: 0) / 100f,
        label = "downloadProgress",
    )

    Card(shape = CardShape) {
        Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text(
                    text = stringResource(R.string.label_downloading),
                    style = MaterialTheme.typography.titleSmall,
                    fontWeight = FontWeight.SemiBold,
                )
                Text(
                    text = percent?.let { "$it%" } ?: "",
                    style = MaterialTheme.typography.headlineSmall,
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.primary,
                )
            }

            // A null percent means neither a declared size nor a segment count is
            // available, so an indeterminate bar is honest where a number would not be.
            if (percent != null) {
                LinearProgressIndicator(
                    progress = { animatedFraction },
                    strokeCap = StrokeCap.Round,
                    modifier = Modifier.fillMaxWidth().height(8.dp).clip(CircleShape),
                )
            } else {
                LinearProgressIndicator(
                    strokeCap = StrokeCap.Round,
                    modifier = Modifier.fillMaxWidth().height(8.dp).clip(CircleShape),
                )
            }

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
            ) {
                Text(
                    text = progress?.let { p ->
                        buildString {
                            append(p.bytesDownloaded.toReadableSize())
                            p.totalBytes?.let { append(" / ${it.toReadableSize()}") }
                        }
                    } ?: stringResource(R.string.label_preparing),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )

                progress?.takeIf { it.bytesPerSecond > 0 }?.let {
                    Text(
                        text = it.bytesPerSecond.toReadableSpeed(),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }

            TextButton(onClick = onCancel, modifier = Modifier.align(Alignment.End)) {
                Text(stringResource(R.string.action_cancel))
            }
        }
    }
}

@Composable
private fun CompletedCard(result: DownloadProgress.Completed, onDismiss: () -> Unit) {
    val context = LocalContext.current

    Card(
        shape = CardShape,
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.secondaryContainer,
        ),
    ) {
        Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(
                    imageVector = Icons.Default.CheckCircle,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.onSecondaryContainer,
                )
                Spacer(Modifier.size(10.dp))
                Text(
                    text = stringResource(R.string.label_download_complete),
                    style = MaterialTheme.typography.titleSmall,
                    fontWeight = FontWeight.SemiBold,
                )
            }

            Text(
                text = stringResource(R.string.label_saved_to_gallery, result.fileName),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSecondaryContainer,
            )

            // A download is only finished once the user can actually watch it.
            Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                FilledTonalButton(
                    onClick = { MediaActions.openInGallery(context, result.galleryUri) },
                    shape = RoundedCornerShape(14.dp),
                    modifier = Modifier.weight(1f),
                ) {
                    Icon(Icons.Outlined.OpenInNew, contentDescription = null, modifier = Modifier.size(18.dp))
                    Spacer(Modifier.size(8.dp))
                    Text(stringResource(R.string.action_open))
                }
                FilledTonalButton(
                    onClick = { MediaActions.share(context, result.galleryUri) },
                    shape = RoundedCornerShape(14.dp),
                    modifier = Modifier.weight(1f),
                ) {
                    Icon(Icons.Outlined.Share, contentDescription = null, modifier = Modifier.size(18.dp))
                    Spacer(Modifier.size(8.dp))
                    Text(stringResource(R.string.action_share))
                }
            }

            TextButton(onClick = onDismiss, modifier = Modifier.align(Alignment.End)) {
                Text(stringResource(R.string.action_download_another))
            }
        }
    }
}

// ------------------------------------------------------------------------- error

@Composable
private fun ErrorCard(
    error: DownloadError,
    showRetry: Boolean,
    onRetry: () -> Unit,
    onDismiss: () -> Unit,
) {
    // A cancellation is the user's own choice, not a fault — showing it in alarm colours
    // would be telling them off for pressing the button the app offered.
    val isCancellation = error is DownloadError.Cancelled

    Card(
        shape = CardShape,
        colors = CardDefaults.cardColors(
            containerColor = if (isCancellation) {
                MaterialTheme.colorScheme.surfaceVariant
            } else {
                MaterialTheme.colorScheme.errorContainer
            },
        ),
    ) {
        Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
            Row(verticalAlignment = Alignment.Top) {
                Icon(
                    imageVector = Icons.Default.ErrorOutline,
                    contentDescription = null,
                    modifier = Modifier.size(20.dp),
                )
                Spacer(Modifier.size(12.dp))
                Text(
                    text = error.userMessage,
                    style = MaterialTheme.typography.bodyMedium,
                )
            }

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.End,
            ) {
                TextButton(onClick = onDismiss) { Text(stringResource(R.string.action_dismiss)) }
                // Retry is offered only where a second attempt could plausibly succeed;
                // inviting the user to fail again is worse than saying no once.
                if (showRetry) {
                    TextButton(onClick = onRetry) { Text(stringResource(R.string.action_retry)) }
                }
            }
        }
    }
}

// ------------------------------------------------------------------- empty state

@Composable
private fun EmptyState() {
    // Only what a user can actually paste. WhatsApp media URLs are encrypted blobs the
    // user never sees, so advertising them would promise something unreachable.
    val platforms = remember { listOf("Instagram", "Facebook", "HLS streams", "Direct links") }

    Column(
        modifier = Modifier.fillMaxWidth().padding(top = 28.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(10.dp),
    ) {
        Box(
            modifier = Modifier
                .size(64.dp)
                .clip(CircleShape)
                .background(MaterialTheme.colorScheme.primaryContainer),
            contentAlignment = Alignment.Center,
        ) {
            Icon(
                imageVector = Icons.Default.Download,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.onPrimaryContainer,
                modifier = Modifier.size(30.dp),
            )
        }

        Text(
            text = stringResource(R.string.empty_title),
            style = MaterialTheme.typography.titleMedium,
            fontWeight = FontWeight.SemiBold,
        )
        Text(
            text = stringResource(R.string.empty_subtitle),
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )

        Spacer(Modifier.size(2.dp))

        FlowRowCentered(platforms)
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun FlowRowCentered(labels: List<String>) {
    FlowRow(
        horizontalArrangement = Arrangement.spacedBy(8.dp, Alignment.CenterHorizontally),
        verticalArrangement = Arrangement.spacedBy(4.dp),
        modifier = Modifier.fillMaxWidth(),
    ) {
        labels.forEach { label ->
            AssistChip(onClick = {}, label = { Text(label, style = MaterialTheme.typography.labelSmall) })
        }
    }
}
