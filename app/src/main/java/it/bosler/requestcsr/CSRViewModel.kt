package it.bosler.requestcsr

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

data class CSRState(
    val keyAlias: String = "openvpn-client",
    val commonName: String = "",
    val keyExists: Boolean = false,
    val securityLevel: String = "",
    val csrPem: String = "",
    val statusMessage: String = "",
    val isLoading: Boolean = false,
)

class CSRViewModel : ViewModel() {

    private val _state = MutableStateFlow(CSRState())
    val state: StateFlow<CSRState> = _state.asStateFlow()

    init {
        refreshKeyStatus()
    }

    fun updateKeyAlias(alias: String) {
        _state.value = _state.value.copy(keyAlias = alias)
        refreshKeyStatus()
    }

    fun updateCommonName(cn: String) {
        _state.value = _state.value.copy(commonName = cn)
    }

    fun generateKey() {
        val alias = _state.value.keyAlias.trim()
        if (alias.isBlank()) {
            _state.value = _state.value.copy(statusMessage = "Key alias cannot be empty")
            return
        }

        _state.value = _state.value.copy(isLoading = true, statusMessage = "Generating RSA 4096 key pair...")

        viewModelScope.launch(Dispatchers.IO) {
            try {
                KeystoreManager.generateKeyPair(alias)
                val level = KeystoreManager.getKeySecurityLevel(alias)
                _state.value = _state.value.copy(
                    isLoading = false,
                    keyExists = true,
                    securityLevel = level,
                    csrPem = "",
                    statusMessage = "Key pair generated successfully"
                )
            } catch (e: Exception) {
                _state.value = _state.value.copy(
                    isLoading = false,
                    statusMessage = "Key generation failed: ${e.message}"
                )
            }
        }
    }

    fun generateCSR() {
        val alias = _state.value.keyAlias.trim()
        val cn = _state.value.commonName.trim()

        if (cn.isBlank()) {
            _state.value = _state.value.copy(statusMessage = "Common Name cannot be empty")
            return
        }
        if (!_state.value.keyExists) {
            _state.value = _state.value.copy(statusMessage = "Generate a key pair first")
            return
        }

        _state.value = _state.value.copy(isLoading = true, statusMessage = "Generating CSR...")

        viewModelScope.launch(Dispatchers.IO) {
            try {
                val pem = KeystoreManager.generateCSR(alias, cn)
                _state.value = _state.value.copy(
                    isLoading = false,
                    csrPem = pem,
                    statusMessage = "CSR generated successfully"
                )
            } catch (e: Exception) {
                _state.value = _state.value.copy(
                    isLoading = false,
                    statusMessage = "CSR generation failed: ${e.message}"
                )
            }
        }
    }

    fun deleteKey() {
        val alias = _state.value.keyAlias.trim()

        _state.value = _state.value.copy(isLoading = true, statusMessage = "Deleting key...")

        viewModelScope.launch(Dispatchers.IO) {
            try {
                KeystoreManager.deleteKey(alias)
                _state.value = _state.value.copy(
                    isLoading = false,
                    keyExists = false,
                    securityLevel = "",
                    csrPem = "",
                    statusMessage = "Key deleted successfully"
                )
            } catch (e: Exception) {
                _state.value = _state.value.copy(
                    isLoading = false,
                    statusMessage = "Key deletion failed: ${e.message}"
                )
            }
        }
    }

    fun onCSRSaved() {
        _state.value = _state.value.copy(statusMessage = "CSR saved to file")
    }

    fun onCSRSaveError(message: String) {
        _state.value = _state.value.copy(statusMessage = "Save failed: $message")
    }

    private fun refreshKeyStatus() {
        val alias = _state.value.keyAlias.trim()
        if (alias.isBlank()) return

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
