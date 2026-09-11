package com.example.flipperdroid.reports

import java.time.LocalDateTime
import java.time.format.DateTimeFormatter

data class PenetrationReport(
    val title: String,
    val date: LocalDateTime,
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

    fun generateMarkdownReport(report: PenetrationReport): String {
        val sb = StringBuilder()

        // Header
        sb.append("# Penetration Test Report\n\n")
        sb.append("## Executive Summary\n")
        sb.append("**Project:** ${report.title}\n")
        sb.append("**Date:** ${report.date.format(DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm"))}\n")
        sb.append("**Tester:** ${report.tester}\n")
        sb.append("**Target:** ${report.target}\n\n")

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
        report.findings.sortedByDescending { it.severity.ordinal }.forEach { finding ->
            sb.append("### [${finding.severity}] ${finding.title}\n")
            sb.append("**ID:** ${finding.id}\n\n")
            sb.append("**Description:**\n${finding.description}\n\n")
            sb.append("**Impact:**\n${finding.impact}\n\n")
            if (finding.cvss > 0f) {
                sb.append("**CVSS Score:** ${finding.cvss}\n\n")
            }
            sb.append("**Remediation:**\n${finding.remediation}\n\n")
            sb.append("**Evidence:**\n```\n${finding.evidence}\n```\n\n")
            sb.append("---\n\n")
        }

        // Recommendations
        if (report.recommendations.isNotEmpty()) {
            sb.append("## Recommendations\n\n")
            report.recommendations.forEach { rec ->
                sb.append("- $rec\n")
            }
            sb.append("\n")
        }

        // Timeline
        if (report.timeline.isNotEmpty()) {
            sb.append("## Test Timeline\n\n")
            report.timeline.forEach { event ->
                sb.append("- $event\n")
            }
            sb.append("\n")
        }

        // Footer
        sb.append("---\n")
        sb.append("_Report generated on ${LocalDateTime.now()}_\n")

        return sb.toString()
    }

    fun generateHtmlReport(report: PenetrationReport): String {
        val sb = StringBuilder()

        sb.append("""
            <!DOCTYPE html>
            <html>
            <head>
                <meta charset="UTF-8">
                <title>${report.title}</title>
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
                    <h1>${report.title}</h1>
                    <p>Date: ${report.date.format(DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm"))}</p>
                    <p>Tester: ${report.tester} | Target: ${report.target}</p>
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
        report.findings.sortedByDescending { it.severity.ordinal }.forEach { finding ->
            sb.append("""
                <div style="margin-top: 20px; padding: 15px; border-left: 4px solid #e74c3c;">
                    <h3><span class='${finding.severity.name.lowercase()}'>${finding.severity.name}</span> - ${finding.title}</h3>
                    <p><strong>ID:</strong> ${finding.id}</p>
                    <p><strong>Description:</strong> ${finding.description}</p>
                    <p><strong>Impact:</strong> ${finding.impact}</p>
            """.trimIndent())

            if (finding.cvss > 0f) {
                sb.append("<p><strong>CVSS Score:</strong> ${finding.cvss}</p>\n")
            }

            sb.append("<p><strong>Remediation:</strong> ${finding.remediation}</p>\n")
            sb.append("<pre>${finding.evidence}</pre>\n")
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
                sb.append("<li>$rec</li>\n")
            }
            sb.append("</ul>\n</div>\n")
        }

        sb.append("</body>\n</html>")

        return sb.toString()
    }

    fun generateJsonReport(report: PenetrationReport): String {
        val sb = StringBuilder()

        sb.append("{\n")
        sb.append("  \"title\": \"${report.title}\",\n")
        sb.append("  \"date\": \"${report.date}\",\n")
        sb.append("  \"tester\": \"${report.tester}\",\n")
        sb.append("  \"target\": \"${report.target}\",\n")
        sb.append("  \"findings\": [\n")

        report.findings.forEachIndexed { index, finding ->
            sb.append("    {\n")
            sb.append("      \"id\": \"${finding.id}\",\n")
            sb.append("      \"title\": \"${finding.title}\",\n")
            sb.append("      \"severity\": \"${finding.severity.name}\",\n")
            sb.append("      \"description\": \"${finding.description}\",\n")
            sb.append("      \"impact\": \"${finding.impact}\",\n")
            sb.append("      \"remediation\": \"${finding.remediation}\",\n")
            sb.append("      \"evidence\": \"${finding.evidence}\",\n")
            sb.append("      \"cvss\": ${finding.cvss}\n")
            sb.append("    }")
            if (index < report.findings.size - 1) sb.append(",")
            sb.append("\n")
        }

        sb.append("  ],\n")
        sb.append("  \"recommendations\": [\n")
        report.recommendations.forEachIndexed { index, rec ->
            sb.append("    \"$rec\"")
            if (index < report.recommendations.size - 1) sb.append(",")
            sb.append("\n")
        }
        sb.append("  ]\n")
        sb.append("}\n")

        return sb.toString()
    }
}
