package com.ruizlenato.karabau.ui.screens

import androidx.compose.animation.AnimatedContentScope
import androidx.compose.animation.EnterExitState
import androidx.compose.animation.ExperimentalSharedTransitionApi
import androidx.compose.animation.SharedTransitionScope
import androidx.compose.animation.animateColor
import androidx.compose.animation.core.EaseInCubic
import androidx.compose.animation.core.EaseOutCubic
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.animateDp
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.scaleIn
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.navigationBars
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import com.ruizlenato.karabau.data.local.SettingsDataStore
import com.ruizlenato.karabau.data.remote.ApiResult
import com.ruizlenato.karabau.data.remote.KarabauRepository
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import java.net.URI

private const val SHARED_ELEMENT_KEY = "create_bookmark_container"

@OptIn(ExperimentalSharedTransitionApi::class, ExperimentalMaterial3Api::class)
@Composable
fun CreateBookmarkScreen(
    onBack: () -> Unit,
    onSaved: () -> Unit,
    sharedTransitionScope: SharedTransitionScope,
    animatedContentScope: AnimatedContentScope
) {
    var url by rememberSaveable { mutableStateOf("") }
    var title by rememberSaveable { mutableStateOf("") }
    var note by rememberSaveable { mutableStateOf("") }
    var urlTouched by rememberSaveable { mutableStateOf(false) }
    var saveError by remember { mutableStateOf<String?>(null) }
    var isSaving by remember { mutableStateOf(false) }

    val coroutineScope = rememberCoroutineScope()
    val repository = remember { KarabauRepository() }
    val context = androidx.compose.ui.platform.LocalContext.current

    val urlError = when {
        !urlTouched -> null
        url.isBlank() -> "URL is required"
        !isValidHttpUrl(url) -> "Enter a valid http(s) URL"
        else -> null
    }

    with(sharedTransitionScope) {
        val transition = animatedContentScope.transition

        val cornerRadius by transition.animateDp(
            transitionSpec = {
                if (targetState == EnterExitState.PostExit) {
                    tween(durationMillis = 200, easing = EaseInCubic)
                } else {
                    tween(durationMillis = 400, easing = EaseOutCubic)
                }
            },
            label = "containerCornerRadius"
        ) { state ->
            when (state) {
                EnterExitState.Visible -> 0.dp
                EnterExitState.PreEnter -> 16.dp
                EnterExitState.PostExit -> 16.dp
            }
        }

        val containerColor by transition.animateColor(
            transitionSpec = {
                tween(durationMillis = 400, easing = FastOutSlowInEasing)
            },
            label = "containerColor"
        ) { state ->
            when (state) {
                EnterExitState.Visible -> MaterialTheme.colorScheme.surface
                EnterExitState.PreEnter -> MaterialTheme.colorScheme.primaryContainer
                EnterExitState.PostExit -> MaterialTheme.colorScheme.primaryContainer
            }
        }

        Box(
            modifier = Modifier
                .sharedBounds(
                    sharedContentState = rememberSharedContentState(key = SHARED_ELEMENT_KEY),
                    animatedVisibilityScope = animatedContentScope,
                    clipInOverlayDuringTransition = OverlayClip(RoundedCornerShape(16.dp)),
                    enter = fadeIn(tween(350, delayMillis = 150, easing = FastOutSlowInEasing)) +
                            scaleIn(
                                initialScale = 0.92f,
                                animationSpec = tween(220, delayMillis = 90, easing = FastOutSlowInEasing)
                            ),
                    exit = fadeOut(tween(200, easing = FastOutSlowInEasing))
                )
                .fillMaxSize(),
            contentAlignment = Alignment.TopStart
        ) {
            Surface(
                modifier = Modifier.fillMaxSize(),
                shape = RoundedCornerShape(cornerRadius),
                color = containerColor
            ) {
                Scaffold(
                    topBar = {
                        TopAppBar(
                            title = { Text("New Bookmark") },
                            navigationIcon = {
                                IconButton(onClick = onBack) {
                                    Icon(
                                        imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                                        contentDescription = "Back"
                                    )
                                }
                            },
                            actions = {
                                TextButton(
                                    onClick = {
                                        urlTouched = true
                                        if (urlError != null || isSaving) return@TextButton
                                        coroutineScope.launch {
                                            isSaving = true
                                            saveError = null
                                            val result = submitBookmark(
                                                context = context,
                                                repository = repository,
                                                url = url,
                                                title = title,
                                                note = note
                                            )
                                            when (result) {
                                                is ApiResult.Success -> onSaved()
                                                is ApiResult.Error -> saveError = result.message
                                                is ApiResult.NetworkError -> saveError = result.message
                                            }
                                            isSaving = false
                                        }
                                    },
                                    enabled = !isSaving && url.isNotBlank()
                                ) {
                                    Text(if (isSaving) "Saving..." else "Save")
                                }
                            },
                            modifier = Modifier.statusBarsPadding()
                        )
                    }
                ) { paddingValues ->
                    Column(
                        modifier = Modifier
                            .fillMaxSize()
                            .padding(paddingValues)
                            .windowInsetsPadding(WindowInsets.navigationBars)
                            .imePadding()
                            .padding(horizontal = 16.dp, vertical = 12.dp)
                            .verticalScroll(rememberScrollState()),
                        verticalArrangement = Arrangement.spacedBy(14.dp)
                    ) {
                        OutlinedTextField(
                            value = url,
                            onValueChange = {
                                url = it
                                if (!urlTouched) urlTouched = true
                            },
                            label = { Text("URL") },
                            placeholder = { Text("https://example.com") },
                            modifier = Modifier.fillMaxWidth(),
                            keyboardOptions = KeyboardOptions(
                                keyboardType = KeyboardType.Uri,
                                imeAction = ImeAction.Next
                            ),
                            singleLine = true,
                            isError = urlError != null,
                            supportingText = {
                                if (urlError != null) Text(urlError)
                            }
                        )

                        OutlinedTextField(
                            value = title,
                            onValueChange = { title = it },
                            label = { Text("Title (optional)") },
                            modifier = Modifier.fillMaxWidth(),
                            keyboardOptions = KeyboardOptions(imeAction = ImeAction.Next),
                            singleLine = true
                        )

                        OutlinedTextField(
                            value = note,
                            onValueChange = { note = it },
                            label = { Text("Note (optional)") },
                            placeholder = { Text("Add context for this bookmark") },
                            modifier = Modifier.fillMaxWidth(),
                            keyboardOptions = KeyboardOptions(imeAction = ImeAction.Done),
                            minLines = 4,
                            maxLines = 8
                        )

                        saveError?.let { message ->
                            Text(
                                text = message,
                                style = MaterialTheme.typography.bodyMedium,
                                color = MaterialTheme.colorScheme.error
                            )
                        }
                    }
                }
            }
        }
    }
}

private suspend fun submitBookmark(
    context: android.content.Context,
    repository: KarabauRepository,
    url: String,
    title: String,
    note: String
): ApiResult<Unit> {
    val settingsDataStore = SettingsDataStore(context.applicationContext)
    val settings = settingsDataStore.settingsFlow.first()
    repository.configure(settings)

    return repository.createLinkBookmark(
        url = url.trim(),
        title = title.trim().takeIf { it.isNotEmpty() },
        note = note.trim().takeIf { it.isNotEmpty() }
    )
}

private fun isValidHttpUrl(value: String): Boolean {
    val normalized = value.trim()
    if (normalized.isBlank()) return false

    return runCatching {
        val uri = URI(normalized)
        val scheme = uri.scheme?.lowercase()
        (scheme == "http" || scheme == "https") && !uri.host.isNullOrBlank()
    }.getOrDefault(false)
}
