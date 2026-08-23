package com.example.flipperdroid.viewmodel

import android.app.Application
import android.nfc.Tag
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import com.example.flipperdroid.nfc.MifareClassicUtils
import java.io.File
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

class NfcViewModel(app: Application) : AndroidViewModel(app) {

    // État du tag courant
    private val _currentTagUid = MutableStateFlow<String?>(null)
    val currentTagUid: StateFlow<String?> = _currentTagUid.asStateFlow()

    private val _currentTagType = MutableStateFlow<String?>(null)
    val currentTagType: StateFlow<String?> = _currentTagType.asStateFlow()

    private val _currentTagDump = MutableStateFlow<List<String>>(emptyList())
    val currentTagDump: StateFlow<List<String>> = _currentTagDump.asStateFlow()

    // Historique des scans
    private val _scanHistory = MutableStateFlow<List<NfcScanResult>>(emptyList())
    val scanHistory: StateFlow<List<NfcScanResult>> = _scanHistory.asStateFlow()

    // Logs/feedback
    private val _logs = MutableStateFlow<List<String>>(emptyList())
    val logs: StateFlow<List<String>> = _logs.asStateFlow()

    // Contenu NDEF lu (texte/URI)
    private val _ndefContent = MutableStateFlow<String?>(null)
    val ndefContent: StateFlow<String?> = _ndefContent.asStateFlow()

    // Clés trouvées par l'attaque par dictionnaire
    private val _foundKeys = MutableStateFlow<List<String>>(emptyList())
    val foundKeys: StateFlow<List<String>> = _foundKeys.asStateFlow()

    private val _isAttacking = MutableStateFlow(false)
    val isAttacking: StateFlow<Boolean> = _isAttacking.asStateFlow()

    // Dernier tag scanné (nécessaire pour l'attaque, le clone, le NDEF)
    private var lastTag: Tag? = null

    // Actions principales.
    // La lecture NFC (connect/authenticate/readBlock) est bloquante : on l'exécute
    // sur un thread d'IO pour ne pas bloquer le thread principal (risque d'ANR).
    fun onTagScanned(tag: Tag) {
        lastTag = tag
        _foundKeys.value = emptyList()
        _ndefContent.value = null
        // Lecture UID (rapide, ne nécessite pas de connexion)
        val id = tag.id?.let { MifareClassicUtils.bytesToHex(it) } ?: "-"
        _currentTagUid.value = id

        // Lecture NDEF si le tag la supporte (texte / URI)
        readNdef(tag)

        val mfc = android.nfc.tech.MifareClassic.get(tag)
        if (mfc == null) {
            // Tag non-Mifare (ex: carte EMV IsoDep) : traité par un autre lecteur.
            addLog("Tag non compatible MifareClassic")
            return
        }

        _currentTagType.value = "Mifare Classic"
        viewModelScope.launch(Dispatchers.IO) {
            val dump = mutableListOf<String>()
            try {
                mfc.connect()
                val sectorCount = mfc.sectorCount
                for (sector in 0 until sectorCount) {
                    val key = MifareClassicUtils.hexToBytes(MifareClassicUtils.DEFAULT_KEY) ?: continue
                    val auth = mfc.authenticateSectorWithKeyA(sector, key)
                    if (auth) {
                        val firstBlock = mfc.sectorToBlock(sector)
                        val blockCount = mfc.getBlockCountInSector(sector)
                        for (i in 0 until blockCount) {
                            val blockBytes = try { mfc.readBlock(firstBlock + i) } catch (_: Exception) { null }
                            dump.add(blockBytes?.let { MifareClassicUtils.bytesToHex(it) } ?: MifareClassicUtils.NO_DATA)
                        }
                    } else {
                        // Secteur non accessible avec la clé par défaut
                        val blockCount = mfc.getBlockCountInSector(sector)
                        repeat(blockCount) { dump.add(MifareClassicUtils.NO_DATA) }
                    }
                }
            } catch (e: Exception) {
                addLog("Erreur lecture tag: ${e.message}")
            } finally {
                try { mfc.close() } catch (_: Exception) {}
            }

            // Mise à jour des états et de l'historique (StateFlow est thread-safe)
            _currentTagDump.value = dump
            val timestamp = SimpleDateFormat("yyyy-MM-dd HH:mm:ss", Locale.getDefault()).format(Date())
            val scan = NfcScanResult(timestamp, id, "Mifare Classic", dump)
            _scanHistory.value = _scanHistory.value + scan
            addLog("Tag scanné UID=$id, ${dump.size} blocks lus")
        }
    }
    /**
     * Lit un message NDEF (texte/URI) si le tag le supporte.
     */
    private fun readNdef(tag: Tag) {
        viewModelScope.launch(Dispatchers.IO) {
            try {
                val ndef = android.nfc.tech.Ndef.get(tag) ?: return@launch
                ndef.connect()
                val message = ndef.ndefMessage ?: ndef.cachedNdefMessage
                ndef.close()
                if (message != null) {
                    val text = message.records.joinToString("\n") { record ->
                        val payload = record.payload
                        when {
                            record.toUri() != null -> "URI: ${record.toUri()}"
                            payload.isNotEmpty() -> {
                                // Enregistrement Texte NDEF : 1er octet = statut (longueur du code langue)
                                val langLen = payload[0].toInt() and 0x3F
                                if (payload.size > langLen + 1) {
                                    "Text: ${String(payload, langLen + 1, payload.size - langLen - 1, Charsets.UTF_8)}"
                                } else String(payload, Charsets.UTF_8)
                            }
                            else -> "(empty record)"
                        }
                    }
                    _ndefContent.value = text
                    addLog("NDEF lu (${message.records.size} enregistrement(s))")
                }
            } catch (e: Exception) {
                // Tag non NDEF : silencieux
            }
        }
    }

    /**
     * Écrit un message NDEF (texte ou URI auto-détectée) sur le dernier tag scanné.
     * Formate le tag si nécessaire (NdefFormatable).
     */
    fun writeNdef(content: String) {
        val tag = lastTag ?: return addLog("Aucun tag scanné")
        if (content.isBlank()) return addLog("Contenu NDEF vide")
        viewModelScope.launch(Dispatchers.IO) {
            try {
                val record = if (content.startsWith("http://", true) || content.startsWith("https://", true)) {
                    android.nfc.NdefRecord.createUri(content)
                } else {
                    android.nfc.NdefRecord.createTextRecord("en", content)
                }
                val message = android.nfc.NdefMessage(arrayOf(record))
                val ndef = android.nfc.tech.Ndef.get(tag)
                if (ndef != null) {
                    ndef.connect()
                    when {
                        !ndef.isWritable -> addLog("Tag NDEF non inscriptible")
                        ndef.maxSize < message.toByteArray().size -> addLog("Message trop grand (${message.toByteArray().size} > ${ndef.maxSize})")
                        else -> {
                            ndef.writeNdefMessage(message)
                            _ndefContent.value = "Écrit: $content"
                            addLog("NDEF écrit: $content")
                        }
                    }
                    try { ndef.close() } catch (_: Exception) {}
                } else {
                    val formatable = android.nfc.tech.NdefFormatable.get(tag)
                    if (formatable != null) {
                        formatable.connect()
                        formatable.format(message)
                        try { formatable.close() } catch (_: Exception) {}
                        _ndefContent.value = "Écrit: $content"
                        addLog("Tag formaté + NDEF écrit: $content")
                    } else {
                        addLog("Tag non compatible NDEF")
                    }
                }
            } catch (e: Exception) {
                addLog("Erreur écriture NDEF: ${e.message}")
            }
        }
    }

    /**
     * Attaque par dictionnaire : teste les clés connues (MifareClassicUtils.COMMON_KEYS)
     * sur chaque secteur et lit ceux dont une clé est trouvée.
     */
    fun runDictionaryAttack() {
        val tag = lastTag ?: return addLog("Aucun tag en mémoire")
        val mfc = android.nfc.tech.MifareClassic.get(tag) ?: return addLog("Tag non Mifare Classic")
        _isAttacking.value = true
        viewModelScope.launch(Dispatchers.IO) {
            val found = mutableListOf<String>()
            val dump = mutableListOf<String>()
            try {
                mfc.connect()
                for (sector in 0 until mfc.sectorCount) {
                    var authenticated = false
                    var label = ""
                    for (k in MifareClassicUtils.COMMON_KEYS) {
                        val kb = MifareClassicUtils.hexToBytes(k) ?: continue
                        try {
                            if (mfc.authenticateSectorWithKeyA(sector, kb)) { authenticated = true; label = "KeyA:$k"; break }
                            if (mfc.authenticateSectorWithKeyB(sector, kb)) { authenticated = true; label = "KeyB:$k"; break }
                        } catch (e: Exception) {
                            // Certaines puces coupent la connexion après un échec : on la rétablit.
                            try { mfc.close(); mfc.connect() } catch (_: Exception) {}
                        }
                    }
                    if (authenticated) {
                        found.add("Sector $sector -> $label")
                        val firstBlock = mfc.sectorToBlock(sector)
                        val blockCount = mfc.getBlockCountInSector(sector)
                        for (i in 0 until blockCount) {
                            val bb = try { mfc.readBlock(firstBlock + i) } catch (_: Exception) { null }
                            dump.add(bb?.let { MifareClassicUtils.bytesToHex(it) } ?: MifareClassicUtils.NO_DATA)
                        }
                    } else {
                        found.add("Sector $sector -> no key found")
                        repeat(mfc.getBlockCountInSector(sector)) { dump.add(MifareClassicUtils.NO_DATA) }
                    }
                }
            } catch (e: Exception) {
                addLog("Erreur attaque: ${e.message}")
            } finally {
                try { mfc.close() } catch (_: Exception) {}
            }
            _foundKeys.value = found
            _currentTagDump.value = dump
            _isAttacking.value = false
            val cracked = found.count { !it.contains("no key") }
            addLog("Attaque dictionnaire terminée : $cracked/${found.size} secteurs cassés")
        }
    }

    /**
     * Clone l'UID (block 0) sur une carte "magic" (générique). Utilise le
     * dernier tag scanné, présent tant que la carte est sur le lecteur.
     */
    fun onCloneUid(newUid: String) {
        val tag = lastTag ?: return addLog("Aucun tag scanné")
        val bytes = MifareClassicUtils.hexToBytes(newUid.replace(" ", ""))
            ?: return addLog("UID invalide (hexadécimal attendu)")
        viewModelScope.launch(Dispatchers.IO) {
            val ok = MifareClassicUtils.cloneUid(tag, bytes)
            withContext(Dispatchers.Main) {
                addLog(if (ok) "UID cloné avec succès (carte magic)" else "Echec du clonage (carte non-magic ?)")
            }
        }
    }
    fun onDumpExport() {
        val dump = _currentTagDump.value
        if (dump.isEmpty()) {
            addLog("Aucun dump à exporter")
            return
        }
        val uid = _currentTagUid.value ?: "unknown"
        viewModelScope.launch(Dispatchers.IO) {
            try {
                val dir = getApplication<Application>().getExternalFilesDir("nfc_dumps")
                    ?: getApplication<Application>().filesDir
                val timestamp = SimpleDateFormat("yyyyMMdd_HHmmss", Locale.getDefault()).format(Date())
                val file = File(dir, "dump_${uid}_$timestamp.txt")
                file.writeText(dump.joinToString("\n"))
                withContext(Dispatchers.Main) { addLog("Dump exporté : ${file.absolutePath}") }
            } catch (e: Exception) {
                withContext(Dispatchers.Main) { addLog("Erreur export dump : ${e.message}") }
            }
        }
    }
    /** Sauvegarde le dump courant au format .nfc du Flipper Zero. */
    fun saveAsNfc() {
        val dump = _currentTagDump.value
        val uid = _currentTagUid.value
        if (dump.isEmpty() || uid == null) { addLog("Rien à sauvegarder en .nfc"); return }
        viewModelScope.launch(Dispatchers.IO) {
            try {
                val content = com.example.flipperdroid.flipper.FlipperNfc.generate(uid.replace(" ", ""), dump)
                val dir = getApplication<Application>().getExternalFilesDir("nfc_dumps")
                    ?: getApplication<Application>().filesDir
                val ts = SimpleDateFormat("yyyyMMdd_HHmmss", Locale.getDefault()).format(Date())
                val file = File(dir, "card_${uid}_$ts.nfc")
                file.writeText(content)
                withContext(Dispatchers.Main) { addLog("Fichier .nfc sauvegardé : ${file.absolutePath}") }
            } catch (e: Exception) {
                withContext(Dispatchers.Main) { addLog("Erreur .nfc : ${e.message}") }
            }
        }
    }

    fun clearLogs() {
        _logs.value = emptyList()
    }
    fun addLog(msg: String) {
        _logs.value = _logs.value + msg
    }
}

// Modèle pour l’historique
data class NfcScanResult(
    val timestamp: String,
    val uid: String?,
    val type: String?,
    val dump: List<String>
)