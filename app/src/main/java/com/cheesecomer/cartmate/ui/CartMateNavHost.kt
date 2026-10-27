package com.cheesecomer.cartmate.ui

import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.lifecycle.Lifecycle
import androidx.navigation.NavBackStackEntry
import androidx.navigation.NavController
import androidx.navigation.NavGraphBuilder
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController
import com.cheesecomer.cartmate.domain.input.formatQuantity
import com.cheesecomer.cartmate.domain.model.ItemId
import com.cheesecomer.cartmate.ui.family.FamilyRoute
import com.cheesecomer.cartmate.ui.family.FamilyScreen
import com.cheesecomer.cartmate.ui.itementry.ItemEntryResult
import com.cheesecomer.cartmate.ui.itementry.ItemEntryRoute
import com.cheesecomer.cartmate.ui.itementry.ItemEntryScreen
import com.cheesecomer.cartmate.ui.join.JoinCodeRoute
import com.cheesecomer.cartmate.ui.join.JoinCodeScreen
import com.cheesecomer.cartmate.ui.join.JoinConfirmRoute
import com.cheesecomer.cartmate.ui.join.JoinConfirmScreen
import com.cheesecomer.cartmate.ui.listdetail.ListDetailRoute
import com.cheesecomer.cartmate.ui.listdetail.ListDetailScreen
import com.cheesecomer.cartmate.ui.lists.ListsScreen
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.filterNotNull
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.onEach
import kotlinx.serialization.Serializable
import com.cheesecomer.cartmate.ui.products.MergeProductsRoute
import com.cheesecomer.cartmate.ui.products.MergeProductsScreen
import com.cheesecomer.cartmate.ui.products.ProductDetailRoute
import com.cheesecomer.cartmate.ui.products.ProductDetailScreen
import com.cheesecomer.cartmate.ui.products.ProductNamesRoute
import com.cheesecomer.cartmate.ui.products.ProductNamesScreen

@Serializable
data object ListsRoute

/** 家族に参加しているときの画面。[pending] の予約（家族を作成した直後、招待リンク）があれば、その画面を開く */
@Composable
fun CartMateNavHost(pending: PendingNavigation, onDeleteAccount: () -> Unit) {
    val navController = rememberNavController()
    val joinCode by pending.joinCode.collectAsStateWithLifecycle()
    LaunchedEffect(Unit) {
        if (pending.consumeFamilyScreen()) navController.navigate(FamilyRoute)
    }
    LaunchedEffect(joinCode) {
        pending.consumeJoin()?.let { navController.navigate(JoinConfirmRoute(it)) }
    }
    NavHost(navController = navController, startDestination = ListsRoute) {
        composable<ListsRoute> {
            ListsScreen(
                onOpenList = { navController.navigate(ListDetailRoute(it.value)) },
                onOpenFamily = { navController.navigate(FamilyRoute) },
            )
        }
        composable<FamilyRoute> {
            FamilyScreen(
                onBack = { navController.popBackStack() },
                onJoinWithCode = { navController.navigate(JoinCodeRoute) },
                onDeleteAccount = onDeleteAccount,
                onOpenProductMerge = { navController.navigate(ProductNamesRoute) },
            )
        }
        composable<ProductNamesRoute> {
            ProductNamesScreen(
                onBack = { navController.popBackStack() },
                onOpen = { navController.navigate(ProductDetailRoute(it.value)) },
            )
        }
        composable<ProductDetailRoute> { backStackEntry ->
            ProductDetailScreen(
                onBack = { navController.popBackStack() },
                onMerge = { navController.navigate(MergeProductsRoute(it.value)) },
                onOpen = { navController.navigate(ProductDetailRoute(it.value)) },
                // 訂正した名前の商品の画面に置き換える（戻ると一覧に戻る）
                onRenamed = { target ->
                    if (backStackEntry.lifecycle.currentState.isAtLeast(Lifecycle.State.RESUMED)) {
                        navController.navigate(ProductDetailRoute(target.value)) {
                            popUpTo(backStackEntry.destination.id) { inclusive = true }
                        }
                    }
                },
            )
        }
        composable<MergeProductsRoute> { backStackEntry ->
            MergeProductsScreen(
                onBack = { navController.popBackStack() },
                // まとめたら統合先の画面に戻る。完了と戻るキーが重なっても、1回だけ戻る
                onDone = {
                    if (backStackEntry.lifecycle.currentState.isAtLeast(Lifecycle.State.RESUMED)) navController.popBackStack()
                },
            )
        }
        joinGraph(navController)
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

/** 招待コードの入力と参加の確認。家族の作成・参加の画面からも、自分しかいない家族の画面（合流）からも開く */
fun NavGraphBuilder.joinGraph(navController: NavController) {
    composable<JoinCodeRoute> {
        JoinCodeScreen(
            onBack = { navController.popBackStack() },
            onFound = { navController.navigate(JoinConfirmRoute(it.value)) },
        )
    }
    composable<JoinConfirmRoute> {
        JoinConfirmScreen(onClose = { navController.popBackStack() })
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
