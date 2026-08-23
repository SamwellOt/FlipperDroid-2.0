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
import com.example.flipperdroid.nfc.ApduCapture
import com.example.flipperdroid.nfc.EmulationStore
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

    // Clonage complet : quand "armé", la prochaine carte présentée reçoit le dump.
    private val _cloneArmed = MutableStateFlow(false)
    val cloneArmed: StateFlow<Boolean> = _cloneArmed.asStateFlow()
    private var pendingCloneDump: List<String>? = null
    private var cloneWriteTrailers = false
    private var cloneWriteSectorZero = true

    // Capture d'émulation HCE : quand "armé", la prochaine carte ISO-DEP est capturée.
    private val _emuCaptureArmed = MutableStateFlow(false)
    val emuCaptureArmed: StateFlow<Boolean> = _emuCaptureArmed.asStateFlow()

    // Dernier tag scanné (nécessaire pour l'attaque, le clone, le NDEF)
    private var lastTag: Tag? = null

    // Actions principales.
    // La lecture NFC (connect/authenticate/readBlock) est bloquante : on l'exécute
    // sur un thread d'IO pour ne pas bloquer le thread principal (risque d'ANR).
    fun onTagScanned(tag: Tag) {
        lastTag = tag
        // Si un clone est armé, la carte présentée est la CIBLE : on y réécrit le dump.
        if (_cloneArmed.value) { performClone(tag); return }
        // Si une capture d'émulation est armée, on enregistre le dialogue APDU de la carte.
        if (_emuCaptureArmed.value) { performEmulationCapture(tag); return }
        _foundKeys.value = emptyList()
        _ndefContent.value = null
        // Lecture UID (rapide, ne nécessite pas de connexion)
        val id = tag.id?.let { MifareClassicUtils.bytesToHex(it) } ?: "-"
        _currentTagUid.value = id
        val techs = tag.techList?.map { it.substringAfterLast('.') } ?: emptyList()
        val typeLabel = describeTag(techs)
        _currentTagType.value = typeLabel

        viewModelScope.launch(Dispatchers.IO) {
            // NDEF puis MifareClassic, séquentiellement : deux technologies NFC ne
            // peuvent pas être connectées en même temps au même tag. Les lancer en
            // parallèle provoquait des échecs de lecture intermittents.
            readNdef(tag)

            val mfc = android.nfc.tech.MifareClassic.get(tag)
            val dump = mutableListOf<String>()
            if (mfc != null) {
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
            } else if (techs.any { it.equals("MifareClassic", true) }) {
                // Carte Mifare Classic présente mais la puce NFC de l'appareil ne la gère pas
                // (fréquent hors puces NXP — ex. certains Pixel).
                addLog("Mifare Classic détecté, mais non supporté par la puce NFC de cet appareil.")
            } else {
                addLog("Tag lu (UID=$id) — type : $typeLabel. Lecture Mifare non applicable.")
            }

            // Historique + dump pour TOUT tag scanné, pas seulement Mifare Classic.
            _currentTagDump.value = dump
            val timestamp = SimpleDateFormat("yyyy-MM-dd HH:mm:ss", Locale.getDefault()).format(Date())
            _scanHistory.value = _scanHistory.value + NfcScanResult(timestamp, id, typeLabel, dump)
            addLog("Tag scanné UID=$id ($typeLabel)" + if (dump.isNotEmpty()) ", ${dump.size} blocks lus" else "")
        }
    }

    /** Étiquette lisible du type de tag à partir de sa liste de technologies. */
    private fun describeTag(techs: List<String>): String = when {
        techs.any { it.equals("MifareClassic", true) } -> "Mifare Classic"
        techs.any { it.equals("MifareUltralight", true) } -> "Mifare Ultralight"
        techs.any { it.equals("IsoDep", true) } -> "ISO-DEP (EMV / DESFire)"
        techs.any { it.equals("NfcA", true) } -> "NFC-A (ISO 14443-3A)"
        techs.any { it.equals("NfcB", true) } -> "NFC-B (ISO 14443-3B)"
        techs.any { it.equals("NfcF", true) } -> "NFC-F (FeliCa)"
        techs.any { it.equals("NfcV", true) } -> "NFC-V (ISO 15693)"
        techs.isNotEmpty() -> techs.joinToString(", ")
        else -> "Unknown"
    }
    /**
     * Lit un message NDEF (texte/URI) si le tag le supporte.
     * Appelée depuis la coroutine IO de [onTagScanned] : bloquante et non relancée,
     * pour ne pas connecter Ndef et MifareClassic simultanément au même tag.
     */
    private fun readNdef(tag: Tag) {
        try {
            val ndef = android.nfc.tech.Ndef.get(tag) ?: return
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
     * Prépare la reproduction : capture le dump courant, puis attend qu'on présente
     * la carte CIBLE (magic / vierge Mifare Classic) pour y réécrire tous les blocs.
     */
    fun armClone(writeTrailers: Boolean = false, writeSectorZero: Boolean = true) {
        val dump = _currentTagDump.value
        if (dump.isEmpty() || dump.all { it == MifareClassicUtils.NO_DATA }) {
            addLog("Aucun dump lisible à cloner (lisez d'abord une carte Mifare Classic).")
            return
        }
        pendingCloneDump = dump
        cloneWriteTrailers = writeTrailers
        cloneWriteSectorZero = writeSectorZero
        _cloneArmed.value = true
        addLog("Clone armé : présentez maintenant la carte cible (magic/vierge).")
    }

    /** Annule un clonage armé. */
    fun cancelClone() {
        pendingCloneDump = null
        _cloneArmed.value = false
        addLog("Clonage annulé.")
    }

    /** Écrit le dump en attente sur la carte cible présentée. */
    private fun performClone(tag: Tag) {
        val dump = pendingCloneDump
        if (dump.isNullOrEmpty()) { _cloneArmed.value = false; addLog("Aucun dump à cloner."); return }
        viewModelScope.launch(Dispatchers.IO) {
            addLog("Écriture du dump sur la carte cible…")
            val res = MifareClassicUtils.writeDump(tag, dump, cloneWriteTrailers, cloneWriteSectorZero)
            addLog("Reproduction terminée — ${res.message}")
            pendingCloneDump = null
            _cloneArmed.value = false
        }
    }

    /**
     * Prépare la capture d'émulation HCE : la prochaine carte ISO-DEP présentée verra
     * son dialogue APDU enregistré, pour être rejoué ensuite par le téléphone.
     */
    fun armEmulationCapture() {
        _emuCaptureArmed.value = true
        addLog("Capture d'émulation armée : présentez une carte ISO-DEP.")
    }

    fun cancelEmulationCapture() {
        _emuCaptureArmed.value = false
        addLog("Capture d'émulation annulée.")
    }

    /** Capture le dialogue APDU d'une carte ISO-DEP et le stocke pour l'émulation. */
    private fun performEmulationCapture(tag: Tag) {
        _emuCaptureArmed.value = false
        val iso = android.nfc.tech.IsoDep.get(tag)
        if (iso == null) {
            addLog("Émulation impossible : carte non ISO-DEP. Le Mifare Classic ne peut pas être " +
                "émulé par Android — utilisez le clone physique.")
            return
        }
        viewModelScope.launch(Dispatchers.IO) {
            try {
                val result = ApduCapture.probe(iso)
                if (result.aids.isEmpty()) {
                    addLog("Aucun AID exploitable : cette carte ISO-DEP n'expose pas de SELECT AID connu " +
                        "(émulation par replay non applicable).")
                } else {
                    val label = "ISO-DEP ${_currentTagUid.value ?: ""} — ${result.aids.size} AID, ${result.map.size} APDU"
                    EmulationStore.save(getApplication<Application>(), label, result.aids, result.map)
                    addLog("Carte capturée pour émulation (${result.aids.size} AID, ${result.map.size} réponses). " +
                        "Ouvrez Card Emulation → Start.")
                }
            } catch (e: Exception) {
                addLog("Erreur capture émulation : ${e.message}")
            } finally {
                try { iso.close() } catch (_: Exception) {}
            }
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