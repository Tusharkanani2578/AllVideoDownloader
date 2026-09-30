package com.tushar.videodownloader.status

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import coil.compose.AsyncImage
import com.tushar.videodownloader.R
import com.tushar.videodownloader.resolver.MediaKind

/**
 * Statuses WhatsApp has cached on this device, each with a button to keep a copy.
 *
 * Stateless — renders [state] and reports intent upward.
 */
@Composable
fun StatusScreen(
    state: StatusUiState,
    onGrantAccess: () -> Unit,
    onSave: (StatusItem) -> Unit,
    modifier: Modifier = Modifier,
) {
    when {
        !state.hasAccess -> AccessPrompt(onGrantAccess, modifier)
        state.isLoading -> Box(modifier.fillMaxSize(), Alignment.Center) {
            CircularProgressIndicator()
        }
        state.isEmpty -> EmptyStatuses(modifier)
        else -> StatusGrid(state, onSave, modifier)
    }
}

@Composable
private fun AccessPrompt(onGrantAccess: () -> Unit, modifier: Modifier) {
    Column(
        modifier = modifier.fillMaxSize().padding(24.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center,
    ) {
        Text(
            text = stringResource(R.string.status_access_title),
            style = MaterialTheme.typography.titleMedium,
            fontWeight = FontWeight.SemiBold,
            textAlign = TextAlign.Center,
        )
        Text(
            text = stringResource(R.string.status_access_body),
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            textAlign = TextAlign.Center,
            modifier = Modifier.padding(top = 10.dp, bottom = 22.dp),
        )
        Button(onClick = onGrantAccess, shape = RoundedCornerShape(16.dp)) {
            Text(stringResource(R.string.status_access_action))
        }
    }
}

@Composable
private fun EmptyStatuses(modifier: Modifier) {
    Column(
        modifier = modifier.fillMaxSize().padding(24.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center,
    ) {
        Text(
            text = stringResource(R.string.status_empty_title),
            style = MaterialTheme.typography.titleMedium,
            fontWeight = FontWeight.SemiBold,
        )
        Text(
            text = stringResource(R.string.status_empty_body),
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            textAlign = TextAlign.Center,
            modifier = Modifier.padding(top = 8.dp),
        )
    }
}

@Composable
private fun StatusGrid(
    state: StatusUiState,
    onSave: (StatusItem) -> Unit,
    modifier: Modifier,
) {
    LazyVerticalGrid(
        columns = GridCells.Adaptive(minSize = 150.dp),
        modifier = modifier.fillMaxSize(),
        contentPadding = PaddingValues(12.dp),
        horizontalArrangement = Arrangement.spacedBy(10.dp),
        verticalArrangement = Arrangement.spacedBy(10.dp),
    ) {
        items(state.statuses, key = { it.uri.toString() }) { item ->
            StatusTile(
                item = item,
                isSaving = state.savingUri == item.uri.toString(),
                isSaved = item.name in state.savedNames,
                onSave = { onSave(item) },
            )
        }
    }
}

@Composable
private fun StatusTile(
    item: StatusItem,
    isSaving: Boolean,
    isSaved: Boolean,
    onSave: () -> Unit,
) {
    Column {
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .aspectRatio(9f / 16f)
                .clip(RoundedCornerShape(14.dp))
                .background(MaterialTheme.colorScheme.surfaceVariant),
            contentAlignment = Alignment.Center,
        ) {
            // Coil renders a video's first frame as well as an image, so the same call
            // covers both kinds of status.
            AsyncImage(
                model = item.uri,
                contentDescription = null,
                contentScale = ContentScale.Crop,
                modifier = Modifier.fillMaxSize(),
            )

            if (item.kind == MediaKind.VIDEO) {
                Box(
                    modifier = Modifier
                        .size(40.dp)
                        .clip(CircleShape)
                        .background(Color.Black.copy(alpha = 0.45f)),
                    contentAlignment = Alignment.Center,
                ) {
                    Icon(
                        imageVector = Icons.Default.PlayArrow,
                        contentDescription = null,
                        tint = Color.White,
                        modifier = Modifier.size(24.dp),
                    )
                }
            }
        }

        Button(
            onClick = onSave,
            enabled = !isSaving && !isSaved,
            shape = RoundedCornerShape(12.dp),
            modifier = Modifier.fillMaxWidth().padding(top = 6.dp),
        ) {
            when {
                isSaving -> CircularProgressIndicator(
                    modifier = Modifier.size(16.dp),
                    strokeWidth = 2.dp,
                    color = MaterialTheme.colorScheme.onPrimary,
                )
                isSaved -> Text(stringResource(R.string.status_saved))
                else -> {
                    Icon(
                        painter = painterResource(R.drawable.ic_download),
                        contentDescription = null,
                        modifier = Modifier.size(16.dp),
                    )
                    Text(
                        text = stringResource(R.string.action_save),
                        modifier = Modifier.padding(start = 6.dp),
                    )
                }
            }
        }
    }
}
