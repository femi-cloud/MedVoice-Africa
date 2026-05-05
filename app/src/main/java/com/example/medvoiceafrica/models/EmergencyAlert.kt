package com.example.medvoiceafrica.models

data class EmergencyAlert(
    val id: String,
    val keywords: List<String>,
    val question_fr: String,
    val conseil_fr: String,
    val conseil_fon: String
)