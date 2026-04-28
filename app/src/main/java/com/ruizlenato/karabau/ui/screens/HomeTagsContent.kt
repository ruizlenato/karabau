package com.ruizlenato.karabau.ui.screens

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowRight
import androidx.compose.material3.Button
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ContainedLoadingIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExperimentalMaterial3ExpressiveApi
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.ListItem
import androidx.compose.material3.ListItemDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.pulltorefresh.PullToRefreshBox
import androidx.compose.material3.pulltorefresh.PullToRefreshDefaults
import androidx.compose.material3.pulltorefresh.rememberPullToRefreshState
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.ruizlenato.karabau.data.model.BookmarkItem
import com.ruizlenato.karabau.data.model.TagItem

@OptIn(ExperimentalMaterial3ExpressiveApi::class)
@Composable
internal fun TagsContent(
    isLoading: Boolean,
    isRefreshing: Boolean,
    errorMessage: String?,
    tags: List<TagItem>,
    selectedTag: TagItem?,
    selectedTagDetails: TagItem?,
    isTagBookmarksLoading: Boolean,
    tagBookmarks: List<BookmarkItem>,
    tagBookmarksErrorMessage: String?,
    onRefresh: () -> Unit,
    onTagClick: (TagItem) -> Unit,
    onCloseTagDetail: () -> Unit,
    onRefreshTagBookmarks: () -> Unit,
    onBookmarkClick: (BookmarkItem) -> Unit = {}
) {
    if (selectedTag != null) {
        TagDetailContent(
            fallbackTag = selectedTag,
            tagDetails = selectedTagDetails,
            isLoading = isTagBookmarksLoading,
            errorMessage = tagBookmarksErrorMessage,
            bookmarks = tagBookmarks,
            onBack = onCloseTagDetail,
            onRetry = onRefreshTagBookmarks,
            onBookmarkClick = onBookmarkClick
        )
        return
    }

    Column(modifier = Modifier.fillMaxSize()) {
        MediumFlexibleTopAppBar(
            title = "Tags",
            modifier = Modifier.padding(horizontal = 16.dp)
        )

        when {
            isLoading -> {
                Box(
                    modifier = Modifier.fillMaxSize(),
                    contentAlignment = Alignment.Center
                ) {
                    ContainedLoadingIndicator(modifier = Modifier.size(56.dp))
                }
            }

            errorMessage != null -> {
                Box(
                    modifier = Modifier.fillMaxSize(),
                    contentAlignment = Alignment.Center
                ) {
                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                        Text(
                            text = "Failed to load tags",
                            style = MaterialTheme.typography.titleMedium,
                            color = MaterialTheme.colorScheme.error
                        )
                        Button(
                            onClick = onRefresh,
                            modifier = Modifier.padding(top = 12.dp)
                        ) {
                            Text("Retry")
                        }
                    }
                }
            }

            tags.isEmpty() -> {
                Box(
                    modifier = Modifier.fillMaxSize(),
                    contentAlignment = Alignment.Center
                ) {
                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                        Text(
                            text = "No Tags",
                            style = MaterialTheme.typography.titleLarge,
                            color = MaterialTheme.colorScheme.onBackground
                        )
                        Text(
                            text = "Tags will appear as you organize your bookmarks",
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            modifier = Modifier.padding(top = 8.dp)
                        )
                    }
                }
            }

            else -> {
                val pullToRefreshState = rememberPullToRefreshState()
                PullToRefreshBox(
                    isRefreshing = isRefreshing,
                    onRefresh = onRefresh,
                    state = pullToRefreshState,
                    indicator = {
                        PullToRefreshExpressiveIndicator(
                            isRefreshing = isRefreshing,
                            state = pullToRefreshState
                        )
                    }
                ) {
                    LazyColumn(
                        modifier = Modifier
                            .fillMaxSize()
                            .graphicsLayer {
                                val threshold = PullToRefreshDefaults.PositionalThreshold.roundToPx()
                                translationY = pullToRefreshState.distanceFraction.coerceIn(0f, 1f) * threshold
                            },
                        contentPadding = PaddingValues(horizontal = 16.dp, vertical = 8.dp)
                    ) {
                        itemsIndexed(tags, key = { _, it -> it.id }) { index, tag ->
                            Surface(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(vertical = 2.dp),
                                shape = MaterialTheme.shapes.small,
                                color = MaterialTheme.colorScheme.surfaceContainer
                            ) {
                                ListItem(
                                    modifier = Modifier.fillMaxWidth(),
                                    onClick = { onTagClick(tag) },
                                    trailingContent = {
                                        Row(
                                            verticalAlignment = Alignment.CenterVertically,
                                            horizontalArrangement = Arrangement.spacedBy(8.dp)
                                        ) {
                                            Text(
                                                text = tag.numBookmarks.toString(),
                                                style = MaterialTheme.typography.labelLarge,
                                                color = MaterialTheme.colorScheme.onSurfaceVariant
                                            )
                                            Icon(
                                                imageVector = Icons.AutoMirrored.Filled.KeyboardArrowRight,
                                                contentDescription = null,
                                                tint = MaterialTheme.colorScheme.onSurfaceVariant
                                            )
                                        }
                                    },
                                    colors = ListItemDefaults.colors(
                                        containerColor = Color.Transparent
                                    )
                                ) {
                                    Text(
                                        text = tag.name,
                                        style = MaterialTheme.typography.bodyLarge,
                                        maxLines = 1,
                                        overflow = TextOverflow.Ellipsis
                                    )
                                }
                            }
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun TagDetailContent(
    fallbackTag: TagItem,
    tagDetails: TagItem?,
    isLoading: Boolean,
    errorMessage: String?,
    bookmarks: List<BookmarkItem>,
    onBack: () -> Unit,
    onRetry: () -> Unit,
    onBookmarkClick: (BookmarkItem) -> Unit = {}
) {
    val pullToRefreshState = rememberPullToRefreshState()
    val cardColors = CardDefaults.elevatedCardColors(
        containerColor = MaterialTheme.colorScheme.surfaceContainerLow
    )
    val cardElevation = CardDefaults.elevatedCardElevation(
        defaultElevation = 1.dp,
        hoveredElevation = 2.dp,
        pressedElevation = 1.dp,
        draggedElevation = 4.dp
    )

    Column(modifier = Modifier.fillMaxSize()) {
        val title = tagDetails?.name ?: fallbackTag.name

        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 8.dp, vertical = 8.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            IconButton(onClick = onBack) {
                Icon(
                    imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                    contentDescription = "Back"
                )
            }
            Text(
                text = title,
                style = MaterialTheme.typography.headlineSmall,
                color = MaterialTheme.colorScheme.onBackground,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
        }

        PullToRefreshBox(
            isRefreshing = isLoading && bookmarks.isNotEmpty(),
            onRefresh = onRetry,
            state = pullToRefreshState,
            indicator = {
                PullToRefreshExpressiveIndicator(
                    isRefreshing = isLoading && bookmarks.isNotEmpty(),
                    state = pullToRefreshState
                )
            }
        ) {
            when {
                isLoading && bookmarks.isEmpty() -> {
                Box(
                    modifier = Modifier.fillMaxSize(),
                    contentAlignment = Alignment.Center
                ) {
                    ContainedLoadingIndicator(modifier = Modifier.size(56.dp))
                }
            }

                errorMessage != null && bookmarks.isEmpty() -> {
                Box(
                    modifier = Modifier.fillMaxSize(),
                    contentAlignment = Alignment.Center
                ) {
                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                        Text(
                            text = "Failed to load bookmarks",
                            style = MaterialTheme.typography.titleMedium,
                            color = MaterialTheme.colorScheme.error
                        )
                        Button(
                            onClick = onRetry,
                            modifier = Modifier.padding(top = 12.dp)
                        ) {
                            Text("Retry")
                        }
                    }
                }
            }

                bookmarks.isEmpty() -> {
                Box(
                    modifier = Modifier.fillMaxSize(),
                    contentAlignment = Alignment.Center
                ) {
                    Text(
                        text = "No bookmarks",
                        style = MaterialTheme.typography.titleMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }

                else -> {
                    LazyColumn(
                    modifier = Modifier
                        .fillMaxSize()
                        .graphicsLayer {
                            val threshold = PullToRefreshDefaults.PositionalThreshold.roundToPx()
                            translationY = pullToRefreshState.distanceFraction.coerceIn(0f, 1f) * threshold
                        },
                    contentPadding = PaddingValues(16.dp),
                    verticalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    items(bookmarks, key = { it.id }) { bookmark ->
                BookmarkListItem(
                    bookmark = bookmark,
                    cardColors = cardColors,
                    cardElevation = cardElevation,
                    onBookmarkClick = onBookmarkClick
                )
                    }
                }
            }
        }
        }
    }
}

@Composable
private fun MediumFlexibleTopAppBar(
    title: String,
    modifier: Modifier = Modifier
) {
    Column(
        modifier = modifier
            .fillMaxWidth()
            .padding(top = 8.dp, bottom = 12.dp)
    ) {
        Text(
            text = title,
            style = MaterialTheme.typography.headlineMedium,
            color = MaterialTheme.colorScheme.onSurface,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis
        )
        Text(
            text = "Manage and browse your tags",
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.padding(top = 4.dp)
        )
    }
}
