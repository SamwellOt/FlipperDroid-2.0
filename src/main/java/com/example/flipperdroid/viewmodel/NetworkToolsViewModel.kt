package com.example.flipperdroid.viewmodel

import android.annotation.SuppressLint
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.update
import com.example.flipperdroid.util.ShellArgs
import kotlinx.coroutines.launch
import java.io.BufferedReader
import java.io.InputStreamReader
import java.net.InetAddress
import java.net.Socket
import java.net.UnknownHostException
import android.content.Context
import android.util.Log
import java.io.File
import java.io.FileOutputStream
import java.net.InetSocketAddress


data class NetworkResult(
    val command: String,
    val output: String,
    val isError: Boolean = false
)
/**
 * ViewModel gerant des outils reseau
 *
 * Cette classe execute les commandes reseau et expose les résultats
 * via des StateFlow
 */
class NetworkToolsViewModel : ViewModel() {

    companion object {
        /** Dossier dédié à l'app dans /data/local/tmp (root) pour nmap et ses .so. */
        const val NMAP_DIR = "/data/local/tmp/flipperdroid"
        private const val NMAP_TIMEOUT_MS = 120_000L
        private const val MAX_PORTS_PER_SCAN = 2048
    }

    private val _results = MutableStateFlow<List<NetworkResult>>(emptyList())
    val results: StateFlow<List<NetworkResult>> = _results

    private val _isScanning = MutableStateFlow(false)
    val isScanning: StateFlow<Boolean> = _isScanning

    /**
     * Contexte Android pour accéder aux assets et au dossier interne
     */
    @SuppressLint("StaticFieldLeak")
    private var context: Context? = null

    /**
     * Initialise le ViewModel avec le contexte de l'application
     */
    fun initialize(context: Context) {
        this.context = context.applicationContext
    }

    /**
     * Copie le binaire nmap depuis les assets vers le dossier interne si besoin, et le rend exécutable
     * @return Chemin absolu du binaire nmap prêt à l'emploi
     */
    fun copyAssetToFile(context: Context, assetName: String, destFile: File): Boolean {
        return try {
            context.assets.open(assetName).use { input ->
                FileOutputStream(destFile).use { output ->
                    input.copyTo(output)
                }
            }
            true
        } catch (e: Exception) {
            Log.e("NMAP_COPY", "Erreur lors de la copie de l'asset $assetName : ${e.message}")
            false
        }
    }

    /**
     * Copie récursivement tout le contenu du dossier assets/nmap/ vers /data/local/tmp/nmap/
     * @return Chemin absolu du dossier nmap prêt à l'emploi
     */
    fun copyNmapToTmp(context: Context): String {
        val internalNmapDir = File(context.filesDir, "nmap")
        return try {
            // Déjà installé et exécutable : pas de recopie de ~11 Mo à chaque scan.
            val installed = Runtime.getRuntime().exec(arrayOf("su", "-c", "test -x ${ShellArgs.quote("$NMAP_DIR/nmap")}")).waitFor() == 0
            if (!installed) {
                if (internalNmapDir.exists()) deleteRecursively(internalNmapDir)
                internalNmapDir.mkdirs()
                copyAssetsRecursively(context, "nmap", internalNmapDir)
                if (!File(internalNmapDir, "nmap").exists()) return "ERROR : nmap asset missing from the APK"
                // Sous-dossier dédié : on ne touche plus aux fichiers des autres apps dans /data/local/tmp.
                val dir = ShellArgs.quote(NMAP_DIR)
                val cmd = "mkdir -p $dir && cp -r ${ShellArgs.quote(internalNmapDir.absolutePath)}/. $dir/ && chmod 755 $dir $dir/*"
                val rc = Runtime.getRuntime().exec(arrayOf("su", "-c", cmd)).waitFor()
                if (rc != 0) return "ERROR : copy to $NMAP_DIR failed (exit $rc)"
            }
            createMissingConfigFiles()
            NMAP_DIR
        } catch (e: Exception) {
            "ERROR : ${e.message}"
        }
    }

    /**
     * Copie récursivement tout le contenu d'un dossier d'assets vers un dossier de destination
     */
    private fun copyAssetsRecursively(context: Context, assetPath: String, destDir: File) {
        try {
            val assets = context.assets.list(assetPath)
            //Log.d("NMAP_COPY", "Asset path: $assetPath, assets: ${assets?.joinToString(", ") ?: "null"}")

            if (assets == null || assets.isEmpty()) {
                //Log.d("NMAP_COPY", "Copie du fichier $assetPath vers ${destDir.absolutePath}")
                val success = copyAssetToFile(context, assetPath, destDir)
                Log.d("NMAP_COPY", "Résultat de la copie de $assetPath : $success")
            } else {
                //Log.d("NMAP_COPY", "Création du dossier ${destDir.absolutePath}")
                destDir.mkdirs()
                for (item in assets) {
                    val newAssetPath = if (assetPath.isEmpty()) item else "$assetPath/$item"
                    val newDestFile = File(destDir, item)
                    //Log.d("NMAP_COPY", "Traitement de $newAssetPath vers ${newDestFile.absolutePath}")
                    copyAssetsRecursively(context, newAssetPath, newDestFile)
                }
            }
        } catch (e: Exception) {
            //Log.e("NMAP_COPY", "Erreur lors de la copie de $assetPath : ${e.message}")
        }
    }



    /**
     * Crée les fichiers de configuration manquants pour éviter les warnings Termux
     */
    private fun createMissingConfigFiles() {
        try {
            val resolvContent = """
                nameserver 8.8.8.8
                nameserver 8.8.4.4
            """.trimIndent()

            val resolvCmd = "printf '%s\\n' ${ShellArgs.quote(resolvContent)} > ${ShellArgs.quote("$NMAP_DIR/resolv.conf")}"
            Runtime.getRuntime().exec(arrayOf("su", "-c", resolvCmd)).waitFor()
            //Log.d("NMAP_COPY", "Fichier resolv.conf créé")

            val nsswitchContent = """
                hosts:      files dns
                networks:   files dns
                services:   files
                protocols:  files
                rpc:        files
            """.trimIndent()

            val nsswitchCmd = "printf '%s\\n' ${ShellArgs.quote(nsswitchContent)} > ${ShellArgs.quote("$NMAP_DIR/nsswitch.conf")}"
            Runtime.getRuntime().exec(arrayOf("su", "-c", nsswitchCmd)).waitFor()
            //Log.d("NMAP_COPY", "Fichier nsswitch.conf créé")

        } catch (e: Exception) {
            //Log.w("NMAP_COPY", "Erreur lors de la création des fichiers de config : ${e.message}")
        }
    }

    /**
     * Supprime récursivement un dossier et son contenu
     */
    private fun deleteRecursively(file: File) {
        if (file.isDirectory) {
            file.listFiles()?.forEach { deleteRecursively(it) }
        }
        file.delete()
    }
    /**
     * Lance une commande ping vers l'adresse ou nom d'hote fourni
     *
     * Recupere la sortie de la commande et met a jour les résultats
     *
     * @param host adresse IP ou nom d hote a tester
     */
    fun ping(host: String) {
        viewModelScope.launch(Dispatchers.IO) {
            _isScanning.value = true
            try {
                val output = StringBuilder()
                output.append("ping $host\n\n")

                val startTime = System.currentTimeMillis()
                val address = InetAddress.getByName(host)
                val isReachable = address.isReachable(5000)
                val endTime = System.currentTimeMillis()
                val responseTime = endTime - startTime

                output.append("Address: ${address.hostAddress}\n")

                if (isReachable) {
                    output.append("Response time: ${responseTime}ms\n")
                } else {
                    output.append("Host unreachable\n")
                }

                _results.value = _results.value + NetworkResult(
                    command = "ping $host",
                    output = output.toString(),
                    isError = !isReachable
                )
            } catch (e: Exception) {
                _results.value = _results.value + NetworkResult(
                    command = "ping $host",
                    output = "Error: ${e.message}",
                    isError = true
                )
            } finally {
                _isScanning.value = false
            }
        }
    }

    /**
     * Scan les ports entre startPort et endPort sur l'hote fourni
     *
     * Detecte les ports ouverts et met a jour les résultats
     *
     * @param host adresse IP ou nom d hote a scanner
     * @param startPort port de debut de plage
     * @param endPort port de fin de plage
     */
    fun scanPorts(host: String, startPort: Int, endPort: Int) {
        if (startPort !in 1..65535 || endPort !in 1..65535 || startPort > endPort) {
            _results.update { it + NetworkResult("Port scan $host", "Invalid port range: use 1-65535 with start <= end.", isError = true) }
            return
        }
        if (endPort - startPort + 1 > MAX_PORTS_PER_SCAN) {
            _results.update { it + NetworkResult("Port scan $host", "Range too large (max $MAX_PORTS_PER_SCAN ports per scan; a filtered range takes ~0.4 s per port).", isError = true) }
            return
        }
        viewModelScope.launch(Dispatchers.IO) {
            _isScanning.value = true
            val openPorts = mutableListOf<Int>()

            try {
                // Résolution unique (auparavant refaite pour chaque port).
                val address = InetAddress.getByName(host)
                for (port in startPort..endPort) {
                    try {
                        // Timeout de connexion explicite : sans lui, un port filtré peut
                        // bloquer le scan très longtemps (timeout TCP par défaut de l'OS).
                        Socket().use { sock ->
                            sock.connect(InetSocketAddress(address, port), 400)
                            openPorts.add(port)
                        }
                    } catch (e: Exception) {
                        // Port is closed or unreachable
                    }
                }

                val output = if (openPorts.isEmpty()) {
                    "No open ports found in range $startPort-$endPort"
                } else {
                    "Open ports: ${openPorts.joinToString(", ")}"
                }

                _results.value = _results.value + NetworkResult(
                    command = "Port scan $host:$startPort-$endPort",
                    output = output
                )
            } catch (e: Exception) {
                _results.value = _results.value + NetworkResult(
                    command = "Port scan $host",
                    output = e.message ?: "Error during port scan",
                    isError = true
                )
            } finally {
                _isScanning.value = false
            }
        }
    }
    /**
     * Effectue une recherche DNS pour l'hote fourni
     *
     * Recupere les adresses associees et met a jour les résultats
     *
     * @param host nom d'hôte a rechercher
     */
    fun dnsLookup(host: String) {
        viewModelScope.launch(Dispatchers.IO) {
            _isScanning.value = true
            try {
                val addresses = InetAddress.getAllByName(host)
                val output = StringBuilder()
                output.append("DNS lookup results for $host:\n")
                addresses.forEach { address ->
                    output.append("${address.hostName} -> ${address.hostAddress}\n")
                }

                _results.value = _results.value + NetworkResult(
                    command = "DNS lookup $host",
                    output = output.toString()
                )
            } catch (e: UnknownHostException) {
                _results.value = _results.value + NetworkResult(
                    command = "DNS lookup $host",
                    output = "Host not found: $host",
                    isError = true
                )
            } catch (e: Exception) {
                _results.value = _results.value + NetworkResult(
                    command = "DNS lookup $host",
                    output = e.message ?: "Error during DNS lookup",
                    isError = true
                )
            } finally {
                _isScanning.value = false
            }
        }
    }
    /**
     * Lance une commande traceroute vers l hote fourni
     *
     * Recupere la sortie et met a jour les résultats
     *
     * @param host adresse IP ou nom d'hôte cible
     */
    fun traceroute(host: String) {
        viewModelScope.launch(Dispatchers.IO) {
            _isScanning.value = true
            // Sans socket ICMP brute (root), Android ne permet pas de faire varier le TTL :
            // ceci n'est PAS un traceroute, seulement un test d'accessibilité TCP de la cible.
            val title = "TCP reachability $host"
            try {
                val output = StringBuilder()
                output.append("Note: not a hop-by-hop traceroute (needs raw ICMP / root).\n")
                output.append("Testing TCP reachability of the target on common ports.\n\n")
                val targetAddress = InetAddress.getByName(host)
                output.append("Target address: ${targetAddress.hostAddress}\n\n")
                var reachable = false
                for (port in listOf(80, 443, 22, 53, 21, 25, 110, 143, 993, 995)) {
                    val start = System.currentTimeMillis()
                    try {
                        Socket().use { socket ->
                            socket.connect(InetSocketAddress(targetAddress, port), 2000)
                            output.append("port $port: OPEN (${System.currentTimeMillis() - start} ms)\n")
                        }
                        reachable = true
                        break
                    } catch (e: java.net.ConnectException) {
                        // RST reçu : l'hôte répond, le port est fermé.
                        output.append("port $port: CLOSED — host answered (${System.currentTimeMillis() - start} ms)\n")
                        reachable = true
                        break
                    } catch (e: java.net.SocketTimeoutException) {
                        output.append("port $port: no answer (timeout)\n")
                    } catch (e: Exception) {
                        output.append("port $port: error (${e.message})\n")
                    }
                }
                output.append(if (reachable) "\nHost reachable over TCP\n" else "\nNo TCP answer on tested ports (host down or filtered)\n")
                _results.update { it + NetworkResult(title, output.toString(), isError = !reachable) }
            } catch (e: Exception) {
                _results.update { it + NetworkResult(title, "Error: ${e.message}", isError = true) }
            } finally {
                _isScanning.value = false
            }
        }
    }

    /**
     * Execute une commande nmap avec les options choisies sur l'hôte fourni
     *
     * Recupere la sortie et met a jour les résultats
     *
     * @param host adresse IP ou nom d'hôte cible
     * @param parameters les options choisies par l'utilisateur (ex: -sV -T4)
     */


    fun nmap(host: String, parameters: String) {
        val target = host.trim()
        if (!ShellArgs.isValidTarget(target)) {
            _results.update { it + NetworkResult("nmap", "Invalid target: use an IPv4/IPv6 address, CIDR or hostname.", isError = true) }
            return
        }
        val opts = ShellArgs.splitSafeOptions(parameters)
        if (opts == null) {
            _results.update { it + NetworkResult("nmap", "Rejected options: shell characters, output/input files (-o*, -i*) and --script are not allowed.", isError = true) }
            return
        }
        if (_isScanning.value) return
        _isScanning.value = true
        viewModelScope.launch(Dispatchers.IO) {
            val display = "nmap ${opts.ifEmpty { listOf("-sn") }.joinToString(" ")} $target"
            try {
                if (!hasRoot()) {
                    _results.update { it + NetworkResult(display, "Root access required to run the bundled nmap.", isError = true) }
                    return@launch
                }
                val ctx = context ?: throw Exception("Context not initialized")
                val prep = copyNmapToTmp(ctx)
                if (prep.startsWith("ERROR")) {
                    _results.update { it + NetworkResult(display, prep, isError = true) }
                    return@launch
                }
                val dir = NMAP_DIR
                val args = ShellArgs.join(opts.ifEmpty { listOf("-sn") } + target)
                // exec : le shell su est remplacé par nmap ; chemin absolu pour pouvoir le tuer.
                val fullCommand = "cd ${ShellArgs.quote(dir)} && LD_LIBRARY_PATH=${ShellArgs.quote(dir)} " +
                    "RESOLV_CONF=${ShellArgs.quote("$dir/resolv.conf")} NSSWITCH_CONF=${ShellArgs.quote("$dir/nsswitch.conf")} " +
                    "exec ${ShellArgs.quote("$dir/nmap")} $args"

                val process = Runtime.getRuntime().exec(arrayOf("su", "-c", fullCommand))
                // StringBuffer (synchronisé) : stdout et stderr sont lus par deux threads.
                val output = StringBuffer()
                val outputThread = Thread {
                    try { process.inputStream.bufferedReader().forEachLine { output.append(it).append("\n") } } catch (_: Exception) {}
                }.apply { isDaemon = true; start() }
                val errorThread = Thread {
                    try { process.errorStream.bufferedReader().forEachLine { output.append("ERR: ").append(it).append("\n") } } catch (_: Exception) {}
                }.apply { isDaemon = true; start() }

                // Process.waitFor(timeout, unit) n'existe qu'en API 26 : attente active bornée.
                val deadline = System.currentTimeMillis() + NMAP_TIMEOUT_MS
                var exitCode: Int? = null
                while (exitCode == null && System.currentTimeMillis() < deadline) {
                    try { exitCode = process.exitValue() } catch (_: IllegalThreadStateException) { Thread.sleep(200) }
                }
                if (exitCode == null) {
                    // Tuer nmap lui-même (le détruire via le client su ne suffit pas toujours).
                    try { Runtime.getRuntime().exec(arrayOf("su", "-c", "pkill -f ${ShellArgs.quote("$dir/nmap")}")).waitFor() } catch (_: Exception) {}
                    process.destroy()
                    output.append("\n[Timeout after ${NMAP_TIMEOUT_MS / 1000} s — scan killed]\n")
                }
                outputThread.join(2000)
                errorThread.join(2000)
                if (output.isBlank()) output.append("No output from nmap.\n")
                _results.update { it + NetworkResult(display, output.toString(), isError = exitCode != 0) }
            } catch (e: Exception) {
                _results.update { it + NetworkResult(display, e.message ?: "Error during nmap execution", isError = true) }
            } finally {
                _isScanning.value = false
            }
        }
    }

    private fun hasRoot(): Boolean = try {
        val p = Runtime.getRuntime().exec(arrayOf("su", "-c", "id"))
        val out = p.inputStream.bufferedReader().readText()
        p.waitFor()
        out.contains("uid=0")
    } catch (_: Exception) { false }

    /**
     * Wake-on-LAN : envoie un "magic packet" à l'adresse MAC fournie (broadcast UDP 9).
     */
    fun wakeOnLan(mac: String) {
        viewModelScope.launch(Dispatchers.IO) {
            _isScanning.value = true
            try {
                val clean = mac.replace("[:\\-\\s]".toRegex(), "")
                if (clean.length != 12) throw IllegalArgumentException("MAC invalide")
                val macBytes = ByteArray(6) { clean.substring(it * 2, it * 2 + 2).toInt(16).toByte() }
                val packet = ByteArray(6 + 16 * 6)
                for (i in 0 until 6) packet[i] = 0xFF.toByte()
                for (i in 0 until 16) System.arraycopy(macBytes, 0, packet, 6 + i * 6, 6)
                val address = java.net.InetAddress.getByName("255.255.255.255")
                java.net.DatagramSocket().use { socket ->
                    socket.broadcast = true
                    socket.send(java.net.DatagramPacket(packet, packet.size, address, 9))
                }
                _results.value = _results.value + NetworkResult("Wake-on-LAN $mac", "Magic packet sent to $mac")
            } catch (e: Exception) {
                _results.value = _results.value + NetworkResult("Wake-on-LAN $mac", "Error: ${e.message}", true)
            } finally {
                _isScanning.value = false
            }
        }
    }

    /**
     * Lit la table ARP du noyau (/proc/net/arp) : voisins IP/MAC sur le réseau local.
     */
    fun arpTable() {
        viewModelScope.launch(Dispatchers.IO) {
            _isScanning.value = true
            try {
                val output = StringBuilder("Neighbors (IP -> MAC):\n\n")
                val lines = File("/proc/net/arp").readLines()
                lines.drop(1).forEach { line ->
                    val cols = line.trim().split(Regex("\\s+"))
                    if (cols.size >= 4 && cols[3] != "00:00:00:00:00:00") {
                        output.append("${cols[0]} -> ${cols[3]}\n")
                    }
                }
                _results.value = _results.value + NetworkResult("ARP table", output.toString())
            } catch (e: Exception) {
                _results.value = _results.value + NetworkResult("ARP table", "Error: ${e.message}", true)
            } finally {
                _isScanning.value = false
            }
        }
    }

    /**
     * Balayage de sous-réseau : ping les hôtes 1..254 du /24 dérivé de l'IP fournie.
     */
    fun pingSweep(baseIp: String) {
        viewModelScope.launch(Dispatchers.IO) {
            _isScanning.value = true
            try {
                val prefix = baseIp.substringBeforeLast('.', "")
                if (prefix.isEmpty() || !baseIp.contains('.')) throw IllegalArgumentException("IP invalide")
                val alive = java.util.Collections.synchronizedList(mutableListOf<String>())
                val threads = (1..254).map { host ->
                    Thread {
                        val ip = "$prefix.$host"
                        try {
                            if (InetAddress.getByName(ip).isReachable(300)) alive.add(ip)
                        } catch (_: Exception) {}
                    }.apply { start() }
                }
                threads.forEach { it.join(1000) }
                val sorted = alive.sortedBy { it.substringAfterLast('.').toIntOrNull() ?: 0 }
                val output = if (sorted.isEmpty()) "No hosts responded in $prefix.0/24"
                    else "Alive hosts (${sorted.size}):\n" + sorted.joinToString("\n")
                _results.value = _results.value + NetworkResult("Ping sweep $prefix.0/24", output)
            } catch (e: Exception) {
                _results.value = _results.value + NetworkResult("Ping sweep", "Error: ${e.message}", true)
            } finally {
                _isScanning.value = false
            }
        }
    }

    /**
     * Vide la liste des résultats precedents
     */
    fun clearResults() {
        _results.value = emptyList()
    }
}