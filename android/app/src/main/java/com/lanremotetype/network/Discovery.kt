package com.lanremotetype.network

import android.content.Context
import android.util.Log
import com.lanremotetype.model.Device
import com.lanremotetype.util.Constants
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.serialization.json.Json
import java.net.DatagramPacket
import java.net.DatagramSocket
import java.net.InetAddress
import java.net.NetworkInterface

class LanDiscovery(private val context: Context) {

    private val TAG = "LanDiscovery"
    private val _discoveredDevices = MutableStateFlow<List<Device>>(emptyList())
    val discoveredDevices: StateFlow<List<Device>> = _discoveredDevices.asStateFlow()
    private val discoveredMap = mutableMapOf<String, Device>()

    fun startDiscovery() {
        Thread {
            try {
                Log.d(TAG, "Starting discovery...")
                discoveredMap.clear()
                _discoveredDevices.value = emptyList()

                // Single socket for both send and receive
                val socket = DatagramSocket()
                socket.broadcast = true
                socket.soTimeout = 3000

                val probe = """{"type":"discovery_probe","client_id":"android_client","timestamp":${System.currentTimeMillis()}}""".toByteArray()

                // Send probes multiple times for reliability
                val targets = listOf("255.255.255.255", "192.168.1.255", "192.168.0.255")
                for (round in 1..3) {
                    for (target in targets) {
                        try {
                            val address = InetAddress.getByName(target)
                            val packet = DatagramPacket(probe, probe.size, address, Constants.DEFAULT_DISCOVERY_PORT)
                            socket.send(packet)
                        } catch (_: Exception) {}
                    }
                    Thread.sleep(200)
                }
                Log.d(TAG, "Probes sent")

                // Listen for responses on SAME socket
                Log.d(TAG, "Listening on port ${socket.localPort}...")
                val endTime = System.currentTimeMillis() + 3000
                val buf = ByteArray(1024)

                while (System.currentTimeMillis() < endTime) {
                    try {
                        val recvPacket = DatagramPacket(buf, buf.size)
                        socket.receive(recvPacket)
                        val response = String(recvPacket.data, 0, recvPacket.length)
                        val ip = recvPacket.address.hostAddress ?: "unknown"
                        Log.d(TAG, "Response from $ip")
                        processResponse(response, ip)
                    } catch (e: java.net.SocketTimeoutException) {
                        break
                    } catch (e: Exception) {
                        Log.e(TAG, "Receive error: ${e.message}")
                        break
                    }
                }

                socket.close()
                Log.d(TAG, "Done. Found ${discoveredMap.size} devices")
            } catch (e: Exception) {
                Log.e(TAG, "Error: ${e.message}", e)
            }
        }.start()
    }

    private fun processResponse(response: String, ip: String) {
        try {
            val r = Json.decodeFromString<DiscoveryResponse>(response)
            val device = Device(
                deviceId = r.device_id,
                deviceName = r.device_name,
                ipAddress = ip,
                wsPort = r.ws_port,
                isPaired = r.paired,
                version = r.version,
                discoveredAt = System.currentTimeMillis()
            )
            discoveredMap[device.deviceId] = device
            _discoveredDevices.value = discoveredMap.values.toList()
            Log.d(TAG, "Found: ${device.deviceName} at $ip")
        } catch (e: Exception) {
            Log.e(TAG, "Parse error: ${e.message}")
        }
    }

    fun clearDiscovered() {
        discoveredMap.clear()
        _discoveredDevices.value = emptyList()
    }
}
