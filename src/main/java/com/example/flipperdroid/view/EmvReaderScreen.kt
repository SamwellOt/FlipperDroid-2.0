package com.example.flipperdroid.view

import androidx.compose.foundation.layout.*
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.navigation.NavController
import com.example.flipperdroid.nfc.EmvCardData
import com.example.flipperdroid.viewmodel.EmvReaderViewModel

/**
 * Ecran de lecture de carte EMV
 *
 * @param navController controleur de navigation
 * @param viewModel vue modele gere la lecture des donnees de carte
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun EmvReaderScreen(
    navController: NavController,
    viewModel: EmvReaderViewModel
) {
    val cardData by viewModel.cardData.collectAsState()
    val isReading by viewModel.isReading.collectAsState()
    val error by viewModel.error.collectAsState()

    Scaffold(
        topBar = {
            /**
             * Barre superieure avec titre et bouton retour
             */
            TopAppBar(
                title = { Text("EMV Card Reader") },
                navigationIcon = {
                    IconButton(onClick = { navController.navigateUp() }) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back")
                    }
                }
            )
        }
    ) { paddingValues ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(paddingValues)
                .padding(16.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            val card = cardData
            when {
                isReading -> {
                    /**
                     * Affichage pendant la lecture de la carte
                     */
                    CircularProgressIndicator(
                        modifier = Modifier.size(48.dp)
                    )
                    Text(
                        text = "Reading card\nPlease keep the card steady",
                        textAlign = TextAlign.Center
                    )
                }
                card != null -> {
                    /**
                     * Affichage des donnees si la lecture a reussi
                     */
                    Icon(
                        imageVector = Icons.Default.CreditCard,
                        contentDescription = null,
                        modifier = Modifier.size(48.dp),
                        tint = MaterialTheme.colorScheme.primary
                    )
                    CardDataDisplay(cardData = card)
                    Button(
                        onClick = { viewModel.clearData() },
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Icon(Icons.Default.Refresh, contentDescription = null)
                        Spacer(modifier = Modifier.width(8.dp))
                        Text("Scan Another Card")
                    }
                }
                error != null -> {
                    /**
                     * Affichage du message derreur si la lecture echoue
                     */
                    Icon(
                        imageVector = Icons.Default.Error,
                        contentDescription = null,
                        modifier = Modifier.size(48.dp),
                        tint = MaterialTheme.colorScheme.error
                    )
                    Text(
                        text = error ?: "",
                        color = MaterialTheme.colorScheme.error,
                        textAlign = TextAlign.Center
                    )
                    Button(
                        onClick = { viewModel.clearData() },
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Icon(Icons.Default.Refresh, contentDescription = null)
                        Spacer(modifier = Modifier.width(8.dp))
                        Text("Try Again")
                    }
                }
                else -> {
                    /**
                     * Etat initial en attente de carte
                     */
                    Icon(
                        imageVector = Icons.Default.CreditCard,
                        contentDescription = null,
                        modifier = Modifier.size(72.dp),
                        tint = MaterialTheme.colorScheme.primary
                    )
                    Text(
                        text = "Hold a contactless payment card near the device",
                        style = MaterialTheme.typography.bodyLarge,
                        textAlign = TextAlign.Center
                    )
                }
            }
        }
    }
}

/**
 * Affiche les informations de la carte EMV
 *
 * @param cardData donnees de la carte a afficher
 */
@Composable
fun CardDataDisplay(cardData: EmvCardData) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surfaceVariant
        )
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            // Avertissement RGPD
            Text(
                text = "⚠️ Les données affichées sont sensibles. Ne partagez pas ces informations. (RGPD)",
                color = MaterialTheme.colorScheme.error,
                style = MaterialTheme.typography.labelSmall,
                fontWeight = FontWeight.Bold,
                modifier = Modifier.padding(bottom = 8.dp)
            )
            cardData.cardType?.let {
                CardDataRow(
                    icon = Icons.Default.CreditCard,
                    label = "Card Type",
                    value = it
                )
            }
            cardData.pan?.let {
                // Masquer le PAN sauf les 4 derniers chiffres
                val masked = if (it.length > 4) "**** **** **** " + it.takeLast(4) else it
                CardDataRow(
                    icon = Icons.Default.Payment,
                    label = "Card Number",
                    value = masked
                )
            }
            cardData.expiryDate?.let {
                CardDataRow(
                    icon = Icons.Default.DateRange,
                    label = "Expiry Date",
                    value = it
                )
            }
            cardData.cardholderName?.let {
                CardDataRow(
                    icon = Icons.Default.Person,
                    label = "Cardholder",
                    value = it
                )
            }
        }
    }
}

/**
 * Affiche une ligne dinformation avec icone etiquette et valeur
 *
 * @param icon icone affichee
 * @param label etiquette du champ
 * @param value valeur du champ
 */
@Composable
fun CardDataRow(
    icon: ImageVector,
    label: String,
    value: String
) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically
    ) {
        Row(
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Icon(
                imageVector = icon,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.primary
            )
            Text(
                text = label,
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
        Text(
            text = value,
            style = MaterialTheme.typography.bodyMedium,
            fontWeight = FontWeight.Bold,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
    }
}
