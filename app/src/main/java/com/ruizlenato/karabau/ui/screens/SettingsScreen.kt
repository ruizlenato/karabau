package com.ruizlenato.karabau.ui.screens

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.Logout
import androidx.compose.material.icons.filled.Info
import androidx.compose.material.icons.filled.Person
import androidx.compose.material.icons.filled.VolunteerActivism
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.IconButtonDefaults
import androidx.compose.material3.LargeTopAppBar
import androidx.compose.material3.ListItem
import androidx.compose.material3.ListItemDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.material3.rememberTopAppBarState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.unit.dp
import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.core.net.toUri
import com.ruizlenato.karabau.R

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

    Scaffold(
        modifier = Modifier.nestedScroll(scrollBehavior.nestedScrollConnection),
        topBar = {
            LargeTopAppBar(
                title = {
                    Text(
                        "Settings",
                        modifier = Modifier.padding(start = 10.dp),
                        style = MaterialTheme.typography.headlineMedium
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
                            contentDescription = "Back"
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

            SettingsCategory(title = "Account")
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
                title = { Text("Logout", color = MaterialTheme.colorScheme.error) }
            )

            Spacer(modifier = Modifier.height(16.dp))

            SettingsCategory(title = "About")
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
                icon = { Icon(Icons.Default.Person, contentDescription = null) },
                title = { Text("Luiz Renato") },
                subtitle = { Text("Developer") }
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
                subtitle = { Text("Repository with source code") }
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
                icon = { Icon(Icons.Default.VolunteerActivism, contentDescription = null) },
                title = { Text("Donate") },
                subtitle = { Text("Support the project and ongoing development") }
            )

            Spacer(modifier = Modifier.height(2.dp))

            SegmentedItem(
                position = SegmentedPosition.BOTTOM,
                icon = { Icon(Icons.Default.Info, contentDescription = null) },
                title = { Text("Version") },
                subtitle = { Text(appVersion) }
            )

            Spacer(modifier = Modifier.height(16.dp))
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
    subtitle: @Composable (() -> Unit)? = null
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
        ListItem(
            leadingContent = icon,
            headlineContent = title,
            supportingContent = subtitle,
            colors = ListItemDefaults.colors(
                containerColor = Color.Transparent
            )
        )
    }
}
