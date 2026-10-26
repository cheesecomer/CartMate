package com.cheesecomer.cartmate

import android.content.Intent
import android.graphics.Color
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.SystemBarStyle
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import com.cheesecomer.cartmate.ui.CartMateApp
import com.cheesecomer.cartmate.ui.PendingNavigation
import com.cheesecomer.cartmate.ui.join.JoinLinks
import com.cheesecomer.cartmate.ui.theme.CartMateTheme
import dagger.hilt.android.AndroidEntryPoint
import javax.inject.Inject

@AndroidEntryPoint
class MainActivity : ComponentActivity() {

    @Inject
    lateinit var pendingNavigation: PendingNavigation

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        // 画面を作り直したとき（回転など）に、同じ招待リンクをもう一度開かないように、初回だけ受け取る
        if (savedInstanceState == null) receiveJoinLink(intent)
        // 背景は常に明るい色なので、端末がダークモードでもステータスバー・ナビゲーションバーのアイコンは濃い色にする
        enableEdgeToEdge(
            statusBarStyle = SystemBarStyle.light(Color.TRANSPARENT, Color.TRANSPARENT),
            navigationBarStyle = SystemBarStyle.light(Color.TRANSPARENT, Color.TRANSPARENT),
        )
        setContent {
            CartMateTheme {
                CartMateApp()
            }
        }
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        receiveJoinLink(intent)
    }

    /** 招待リンクから開いた場合は、ログインのあとで参加の確認を開く */
    private fun receiveJoinLink(intent: Intent) {
        intent.data?.let(JoinLinks::codeOf)?.let(pendingNavigation::requestJoin)
    }
}
