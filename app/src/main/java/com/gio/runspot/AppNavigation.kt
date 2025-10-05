package com.gio.runspot

import androidx.compose.runtime.*
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController
import com.google.firebase.firestore.FirebaseFirestore

object Routes {
    const val LOGIN_SCREEN = "LoginScreen"
    const val REGISTRATION_SCREEN = "RegistrationScreen"
    const val MAIN_SCREEN = "MainScreen"
    const val RANKING_SCREEN = "RankingScreen"
    const val PROFILE_SCREEN = "ProfileScreen"
}

@Composable
fun AppNavigation(startDestination: String,routeIdToShow:String?) {
    val navController = rememberNavController()
    var selectedRoute by remember { mutableStateOf<Route?>(null) }

    LaunchedEffect(routeIdToShow) {
        if (routeIdToShow != null) {
            // Ako smo dobili ID, preuzmi tu rutu iz baze i postavi je kao selektovanu
            val db = FirebaseFirestore.getInstance()
            db.collection("routes").document(routeIdToShow).get()
                .addOnSuccessListener { document ->
                    val route = document.toObject(Route::class.java)?.copy(id = document.id)
                    if (route != null) {
                        selectedRoute = route
                    }
                }
        }
    }

    NavHost(navController = navController, startDestination = startDestination) {

        composable(Routes.LOGIN_SCREEN) {
            LoginScreen(
                onLoginSuccess = {
                    navController.navigate(Routes.MAIN_SCREEN) {
                        popUpTo(Routes.LOGIN_SCREEN) { inclusive = true }
                    }
                },
                onNavigateToRegister = {
                    navController.navigate(Routes.REGISTRATION_SCREEN)
                }
            )
        }

        composable(Routes.REGISTRATION_SCREEN) {
            RegistrationScreen(
                onRegistrationSuccess = {
                    navController.navigate(Routes.MAIN_SCREEN) {
                        popUpTo(Routes.LOGIN_SCREEN) { inclusive = true }
                    }
                }
            )
        }

        composable(Routes.MAIN_SCREEN) {
            MainScreen(
                navController = navController,
                selectedRoute = selectedRoute,
                onRouteSelected = { route -> selectedRoute = route },
                onNavigateToRanking = { navController.navigate(Routes.RANKING_SCREEN) }
            )
        }

        composable(Routes.RANKING_SCREEN) {
            RankingScreen(
                onNavigateBack = { navController.popBackStack() }
            )
        }

        composable(Routes.PROFILE_SCREEN) {
            ProfileScreen(
                onNavigateBack = { navController.popBackStack() },
                onLogout = {
                    navController.navigate(Routes.LOGIN_SCREEN) {
                        popUpTo(navController.graph.id) {
                            inclusive = true
                        }
                    }
                }
            )
        }
    }
}