package com.cheesecomer.cartmate

import android.util.Log
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.firestore.FirebaseFirestore
import kotlinx.coroutines.tasks.await
import java.time.Clock
import java.time.Instant
import java.time.ZoneId

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

    /**
     * アプリの「今」を固定する。null なら端末の時計。スクリーンショットで、テストデータ（seed.py --now）と同じ日時にして、
     * 撮る日で画像が変わらないようにする（ScreenshotTour）
     */
    @Volatile
    var fixedNow: Instant? = null

    /**
     * アプリが使う時計（DataModule）。アプリの起動時に作られた部品にも効くよう、時刻を聞かれるたびに [fixedNow] を見る
     */
    val clock: Clock = object : Clock() {
        private val system = systemDefaultZone()
        override fun getZone(): ZoneId = system.zone
        override fun withZone(zone: ZoneId): Clock = fixedNow?.let { fixed(it, zone) } ?: system.withZone(zone)
        override fun instant(): Instant = fixedNow ?: system.instant()
    }

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
