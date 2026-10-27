package com.cheesecomer.cartmate.ui

import android.widget.Toast
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController
import com.cheesecomer.cartmate.R
import com.cheesecomer.cartmate.domain.family.Destination
import com.cheesecomer.cartmate.ui.join.JoinCodeRoute
import com.cheesecomer.cartmate.ui.join.JoinConfirmRoute
import com.cheesecomer.cartmate.ui.onboarding.OnboardingScreen
import com.cheesecomer.cartmate.ui.signin.GoogleSignIn
import com.cheesecomer.cartmate.ui.signin.SignInScreen
import kotlinx.coroutines.launch
import kotlinx.serialization.Serializable

@Serializable
data object OnboardingRoute

/**
 * アプリの入口。ログインと所属に応じて、ログイン・家族の作成・参加・リスト一覧を切り替える。
 * 状態が変わると、それまでの画面の履歴は捨てる（ログアウトや、家族から外された場合に、前の画面へ戻れないように）
 */
@Composable
fun CartMateApp(viewModel: AppViewModel = hiltViewModel()) {
    val destination by viewModel.destination.collectAsStateWithLifecycle()
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    LaunchedEffect(viewModel) {
        viewModel.deleteFailed.collect {
            Toast.makeText(context, R.string.family_action_failed, Toast.LENGTH_LONG).show()
        }
    }
    LaunchedEffect(viewModel) {
        viewModel.mergeRejected.collect {
            Toast.makeText(context, R.string.merge_products_rejected, Toast.LENGTH_LONG).show()
        }
    }
    when (val current = destination) {
        Destination.Loading -> Box(Modifier.fillMaxSize().background(MaterialTheme.colorScheme.surface))
        Destination.Login -> SignInScreen()
        is Destination.Onboarding -> OnboardingNavHost(removed = current.removed, pending = viewModel.pendingNavigation)
        // 家族が変わったら（合流など）、画面の履歴を作り直す
        is Destination.Lists -> key(current.householdId) {
            CartMateNavHost(
                pending = viewModel.pendingNavigation,
                onDeleteAccount = {
                    // 削除の前に Google で再ログインする（docs/design/security.md「アカウントの削除」）
                    scope.launch {
                        when (val result = GoogleSignIn.requestIdToken(context)) {
                            is GoogleSignIn.Result.Success -> {
                                viewModel.deleteAccount(result.idToken)
                                GoogleSignIn.clear(context)
                            }
                            GoogleSignIn.Result.Cancelled -> Unit
                            is GoogleSignIn.Result.Failed ->
                                Toast.makeText(context, R.string.family_action_failed, Toast.LENGTH_LONG).show()
                        }
                    }
                },
            )
        }
    }
}

/** 家族に参加していないときの画面。招待リンクから開いた場合は、参加の確認を開く */
@Composable
private fun OnboardingNavHost(removed: Boolean, pending: PendingNavigation) {
    val navController = rememberNavController()
    val joinCode by pending.joinCode.collectAsStateWithLifecycle()
    LaunchedEffect(joinCode) {
        pending.consumeJoin()?.let { navController.navigate(JoinConfirmRoute(it)) }
    }
    NavHost(navController = navController, startDestination = OnboardingRoute) {
        composable<OnboardingRoute> {
            OnboardingScreen(removed = removed, onJoinWithCode = { navController.navigate(JoinCodeRoute) })
        }
        joinGraph(navController)
    }
}
