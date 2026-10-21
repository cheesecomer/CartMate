package com.cheesecomer.cartmate.ui

import androidx.compose.runtime.Composable
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController
import com.cheesecomer.cartmate.ui.listdetail.ListDetailRoute
import com.cheesecomer.cartmate.ui.listdetail.ListDetailScreen
import com.cheesecomer.cartmate.ui.lists.ListsScreen
import kotlinx.serialization.Serializable

@Serializable
data object ListsRoute

@Composable
fun CartMateNavHost() {
    val navController = rememberNavController()
    NavHost(navController = navController, startDestination = ListsRoute) {
        composable<ListsRoute> {
            ListsScreen(onOpenList = { navController.navigate(ListDetailRoute(it.value)) })
        }
        composable<ListDetailRoute> {
            ListDetailScreen(onBack = { navController.popBackStack() })
        }
    }
}
