package com.cheesecomer.cartmate.data.firestore

import androidx.test.platform.app.InstrumentationRegistry
import com.cheesecomer.cartmate.domain.model.Account
import com.cheesecomer.cartmate.domain.model.HouseholdId
import com.cheesecomer.cartmate.domain.model.UserId
import com.google.firebase.FirebaseApp
import com.google.firebase.FirebaseOptions
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.firestore.FirebaseFirestore
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.tasks.await
import kotlinx.coroutines.withTimeout
import org.junit.Assume.assumeTrue
import java.time.Clock

/**
 * Firebase のエミュレータにつなぐ、1台の端末に見立てたクライアント（docs/testing.md「同時実行」）。
 * 端末ごとに名前の違う FirebaseApp を使うので、キャッシュと書き込みのキューも端末ごとに分かれる。
 * リポジトリはアプリと同じ実装を使う
 */
class EmulatorClient private constructor(
    private val app: FirebaseApp,
    val auth: FirebaseAuth,
    val displayName: String,
) {
    lateinit var db: FirebaseFirestore
        private set
    lateinit var session: FirestoreSession
        private set
    lateinit var store: FirestoreShoppingStore
        private set
    lateinit var households: FirestoreHouseholdRepository
        private set
    lateinit var products: FirestoreProductRepository
        private set

    val uid: String get() = checkNotNull(auth.currentUser).uid
    val account: Account get() = Account(UserId(uid), displayName, "$displayName@example.com", photoUrl = null)

    /** Firestore とリポジトリを作り直す。[restart] で、アプリのプロセスが終わって起動し直したのと同じ状態にする */
    private fun connect() {
        db = FirebaseFirestore.getInstance(app).apply { useEmulator(checkNotNull(host), FIRESTORE_PORT) }
        session = FirestoreSession(auth, db)
        store = FirestoreShoppingStore(session, db, Clock.systemUTC())
        households = FirestoreHouseholdRepository(session, db, FamilyMerger(context, db), Clock.systemUTC())
        products = FirestoreProductRepository(context, session, db)
    }

    /**
     * 所属する家族が [householdId] になるまで待つ。家族の作成や参加の直後は、所属の購読がまだ前の値を返すことがある
     * （アプリでは、作成や参加の直後に画面が切り替わるのを待ってから次の操作をする）
     */
    suspend fun awaitHousehold(householdId: HouseholdId) {
        withTimeout(10_000) { session.householdId.first { it == householdId } }
    }

    /**
     * Firestore を終了して作り直す。書き込みのキューは端末に残っているので、つながると送られる。
     * [offline] なら、つながる前にオフラインにする（起動し直した直後に送られないように）
     */
    suspend fun restart(offline: Boolean = false) {
        db.terminate().await()
        connect()
        if (offline) offline()
    }

    suspend fun offline() {
        db.disableNetwork().await()
    }

    suspend fun online() {
        db.enableNetwork().await()
    }

    /**
     * Firestore を終了して端末のキャッシュを消す。FirebaseApp は消さない（消すと、Auth が内部に持っている
     * アプリへの参照が残り、次のテストのログインが失敗する）。名前はテストごとに違うので、次のテストとは混ざらない
     */
    suspend fun close() {
        db.terminate().await()
        db.clearPersistence().await()
        auth.signOut()
    }

    companion object {
        private const val PROJECT_ID = "demo-cartmate"
        private const val FIRESTORE_PORT = 8085
        private const val AUTH_PORT = 9099

        private val context get() = InstrumentationRegistry.getInstrumentation().targetContext
        /** 端末から見たエミュレータのホスト。-PemulatorTests を付けたときだけ渡される（app/build.gradle.kts） */
        private val host: String?
            get() = InstrumentationRegistry.getArguments().getString("emulatorHost")

        /** エミュレータを使うテストは、-PemulatorTests を付けたときだけ流す。通常の実行では飛ばす */
        fun assumeRequested() {
            assumeTrue(host != null)
        }

        /** エミュレータに新しい匿名のユーザーでログインしたクライアント */
        suspend fun signIn(displayName: String): EmulatorClient {
            val options = FirebaseOptions.Builder()
                .setProjectId(PROJECT_ID)
                .setApplicationId(FirebaseApp.getInstance().options.applicationId)
                .setApiKey("demo-api-key")
                .build()
            val app = FirebaseApp.initializeApp(context, options, "$displayName-${System.nanoTime()}")
            val auth = FirebaseAuth.getInstance(app).apply { useEmulator(checkNotNull(host), AUTH_PORT) }
            auth.signInAnonymously().await()
            return EmulatorClient(app, auth, displayName).apply { connect() }
        }
    }
}
