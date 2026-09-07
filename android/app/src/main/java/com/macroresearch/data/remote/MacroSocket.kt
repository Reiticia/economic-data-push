package com.macroresearch.data.remote

import com.google.gson.Gson
import com.macroresearch.data.model.SocketEvent
import kotlinx.coroutines.channels.BufferOverflow
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.launch
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.Response
import okhttp3.WebSocket
import okhttp3.WebSocketListener

class MacroSocket(
    private val client: OkHttpClient,
    private val url: String,
    private val gson: Gson,
) {
    private val _events = MutableSharedFlow<SocketEvent>(
        extraBufferCapacity = 32,
        onBufferOverflow = BufferOverflow.DROP_OLDEST,
    )
    val events: SharedFlow<SocketEvent> = _events

    private var webSocket: WebSocket? = null
    private var reconnectAttempt = 0
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)

    fun connect() {
        if (webSocket != null) return
        webSocket = client.newWebSocket(Request.Builder().url(url).build(), listener)
    }

    fun close() {
        webSocket?.close(1000, "client shutdown")
        webSocket = null
    }

    private val listener = object : WebSocketListener() {
        override fun onOpen(webSocket: WebSocket, response: Response) {
            reconnectAttempt = 0
        }

        override fun onMessage(webSocket: WebSocket, text: String) {
            runCatching { gson.fromJson(text, SocketEvent::class.java) }
                .onSuccess(_events::tryEmit)
        }

        override fun onClosed(webSocket: WebSocket, code: Int, reason: String) {
            this@MacroSocket.webSocket = null
        }

        override fun onFailure(webSocket: WebSocket, throwable: Throwable, response: Response?) {
            this@MacroSocket.webSocket = null
            val delaySeconds = (1L shl reconnectAttempt.coerceAtMost(5))
            reconnectAttempt++
            scope.launch {
                delay(delaySeconds * 1_000)
                connect()
            }
        }
    }
}
