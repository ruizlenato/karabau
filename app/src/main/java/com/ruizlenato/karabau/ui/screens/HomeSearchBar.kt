package com.ruizlenato.karabau.ui.screens

import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.FastOutLinearInEasing
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.animateDpAsState
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.scaleIn
import androidx.compose.animation.scaleOut
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.asPaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBars
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Clear
import androidx.compose.material.icons.filled.Search
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextField
import androidx.compose.material3.TextFieldDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.platform.LocalSoftwareKeyboardController
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import coil.compose.AsyncImage
import coil.request.ImageRequest

@Composable
internal fun HomeTopBar(
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
                (fadeIn(animationSpec = tween(200)) + slideInVertically(
                    initialOffsetY = { -it / 2 },
                    animationSpec = tween(durationMillis = 300, easing = FastOutSlowInEasing)
                ))
                    .togetherWith(
                        fadeOut(animationSpec = tween(150)) + slideOutVertically(
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
                enter = scaleIn(animationSpec = tween(durationMillis = 200, easing = FastOutSlowInEasing)) + fadeIn(animationSpec = tween(durationMillis = 150)),
                exit = scaleOut(animationSpec = tween(durationMillis = 150, easing = FastOutLinearInEasing)) + fadeOut(animationSpec = tween(durationMillis = 100))
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
            shape = androidx.compose.foundation.shape.RoundedCornerShape(28.dp),
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
