package com.example.flipperdroid.viewmodel

import android.app.Application
import android.content.Context
import android.hardware.ConsumerIrManager
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.example.flipperdroid.infrared.IrFile
import com.example.flipperdroid.infrared.IrProtocols
import com.example.flipperdroid.infrared.PowerCode
import com.example.flipperdroid.infrared.PowerCodes
import com.example.flipperdroid.util.AppLog
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch

/**
 * Outils IR offensifs : TV-B-Gone (balayage de tous les codes d'extinction) et
 * brute-force de commandes (cartographier une télécommande inconnue). Pour tests
 * autorisés uniquement.
 */
class IrToolsViewModel(app: Application) : AndroidViewModel(app) {

    private val irManager =
        app.getSystemService(Context.CONSUMER_IR_SERVICE) as? ConsumerIrManager

    val hasEmitter: Boolean get() = irManager?.hasIrEmitter() == true

    private val _running = MutableStateFlow(false)
    val running: StateFlow<Boolean> = _running

    private val _progress = MutableStateFlow("")
    val progress: StateFlow<String> = _progress

    private val _bruteCommand = MutableStateFlow(-1)
    val bruteCommand: StateFlow<Int> = _bruteCommand

    private var job: Job? = null

    /**
     * Émet tous les codes power connus (fichiers .ir empaquetés + liste curée).
     *
     * Chaque code est envoyé comme une **rafale répétée** (IrProtocols.encodePowerBurst) :
     * une trame isolée est filtrée comme du bruit par la plupart des TV, et Sony exige
     * 3 répétitions. Les codes en double (ex. LG présent dans LG_TV.ir ET dans la liste
     * curée) sont **dédupliqués** : "power" étant une bascule, émettre deux fois le même
     * code rallumerait la TV qu'on vient d'éteindre.
     *
     * @param rounds nombre de passes. 1 par défaut (une bascule = extinction). Ne PAS
     *   augmenter sans raison : chaque passe supplémentaire rebascule chaque appareil.
     */
    fun tvBGone(rounds: Int = 1) {
        if (_running.value) return
        val manager = irManager
        if (manager == null || !manager.hasIrEmitter()) {
            _progress.value = IrProtocols.NO_EMITTER_MSG
            return
        }
        _running.value = true   // mis à jour AVANT le lancement : un second tap est refusé immédiatement
        job = viewModelScope.launch(Dispatchers.IO) {
            try {
                val codes = collectPowerSignals()
                _progress.value = "TV-B-Gone: ${codes.size} codes uniques chargés"
                var sent = 0
                var lastError: String? = null
                repeat(rounds) {
                    for ((label, signal) in codes) {
                        if (!isActive) return@launch
                        val (freq, pattern) = signal
                        try {
                            manager.transmit(freq, pattern)
                            sent++
                        } catch (e: Exception) {
                            lastError = e.message ?: e.javaClass.simpleName
                        }
                        _progress.value = "Envoyé $sent/${codes.size} — $label"
                        // Laisse le temps au récepteur de traiter la pression avant la suivante.
                        delay(200)
                    }
                }
                val errTail = lastError?.let { " — erreur : $it" } ?: ""
                _progress.value = "TV-B-Gone terminé : $sent émissions (${codes.size} codes uniques)$errTail"
                AppLog.log("IRTools", "TV-B-Gone fired $sent bursts (${codes.size} unique codes)")
            } finally {
                _running.value = false
            }
        }
    }

    /**
     * Rassemble les signaux power à émettre, **dédupliqués** sur le contenu de la trame
     * de base (pour qu'un même code présent dans un .ir et dans la liste curée ne soit
     * pas émis deux fois → pas de rebascule). Chaque entrée est déjà une rafale prête.
     */
    private fun collectPowerSignals(): List<Pair<String, Pair<Int, IntArray>>> {
        val out = mutableListOf<Pair<String, Pair<Int, IntArray>>>()
        val seen = HashSet<String>()

        // Clé d'unicité : la trame de base (fréquence + motif), indépendante du nb de répétitions.
        fun addUnique(label: String, base: Pair<Int, IntArray>?, burst: Pair<Int, IntArray>?) {
            if (base == null || burst == null) return
            val key = "${base.first}:${base.second.joinToString(",")}"
            if (seen.add(key)) out.add(label to burst)
        }

        try {
            val assets = getApplication<Application>().assets
            val files = assets.list("infrared")?.filter { it.endsWith(".ir") } ?: emptyList()
            for (file in files) {
                val text = assets.open("infrared/$file").bufferedReader().use { it.readText() }
                IrFile.parse(text)
                    .filter {
                        it.name.contains("power", true) ||
                            it.name.equals("on", true) ||
                            it.name.equals("off", true)
                    }
                    .forEach { b ->
                        addUnique("${file.removeSuffix(".ir")}/${b.name}", b.toSignal(), b.toBurst())
                    }
            }
        } catch (e: Exception) {
            // Ne pas avaler : le balayage continue avec la liste curée, et le problème est journalisé.
            AppLog.log("IRTools", "Chargement des .ir impossible : ${e.message}")
        }

        for (pc in PowerCodes.EXTRA) {
            addUnique(pc.brand, pc.toSignal(), pc.toBurst())
        }
        return out
    }

    /** Codes power ciblables un par un — mode précis : on vise un appareil, on tire une fois. */
    val powerBrands: List<PowerCode> = PowerCodes.EXTRA

    /**
     * Émet UN seul code power, en rafale "maintenue" robuste (presses élevé), pour viser
     * un appareil précis au lieu de balayer toute la liste. "Power" étant une bascule, un
     * appui = un basculement : si rien ne se passe, re-viser et re-tirer (ne pas marteler
     * en boucle, ce qui rallumerait un appareil qu'on vient d'éteindre).
     */
    fun fireBrand(pc: PowerCode) {
        if (_running.value) return
        val manager = irManager
        if (manager == null || !manager.hasIrEmitter()) {
            _progress.value = IrProtocols.NO_EMITTER_MSG
            return
        }
        _running.value = true
        job = viewModelScope.launch(Dispatchers.IO) {
            try {
                val burst = pc.toBurst(presses = 6)
                if (burst == null) {
                    _progress.value = "Code ${pc.brand} non encodable"
                    return@launch
                }
                val (freq, pattern) = burst
                try {
                    manager.transmit(freq, pattern)
                    _progress.value = "Émis : ${pc.brand} (power). Rien ? Vise mieux et re-tape."
                    AppLog.log("IRTools", "Fired targeted power code ${pc.brand}")
                } catch (e: Exception) {
                    _progress.value = "Échec de l'émission ${pc.brand} : ${e.message}"
                }
            } finally {
                _running.value = false
            }
        }
    }

    /** Balaye les commandes [start]..[end] pour (protocole, adresse). */
    fun bruteForce(protocol: String, address: Int, start: Int, end: Int, delayMs: Long) {
        if (_running.value) return
        if (IrProtocols.encode(protocol, address, start) == null) {
            _progress.value = "Unsupported protocol: $protocol"
            return
        }
        val manager = irManager
        if (manager == null || !manager.hasIrEmitter()) {
            _progress.value = IrProtocols.NO_EMITTER_MSG
            return
        }
        _running.value = true
        job = viewModelScope.launch(Dispatchers.IO) {
            try {
                var cmd = start
                var lastError: String? = null
                while (cmd <= end && isActive) {
                    _bruteCommand.value = cmd
                    val step = IrProtocols.encode(protocol, address, cmd)
                    if (step != null) {
                        try {
                            manager.transmit(step.first, step.second)
                        } catch (e: Exception) {
                            lastError = e.message ?: e.javaClass.simpleName
                        }
                    }
                    val hex = cmd.toString(16).uppercase()
                    _progress.value = "Brute $protocol addr=0x${address.toString(16).uppercase()} cmd=0x$hex ($cmd)"
                    delay(delayMs)
                    cmd++
                }
                val errTail = lastError?.let { " — erreur : $it" } ?: ""
                if (isActive) _progress.value = "Brute-force terminé (jusqu'à 0x${end.toString(16).uppercase()})$errTail"
            } finally {
                _running.value = false
            }
        }
    }

    fun stop() {
        job?.cancel()
        _running.value = false
        if (_progress.value.isNotEmpty()) _progress.value = "${_progress.value} — arrêté"
    }
}
