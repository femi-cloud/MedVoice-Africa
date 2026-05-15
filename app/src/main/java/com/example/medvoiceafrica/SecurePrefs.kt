package com.example.medvoiceafrica


import android.content.Context
import android.content.SharedPreferences
import androidx.security.crypto.EncryptedSharedPreferences
import androidx.security.crypto.MasterKey

object SecurePrefs {

    // Nom du fichier de prefs chiffrées sur le disque
    private const val PREFS_FILE_NAME = "medvoice_secure"

    @Volatile
    private var instance: SharedPreferences? = null

    /**
     * Retourne l'instance unique des SharedPreferences chiffrées.
     * Thread-safe via double-checked locking.
     *
     * En cas d'échec (appareil sans Keystore, rare) → retourne
     * des SharedPreferences normales en fallback plutôt que de crasher.
     */
    fun get(context: Context): SharedPreferences {
        return instance ?: synchronized(this) {
            instance ?: createInstance(context).also { instance = it }
        }
    }

    private fun createInstance(context: Context): SharedPreferences {
        return try {
            val masterKey = MasterKey.Builder(context)
                .setKeyScheme(MasterKey.KeyScheme.AES256_GCM)
                .build()

            EncryptedSharedPreferences.create(
                context,
                PREFS_FILE_NAME,
                masterKey,
                EncryptedSharedPreferences.PrefKeyEncryptionScheme.AES256_SIV,
                EncryptedSharedPreferences.PrefValueEncryptionScheme.AES256_GCM
            )
        } catch (e: Exception) {
            // Fallback non-chiffré si le Keystore est indisponible
            // (ex: émulateur sans hardware keystore, très rare en prod)
            android.util.Log.e(
                "SecurePrefs",
                "EncryptedSharedPreferences indisponible, fallback non-chiffré: ${e.message}"
            )
            context.getSharedPreferences(PREFS_FILE_NAME + "_plain", Context.MODE_PRIVATE)
        }
    }

    /**
     * Réinitialise l'instance (utile pour les tests unitaires).
     * NE PAS appeler en production.
     */
    fun resetForTesting() {
        instance = null
    }
}