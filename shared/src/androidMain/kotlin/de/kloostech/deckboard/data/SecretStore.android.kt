package de.kloostech.deckboard.data

import android.content.Context
import android.security.keystore.KeyGenParameterSpec
import android.security.keystore.KeyProperties
import android.util.Base64
import java.security.KeyStore
import javax.crypto.Cipher
import javax.crypto.KeyGenerator
import javax.crypto.SecretKey
import javax.crypto.spec.GCMParameterSpec

/** Gives the secret store the application context; call once from the Activity before the UI is built. */
object AndroidAppContext {
    @Volatile
    internal var app: Context? = null

    fun init(context: Context) {
        app = context.applicationContext
    }
}

/** Android: AES-GCM with a key that lives in the Android Keystore; only the ciphertext is stored in app preferences. */
actual fun createSecretStore(): SecretStore =
    // No context (Compose previews, tests): keep secrets in memory rather than crash.
    AndroidAppContext.app?.let { AndroidKeystoreSecretStore(it) } ?: InMemorySecretStore()

class AndroidKeystoreSecretStore(context: Context) : SecretStore {
    private val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)

    override fun get(key: String): String? {
        val stored = prefs.getString(key, null) ?: return null
        return runCatching {
            val bytes = Base64.decode(stored, Base64.NO_WRAP)
            val cipher = Cipher.getInstance(TRANSFORMATION)
            cipher.init(Cipher.DECRYPT_MODE, secretKey(), GCMParameterSpec(TAG_BITS, bytes.copyOfRange(0, IV_BYTES)))
            String(cipher.doFinal(bytes, IV_BYTES, bytes.size - IV_BYTES), Charsets.UTF_8)
        }.getOrNull() // key gone (restored backup, reset keystore): treat as not set, the user signs in again
    }

    override fun set(key: String, value: String) {
        val cipher = Cipher.getInstance(TRANSFORMATION)
        cipher.init(Cipher.ENCRYPT_MODE, secretKey())
        val encrypted = cipher.iv + cipher.doFinal(value.toByteArray(Charsets.UTF_8))
        prefs.edit().putString(key, Base64.encodeToString(encrypted, Base64.NO_WRAP)).apply()
    }

    override fun remove(key: String) {
        prefs.edit().remove(key).apply()
    }

    private fun secretKey(): SecretKey {
        val keyStore = KeyStore.getInstance(ANDROID_KEYSTORE).apply { load(null) }
        (keyStore.getKey(KEY_ALIAS, null) as? SecretKey)?.let { return it }
        val generator = KeyGenerator.getInstance(KeyProperties.KEY_ALGORITHM_AES, ANDROID_KEYSTORE)
        generator.init(
            KeyGenParameterSpec.Builder(KEY_ALIAS, KeyProperties.PURPOSE_ENCRYPT or KeyProperties.PURPOSE_DECRYPT)
                .setBlockModes(KeyProperties.BLOCK_MODE_GCM)
                .setEncryptionPaddings(KeyProperties.ENCRYPTION_PADDING_NONE)
                .setKeySize(256)
                .build(),
        )
        return generator.generateKey()
    }

    private companion object {
        const val PREFS_NAME = "deckboard_secrets"
        const val ANDROID_KEYSTORE = "AndroidKeyStore"
        const val KEY_ALIAS = "deckboard_secret_key"
        const val TRANSFORMATION = "AES/GCM/NoPadding"
        const val IV_BYTES = 12
        const val TAG_BITS = 128
    }
}
