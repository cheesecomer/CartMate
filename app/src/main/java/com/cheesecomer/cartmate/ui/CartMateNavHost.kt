package com.cheesecomer.cartmate.ui

import androidx.compose.runtime.Composable
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController
import com.cheesecomer.cartmate.ui.lists.ListsScreen
import kotlinx.serialization.Serializable

@Serializable
data object ListsRoute

@Composable
fun CartMateNavHost() {
    val navController = rememberNavController()
    NavHost(navController = navController, startDestination = ListsRoute) {
        composable<ListsRoute> {
            ListsScreen()
        }
    }
}
