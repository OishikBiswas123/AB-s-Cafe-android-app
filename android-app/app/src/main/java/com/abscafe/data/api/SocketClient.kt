package com.abscafe.data.api

import com.abscafe.BuildConfig
import io.socket.client.IO
import io.socket.client.Socket
import io.socket.emitter.Emitter
import org.json.JSONObject

class SocketClient {

    private var socket: Socket? = null

    fun connect(token: String) {
        try {
            val options = IO.Options().apply {
                forceNew = true
                reconnection = true
                reconnectionAttempts = 10
                reconnectionDelay = 1000
                auth = mapOf("token" to token)
            }
            socket = IO.socket(BuildConfig.BASE_URL, options)
            socket?.connect()
        } catch (e: Exception) {
            e.printStackTrace()
        }
    }

    fun disconnect() {
        socket?.disconnect()
        socket = null
    }

    fun emit(event: String, data: JSONObject) {
        socket?.emit(event, data)
    }

    fun on(event: String, listener: Emitter.Listener) {
        socket?.on(event, listener)
    }

    fun off(event: String) {
        socket?.off(event)
    }

    val isConnected: Boolean get() = socket?.connected() ?: false
}
