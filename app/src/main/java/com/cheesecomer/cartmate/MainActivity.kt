package com.cheesecomer.cartmate

import android.graphics.Color
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.SystemBarStyle
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import com.cheesecomer.cartmate.ui.CartMateNavHost
import com.cheesecomer.cartmate.ui.theme.CartMateTheme
import dagger.hilt.android.AndroidEntryPoint

@AndroidEntryPoint
class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        // 背景は常に白なので、端末がダークモードでもステータスバー・ナビゲーションバーのアイコンは濃い色にする
        enableEdgeToEdge(
            statusBarStyle = SystemBarStyle.light(Color.TRANSPARENT, Color.TRANSPARENT),
            navigationBarStyle = SystemBarStyle.light(Color.TRANSPARENT, Color.TRANSPARENT),
        )
        setContent {
            CartMateTheme {
                CartMateNavHost()
            }
        }
    }
}
