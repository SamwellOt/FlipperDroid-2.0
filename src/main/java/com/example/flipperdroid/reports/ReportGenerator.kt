package com.example.flipperdroid.reports

import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

/** Rapport d'audit. Date en [Date] (java.time n'est pas disponible avant API 26). */
data class PenetrationReport(
    val title: String,
    val date: Date,
    val tester: String,
    val target: String,
    val findings: List<Finding>,
    val recommendations: List<String>,
    val timeline: List<String>
)

data class Finding(
    val id: String,
    val title: String,
    val severity: Severity,
    val description: String,
    val impact: String,
    val remediation: String,
    val evidence: String,
    val cvss: Float = 0f
)

enum class Severity {
    CRITICAL,
    HIGH,
    MEDIUM,
    LOW,
    INFO
}

object ReportGenerator {

    private const val DATE_PATTERN = "yyyy-MM-dd HH:mm"
    private const val ISO_PATTERN = "yyyy-MM-dd'T'HH:mm:ss"
    private const val ID_PREFIX = "FINDING-"

    /** Caractères Markdown à échapper dans le texte utilisateur. */
    private const val MARKDOWN_SPECIAL = "\\`*_{}[]()#+-.!|<>~"

    /** Formate une date avec des locales fixes (SimpleDateFormat n'est pas thread-safe : instance locale). */
    private fun formatDate(date: Date, pattern: String = DATE_PATTERN): String =
        SimpleDateFormat(pattern, Locale.US).format(date)

    private fun escapeHtml(value: String): String = value
        .replace("&", "&amp;")
        .replace("<", "&lt;")
        .replace(">", "&gt;")
        .replace("\"", "&quot;")
        .replace("'", "&#39;")

    private fun escapeJson(value: String): String {
        val sb = StringBuilder()
        for (c in value) {
            when (c) {
                '\\' -> sb.append("\\\\")
                '"' -> sb.append("\\\"")
                '\n' -> sb.append("\\n")
                '\r' -> sb.append("\\r")
                '\t' -> sb.append("\\t")
                '\b' -> sb.append("\\b")
                else -> if (c < ' ') sb.append("\\u%04x".format(c.code)) else sb.append(c)
            }
        }
        return sb.toString()
    }

    /** CVSS utilisable en sortie : NaN et infini deviennent 0. */
    internal fun safeCvss(value: Float): Float =
        if (value.isNaN() || value.isInfinite()) 0f else value

    /**
     * Échappe les caractères spéciaux Markdown.
     * @param singleLine si true, les retours à la ligne deviennent des espaces (titres, champs courts).
     */
    internal fun escapeMarkdown(value: String, singleLine: Boolean = false): String {
        val sb = StringBuilder(value.length + 8)
        for (c in value) {
            if (singleLine && (c == '\n' || c == '\r')) {
                sb.append(' ')
                continue
            }
            if (MARKDOWN_SPECIAL.indexOf(c) >= 0) sb.append('\\')
            sb.append(c)
        }
        return sb.toString()
    }

    /** Clôture de bloc de code : plus longue que toute suite de backticks contenue dans [content]. */
    internal fun markdownFence(content: String): String {
        var longest = 0
        var run = 0
        for (c in content) {
            if (c == '`') {
                run++
                if (run > longest) longest = run
            } else {
                run = 0
            }
        }
        return "`".repeat(maxOf(3, longest + 1))
    }

    /** Numéro d'un identifiant "FINDING-n", ou null. */
    internal fun findingNumber(id: String): Int? =
        if (id.startsWith(ID_PREFIX)) id.substring(ID_PREFIX.length).toIntOrNull() else null

    /**
     * Prochain numéro de finding : strictement supérieur au maximum déjà utilisé
     * et au dernier numéro émis, pour ne jamais réutiliser un identifiant supprimé.
     */
    internal fun nextFindingNumber(existingIds: Collection<String>, lastIssued: Int): Int =
        maxOf(lastIssued, existingIds.mapNotNull { findingNumber(it) }.maxOrNull() ?: 0) + 1

    internal fun formatFindingId(number: Int): String = "$ID_PREFIX$number"

    fun generateMarkdownReport(report: PenetrationReport): String {
        val sb = StringBuilder()

        // Header
        sb.append("# Penetration Test Report\n\n")
        sb.append("## Executive Summary\n")
        sb.append("**Project:** ${escapeMarkdown(report.title, singleLine = true)}\n")
        sb.append("**Date:** ${formatDate(report.date)}\n")
        sb.append("**Tester:** ${escapeMarkdown(report.tester, singleLine = true)}\n")
        sb.append("**Target:** ${escapeMarkdown(report.target, singleLine = true)}\n\n")

        // Statistics
        val stats = report.findings.groupingBy { it.severity }.eachCount()
        sb.append("### Findings Summary\n")
        sb.append("| Severity | Count |\n")
        sb.append("|----------|-------|\n")
        Severity.values().forEach { severity ->
            sb.append("| $severity | ${stats[severity] ?: 0} |\n")
        }
        sb.append("\n")

        // Findings
        sb.append("## Findings\n\n")
        report.findings.sortedBy { it.severity.ordinal }.forEach { finding ->
            val cvss = safeCvss(finding.cvss)
            sb.append("### [${finding.severity}] ${escapeMarkdown(finding.title, singleLine = true)}\n")
            sb.append("**ID:** ${escapeMarkdown(finding.id, singleLine = true)}\n\n")
            sb.append("**Description:**\n${escapeMarkdown(finding.description)}\n\n")
            sb.append("**Impact:**\n${escapeMarkdown(finding.impact)}\n\n")
            if (cvss > 0f) {
                sb.append("**CVSS Score:** $cvss\n\n")
            }
            sb.append("**Remediation:**\n${escapeMarkdown(finding.remediation)}\n\n")
            // Preuve brute : jamais échappée, dans un bloc clôturé par une fence plus longue que son contenu
            val fence = markdownFence(finding.evidence)
            sb.append("**Evidence:**\n$fence\n${finding.evidence}\n$fence\n\n")
            sb.append("---\n\n")
        }

        // Recommendations
        if (report.recommendations.isNotEmpty()) {
            sb.append("## Recommendations\n\n")
            report.recommendations.forEach { rec ->
                sb.append("- ${escapeMarkdown(rec, singleLine = true)}\n")
            }
            sb.append("\n")
        }

        // Timeline (entrées stockées sans préfixe "- ")
        if (report.timeline.isNotEmpty()) {
            sb.append("## Test Timeline\n\n")
            report.timeline.forEach { event ->
                sb.append("- ${escapeMarkdown(event, singleLine = true)}\n")
            }
            sb.append("\n")
        }

        // Footer
        sb.append("---\n")
        sb.append("_Report generated on ${formatDate(Date(), "yyyy-MM-dd HH:mm:ss")}_\n")

        return sb.toString()
    }

    fun generateHtmlReport(report: PenetrationReport): String {
        val sb = StringBuilder()

        sb.append("""
            <!DOCTYPE html>
            <html>
            <head>
                <meta charset="UTF-8">
                <title>${escapeHtml(report.title)}</title>
                <style>
                    body { font-family: Arial, sans-serif; margin: 20px; background: #f5f5f5; }
                    .header { background: #2c3e50; color: white; padding: 20px; border-radius: 5px; }
                    .section { background: white; padding: 20px; margin-top: 20px; border-radius: 5px; box-shadow: 0 2px 4px rgba(0,0,0,0.1); }
                    h1 { margin: 0; }
                    h2 { color: #2c3e50; border-bottom: 2px solid #3498db; padding-bottom: 10px; }
                    table { width: 100%; border-collapse: collapse; }
                    th, td { padding: 12px; text-align: left; border-bottom: 1px solid #ddd; }
                    th { background: #3498db; color: white; }
                    .critical { background: #e74c3c; color: white; padding: 3px 8px; border-radius: 3px; }
                    .high { background: #e67e22; color: white; padding: 3px 8px; border-radius: 3px; }
                    .medium { background: #f39c12; color: white; padding: 3px 8px; border-radius: 3px; }
                    .low { background: #3498db; color: white; padding: 3px 8px; border-radius: 3px; }
                    .info { background: #95a5a6; color: white; padding: 3px 8px; border-radius: 3px; }
                    code { background: #ecf0f1; padding: 2px 6px; border-radius: 3px; }
                    pre { background: #2c3e50; color: #ecf0f1; padding: 15px; overflow-x: auto; }
                </style>
            </head>
            <body>
                <div class="header">
                    <h1>${escapeHtml(report.title)}</h1>
                    <p>Date: ${escapeHtml(formatDate(report.date))}</p>
                    <p>Tester: ${escapeHtml(report.tester)} | Target: ${escapeHtml(report.target)}</p>
                </div>
        """.trimIndent())

        // Summary
        val stats = report.findings.groupingBy { it.severity }.eachCount()
        sb.append("""
            <div class="section">
                <h2>Findings Summary</h2>
                <table>
                    <tr>
                        <th>Severity</th>
                        <th>Count</th>
                    </tr>
        """.trimIndent())

        Severity.values().forEach { severity ->
            sb.append("<tr><td><span class='${severity.name.lowercase()}'>${severity.name}</span></td><td>${stats[severity] ?: 0}</td></tr>\n")
        }

        sb.append("</table>\n</div>\n")

        // Findings
        sb.append("<div class='section'><h2>Findings</h2>\n")
        report.findings.sortedBy { it.severity.ordinal }.forEach { finding ->
            sb.append("""
                <div style="margin-top: 20px; padding: 15px; border-left: 4px solid #e74c3c;">
                    <h3><span class='${finding.severity.name.lowercase()}'>${finding.severity.name}</span> - ${escapeHtml(finding.title)}</h3>
                    <p><strong>ID:</strong> ${escapeHtml(finding.id)}</p>
                    <p><strong>Description:</strong> ${escapeHtml(finding.description)}</p>
                    <p><strong>Impact:</strong> ${escapeHtml(finding.impact)}</p>
            """.trimIndent())

            val cvss = safeCvss(finding.cvss)
            if (cvss > 0f) {
                sb.append("<p><strong>CVSS Score:</strong> $cvss</p>\n")
            }

            sb.append("<p><strong>Remediation:</strong> ${escapeHtml(finding.remediation)}</p>\n")
            sb.append("<pre>${escapeHtml(finding.evidence)}</pre>\n")
            sb.append("</div>\n")
        }

        sb.append("</div>\n")

        // Recommendations
        if (report.recommendations.isNotEmpty()) {
            sb.append("""
                <div class="section">
                    <h2>Recommendations</h2>
                    <ul>
            """.trimIndent())
            report.recommendations.forEach { rec ->
                sb.append("<li>${escapeHtml(rec)}</li>\n")
            }
            sb.append("</ul>\n</div>\n")
        }

        // Timeline
        if (report.timeline.isNotEmpty()) {
            sb.append("<div class='section'><h2>Test Timeline</h2>\n<ul>\n")
            report.timeline.forEach { event ->
                sb.append("<li>${escapeHtml(event)}</li>\n")
            }
            sb.append("</ul>\n</div>\n")
        }

        sb.append("</body>\n</html>")

        return sb.toString()
    }

    /** Écrit un tableau JSON de chaînes indenté de deux espaces (sans virgule finale). */
    private fun StringBuilder.appendJsonStringArray(key: String, items: List<String>): StringBuilder {
        append("  \"$key\": [\n")
        items.forEachIndexed { index, item ->
            append("    \"${escapeJson(item)}\"")
            if (index < items.size - 1) append(",")
            append("\n")
        }
        append("  ]")
        return this
    }

    fun generateJsonReport(report: PenetrationReport): String {
        val sb = StringBuilder()

        sb.append("{\n")
        sb.append("  \"title\": \"${escapeJson(report.title)}\",\n")
        sb.append("  \"date\": \"${escapeJson(formatDate(report.date, ISO_PATTERN))}\",\n")
        sb.append("  \"tester\": \"${escapeJson(report.tester)}\",\n")
        sb.append("  \"target\": \"${escapeJson(report.target)}\",\n")
        sb.append("  \"findings\": [\n")

        report.findings.forEachIndexed { index, finding ->
            sb.append("    {\n")
            sb.append("      \"id\": \"${escapeJson(finding.id)}\",\n")
            sb.append("      \"title\": \"${escapeJson(finding.title)}\",\n")
            sb.append("      \"severity\": \"${escapeJson(finding.severity.name)}\",\n")
            sb.append("      \"description\": \"${escapeJson(finding.description)}\",\n")
            sb.append("      \"impact\": \"${escapeJson(finding.impact)}\",\n")
            sb.append("      \"remediation\": \"${escapeJson(finding.remediation)}\",\n")
            sb.append("      \"evidence\": \"${escapeJson(finding.evidence)}\",\n")
            sb.append("      \"cvss\": ${safeCvss(finding.cvss)}\n")
            sb.append("    }")
            if (index < report.findings.size - 1) sb.append(",")
            sb.append("\n")
        }

        sb.append("  ],\n")
        sb.appendJsonStringArray("recommendations", report.recommendations).append(",\n")
        sb.appendJsonStringArray("timeline", report.timeline).append("\n")
        sb.append("}\n")

        return sb.toString()
    }
}
