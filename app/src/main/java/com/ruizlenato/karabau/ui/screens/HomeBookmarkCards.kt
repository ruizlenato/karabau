package com.ruizlenato.karabau.ui.screens

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.staggeredgrid.LazyVerticalStaggeredGrid
import androidx.compose.foundation.lazy.staggeredgrid.StaggeredGridCells
import androidx.compose.foundation.lazy.staggeredgrid.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Search
import androidx.compose.material3.CardColors
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CardElevation
import androidx.compose.material3.ContainedLoadingIndicator
import androidx.compose.material3.ElevatedCard
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExperimentalMaterial3ExpressiveApi
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.pulltorefresh.PullToRefreshState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import coil.compose.AsyncImage
import coil.request.ImageRequest
import com.ruizlenato.karabau.data.model.BookmarkItem

@OptIn(ExperimentalMaterial3Api::class)
@Composable
internal fun BookmarkListItem(
    bookmark: BookmarkItem,
    cardColors: CardColors,
    cardElevation: CardElevation,
    onBookmarkClick: (BookmarkItem) -> Unit = {}
) {
    val context = LocalContext.current
    val imageRequest = remember(context, bookmark.imageUrl) {
        bookmark.imageUrl?.takeIf { it.isNotBlank() }?.let { imageUrl ->
            ImageRequest.Builder(context)
                .data(imageUrl)
                .crossfade(0)
                .build()
        }
    }

    ElevatedCard(
        onClick = { onBookmarkClick(bookmark) },
        modifier = Modifier.fillMaxWidth(),
        shape = MaterialTheme.shapes.medium,
        colors = cardColors,
        elevation = cardElevation
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            if (imageRequest != null) {
                AsyncImage(
                    model = imageRequest,
                    contentDescription = null,
                    contentScale = ContentScale.Crop,
                    modifier = Modifier
                        .size(56.dp)
                        .clip(RoundedCornerShape(8.dp))
                )
                Spacer(modifier = Modifier.size(16.dp))
            }

            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = bookmark.title?.takeIf { it.isNotBlank() } ?: "Untitled",
                    style = MaterialTheme.typography.titleMedium,
                    color = MaterialTheme.colorScheme.onSurface,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis
                )

                if (bookmark.tags.isNotEmpty()) {
                    Spacer(modifier = Modifier.height(6.dp))
                    TagChipRow(tags = bookmark.tags)
                }

                bookmark.subtitle?.takeIf { it.isNotBlank() }?.let { subtitle ->
                    Spacer(modifier = Modifier.height(4.dp))
                    Text(
                        text = subtitle,
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        maxLines = 2,
                        overflow = TextOverflow.Ellipsis
                    )
                }

                bookmark.linkUrl?.takeIf { it.isNotBlank() }?.let { link ->
                    Spacer(modifier = Modifier.height(4.dp))
                    Text(
                        text = link.removePrefix("https://").removePrefix("http://").take(40),
                        style = MaterialTheme.typography.labelMedium,
                        color = MaterialTheme.colorScheme.primary,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                }
            }
        }
    }
}

@Composable
internal fun BookmarkGridContent(
    bookmarks: List<BookmarkItem>,
    modifier: Modifier = Modifier,
    onBookmarkClick: (BookmarkItem) -> Unit = {}
) {
    val cardColors = CardDefaults.elevatedCardColors(
        containerColor = MaterialTheme.colorScheme.surfaceContainerLow
    )
    val cardElevation = CardDefaults.elevatedCardElevation(
        defaultElevation = 1.dp,
        hoveredElevation = 2.dp,
        pressedElevation = 1.dp,
        draggedElevation = 4.dp
    )

    LazyVerticalStaggeredGrid(
        columns = StaggeredGridCells.Fixed(2),
        modifier = modifier,
        contentPadding = PaddingValues(16.dp),
        horizontalArrangement = Arrangement.spacedBy(16.dp),
        verticalItemSpacing = 16.dp
    ) {
        items(bookmarks, key = { it.id }) { bookmark ->
        KeepStyleCard(
            bookmark = bookmark,
            cardColors = cardColors,
            cardElevation = cardElevation,
            onBookmarkClick = onBookmarkClick
        )
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
internal fun KeepStyleCard(
    bookmark: BookmarkItem,
    cardColors: CardColors,
    cardElevation: CardElevation,
    onBookmarkClick: (BookmarkItem) -> Unit = {}
) {
    val context = LocalContext.current
    val imageRequest = remember(context, bookmark.imageUrl) {
        bookmark.imageUrl?.takeIf { it.isNotBlank() }?.let { imageUrl ->
            ImageRequest.Builder(context)
                .data(imageUrl)
                .crossfade(0)
                .build()
        }
    }

    ElevatedCard(
        onClick = { onBookmarkClick(bookmark) },
        modifier = Modifier.fillMaxWidth(),
        shape = MaterialTheme.shapes.medium,
        colors = cardColors,
        elevation = cardElevation
    ) {
        Column {
            if (imageRequest != null) {
                AsyncImage(
                    model = imageRequest,
                    contentDescription = null,
                    contentScale = ContentScale.Crop,
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(160.dp)
                )
            }

            Column(modifier = Modifier.padding(16.dp)) {
                Text(
                    text = bookmark.title?.takeIf { it.isNotBlank() } ?: "Untitled",
                    style = MaterialTheme.typography.titleMedium,
                    color = MaterialTheme.colorScheme.onSurface,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis
                )

                if (bookmark.tags.isNotEmpty()) {
                    Spacer(modifier = Modifier.height(6.dp))
                    TagChipRow(tags = bookmark.tags)
                }

                bookmark.subtitle?.takeIf { it.isNotBlank() }?.let { subtitle ->
                    Spacer(modifier = Modifier.height(8.dp))
                    Text(
                        text = subtitle,
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        maxLines = 2,
                        overflow = TextOverflow.Ellipsis
                    )
                }

                bookmark.linkUrl?.takeIf { it.isNotBlank() }?.let { link ->
                    Spacer(modifier = Modifier.height(8.dp))
                    Text(
                        text = link.removePrefix("https://").removePrefix("http://").take(30),
                        style = MaterialTheme.typography.labelMedium,
                        color = MaterialTheme.colorScheme.primary,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                }
            }
        }
    }
}

@Composable
internal fun SearchResultsContent(
    bookmarks: List<BookmarkItem>,
    modifier: Modifier = Modifier,
    onBookmarkClick: (BookmarkItem) -> Unit = {}
) {
    val cardColors = CardDefaults.elevatedCardColors(
        containerColor = MaterialTheme.colorScheme.surfaceContainerLow
    )
    val cardElevation = CardDefaults.elevatedCardElevation(
        defaultElevation = 1.dp,
        hoveredElevation = 2.dp,
        pressedElevation = 1.dp,
        draggedElevation = 4.dp
    )

    Column(modifier = modifier) {
        Text(
            text = "Results",
            style = MaterialTheme.typography.titleMedium,
            color = MaterialTheme.colorScheme.onSurface,
            modifier = Modifier.padding(horizontal = 16.dp, vertical = 12.dp)
        )

        LazyColumn(
            modifier = Modifier.fillMaxSize(),
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

@OptIn(ExperimentalMaterial3ExpressiveApi::class)
@Composable
internal fun ExpressiveLoadingIndicator() {
    ContainedLoadingIndicator(modifier = Modifier.size(72.dp))
}

@OptIn(ExperimentalMaterial3ExpressiveApi::class, ExperimentalMaterial3Api::class)
@Composable
internal fun PullToRefreshExpressiveIndicator(
    isRefreshing: Boolean,
    state: PullToRefreshState
) {
    val isVisible = isRefreshing || state.distanceFraction > 0f

    Box(
        modifier = Modifier
            .fillMaxWidth()
            .padding(top = 12.dp),
        contentAlignment = Alignment.TopCenter
    ) {
        AnimatedVisibility(
            visible = isVisible,
            enter = fadeIn(animationSpec = tween(200)) + slideInVertically(initialOffsetY = { -10 }),
            exit = fadeOut(animationSpec = tween(150)) + slideOutVertically(targetOffsetY = { -10 })
        ) {
            if (isRefreshing) {
                ContainedLoadingIndicator(modifier = Modifier.size(48.dp))
            } else {
                ContainedLoadingIndicator(
                    progress = { state.distanceFraction.coerceIn(0f, 1f) },
                    modifier = Modifier.size(48.dp)
                )
            }
        }
    }
}

@Composable
internal fun SearchEmptyState() {
    Box(
        modifier = Modifier.fillMaxSize(),
        contentAlignment = Alignment.Center
    ) {
        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            Icon(
                imageVector = Icons.Default.Search,
                contentDescription = null,
                modifier = Modifier.size(48.dp),
                tint = MaterialTheme.colorScheme.onSurfaceVariant
            )

            Spacer(modifier = Modifier.height(16.dp))

            Text(
                text = "No results found",
                style = MaterialTheme.typography.titleMedium,
                color = MaterialTheme.colorScheme.onBackground
            )

            Text(
                text = "Try a different search term",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(top = 8.dp)
            )
        }
    }
}

@Composable
internal fun TagChipRow(tags: List<String>) {
    Row(
        horizontalArrangement = Arrangement.spacedBy(4.dp),
        modifier = Modifier.fillMaxWidth()
    ) {
        tags.take(2).forEach { tag ->
            Surface(
                color = MaterialTheme.colorScheme.secondaryContainer,
                contentColor = MaterialTheme.colorScheme.onSecondaryContainer,
                shape = RoundedCornerShape(6.dp)
            ) {
                Text(
                    text = tag.trim(),
                    style = MaterialTheme.typography.labelSmall,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp)
                )
            }
        }
    }
}
