package it.bosler.requestcsr

import android.os.Build
import android.security.keystore.KeyGenParameterSpec
import android.security.keystore.KeyInfo
import android.security.keystore.KeyProperties
import org.bouncycastle.asn1.x500.X500Name
import org.bouncycastle.operator.ContentSigner
import org.bouncycastle.asn1.ASN1ObjectIdentifier
import org.bouncycastle.asn1.x509.AlgorithmIdentifier
import org.bouncycastle.pkcs.PKCS10CertificationRequestBuilder
import org.bouncycastle.pkcs.jcajce.JcaPKCS10CertificationRequestBuilder
import org.bouncycastle.operator.jcajce.JcaContentSignerBuilder
import java.io.ByteArrayOutputStream
import java.io.OutputStream
import java.security.KeyFactory
import java.security.KeyPairGenerator
import java.security.KeyStore
import java.security.PrivateKey
import java.security.Signature
import java.security.cert.X509Certificate
import java.util.Base64

object KeystoreManager {

    private const val KEYSTORE_PROVIDER = "AndroidKeyStore"

    /**
     * Generate an RSA 4096 key pair in the Android Keystore.
     * The key is non-extractable and marked for signing.
     */
    fun generateKeyPair(alias: String) {
        val keyStore = KeyStore.getInstance(KEYSTORE_PROVIDER)
        keyStore.load(null)

        // Delete existing key if present
        if (keyStore.containsAlias(alias)) {
            keyStore.deleteEntry(alias)
        }

        val parameterSpec = KeyGenParameterSpec.Builder(
            alias,
            KeyProperties.PURPOSE_SIGN or KeyProperties.PURPOSE_VERIFY
        )
            .setKeySize(4096)
            .setDigests(
                KeyProperties.DIGEST_SHA256,
                KeyProperties.DIGEST_SHA384,
                KeyProperties.DIGEST_SHA512
            )
            .setSignaturePaddings(KeyProperties.SIGNATURE_PADDING_RSA_PKCS1)
            .setUserAuthenticationRequired(false)
            .build()

        val keyPairGenerator = KeyPairGenerator.getInstance(
            KeyProperties.KEY_ALGORITHM_RSA,
            KEYSTORE_PROVIDER
        )
        keyPairGenerator.initialize(parameterSpec)
        keyPairGenerator.generateKeyPair()
    }

    /**
     * Check whether the key with the given alias exists in the keystore.
     */
    fun keyExists(alias: String): Boolean {
        val keyStore = KeyStore.getInstance(KEYSTORE_PROVIDER)
        keyStore.load(null)
        return keyStore.containsAlias(alias)
    }

    /**
     * Delete the key with the given alias from the keystore.
     */
    fun deleteKey(alias: String) {
        val keyStore = KeyStore.getInstance(KEYSTORE_PROVIDER)
        keyStore.load(null)
        if (keyStore.containsAlias(alias)) {
            keyStore.deleteEntry(alias)
        }
    }

    /**
     * Determine whether the key is stored in hardware (TEE / StrongBox / Titan M2).
     * Returns a human-readable description.
     */
    fun getKeySecurityLevel(alias: String): String {
        val keyStore = KeyStore.getInstance(KEYSTORE_PROVIDER)
        keyStore.load(null)

        val key = keyStore.getKey(alias, null) as? PrivateKey
            ?: return "Key not found"

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
                if (keyInfo.isInsideSecureHardware) {
                    "Hardware-backed (TEE)"
                } else {
                    "Software (NOT hardware-backed)"
                }
            }
        } catch (e: Exception) {
            "Could not determine: ${e.message}"
        }
    }

    /**
     * Generate a PKCS#10 CSR using the hardware-backed private key.
     * Returns the CSR in PEM format.
     */
    fun generateCSR(alias: String, commonName: String): String {
        val keyStore = KeyStore.getInstance(KEYSTORE_PROVIDER)
        keyStore.load(null)

        val privateKey = keyStore.getKey(alias, null) as? PrivateKey
            ?: throw IllegalStateException("Private key not found for alias: $alias")

        val certificate = keyStore.getCertificate(alias) as? X509Certificate
            ?: throw IllegalStateException("Certificate not found for alias: $alias")

        val publicKey = certificate.publicKey

        val subject = X500Name("CN=$commonName")

        // Use a custom ContentSigner that delegates to Android Keystore
        val contentSigner = AndroidKeystoreContentSigner(privateKey)

        val csrBuilder = JcaPKCS10CertificationRequestBuilder(subject, publicKey)
        val csr = csrBuilder.build(contentSigner)

        val pemBytes = csr.encoded
        val base64 = Base64.getMimeEncoder(64, "\n".toByteArray())
            .encodeToString(pemBytes)

        return "-----BEGIN CERTIFICATE REQUEST-----\n$base64\n-----END CERTIFICATE REQUEST-----\n"
    }
}

/**
 * ContentSigner implementation that uses an Android Keystore private key.
 * This is needed because Bouncy Castle's JcaContentSignerBuilder doesn't work
 * with Android Keystore keys directly (they require the AndroidKeyStore provider).
 */
private class AndroidKeystoreContentSigner(
    private val privateKey: PrivateKey
) : ContentSigner {

    private val outputStream = ByteArrayOutputStream()

    override fun getAlgorithmIdentifier(): AlgorithmIdentifier {
        // SHA256withRSA OID: 1.2.840.113549.1.1.11
        return AlgorithmIdentifier(ASN1ObjectIdentifier("1.2.840.113549.1.1.11"))
    }

    override fun getOutputStream(): OutputStream = outputStream

    override fun getSignature(): ByteArray {
        val signature = Signature.getInstance("SHA256withRSA")
        signature.initSign(privateKey)
        signature.update(outputStream.toByteArray())
        return signature.sign()
    }
}
