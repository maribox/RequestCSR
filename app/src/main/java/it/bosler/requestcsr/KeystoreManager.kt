package it.bosler.requestcsr

import android.content.Context
import android.os.Build
import android.security.keystore.KeyInfo
import android.security.keystore.KeyProperties
import org.bouncycastle.asn1.x500.X500Name
import org.bouncycastle.operator.jcajce.JcaContentSignerBuilder
import org.bouncycastle.pkcs.jcajce.JcaPKCS10CertificationRequestBuilder
import java.io.ByteArrayOutputStream
import java.io.File
import java.security.KeyFactory
import java.security.KeyPair
import java.security.KeyPairGenerator
import java.security.KeyStore
import java.security.PrivateKey
import java.security.cert.CertificateFactory
import java.security.cert.X509Certificate
import java.security.spec.PKCS8EncodedKeySpec
import java.util.Base64

object KeystoreManager {

    private const val KEYSTORE_PROVIDER = "AndroidKeyStore"
    private const val PENDING_KEYS_DIR = "pending_keys"

    // ── In-memory key generation (for system KeyChain flow) ──

    fun generateKeyPairInMemory(): KeyPair {
        val kpg = KeyPairGenerator.getInstance("RSA")
        kpg.initialize(4096)
        return kpg.generateKeyPair()
    }

    fun generateCSR(keyPair: KeyPair, commonName: String): String {
        val subject = X500Name("CN=$commonName")
        val signer = JcaContentSignerBuilder("SHA256withRSA").build(keyPair.private)
        val csr = JcaPKCS10CertificationRequestBuilder(subject, keyPair.public).build(signer)
        val base64 = Base64.getMimeEncoder(64, "\n".toByteArray()).encodeToString(csr.encoded)
        return "-----BEGIN CERTIFICATE REQUEST-----\n$base64\n-----END CERTIFICATE REQUEST-----\n"
    }

    // ── Pending key storage (app-internal, temporary until KeyChain install) ──

    fun savePrivateKey(context: Context, alias: String, key: PrivateKey) {
        val dir = File(context.filesDir, PENDING_KEYS_DIR)
        dir.mkdirs()
        File(dir, "$alias.key").writeBytes(key.encoded)
    }

    fun loadPrivateKey(context: Context, alias: String): PrivateKey? {
        val file = File(context.filesDir, "$PENDING_KEYS_DIR/$alias.key")
        if (!file.exists()) return null
        val keySpec = PKCS8EncodedKeySpec(file.readBytes())
        return KeyFactory.getInstance("RSA").generatePrivate(keySpec)
    }

    fun deletePrivateKey(context: Context, alias: String) {
        File(context.filesDir, "$PENDING_KEYS_DIR/$alias.key").delete()
    }

    fun listPendingKeys(context: Context): List<String> {
        val dir = File(context.filesDir, PENDING_KEYS_DIR)
        if (!dir.exists()) return emptyList()
        return dir.listFiles()
            ?.filter { it.extension == "key" }
            ?.map { it.nameWithoutExtension }
            ?: emptyList()
    }

    // ── PKCS#12 creation (for KeyChain install) ──

    fun createPkcs12(
        privateKey: PrivateKey,
        signedCertBytes: ByteArray,
        alias: String,
        password: String = "",
    ): ByteArray {
        val certFactory = CertificateFactory.getInstance("X.509")
        val cert = certFactory.generateCertificate(signedCertBytes.inputStream()) as X509Certificate
        val ks = KeyStore.getInstance("PKCS12")
        ks.load(null, null)
        ks.setKeyEntry(alias, privateKey, password.toCharArray(), arrayOf(cert))
        val baos = ByteArrayOutputStream()
        ks.store(baos, password.toCharArray())
        return baos.toByteArray()
    }

    // ── Android Keystore methods (for listing/cleanup of old keys) ──

    fun listKeystoreKeys(): List<String> {
        val ks = KeyStore.getInstance(KEYSTORE_PROVIDER)
        ks.load(null)
        return ks.aliases().toList()
    }

    fun deleteKeystoreKey(alias: String) {
        val ks = KeyStore.getInstance(KEYSTORE_PROVIDER)
        ks.load(null)
        if (ks.containsAlias(alias)) ks.deleteEntry(alias)
    }

    fun getKeySecurityLevel(alias: String): String {
        val ks = KeyStore.getInstance(KEYSTORE_PROVIDER)
        ks.load(null)
        val key = ks.getKey(alias, null) as? PrivateKey ?: return "Key not found"
        return try {
            val factory = KeyFactory.getInstance(key.algorithm, KEYSTORE_PROVIDER)
            val keyInfo = factory.getKeySpec(key, KeyInfo::class.java)
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
                when (keyInfo.securityLevel) {
                    KeyProperties.SECURITY_LEVEL_STRONGBOX -> "StrongBox (hardware)"
                    KeyProperties.SECURITY_LEVEL_TRUSTED_ENVIRONMENT -> "TEE (hardware)"
                    KeyProperties.SECURITY_LEVEL_SOFTWARE -> "Software (NOT hardware-backed)"
                    KeyProperties.SECURITY_LEVEL_UNKNOWN_SECURE -> "Unknown secure hardware"
                    else -> "Unknown security level"
                }
            } else {
                @Suppress("DEPRECATION")
                if (keyInfo.isInsideSecureHardware) "Hardware-backed (TEE)" else "Software (NOT hardware-backed)"
            }
        } catch (e: Exception) {
            "Could not determine: ${e.message}"
        }
    }
}
