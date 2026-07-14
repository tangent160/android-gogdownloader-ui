package io.github.tangent160.gogdownloader

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
import io.github.tangent160.gogdownloader.ui.GameDetailScreen
import io.github.tangent160.gogdownloader.ui.LibraryScreen
import io.github.tangent160.gogdownloader.ui.LoginScreen
import io.github.tangent160.gogdownloader.ui.PreflightScreen
import io.github.tangent160.gogdownloader.ui.QueueScreen
import io.github.tangent160.gogdownloader.ui.SettingsScreen
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
                        startDestination = if (app.gameDatabase.isLoggedIn()) "sync" else "preflight"
                    }
                    when (val start = startDestination) {
                        null -> Box(
                            modifier = Modifier.fillMaxSize(),
                            contentAlignment = Alignment.Center,
                        ) { CircularProgressIndicator() }

                        else -> AppNavHost(start)
                    }
                }
            }
        }
    }
}

@Composable
private fun AppNavHost(startDestination: String) {
    val navController = rememberNavController()
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
                    navController.navigate("sync") { popUpTo("login") { inclusive = true } }
                },
            )
        }
        composable("sync") {
            SyncScreen(
                onDone = {
                    navController.navigate("library") { popUpTo("sync") { inclusive = true } }
                },
            )
        }
        composable("library") {
            LibraryScreen(
                onGameClick = { rowId -> navController.navigate("game/$rowId") },
                onSettingsClick = { navController.navigate("settings") },
                onQueueClick = { navController.navigate("queue") },
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
            SettingsScreen(onBack = { navController.popBackStack() })
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
