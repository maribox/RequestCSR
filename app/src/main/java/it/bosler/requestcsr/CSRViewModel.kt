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
)

data class CSRState(
    val keyAlias: String = "client-key",
    val clientName: String = "client",
    val keyExists: Boolean = false,
    val securityLevel: String = "",
    val csrPem: String = "",
    val statusMessage: String = "",
    val isLoading: Boolean = false,
    val readyToSave: Boolean = false,
    val showKeystore: Boolean = false,
    val keystoreKeys: List<KeyInfo> = emptyList(),
)

class CSRViewModel : ViewModel() {

    private val _state = MutableStateFlow(CSRState())
    val state: StateFlow<CSRState> = _state.asStateFlow()

    init {
        refreshKeyStatus()
    }

    fun updateClientName(name: String) {
        _state.value = _state.value.copy(clientName = name)
    }

    fun generateAndExport() {
        val alias = _state.value.keyAlias
        val name = _state.value.clientName.trim().ifBlank { "client" }

        _state.value = _state.value.copy(isLoading = true, statusMessage = "Generating RSA 4096 key pair...")

        viewModelScope.launch(Dispatchers.IO) {
            try {
                KeystoreManager.generateKeyPair(alias)
                val level = KeystoreManager.getKeySecurityLevel(alias)

                _state.value = _state.value.copy(statusMessage = "Generating CSR...")

                val pem = KeystoreManager.generateCSR(alias, name)

                _state.value = _state.value.copy(
                    isLoading = false,
                    keyExists = true,
                    securityLevel = level,
                    csrPem = pem,
                    readyToSave = true,
                    statusMessage = "Key generated ($level). Save the CSR file.",
                )
            } catch (e: Exception) {
                _state.value = _state.value.copy(
                    isLoading = false,
                    statusMessage = "Failed: ${e.message}",
                )
            }
        }
    }

    fun deleteKey() {
        val alias = _state.value.keyAlias

        viewModelScope.launch(Dispatchers.IO) {
            try {
                KeystoreManager.deleteKey(alias)
                _state.value = _state.value.copy(
                    keyExists = false,
                    securityLevel = "",
                    csrPem = "",
                    readyToSave = false,
                    statusMessage = "Key deleted",
                )
            } catch (e: Exception) {
                _state.value = _state.value.copy(
                    statusMessage = "Delete failed: ${e.message}",
                )
            }
        }
    }

    fun onCSRSaved() {
        _state.value = _state.value.copy(
            readyToSave = false,
            statusMessage = "CSR saved! Transfer it to your CA for signing.",
        )
    }

    fun onCSRSaveError(message: String) {
        _state.value = _state.value.copy(statusMessage = "Save failed: $message")
    }

    fun toggleKeystore() {
        val show = !_state.value.showKeystore
        if (show) {
            refreshKeystoreList()
        } else {
            _state.value = _state.value.copy(showKeystore = false)
        }
    }

    fun deleteKeystoreKey(alias: String) {
        viewModelScope.launch(Dispatchers.IO) {
            try {
                KeystoreManager.deleteKey(alias)
                refreshKeystoreList()
                // Also refresh main key status if it was our key
                if (alias == _state.value.keyAlias) {
                    _state.value = _state.value.copy(
                        keyExists = false,
                        securityLevel = "",
                        csrPem = "",
                        readyToSave = false,
                    )
                }
                _state.value = _state.value.copy(statusMessage = "Deleted key: $alias")
            } catch (e: Exception) {
                _state.value = _state.value.copy(statusMessage = "Delete failed: ${e.message}")
            }
        }
    }

    private fun refreshKeystoreList() {
        viewModelScope.launch(Dispatchers.IO) {
            val keys = KeystoreManager.listKeys().map { alias ->
                KeyInfo(alias, KeystoreManager.getKeySecurityLevel(alias))
            }
            _state.value = _state.value.copy(showKeystore = true, keystoreKeys = keys)
        }
    }

    private fun refreshKeyStatus() {
        val alias = _state.value.keyAlias
        viewModelScope.launch(Dispatchers.IO) {
            val exists = KeystoreManager.keyExists(alias)
            val level = if (exists) KeystoreManager.getKeySecurityLevel(alias) else ""
            _state.value = _state.value.copy(
                keyExists = exists,
                securityLevel = level,
            )
        }
    }
}
