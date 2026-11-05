package com.example.flipperdroid.util

/**
 * Construction sûre de lignes de commande passées à `su -c` (un seul argument shell).
 *
 * Toute donnée venant de l'utilisateur ou du réseau doit être soit validée par un
 * des validateurs ci-dessous, soit encadrée par [quote]. Sans ça, une saisie comme
 * `1.1.1.1; reboot` s'exécutait en root. Aucun import Android : testable en JVM.
 */
object ShellArgs {

    /** Encadre en quotes simples POSIX ; les quotes internes deviennent '\''. */
    fun quote(arg: String): String = "'" + arg.replace("'", "'\\''") + "'"

    fun join(args: List<String>): String = args.joinToString(" ") { quote(it) }

    private val HOSTNAME = Regex("^(?=.{1,253}$)([A-Za-z0-9]([A-Za-z0-9-]{0,61}[A-Za-z0-9])?)(\\.[A-Za-z0-9]([A-Za-z0-9-]{0,61}[A-Za-z0-9])?)*\\.?$")
    private val IPV4 = Regex("^((25[0-5]|2[0-4]\\d|1?\\d?\\d)\\.){3}(25[0-5]|2[0-4]\\d|1?\\d?\\d)$")
    private val IPV4_CIDR = Regex("^((25[0-5]|2[0-4]\\d|1?\\d?\\d)\\.){3}(25[0-5]|2[0-4]\\d|1?\\d?\\d)/(3[0-2]|[12]?\\d)$")
    private val IPV6 = Regex("^[0-9A-Fa-f:.]{2,45}$")
    private val IFACE = Regex("^[A-Za-z0-9._-]{1,15}$")
    private val MAC = Regex("^([0-9A-Fa-f]{2}:){5}[0-9A-Fa-f]{2}$")

    fun isValidIpv4(s: String) = IPV4.matches(s)

    /** Cible réseau acceptée : IPv4, IPv4/CIDR, IPv6 littérale ou nom d'hôte. */
    fun isValidTarget(s: String): Boolean =
        IPV4.matches(s) || IPV4_CIDR.matches(s) || (s.contains(':') && IPV6.matches(s)) ||
            // Un nom d'hôte ne finit pas par un label purement numérique (sinon "256.1.1.1" passerait).
            (HOSTNAME.matches(s) && !s.trimEnd('.').substringAfterLast('.').all { it.isDigit() })

    fun isValidInterface(s: String) = IFACE.matches(s)

    fun isValidMac(s: String) = MAC.matches(s)

    /**
     * Découpe des options de type nmap en arguments, en refusant les métacaractères
     * shell (même quotés, ils n'ont rien à faire dans des options) et les options qui
     * écrivent/lisent des fichiers arbitraires ou exécutent des scripts.
     * @return la liste d'arguments, ou null si refusée.
     */
    fun splitSafeOptions(options: String): List<String>? {
        val trimmed = options.trim()
        if (trimmed.isEmpty()) return emptyList()
        if (trimmed.any { it in ";&|`$<>\\\"'\n\r(){}" }) return null
        val parts = trimmed.split(Regex("\\s+"))
        // -o* (fichiers de sortie), -i* (listes d'entrée), scripts NSE et chemins de données.
        val forbiddenPrefixes = listOf("-o", "-i", "--script", "--datadir", "--resume", "--stylesheet", "--servicedb", "--versiondb")
        if (parts.any { p -> forbiddenPrefixes.any { p.startsWith(it) } }) return null
        return parts
    }
}
