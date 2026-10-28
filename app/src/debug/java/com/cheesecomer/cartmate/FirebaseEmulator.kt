package com.cheesecomer.cartmate

import android.util.Log
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.firestore.FirebaseFirestore
import kotlinx.coroutines.tasks.await

/**
 * デバッグ版を Firebase のエミュレータ（Auth と Firestore）につなぐ。`-PfirebaseEmulator` を付けてビルドしたときだけ。
 * テストデータ（tools/testdata/seed.py）で予測の画面を確かめるために使う（docs/testing.md「テストデータ」）
 */
object FirebaseEmulator {
    private const val AUTH_PORT = 9099
    private const val FIRESTORE_PORT = 8085

    /** tools/testdata/seed.py が作るユーザー */
    private const val TEST_EMAIL = "taro@example.com"
    private const val TEST_PASSWORD = "cartmate-test"

    val enabled: Boolean get() = BuildConfig.FIREBASE_EMULATOR_HOST.isNotEmpty()

    /** ログイン画面に、テスト用ユーザーでログインするボタンを出すか。スクリーンショットでは隠す（ScreenshotTour） */
    var testUserSignInVisible = true

    /** Firebase へのアクセスより先に呼ぶ */
    fun install() {
        if (!enabled) return
        val host = BuildConfig.FIREBASE_EMULATOR_HOST
        FirebaseAuth.getInstance().useEmulator(host, AUTH_PORT)
        FirebaseFirestore.getInstance().useEmulator(host, FIRESTORE_PORT)
    }

    /** エミュレータのテスト用ユーザーでログインする（Google のログインはエミュレータでは使えないため） */
    suspend fun signInTestUser() {
        runCatching { FirebaseAuth.getInstance().signInWithEmailAndPassword(TEST_EMAIL, TEST_PASSWORD).await() }
            .onFailure { Log.w(TAG, "テスト用ユーザーでログインできない（tools/testdata/seed.py を実行したか）", it) }
    }

    private const val TAG = "FirebaseEmulator"
}
