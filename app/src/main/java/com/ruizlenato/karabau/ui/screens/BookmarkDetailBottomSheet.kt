package com.ruizlenato.karabau.ui.screens

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.OpenInBrowser
import androidx.compose.material.icons.filled.Share
import androidx.compose.material.icons.filled.Star
import androidx.compose.material.icons.outlined.DeleteOutline
import androidx.compose.material.icons.outlined.StarBorder
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilledTonalIconButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButtonDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedCard
import androidx.compose.material3.SuggestionChip
import androidx.compose.material3.SuggestionChipDefaults
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import coil.compose.AsyncImage
import coil.request.ImageRequest
import com.ruizlenato.karabau.data.model.BookmarkItem
import java.time.Instant
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.time.format.FormatStyle
import java.util.Locale

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun BookmarkDetailBottomSheet(
    bookmark: BookmarkItem,
    onDismiss: () -> Unit,
    onOpenLink: (String) -> Unit,
    onShare: (BookmarkItem) -> Unit,
    onDelete: (BookmarkItem) -> Unit,
    onToggleFavourite: (BookmarkItem) -> Unit
) {
    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)

    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = sheetState,
        containerColor = MaterialTheme.colorScheme.surfaceContainerLow
    ) {
        if (!bookmark.imageUrl.isNullOrBlank()) {
            val context = LocalContext.current
            val imageRequest = remember(bookmark.imageUrl) {
                ImageRequest.Builder(context)
                    .data(bookmark.imageUrl)
                    .crossfade(true)
                    .build()
            }
            AsyncImage(
                model = imageRequest,
                contentDescription = null,
                contentScale = ContentScale.Crop,
                modifier = Modifier
                    .padding(horizontal = 16.dp)
                    .fillMaxWidth()
                    .height(200.dp)
                    .clip(RoundedCornerShape(16.dp))
            )
        }

        Column(
            modifier = Modifier.padding(horizontal = 24.dp)
        ) {
            val titleText = bookmark.title?.takeIf { it.isNotBlank() } ?: "Untitled"
            Text(
                text = titleText,
                style = MaterialTheme.typography.headlineSmall,
                color = MaterialTheme.colorScheme.onSurface,
                modifier = Modifier.padding(top = 20.dp)
            )

            if (!bookmark.subtitle.isNullOrBlank()) {
                Text(
                    text = bookmark.subtitle,
                    style = MaterialTheme.typography.bodyLarge,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 10,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.padding(top = 12.dp)
                )
            }

            if (bookmark.tags.isNotEmpty()) {
                FlowRow(
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    verticalArrangement = Arrangement.spacedBy(8.dp),
                    modifier = Modifier.padding(top = 16.dp)
                ) {
                    bookmark.tags.forEach { tag ->
                        SuggestionChip(
                            onClick = {},
                            label = { Text(tag) },
                            colors = SuggestionChipDefaults.suggestionChipColors(
                                containerColor = MaterialTheme.colorScheme.secondaryContainer,
                                labelColor = MaterialTheme.colorScheme.onSecondaryContainer
                            ),
                            shape = MaterialTheme.shapes.small
                        )
                    }
                }
            }

            if (!bookmark.linkUrl.isNullOrBlank()) {
                val displayUrl = bookmark.linkUrl
                    .removePrefix("https://")
                    .removePrefix("http://")
                
                OutlinedCard(
                    onClick = { onOpenLink(bookmark.linkUrl) },
                    modifier = Modifier
                        .padding(top = 16.dp)
                        .fillMaxWidth()
                ) {
                    Row(
                        modifier = Modifier.padding(16.dp),
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Icon(
                            imageVector = Icons.Default.OpenInBrowser,
                            contentDescription = "Open Link",
                            tint = MaterialTheme.colorScheme.primary
                        )
                        Text(
                            text = displayUrl,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                            color = MaterialTheme.colorScheme.primary
                        )
                    }
                }
            }

            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(top = 20.dp),
                horizontalArrangement = Arrangement.SpaceEvenly
            ) {
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    FilledTonalIconButton(
                        onClick = { onToggleFavourite(bookmark) },
                        colors = if (bookmark.favourited) {
                            IconButtonDefaults.filledTonalIconButtonColors(
                                containerColor = MaterialTheme.colorScheme.primaryContainer,
                                contentColor = MaterialTheme.colorScheme.primary
                            )
                        } else {
                            IconButtonDefaults.filledTonalIconButtonColors()
                        }
                    ) {
                        Icon(
                            imageVector = if (bookmark.favourited) Icons.Default.Star else Icons.Outlined.StarBorder,
                            contentDescription = if (bookmark.favourited) "Favourited" else "Favourite"
                        )
                    }
                    Text(
                        text = if (bookmark.favourited) "Favourited" else "Favourite",
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.padding(top = 6.dp)
                    )
                }

                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    FilledTonalIconButton(
                        onClick = { onShare(bookmark) }
                    ) {
                        Icon(
                            imageVector = Icons.Default.Share,
                            contentDescription = "Share"
                        )
                    }
                    Text(
                        text = "Share",
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.padding(top = 6.dp)
                    )
                }

                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    FilledTonalIconButton(
                        onClick = { onDelete(bookmark) },
                        colors = IconButtonDefaults.filledTonalIconButtonColors(
                            containerColor = MaterialTheme.colorScheme.errorContainer,
                            contentColor = MaterialTheme.colorScheme.error
                        )
                    ) {
                        Icon(
                            imageVector = Icons.Outlined.DeleteOutline,
                            contentDescription = "Delete"
                        )
                    }
                    Text(
                        text = "Delete",
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.padding(top = 6.dp)
                    )
                }
            }

            Row(
                modifier = Modifier
                    .padding(top = 16.dp, bottom = 24.dp)
                    .fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(16.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                if (bookmark.archived) {
                    Surface(
                        color = MaterialTheme.colorScheme.tertiaryContainer,
                        shape = MaterialTheme.shapes.small
                    ) {
                        Text(
                            text = "Archived",
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.onTertiaryContainer,
                            modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp)
                        )
                    }
                }

                val formattedDate = remember(bookmark.createdAt) {
                    try {
                        val instant = Instant.parse(bookmark.createdAt)
                        val localDateTime = instant.atZone(ZoneId.systemDefault())
                        val locale = Locale.getDefault()
                        val formatter = DateTimeFormatter.ofLocalizedDateTime(FormatStyle.MEDIUM, FormatStyle.SHORT)
                            .withLocale(locale)
                        localDateTime.format(formatter)
                    } catch (_: Exception) {
                        bookmark.createdAt
                    }
                }

                Text(
                    text = formattedDate,
                    style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    textAlign = TextAlign.End,
                    modifier = Modifier.weight(1f)
                )
            }
        }

        Spacer(modifier = Modifier.navigationBarsPadding())
    }
}
