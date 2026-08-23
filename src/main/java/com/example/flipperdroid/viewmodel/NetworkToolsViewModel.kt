package com.example.flipperdroid.viewmodel

import android.annotation.SuppressLint
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
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
        val nmapDir = File("/data/local/tmp")
        val internalNmapDir = File(context.filesDir, "nmap")

        try {
            // Suppression du dossier
            if (internalNmapDir.exists()) {
                Log.d("NMAP_COPY", "Suppression de l'ancien dossier nmap dans ${internalNmapDir.absolutePath}")
                deleteRecursively(internalNmapDir)
            }

            // Créer le dossier interne
            internalNmapDir.mkdirs()

            Log.d("NMAP_COPY", "Copie depuis assets/nmap/ vers ${internalNmapDir.absolutePath}")
            copyAssetsRecursively(context, "nmap", internalNmapDir)

            // copie ver /data/local/tmp avec root
            val copyCmd = "cp -r ${internalNmapDir.absolutePath}/* /data/local/tmp/"
            val chmodCmd = "chmod -R 755 /data/local/tmp"

            Log.d("NMAP_COPY", "Commande de copie : $copyCmd")
            val copyResult = Runtime.getRuntime().exec(arrayOf("su", "-c", copyCmd)).waitFor()
            Log.d("NMAP_COPY", "Résultat de la copie : $copyResult")

            Log.d("NMAP_COPY", "Commande chmod : $chmodCmd")
            val chmodResult = Runtime.getRuntime().exec(arrayOf("su", "-c", chmodCmd)).waitFor()
            Log.d("NMAP_COPY", "Résultat du chmod : $chmodResult")


            createMissingConfigFiles()

            if (!File("/data/local/tmp/nmap").exists()) throw Exception("Le binaire nmap n'a pas été copié dans /data/local/tmp/")

            return nmapDir.absolutePath
        } catch (e: Exception) {
            //Log.e("NMAP_COPY", "Erreur : ${e.message}")
            return "ERROR : ${e.message}"
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

            val resolvCmd = "echo '$resolvContent' > /data/local/tmp/resolv.conf"
            Runtime.getRuntime().exec(arrayOf("su", "-c", resolvCmd)).waitFor()
            //Log.d("NMAP_COPY", "Fichier resolv.conf créé")

            val nsswitchContent = """
                hosts:      files dns
                networks:   files dns
                services:   files
                protocols:  files
                rpc:        files
            """.trimIndent()

            val nsswitchCmd = "echo '$nsswitchContent' > /data/local/tmp/nsswitch.conf"
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
        viewModelScope.launch(Dispatchers.IO) {
            _isScanning.value = true
            val openPorts = mutableListOf<Int>()

            try {
                for (port in startPort..endPort) {
                    try {
                        // Timeout de connexion explicite : sans lui, un port filtré peut
                        // bloquer le scan très longtemps (timeout TCP par défaut de l'OS).
                        Socket().use { sock ->
                            sock.connect(InetSocketAddress(host, port), 400)
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
            try {
                val output = StringBuilder()
                output.append("Traceroute to $host\n\n")

                val targetAddress = InetAddress.getByName(host)
                output.append("Target address: ${targetAddress.hostAddress}\n\n")

                // Special handling for localhost
                if (targetAddress.hostAddress == "127.0.0.1" || host.lowercase() == "localhost") {
                    output.append("Testing localhost connectivity...\n\n")
                    
                    try {
                        val socket = Socket()
                        socket.setSoTimeout(1000)
                        
                        val startTime = System.currentTimeMillis()
                        socket.connect(InetSocketAddress("127.0.0.1", 8080), 1000)
                        val endTime = System.currentTimeMillis()
                        
                        output.append("1: 127.0.0.1 (localhost) (${endTime - startTime}ms) [ACCESSIBLE]\n")
                        socket.close()
                        
                        _results.value = _results.value + NetworkResult(
                            command = "traceroute $host",
                            output = output.toString(),
                            isError = false
                        )
                    } catch (e: Exception) {
                        output.append("1: 127.0.0.1 (localhost) [ACCESSIBLE]\n")

                        _results.value = _results.value + NetworkResult(
                            command = "traceroute $host",
                            output = output.toString(),
                            isError = false
                        )
                    }
                }

                val commonPorts = listOf(80, 443, 22, 21, 25, 53, 110, 143, 993, 995)
                var reachedTarget = false
                var hopCount = 0

                for (port in commonPorts) {
                    hopCount++
                    try {
                        val socket = Socket()
                        socket.setSoTimeout(3000)
                        
                        val startTime = System.currentTimeMillis()
                        
                        // Tentative de connexion au port
                        socket.connect(InetSocketAddress(targetAddress, port), 3000)
                        
                        val endTime = System.currentTimeMillis()
                        val responseTime = endTime - startTime
                        
                        if (socket.isConnected) {
                            output.append("$hopCount: ${socket.localAddress.hostAddress} -> ${targetAddress.hostAddress}:$port (${responseTime}ms) [OPEN]\n")
                            reachedTarget = true
                            socket.close()
                            break
                        }
                        
                        socket.close()
                        
                    } catch (e: java.net.ConnectException) {
                        // Port ferme mais accessible
                        output.append("$hopCount: ${targetAddress.hostAddress}:$port [CLOSED]\n")
                        reachedTarget = true
                        break
                    } catch (e: java.net.SocketTimeoutException) {
                        output.append("$hopCount: * * * (timeout on port $port)\n")
                    } catch (e: Exception) {
                        output.append("$hopCount: * * * (error: ${e.message})\n")
                    }
                }

                // Si aucun port commun est valide alors connexion directe
                if (!reachedTarget) {
                    try {
                        val socket = Socket()
                        socket.setSoTimeout(5000)
                        
                        val startTime = System.currentTimeMillis()
                        socket.connect(InetSocketAddress(targetAddress, 80), 5000)
                        val endTime = System.currentTimeMillis()
                        
                        if (socket.isConnected) {
                            output.append("${hopCount + 1}: ${socket.localAddress.hostAddress} -> ${targetAddress.hostAddress} (${endTime - startTime}ms) [ACCESSIBLE]\n")
                            reachedTarget = true
                            socket.close()
                        }
                        
                    } catch (e: Exception) {
                        output.append("${hopCount + 1}: * * * (host unreachable)\n")
                    }
                }

                if (!reachedTarget) {
                    output.append("Unable to reach destination\n")
                } else {
                    output.append("\nHost accessible\n")
                }

                _results.value = _results.value + NetworkResult(
                    command = "traceroute $host",
                    output = output.toString(),
                    isError = !reachedTarget
                )
            } catch (e: Exception) {
                _results.value = _results.value + NetworkResult(
                    command = "traceroute $host",
                    output = "Error: ${e.message}",
                    isError = true
                )
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
        viewModelScope.launch(Dispatchers.IO) {
            _isScanning.value = true
            try {
                val ctx = context ?: throw Exception("Contexte non initialisé")
                val nmapDir = copyNmapToTmp(ctx)
                val tmpDir = "/data/local/tmp"

                // Utiliser des options par défaut si aucun paramètre n'est fourni
                val defaultParams = if (parameters.isEmpty()) "-sn" else parameters
                val fullCommand = "cd $tmpDir/ && LD_LIBRARY_PATH=$tmpDir/ RESOLV_CONF=$tmpDir/resolv.conf NSSWITCH_CONF=$tmpDir/nsswitch.conf ./nmap $defaultParams $host"
                val command = arrayOf("su", "-c", fullCommand)

                //Log.d("NMAP_EXEC", "Commande exécutée : $fullCommand")
                //Log.d("NMAP_EXEC", "Répertoire courant : ${System.getProperty("user.dir")}")

                val process = Runtime.getRuntime().exec(command)
                val reader = BufferedReader(InputStreamReader(process.inputStream))
                val errorReader = BufferedReader(InputStreamReader(process.errorStream))
                // StringBuffer (synchronisé) : stdout et stderr sont lus par deux threads
                // concurrents ; un StringBuilder non synchronisé pourrait corrompre/crasher.
                val output = StringBuffer()

                output.append("Command executed: $fullCommand\n\n")

                // Lire la sortie standard
                val outputThread = Thread {
                    var line: String?
                    while (reader.readLine().also { line = it } != null) {
                        output.append(line).append("\n")
                    }
                }
                outputThread.start()

                // Lire la sortie d'erreur
                val errorThread = Thread {
                    var line: String?
                    while (errorReader.readLine().also { line = it } != null) {
                        output.append("ERR: $line\n")
                    }
                }
                errorThread.start()

                // Attendre la fin avec timeout
                val timeout = 30000L // 30 secondes
                val startTime = System.currentTimeMillis()
                var exitCode = -1

                while (System.currentTimeMillis() - startTime < timeout) {
                    try {
                        exitCode = process.waitFor()
                        break
                    } catch (e: InterruptedException) {
                        process.destroy()
                        output.append("\n[Timeout après ${timeout/1000} secondes]\n")
                        break
                    }
                }

                // Attendre que les threads se terminent
                outputThread.join(2000)
                errorThread.join(2000)

                // If no output, add a message
                if (output.toString().trim().isEmpty() || output.toString().trim() == "Command executed: $fullCommand") {
                    output.append("No output detected. Check that nmap is working correctly.\n")
                }

                _results.value = _results.value + NetworkResult(
                    command = fullCommand,
                    output = output.toString(),
                    isError = exitCode != 0
                )
            } catch (e: Exception) {
                val message = if (e.message?.contains("No such file") == true || e.message?.contains("not found") == true) {
                    "Error: nmap binary not found or copied. Check its presence in assets."
                } else {
                    e.message ?: "Error during nmap execution"
                }
                _results.value = _results.value + NetworkResult(
                    command = "nmap $parameters $host",
                    output = message,
                    isError = true
                )
            } finally {
                _isScanning.value = false
            }
        }
    }



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