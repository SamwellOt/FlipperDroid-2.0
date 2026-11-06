package com.example.flipperdroid.viewmodel

import android.app.Application
import android.content.Context
import android.net.nsd.NsdManager
import android.net.nsd.NsdServiceInfo
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import java.net.DatagramPacket
import java.net.DatagramSocket
import java.net.InetAddress
import java.net.InetSocketAddress
import java.net.Socket

/** Un service découvert sur le réseau local (mDNS ou SSDP/UPnP). */
data class LanService(
    val label: String,
    val host: String,
    val port: Int,
    val source: String,
    val info: String,
    val banner: String? = null
)

/**
 * Découverte de services sur la LAN : mDNS/Bonjour (NsdManager) + SSDP/UPnP, avec
 * récupération de bannière (HTTP/SSH/FTP) sur les ports ouverts. Sans root.
 */
class LanDiscoveryViewModel(app: Application) : AndroidViewModel(app) {

    private val nsd = app.getSystemService(Context.NSD_SERVICE) as? NsdManager

    private val serviceTypes = listOf(
        "_http._tcp.", "_https._tcp.", "_printer._tcp.", "_ipp._tcp.",
        "_googlecast._tcp.", "_raop._tcp.", "_airplay._tcp.", "_ssh._tcp.",
        "_workstation._tcp.", "_smb._tcp.", "_afpovertcp._tcp.", "_ftp._tcp."
    )

    /** Protégé par synchronized(listeners). */
    private val listeners = mutableListOf<NsdManager.DiscoveryListener>()
    /** Protégé par synchronized(this) (méthodes @Synchronized). */
    private val resolveQueue = ArrayDeque<NsdServiceInfo>()
    private var resolving = false

    /**
     * Services découverts. Écrit par les callbacks NSD, la boucle SSDP (IO) et les
     * bannières (IO) : tout accès passe par synchronized(map).
     */
    private val map = LinkedHashMap<String, LanService>()

    private val _services = MutableStateFlow<List<LanService>>(emptyList())
    val services: StateFlow<List<LanService>> = _services

    private val _isScanning = MutableStateFlow(false)
    val isScanning: StateFlow<Boolean> = _isScanning

    private val _status = MutableStateFlow("")
    val status: StateFlow<String> = _status

    fun start() {
        if (_isScanning.value) return
        synchronized(map) { map.clear(); emit() }
        _isScanning.value = true
        _status.value = "Discovering (mDNS + SSDP)…"
        startMdns()
        startSsdp()
    }

    fun stop() {
        _isScanning.value = false
        val toStop = synchronized(listeners) {
            val copy = listeners.toList()
            listeners.clear()
            copy
        }
        toStop.forEach { try { nsd?.stopServiceDiscovery(it) } catch (_: Exception) {} }
        synchronized(this) { resolveQueue.clear() }
        val count = synchronized(map) { map.size }
        _status.value = "Stopped. $count services."
    }

    private fun startMdns() {
        val manager = nsd ?: run { _status.value = "NSD unavailable"; return }
        serviceTypes.forEach { type ->
            val listener = object : NsdManager.DiscoveryListener {
                override fun onStartDiscoveryFailed(serviceType: String?, errorCode: Int) {}
                override fun onStopDiscoveryFailed(serviceType: String?, errorCode: Int) {}
                override fun onDiscoveryStarted(serviceType: String?) {}
                override fun onDiscoveryStopped(serviceType: String?) {}
                override fun onServiceFound(serviceInfo: NsdServiceInfo) { enqueueResolve(serviceInfo) }
                override fun onServiceLost(serviceInfo: NsdServiceInfo?) {}
            }
            try {
                manager.discoverServices(type, NsdManager.PROTOCOL_DNS_SD, listener)
                synchronized(listeners) { listeners.add(listener) }
            } catch (_: Exception) {}
        }
    }

    @Synchronized
    private fun enqueueResolve(info: NsdServiceInfo) {
        resolveQueue.addLast(info)
        pumpResolve()
    }

    @Synchronized
    private fun pumpResolve() {
        if (resolving) return
        val next = if (resolveQueue.isEmpty()) null else resolveQueue.removeFirst()
        if (next == null) return
        val manager = nsd ?: return
        resolving = true
        val resolveListener = object : NsdManager.ResolveListener {
            override fun onResolveFailed(serviceInfo: NsdServiceInfo?, errorCode: Int) {
                synchronized(this@LanDiscoveryViewModel) { resolving = false; pumpResolve() }
            }
            @Suppress("DEPRECATION")
            override fun onServiceResolved(serviceInfo: NsdServiceInfo) {
                val host = serviceInfo.host?.hostAddress
                if (host != null) {
                    addService(
                        LanService(
                            label = serviceInfo.serviceName,
                            host = host,
                            port = serviceInfo.port,
                            source = "mDNS",
                            info = serviceInfo.serviceType?.trim('.') ?: ""
                        )
                    )
                }
                synchronized(this@LanDiscoveryViewModel) { resolving = false; pumpResolve() }
            }
        }
        try { manager.resolveService(next, resolveListener) }
        catch (e: Exception) { resolving = false; pumpResolve() }
    }

    private fun startSsdp() {
        viewModelScope.launch(Dispatchers.IO) {
            var socket: DatagramSocket? = null
            try {
                val s = DatagramSocket()
                socket = s
                s.soTimeout = 4000
                val query = (
                    "M-SEARCH * HTTP/1.1\r\n" +
                        "HOST: 239.255.255.250:1900\r\n" +
                        "MAN: \"ssdp:discover\"\r\n" +
                        "MX: 2\r\n" +
                        "ST: ssdp:all\r\n\r\n"
                    ).toByteArray()
                val group = InetAddress.getByName("239.255.255.250")
                s.send(DatagramPacket(query, query.size, group, 1900))
                val buf = ByteArray(2048)
                while (true) {
                    val packet = DatagramPacket(buf, buf.size)
                    try { s.receive(packet) } catch (e: Exception) { break }
                    val resp = String(packet.data, 0, packet.length)
                    val server = headerOf(resp, "SERVER")
                    val location = headerOf(resp, "LOCATION")
                    val st = headerOf(resp, "ST")
                    val host = packet.address?.hostAddress ?: "?"
                    addService(
                        LanService(
                            label = server ?: st ?: "UPnP device",
                            host = host,
                            port = portFromLocation(location),
                            source = "SSDP",
                            info = location ?: (st ?: "")
                        )
                    )
                }
            } catch (e: Exception) {
                _status.value = "SSDP error: ${e.message}"
            } finally {
                // Socket fermé dans tous les cas (y compris en cas d'erreur d'envoi).
                socket?.close()
                if (_isScanning.value) {
                    _status.value = "Found ${synchronized(map) { map.size }} services."
                }
            }
        }
    }

    /** Récupère une bannière (HTTP/SSH/FTP…) sur host:port. */
    fun grabBanner(host: String, port: Int) {
        viewModelScope.launch(Dispatchers.IO) {
            val banner = if (port == 443) {
                // Port TLS : une requête HTTP en clair n'a pas de sens, on ne sonde pas.
                "TLS — not probed"
            } else {
                try {
                    Socket().use { s ->
                        s.connect(InetSocketAddress(host, port), 3000)
                        s.soTimeout = 3000
                        // HTTP : provoque une réponse ; sinon on lit ce que le service envoie.
                        if (port == 80 || port == 8080 || port == 8000) {
                            s.getOutputStream().write("HEAD / HTTP/1.0\r\nHost: $host\r\n\r\n".toByteArray())
                            s.getOutputStream().flush()
                        }
                        val buf = ByteArray(512)
                        val n = try { s.getInputStream().read(buf) } catch (_: Exception) { -1 }
                        if (n > 0) String(buf, 0, n).trim() else "(no banner)"
                    }
                } catch (e: Exception) { "banner error: ${e.message}" }
            }
            val key = "$host:$port"
            synchronized(map) {
                val existing = map.entries.firstOrNull { it.value.host == host && it.value.port == port }
                if (existing != null) {
                    map[existing.key] = existing.value.copy(banner = banner.take(300))
                } else {
                    map[key + ":banner"] = LanService(key, host, port, "banner", "", banner.take(300))
                }
                emit()
            }
            _status.value = "Banner $host:$port grabbed."
        }
    }

    /** Ajoute un service si absent. */
    private fun addService(s: LanService) {
        val key = "${s.source}:${s.host}:${s.port}:${s.label}"
        synchronized(map) {
            if (!map.containsKey(key)) { map[key] = s; emit() }
        }
    }

    /** Publie la liste triée. À appeler avec le verrou sur [map] tenu. */
    private fun emit() {
        _services.value = map.values.sortedWith(compareBy({ it.host }, { it.port }))
    }

    private fun headerOf(resp: String, name: String): String? =
        resp.lineSequence().firstOrNull { it.startsWith("$name:", true) }
            ?.substringAfter(":")?.trim()?.ifBlank { null }

    private fun portFromLocation(loc: String?): Int {
        if (loc == null) return 0
        return try {
            val hostPort = loc.substringAfter("://").substringBefore('/')
            hostPort.substringAfter(':', "").toIntOrNull() ?: if (loc.startsWith("https")) 443 else 80
        } catch (e: Exception) { 0 }
    }

    override fun onCleared() {
        super.onCleared()
        stop()
    }
}
