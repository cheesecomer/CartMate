package com.cheesecomer.cartmate.data.functions

import com.google.common.truth.Truth.assertThat
import com.sun.net.httpserver.HttpServer
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.test.runTest
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.buildJsonObject
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.Test
import java.io.IOException
import java.net.InetAddress
import java.net.InetSocketAddress
import java.net.ServerSocket

/** 呼び出し可能な関数の HTTPS の呼び出し（呼び出し可能な関数の約束事どおりに送り、応答を読むこと） */
class CallableFunctionsTest {

    private data class Received(val method: String, val path: String, val headers: Map<String, String?>, val body: String)

    private var received: Received? = null
    private var status = 200
    private var response = """{"result":{"key":"k","status":"done"}}"""

    private val server = HttpServer.create(InetSocketAddress("127.0.0.1", 0), 0).apply {
        createContext("/") { exchange ->
            received = Received(
                exchange.requestMethod,
                exchange.requestURI.path,
                listOf("Authorization", "X-Firebase-AppCheck", "Content-Type").associateWith { exchange.requestHeaders.getFirst(it) },
                exchange.requestBody.readBytes().toString(Charsets.UTF_8),
            )
            val bytes = response.toByteArray()
            exchange.sendResponseHeaders(status, bytes.size.toLong())
            exchange.responseBody.use { it.write(bytes) }
        }
        start()
    }

    @AfterEach
    fun stop() = server.stop(0)

    private fun client(idToken: String? = "id-token", appCheck: String? = "app-check-token") = CallableFunctions(
        baseUrl = "http://127.0.0.1:${server.address.port}",
        idToken = { idToken },
        appCheckToken = { appCheck },
        dispatcher = Dispatchers.IO,
    )

    private val data = buildJsonObject { put("normalizedName", JsonPrimitive("胡麻ドレ")) }

    @Test
    fun `ログインと App Check のトークンを付け、data に包んで POST し、result を返す`() = runTest {
        val result = client().call("judgeName", data)

        val request = received!!
        assertThat(request.method).isEqualTo("POST")
        assertThat(request.path).isEqualTo("/judgeName")
        assertThat(request.headers["Authorization"]).isEqualTo("Bearer id-token")
        assertThat(request.headers["X-Firebase-AppCheck"]).isEqualTo("app-check-token")
        assertThat(request.headers["Content-Type"]).startsWith("application/json")
        assertThat(Json.parseToJsonElement(request.body)).isEqualTo(buildJsonObject { put("data", data) })
        assertThat(result).isEqualTo(Json.parseToJsonElement("""{"key":"k","status":"done"}"""))
    }

    @Test
    fun `トークンが取れなければ、そのヘッダーを付けずに送る（関数が拒否する）`() = runTest {
        client(idToken = null, appCheck = null).call("judgeName", data)
        assertThat(received!!.headers["Authorization"]).isNull()
        assertThat(received!!.headers["X-Firebase-AppCheck"]).isNull()
    }

    @Test
    fun `関数のエラーは、状態の名前を持つ例外にする`() = runTest {
        status = 401
        response = """{"error":{"status":"UNAUTHENTICATED","message":"Unauthenticated"}}"""
        val error = runCatching { client().call("judgeName", data) }.exceptionOrNull() as CallableFunctions.CallableException
        assertThat(error.httpStatus).isEqualTo(401)
        assertThat(error.status).isEqualTo("UNAUTHENTICATED")
    }

    @Test
    fun `JSON でない応答は、失敗として扱う`() = runTest {
        status = 502
        response = "Bad Gateway"
        val error = runCatching { client().call("judgeName", data) }.exceptionOrNull() as CallableFunctions.CallableException
        assertThat(error.httpStatus).isEqualTo(502)
        assertThat(error.status).isNull()
    }

    @Test
    fun `つながらなければ IOException`() = runTest {
        // 一度開いて閉じたポートには、だれもつながっていない
        val port = ServerSocket(0, 0, InetAddress.getByName("127.0.0.1")).use { it.localPort }
        val closed = CallableFunctions("http://127.0.0.1:$port", { null }, { null }, Dispatchers.IO)
        assertThat(runCatching { closed.call("judgeName", data) }.exceptionOrNull()).isInstanceOf(IOException::class.java)
    }
}
