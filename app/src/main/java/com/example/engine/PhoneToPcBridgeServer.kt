package com.example.engine

import android.content.Context
import android.os.Environment
import android.os.StatFs
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import java.io.BufferedInputStream
import java.io.File
import java.io.FileOutputStream
import java.io.IOException
import java.net.ServerSocket
import java.net.Socket
import java.net.URLDecoder
import java.net.URLEncoder
import java.nio.charset.StandardCharsets
import java.util.UUID

/**
 * Local-network browser bridge. It is intentionally HTTP-only and must be used on a trusted LAN.
 * The random session token authorizes each request; this is not an encrypted transport.
 */
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
        if (isRunning || serverJob?.isActive == true) return
        serverJob = scope.launch {
            try {
                serverSocket = ServerSocket(port).apply { reuseAddress = true }
                isRunning = true
                while (isActive && serverSocket?.isClosed == false) {
                    val client = serverSocket?.accept() ?: break
                    scope.launch { handleClient(client) }
                }
            } catch (_: Exception) {
                // Server can be stopped while accept() is blocked.
            } finally {
                isRunning = false
            }
        }
    }

    private fun handleClient(socket: Socket) {
        socket.use { client ->
            client.soTimeout = 30_000
            val input = BufferedInputStream(client.getInputStream())
            val output = client.getOutputStream()
            val requestLine = readAsciiLine(input, 8192) ?: return
            val requestParts = requestLine.split(' ', limit = 3)
            if (requestParts.size != 3 || !requestParts[2].startsWith("HTTP/1.")) {
                respond(output, 400, "Bad Request", "text/plain; charset=utf-8", "Invalid HTTP request".toByteArray())
                return
            }
            val method = requestParts[0].uppercase()
            val target = requestParts[1]
            val headers = linkedMapOf<String, String>()
            var headerBytes = 0
            while (true) {
                val line = readAsciiLine(input, 8192) ?: return
                headerBytes += line.length
                if (headerBytes > 32 * 1024) {
                    respond(output, 431, "Request Header Fields Too Large", "text/plain; charset=utf-8", byteArrayOf())
                    return
                }
                if (line.isEmpty()) break
                val colon = line.indexOf(':')
                if (colon > 0) headers[line.substring(0, colon).trim().lowercase()] = line.substring(colon + 1).trim()
            }

            val path = target.substringBefore('?')
            val query = parseQuery(target.substringAfter('?', ""))
            if (query["token"] != authToken) {
                respond(output, 403, "Forbidden", "text/plain; charset=utf-8", "Authentication token invalid".toByteArray())
                return
            }
            onClientConnected(client.inetAddress.hostAddress ?: "Unknown")

            when {
                method == "GET" && path == "/" -> {
                    val html = bridgeHtml().toByteArray(StandardCharsets.UTF_8)
                    respond(output, 200, "OK", "text/html; charset=utf-8", html)
                }
                method == "GET" && path == "/files" -> {
                    val files = receivedDirectory().listFiles()
                        ?.filter { it.isFile && !it.name.startsWith(".aero_receive_") && !it.name.endsWith(".part") }
                        .orEmpty()
                    val json = files.joinToString(prefix = "[", postfix = "]") { file ->
                        """{"name":"${jsonEscape(file.name)}","size":${file.length()},"url":"/download?token=${url(authToken)}&name=${url(file.name)}"}"""
                    }
                    respond(output, 200, "OK", "application/json; charset=utf-8", json.toByteArray(StandardCharsets.UTF_8))
                }
                method == "GET" && path == "/download" -> {
                    val name = query["name"] ?: run {
                        respond(output, 400, "Bad Request", "text/plain; charset=utf-8", "Missing file name".toByteArray())
                        return
                    }
                    val file = safeExistingFile(name) ?: run {
                        respond(output, 404, "Not Found", "text/plain; charset=utf-8", "File not found".toByteArray())
                        return
                    }
                    output.write("HTTP/1.1 200 OK\r\n".toByteArray())
                    output.write("Content-Type: application/octet-stream\r\n".toByteArray())
                    output.write("Content-Length: ${file.length()}\r\n".toByteArray())
                    output.write("Content-Disposition: attachment; filename=\"${headerSafe(file.name)}\"\r\n".toByteArray())
                    output.write("X-Content-Type-Options: nosniff\r\nConnection: close\r\n\r\n".toByteArray())
                    file.inputStream().buffered(64 * 1024).use { it.copyTo(output, 64 * 1024) }
                    output.flush()
                }
                method == "PUT" && path == "/upload" -> {
                    val name = AeroSecuritySanitizer.sanitizeFilename(query["name"].orEmpty())
                    val length = headers["content-length"]?.toLongOrNull()
                    if (length == null || length < 0L || length > MAX_UPLOAD_BYTES) {
                        respond(output, 413, "Payload Too Large", "text/plain; charset=utf-8", "Invalid or oversized upload".toByteArray())
                        return
                    }
                    val directory = receivedDirectory()
                    val available = try { StatFs(directory.absolutePath).availableBytes } catch (_: Exception) { 0L }
                    if (length > Long.MAX_VALUE - STORAGE_MARGIN_BYTES || available < length + STORAGE_MARGIN_BYTES) {
                        respond(output, 507, "Insufficient Storage", "text/plain; charset=utf-8", "Not enough free storage".toByteArray())
                        return
                    }
                    val temp = File.createTempFile(".aero_pc_", ".part", directory)
                    try {
                        FileOutputStream(temp).use { fileOut ->
                            val buffer = ByteArray(64 * 1024)
                            var remaining = length
                            while (remaining > 0L) {
                                val count = input.read(buffer, 0, minOf(buffer.size.toLong(), remaining).toInt())
                                if (count < 0) throw IOException("Upload ended before all bytes arrived")
                                fileOut.write(buffer, 0, count)
                                remaining -= count
                            }
                            fileOut.fd.sync()
                        }
                        val destination = AeroSecuritySanitizer.resolveUniqueFile(directory, name)
                        if (!temp.renameTo(destination)) throw IOException("Could not finalize uploaded file")
                        val body = """{"ok":true,"name":"${jsonEscape(destination.name)}","size":${destination.length()}}"""
                        respond(output, 201, "Created", "application/json; charset=utf-8", body.toByteArray(StandardCharsets.UTF_8))
                    } catch (ex: Exception) {
                        temp.delete()
                        respond(output, 400, "Bad Request", "text/plain; charset=utf-8", (ex.message ?: "Upload failed").toByteArray())
                    }
                }
                method == "GET" || method == "HEAD" -> {
                    respond(output, 404, "Not Found", "text/plain; charset=utf-8", "Route not found".toByteArray())
                }
                else -> respond(output, 405, "Method Not Allowed", "text/plain; charset=utf-8", "Method not allowed".toByteArray())
            }
        }
    }

    private fun receivedDirectory(): File {
        val directory = (context.getExternalFilesDir(Environment.DIRECTORY_DOWNLOADS)
            ?: File(context.filesDir, "received")).canonicalFile
        if (!directory.exists() && !directory.mkdirs()) throw IOException("Cannot create received-files folder")
        if (!directory.isDirectory || !directory.canWrite()) throw IOException("Received-files folder is not writable")
        return directory
    }

    private fun safeExistingFile(name: String): File? {
        if (name.isBlank() || name.contains('/') || name.contains('\\')) return null
        val directory = receivedDirectory().canonicalFile
        val file = File(directory, name).canonicalFile
        return file.takeIf { it.parentFile == directory && it.isFile && !it.name.endsWith(".part") }
    }

    private fun bridgeHtml(): String = """
        <!doctype html>
        <html lang="en"><head><meta charset="utf-8"><meta name="viewport" content="width=device-width,initial-scale=1">
        <title>Aero SHAREit · PC Bridge</title><style>
        :root{color-scheme:dark}body{font-family:system-ui,sans-serif;background:#080b0a;color:#f4f7f5;margin:0;padding:24px}
        main{max-width:760px;margin:24px auto}.card{background:#18211b;border:1px solid #29352d;border-radius:18px;padding:22px;margin:14px 0}
        h1{color:#00d084}p,small{color:#a4b1a8}.btn{background:#00d084;color:#08100b;border:0;border-radius:10px;padding:10px 14px;font-weight:700;cursor:pointer}
        input{max-width:100%}li{padding:10px 0;border-bottom:1px solid #29352d;overflow-wrap:anywhere}a{color:#00d084}
        </style></head><body><main><h1>Aero SHAREit</h1><p>Local-network file bridge · files stay on this device</p>
        <section class="card"><h2>Send a file to your phone</h2><input id="file" type="file"><button class="btn" id="upload">Upload file</button><p id="status" role="status">Choose a file to begin.</p></section>
        <section class="card"><h2>Files on your phone</h2><button class="btn" id="refresh">Refresh files</button><ul id="files"></ul></section>
        <small>Use only on a trusted local network. This bridge uses HTTP, not encrypted HTTPS. Keep the session link private and turn the bridge off when finished.</small></main>
        <script>
        const token=new URLSearchParams(location.search).get('token')||'';
        const status=document.getElementById('status'),list=document.getElementById('files');
        const human=n=>{if(n<1024)return n+' B';const u=['KB','MB','GB','TB'];let i=-1;do{n/=1024;i++}while(n>=1024&&i<u.length-1);return n.toFixed(1)+' '+u[i]};
        async function refresh(){list.replaceChildren();try{const r=await fetch('/files?token='+encodeURIComponent(token));if(!r.ok)throw Error('Could not load files ('+r.status+')');for(const f of await r.json()){const li=document.createElement('li'),a=document.createElement('a');a.href=f.url;a.textContent=f.name;a.download=f.name;li.append(a,document.createTextNode(' · '+human(f.size)));list.append(li)}if(!list.children.length)list.textContent='No received files found yet.'}catch(e){list.textContent=e.message}}
        document.getElementById('refresh').onclick=refresh;
        document.getElementById('upload').onclick=async()=>{const f=document.getElementById('file').files[0];if(!f){status.textContent='Choose a file first.';return}status.textContent='Uploading '+f.name+'…';try{const r=await fetch('/upload?token='+encodeURIComponent(token)+'&name='+encodeURIComponent(f.name),{method:'PUT',headers:{'Content-Length':String(f.size)},body:f});const text=await r.text();if(!r.ok)throw Error(text||'Upload failed ('+r.status+')');status.textContent='Upload complete: '+f.name;await refresh()}catch(e){status.textContent='Upload failed: '+e.message}};
        refresh();
        </script></body></html>
    """.trimIndent()

    private fun parseQuery(query: String): Map<String, String> = query.split('&')
        .filter { it.isNotEmpty() }
        .associate { pair ->
            val parts = pair.split('=', limit = 2)
            URLDecoder.decode(parts[0], "UTF-8") to URLDecoder.decode(parts.getOrElse(1) { "" }, "UTF-8")
        }

    private fun readAsciiLine(input: BufferedInputStream, maxLength: Int): String? {
        val bytes = java.io.ByteArrayOutputStream()
        while (bytes.size() <= maxLength) {
            val value = input.read()
            if (value == -1) return if (bytes.size() == 0) null else bytes.toString("UTF-8")
            if (value == 10) break
            if (value != 13) bytes.write(value)
        }
        if (bytes.size() > maxLength) throw IOException("HTTP line too long")
        return bytes.toString("UTF-8")
    }

    private fun respond(output: java.io.OutputStream, code: Int, reason: String, contentType: String, body: ByteArray) {
        output.write("HTTP/1.1 $code $reason\r\n".toByteArray(StandardCharsets.US_ASCII))
        output.write("Content-Type: $contentType\r\n".toByteArray(StandardCharsets.US_ASCII))
        output.write("Content-Length: ${body.size}\r\nConnection: close\r\nX-Content-Type-Options: nosniff\r\n\r\n".toByteArray(StandardCharsets.US_ASCII))
        output.write(body)
        output.flush()
    }

    private fun jsonEscape(value: String): String = buildString {
        for (ch in value) when (ch) {
            '\\' -> append("\\\\")
            '"' -> append("\\\"")
            '\n' -> append("\\n")
            '\r' -> append("\\r")
            '\t' -> append("\\t")
            else -> if (ch.code < 0x20) append("\\u%04x".format(ch.code)) else append(ch)
        }
    }

    private fun headerSafe(value: String): String = value.replace("\r", "").replace("\n", "").replace("\"", "").take(180)
    private fun url(value: String): String = URLEncoder.encode(value, "UTF-8")

    fun stop() {
        isRunning = false
        try { serverSocket?.close() } catch (_: Exception) {}
        serverJob?.cancel()
    }

    companion object {
        private const val MAX_UPLOAD_BYTES = 10L * 1024 * 1024 * 1024
        private const val STORAGE_MARGIN_BYTES = 100L * 1024 * 1024
    }
}
