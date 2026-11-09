package com.cheesecomer.cartmate.data.functions

import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import java.io.IOException
import java.net.HttpURLConnection
import java.net.URL

/**
 * 呼び出し可能な関数（onCall）を HTTPS で呼ぶ。ログインの ID トークンと App Check のトークンを付ける。
 *
 * Functions の SDK を使わないのは、SDK が firebase-iid を必須の依存にしていて、Firebase Installations（端末の ID）を
 * 持ち込むため（データセーフティの申告を変えないため。docs/release.md「データセーフティ」）。
 * 呼び出しの形（POST の {"data": ...}、応答の {"result": ...} か {"error": {"status": ...}}）は、呼び出し可能な関数の約束事のとおり
 */
class CallableFunctions(
    /** 関数の URL の手前。`https://asia-northeast1-<プロジェクト>.cloudfunctions.net` */
    private val baseUrl: String,
    /** ログインの ID トークン。ログインしていなければ null */
    private val idToken: suspend () -> String?,
    /** App Check のトークン。取れなければ null（関数が拒否する） */
    private val appCheckToken: suspend () -> String?,
    private val dispatcher: CoroutineDispatcher = Dispatchers.IO,
) {
    /** 関数が返したエラー。[status] は FAILED_PRECONDITION や UNAUTHENTICATED などの名前 */
    class CallableException(val httpStatus: Int, val status: String?, message: String?) :
        IOException("$httpStatus $status: $message")

    /** 関数 [name] を [data] で呼び、関数の戻り値を返す。通信できなければ IOException、関数がエラーを返せば [CallableException] */
    suspend fun call(name: String, data: JsonObject): JsonElement? {
        val id = idToken()
        val appCheck = appCheckToken()
        return withContext(dispatcher) {
            val connection = URL("$baseUrl/$name").openConnection() as HttpURLConnection
            try {
                connection.requestMethod = "POST"
                connection.connectTimeout = TIMEOUT_MILLIS
                connection.readTimeout = TIMEOUT_MILLIS
                connection.doOutput = true
                connection.setRequestProperty("Content-Type", "application/json; charset=utf-8")
                id?.let { connection.setRequestProperty("Authorization", "Bearer $it") }
                appCheck?.let { connection.setRequestProperty("X-Firebase-AppCheck", it) }
                val body = buildJsonObject { put("data", data) }.toString()
                connection.outputStream.use { it.write(body.toByteArray(Charsets.UTF_8)) }
                val code = connection.responseCode
                val stream = if (code in 200..299) connection.inputStream else connection.errorStream
                val text = stream?.use { it.readBytes().toString(Charsets.UTF_8) }.orEmpty()
                val response = runCatching { Json.parseToJsonElement(text).jsonObject }.getOrNull()
                if (code !in 200..299 || response == null || "error" in response) {
                    val error = response?.get("error") as? JsonObject
                    throw CallableException(
                        code,
                        error?.get("status")?.jsonPrimitive?.content,
                        error?.get("message")?.jsonPrimitive?.content,
                    )
                }
                response["result"]
            } finally {
                connection.disconnect()
            }
        }
    }

    private companion object {
        /** 関数は AI の応答を待つ（関数の timeoutSeconds は 60） */
        const val TIMEOUT_MILLIS = 70_000
    }
}
