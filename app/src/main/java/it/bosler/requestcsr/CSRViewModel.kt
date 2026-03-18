package it.bosler.requestcsr

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

data class PendingKeyInfo(
    val alias: String,
)

data class KeystoreKeyInfo(
    val alias: String,
    val securityLevel: String,
)

data class CSRState(
    val clientName: String = "client",
    val csrPem: String = "",
    val statusMessage: String = "",
    val isError: Boolean = false,
    val isLoading: Boolean = false,
    val readyToSave: Boolean = false,
    val pendingKeys: List<PendingKeyInfo> = emptyList(),
    val keystoreKeys: List<KeystoreKeyInfo> = emptyList(),
    val pkcs12ToInstall: ByteArray? = null,
    val pkcs12Alias: String = "",
)

class CSRViewModel(application: Application) : AndroidViewModel(application) {

    private val context get() = getApplication<Application>().applicationContext
    private val _state = MutableStateFlow(CSRState())
    val state: StateFlow<CSRState> = _state.asStateFlow()

    init {
        cleanupLegacyKeys()
        refreshKeys()
    }

    private fun cleanupLegacyKeys() {
        viewModelScope.launch(Dispatchers.IO) {
            KeystoreManager.listKeystoreKeys().forEach { alias ->
                KeystoreManager.deleteKeystoreKey(alias)
            }
        }
    }

    fun updateClientName(name: String) {
        _state.value = _state.value.copy(clientName = name)
    }

    fun generateAndExport() {
        val name = _state.value.clientName.trim().ifBlank { "client" }

        _state.value = _state.value.copy(
            isLoading = true,
            statusMessage = "Generating RSA 4096 key pair...",
            isError = false,
        )

        viewModelScope.launch(Dispatchers.IO) {
            try {
                val keyPair = KeystoreManager.generateKeyPairInMemory()
                _state.value = _state.value.copy(statusMessage = "Generating CSR...")

                val pem = KeystoreManager.generateCSR(keyPair, name)
                KeystoreManager.savePrivateKey(context, name, keyPair.private)

                _state.value = _state.value.copy(
                    isLoading = false,
                    csrPem = pem,
                    readyToSave = true,
                    statusMessage = "Save the CSR file.",
                    isError = false,
                )
                refreshKeys()
            } catch (e: Exception) {
                _state.value = _state.value.copy(
                    isLoading = false,
                    statusMessage = "Failed: ${e.message}",
                    isError = true,
                )
            }
        }
    }

    fun importSignedCert(alias: String, certBytes: ByteArray) {
        viewModelScope.launch(Dispatchers.IO) {
            try {
                val privateKey = KeystoreManager.loadPrivateKey(context, alias)
                    ?: throw IllegalStateException("No pending key found for: $alias")

                val pkcs12 = KeystoreManager.createPkcs12(privateKey, certBytes, alias)

                _state.value = _state.value.copy(
                    pkcs12ToInstall = pkcs12,
                    pkcs12Alias = alias,
                    statusMessage = "Installing certificate to system KeyChain...",
                    isError = false,
                )
            } catch (e: Exception) {
                _state.value = _state.value.copy(
                    statusMessage = "Import failed: ${e.message}",
                    isError = true,
                )
            }
        }
    }

    fun onKeyChainInstallComplete(success: Boolean) {
        val alias = _state.value.pkcs12Alias
        if (success) {
            KeystoreManager.deletePrivateKey(context, alias)
            _state.value = _state.value.copy(
                pkcs12ToInstall = null,
                pkcs12Alias = "",
                statusMessage = "Certificate installed to system KeyChain! Select '$alias' in OpenVPN.",
                isError = false,
            )
            refreshKeys()
        } else {
            _state.value = _state.value.copy(
                pkcs12ToInstall = null,
                pkcs12Alias = "",
                statusMessage = "KeyChain installation cancelled. Tap import to try again.",
                isError = true,
            )
        }
    }

    fun deletePendingKey(alias: String) {
        viewModelScope.launch(Dispatchers.IO) {
            KeystoreManager.deletePrivateKey(context, alias)
            _state.value = _state.value.copy(
                statusMessage = "Deleted pending key: $alias",
                isError = false,
            )
            refreshKeys()
        }
    }

    fun deleteKeystoreKey(alias: String) {
        viewModelScope.launch(Dispatchers.IO) {
            try {
                KeystoreManager.deleteKeystoreKey(alias)
                _state.value = _state.value.copy(
                    statusMessage = "Deleted: $alias",
                    isError = false,
                )
                refreshKeys()
            } catch (e: Exception) {
                _state.value = _state.value.copy(
                    statusMessage = "Delete failed: ${e.message}",
                    isError = true,
                )
            }
        }
    }

    fun onCSRSaved() {
        _state.value = _state.value.copy(
            readyToSave = false,
            statusMessage = "CSR saved! Get it signed by a CA, then import the signed certificate.",
            isError = false,
        )
    }

    fun onCSRSaveError(message: String) {
        _state.value = _state.value.copy(statusMessage = "Save failed: $message", isError = true)
    }

    private fun refreshKeys() {
        viewModelScope.launch(Dispatchers.IO) {
            val pending = KeystoreManager.listPendingKeys(context).map { PendingKeyInfo(it) }
            val keystore = KeystoreManager.listKeystoreKeys().map { alias ->
                KeystoreKeyInfo(
                    alias = alias,
                    securityLevel = KeystoreManager.getKeySecurityLevel(alias),
                )
            }
            _state.value = _state.value.copy(
                pendingKeys = pending,
                keystoreKeys = keystore,
            )
        }
    }
}
