package com.lanremotetype.network

import android.content.Context
import android.content.SharedPreferences
import android.util.Log

class TokenManager(context: Context) {
    private val TAG = "TokenManager"
    private val prefs: SharedPreferences = context.getSharedPreferences(
        "sync_type_prefs", Context.MODE_PRIVATE
    )

    fun saveToken(deviceId: String, token: String) {
        // Use commit() for synchronous write - ensures data is saved immediately
        val success = prefs.edit().putString("token_$deviceId", token).commit()
        Log.d(TAG, "=== SAVE TOKEN === device=$deviceId, token=${token.take(8)}..., success=$success")
    }

    fun getToken(deviceId: String): String? {
        val token = prefs.getString("token_$deviceId", null)
        Log.d(TAG, "=== GET TOKEN === device=$deviceId, found=${token != null}, value=${token?.take(8) ?: "null"}")
        return token
    }

    fun clearToken(deviceId: String) {
        prefs.edit().remove("token_$deviceId").commit()
        Log.d(TAG, "=== CLEAR TOKEN === device=$deviceId")
    }

    fun saveDeviceId(deviceId: String) {
        prefs.edit().putString("my_device_id", deviceId).commit()
        Log.d(TAG, "=== SAVE DEVICE ID === $deviceId")
    }

    fun getDeviceId(): String? {
        val id = prefs.getString("my_device_id", null)
        Log.d(TAG, "=== GET DEVICE ID === $id")
        return id
    }

    fun saveLastConnection(ip: String, port: Int, name: String, deviceId: String) {
        prefs.edit()
            .putString("last_ip", ip)
            .putInt("last_port", port)
            .putString("last_name", name)
            .putString("last_device_id", deviceId)
            .commit()
        Log.d(TAG, "=== SAVE LAST CONNECTION === $ip:$port")
    }

    fun getLastConnection(): LastConnection? {
        val ip = prefs.getString("last_ip", null) ?: return null
        val port = prefs.getInt("last_port", 9876)
        val name = prefs.getString("last_name", "Unknown") ?: "Unknown"
        val deviceId = prefs.getString("last_device_id", "") ?: ""
        Log.d(TAG, "=== GET LAST CONNECTION === $ip:$port")
        return LastConnection(ip, port, name, deviceId)
    }

    data class LastConnection(
        val ip: String,
        val port: Int,
        val name: String,
        val deviceId: String
    )

    fun clearAll() {
        prefs.edit().clear().commit()
        Log.d(TAG, "=== CLEAR ALL ===")
    }
}
