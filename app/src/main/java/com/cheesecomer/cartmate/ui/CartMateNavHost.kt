package com.cheesecomer.cartmate.ui

import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.lifecycle.Lifecycle
import androidx.navigation.NavBackStackEntry
import androidx.navigation.NavController
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController
import com.cheesecomer.cartmate.domain.input.formatQuantity
import com.cheesecomer.cartmate.domain.model.ItemId
import com.cheesecomer.cartmate.ui.itementry.ItemEntryResult
import com.cheesecomer.cartmate.ui.itementry.ItemEntryRoute
import com.cheesecomer.cartmate.ui.itementry.ItemEntryScreen
import com.cheesecomer.cartmate.ui.listdetail.ListDetailRoute
import com.cheesecomer.cartmate.ui.listdetail.ListDetailScreen
import com.cheesecomer.cartmate.ui.lists.ListsScreen
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.filterNotNull
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.onEach
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
        composable<ListDetailRoute> { backStackEntry ->
            val listId = backStackEntry.arguments?.getString(ListDetailRoute::listId.name).orEmpty()
            ListDetailScreen(
                onBack = { navController.popBackStack() },
                onOpenEntry = { name, quantity, unit -> navController.navigate(newEntryRoute(listId, name, quantity, unit)) },
                onEditItem = { navController.navigate(editEntryRoute(listId, it)) },
                entryResults = rememberEntryResults(backStackEntry),
            )
        }
        composable<ItemEntryRoute> { backStackEntry ->
            ItemEntryScreen(onClose = { navController.closeEntry(backStackEntry, it) })
        }
    }
}

fun newEntryRoute(listId: String, name: String, quantity: Double?, unit: String?) =
    ItemEntryRoute(listId, name = name, quantity = quantity?.let(::formatQuantity), unit = unit)

fun editEntryRoute(listId: String, itemId: ItemId) = ItemEntryRoute(listId, itemId = itemId.value)

/** 商品詳細入力画面の結果。リスト詳細の SavedStateHandle で受け取り、受け取ったら消す */
@Composable
fun rememberEntryResults(listDetailEntry: NavBackStackEntry): Flow<ItemEntryResult> = remember(listDetailEntry) {
    val handle = listDetailEntry.savedStateHandle
    handle.getStateFlow<String?>(ItemEntryResult.KEY, null)
        .filterNotNull()
        .onEach { handle[ItemEntryResult.KEY] = null }
        .map(ItemEntryResult::valueOf)
}

/** 商品詳細入力画面を閉じ、結果をリスト詳細に渡す */
fun NavController.closeEntry(entryBackStackEntry: NavBackStackEntry, result: ItemEntryResult) {
    // 閉じる操作が重なっても（確定と戻るキーなど）、1回だけ戻る
    if (!entryBackStackEntry.lifecycle.currentState.isAtLeast(Lifecycle.State.RESUMED)) return
    previousBackStackEntry?.savedStateHandle?.set(ItemEntryResult.KEY, result.name)
    popBackStack()
}
