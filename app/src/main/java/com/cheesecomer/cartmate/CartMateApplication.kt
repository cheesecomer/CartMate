package com.cheesecomer.cartmate

import android.app.Application
import com.cheesecomer.cartmate.domain.repository.DictionaryProvider
import dagger.hilt.android.HiltAndroidApp
import javax.inject.Inject

@HiltAndroidApp
class CartMateApplication : Application() {

    /** 同梱の名寄せ辞書。起動時に作り、別スレッドで読み込み始める（使う画面を開いたときに読み終わっているように） */
    @Inject
    lateinit var dictionaryProvider: DictionaryProvider

    override fun onCreate() {
        super.onCreate()
        // Firebase へのアクセスより先に登録する
        AppCheckSetup.install()
        FirebaseEmulator.install()
    }
}
