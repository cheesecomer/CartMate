package com.cheesecomer.cartmate.ui

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.collectAsState
import androidx.compose.ui.Modifier
import androidx.compose.ui.input.pointer.PointerEventPass
import androidx.compose.ui.input.pointer.pointerInput
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
import com.cheesecomer.cartmate.ui.products.PurchaseHistoryRoute
import com.cheesecomer.cartmate.ui.products.PurchaseHistoryScreen

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
        // 遷移の操作は、その画面が操作中（RESUMED）のときだけ行う。遷移の途中に重なった操作で、2回戻ったり2回開いたりしない
        screen<ListsRoute> { entry ->
            ListsScreen(
                onOpenList = { entry.whenResumed { navController.navigate(ListDetailRoute(it.value)) } },
                onOpenFamily = { entry.whenResumed { navController.navigate(FamilyRoute) } },
            )
        }
        screen<FamilyRoute> { entry ->
            FamilyScreen(
                onBack = { entry.whenResumed { navController.popBackStack() } },
                onJoinWithCode = { entry.whenResumed { navController.navigate(JoinCodeRoute) } },
                onDeleteAccount = onDeleteAccount,
                onOpenProductMerge = { entry.whenResumed { navController.navigate(ProductNamesRoute) } },
            )
        }
        screen<ProductNamesRoute> { entry ->
            ProductNamesScreen(
                onBack = { entry.whenResumed { navController.popBackStack() } },
                onOpen = { entry.whenResumed { navController.navigate(ProductDetailRoute(it.value)) } },
            )
        }
        screen<ProductDetailRoute> { entry ->
            ProductDetailScreen(
                onBack = { entry.whenResumed { navController.popBackStack() } },
                onMerge = { entry.whenResumed { navController.navigate(MergeProductsRoute(it.value)) } },
                onOpen = { entry.whenResumed { navController.navigate(ProductDetailRoute(it.value)) } },
                onOpenHistory = { entry.whenResumed { navController.navigate(PurchaseHistoryRoute(it.value)) } },
                // 訂正した名前の商品の画面に置き換える（戻ると一覧に戻る）
                onRenamed = { target ->
                    entry.whenResumed {
                        navController.navigate(ProductDetailRoute(target.value)) {
                            popUpTo(entry.destination.id) { inclusive = true }
                        }
                    }
                },
            )
        }
        screen<PurchaseHistoryRoute> { entry ->
            PurchaseHistoryScreen(
                onBack = { entry.whenResumed { navController.popBackStack() } },
                // 最後の購入を削除したら、商品の画面を経由せず商品名の管理まで戻る。リスト詳細のサジェストから開いた場合は
                // 商品名の管理がないので、商品の画面を開く前の画面（リスト詳細）まで戻る
                onEmptied = {
                    entry.whenResumed {
                        if (!navController.popBackStack<ProductNamesRoute>(inclusive = false)) {
                            navController.popBackStack<ProductDetailRoute>(inclusive = true)
                        }
                    }
                },
            )
        }
        screen<MergeProductsRoute> { entry ->
            MergeProductsScreen(
                onBack = { entry.whenResumed { navController.popBackStack() } },
                // まとめたら統合先の画面に戻る。完了と戻るキーが重なっても、1回だけ戻る
                onDone = { entry.whenResumed { navController.popBackStack() } },
            )
        }
        joinGraph(navController)
        screen<ListDetailRoute> { entry ->
            val listId = entry.arguments?.getString(ListDetailRoute::listId.name).orEmpty()
            ListDetailScreen(
                onBack = { entry.whenResumed { navController.popBackStack() } },
                onOpenEntry = { name, quantity, unit -> entry.whenResumed { navController.navigate(newEntryRoute(listId, name, quantity, unit)) } },
                onEditItem = { entry.whenResumed { navController.navigate(editEntryRoute(listId, it)) } },
                entryResults = rememberEntryResults(entry),
                onOpenProduct = { entry.whenResumed { navController.navigate(ProductDetailRoute(it.value)) } },
            )
        }
        screen<ItemEntryRoute> { entry ->
            ItemEntryScreen(onClose = { navController.closeEntry(entry, it) })
        }
    }
}

/** 招待コードの入力と参加の確認。家族の作成・参加の画面からも、自分しかいない家族の画面（合流）からも開く */
fun NavGraphBuilder.joinGraph(navController: NavController) {
    screen<JoinCodeRoute> { entry ->
        JoinCodeScreen(
            onBack = { entry.whenResumed { navController.popBackStack() } },
            onFound = { entry.whenResumed { navController.navigate(JoinConfirmRoute(it.value)) } },
        )
    }
    screen<JoinConfirmRoute> { entry ->
        JoinConfirmScreen(onClose = { entry.whenResumed { navController.popBackStack() } })
    }
}

/**
 * 画面の行き先。遷移のアニメーション（約0.7秒のクロスフェード）の間は、離れようとしている画面もまだ表示されていて、
 * 戻るときはそちらが手前にあるので、タップを受けてしまう（戻った直後の一覧へのタップで項目がチェックされる、
 * 一覧のタイトルの位置のタップでもう一度戻って白紙になる。フィードバック #015・#017）。
 * そこで、操作中（RESUMED）でない画面は入力を受けない。遷移の間のタップは、どちらの画面にも届かない
 */
inline fun <reified T : Any> NavGraphBuilder.screen(noinline content: @Composable (NavBackStackEntry) -> Unit) {
    composable<T> { entry -> InputOnlyWhenResumed(entry) { content(entry) } }
}

@Composable
fun InputOnlyWhenResumed(entry: NavBackStackEntry, content: @Composable () -> Unit) {
    // この画面自身が止まっても状態の変化を受け取れるよう、ライフサイクルに合わせて止めずに見る
    val state by entry.lifecycle.currentStateFlow.collectAsState()
    Box(Modifier.fillMaxSize()) {
        content()
        if (!state.isAtLeast(Lifecycle.State.RESUMED)) {
            // 手前に重ねて、すべてのタップを受け止める
            Box(
                Modifier
                    .matchParentSize()
                    .pointerInput(Unit) {
                        awaitPointerEventScope {
                            while (true) awaitPointerEvent(PointerEventPass.Initial).changes.forEach { it.consume() }
                        }
                    },
            )
        }
    }
}

/** [entry] が操作中（RESUMED）のときだけ [block] を行う。戻るキーと戻るボタンが重なったときなどに、2回戻らないように */
inline fun NavBackStackEntry.whenResumed(block: () -> Unit) {
    if (lifecycle.currentState.isAtLeast(Lifecycle.State.RESUMED)) block()
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
