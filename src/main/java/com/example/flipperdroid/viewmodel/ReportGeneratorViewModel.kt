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
import java.time.LocalDateTime

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

    private val _timeline = MutableStateFlow<List<String>>(emptyList())
    val timeline: StateFlow<List<String>> = _timeline

    private val _generatedReport = MutableStateFlow<String>("")
    val generatedReport: StateFlow<String> = _generatedReport

    fun addFinding(
        title: String,
        severity: Severity,
        description: String,
        impact: String,
        remediation: String,
        evidence: String,
        cvss: Float = 0f
    ) {
        val id = "FINDING-${_findings.value.size + 1}"
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
        AppLog.i("Finding added: $id - $title")
    }

    fun removeFinding(id: String) {
        _findings.value = _findings.value.filter { it.id != id }
        AppLog.i("Finding removed: $id")
    }

    fun addRecommendation(recommendation: String) {
        _recommendations.value = _recommendations.value + recommendation
        AppLog.i("Recommendation added: $recommendation")
    }

    fun addTimelineEvent(event: String) {
        _timeline.value = _timeline.value + "- ${LocalDateTime.now().format(java.time.format.DateTimeFormatter.ofPattern("HH:mm:ss"))} $event"
        AppLog.i("Timeline event added: $event")
    }

    fun setReportMetadata(title: String, tester: String, target: String) {
        _reportTitle.value = title
        _testerName.value = tester
        _targetSystem.value = target
        AppLog.i("Report metadata updated")
    }

    fun generateMarkdownReport() {
        viewModelScope.launch {
            val report = PenetrationReport(
                title = _reportTitle.value,
                date = LocalDateTime.now(),
                tester = _testerName.value,
                target = _targetSystem.value,
                findings = _findings.value,
                recommendations = _recommendations.value,
                timeline = _timeline.value
            )

            _generatedReport.value = ReportGenerator.generateMarkdownReport(report)
            AppLog.i("Markdown report generated")
        }
    }

    fun generateHtmlReport() {
        viewModelScope.launch {
            val report = PenetrationReport(
                title = _reportTitle.value,
                date = LocalDateTime.now(),
                tester = _testerName.value,
                target = _targetSystem.value,
                findings = _findings.value,
                recommendations = _recommendations.value,
                timeline = _timeline.value
            )

            _generatedReport.value = ReportGenerator.generateHtmlReport(report)
            AppLog.i("HTML report generated")
        }
    }

    fun generateJsonReport() {
        viewModelScope.launch {
            val report = PenetrationReport(
                title = _reportTitle.value,
                date = LocalDateTime.now(),
                tester = _testerName.value,
                target = _targetSystem.value,
                findings = _findings.value,
                recommendations = _recommendations.value,
                timeline = _timeline.value
            )

            _generatedReport.value = ReportGenerator.generateJsonReport(report)
            AppLog.i("JSON report generated")
        }
    }

    fun getReportStats(): Map<String, Any> {
        val severityCounts = _findings.value.groupingBy { it.severity }.eachCount()
        return mapOf(
            "total_findings" to _findings.value.size,
            "critical" to (severityCounts[Severity.CRITICAL] ?: 0),
            "high" to (severityCounts[Severity.HIGH] ?: 0),
            "medium" to (severityCounts[Severity.MEDIUM] ?: 0),
            "low" to (severityCounts[Severity.LOW] ?: 0),
            "info" to (severityCounts[Severity.INFO] ?: 0),
            "recommendations" to _recommendations.value.size,
            "avg_cvss" to (_findings.value.map { it.cvss }.average())
        )
    }

    fun clearAll() {
        _findings.value = emptyList()
        _recommendations.value = emptyList()
        _timeline.value = emptyList()
        _generatedReport.value = ""
        AppLog.i("Report cleared")
    }
}
