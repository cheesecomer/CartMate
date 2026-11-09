package com.cheesecomer.cartmate.data.firestore

import android.content.Context
import android.util.Log
import androidx.core.content.edit
import com.cheesecomer.cartmate.data.functions.CallableFunctions
import com.cheesecomer.cartmate.domain.product.AiCandidates
import com.cheesecomer.cartmate.domain.product.JudgementRequest
import com.cheesecomer.cartmate.domain.product.NameJudgement
import com.cheesecomer.cartmate.domain.repository.AiDismissalRepository
import com.cheesecomer.cartmate.domain.repository.NameJudgementRepository
import com.google.firebase.firestore.DocumentSnapshot
import com.google.firebase.firestore.FirebaseFirestore
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.tasks.await
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.buildJsonArray
import kotlinx.serialization.json.buildJsonObject
import javax.inject.Inject
import javax.inject.Singleton

/** AI の名寄せの判定（docs/design/data-model.md「AI の名寄せ」）。判定は家族のメンバーが読み、関数（judgeName）だけが書く */
@OptIn(ExperimentalCoroutinesApi::class)
@Singleton
class FirestoreNameJudgementRepository @Inject constructor(
    private val session: FirestoreSession,
    private val db: FirebaseFirestore,
    private val functions: CallableFunctions,
) : NameJudgementRepository {

    override fun observeJudgements(): Flow<List<NameJudgement>> = session.scope.flatMapLatest { scope ->
        if (scope == null) flowOf(emptyList())
        else recoverListen(
            listen = { Paths.nameJudgements(db, scope.householdId).snapshotFlow().map { it.documents.mapNotNull { doc -> doc.toNameJudgement() } } },
            denied = emptyList(),
            awaitPendingWrites = { db.waitForPendingWrites().await() },
            onError = { Log.w(TAG, "listen failed", it) },
        )
    }

    override suspend fun requestJudgement(request: JudgementRequest, dictionaryVersion: String) {
        // 家族を抜けた直後などは頼まない
        val scope = session.scope.first() ?: return
        val data = buildJsonObject {
            put("householdId", JsonPrimitive(scope.householdId.value))
            put("normalizedName", JsonPrimitive(request.normalizedName))
            put("qualifiers", buildJsonObject {
                for ((kind, values) in request.qualifiers) put(kind, buildJsonArray { values.forEach { add(JsonPrimitive(it)) } })
            })
            put("dictionaryVersion", JsonPrimitive(dictionaryVersion))
        }
        try {
            functions.call(FUNCTION, data)
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            // 失敗しても候補が出ないだけで、アプリはそのまま使える。判定の失敗（上限など）は判定のドキュメントに残る
            Log.w(TAG, "judgeName failed", e)
        }
    }

    private companion object {
        const val TAG = "NameJudgements"
        const val FUNCTION = "judgeName"
    }
}

private fun DocumentSnapshot.toNameJudgement(): NameJudgement? {
    val status = when (getString("status")) {
        "pending" -> NameJudgement.Status.PENDING
        "done" -> NameJudgement.Status.DONE
        "failed" -> NameJudgement.Status.FAILED
        else -> return null
    }
    @Suppress("UNCHECKED_CAST")
    val qualifiers = (get("qualifiers") as? Map<String, Any?>).orEmpty()
        .mapValues { (_, values) -> (values as? List<*>).orEmpty().filterIsInstance<String>() }
    return NameJudgement(
        key = id,
        normalizedName = getString("normalizedName") ?: return null,
        qualifiers = qualifiers,
        status = status,
        judgeVersion = getString("judgeVersion") ?: return null,
        requestedAt = getTimestamp("requestedAt")?.toInstant(),
        judgedAt = getTimestamp("judgedAt")?.toInstant(),
        item = getString("item"),
        form = getString("form"),
        volume = getString("volume"),
        use = getString("use"),
        variant = getString("variant"),
        confidence = getDouble("confidence"),
        reason = getString("reason"),
    )
}

/**
 * 「この候補を出さない」にした組（docs/design/suggestions.md「候補にする条件」）。端末の中に、ユーザーと家族ごとに保存する。
 * 家族では共有しない（候補を見るかどうかは各自の好みで、名寄せの訂正ではないため）
 */
@OptIn(ExperimentalCoroutinesApi::class)
@Singleton
class SharedPreferencesAiDismissalRepository @Inject constructor(
    @ApplicationContext context: Context,
    private val session: FirestoreSession,
) : AiDismissalRepository {

    private val prefs by lazy { context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE) }

    /** 保存したら流し直すための合図 */
    private val changes = MutableStateFlow(0)

    override fun observeDismissed(): Flow<Set<AiCandidates.Pair>> = combine(session.scope, changes) { scope, _ ->
        if (scope == null) emptySet()
        else prefs.getStringSet(keyOf(scope), emptySet()).orEmpty().mapNotNull(AiCandidates.Pair::decode).toSet()
    }

    override suspend fun dismiss(pair: AiCandidates.Pair) {
        val scope = session.requireScope()
        val key = keyOf(scope)
        val saved = prefs.getStringSet(key, emptySet()).orEmpty()
        prefs.edit { putStringSet(key, saved + pair.encode()) }
        changes.value++
    }

    private fun keyOf(scope: FirestoreSession.Scope) = "${scope.uid}/${scope.householdId.value}"

    private companion object {
        const val PREFS_NAME = "ai_dismissals"
    }
}
