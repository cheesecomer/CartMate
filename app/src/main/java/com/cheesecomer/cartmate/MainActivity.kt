package com.cheesecomer.cartmate

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import com.cheesecomer.cartmate.ui.CartMateNavHost
import com.cheesecomer.cartmate.ui.theme.CartMateTheme
import dagger.hilt.android.AndroidEntryPoint

@AndroidEntryPoint
class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContent {
            CartMateTheme {
                CartMateNavHost()
            }
        }
    }
}
