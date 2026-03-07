package com.example.audiosummeryapp

import android.content.pm.PackageManager
import android.os.Build
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.material3.MaterialTheme
import androidx.core.content.ContextCompat
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController
import com.example.audiosummeryapp.ui.dashboard.DashboardScreen
import com.example.audiosummeryapp.ui.theme.AudioSummeryAppTheme
import dagger.hilt.android.AndroidEntryPoint
import android.Manifest
import androidx.navigation.NavType
import androidx.navigation.navArgument
import com.example.audiosummeryapp.ui.session.SessionDetailScreen

@AndroidEntryPoint
class MainActivity : ComponentActivity() {

    private val permissionsToRequest = buildList {
        add(Manifest.permission.RECORD_AUDIO)
        add(Manifest.permission.READ_PHONE_STATE)
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            add(Manifest.permission.POST_NOTIFICATIONS)
        }
    }.toTypedArray()

    private val permissionLauncher = registerForActivityResult(
        ActivityResultContracts.RequestMultiplePermissions()
    ) { /* permissions granted/denied — UI handles gracefully */ }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()

        // Request permissions on first launch
        requestMissingPermissions()

        setContent {
            AudioSummeryAppTheme {
                MaterialTheme {
                    val navController = rememberNavController()
                    NavHost(
                        navController    = navController,
                        startDestination = "dashboard"
                    ) {
                        composable("dashboard") {
                            DashboardScreen(
                                onSessionClick      = { session ->
                                    navController.navigate("detail/${session.id}") },
                                onNewRecordingClick = {
                                    navController.navigate("recording")
                                }
                            )
                        }
                        composable("recording") {
                            RecordingScreen(
                                onNavigateToDashboard = {
                                    navController.popBackStack()
                                }
                            )
                        }
                        composable(
                            route     = "detail/{sessionId}",
                            arguments = listOf(navArgument("sessionId") { type = NavType.StringType })
                        ) {
                            SessionDetailScreen(
                                onBack = { navController.popBackStack() }
                            )
                        }
                    }
                }
            }
        }
    }

    private fun requestMissingPermissions() {
        val missing = permissionsToRequest.filter {
            ContextCompat.checkSelfPermission(this, it) != PackageManager.PERMISSION_GRANTED
        }
        if (missing.isNotEmpty()) {
            permissionLauncher.launch(missing.toTypedArray())
        }
    }
}