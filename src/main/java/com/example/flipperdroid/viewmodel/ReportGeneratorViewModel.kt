package com.example.flipperdroid.viewmodel

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.flipperdroid.reports.Finding
import com.example.flipperdroid.reports.PenetrationReport
import com.example.flipperdroid.reports.ReportGenerator
import com.example.flipperdroid.reports.Severity
import com.example.flipperdroid.util.AppLog
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.launch
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

class ReportGeneratorViewModel : ViewModel() {

    private val _findings = MutableStateFlow<List<Finding>>(emptyList())
    val findings: StateFlow<List<Finding>> = _findings

    private val _reportTitle = MutableStateFlow("Penetration Test Report")
    val reportTitle: StateFlow<String> = _reportTitle

    private val _testerName = MutableStateFlow("Security Researcher")
    val testerName: StateFlow<String> = _testerName

    private val _targetSystem = MutableStateFlow("Target System")
    val targetSystem: StateFlow<String> = _targetSystem

    private val _recommendations = MutableStateFlow<List<String>>(emptyList())
    val recommendations: StateFlow<List<String>> = _recommendations

    /** Entrées de chronologie, sans préfixe (le générateur ajoute la puce). */
    private val _timeline = MutableStateFlow<List<String>>(emptyList())
    val timeline: StateFlow<List<String>> = _timeline

    private val _generatedReport = MutableStateFlow<String>("")
    val generatedReport: StateFlow<String> = _generatedReport

    /** Dernier numéro de finding émis : ne redescend jamais, même après suppression. */
    private var lastFindingNumber = 0

    fun addFinding(
        title: String,
        severity: Severity,
        description: String,
        impact: String,
        remediation: String,
        evidence: String,
        cvss: Float = 0f
    ) {
        val number = ReportGenerator.nextFindingNumber(_findings.value.map { it.id }, lastFindingNumber)
        lastFindingNumber = number
        val id = ReportGenerator.formatFindingId(number)
        val finding = Finding(
            id = id,
            title = title,
            severity = severity,
            description = description,
            impact = impact,
            remediation = remediation,
            evidence = evidence,
            cvss = cvss
        )

        _findings.value = _findings.value + finding
        AppLog.log("Report", "Finding added: $id - $title")
    }

    fun removeFinding(id: String) {
        _findings.value = _findings.value.filter { it.id != id }
        AppLog.log("Report", "Finding removed: $id")
    }

    fun addRecommendation(recommendation: String) {
        _recommendations.value = _recommendations.value + recommendation
        AppLog.log("Report", "Recommendation added: $recommendation")
    }

    fun addTimelineEvent(event: String) {
        val time = SimpleDateFormat("HH:mm:ss", Locale.US).format(Date())
        _timeline.value = _timeline.value + "$time $event"
        AppLog.log("Report", "Timeline event added: $event")
    }

    fun setReportMetadata(title: String, tester: String, target: String) {
        _reportTitle.value = title
        _testerName.value = tester
        _targetSystem.value = target
        AppLog.log("Report", "Report metadata updated")
    }

    /** Instantané des données courantes, daté de maintenant. */
    private fun snapshot() = PenetrationReport(
        title = _reportTitle.value,
        date = Date(),
        tester = _testerName.value,
        target = _targetSystem.value,
        findings = _findings.value,
        recommendations = _recommendations.value,
        timeline = _timeline.value
    )

    fun generateMarkdownReport() {
        viewModelScope.launch {
            _generatedReport.value = ReportGenerator.generateMarkdownReport(snapshot())
            AppLog.log("Report", "Markdown report generated")
        }
    }

    fun generateHtmlReport() {
        viewModelScope.launch {
            _generatedReport.value = ReportGenerator.generateHtmlReport(snapshot())
            AppLog.log("Report", "HTML report generated")
        }
    }

    fun generateJsonReport() {
        viewModelScope.launch {
            _generatedReport.value = ReportGenerator.generateJsonReport(snapshot())
            AppLog.log("Report", "JSON report generated")
        }
    }

    fun getReportStats(): Map<String, Any> {
        val severityCounts = _findings.value.groupingBy { it.severity }.eachCount()
        // Moyenne CVSS : 0.0 si aucun finding (pas de NaN)
        val cvssValues = _findings.value.map { it.cvss }.filter { it.isFinite() }
        val avgCvss = if (cvssValues.isEmpty()) 0.0 else cvssValues.average()
        return mapOf(
            "total_findings" to _findings.value.size,
            "critical" to (severityCounts[Severity.CRITICAL] ?: 0),
            "high" to (severityCounts[Severity.HIGH] ?: 0),
            "medium" to (severityCounts[Severity.MEDIUM] ?: 0),
            "low" to (severityCounts[Severity.LOW] ?: 0),
            "info" to (severityCounts[Severity.INFO] ?: 0),
            "recommendations" to _recommendations.value.size,
            "avg_cvss" to avgCvss
        )
    }

    fun clearAll() {
        _findings.value = emptyList()
        _recommendations.value = emptyList()
        _timeline.value = emptyList()
        _generatedReport.value = ""
        AppLog.log("Report", "Report cleared")
    }
}
