package it.bosler.requestcsr

import android.net.Uri
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.selection.SelectionContainer
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.viewmodel.compose.viewModel

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContent {
            RequestCSRTheme {
                Scaffold(modifier = Modifier.fillMaxSize()) { innerPadding ->
                    CSRScreen(modifier = Modifier.padding(innerPadding))
                }
            }
        }
    }
}

@Composable
fun CSRScreen(
    modifier: Modifier = Modifier,
    viewModel: CSRViewModel = viewModel()
) {
    val state by viewModel.state.collectAsState()
    val context = LocalContext.current

    val saveFileLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.CreateDocument("application/x-pem-file")
    ) { uri: Uri? ->
        if (uri != null) {
            try {
                context.contentResolver.openOutputStream(uri)?.use { stream ->
                    stream.write(state.csrPem.toByteArray())
                }
                viewModel.onCSRSaved()
            } catch (e: Exception) {
                viewModel.onCSRSaveError(e.message ?: "Unknown error")
            }
        }
    }

    Column(
        modifier = modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        Text(
            text = "Request CSR",
            style = MaterialTheme.typography.headlineLarge,
            fontWeight = FontWeight.Bold,
        )

        Text(
            text = "Generate a hardware-backed key and CSR for OpenVPN client certificates.",
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )

        Spacer(modifier = Modifier.height(4.dp))

        // Key Alias
        OutlinedTextField(
            value = state.keyAlias,
            onValueChange = { viewModel.updateKeyAlias(it) },
            label = { Text("Key Alias") },
            singleLine = true,
            modifier = Modifier.fillMaxWidth(),
        )

        // Common Name
        OutlinedTextField(
            value = state.commonName,
            onValueChange = { viewModel.updateCommonName(it) },
            label = { Text("Common Name (CN)") },
            placeholder = { Text("e.g. client1") },
            singleLine = true,
            modifier = Modifier.fillMaxWidth(),
        )

        // Key status card
        if (state.keyExists) {
            Card(
                modifier = Modifier.fillMaxWidth(),
                colors = CardDefaults.cardColors(
                    containerColor = MaterialTheme.colorScheme.secondaryContainer,
                ),
            ) {
                Column(modifier = Modifier.padding(16.dp)) {
                    Text(
                        text = "Key Status",
                        style = MaterialTheme.typography.titleSmall,
                        fontWeight = FontWeight.Bold,
                    )
                    Spacer(modifier = Modifier.height(4.dp))
                    Text(
                        text = "Alias: ${state.keyAlias}",
                        style = MaterialTheme.typography.bodyMedium,
                    )
                    Text(
                        text = "Security: ${state.securityLevel}",
                        style = MaterialTheme.typography.bodyMedium,
                    )
                }
            }
        }

        // Action buttons
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            Button(
                onClick = { viewModel.generateKey() },
                enabled = !state.isLoading,
                modifier = Modifier.weight(1f),
            ) {
                Text("Generate Key")
            }

            Button(
                onClick = { viewModel.generateCSR() },
                enabled = !state.isLoading && state.keyExists && state.commonName.isNotBlank(),
                modifier = Modifier.weight(1f),
            ) {
                Text("Generate CSR")
            }
        }

        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            OutlinedButton(
                onClick = {
                    val filename = "${state.commonName.ifBlank { state.keyAlias }}.csr.pem"
                    saveFileLauncher.launch(filename)
                },
                enabled = state.csrPem.isNotBlank(),
                modifier = Modifier.weight(1f),
            ) {
                Text("Save CSR")
            }

            OutlinedButton(
                onClick = { viewModel.deleteKey() },
                enabled = !state.isLoading && state.keyExists,
                colors = ButtonDefaults.outlinedButtonColors(
                    contentColor = MaterialTheme.colorScheme.error,
                ),
                modifier = Modifier.weight(1f),
            ) {
                Text("Delete Key")
            }
        }

        // Loading indicator
        if (state.isLoading) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.Center,
                verticalAlignment = Alignment.CenterVertically,
            ) {
                CircularProgressIndicator()
                Spacer(modifier = Modifier.width(12.dp))
                Text("Please wait...")
            }
        }

        // Status message
        if (state.statusMessage.isNotBlank()) {
            Card(
                modifier = Modifier.fillMaxWidth(),
                colors = CardDefaults.cardColors(
                    containerColor = if (state.statusMessage.contains("failed", ignoreCase = true) ||
                        state.statusMessage.contains("error", ignoreCase = true)
                    ) {
                        MaterialTheme.colorScheme.errorContainer
                    } else {
                        MaterialTheme.colorScheme.primaryContainer
                    },
                ),
            ) {
                Text(
                    text = state.statusMessage,
                    modifier = Modifier.padding(16.dp),
                    style = MaterialTheme.typography.bodyMedium,
                )
            }
        }

        // CSR output
        if (state.csrPem.isNotBlank()) {
            Text(
                text = "CSR (PEM)",
                style = MaterialTheme.typography.titleSmall,
                fontWeight = FontWeight.Bold,
            )
            Card(
                modifier = Modifier.fillMaxWidth(),
                colors = CardDefaults.cardColors(
                    containerColor = MaterialTheme.colorScheme.surfaceVariant,
                ),
            ) {
                SelectionContainer {
                    Text(
                        text = state.csrPem,
                        modifier = Modifier.padding(12.dp),
                        fontFamily = FontFamily.Monospace,
                        fontSize = 10.sp,
                        lineHeight = 14.sp,
                    )
                }
            }
        }
    }
}
