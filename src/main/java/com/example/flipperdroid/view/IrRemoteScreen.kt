package com.example.flipperdroid.view

import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.FileOpen
import androidx.compose.material.icons.filled.PowerSettingsNew
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.navigation.NavController
import com.example.flipperdroid.viewmodel.IrRemoteViewModel
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

/**
 * Télécommande universelle basée sur une base de codes .ir (Flipper).
 * Choisir un remote empaqueté ou importer un fichier .ir (ex: Flipper-IRDB).
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun IrRemoteScreen(
    navController: NavController,
    viewModel: IrRemoteViewModel = viewModel()
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val remotes by viewModel.remotes.collectAsState()
    val currentRemote by viewModel.currentRemote.collectAsState()
    val buttons by viewModel.buttons.collectAsState()
    val status by viewModel.status.collectAsState()

    LaunchedEffect(Unit) { viewModel.loadAssetList() }

    val importLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.OpenDocument()
    ) { uri ->
        if (uri != null) {
            scope.launch {
                val text = withContext(Dispatchers.IO) {
                    try {
                        context.contentResolver.openInputStream(uri)?.bufferedReader()?.use { it.readText() }
                    } catch (e: Exception) { null }
                }
                val name = uri.lastPathSegment?.substringAfterLast('/') ?: "imported.ir"
                if (text != null) viewModel.loadFromText(name, text)
            }
        }
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("IR Remotes") },
                navigationIcon = {
                    IconButton(onClick = { navController.navigateUp() }) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back")
                    }
                },
                actions = {
                    IconButton(onClick = { importLauncher.launch(arrayOf("*/*")) }) {
                        Icon(Icons.Default.FileOpen, contentDescription = "Import .ir")
                    }
                    IconButton(onClick = { viewModel.powerSweep() }) {
                        Icon(Icons.Default.PowerSettingsNew, contentDescription = "Power sweep")
                    }
                }
            )
        }
    ) { paddingValues ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(paddingValues)
                .padding(16.dp)
        ) {
            // Sélecteur de remote
            Row(
                Modifier.horizontalScroll(rememberScrollState()),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                remotes.forEach { remote ->
                    FilterChip(
                        selected = currentRemote == remote,
                        onClick = { viewModel.loadRemote(remote) },
                        label = { Text(remote.removeSuffix(".ir")) }
                    )
                }
            }

            if (status.isNotEmpty()) {
                Spacer(Modifier.height(8.dp))
                Text(status, style = MaterialTheme.typography.bodySmall)
            }

            Spacer(Modifier.height(12.dp))

            if (buttons.isEmpty()) {
                Box(Modifier.fillMaxSize(), contentAlignment = androidx.compose.ui.Alignment.Center) {
                    Text(
                        "Select a remote above, or import a .ir file (Flipper-IRDB).",
                        textAlign = TextAlign.Center,
                        style = MaterialTheme.typography.bodyLarge
                    )
                }
            } else {
                LazyVerticalGrid(
                    columns = GridCells.Fixed(3),
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    verticalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    items(buttons) { button ->
                        ElevatedButton(
                            onClick = { viewModel.transmit(button) },
                            modifier = Modifier
                                .fillMaxWidth()
                                .height(64.dp)
                        ) {
                            Text(button.name, textAlign = TextAlign.Center, style = MaterialTheme.typography.labelLarge)
                        }
                    }
                }
            }
        }
    }
}
