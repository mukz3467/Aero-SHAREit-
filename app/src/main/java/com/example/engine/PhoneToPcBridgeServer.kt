package com.example.engine

import android.content.Context
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import java.io.BufferedReader
import java.io.File
import java.io.InputStreamReader
import java.io.PrintWriter
import java.net.ServerSocket
import java.net.Socket
import java.util.UUID

class PhoneToPcBridgeServer(
    private val context: Context,
    private val port: Int = AeroTransferProtocol.PC_BRIDGE_PORT,
    private val authToken: String = UUID.randomUUID().toString().replace("-", ""),
    private val onClientConnected: (String) -> Unit = {}
) {
    private var serverSocket: ServerSocket? = null
    private var serverJob: Job? = null
    private val scope = CoroutineScope(Dispatchers.IO)

    @Volatile
    var isRunning: Boolean = false
        private set

    fun start() {
        if (isRunning) return
        serverJob = scope.launch {
            try {
                serverSocket = ServerSocket(port)
                isRunning = true

                while (isActive && !serverSocket!!.isClosed) {
                    val client = serverSocket!!.accept()
                    scope.launch { handleClient(client) }
                }
            } catch (_: Exception) {
            } finally {
                isRunning = false
            }
        }
    }

    private fun handleClient(socket: Socket) {
        socket.use { s ->
            s.soTimeout = 10000
            val reader = BufferedReader(InputStreamReader(s.getInputStream()))
            val writer = PrintWriter(s.getOutputStream())

            val reqLine = reader.readLine() ?: return
            val clientIp = s.inetAddress.hostAddress ?: "Unknown"

            if (reqLine.contains("token=$authToken") || reqLine.contains("token%3D$authToken")) {
                onClientConnected(clientIp)
                val html = """
                    <!DOCTYPE html>
                    <html>
                    <head>
                        <title>AeroShare PC Bridge</title>
                        <meta name="viewport" content="width=device-width, initial-scale=1">
                        <style>
                            body { font-family: -apple-system, sans-serif; background: #080B0A; color: #F4F7F5; padding: 24px; text-align: center; }
                            .card { background: #18211B; border: 1px solid #29352D; border-radius: 16px; padding: 24px; max-width: 480px; margin: 40px auto; }
                            h1 { color: #00D084; font-size: 24px; }
                            p { color: #A4B1A8; font-size: 14px; }
                            .badge { background: #1A00D084; color: #00D084; padding: 6px 14px; border-radius: 20px; font-weight: bold; font-size: 13px; display: inline-block; margin-top: 12px; }
                        </style>
                    </head>
                    <body>
                        <div class="card">
                            <h1>AeroShare WebShare Bridge</h1>
                            <p>Direct high-speed link active with phone storage.</p>
                            <div class="badge">Connected (Port $port)</div>
                            <p style="margin-top: 20px; font-size: 12px; color: #7CFFB2;">Zero cloud routing • Pure local network throughput</p>
                        </div>
                    </body>
                    </html>
                """.trimIndent()

                writer.println("HTTP/1.1 200 OK")
                writer.println("Content-Type: text/html; charset=utf-8")
                writer.println("Content-Length: ${html.toByteArray().size}")
                writer.println("Connection: close")
                writer.println()
                writer.print(html)
                writer.flush()
            } else {
                val forbidden = "HTTP/1.1 403 Forbidden\r\nConnection: close\r\n\r\nAuthentication Token Invalid"
                writer.print(forbidden)
                writer.flush()
            }
        }
    }

    fun stop() {
        isRunning = false
        try {
            serverSocket?.close()
        } catch (_: Exception) {}
        serverJob?.cancel()
    }
}
