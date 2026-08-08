package com.agro.interviewer.presentation.navigation

import androidx.compose.runtime.Composable
import androidx.navigation.NavType
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController
import androidx.navigation.navArgument
import com.agro.interviewer.presentation.config.InterviewConfigScreen
import com.agro.interviewer.presentation.home.HomeScreen
import com.agro.interviewer.presentation.results.ResultsScreen
import com.agro.interviewer.presentation.voice.VoiceScreen

sealed class Screen(val route: String) {
    data object Home : Screen("home")
    data object Config : Screen("config")
    data object Voice : Screen("voice/{sessionId}") {
        fun createRoute(sessionId: String) = "voice/$sessionId"
    }
    data object Results : Screen("results/{sessionId}") {
        fun createRoute(sessionId: String) = "results/$sessionId"
    }
}

@Composable
fun AppNavigation() {
    val navController = rememberNavController()

    NavHost(
        navController = navController,
        startDestination = Screen.Home.route
    ) {
        composable(Screen.Home.route) {
            HomeScreen(
                onStartNewInterview = { navController.navigate(Screen.Config.route) },
                onViewSession = { sessionId ->
                    navController.navigate(Screen.Results.createRoute(sessionId))
                }
            )
        }

        composable(Screen.Config.route) {
            InterviewConfigScreen(
                onStartInterview = { sessionId ->
                    navController.navigate(Screen.Voice.createRoute(sessionId)) {
                        popUpTo(Screen.Config.route) { inclusive = true }
                    }
                },
                onBack = { navController.popBackStack() }
            )
        }

        composable(
            route = Screen.Voice.route,
            arguments = listOf(navArgument("sessionId") { type = NavType.StringType })
        ) { backStackEntry ->
            val sessionId = backStackEntry.arguments?.getString("sessionId") ?: ""
            VoiceScreen(
                sessionId = sessionId,
                onSessionComplete = { completedSessionId ->
                    navController.navigate(Screen.Results.createRoute(completedSessionId)) {
                        popUpTo(Screen.Home.route)
                    }
                },
                onBack = {
                    navController.navigate(Screen.Home.route) {
                        popUpTo(Screen.Home.route) { inclusive = true }
                    }
                }
            )
        }

        composable(
            route = Screen.Results.route,
            arguments = listOf(navArgument("sessionId") { type = NavType.StringType })
        ) { backStackEntry ->
            val sessionId = backStackEntry.arguments?.getString("sessionId") ?: ""
            ResultsScreen(
                sessionId = sessionId,
                onBack = { navController.popBackStack() },
                onStartNewInterview = {
                    navController.navigate(Screen.Config.route) {
                        popUpTo(Screen.Home.route)
                    }
                }
            )
        }
    }
}
