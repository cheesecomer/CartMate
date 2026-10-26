package com.cheesecomer.cartmate

import android.app.Application
import dagger.hilt.android.HiltAndroidApp

@HiltAndroidApp
class CartMateApplication : Application() {
    override fun onCreate() {
        super.onCreate()
        // Firebase へのアクセスより先に登録する
        AppCheckSetup.install()
    }
}
