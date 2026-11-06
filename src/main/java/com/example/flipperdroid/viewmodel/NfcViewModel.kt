package com.example.flipperdroid.viewmodel

import android.app.Application
import android.nfc.NdefMessage
import android.nfc.NdefRecord
import android.nfc.Tag
import android.nfc.TagLostException
import android.nfc.tech.IsoDep
import android.nfc.tech.MifareClassic
import android.nfc.tech.MifareUltralight
import android.nfc.tech.Ndef
import android.nfc.tech.NdefFormatable
import android.nfc.tech.NfcA
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import com.example.flipperdroid.flipper.FlipperNfc
import com.example.flipperdroid.nfc.ApduCapture
import com.example.flipperdroid.nfc.EmulationStore
import com.example.flipperdroid.nfc.MifareClassicUtils
import com.example.flipperdroid.nfc.MifareUltralightUtils
import com.example.flipperdroid.nfc.NfcInfo
import com.example.flipperdroid.nfc.NfcVUtils
import java.io.ByteArrayOutputStream
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
    @Volatile private var pendingCloneDump: List<String>? = null
    @Volatile private var cloneWriteTrailers = false
    @Volatile private var cloneWriteSectorZero = true

    // Capture d'émulation HCE : quand "armé", la prochaine carte ISO-DEP est capturée.
    private val _emuCaptureArmed = MutableStateFlow(false)
    val emuCaptureArmed: StateFlow<Boolean> = _emuCaptureArmed.asStateFlow()

    // Analyse détaillée du dernier tag (UID/ATQA/SAK/ATS/NDEF/amiibo…).
    private val _tagInfo = MutableStateFlow<String?>(null)
    val tagInfo: StateFlow<String?> = _tagInfo.asStateFlow()

    // Dernier tag scanné (nécessaire pour l'attaque, le clone, le NDEF)
    @Volatile private var lastTag: Tag? = null

    // Métadonnées du dernier dump, pour l'export .nfc Flipper.
    @Volatile private var dumpDeviceType: String? = null  // "Mifare Classic", "NTAG/Ultralight" ou null
    @Volatile private var dumpAtqa: String? = null
    @Volatile private var dumpSak: String? = null

    // Actions principales.
    // La lecture NFC (connect/authenticate/readBlock) est bloquante : on l'exécute
    // sur un thread d'IO pour ne pas bloquer le thread principal (risque d'ANR).
    fun onTagScanned(tag: Tag) {
        lastTag = tag
        // Clone armé : la carte présentée est la CIBLE. On désarme AVANT d'écrire (échange atomique)
        // pour qu'un second événement ne relance pas l'écriture.
        if (_cloneArmed.compareAndSet(true, false)) {
            val dump = pendingCloneDump
            val trailers = cloneWriteTrailers
            val sectorZero = cloneWriteSectorZero
            pendingCloneDump = null
            performClone(tag, dump, trailers, sectorZero)
            return
        }
        // Si une capture d'émulation est armée, on enregistre le dialogue APDU de la carte.
        if (_emuCaptureArmed.compareAndSet(true, false)) { performEmulationCapture(tag); return }
        _foundKeys.value = emptyList()
        _ndefContent.value = null
        // Lecture UID (rapide, ne nécessite pas de connexion)
        val id = tag.id?.let { MifareClassicUtils.bytesToHex(it) } ?: "-"
        _currentTagUid.value = id
        val techs = tag.techList?.map { it.substringAfterLast('.') } ?: emptyList()
        val typeLabel = describeTag(techs)
        _currentTagType.value = typeLabel
        _tagInfo.value = try { NfcInfo.describe(tag) } catch (e: Exception) { null }
        // ATQA / SAK réels (pour l'export .nfc)
        val nfcA = NfcA.get(tag)
        dumpAtqa = nfcA?.let { MifareClassicUtils.bytesToHex(it.atqa) }
        dumpSak = nfcA?.let { "%02X".format(it.sak.toInt() and 0xFF) }

        viewModelScope.launch(Dispatchers.IO) {
            // NDEF puis MifareClassic, séquentiellement : deux technologies NFC ne
            // peuvent pas être connectées en même temps au même tag. Les lancer en
            // parallèle provoquait des échecs de lecture intermittents.
            readNdef(tag)

            val mfc = MifareClassic.get(tag)
            val dump = mutableListOf<String>()
            var deviceType: String? = null
            if (mfc != null) {
                deviceType = "Mifare Classic"
                val interrupted = readClassicDefaultKey(mfc, dump)
                if (interrupted) addLog("ATTENTION : lecture interrompue, dump INCOMPLET.")
                val unread = dump.count { it == MifareClassicUtils.NO_DATA }
                if (unread > 0) addLog("$unread bloc(s) non lu(s) (secteurs protégés ou erreurs).")
            } else {
                // Pas du Mifare Classic : tente Ultralight / NTAG, puis ISO 15693 (NfcV).
                val pages = MifareUltralightUtils.readDump(tag)
                val vblocks = if (pages == null) NfcVUtils.readDump(tag) else null
                if (pages != null) {
                    dump.addAll(pages)
                    deviceType = "NTAG/Ultralight"
                    addLog("Ultralight/NTAG lu : ${pages.size} pages")
                } else if (vblocks != null && vblocks.isNotEmpty()) {
                    dump.addAll(vblocks)
                    addLog("ISO 15693 (NfcV) lu : ${vblocks.size} blocs")
                } else if (techs.any { it.equals("MifareClassic", true) }) {
                    addLog("Mifare Classic détecté, mais non supporté par la puce NFC de cet appareil.")
                } else {
                    addLog("Tag lu (UID=$id) — type : $typeLabel. Lecture de mémoire non applicable.")
                }
            }
            dumpDeviceType = deviceType

            // Historique + dump pour TOUT tag scanné, pas seulement Mifare Classic.
            _currentTagDump.value = dump.toList()
            // Amiibo (NTAG215, ~135 pages) : l'ID amiibo (8 octets) est aux pages 21-22.
            if (dump.size in 130..145) {
                val amiiboId = ((dump.getOrNull(21) ?: "") + (dump.getOrNull(22) ?: "")).replace(" ", "")
                if (amiiboId.length == 16 && amiiboId.any { it != '0' } && amiiboId.none { it == '-' }) {
                    _tagInfo.update { (it ?: "") + "\nAmiibo ID: $amiiboId" }
                    addLog("Amiibo détecté (ID $amiiboId)")
                }
            }
            val timestamp = SimpleDateFormat("yyyy-MM-dd HH:mm:ss", Locale.getDefault()).format(Date())
            _scanHistory.update { it + NfcScanResult(timestamp, id, typeLabel, dump.toList()) }
            addLog("Tag scanné UID=$id ($typeLabel)" + if (dump.isNotEmpty()) ", ${dump.size} blocks lus" else "")
        }
    }

    /**
     * Lit tous les secteurs Mifare Classic avec la clé par défaut. Chaque secteur est lu dans
     * son propre try : en cas d'erreur il est rempli de [MifareClassicUtils.NO_DATA], ce qui
     * garde les index alignés. Si le tag disparaît, la lecture s'arrête et le reste est rempli.
     * @return true si la lecture a été interrompue (tag perdu ou connexion impossible).
     */
    private fun readClassicDefaultKey(mfc: MifareClassic, dump: MutableList<String>): Boolean {
        val key = MifareClassicUtils.hexToBytes(MifareClassicUtils.DEFAULT_KEY)
        try {
            mfc.connect()
            for (sector in 0 until mfc.sectorCount) {
                val start = dump.size
                val blockCount = mfc.getBlockCountInSector(sector)
                try {
                    if (key != null && mfc.authenticateSectorWithKeyA(sector, key)) {
                        readSectorBlocks(mfc, sector, dump)
                    } else {
                        // Secteur non accessible avec la clé par défaut
                        repeat(blockCount) { dump.add(MifareClassicUtils.NO_DATA) }
                    }
                } catch (e: TagLostException) {
                    addLog("Tag perdu pendant la lecture du secteur $sector.")
                    dump.padTo(mfc.blockCount)
                    return true
                } catch (e: Exception) {
                    dump.padTo(start + blockCount)
                }
            }
            return false
        } catch (e: TagLostException) {
            dump.padTo(mfc.blockCount)
            return true
        } catch (e: Exception) {
            addLog("Erreur lecture tag: ${e.message}")
            return true
        } finally {
            try { mfc.close() } catch (_: Exception) {}
        }
    }

    /**
     * Ajoute les blocs d'un secteur déjà authentifié. Un bloc illisible devient NO_DATA ;
     * [TagLostException] est relancée pour arrêter la lecture.
     */
    private fun readSectorBlocks(mfc: MifareClassic, sector: Int, dump: MutableList<String>) {
        val first = mfc.sectorToBlock(sector)
        for (i in 0 until mfc.getBlockCountInSector(sector)) {
            val bb = try { mfc.readBlock(first + i) } catch (e: TagLostException) { throw e } catch (_: Exception) { null }
            dump.add(bb?.let { MifareClassicUtils.bytesToHex(it) } ?: MifareClassicUtils.NO_DATA)
        }
    }

    /** Complète [this] avec NO_DATA jusqu'à [size] entrées. */
    private fun MutableList<String>.padTo(size: Int) {
        while (this.size < size) add(MifareClassicUtils.NO_DATA)
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
        val ndef = Ndef.get(tag) ?: return
        try {
            ndef.connect()
            val message = ndef.ndefMessage ?: ndef.cachedNdefMessage
            if (message != null) {
                val text = message.records.joinToString("\n") { record ->
                    when {
                        record.toUri() != null -> "URI: ${record.toUri()}"
                        record.payload.isNotEmpty() && record.type.contentEquals(NdefRecord.RTD_TEXT) ->
                            "Text: ${decodeNdefText(record.payload)}"
                        record.payload.isNotEmpty() -> String(record.payload, Charsets.UTF_8)
                        else -> "(empty record)"
                    }
                }
                _ndefContent.value = text
                addLog("NDEF lu (${message.records.size} enregistrement(s))")
            }
        } catch (e: Exception) {
            // Tag non NDEF / lecture refusée : silencieux
        } finally {
            try { ndef.close() } catch (_: Exception) {}
        }
    }

    /**
     * Décode la charge utile d'un enregistrement Texte NDEF (RTD "T").
     * Octet de statut : bit 7 = UTF-16 (sinon UTF-8), bits 5..0 = longueur du code langue.
     */
    private fun decodeNdefText(payload: ByteArray): String {
        val status = payload[0].toInt() and 0xFF
        val charset = if ((status and 0x80) != 0) Charsets.UTF_16 else Charsets.UTF_8
        val langLen = status and 0x3F
        val start = minOf(langLen + 1, payload.size)
        return String(payload, start, payload.size - start, charset)
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
                    NdefRecord.createUri(content)
                } else {
                    NdefRecord.createTextRecord("en", content)
                }
                val message = NdefMessage(arrayOf(record))
                val ndef = Ndef.get(tag)
                if (ndef != null) {
                    try {
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
                    } finally {
                        try { ndef.close() } catch (_: Exception) {}
                    }
                } else {
                    val formatable = NdefFormatable.get(tag)
                    if (formatable != null) {
                        try {
                            formatable.connect()
                            formatable.format(message)
                            _ndefContent.value = "Écrit: $content"
                            addLog("Tag formaté + NDEF écrit: $content")
                        } finally {
                            try { formatable.close() } catch (_: Exception) {}
                        }
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
        val mfc = MifareClassic.get(tag) ?: return addLog("Tag non Mifare Classic")
        _isAttacking.value = true
        viewModelScope.launch(Dispatchers.IO) {
            try {
                attackAndRead(mfc)
            } finally {
                _isAttacking.value = false
            }
        }
    }

    /** Corps de l'attaque par dictionnaire (exécuté sur IO). */
    private fun attackAndRead(mfc: MifareClassic) {
        val found = mutableListOf<String>()
        val dump = mutableListOf<String>()
        var interrupted = false
        try {
            mfc.connect()
            for (sector in 0 until mfc.sectorCount) {
                val start = dump.size
                val blockCount = mfc.getBlockCountInSector(sector)
                try {
                    val label = findSectorKey(mfc, sector)
                    if (label != null) {
                        found.add("Sector $sector -> $label")
                        readSectorBlocks(mfc, sector, dump)
                    } else {
                        found.add("Sector $sector -> no key found")
                        repeat(blockCount) { dump.add(MifareClassicUtils.NO_DATA) }
                    }
                } catch (e: TagLostException) {
                    interrupted = true
                    found.add("Sector $sector -> tag perdu")
                    dump.padTo(mfc.blockCount)
                    break
                } catch (e: Exception) {
                    found.add("Sector $sector -> erreur de lecture")
                    dump.padTo(start + blockCount)
                }
            }
        } catch (e: TagLostException) {
            interrupted = true
            addLog("Tag perdu pendant l'attaque.")
        } catch (e: Exception) {
            interrupted = true
            addLog("Erreur attaque: ${e.message}")
        } finally {
            try { mfc.close() } catch (_: Exception) {}
        }
        _foundKeys.value = found
        val cracked = found.count { it.contains("-> Key") }
        addLog("Attaque dictionnaire terminée : $cracked/${found.size} secteurs cassés")
        if (interrupted && _currentTagDump.value.isNotEmpty()) {
            // Un dump incomplet ne doit pas remplacer le précédent : on le conserve et on le signale.
            addLog("Dump de l'attaque INCOMPLET : dump précédent conservé (clés trouvées affichées).")
        } else {
            _currentTagDump.value = dump.toList()
            if (interrupted) addLog("ATTENTION : dump de l'attaque INCOMPLET (tag perdu ou erreur).")
        }
    }

    /**
     * Teste le dictionnaire sur un secteur. Renvoie "KeyA:..." / "KeyB:..." ou null.
     * Relance [TagLostException] pour arrêter l'attaque.
     */
    private fun findSectorKey(mfc: MifareClassic, sector: Int): String? {
        for (k in MifareClassicUtils.COMMON_KEYS) {
            val kb = MifareClassicUtils.hexToBytes(k) ?: continue
            try {
                if (mfc.authenticateSectorWithKeyA(sector, kb)) return "KeyA:$k"
                if (mfc.authenticateSectorWithKeyB(sector, kb)) return "KeyB:$k"
            } catch (e: TagLostException) {
                throw e
            } catch (e: Exception) {
                // Certaines puces coupent la connexion après un échec : on la rétablit.
                try { mfc.close(); mfc.connect() } catch (ex: TagLostException) { throw ex } catch (_: Exception) {}
            }
        }
        return null
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

    /** Écrit le dump en attente sur la carte cible présentée (paramètres figés à l'armement). */
    private fun performClone(tag: Tag, dump: List<String>?, writeTrailers: Boolean, writeSectorZero: Boolean) {
        if (dump.isNullOrEmpty()) { addLog("Aucun dump à cloner."); return }
        viewModelScope.launch(Dispatchers.IO) {
            addLog("Écriture du dump sur la carte cible…")
            val res = when {
                MifareClassic.get(tag) != null ->
                    MifareClassicUtils.writeDump(tag, dump, writeTrailers, writeSectorZero).message
                MifareUltralight.get(tag) != null ->
                    MifareUltralightUtils.writeDump(tag, dump).message
                else -> "Carte cible non supportée (ni Mifare Classic ni Ultralight/NTAG)."
            }
            addLog("Reproduction terminée — $res")
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

    /** Capture le dialogue APDU d'une carte ISO-DEP et le stocke (chiffré) pour l'émulation. */
    private fun performEmulationCapture(tag: Tag) {
        val iso = IsoDep.get(tag)
        if (iso == null) {
            addLog("Émulation impossible : carte non ISO-DEP. Le Mifare Classic ne peut pas être " +
                "émulé par Android — utilisez le clone physique.")
            return
        }
        // UID de CETTE carte (et non celle du tag précédemment affiché).
        val uid = tag.id?.let { MifareClassicUtils.bytesToHex(it) } ?: "-"
        viewModelScope.launch(Dispatchers.IO) {
            try {
                val result = ApduCapture.probe(iso)
                if (result.aids.isEmpty()) {
                    addLog("Aucun AID exploitable : cette carte ISO-DEP n'expose pas de SELECT AID connu " +
                        "(émulation par replay non applicable).")
                } else {
                    val label = "ISO-DEP $uid — ${result.aids.size} AID, ${result.map.size} APDU"
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

    /** Sauvegarde le dump courant au format .nfc du Flipper Zero (type, ATQA et SAK réels). */
    fun saveAsNfc() {
        val dump = _currentTagDump.value
        val uid = _currentTagUid.value
        if (dump.isEmpty() || uid == null) { addLog("Rien à sauvegarder en .nfc"); return }
        val deviceType = dumpDeviceType
            ?: return addLog("Format .nfc non applicable à ce type de tag (Mifare Classic ou NTAG/Ultralight uniquement).")
        val atqa = dumpAtqa
        val sak = dumpSak
        viewModelScope.launch(Dispatchers.IO) {
            try {
                val content = FlipperNfc.generate(
                    uid.replace(" ", ""), dump,
                    deviceType = deviceType, atqa = atqa, sak = sak
                )
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

    /** Exporte le dump courant en binaire brut (.bin). Les blocs illisibles sont écrits à zéro. */
    fun exportBin() {
        val dump = _currentTagDump.value
        if (dump.isEmpty()) { addLog("Aucun dump à exporter"); return }
        val uid = _currentTagUid.value?.replace(" ", "") ?: "unknown"
        val unreadable = dump.count { it == MifareClassicUtils.NO_DATA }
        viewModelScope.launch(Dispatchers.IO) {
            try {
                val out = ByteArrayOutputStream()
                for (entry in dump) {
                    val bytes = if (entry == MifareClassicUtils.NO_DATA) ByteArray(entry.length / 2)
                    else (MifareClassicUtils.hexToBytes(entry.replace(" ", "")) ?: ByteArray(0))
                    out.write(bytes)
                }
                val dir = getApplication<Application>().getExternalFilesDir("nfc_dumps") ?: getApplication<Application>().filesDir
                val ts = SimpleDateFormat("yyyyMMdd_HHmmss", Locale.US).format(Date())
                val file = File(dir, "dump_${uid}_$ts.bin")
                file.writeBytes(out.toByteArray())
                withContext(Dispatchers.Main) {
                    if (unreadable > 0) addLog("ATTENTION : $unreadable bloc(s) illisible(s) exporté(s) à zéro.")
                    addLog("Export .bin : ${file.absolutePath}")
                }
            } catch (e: Exception) { withContext(Dispatchers.Main) { addLog("Erreur export .bin : ${e.message}") } }
        }
    }

    /**
     * Exporte le dump au format texte Mifare Classic Tool (+Sector + blocs).
     * Secteurs 0-31 : 4 blocs ; secteurs 32-39 (4K) : 16 blocs.
     */
    fun exportMct() {
        val dump = _currentTagDump.value
        if (dump.isEmpty()) { addLog("Aucun dump à exporter"); return }
        val uid = _currentTagUid.value?.replace(" ", "") ?: "unknown"
        viewModelScope.launch(Dispatchers.IO) {
            try {
                val sb = StringBuilder()
                var idx = 0
                var sector = 0
                while (idx < dump.size) {
                    sb.append("+Sector: $sector\n")
                    for (j in 0 until blocksInSector(sector)) {
                        if (idx >= dump.size) break
                        sb.append(dump[idx].replace(" ", "")).append('\n')
                        idx++
                    }
                    sector++
                }
                val dir = getApplication<Application>().getExternalFilesDir("nfc_dumps") ?: getApplication<Application>().filesDir
                val ts = SimpleDateFormat("yyyyMMdd_HHmmss", Locale.US).format(Date())
                val file = File(dir, "dump_${uid}_$ts.mct")
                file.writeText(sb.toString())
                withContext(Dispatchers.Main) { addLog("Export MCT : ${file.absolutePath}") }
            } catch (e: Exception) { withContext(Dispatchers.Main) { addLog("Erreur export MCT : ${e.message}") } }
        }
    }

    /** Importe un dump depuis un texte MCT ; le charge pour un clonage ultérieur. */
    fun importMct(text: String) {
        val blocks = text.lines().map { it.trim() }.filter { line ->
            line.length == 32 && (line == MifareClassicUtils.NO_DATA ||
                line.all { it.isDigit() || it.lowercaseChar() in 'a'..'f' })
        }
        if (blocks.isEmpty()) { addLog("Aucun bloc valide (MCT) dans le fichier"); return }
        _currentTagDump.value = blocks
        _currentTagUid.value = uidFromDump(blocks)
        setImportedMeta(blocks)
        addLog("Import MCT : ${blocks.size} blocs. Utilisez 'Clone' pour écrire sur une carte.")
    }

    /** Importe un dump binaire brut (.bin) découpé en blocs de 16 octets. */
    fun importBin(bytes: ByteArray) {
        if (bytes.isEmpty()) { addLog("Fichier vide"); return }
        if (bytes.size % 16 != 0) {
            addLog("Fichier .bin rejeté : ${bytes.size} octets, non multiple de 16 (blocs Mifare Classic).")
            return
        }
        val blocks = (bytes.indices step 16).map { MifareClassicUtils.bytesToHex(bytes.copyOfRange(it, it + 16)) }
        _currentTagDump.value = blocks
        _currentTagUid.value = uidFromDump(blocks)
        setImportedMeta(blocks)
        addLog("Import .bin : ${blocks.size} blocs. Utilisez 'Clone' pour écrire sur une carte.")
    }

    /** Un dump importé n'a pas d'ATQA/SAK ; son type est déduit de la taille des entrées. */
    private fun setImportedMeta(blocks: List<String>) {
        dumpAtqa = null
        dumpSak = null
        dumpDeviceType = when {
            blocks.all { it.length == 32 } -> "Mifare Classic"
            blocks.all { it.length == 8 } -> "NTAG/Ultralight"
            else -> null
        }
    }

    /**
     * UID déduit du bloc 0 (BCC) : 4 octets si l'octet 4 vaut le XOR des octets 0-3.
     * Sinon UID de 7 octets : octets 0-2 puis 4-7 du bloc 0 (l'octet 3 est un BCC de cascade).
     */
    private fun uidFromDump(dump: List<String>): String {
        val b0 = dump.getOrNull(0)?.let { MifareClassicUtils.hexToBytes(it.replace(" ", "")) }
            ?: return "unknown"
        if (b0.size < 5) return MifareClassicUtils.bytesToHex(b0)
        val bcc = (b0[0].toInt() xor b0[1].toInt() xor b0[2].toInt() xor b0[3].toInt()) and 0xFF
        if ((b0[4].toInt() and 0xFF) == bcc) return MifareClassicUtils.bytesToHex(b0.copyOfRange(0, 4))
        if (b0.size < 8) return MifareClassicUtils.bytesToHex(b0.copyOfRange(0, minOf(7, b0.size)))
        return MifareClassicUtils.bytesToHex(b0.copyOfRange(0, 3) + b0.copyOfRange(4, 8))
    }

    fun clearLogs() {
        _logs.value = emptyList()
    }

    fun addLog(msg: String) {
        _logs.update { it + msg }
    }
}

/** Nombre de blocs d'un secteur Mifare Classic : 4 jusqu'au secteur 31, 16 au-delà (4K). */
private fun blocksInSector(sector: Int): Int = if (sector < 32) 4 else 16

// Modèle pour l'historique
data class NfcScanResult(
    val timestamp: String,
    val uid: String?,
    val type: String?,
    val dump: List<String>
)
