package com.cheesecomer.cartmate.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.ui.Modifier
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.cheesecomer.cartmate.domain.family.Destination
import com.cheesecomer.cartmate.ui.onboarding.OnboardingScreen
import com.cheesecomer.cartmate.ui.signin.SignInScreen

/**
 * アプリの入口。ログインと所属に応じて、ログイン・家族の作成・参加・リスト一覧を切り替える。
 * 状態が変わると、それまでの画面の履歴は捨てる（ログアウトや、家族から外された場合に、前の画面へ戻れないように）
 */
@Composable
fun CartMateApp(viewModel: AppViewModel = hiltViewModel()) {
    val destination by viewModel.destination.collectAsStateWithLifecycle()
    when (val current = destination) {
        Destination.Loading -> Box(Modifier.fillMaxSize().background(MaterialTheme.colorScheme.surface))
        Destination.Login -> SignInScreen()
        is Destination.Onboarding -> OnboardingScreen(removed = current.removed, onJoinWithCode = {})
        // 家族が変わったら（合流など）、画面の履歴を作り直す
        is Destination.Lists -> key(current.householdId) { CartMateNavHost() }
    }
}
