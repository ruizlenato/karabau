package com.ruizlenato.karabau.ui.screens

import android.content.Intent
import android.net.Uri
import androidx.activity.compose.BackHandler
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.ExperimentalSharedTransitionApi
import androidx.compose.animation.SharedTransitionScope
import androidx.compose.animation.SizeTransform
import androidx.compose.animation.core.FastOutLinearInEasing
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.LinearOutSlowInEasing
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInHorizontally
import androidx.compose.animation.slideOutHorizontally
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.Home
import androidx.compose.material.icons.filled.LocalOffer
import androidx.compose.material3.Button
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FabPosition
import androidx.compose.material3.FloatingActionButton
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.pulltorefresh.PullToRefreshDefaults
import androidx.compose.material3.pulltorefresh.PullToRefreshBox
import androidx.compose.material3.pulltorefresh.rememberPullToRefreshState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import com.ruizlenato.karabau.data.model.BookmarkItem
import com.ruizlenato.karabau.ui.viewmodel.HomeViewModel
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.launch

private data class HomeDestination(
    val label: String,
    val icon: ImageVector
)

private val homeDestinations = listOf(
    HomeDestination(label = "Home", icon = Icons.Default.Home),
    HomeDestination(label = "Tags", icon = Icons.Default.LocalOffer)
)

@OptIn(ExperimentalMaterial3Api::class, ExperimentalSharedTransitionApi::class)
@Composable
fun HomeScreen(
    onLogout: () -> Unit,
    onAddBookmark: () -> Unit = {},
    sharedTransitionScope: SharedTransitionScope,
    animatedContentScope: androidx.compose.animation.AnimatedContentScope,
    onBookmarkCreated: () -> Unit = {}
) {
    var selectedTab by remember { mutableIntStateOf(0) }
    val homeViewModel: HomeViewModel = viewModel()
    val homeUiState by homeViewModel.uiState.collectAsStateWithLifecycle()
    var selectedBookmark by remember { mutableStateOf<BookmarkItem?>(null) }
    val context = LocalContext.current

    LaunchedEffect(Unit) {
        coroutineScope {
            launch { homeViewModel.loadSavedItems() }
            launch { homeViewModel.loadTags() }
        }
    }

    LaunchedEffect(selectedTab) {
        if (selectedTab == 1) {
            homeViewModel.loadTags()
        }
    }

    BackHandler(enabled = selectedTab == 0 && homeUiState.isSearchActive) {
        homeViewModel.onSearchActiveChange(false)
        homeViewModel.onSearchQueryChange("")
    }

    BackHandler(enabled = selectedTab == 2) {
        selectedTab = 0
    }

    BackHandler(enabled = selectedTab == 1 && homeUiState.selectedTag != null) {
        homeViewModel.closeTagDetail()
    }

    AnimatedContent(
        targetState = selectedTab,
        transitionSpec = {
            when {
                targetState == 2 && initialState != 2 -> {
                    (slideInHorizontally(
                        initialOffsetX = { fullWidth -> (fullWidth * 1.05f).toInt() },
                        animationSpec = tween(durationMillis = 360, easing = LinearOutSlowInEasing)
                    ) + fadeIn(animationSpec = tween(240, easing = LinearOutSlowInEasing)))
                        .togetherWith(
                            slideOutHorizontally(
                                targetOffsetX = { fullWidth -> -(fullWidth * 0.1f).toInt() },
                                animationSpec = tween(durationMillis = 300, easing = FastOutSlowInEasing)
                            ) + fadeOut(animationSpec = tween(220, easing = FastOutLinearInEasing))
                        ).using(SizeTransform(clip = false))
                }

                initialState == 2 && targetState != 2 -> {
                    (slideInHorizontally(
                        initialOffsetX = { fullWidth -> -(fullWidth * 0.1f).toInt() },
                        animationSpec = tween(durationMillis = 360, easing = LinearOutSlowInEasing)
                    ) + fadeIn(animationSpec = tween(240, easing = LinearOutSlowInEasing)))
                        .togetherWith(
                            slideOutHorizontally(
                                targetOffsetX = { fullWidth -> (fullWidth * 1.05f).toInt() },
                                animationSpec = tween(durationMillis = 360, easing = FastOutSlowInEasing)
                            ) + fadeOut(animationSpec = tween(240, easing = FastOutLinearInEasing))
                        ).using(SizeTransform(clip = false))
                }

                else -> {
                    fadeIn(animationSpec = tween(120))
                        .togetherWith(fadeOut(animationSpec = tween(90)))
                }
            }
        },
        label = "mainTabTransition"
    ) { activeTab ->
        if (activeTab == 2) {
            SettingsContent(
                onLogout = onLogout,
                onBack = { selectedTab = 0 }
            )
        } else {
            Scaffold(
                topBar = {
                    if (activeTab == 0) {
                        HomeTopBar(
                            query = homeUiState.searchQuery,
                            isSearchActive = homeUiState.isSearchActive,
                            profileName = homeUiState.profileName,
                            profileImage = homeUiState.profileImage,
                            profileImageHeaders = homeUiState.profileImageHeaders,
                            onQueryChange = homeViewModel::onSearchQueryChange,
                            onSearchActiveChange = homeViewModel::onSearchActiveChange,
                            onClearQuery = { homeViewModel.onSearchQueryChange("") },
                            onProfileClick = {
                                homeViewModel.onSearchActiveChange(false)
                                homeViewModel.onSearchQueryChange("")
                                selectedTab = 2
                            }
                        )
                    }
                },
                floatingActionButton = {
                    if (activeTab == 0 && !homeUiState.isSearchActive) {
                        with(sharedTransitionScope) {
                            FloatingActionButton(
                                onClick = onAddBookmark,
                                modifier = Modifier
                                    .size(70.dp)
                                    .sharedBounds(
                                        sharedContentState = rememberSharedContentState(key = "create_bookmark_container"),
                                        animatedVisibilityScope = animatedContentScope,
                                        clipInOverlayDuringTransition = OverlayClip(RoundedCornerShape(16.dp)),
                                        enter = fadeIn(tween(200, delayMillis = 300, easing = FastOutSlowInEasing)),
                                        exit = fadeOut(tween(150, easing = FastOutSlowInEasing))
                                    ),
                                shape = RoundedCornerShape(16.dp),
                                containerColor = MaterialTheme.colorScheme.primaryContainer,
                                contentColor = MaterialTheme.colorScheme.onPrimaryContainer
                            ) {
                                Icon(
                                    imageVector = Icons.Default.Edit,
                                    contentDescription = "Add bookmark",
                                    modifier = Modifier.size(28.dp)
                                )
                            }
                        }
                    }
                },
                floatingActionButtonPosition = FabPosition.End,
                bottomBar = {
                    if (!homeUiState.isSearchActive) {
                        NavigationBar(
                            tonalElevation = 0.dp,
                            windowInsets = WindowInsets(0.dp)
                        ) {
                            homeDestinations.forEachIndexed { index, destination ->
                                NavigationBarItem(
                                    selected = activeTab == index,
                                    onClick = { selectedTab = index },
                                    icon = {
                                        Icon(
                                            imageVector = destination.icon,
                                            contentDescription = destination.label
                                        )
                                    },
                                    label = { Text(destination.label) }
                                )
                            }
                        }
                    }
                }
            ) { paddingValues ->
                Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(paddingValues)
                ) {
                    when (activeTab) {
                        0 -> HomeContent(
                            isLoading = homeUiState.isLoading,
                            isRefreshing = homeUiState.isRefreshing,
                            errorMessage = homeUiState.errorMessage,
                            bookmarks = homeUiState.displayedBookmarks,
                            isSearchActive = homeUiState.isSearchActive,
                            searchQuery = homeUiState.searchQuery,
                            onRetry = { homeViewModel.loadSavedItems() },
                            onRefresh = { homeViewModel.refreshSavedItems() },
                            onBookmarkClick = { selectedBookmark = it }
                        )

                        1 -> TagsContent(
                            isLoading = homeUiState.isTagsLoading,
                            isRefreshing = homeUiState.isTagsRefreshing,
                            errorMessage = homeUiState.tagsErrorMessage,
                            tags = homeUiState.tags,
                            selectedTag = homeUiState.selectedTag,
                            selectedTagDetails = homeUiState.selectedTagDetails,
                            isTagBookmarksLoading = homeUiState.isTagBookmarksLoading,
                            tagBookmarks = homeUiState.tagBookmarks,
                            tagBookmarksErrorMessage = homeUiState.tagBookmarksErrorMessage,
                            onRefresh = { homeViewModel.refreshTags() },
                            onTagClick = homeViewModel::openTag,
                            onCloseTagDetail = homeViewModel::closeTagDetail,
                            onRefreshTagBookmarks = homeViewModel::refreshTagBookmarks,
                            onBookmarkClick = { selectedBookmark = it }
                        )
                    }
                }
            }
        }
    }

    selectedBookmark?.let { bookmark ->
        BookmarkDetailBottomSheet(
            bookmark = bookmark,
            onDismiss = { selectedBookmark = null },
            onOpenLink = { url ->
                val uri = Uri.parse(url)
                if (uri.scheme == "http" || uri.scheme == "https") {
                    val intent = Intent(Intent.ACTION_VIEW, uri)
                    context.startActivity(intent)
                }
            },
            onShare = { item ->
                val shareText = item.linkUrl ?: item.title ?: item.id
                val shareIntent = Intent(Intent.ACTION_SEND).apply {
                    type = "text/plain"
                    putExtra(Intent.EXTRA_TEXT, shareText)
                    item.title?.let { putExtra(Intent.EXTRA_SUBJECT, it) }
                }
                context.startActivity(Intent.createChooser(shareIntent, null))
            },
            onDelete = { /* TODO: implement delete */ },
            onToggleFavourite = { /* TODO: implement toggle favourite */ }
        )
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun HomeContent(
    isLoading: Boolean,
    isRefreshing: Boolean,
    errorMessage: String?,
    bookmarks: List<BookmarkItem>,
    isSearchActive: Boolean,
    searchQuery: String,
    onRetry: () -> Unit,
    onRefresh: () -> Unit,
    onBookmarkClick: (BookmarkItem) -> Unit
) {
    val pullToRefreshState = rememberPullToRefreshState()

    when {
        isLoading -> {
            Box(
                modifier = Modifier.fillMaxSize(),
                contentAlignment = Alignment.Center
            ) {
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    ExpressiveLoadingIndicator()
                }
            }
        }

        errorMessage != null -> {
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

        bookmarks.isEmpty() && isSearchActive && searchQuery.isNotBlank() -> {
            SearchEmptyState()
        }

        bookmarks.isEmpty() -> {
            Box(
                modifier = Modifier.fillMaxSize(),
                contentAlignment = Alignment.Center
            ) {
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    Text(
                        text = "No Bookmarks",
                        style = MaterialTheme.typography.headlineSmall,
                        color = MaterialTheme.colorScheme.onBackground
                    )
                    Text(
                        text = "Your saved bookmarks will appear here",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.padding(top = 8.dp)
                    )
                }
            }
        }

        else -> {
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
                val contentModifier = Modifier
                    .fillMaxSize()
                    .graphicsLayer {
                        val threshold = PullToRefreshDefaults.PositionalThreshold.roundToPx()
                        translationY = pullToRefreshState.distanceFraction.coerceIn(0f, 1f) * threshold
                    }

                AnimatedContent(
                    targetState = isSearchActive,
                    transitionSpec = {
                        fadeIn(animationSpec = tween(200)) togetherWith
                                fadeOut(animationSpec = tween(150))
                    },
                    label = "content"
                ) { active ->
                    if (active) {
                        SearchResultsContent(
                            bookmarks = bookmarks,
                            modifier = contentModifier,
                            onBookmarkClick = onBookmarkClick
                        )
                    } else {
                        BookmarkGridContent(
                            bookmarks = bookmarks,
                            modifier = contentModifier,
                            onBookmarkClick = onBookmarkClick
                        )
                    }
                }
            }
        }
    }
}
