package com.ruizlenato.karabau.ui.screens

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.Logout
import androidx.compose.material.icons.outlined.EmojiEmotions
import androidx.compose.material.icons.outlined.Info
import androidx.compose.material.icons.outlined.Person
import androidx.compose.material.icons.outlined.VolunteerActivism
import androidx.compose.material.icons.outlined.Archive
import androidx.compose.material.icons.outlined.Description
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.IconButtonDefaults
import androidx.compose.material3.LargeTopAppBar
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Switch
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.material3.rememberTopAppBarState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Modifier
import androidx.compose.ui.Alignment
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.unit.dp
import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.core.net.toUri
import com.ruizlenato.karabau.ui.licenses.OpenSourceLicensesActivity
import com.ruizlenato.karabau.R
import com.ruizlenato.karabau.data.local.SettingsDataStore
import com.ruizlenato.karabau.data.model.ArchiveDisplayBehaviour
import com.ruizlenato.karabau.data.model.Settings
import com.ruizlenato.karabau.data.remote.KarabauRepository
import com.ruizlenato.karabau.data.remote.ApiResult
import kotlinx.coroutines.launch
import androidx.compose.runtime.LaunchedEffect

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SettingsContent(
    onLogout: () -> Unit,
    onBack: () -> Unit
) {
    val scrollBehavior = TopAppBarDefaults.exitUntilCollapsedScrollBehavior(rememberTopAppBarState())
    val context = LocalContext.current
    val appVersion = remember {
        try {
            val packageInfo = context.packageManager.getPackageInfo(context.packageName, 0)
            packageInfo.versionName ?: "1.0"
        } catch (_: Exception) {
            "1.0"
        }
    }
    val settingsDataStore = remember(context) { SettingsDataStore(context.applicationContext) }
    val currentSettings by settingsDataStore.settingsFlow.collectAsState(initial = Settings())
    val scope = rememberCoroutineScope()

    Scaffold(
        modifier = Modifier.nestedScroll(scrollBehavior.nestedScrollConnection),
        topBar = {
            LargeTopAppBar(
                title = {
                    Text(
                        stringResource(R.string.settings_title),
                        modifier = Modifier.padding(start = 10.dp),
                        style = MaterialTheme.typography.headlineLarge
                    )
                },
                navigationIcon = {
                    IconButton(
                        onClick = onBack,
                        modifier = Modifier.padding(start = 10.dp),
                        colors = IconButtonDefaults.iconButtonColors(
                            containerColor = MaterialTheme.colorScheme.surfaceContainerHigh,
                            contentColor = MaterialTheme.colorScheme.onSurface
                        )
                    ) {
                        Icon(
                            imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                            contentDescription = stringResource(R.string.back)
                        )
                    }
                },
                scrollBehavior = scrollBehavior
            )
        }
    ) { paddingValues ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(paddingValues)
                .padding(horizontal = 16.dp)
                .verticalScroll(rememberScrollState())
        ) {
            Spacer(modifier = Modifier.height(8.dp))

            SettingsCategory(title = stringResource(R.string.settings_account))
            Spacer(modifier = Modifier.height(2.dp))
            SegmentedItem(
                position = SegmentedPosition.SINGLE,
                onClick = onLogout,
                icon = {
                    Icon(
                        Icons.AutoMirrored.Filled.Logout,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.error
                    )
                },
                title = { Text(stringResource(R.string.logout), color = MaterialTheme.colorScheme.error) }
            )

            Spacer(modifier = Modifier.height(16.dp))

            SettingsCategory(title = stringResource(R.string.settings_bookmarks))
            Spacer(modifier = Modifier.height(2.dp))
            SegmentedItem(
                position = SegmentedPosition.TOP,
                icon = { Icon(Icons.Outlined.Archive, contentDescription = null) },
                title = { Text(stringResource(R.string.archived_favorites)) },
                subtitle = {
                    Text(stringResource(R.string.show_archived_favorites_desc))
                },
                trailingContent = {
                    Switch(
                        checked = currentSettings.archiveDisplayBehaviour == ArchiveDisplayBehaviour.SHOW,
                        onCheckedChange = { checked ->
                            val value = if (checked) {
                                ArchiveDisplayBehaviour.SHOW
                            } else {
                                ArchiveDisplayBehaviour.HIDE
                            }

                            if (currentSettings.archiveDisplayBehaviour != value) {
                                scope.launch {
                                    val updated = currentSettings.copy(archiveDisplayBehaviour = value)
                                    settingsDataStore.updateSettings(updated)

                                    val repo = KarabauRepository().apply { configure(updated) }
                                    repo.updateArchiveDisplayBehaviour(value)
                                }
                            }
                        }
                    )
                }
            )

            Spacer(modifier = Modifier.height(2.dp))

            SegmentedItem(
                position = SegmentedPosition.BOTTOM,
                icon = {
                    Icon(Icons.Outlined.EmojiEmotions, contentDescription = null)
                },
                title = { Text(stringResource(R.string.list_icons)) },
                subtitle = {
                    Text(stringResource(R.string.show_list_icons_desc))
                },
                trailingContent = {
                    Switch(
                        checked = currentSettings.showListIcons,
                        onCheckedChange = { checked ->
                            if (currentSettings.showListIcons != checked) {
                                scope.launch {
                                    settingsDataStore.updateSettings(
                                        currentSettings.copy(showListIcons = checked)
                                    )
                                }
                            }
                        }
                    )
                }
            )

            Spacer(modifier = Modifier.height(16.dp))

            SettingsCategory(title = stringResource(R.string.settings_about))
            Spacer(modifier = Modifier.height(2.dp))
            SegmentedItem(
                position = SegmentedPosition.TOP,
                onClick = {
                    val intent = android.content.Intent(
                        android.content.Intent.ACTION_VIEW,
                        "https://github.com/ruizlenato".toUri()
                    )
                    context.startActivity(intent)
                },
                icon = { Icon(Icons.Outlined.Person, contentDescription = null) },
                title = { Text("Luiz Renato") },
                subtitle = { Text(stringResource(R.string.developer)) }
            )

            Spacer(modifier = Modifier.height(2.dp))

            SegmentedItem(
                position = SegmentedPosition.MIDDLE,
                onClick = {
                    val intent = android.content.Intent(
                        android.content.Intent.ACTION_VIEW,
                        "https://github.com/ruizlenato/karabau".toUri()
                    )
                    context.startActivity(intent)
                },
                icon = {
                    Icon(
                        painter = painterResource(id = R.drawable.ic_github),
                        contentDescription = null
                    )
                },
                title = { Text("GitHub") },
                subtitle = { Text(stringResource(R.string.github_repo_desc)) }
            )

            Spacer(modifier = Modifier.height(2.dp))

            SegmentedItem(
                position = SegmentedPosition.MIDDLE,
                onClick = {
                    val intent = android.content.Intent(
                        android.content.Intent.ACTION_VIEW,
                        "https://ruizlenato.github.io/donate".toUri()
                    )
                    context.startActivity(intent)
                },
                icon = { Icon(Icons.Outlined.VolunteerActivism, contentDescription = null) },
                title = { Text(stringResource(R.string.donate)) },
                subtitle = { Text(stringResource(R.string.donate_desc)) }
            )

            Spacer(modifier = Modifier.height(2.dp))

            SegmentedItem(
                position = SegmentedPosition.MIDDLE,
                onClick = {
                    val intent = android.content.Intent(context, OpenSourceLicensesActivity::class.java)
                    context.startActivity(intent)
                },
                icon = { Icon(Icons.Outlined.Description, contentDescription = null) },
                title = { Text(stringResource(R.string.open_source_licenses)) },
                subtitle = { Text(stringResource(R.string.open_source_licenses_desc)) }
            )

            Spacer(modifier = Modifier.height(2.dp))

            SegmentedItem(
                position = SegmentedPosition.BOTTOM,
                icon = { Icon(Icons.Outlined.Info, contentDescription = null) },
                title = { Text(stringResource(R.string.version)) },
                subtitle = { Text(appVersion) }
            )

            Spacer(modifier = Modifier.height(16.dp))
        }
    }

    LaunchedEffect(currentSettings.address, currentSettings.apiKey, currentSettings.apiKeyId) {
        val current = currentSettings
        if (current.address.isBlank() || current.apiKey.isNullOrBlank()) return@LaunchedEffect

        val repo = KarabauRepository().apply { configure(current) }
        when (val remote = repo.getArchiveDisplayBehaviour()) {
            is ApiResult.Success -> {
                if (current.archiveDisplayBehaviour != remote.data) {
                    settingsDataStore.updateSettings(
                        current.copy(archiveDisplayBehaviour = remote.data)
                    )
                }
            }

            is ApiResult.Error -> Unit
            is ApiResult.NetworkError -> Unit
        }
    }
}

@Composable
private fun SettingsCategory(
    title: String,
    modifier: Modifier = Modifier
) {
    Text(
        text = title,
        style = MaterialTheme.typography.labelLarge,
        color = MaterialTheme.colorScheme.primary,
        modifier = modifier.padding(start = 16.dp, bottom = 8.dp)
    )
}

private enum class SegmentedPosition {
    TOP, MIDDLE, BOTTOM, SINGLE
}

@Composable
private fun SegmentedItem(
    position: SegmentedPosition,
    onClick: (() -> Unit)? = null,
    icon: @Composable (() -> Unit)? = null,
    title: @Composable () -> Unit,
    subtitle: @Composable (() -> Unit)? = null,
    trailingContent: @Composable (() -> Unit)? = null
) {
    val shape = when (position) {
        SegmentedPosition.TOP -> RoundedCornerShape(topStart = 28.dp, topEnd = 28.dp, bottomStart = 4.dp, bottomEnd = 4.dp)
        SegmentedPosition.MIDDLE -> RoundedCornerShape(4.dp)
        SegmentedPosition.BOTTOM -> RoundedCornerShape(topStart = 4.dp, topEnd = 4.dp, bottomStart = 28.dp, bottomEnd = 28.dp)
        SegmentedPosition.SINGLE -> RoundedCornerShape(28.dp)
    }

    Surface(
        modifier = Modifier
            .fillMaxWidth()
            .then(if (onClick != null) Modifier.clickable(onClick = onClick) else Modifier),
        shape = shape,
        color = MaterialTheme.colorScheme.surfaceContainer
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp, vertical = 12.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            if (icon != null) {
                Box(
                    modifier = Modifier
                        .size(34.dp)
                        .padding(end = 12.dp),
                    contentAlignment = Alignment.Center
                ) {
                    androidx.compose.runtime.CompositionLocalProvider(
                        androidx.compose.material3.LocalContentColor provides MaterialTheme.colorScheme.onSurfaceVariant
                    ) {
                        icon()
                    }
                }
            }

            Column(modifier = Modifier.weight(1f)) {
                androidx.compose.runtime.CompositionLocalProvider(
                    androidx.compose.material3.LocalContentColor provides MaterialTheme.colorScheme.onSurface
                ) {
                    title()
                }
                subtitle?.let {
                    Spacer(modifier = Modifier.height(2.dp))
                    androidx.compose.runtime.CompositionLocalProvider(
                        androidx.compose.material3.LocalTextStyle provides MaterialTheme.typography.bodyMedium,
                        androidx.compose.material3.LocalContentColor provides MaterialTheme.colorScheme.onSurfaceVariant
                    ) {
                        it()
                    }
                }
            }

            trailingContent?.let {
                Spacer(modifier = Modifier.width(12.dp))
                it()
            }
        }
    }
}
