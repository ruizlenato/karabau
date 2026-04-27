package com.ruizlenato.karabau

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.core.splashscreen.SplashScreen.Companion.installSplashScreen
import androidx.compose.animation.EnterTransition
import androidx.compose.animation.ExitTransition
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInHorizontally
import androidx.compose.animation.slideOutHorizontally
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.Surface
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController
import com.ruizlenato.karabau.data.local.SettingsDataStore
import com.ruizlenato.karabau.data.model.isLoggedIn
import com.ruizlenato.karabau.ui.screens.CreateBookmarkScreen
import com.ruizlenato.karabau.ui.screens.HomeScreen
import com.ruizlenato.karabau.ui.screens.LoginScreen
import com.ruizlenato.karabau.ui.screens.ServerConfigScreen
import com.ruizlenato.karabau.ui.screens.WelcomeScreen
import com.ruizlenato.karabau.ui.theme.KarabauTheme
import com.ruizlenato.karabau.ui.viewmodel.AuthViewModel
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch

private const val ForwardBackwardDuration = 450

private fun forwardEnter(): EnterTransition {
    return slideInHorizontally(
        initialOffsetX = { it },
        animationSpec = tween(durationMillis = ForwardBackwardDuration, easing = FastOutSlowInEasing)
    ) + fadeIn(
        initialAlpha = 0.35f,
        animationSpec = tween(durationMillis = ForwardBackwardDuration)
    )
}

private fun forwardExit(): ExitTransition {
    return slideOutHorizontally(
        targetOffsetX = { -it },
        animationSpec = tween(durationMillis = ForwardBackwardDuration, easing = FastOutSlowInEasing)
    ) + fadeOut(
        targetAlpha = 0.35f,
        animationSpec = tween(durationMillis = ForwardBackwardDuration)
    )
}

private fun backwardEnter(): EnterTransition {
    return slideInHorizontally(
        initialOffsetX = { -it },
        animationSpec = tween(durationMillis = ForwardBackwardDuration, easing = FastOutSlowInEasing)
    ) + fadeIn(
        initialAlpha = 0.35f,
        animationSpec = tween(durationMillis = ForwardBackwardDuration)
    )
}

private fun backwardExit(): ExitTransition {
    return slideOutHorizontally(
        targetOffsetX = { it },
        animationSpec = tween(durationMillis = ForwardBackwardDuration, easing = FastOutSlowInEasing)
    ) + fadeOut(
        targetAlpha = 0.35f,
        animationSpec = tween(durationMillis = ForwardBackwardDuration)
    )
}

sealed class Screen(val route: String) {
    data object Welcome : Screen("welcome")
    data object Login : Screen("login")
    data object ServerConfig : Screen("server_config")
    data object Home : Screen("home")
    data object CreateBookmark : Screen("create_bookmark")
}

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        val splashScreen = installSplashScreen()
        super.onCreate(savedInstanceState)

        splashScreen.setKeepOnScreenCondition { true }

        enableEdgeToEdge()
        setContent {
            KarabauTheme {
                KarabauApp(
                    onReady = {
                        splashScreen.setKeepOnScreenCondition { false }
                    }
                )
            }
        }
    }
}

@OptIn(androidx.compose.animation.ExperimentalSharedTransitionApi::class)
@Composable
fun KarabauApp(
    onReady: () -> Unit
) {
    val navController = rememberNavController()
    val coroutineScope = rememberCoroutineScope()
    val context = androidx.compose.ui.platform.LocalContext.current
    val settingsDataStore = remember { SettingsDataStore(context) }
    var isLoading by remember { mutableStateOf(true) }
    var isLoggedIn by remember { mutableStateOf(false) }

    LaunchedEffect(Unit) {
        val settings = settingsDataStore.settingsFlow.first()
        isLoggedIn = settings.isLoggedIn()
        isLoading = false
    }

    if (!isLoading) {
        LaunchedEffect(Unit) {
            onReady()
        }

        Surface(
            modifier = Modifier.fillMaxSize(),
            color = androidx.compose.material3.MaterialTheme.colorScheme.background
        ) {
            androidx.compose.animation.SharedTransitionLayout {
                NavHost(
                    navController = navController,
                    startDestination = if (isLoggedIn) Screen.Home.route else Screen.Welcome.route
                ) {
                    composable(
                        route = Screen.Welcome.route,
                        enterTransition = { backwardEnter() },
                        exitTransition = { forwardExit() },
                        popEnterTransition = { backwardEnter() },
                        popExitTransition = { backwardExit() }
                    ) {
                        WelcomeScreen(
                            onContinue = {
                                navController.navigate(Screen.ServerConfig.route) {
                                    popUpTo(Screen.Welcome.route) { inclusive = false }
                                }
                            }
                        )
                    }

                    composable(
                        route = Screen.ServerConfig.route,
                        enterTransition = { forwardEnter() },
                        exitTransition = { forwardExit() },
                        popEnterTransition = { backwardEnter() },
                        popExitTransition = { backwardExit() }
                    ) {
                        val authViewModel: AuthViewModel = viewModel()
                        val uiState by authViewModel.uiState.collectAsStateWithLifecycle()

                        ServerConfigScreen(
                            onBackClick = { navController.popBackStack() },
                            onAddressChange = { address ->
                                authViewModel.onServerAddressChange(address)
                            },
                            onContinue = { address ->
                                coroutineScope.launch {
                                    authViewModel.onServerAddressChange(address)
                                    navController.navigate(Screen.Login.route) {
                                        popUpTo(Screen.ServerConfig.route) { inclusive = false }
                                    }
                                }
                            },
                            currentAddress = uiState.serverAddress
                        )
                    }

                    composable(
                        route = Screen.Login.route,
                        enterTransition = {
                            slideInHorizontally(
                                initialOffsetX = { it },
                                animationSpec = tween(300)
                            ) + fadeIn(tween(300))
                        },
                        exitTransition = {
                            slideOutHorizontally(
                                targetOffsetX = { -it },
                                animationSpec = tween(300)
                            ) + fadeOut(tween(300))
                        },
                        popEnterTransition = {
                            slideInHorizontally(
                                initialOffsetX = { -it / 3 },
                                animationSpec = tween(300)
                            ) + fadeIn(tween(300))
                        },
                        popExitTransition = {
                            slideOutHorizontally(
                                targetOffsetX = { it },
                                animationSpec = tween(300)
                            ) + fadeOut(tween(300))
                        }
                    ) {
                        LoginScreen(
                            onBackClick = { navController.popBackStack() },
                            onLoginSuccess = {
                                isLoggedIn = true
                                navController.navigate(Screen.Home.route) {
                                    popUpTo(Screen.Welcome.route) { inclusive = true }
                                }
                            }
                        )
                    }

                    composable(
                        route = Screen.Home.route,
                        enterTransition = { fadeIn(tween(400)) },
                        exitTransition = { fadeOut(tween(300)) }
                    ) {
        HomeScreen(
                            onLogout = {
                                coroutineScope.launch {
                                    settingsDataStore.clearAuth()
                                    isLoggedIn = false
                                    navController.navigate(Screen.Welcome.route) {
                                        popUpTo(Screen.Home.route) { inclusive = true }
                                    }
                                }
                            },
                            onAddBookmark = {
                                navController.navigate(Screen.CreateBookmark.route)
                            },
                            sharedTransitionScope = this@SharedTransitionLayout,
                            animatedContentScope = this@composable,
                            onBookmarkCreated = {
                            }
                        )

                        androidx.compose.runtime.DisposableEffect(navController.currentBackStackEntry) {
                            val entry = navController.currentBackStackEntry ?: return@DisposableEffect onDispose { }
                            val savedStateHandle = entry.savedStateHandle
                            onDispose {
                                val created = savedStateHandle.get<Boolean>("bookmark_created") ?: false
                                if (created) {
                                    savedStateHandle.remove<Boolean>("bookmark_created")
                                }
                            }
                        }
                    }

                    composable(
                        route = Screen.CreateBookmark.route
                    ) {
                        CreateBookmarkScreen(
                            onBack = { navController.popBackStack() },
                            onSaved = {
                                navController.previousBackStackEntry?.savedStateHandle?.set(
                                    "bookmark_created",
                                    true
                                )
                                navController.popBackStack()
                            },
                            sharedTransitionScope = this@SharedTransitionLayout,
                            animatedContentScope = this@composable
                        )
                    }
                }
            }
        }
    }
}
