package com.macroresearch.data

import android.content.Context
import android.security.keystore.KeyGenParameterSpec
import android.security.keystore.KeyProperties
import android.util.Base64
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import java.net.URI
import java.security.KeyStore
import javax.crypto.Cipher
import javax.crypto.KeyGenerator
import javax.crypto.SecretKey
import javax.crypto.spec.GCMParameterSpec

data class TranslationSettings(
    val configured: Boolean,
    val baseUrl: String,
    val model: String,
)

/** Stores the user-supplied translation credential encrypted by Android Keystore. */
class TranslationPreferences(context: Context) {
    private val preferences = context.getSharedPreferences(PREFERENCES_NAME, Context.MODE_PRIVATE)
    private val _settings = MutableStateFlow(loadSettings())
    val settings: StateFlow<TranslationSettings> = _settings.asStateFlow()

    fun apiKey(): String? {
        val encrypted = preferences.getString(KEY_CIPHERTEXT, null) ?: return null
        val iv = preferences.getString(KEY_IV, null) ?: return null
        return runCatching {
            val cipher = Cipher.getInstance(TRANSFORMATION)
            cipher.init(
                Cipher.DECRYPT_MODE,
                secretKey(),
                GCMParameterSpec(128, Base64.decode(iv, Base64.NO_WRAP)),
            )
            cipher.doFinal(Base64.decode(encrypted, Base64.NO_WRAP)).toString(Charsets.UTF_8)
        }.getOrElse {
            clearCredential()
            null
        }?.takeIf { it.isNotBlank() }
    }

    fun save(apiKey: String, baseUrl: String, model: String) {
        val cleanKey = apiKey.trim()
        require(cleanKey.isNotEmpty()) { "API key is required" }
        val cleanUrl = normalizeBaseUrl(baseUrl)
        val cleanModel = model.trim()
        require(cleanModel.isNotEmpty()) { "Model is required" }

        val cipher = Cipher.getInstance(TRANSFORMATION)
        cipher.init(Cipher.ENCRYPT_MODE, secretKey())
        val encrypted = cipher.doFinal(cleanKey.toByteArray(Charsets.UTF_8))
        preferences.edit()
            .putString(KEY_CIPHERTEXT, Base64.encodeToString(encrypted, Base64.NO_WRAP))
            .putString(KEY_IV, Base64.encodeToString(cipher.iv, Base64.NO_WRAP))
            .putString(KEY_BASE_URL, cleanUrl)
            .putString(KEY_MODEL, cleanModel)
            .apply()
        _settings.value = TranslationSettings(true, cleanUrl, cleanModel)
    }

    fun updateProvider(baseUrl: String, model: String) {
        check(apiKey() != null) { "Save an API key first" }
        val cleanUrl = normalizeBaseUrl(baseUrl)
        val cleanModel = model.trim()
        require(cleanModel.isNotEmpty()) { "Model is required" }
        preferences.edit()
            .putString(KEY_BASE_URL, cleanUrl)
            .putString(KEY_MODEL, cleanModel)
            .apply()
        _settings.value = TranslationSettings(true, cleanUrl, cleanModel)
    }

    fun clear() {
        preferences.edit().clear().apply()
        _settings.value = TranslationSettings(false, DEFAULT_BASE_URL, DEFAULT_MODEL)
    }

    private fun clearCredential() {
        preferences.edit().remove(KEY_CIPHERTEXT).remove(KEY_IV).apply()
    }

    private fun loadSettings(): TranslationSettings {
        val baseUrl = preferences.getString(KEY_BASE_URL, DEFAULT_BASE_URL).orEmpty()
            .ifBlank { DEFAULT_BASE_URL }
        val model = preferences.getString(KEY_MODEL, DEFAULT_MODEL).orEmpty()
            .ifBlank { DEFAULT_MODEL }
        return TranslationSettings(apiKey() != null, baseUrl, model)
    }

    private fun secretKey(): SecretKey {
        val keyStore = KeyStore.getInstance(KEYSTORE).apply { load(null) }
        (keyStore.getKey(KEY_ALIAS, null) as? SecretKey)?.let { return it }
        return KeyGenerator.getInstance(KeyProperties.KEY_ALGORITHM_AES, KEYSTORE).run {
            init(
                KeyGenParameterSpec.Builder(
                    KEY_ALIAS,
                    KeyProperties.PURPOSE_ENCRYPT or KeyProperties.PURPOSE_DECRYPT,
                )
                    .setBlockModes(KeyProperties.BLOCK_MODE_GCM)
                    .setEncryptionPaddings(KeyProperties.ENCRYPTION_PADDING_NONE)
                    .setRandomizedEncryptionRequired(true)
                    .build(),
            )
            generateKey()
        }
    }

    companion object {
        const val DEFAULT_BASE_URL = "https://api.openai.com/v1"
        const val DEFAULT_MODEL = "gpt-4o-mini"

        private const val PREFERENCES_NAME = "translation_credentials"
        private const val KEY_CIPHERTEXT = "api_key_ciphertext"
        private const val KEY_IV = "api_key_iv"
        private const val KEY_BASE_URL = "base_url"
        private const val KEY_MODEL = "model"
        private const val KEYSTORE = "AndroidKeyStore"
        private const val KEY_ALIAS = "macro_translation_api_key_v1"
        private const val TRANSFORMATION = "AES/GCM/NoPadding"

        internal fun normalizeBaseUrl(value: String): String {
            val normalized = value.trim().trimEnd('/')
            val uri = runCatching { URI(normalized) }
                .getOrElse { throw IllegalArgumentException("Invalid API endpoint") }
            require(uri.scheme.equals("https", ignoreCase = true) && !uri.host.isNullOrBlank()) {
                "API endpoint must use HTTPS"
            }
            require(uri.userInfo == null && uri.query == null && uri.fragment == null) {
                "API endpoint must not contain credentials, query, or fragment"
            }
            return normalized
        }
    }
}
