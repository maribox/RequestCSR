package it.bosler.requestcsr

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

data class KeyInfo(
    val alias: String,
    val securityLevel: String,
    val certSubject: String? = null,
    val certIssuer: String? = null,
)

data class CSRState(
    val clientName: String = "client",
    val csrPem: String = "",
    val statusMessage: String = "",
    val isError: Boolean = false,
    val isLoading: Boolean = false,
    val readyToSave: Boolean = false,
    val keystoreKeys: List<KeyInfo> = emptyList(),
)

class CSRViewModel : ViewModel() {

    private val _state = MutableStateFlow(CSRState())
    val state: StateFlow<CSRState> = _state.asStateFlow()

    private val keyAlias = "client-key"

    init {
        refreshKeystoreList()
    }

    fun updateClientName(name: String) {
        _state.value = _state.value.copy(clientName = name)
    }

    fun generateAndExport() {
        val name = _state.value.clientName.trim().ifBlank { "client" }

        _state.value = _state.value.copy(isLoading = true, statusMessage = "Generating RSA 4096 key pair...", isError = false)

        viewModelScope.launch(Dispatchers.IO) {
            try {
                KeystoreManager.generateKeyPair(keyAlias)
                _state.value = _state.value.copy(statusMessage = "Generating CSR...")

                val pem = KeystoreManager.generateCSR(keyAlias, name)

                _state.value = _state.value.copy(
                    isLoading = false,
                    csrPem = pem,
                    readyToSave = true,
                    statusMessage = "Save the CSR file.",
                    isError = false,
                )
                refreshKeystoreList()
            } catch (e: Exception) {
                _state.value = _state.value.copy(
                    isLoading = false,
                    statusMessage = "Failed: ${e.message}",
                    isError = true,
                )
                refreshKeystoreList()
            }
        }
    }

    fun deleteKeystoreKey(alias: String) {
        viewModelScope.launch(Dispatchers.IO) {
            try {
                KeystoreManager.deleteKey(alias)
                _state.value = _state.value.copy(
                    statusMessage = "Deleted: $alias",
                    isError = false,
                )
                if (alias == keyAlias) {
                    _state.value = _state.value.copy(csrPem = "", readyToSave = false)
                }
                refreshKeystoreList()
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
            statusMessage = "CSR saved! Transfer it to your CA for signing.",
            isError = false,
        )
    }

    fun onCSRSaveError(message: String) {
        _state.value = _state.value.copy(statusMessage = "Save failed: $message", isError = true)
    }

    fun importSignedCert(alias: String, certBytes: ByteArray) {
        viewModelScope.launch(Dispatchers.IO) {
            try {
                KeystoreManager.importSignedCertificate(alias, certBytes)
                _state.value = _state.value.copy(
                    statusMessage = "Signed certificate imported for: $alias",
                    isError = false,
                )
                refreshKeystoreList()
            } catch (e: Exception) {
                _state.value = _state.value.copy(
                    statusMessage = "Import failed: ${e.message}",
                    isError = true,
                )
            }
        }
    }

    private fun refreshKeystoreList() {
        viewModelScope.launch(Dispatchers.IO) {
            val keys = KeystoreManager.listKeys().map { alias ->
                KeyInfo(
                    alias = alias,
                    securityLevel = KeystoreManager.getKeySecurityLevel(alias),
                    certSubject = KeystoreManager.getCertificateSubject(alias),
                    certIssuer = KeystoreManager.getCertificateIssuer(alias),
                )
            }
            _state.value = _state.value.copy(keystoreKeys = keys)
        }
    }
}
