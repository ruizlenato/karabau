package com.ruizlenato.karabau.ui.processtext

import android.content.Intent
import android.os.Bundle
import android.util.Patterns
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.activity.compose.BackHandler
import androidx.activity.compose.setContent
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.slideInVertically
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.systemBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.core.view.WindowCompat
import androidx.lifecycle.lifecycleScope
import androidx.lifecycle.viewmodel.compose.viewModel
import com.ruizlenato.karabau.R
import com.ruizlenato.karabau.MainActivity
import com.ruizlenato.karabau.data.local.SettingsDataStore
import com.ruizlenato.karabau.data.model.isLoggedIn
import com.ruizlenato.karabau.data.remote.ApiResult
import com.ruizlenato.karabau.ui.theme.KarabauTheme
import com.ruizlenato.karabau.ui.viewmodel.CreateBookmarkViewModel
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import java.net.URI

class SaveBookmarkProcessTextActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        WindowCompat.setDecorFitsSystemWindows(window, false)

        val selectedText = intent.getCharSequenceExtra(Intent.EXTRA_PROCESS_TEXT)
            ?.toString()
            ?.trim()
            .orEmpty()

        lifecycleScope.launch {
            val isLoggedIn = SettingsDataStore(applicationContext)
                .settingsFlow
                .first()
                .isLoggedIn()

            if (!isLoggedIn) {
                Toast.makeText(
                    this@SaveBookmarkProcessTextActivity,
                    getString(R.string.process_text_login_required),
                    Toast.LENGTH_SHORT
                ).show()

                startActivity(
                    Intent(this@SaveBookmarkProcessTextActivity, MainActivity::class.java).apply {
                        flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP or Intent.FLAG_ACTIVITY_SINGLE_TOP
                    }
                )
                finish()
                return@launch
            }

            setContent {
                KarabauTheme {
                    ProcessTextBookmarkDialog(
                        selectedText = selectedText,
                        onClose = { finish() }
                    )
                }
            }
        }
    }
}

@Composable
private fun ProcessTextBookmarkDialog(
    selectedText: String,
    onClose: () -> Unit
) {
    val context = LocalContext.current
    val createBookmarkViewModel: CreateBookmarkViewModel = viewModel()
    val scope = rememberCoroutineScope()

    var url by rememberSaveable(selectedText) { mutableStateOf(selectedText) }
    var title by rememberSaveable { mutableStateOf("") }
    var note by rememberSaveable { mutableStateOf("") }
    var isSaving by remember { mutableStateOf(false) }
    var saveError by rememberSaveable { mutableStateOf<String?>(null) }
    var touched by rememberSaveable { mutableStateOf(false) }
    var panelVisible by remember { mutableStateOf(false) }

    val urlError = when {
        !touched -> null
        url.isBlank() -> stringResource(R.string.process_text_url_required)
        !isValidHttpUrlFromProcessText(url) -> stringResource(R.string.process_text_url_invalid)
        else -> null
    }
    val canSave = !isSaving && urlError == null && url.isNotBlank()

    LaunchedEffect(selectedText) {
        if (!isValidHttpUrlFromProcessText(selectedText)) {
            Toast.makeText(context, context.getString(R.string.process_text_invalid_link), Toast.LENGTH_SHORT).show()
            onClose()
        }
    }

    LaunchedEffect(Unit) {
        panelVisible = true
    }

    BackHandler(onBack = onClose)

    Box(
        modifier = Modifier
            .fillMaxSize()
            .clickable(
                interactionSource = remember { MutableInteractionSource() },
                indication = null,
                onClick = onClose
            )
            .systemBarsPadding()
            .imePadding(),
        contentAlignment = Alignment.TopCenter
    ) {
        AnimatedVisibility(
            visible = panelVisible,
            enter = slideInVertically(
                initialOffsetY = { fullHeight -> fullHeight / 3 },
                animationSpec = tween(durationMillis = 480, easing = FastOutSlowInEasing)
            ) + fadeIn(animationSpec = tween(durationMillis = 420, easing = FastOutSlowInEasing))
        ) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 20.dp, vertical = 10.dp)
                    .clickable(
                        interactionSource = remember { MutableInteractionSource() },
                        indication = null,
                        onClick = {}
                    )
            ) {
                Card(
                    modifier = Modifier
                        .fillMaxWidth()
                        .heightIn(max = 520.dp)
                        .verticalScroll(rememberScrollState()),
                    shape = RoundedCornerShape(24.dp),
                    colors = CardDefaults.cardColors(
                        containerColor = MaterialTheme.colorScheme.background,
                        contentColor = MaterialTheme.colorScheme.onBackground
                    ),
                    elevation = CardDefaults.cardElevation(defaultElevation = 10.dp)
                ) {
                    Column(modifier = Modifier.padding(20.dp)) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.Start
                    ) {
                        Text(
                            text = stringResource(R.string.save_bookmark_dialog_title),
                            style = MaterialTheme.typography.titleLarge,
                            fontWeight = FontWeight.SemiBold,
                            color = MaterialTheme.colorScheme.onBackground
                        )
                    }

                    Spacer(modifier = Modifier.height(4.dp))

                    Text(
                        text = stringResource(R.string.save_bookmark_dialog_subtitle),
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onBackground.copy(alpha = 0.8f)
                    )

                    Spacer(modifier = Modifier.height(16.dp))

                    OutlinedTextField(
                        modifier = Modifier.fillMaxWidth(),
                        value = url,
                        onValueChange = {
                            url = it
                            touched = true
                        },
                        label = { Text(stringResource(R.string.selected_link)) },
                        placeholder = { Text(stringResource(R.string.selected_link_placeholder)) },
                        singleLine = true,
                        isError = urlError != null,
                        supportingText = { if (urlError != null) Text(urlError) }
                    )

                    Spacer(modifier = Modifier.height(8.dp))

                    OutlinedTextField(
                        modifier = Modifier.fillMaxWidth(),
                        value = title,
                        onValueChange = { title = it },
                        label = { Text(stringResource(R.string.title_optional)) },
                        singleLine = true
                    )

                    Spacer(modifier = Modifier.height(8.dp))

                    OutlinedTextField(
                        modifier = Modifier.fillMaxWidth(),
                        value = note,
                        onValueChange = { note = it },
                        label = { Text(stringResource(R.string.note_optional)) },
                        placeholder = { Text(stringResource(R.string.add_context_bookmark)) }
                    )

                    if (saveError != null) {
                        Spacer(modifier = Modifier.height(8.dp))
                        Text(
                            text = saveError!!,
                            color = MaterialTheme.colorScheme.error,
                            style = MaterialTheme.typography.bodySmall
                        )
                    }
                }
            }

                Spacer(modifier = Modifier.height(14.dp))

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Button(
                        onClick = onClose,
                        enabled = !isSaving,
                        shape = RoundedCornerShape(28.dp),
                        colors = ButtonDefaults.buttonColors(
                            containerColor = MaterialTheme.colorScheme.surfaceVariant,
                            contentColor = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    ) {
                        Text(stringResource(R.string.cancel))
                    }

                    Button(
                        enabled = canSave,
                        onClick = {
                            touched = true
                            if (url.isBlank() || !isValidHttpUrlFromProcessText(url)) return@Button

                            scope.launch {
                                isSaving = true
                                saveError = null
                                when (val result = createBookmarkViewModel.submitBookmark(url, title, note)) {
                                    is ApiResult.Success -> {
                                        Toast.makeText(context, context.getString(R.string.saved), Toast.LENGTH_SHORT).show()
                                        onClose()
                                    }
                                    is ApiResult.Error -> saveError = result.message
                                    is ApiResult.NetworkError -> saveError = result.message
                                }
                                isSaving = false
                            }
                        },
                        shape = RoundedCornerShape(28.dp),
                        colors = ButtonDefaults.buttonColors(
                            containerColor = MaterialTheme.colorScheme.onPrimaryContainer,
                            contentColor = MaterialTheme.colorScheme.primaryContainer
                        )
                    ) {
                        Text(if (isSaving) stringResource(R.string.saving) else stringResource(R.string.save_bookmark))
                    }
                }
            }
        }
    }
}

private fun isValidHttpUrlFromProcessText(value: String): Boolean {
    val normalized = value.trim()
    if (normalized.isBlank()) return false
    if (!Patterns.WEB_URL.matcher(normalized).matches()) return false

    return runCatching {
        val uri = URI(normalized)
        val scheme = uri.scheme?.lowercase()
        (scheme == "http" || scheme == "https") && !uri.host.isNullOrBlank()
    }.getOrDefault(false)
}
