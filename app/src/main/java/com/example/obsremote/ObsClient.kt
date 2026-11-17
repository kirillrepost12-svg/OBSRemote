package com.example.obsremote

import android.util.Base64
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.withTimeoutOrNull
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.Response
import okhttp3.WebSocket
import okhttp3.WebSocketListener
import org.json.JSONObject
import java.security.MessageDigest
import java.util.UUID
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicInteger

sealed interface ConnectionState {
    data object Disconnected : ConnectionState
    data object Connecting : ConnectionState
    data object Connected : ConnectionState
    data class Failed(val message: String) : ConnectionState
}

data class ObsEvent(val type: String, val data: JSONObject)

class ObsException(message: String, val code: Int = 0) : Exception(message)

class ObsClient {
    private val http = OkHttpClient.Builder()
        .connectTimeout(5, TimeUnit.SECONDS)
        .pingInterval(15, TimeUnit.SECONDS)
        .build()

    @Volatile
    private var socket: WebSocket? = null
    @Volatile
    private var password: String = ""

    private val generation = AtomicInteger(0)
    private val pending = ConcurrentHashMap<String, CompletableDeferred<JSONObject>>()

    private val _state = MutableStateFlow<ConnectionState>(ConnectionState.Disconnected)
    val state: StateFlow<ConnectionState> = _state

    private val _events = MutableSharedFlow<ObsEvent>(extraBufferCapacity = 64)
    val events: SharedFlow<ObsEvent> = _events

    fun connect(host: String, port: Int, password: String) {
        closeSocket()
        this.password = password
        _state.value = ConnectionState.Connecting
        val gen = generation.incrementAndGet()
        try {
            val request = Request.Builder().url("ws://${host.trim()}:$port").build()
            socket = http.newWebSocket(request, Listener(gen))
        } catch (e: IllegalArgumentException) {
            fail(gen, "Некорректный адрес: ${host.trim()}")
        }
    }

    fun disconnect() {
        closeSocket()
        _state.value = ConnectionState.Disconnected
    }

        suspend fun request(type: String, data: JSONObject? = null): JSONObject {
        val ws = socket ?: throw ObsException("Нет соединения с OBS")
        val id = UUID.randomUUID().toString()
        val deferred = CompletableDeferred<JSONObject>()
        pending[id] = deferred

        val d = JSONObject().put("requestType", type).put("requestId", id)
        if (data != null) d.put("requestData", data)
        val message = JSONObject().put("op", 6).put("d", d).toString()

        if (!ws.send(message)) {
            pending.remove(id)
            throw ObsException("Не удалось отправить команду")
        }
        try {
            return withTimeoutOrNull(5_000) { deferred.await() }
                ?: throw ObsException("OBS не ответил на запрос $type")
        } finally {
            pending.remove(id)
        }
    }

    private fun closeSocket() {
        generation.incrementAndGet()
        socket?.close(1000, "bye")
        socket = null
        failPending("Соединение закрыто")
    }

    private fun fail(gen: Int, message: String) {
        if (gen != generation.get()) return
        generation.incrementAndGet()
        socket = null
        failPending(message)
        _state.value = ConnectionState.Failed(message)
    }

    private fun failPending(message: String) {
        val it = pending.values.iterator()
        while (it.hasNext()) {
            it.next().completeExceptionally(ObsException(message))
            it.remove()
        }
    }

    private fun sha256Base64(text: String): String {
        val hash = MessageDigest.getInstance("SHA-256").digest(text.toByteArray(Charsets.UTF_8))
        return Base64.encodeToString(hash, Base64.NO_WRAP)
    }

    private fun identify(ws: WebSocket, hello: JSONObject) {
        val identify = JSONObject().put("rpcVersion", 1)
        val auth = hello.optJSONObject("authentication")
        if (auth != null) {
            val secret = sha256Base64(password + auth.getString("salt"))
            identify.put("authentication", sha256Base64(secret + auth.getString("challenge")))
        }
        ws.send(JSONObject().put("op", 1).put("d", identify).toString())
    }

    private fun handleResponse(d: JSONObject) {
        val deferred = pending.remove(d.getString("requestId")) ?: return
        val status = d.getJSONObject("requestStatus")
        if (status.getBoolean("result")) {
            deferred.complete(d.optJSONObject("responseData") ?: JSONObject())
        } else {
            deferred.completeExceptionally(
                ObsException(status.optString("comment", "Ошибка запроса"), status.optInt("code"))
            )
        }
    }

    private inner class Listener(private val gen: Int) : WebSocketListener() {
        override fun onMessage(webSocket: WebSocket, text: String) {
            if (gen != generation.get()) return
            try {
                val json = JSONObject(text)
                val d = json.optJSONObject("d") ?: JSONObject()
                when (json.getInt("op")) {
                    0 -> identify(webSocket, d)
                    2 -> _state.value = ConnectionState.Connected
                    5 -> _events.tryEmit(
                        ObsEvent(d.getString("eventType"), d.optJSONObject("eventData") ?: JSONObject())
                    )
                    7 -> handleResponse(d)
                }
            } catch (e: Exception) {
            }
        }

        override fun onClosing(webSocket: WebSocket, code: Int, reason: String) {
            val msg = if (code == 4009) "Неверный пароль" else "OBS закрыл соединение ($code) $reason".trim()
            fail(gen, msg)
            webSocket.close(1000, null)
        }

        override fun onFailure(webSocket: WebSocket, t: Throwable, response: Response?) {
            fail(gen, "Не удалось подключиться: ${t.message ?: "нет ответа"}")
        }
    }
}
