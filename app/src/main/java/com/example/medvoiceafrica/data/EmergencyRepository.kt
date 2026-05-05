package com.example.medvoiceafrica.data

import android.content.Context
import com.example.medvoiceafrica.models.EmergencyAlert
import com.google.gson.Gson
import com.google.gson.reflect.TypeToken

class EmergencyRepository(private val context: Context) {

    // On charge la liste une seule fois en mémoire pour ne pas ralentir le téléphone
    private val alerts: List<EmergencyAlert> by lazy {
        val jsonString = context.assets.open("fon.json").bufferedReader().use { it.readText() }
        val typeToken = object : TypeToken<List<EmergencyAlert>>() {}.type
        Gson().fromJson(jsonString, typeToken)
    }

    fun findMatch(userInput: String): EmergencyAlert? {
        // 1. On enlève la ponctuation et on met en minuscules
        val cleanInput = userInput
            .lowercase()
            .replace(Regex("[.,!?;:]"), "")
            .trim()

        if (cleanInput.isBlank()) return null

        return alerts.find { alert ->
            // On vérifie si un des mots-clés est présent dans la saisie nettoyée
            alert.keywords.any { keyword -> cleanInput.contains(keyword.lowercase()) }
        }
    }
}