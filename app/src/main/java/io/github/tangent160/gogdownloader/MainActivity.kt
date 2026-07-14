package io.github.tangent160.gogdownloader

import android.net.Uri
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.foundation.layout.Box
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController
import androidx.navigation.navArgument
import io.github.tangent160.gogdownloader.ui.GameDetailScreen
import io.github.tangent160.gogdownloader.ui.LibraryScreen
import io.github.tangent160.gogdownloader.ui.LoginScreen
import io.github.tangent160.gogdownloader.ui.PreflightScreen
import io.github.tangent160.gogdownloader.ui.QueueScreen
import io.github.tangent160.gogdownloader.ui.SettingsScreen
import io.github.tangent160.gogdownloader.core.Settings
import io.github.tangent160.gogdownloader.core.SyncMode
import io.github.tangent160.gogdownloader.ui.SyncChoiceScreen
import io.github.tangent160.gogdownloader.ui.SyncScreen

class MainActivity : ComponentActivity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        val app = application as GogApp
        setContent {
            AppTheme {
                Surface(modifier = Modifier.fillMaxSize()) {
                    var startDestination by remember { mutableStateOf<String?>(null) }
                    LaunchedEffect(Unit) {
                        startDestination = when {
                            !app.gameDatabase.isLoggedIn() -> "preflight"
                            // Logged in but nothing synced yet: let the user
                            // pick between a full update and a search.
                            app.gameDatabase.games().isEmpty() -> "syncchoice"
                            // Never sync automatically on app start; updates
                            // are triggered from Settings or the refresh button.
                            else -> "library"
                        }
                    }
                    when (val start = startDestination) {
                        null -> Box(
                            modifier = Modifier.fillMaxSize(),
                            contentAlignment = Alignment.Center,
                        ) { CircularProgressIndicator() }

                        else -> AppNavHost(app, start)
                    }
                }
            }
        }
    }
}

@Composable
private fun AppNavHost(app: GogApp, startDestination: String) {
    val navController = rememberNavController()
    val librarySyncMode by app.settings.librarySyncMode
        .collectAsState(initial = Settings.SYNC_MODE_FULL)
    NavHost(navController = navController, startDestination = startDestination) {
        composable("preflight") {
            PreflightScreen(
                onReady = {
                    navController.navigate("login") { popUpTo("preflight") { inclusive = true } }
                },
            )
        }
        composable("login") {
            LoginScreen(
                onLoggedIn = {
                    navController.navigate("syncchoice") { popUpTo("login") { inclusive = true } }
                },
            )
        }
        composable("syncchoice") {
            SyncChoiceScreen(
                onFullUpdate = {
                    navController.navigate("sync?mode=full") { popUpTo(0) { inclusive = true } }
                },
                onSearchUpdate = { query ->
                    navController.navigate("sync?mode=search&query=${Uri.encode(query)}") {
                        popUpTo(0) { inclusive = true }
                    }
                },
            )
        }
        composable(
            route = "sync?mode={mode}&query={query}",
            arguments = listOf(
                navArgument("mode") { defaultValue = "incremental" },
                navArgument("query") { defaultValue = "" },
            ),
        ) { backStackEntry ->
            val mode = when (backStackEntry.arguments?.getString("mode")) {
                "full" -> SyncMode.Full
                "search" -> SyncMode.Search(backStackEntry.arguments?.getString("query").orEmpty())
                else -> SyncMode.Incremental
            }
            SyncScreen(
                mode = mode,
                onDone = {
                    navController.navigate("library") { popUpTo(0) { inclusive = true } }
                },
            )
        }
        composable("library") {
            LibraryScreen(
                onGameClick = { rowId -> navController.navigate("game/$rowId") },
                onSettingsClick = { navController.navigate("settings") },
                onQueueClick = { navController.navigate("queue") },
                onRefresh = {
                    // Search mode: no automatic update; offer the choice again.
                    val target = if (librarySyncMode == Settings.SYNC_MODE_SEARCH) {
                        "syncchoice"
                    } else {
                        "sync?mode=incremental"
                    }
                    navController.navigate(target) { popUpTo(0) { inclusive = true } }
                },
            )
        }
        composable("game/{rowId}") { backStackEntry ->
            val rowId = backStackEntry.arguments?.getString("rowId")?.toLongOrNull() ?: return@composable
            GameDetailScreen(
                gameRowId = rowId,
                onBack = { navController.popBackStack() },
                onQueued = { navController.navigate("queue") },
            )
        }
        composable("settings") {
            SettingsScreen(
                onBack = { navController.popBackStack() },
                onFullResync = {
                    navController.navigate("sync?mode=full") { popUpTo(0) { inclusive = true } }
                },
                onIncrementalSync = {
                    navController.navigate("sync?mode=incremental") { popUpTo(0) { inclusive = true } }
                },
                onSearchSync = { query ->
                    navController.navigate("sync?mode=search&query=${Uri.encode(query)}") {
                        popUpTo(0) { inclusive = true }
                    }
                },
            )
        }
        composable("queue") {
            QueueScreen(onBack = { navController.popBackStack() })
        }
    }
}

@Composable
private fun AppTheme(content: @Composable () -> Unit) {
    MaterialTheme(
        colorScheme = if (isSystemInDarkTheme()) darkColorScheme() else lightColorScheme(),
        content = content,
    )
}
