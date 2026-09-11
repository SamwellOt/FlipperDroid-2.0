package com.example.flipperdroid.view

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.FileOpen
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.unit.dp
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.navigation.NavController
import com.example.flipperdroid.reports.Severity
import com.example.flipperdroid.viewmodel.ReportGeneratorViewModel

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ReportGeneratorScreen(navController: NavController) {
    val viewModel: ReportGeneratorViewModel = viewModel()

    val findings by viewModel.findings.collectAsState()
    val reportTitle by viewModel.reportTitle.collectAsState()
    val testerName by viewModel.testerName.collectAsState()
    val targetSystem by viewModel.targetSystem.collectAsState()
    val recommendations by viewModel.recommendations.collectAsState()
    val generatedReport by viewModel.generatedReport.collectAsState()

    var showAddFinding by remember { mutableStateOf(false) }
    var editTitle by remember { mutableStateOf(reportTitle) }
    var editTester by remember { mutableStateOf(testerName) }
    var editTarget by remember { mutableStateOf(targetSystem) }
    var tab by remember { mutableStateOf(0) }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Report Generator") },
                navigationIcon = {
                    IconButton(onClick = { navController.popBackStack() }) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, "Back")
                    }
                }
            )
        }
    ) { padding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
        ) {
            TabRow(selectedTabIndex = tab) {
                Tab(
                    selected = tab == 0,
                    onClick = { tab = 0 },
                    text = { Text("Build") }
                )
                Tab(
                    selected = tab == 1,
                    onClick = { tab = 1 },
                    text = { Text("Preview") }
                )
            }

            when (tab) {
                0 -> BuildTab(
                    viewModel,
                    findings,
                    reportTitle,
                    testerName,
                    targetSystem,
                    editTitle,
                    { editTitle = it },
                    editTester,
                    { editTester = it },
                    editTarget,
                    { editTarget = it },
                    { viewModel.setReportMetadata(editTitle, editTester, editTarget) },
                    showAddFinding,
                    { showAddFinding = it }
                )
                1 -> PreviewTab(viewModel, generatedReport)
            }
        }
    }
}

@Composable
fun BuildTab(
    viewModel: ReportGeneratorViewModel,
    findings: List<com.example.flipperdroid.reports.Finding>,
    reportTitle: String,
    testerName: String,
    targetSystem: String,
    editTitle: String,
    onTitleChange: (String) -> Unit,
    editTester: String,
    onTesterChange: (String) -> Unit,
    editTarget: String,
    onTargetChange: (String) -> Unit,
    onMetadataSave: () -> Unit,
    showAddFinding: Boolean,
    onAddFindingChange: (Boolean) -> Unit
) {
    LazyColumn(
        modifier = Modifier
            .fillMaxSize()
            .padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        item {
            Card {
                Column(modifier = Modifier.padding(16.dp)) {
                    Text("Report Metadata", style = MaterialTheme.typography.titleSmall)

                    OutlinedTextField(
                        value = editTitle,
                        onValueChange = onTitleChange,
                        label = { Text("Report Title") },
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(top = 8.dp)
                    )

                    OutlinedTextField(
                        value = editTester,
                        onValueChange = onTesterChange,
                        label = { Text("Tester Name") },
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(top = 8.dp)
                    )

                    OutlinedTextField(
                        value = editTarget,
                        onValueChange = onTargetChange,
                        label = { Text("Target System") },
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(top = 8.dp)
                    )

                    Button(
                        onClick = onMetadataSave,
                        modifier = Modifier
                            .align(Alignment.End)
                            .padding(top = 8.dp)
                    ) {
                        Text("Save")
                    }
                }
            }
        }

        item {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text("Findings: ${findings.size}", style = MaterialTheme.typography.titleSmall)
                Button(onClick = { onAddFindingChange(!showAddFinding) }) {
                    Icon(Icons.Default.Add, null)
                    Spacer(Modifier.width(4.dp))
                    Text("Add")
                }
            }
        }

        if (showAddFinding) {
            item {
                AddFindingForm(viewModel) { onAddFindingChange(false) }
            }
        }

        items(findings) { finding ->
            Card(modifier = Modifier.fillMaxWidth()) {
                Column(modifier = Modifier.padding(12.dp)) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(
                            finding.title,
                            style = MaterialTheme.typography.labelMedium
                        )
                        AssistChip(
                            onClick = {},
                            label = { Text(finding.severity.name, fontSize = MaterialTheme.typography.labelSmall.fontSize) }
                        )
                    }

                    Text(
                        finding.description,
                        style = MaterialTheme.typography.labelSmall,
                        modifier = Modifier.padding(top = 8.dp)
                    )

                    IconButton(
                        onClick = { viewModel.removeFinding(finding.id) },
                        modifier = Modifier.align(Alignment.End)
                    ) {
                        Icon(Icons.Default.Delete, null)
                    }
                }
            }
        }
    }
}

@Composable
fun AddFindingForm(viewModel: ReportGeneratorViewModel, onDone: () -> Unit) {
    var title by remember { mutableStateOf("") }
    var severity by remember { mutableStateOf(Severity.MEDIUM) }
    var description by remember { mutableStateOf("") }
    var impact by remember { mutableStateOf("") }
    var remediation by remember { mutableStateOf("") }
    var evidence by remember { mutableStateOf("") }

    Card {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp)
                .verticalScroll(rememberScrollState())
        ) {
            Text("New Finding", style = MaterialTheme.typography.titleSmall)

            OutlinedTextField(
                value = title,
                onValueChange = { title = it },
                label = { Text("Title") },
                modifier = Modifier.fillMaxWidth()
            )

            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(top = 8.dp),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                Severity.values().forEach { sev ->
                    Button(
                        onClick = { severity = sev },
                        modifier = Modifier.weight(1f)
                    ) {
                        Text(sev.name.take(3), fontSize = MaterialTheme.typography.labelSmall.fontSize)
                    }
                }
            }

            OutlinedTextField(
                value = description,
                onValueChange = { description = it },
                label = { Text("Description") },
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(top = 8.dp),
                minLines = 2
            )

            OutlinedTextField(
                value = impact,
                onValueChange = { impact = it },
                label = { Text("Impact") },
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(top = 8.dp),
                minLines = 2
            )

            OutlinedTextField(
                value = remediation,
                onValueChange = { remediation = it },
                label = { Text("Remediation") },
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(top = 8.dp),
                minLines = 2
            )

            OutlinedTextField(
                value = evidence,
                onValueChange = { evidence = it },
                label = { Text("Evidence/Proof") },
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(top = 8.dp),
                minLines = 3
            )

            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(top = 12.dp),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                Button(
                    onClick = {
                        if (title.isNotEmpty()) {
                            viewModel.addFinding(title, severity, description, impact, remediation, evidence)
                            onDone()
                        }
                    },
                    modifier = Modifier.weight(1f)
                ) {
                    Text("Add")
                }
                Button(
                    onClick = onDone,
                    modifier = Modifier.weight(1f)
                ) {
                    Text("Cancel")
                }
            }
        }
    }
}

@Composable
fun PreviewTab(viewModel: ReportGeneratorViewModel, generatedReport: String) {
    LazyColumn(
        modifier = Modifier
            .fillMaxSize()
            .padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        item {
            val stats = viewModel.getReportStats()
            Card {
                Column(modifier = Modifier.padding(16.dp)) {
                    Text("Summary", style = MaterialTheme.typography.titleSmall)
                    Text(
                        "Findings: ${stats["total_findings"]} | " +
                        "Critical: ${stats["critical"]} | " +
                        "High: ${stats["high"]}",
                        style = MaterialTheme.typography.labelSmall,
                        modifier = Modifier.padding(top = 8.dp)
                    )
                }
            }
        }

        item {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                Button(
                    onClick = { viewModel.generateMarkdownReport() },
                    modifier = Modifier.weight(1f)
                ) {
                    Text("Markdown")
                }
                Button(
                    onClick = { viewModel.generateHtmlReport() },
                    modifier = Modifier.weight(1f)
                ) {
                    Text("HTML")
                }
                Button(
                    onClick = { viewModel.generateJsonReport() },
                    modifier = Modifier.weight(1f)
                ) {
                    Text("JSON")
                }
            }
        }

        if (generatedReport.isNotEmpty()) {
            item {
                Card(modifier = Modifier.heightIn(min = 300.dp)) {
                    Text(
                        generatedReport,
                        style = MaterialTheme.typography.labelSmall,
                        fontFamily = FontFamily.Monospace,
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(12.dp)
                    )
                }
            }
        }
    }
}
