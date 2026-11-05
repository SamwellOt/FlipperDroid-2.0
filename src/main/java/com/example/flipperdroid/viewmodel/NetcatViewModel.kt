package com.example.flipperdroid.viewmodel

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.launch
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.update
import java.io.File
import java.io.OutputStream
import java.net.DatagramPacket
import java.net.DatagramSocket
import java.net.InetAddress
import java.net.InetSocketAddress
import java.net.NetworkInterface
import java.net.ServerSocket
import java.net.Socket

/**
 * « Netcat » : client/serveur TCP, envoi UDP, et un petit serveur HTTP de fichiers
 * (livraison de payload / listeners en pentest LAN). Sans root.
 */
class NetcatViewModel(app: Application) : AndroidViewModel(app) {

    private val _log = MutableStateFlow<List<String>>(emptyList())
    val log: StateFlow<List<String>> = _log

    private val _connected = MutableStateFlow(false)
    val connected: StateFlow<Boolean> = _connected

    private val _httpInfo = MutableStateFlow("")
    val httpInfo: StateFlow<String> = _httpInfo

    private var socket: Socket? = null
    private var serverSocket: ServerSocket? = null
    private var out: OutputStream? = null
    private var connJob: Job? = null

    private var httpServer: ServerSocket? = null
    @Volatile private var httpRunning = false
    private var httpThread: Thread? = null

    private fun appendLog(line: String) { _log.update { (it + line).takeLast(300) } }

    fun clearLog() { _log.value = emptyList() }

    // --- TCP client ---
    fun tcpConnect(host: String, port: Int) {
        if (_connected.value || connJob?.isActive == true) { appendLog("[already connected or connecting]"); return }
        connJob = viewModelScope.launch(Dispatchers.IO) {
            try {
                val s = Socket()
                s.connect(InetSocketAddress(host, port), 5000)
                socket = s; out = s.outputStream
                _connected.value = true
                appendLog("[connected to $host:$port]")
                readLoop(s)
            } catch (e: Exception) {
                appendLog("[connect error: ${e.message}]")
                closeConn()
            }
        }
    }

    // --- TCP listener ---
    fun tcpListen(port: Int) {
        if (_connected.value || connJob?.isActive == true) { appendLog("[busy]"); return }
        connJob = viewModelScope.launch(Dispatchers.IO) {
            try {
                val srv = ServerSocket(port)
                serverSocket = srv
                appendLog("[listening on 0.0.0.0:$port]")
                val c = srv.accept()
                socket = c; out = c.outputStream
                _connected.value = true
                appendLog("[client ${c.inetAddress?.hostAddress} connected]")
                readLoop(c)
            } catch (e: Exception) {
                appendLog("[listen error: ${e.message}]")
                closeConn()
            }
        }
    }

    private fun readLoop(s: Socket) {
        try {
            val input = s.getInputStream()
            val buf = ByteArray(2048)
            // disconnect() ferme le socket -> input.read lève une exception -> on sort.
            while (!s.isClosed) {
                val n = input.read(buf)
                if (n < 0) break
                appendLog("< " + String(buf, 0, n, Charsets.UTF_8).trimEnd('\n', '\r'))
            }
        } catch (_: Exception) {
        } finally {
            appendLog("[disconnected]")
            closeConn()
        }
    }

    fun send(text: String) {
        val o = out ?: run { appendLog("[not connected]"); return }
        viewModelScope.launch(Dispatchers.IO) {
            try { o.write((text + "\n").toByteArray(Charsets.UTF_8)); o.flush(); appendLog("> $text") }
            catch (e: Exception) { appendLog("[send error: ${e.message}]") }
        }
    }

    fun disconnect() {
        connJob?.cancel(); connJob = null
        closeConn()
        appendLog("[closed]")
    }

    private fun closeConn() {
        try { out?.close() } catch (_: Exception) {}
        try { socket?.close() } catch (_: Exception) {}
        try { serverSocket?.close() } catch (_: Exception) {}
        out = null; socket = null; serverSocket = null
        _connected.value = false
    }

    // --- UDP ---
    fun udpSend(host: String, port: Int, text: String) {
        viewModelScope.launch(Dispatchers.IO) {
            try {
                DatagramSocket().use { ds ->
                    val data = text.toByteArray(Charsets.UTF_8)
                    ds.send(DatagramPacket(data, data.size, InetAddress.getByName(host), port))
                }
                appendLog("[udp -> $host:$port] $text")
            } catch (e: Exception) { appendLog("[udp error: ${e.message}]") }
        }
    }

    // --- HTTP file server (serves the app's external files dir) ---
    fun startHttp(port: Int) {
        if (httpRunning) { appendLog("[http already running]"); return }
        // Seul un sous-dossier dédié est servi : la racine contenait les dumps NFC (clés),
        // les journaux et le wardriving (GPS), exposés sans authentification sur le LAN.
        val root = getApplication<Application>().getExternalFilesDir(null)
            ?: getApplication<Application>().filesDir
        val base = File(root, "served").apply { mkdirs() }
        httpRunning = true
        httpThread = Thread {
            try {
                val srv = ServerSocket(port)
                httpServer = srv
                _httpInfo.value = "http://${localIp()}:$port  (serving ${base.absolutePath} — readable by anyone on this network)"
                appendLog("[http server on :$port, serving only ${base.name}/]")
                while (httpRunning) {
                    val client = try { srv.accept() } catch (e: Exception) { break }
                    // Un client muet ne doit pas bloquer le serveur indéfiniment.
                    try { client.soTimeout = 5000; serveHttp(client, base) }
                    catch (_: Throwable) {}
                    finally { try { client.close() } catch (_: Exception) {} }
                }
            } catch (e: Exception) {
                _httpInfo.value = "HTTP error: ${e.message}"
                httpRunning = false
            }
        }.apply { start() }
    }

    fun stopHttp() {
        httpRunning = false
        try { httpServer?.close() } catch (_: Exception) {}
        httpServer = null; httpThread = null
        _httpInfo.value = ""
        appendLog("[http stopped]")
    }

    private fun serveHttp(client: Socket, base: File) {
        val reader = client.getInputStream().bufferedReader()
        val requestLine = reader.readLine() ?: return
        val path = requestLine.split(" ").getOrNull(1)?.substringBefore('?') ?: "/"
        val decoded = java.net.URLDecoder.decode(path, "UTF-8").removePrefix("/")
        val target = File(base, decoded)
        val out = client.getOutputStream()
        // Empêche la traversée de répertoire hors de base (y compris un dossier frère
        // dont le nom commence pareil, ex. "served2").
        val basePath = base.canonicalPath
        val targetPath = target.canonicalPath
        if (targetPath != basePath && !targetPath.startsWith(basePath + File.separator)) {
            out.write("HTTP/1.1 403 Forbidden\r\nConnection: close\r\n\r\n".toByteArray()); return
        }
        if (target.isDirectory || decoded.isEmpty()) {
            val dir = if (decoded.isEmpty()) base else target
            val items = dir.listFiles()?.sortedBy { it.name } ?: emptyList()
            val html = StringBuilder("<html><body><h3>${dir.name.ifEmpty { "/" }}</h3><ul>")
            items.forEach {
                val rel = it.relativeTo(base).path.split(File.separatorChar)
                    .joinToString("/") { seg -> java.net.URLEncoder.encode(seg, "UTF-8").replace("+", "%20") }
                val href = "/" + rel + (if (it.isDirectory) "/" else "")
                html.append("<li><a href=\"$href\">${escapeHtml(it.name)}${if (it.isDirectory) "/" else " (${it.length()} B)"}</a></li>")
            }
            html.append("</ul></body></html>")
            val bytes = html.toString().toByteArray()
            out.write(("HTTP/1.1 200 OK\r\nContent-Type: text/html\r\nContent-Length: ${bytes.size}\r\nConnection: close\r\n\r\n").toByteArray())
            out.write(bytes)
        } else if (target.isFile) {
            // Flux continu : readBytes() d'un gros fichier provoquait un OutOfMemoryError.
            val size = target.length()
            out.write(("HTTP/1.1 200 OK\r\nContent-Type: application/octet-stream\r\nContent-Length: $size\r\nConnection: close\r\n\r\n").toByteArray())
            target.inputStream().use { it.copyTo(out) }
            appendLog("[http] served ${target.name} ($size B) to ${client.inetAddress?.hostAddress}")
        } else {
            out.write("HTTP/1.1 404 Not Found\r\nConnection: close\r\n\r\n".toByteArray())
        }
        out.flush()
    }

    private fun escapeHtml(v: String) = v.replace("&", "&amp;").replace("<", "&lt;")
        .replace(">", "&gt;").replace("\"", "&quot;")

    private fun localIp(): String {
        try {
            NetworkInterface.getNetworkInterfaces().toList().forEach { nif ->
                nif.inetAddresses.toList().forEach { addr ->
                    if (!addr.isLoopbackAddress && addr is java.net.Inet4Address) return addr.hostAddress ?: "?"
                }
            }
        } catch (_: Exception) {}
        return "?"
    }

    override fun onCleared() {
        super.onCleared()
        disconnect()
        stopHttp()
    }
}
