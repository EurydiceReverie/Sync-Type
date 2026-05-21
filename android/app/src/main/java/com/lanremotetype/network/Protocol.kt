package com.lanremotetype.network

import kotlinx.serialization.Serializable
import kotlinx.serialization.json.JsonObject

@Serializable
data class WsMessage(
    val type: String,
    val id: String,
    val timestamp: Long,
    val payload: JsonObject
) {
    companion object {
        fun create(type: String, payload: JsonObject): WsMessage {
            return WsMessage(
                type = type,
                id = generateId(),
                timestamp = System.currentTimeMillis(),
                payload = payload
            )
        }

        fun create(type: String, payload: Map<String, String>): WsMessage {
            return WsMessage(
                type = type,
                id = generateId(),
                timestamp = System.currentTimeMillis(),
                payload = kotlinx.serialization.json.buildJsonObject {
                    payload.forEach { (k, v) ->
                        put(k, kotlinx.serialization.json.JsonPrimitive(v))
                    }
                }
            )
        }

        private fun generateId(): String {
            return "msg_${System.currentTimeMillis()}_${(0..999).random()}"
        }
    }
}

@Serializable
data class AuthPayload(
    val device_name: String,
    val device_id: String,
    val token: String
)

@Serializable
data class TypePayload(
    val text: String,
    val mode: String,
    val speed: Int,
    val priority: String = "normal"
)

@Serializable
data class KeyPressPayload(
    val key: String,
    val modifiers: List<String> = emptyList()
)

@Serializable
data class ClipboardPayload(
    val text: String
)

@Serializable
data class PairRequestPayload(
    val device_name: String,
    val device_id: String
)

@Serializable
data class PairVerifyPayload(
    val pin: String,
    val device_id: String
)

@Serializable
data class TypingControlPayload(
    val action: String
)

@Serializable
data class DiscoveryProbe(
    val type: String,
    val client_id: String,
    val timestamp: Long
)

@Serializable
data class DiscoveryResponse(
    val type: String,
    val device_name: String,
    val device_id: String,
    val ws_port: Int,
    val paired: Boolean,
    val version: String,
    val timestamp: Long
)
