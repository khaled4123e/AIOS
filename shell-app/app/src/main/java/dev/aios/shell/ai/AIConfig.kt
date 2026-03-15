// Copyright 2026 AIOS Contributors
// SPDX-License-Identifier: Apache-2.0

package dev.aios.shell.ai

import android.content.Context
import android.content.SharedPreferences
import android.security.keystore.KeyGenParameterSpec
import android.security.keystore.KeyProperties
import android.util.Base64
import android.util.Log
import java.security.KeyStore
import javax.crypto.Cipher
import javax.crypto.KeyGenerator
import javax.crypto.SecretKey
import javax.crypto.spec.GCMParameterSpec

/**
 * Persistente KI-Konfiguration mit SharedPreferences.
 *
 * Speichert und laedt alle Einstellungen fuer die verschiedenen LLM-Backends.
 * API-Schluessel werden mit dem Android KeyStore verschluesselt gespeichert.
 */
class AIConfig(context: Context) {

    companion object {
        private const val TAG = "AIConfig"
        private const val PREFS_NAME = "aios_ai_config"
        private const val KEYSTORE_ALIAS = "aios_ai_key"

        // SharedPreferences-Schluessel
        private const val KEY_BACKEND_TYPE = "aios_ai_backend_type"
        private const val KEY_API_TYPE = "aios_ai_api_type"
        private const val KEY_API_KEY_ENCRYPTED = "aios_ai_api_key_enc"
        private const val KEY_API_KEY_IV = "aios_ai_api_key_iv"
        private const val KEY_API_MODEL = "aios_ai_api_model"
        private const val KEY_CUSTOM_URL = "aios_ai_custom_url"
        private const val KEY_LOCAL_MODEL = "aios_ai_local_model"
        private const val KEY_REMOTE_SERVER_URL = "aios_ai_remote_server_url"
        private const val KEY_TEMPERATURE = "aios_ai_temperature"
        private const val KEY_MAX_TOKENS = "aios_ai_max_tokens"
        private const val KEY_CONTEXT_SIZE = "aios_ai_context_size"
    }

    private val prefs: SharedPreferences =
        context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)

    // --- Backend-Typ ---

    var backendType: BackendType
        get() {
            val value = prefs.getString(KEY_BACKEND_TYPE, BackendType.LOCAL.name)
            return try {
                BackendType.valueOf(value ?: BackendType.LOCAL.name)
            } catch (e: IllegalArgumentException) {
                BackendType.LOCAL
            }
        }
        set(value) {
            prefs.edit().putString(KEY_BACKEND_TYPE, value.name).apply()
        }

    // --- API-Typ ---

    var apiType: APIType
        get() {
            val value = prefs.getString(KEY_API_TYPE, APIType.CLAUDE.name)
            return try {
                APIType.valueOf(value ?: APIType.CLAUDE.name)
            } catch (e: IllegalArgumentException) {
                APIType.CLAUDE
            }
        }
        set(value) {
            prefs.edit().putString(KEY_API_TYPE, value.name).apply()
        }

    // --- API-Schluessel (verschluesselt) ---

    var apiKey: String
        get() = decryptApiKey()
        set(value) {
            encryptAndSaveApiKey(value)
        }

    // --- API-Modellname ---

    var apiModel: String
        get() = prefs.getString(KEY_API_MODEL, "") ?: ""
        set(value) {
            prefs.edit().putString(KEY_API_MODEL, value).apply()
        }

    // --- Benutzerdefinierte URL ---

    var customUrl: String
        get() = prefs.getString(KEY_CUSTOM_URL, "") ?: ""
        set(value) {
            prefs.edit().putString(KEY_CUSTOM_URL, value).apply()
        }

    // --- Remote-Server-URL ---

    var remoteServerUrl: String
        get() = prefs.getString(KEY_REMOTE_SERVER_URL, "http://10.0.2.2:8085") ?: "http://10.0.2.2:8085"
        set(value) {
            prefs.edit().putString(KEY_REMOTE_SERVER_URL, value).apply()
        }

    // --- Lokales Modell ---

    var localModelFilename: String
        get() = prefs.getString(KEY_LOCAL_MODEL, "") ?: ""
        set(value) {
            prefs.edit().putString(KEY_LOCAL_MODEL, value).apply()
        }

    // --- Generierungsparameter ---

    var temperature: Float
        get() = prefs.getFloat(KEY_TEMPERATURE, 0.7f)
        set(value) {
            prefs.edit().putFloat(KEY_TEMPERATURE, value).apply()
        }

    var maxTokens: Int
        get() = prefs.getInt(KEY_MAX_TOKENS, 500)
        set(value) {
            prefs.edit().putInt(KEY_MAX_TOKENS, value).apply()
        }

    var contextSize: Int
        get() = prefs.getInt(KEY_CONTEXT_SIZE, 2048)
        set(value) {
            prefs.edit().putInt(KEY_CONTEXT_SIZE, value).apply()
        }

    // --- Verschluesselung mit Android KeyStore ---

    private fun getOrCreateSecretKey(): SecretKey {
        val keyStore = KeyStore.getInstance("AndroidKeyStore")
        keyStore.load(null)

        if (keyStore.containsAlias(KEYSTORE_ALIAS)) {
            val entry = keyStore.getEntry(KEYSTORE_ALIAS, null) as KeyStore.SecretKeyEntry
            return entry.secretKey
        }

        val keyGenerator = KeyGenerator.getInstance(
            KeyProperties.KEY_ALGORITHM_AES,
            "AndroidKeyStore"
        )
        keyGenerator.init(
            KeyGenParameterSpec.Builder(
                KEYSTORE_ALIAS,
                KeyProperties.PURPOSE_ENCRYPT or KeyProperties.PURPOSE_DECRYPT
            )
                .setBlockModes(KeyProperties.BLOCK_MODE_GCM)
                .setEncryptionPaddings(KeyProperties.ENCRYPTION_PADDING_NONE)
                .setKeySize(256)
                .build()
        )
        return keyGenerator.generateKey()
    }

    private fun encryptAndSaveApiKey(apiKey: String) {
        if (apiKey.isBlank()) {
            prefs.edit()
                .remove(KEY_API_KEY_ENCRYPTED)
                .remove(KEY_API_KEY_IV)
                .apply()
            return
        }

        try {
            val secretKey = getOrCreateSecretKey()
            val cipher = Cipher.getInstance("AES/GCM/NoPadding")
            cipher.init(Cipher.ENCRYPT_MODE, secretKey)

            val encryptedBytes = cipher.doFinal(apiKey.toByteArray(Charsets.UTF_8))
            val iv = cipher.iv

            prefs.edit()
                .putString(KEY_API_KEY_ENCRYPTED, Base64.encodeToString(encryptedBytes, Base64.NO_WRAP))
                .putString(KEY_API_KEY_IV, Base64.encodeToString(iv, Base64.NO_WRAP))
                .apply()
        } catch (e: Exception) {
            Log.e(TAG, "Fehler beim Verschluesseln des API-Schluessels: ${e.message}", e)
        }
    }

    private fun decryptApiKey(): String {
        val encryptedBase64 = prefs.getString(KEY_API_KEY_ENCRYPTED, null) ?: return ""
        val ivBase64 = prefs.getString(KEY_API_KEY_IV, null) ?: return ""

        return try {
            val secretKey = getOrCreateSecretKey()
            val cipher = Cipher.getInstance("AES/GCM/NoPadding")
            val iv = Base64.decode(ivBase64, Base64.NO_WRAP)
            cipher.init(Cipher.DECRYPT_MODE, secretKey, GCMParameterSpec(128, iv))

            val encryptedBytes = Base64.decode(encryptedBase64, Base64.NO_WRAP)
            String(cipher.doFinal(encryptedBytes), Charsets.UTF_8)
        } catch (e: Exception) {
            Log.e(TAG, "Fehler beim Entschluesseln des API-Schluessels: ${e.message}", e)
            ""
        }
    }

    /**
     * Setzt alle Einstellungen auf Standardwerte zurueck.
     */
    fun resetToDefaults() {
        prefs.edit().clear().apply()
        // KeyStore-Schluessel bleibt erhalten
    }
}
