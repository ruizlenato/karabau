package com.ruizlenato.karabau.ui.screens

import androidx.activity.compose.BackHandler
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.SizeTransform
import androidx.compose.animation.core.FastOutLinearInEasing
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.LinearOutSlowInEasing
import androidx.compose.animation.core.animateDpAsState
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.scaleIn
import androidx.compose.animation.scaleOut
import androidx.compose.animation.slideInHorizontally
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutHorizontally
import androidx.compose.animation.slideOutVertically
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.asPaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBars
import androidx.compose.foundation.clickable
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.staggeredgrid.LazyVerticalStaggeredGrid
import androidx.compose.foundation.lazy.staggeredgrid.StaggeredGridCells
import androidx.compose.foundation.lazy.staggeredgrid.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowRight
import androidx.compose.material.icons.filled.Clear
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.Home
import androidx.compose.material.icons.filled.LocalOffer
import androidx.compose.material.icons.filled.Search
import androidx.compose.material3.Button
import androidx.compose.material3.CardColors
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CardElevation
import androidx.compose.material3.ContainedLoadingIndicator
import androidx.compose.material3.ElevatedCard
import androidx.compose.material3.ExperimentalMaterial3ExpressiveApi
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FabPosition
import androidx.compose.material3.FloatingActionButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ListItem
import androidx.compose.material3.ListItemDefaults
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextField
import androidx.compose.material3.TextFieldDefaults
import androidx.compose.material3.pulltorefresh.PullToRefreshState
import androidx.compose.material3.pulltorefresh.PullToRefreshBox
import androidx.compose.material3.pulltorefresh.rememberPullToRefreshState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.platform.LocalSoftwareKeyboardController
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import coil.compose.AsyncImage
import coil.request.ImageRequest
import com.ruizlenato.karabau.data.model.BookmarkItem
import com.ruizlenato.karabau.data.model.TagDetails
import com.ruizlenato.karabau.data.model.TagItem
import com.ruizlenato.karabau.ui.viewmodel.HomeViewModel

private data class HomeDestination(
    val label: String,
    val icon: ImageVector
)

private val homeDestinations = listOf(
    HomeDestination(label = "Home", icon = Icons.Default.Home),
    HomeDestination(label = "Tags", icon = Icons.Default.LocalOffer)
)

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun HomeScreen(
    onLogout: () -> Unit
) {
    var selectedTab by remember { mutableIntStateOf(0) }
    val homeViewModel: HomeViewModel = viewModel()
    val homeUiState by homeViewModel.uiState.collectAsStateWithLifecycle()

    LaunchedEffect(Unit) {
        homeViewModel.loadSavedItems()
        homeViewModel.loadTags()
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
            SettingsContent(onLogout = onLogout)
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
                        FloatingActionButton(
                            onClick = { },
                            modifier = Modifier.size(70.dp),
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
                            onRefresh = { homeViewModel.refreshSavedItems() }
                        )

                        1 -> TagsContent(
                            isLoading = homeUiState.isTagsLoading,
                            errorMessage = homeUiState.tagsErrorMessage,
                            tags = homeUiState.tags,
                            selectedTag = homeUiState.selectedTag,
                            selectedTagDetails = homeUiState.selectedTagDetails,
                            isTagBookmarksLoading = homeUiState.isTagBookmarksLoading,
                            tagBookmarks = homeUiState.tagBookmarks,
                            tagBookmarksErrorMessage = homeUiState.tagBookmarksErrorMessage,
                            onRefresh = { homeViewModel.loadTags() },
                            onTagClick = homeViewModel::openTag,
                            onCloseTagDetail = homeViewModel::closeTagDetail,
                            onRefreshTagBookmarks = homeViewModel::refreshTagBookmarks
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun HomeTopBar(
    query: String,
    isSearchActive: Boolean,
    profileName: String?,
    profileImage: String?,
    profileImageHeaders: Map<String, String>,
    onQueryChange: (String) -> Unit,
    onSearchActiveChange: (Boolean) -> Unit,
    onClearQuery: () -> Unit,
    onProfileClick: () -> Unit
) {
    val statusBarHeight = WindowInsets.statusBars.asPaddingValues().calculateTopPadding()
    val focusManager = LocalFocusManager.current
    val keyboardController = LocalSoftwareKeyboardController.current
    val searchFieldFocusRequester = remember { FocusRequester() }

    LaunchedEffect(isSearchActive) {
        if (isSearchActive) {
            searchFieldFocusRequester.requestFocus()
            keyboardController?.show()
        } else {
            keyboardController?.hide()
        }
    }

    val horizontalPadding by animateDpAsState(
        targetValue = if (isSearchActive) 0.dp else 16.dp,
        animationSpec = tween(durationMillis = 300, easing = FastOutSlowInEasing),
        label = "horizontalPadding"
    )

    val verticalPadding by animateDpAsState(
        targetValue = if (isSearchActive) 0.dp else 12.dp,
        animationSpec = tween(durationMillis = 300, easing = FastOutSlowInEasing),
        label = "verticalPadding"
    )

    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(top = statusBarHeight)
    ) {
        AnimatedContent(
            targetState = isSearchActive,
            transitionSpec = {
                (fadeIn(animationSpec = tween(200)) +
                 slideInVertically(
                      initialOffsetY = { -it / 2 },
                      animationSpec = tween(durationMillis = 300, easing = FastOutSlowInEasing)
                 ))
                    .togetherWith(
                        fadeOut(animationSpec = tween(150)) +
                        slideOutVertically(
                            targetOffsetY = { -it / 2 },
                            animationSpec = tween(durationMillis = 250, easing = FastOutSlowInEasing)
                        )
                    )
            },
            label = "searchBar"
        ) { active ->
            if (active) {
                ActiveSearchBar(
                    query = query,
                    onQueryChange = onQueryChange,
                    onClearQuery = onClearQuery,
                    focusRequester = searchFieldFocusRequester,
                    onBackClick = {
                        onSearchActiveChange(false)
                        onClearQuery()
                        focusManager.clearFocus()
                    }
                )
            } else {
                CompactSearchBar(
                    query = query,
                    profileName = profileName,
                    profileImage = profileImage,
                    profileImageHeaders = profileImageHeaders,
                    onProfileClick = onProfileClick,
                    onClick = { onSearchActiveChange(true) },
                    modifier = Modifier.padding(horizontal = horizontalPadding, vertical = verticalPadding)
                )
            }
        }

        AnimatedVisibility(
            visible = isSearchActive,
            enter = fadeIn(animationSpec = tween(200)) + slideInVertically(initialOffsetY = { -10 }),
            exit = fadeOut(animationSpec = tween(150)) + slideOutVertically(targetOffsetY = { -10 })
        ) {
            HorizontalDivider(
                color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f),
                thickness = 1.dp
            )
        }
    }
}

@Composable
private fun ActiveSearchBar(
    query: String,
    onQueryChange: (String) -> Unit,
    onClearQuery: () -> Unit,
    focusRequester: FocusRequester,
    onBackClick: () -> Unit
) {
    Surface(
        modifier = Modifier.fillMaxWidth(),
        color = MaterialTheme.colorScheme.surface
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .height(56.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            IconButton(
                onClick = onBackClick,
                modifier = Modifier.padding(start = 4.dp)
            ) {
                Icon(
                    imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                    contentDescription = "Back",
                    tint = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }

            TextField(
                value = query,
                onValueChange = onQueryChange,
                modifier = Modifier
                    .weight(1f)
                    .focusRequester(focusRequester),
                placeholder = { Text("Search your bookmarks") },
                singleLine = true,
                colors = TextFieldDefaults.colors(
                    focusedContainerColor = Color.Transparent,
                    unfocusedContainerColor = Color.Transparent,
                    disabledContainerColor = Color.Transparent,
                    focusedIndicatorColor = Color.Transparent,
                    unfocusedIndicatorColor = Color.Transparent,
                    disabledIndicatorColor = Color.Transparent
                ),
                textStyle = MaterialTheme.typography.bodyLarge
            )

            AnimatedVisibility(
                visible = query.isNotEmpty(),
                enter = scaleIn(animationSpec = tween(durationMillis = 200, easing = FastOutSlowInEasing)) +
                    fadeIn(animationSpec = tween(durationMillis = 150)),
                exit = scaleOut(animationSpec = tween(durationMillis = 150, easing = FastOutLinearInEasing)) +
                    fadeOut(animationSpec = tween(durationMillis = 100))
            ) {
                IconButton(
                    onClick = onClearQuery,
                    modifier = Modifier.padding(end = 4.dp)
                ) {
                    Icon(
                        imageVector = Icons.Default.Clear,
                        contentDescription = "Clear",
                        tint = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }
        }
    }
}

@Composable
private fun CompactSearchBar(
    query: String,
    profileName: String?,
    profileImage: String?,
    profileImageHeaders: Map<String, String>,
    onProfileClick: () -> Unit,
    onClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    val searchHeight = 56.dp

    Row(
        modifier = modifier.fillMaxWidth(),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Surface(
            modifier = Modifier
                .weight(1f)
                .height(searchHeight),
            shape = RoundedCornerShape(28.dp),
            color = MaterialTheme.colorScheme.surfaceContainerHigh,
            onClick = onClick
        ) {
            Row(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(horizontal = 16.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Icon(
                    imageVector = Icons.Default.Search,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.size(20.dp)
                )
                Text(
                    text = query.ifEmpty { "Search your bookmarks" },
                    style = MaterialTheme.typography.bodyMedium,
                    color = if (query.isEmpty()) {
                        MaterialTheme.colorScheme.onSurfaceVariant
                    } else {
                        MaterialTheme.colorScheme.onSurface
                    },
                    modifier = Modifier.padding(start = 12.dp),
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
            }
        }

        ProfileAvatar(
            name = profileName,
            image = profileImage,
            imageHeaders = profileImageHeaders,
            onClick = onProfileClick,
            modifier = Modifier
                .padding(start = 12.dp)
                .size(52.dp)
        )
    }
}

@Composable
private fun ProfileAvatar(
    name: String?,
    image: String?,
    imageHeaders: Map<String, String>,
    onClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current
    val fallbackInitial = name?.trim()?.firstOrNull()?.uppercase() ?: "U"
    val imageRequest = remember(context, image, imageHeaders) {
        if (image.isNullOrBlank()) {
            null
        } else {
            ImageRequest.Builder(context)
                .data(image)
                .apply {
                    imageHeaders.forEach { (key, value) ->
                        setHeader(key, value)
                    }
                }
                .crossfade(true)
                .build()
        }
    }

    if (imageRequest != null) {
        AsyncImage(
            model = imageRequest,
            contentDescription = "Profile",
            contentScale = ContentScale.Crop,
            modifier = modifier
                .clip(CircleShape)
                .clickable(onClick = onClick)
        )
    } else {
        Surface(
            shape = CircleShape,
            color = MaterialTheme.colorScheme.primaryContainer,
            modifier = modifier.clickable(onClick = onClick)
        ) {
            Box(contentAlignment = Alignment.Center) {
                Text(
                    text = fallbackInitial,
                    style = MaterialTheme.typography.labelLarge,
                    color = MaterialTheme.colorScheme.onPrimaryContainer
                )
            }
        }
    }
}

@OptIn(ExperimentalMaterial3ExpressiveApi::class)
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

@OptIn(ExperimentalMaterial3ExpressiveApi::class)
@Composable
private fun TagsContent(
    isLoading: Boolean,
    errorMessage: String?,
    tags: List<TagItem>,
    selectedTag: TagItem?,
    selectedTagDetails: TagDetails?,
    isTagBookmarksLoading: Boolean,
    tagBookmarks: List<BookmarkItem>,
    tagBookmarksErrorMessage: String?,
    onRefresh: () -> Unit,
    onTagClick: (TagItem) -> Unit,
    onCloseTagDetail: () -> Unit,
    onRefreshTagBookmarks: () -> Unit
) {
    if (selectedTag != null) {
        TagDetailContent(
            fallbackTag = selectedTag,
            tagDetails = selectedTagDetails,
            isLoading = isTagBookmarksLoading,
            errorMessage = tagBookmarksErrorMessage,
            bookmarks = tagBookmarks,
            onBack = onCloseTagDetail,
            onRetry = onRefreshTagBookmarks
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
                LazyColumn(
                    modifier = Modifier.fillMaxSize(),
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

@Composable
private fun TagDetailContent(
    fallbackTag: TagItem,
    tagDetails: TagDetails?,
    isLoading: Boolean,
    errorMessage: String?,
    bookmarks: List<BookmarkItem>,
    onBack: () -> Unit,
    onRetry: () -> Unit
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
                    modifier = Modifier.fillMaxSize(),
                    contentPadding = PaddingValues(16.dp),
                    verticalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    items(bookmarks, key = { it.id }) { bookmark ->
                        BookmarkListItem(
                            bookmark = bookmark,
                            cardColors = cardColors,
                            cardElevation = cardElevation
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun HomeContent(
    isLoading: Boolean,
    isRefreshing: Boolean,
    errorMessage: String?,
    bookmarks: List<BookmarkItem>,
    isSearchActive: Boolean,
    searchQuery: String,
    onRetry: () -> Unit,
    onRefresh: () -> Unit
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
                AnimatedContent(
                    targetState = isSearchActive,
                    transitionSpec = {
                        fadeIn(animationSpec = tween(200)) togetherWith
                        fadeOut(animationSpec = tween(150))
                    },
                    label = "content"
                ) { active ->
                    if (active) {
                        SearchResultsContent(bookmarks = bookmarks)
                    } else {
                        BookmarkGridContent(bookmarks = bookmarks)
                    }
                }
            }
        }
    }
}

@OptIn(ExperimentalMaterial3ExpressiveApi::class)
@Composable
private fun ExpressiveLoadingIndicator() {
    ContainedLoadingIndicator(modifier = Modifier.size(72.dp))
}

@OptIn(ExperimentalMaterial3ExpressiveApi::class)
@Composable
private fun PullToRefreshExpressiveIndicator(
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
private fun SearchEmptyState() {
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
private fun SearchResultsContent(
    bookmarks: List<BookmarkItem>
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

    Column(modifier = Modifier.fillMaxSize()) {
        Text(
            text = "Results",
            style = MaterialTheme.typography.titleMedium,
            fontWeight = FontWeight.SemiBold,
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
                cardElevation = cardElevation
            )
        }
    }
    }
}

@Composable
private fun BookmarkListItem(
    bookmark: BookmarkItem,
    cardColors: CardColors,
    cardElevation: CardElevation
) {
    val context = LocalContext.current
    val imageRequest = remember(context, bookmark.imageUrl) {
        bookmark.imageUrl?.takeIf { it.isNotBlank() }?.let { imageUrl ->
            ImageRequest.Builder(context)
                .data(imageUrl)
                .crossfade(true)
                .build()
        }
    }

    ElevatedCard(
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

            Column(
                modifier = Modifier.weight(1f)
            ) {
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
private fun BookmarkGridContent(bookmarks: List<BookmarkItem>) {
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
        modifier = Modifier.fillMaxSize(),
        contentPadding = PaddingValues(16.dp),
        horizontalArrangement = Arrangement.spacedBy(16.dp),
        verticalItemSpacing = 16.dp
    ) {
        items(bookmarks, key = { it.id }) { bookmark ->
            KeepStyleCard(
                bookmark = bookmark,
                cardColors = cardColors,
                cardElevation = cardElevation
            )
        }
    }
}

@Composable
private fun KeepStyleCard(
    bookmark: BookmarkItem,
    cardColors: CardColors,
    cardElevation: CardElevation
) {
    val context = LocalContext.current
    val imageRequest = remember(context, bookmark.imageUrl) {
        bookmark.imageUrl?.takeIf { it.isNotBlank() }?.let { imageUrl ->
            ImageRequest.Builder(context)
                .data(imageUrl)
                .crossfade(true)
                .build()
        }
    }

    ElevatedCard(
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

            Column(
                modifier = Modifier.padding(16.dp)
            ) {
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
private fun TagChipRow(tags: List<String>) {
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

@Composable
private fun SettingsContent(onLogout: () -> Unit) {
    Box(
        modifier = Modifier.fillMaxSize(),
        contentAlignment = Alignment.Center
    ) {
        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            Text(
                text = "Settings",
                style = MaterialTheme.typography.headlineMedium,
                color = MaterialTheme.colorScheme.onBackground
            )
            Button(
                onClick = onLogout,
                modifier = Modifier.padding(top = 16.dp)
            ) {
                Text("Logout")
            }
        }
    }
}
