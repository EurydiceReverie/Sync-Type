package com.lanremotetype.model

data class Device(
    val deviceId: String,
    val deviceName: String,
    val ipAddress: String,
    val wsPort: Int,
    val isPaired: Boolean,
    val version: String,
    val discoveredAt: Long = System.currentTimeMillis()
)
