package com.example.medvoiceafrica

import android.content.Context
import android.content.Intent
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.RectF
import android.graphics.Typeface
import android.graphics.pdf.PdfDocument
import androidx.core.content.FileProvider
import java.io.File
import java.io.FileOutputStream
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

// ═══════════════════════════════════════════════════════════════════
// PdfReportEngine.kt — Rapport de consultation MedVoice Africa
// Structure en 6 sections selon cahier de charges
// ═══════════════════════════════════════════════════════════════════
object PdfReportEngine {

    private const val PAGE_W    = 595   // A4 largeur en points (72 dpi)
    private const val PAGE_H    = 842   // A4 hauteur
    private const val ML        = 48f   // Marge gauche
    private const val MR        = 547f  // Marge droite (PAGE_W - 48)
    private const val COL_W     = MR - ML

    // ── Palette couleurs ─────────────────────────────────────────────
    private val C_ROUGE   = Color.rgb(220, 50,  47)
    private val C_JAUNE   = Color.rgb(203, 153,  0)
    private val C_VERT    = Color.rgb( 29, 158, 117)
    private val C_ACCENT  = Color.rgb( 29, 158, 117)  // #1D9E75 — même couleur app
    private val C_DARK    = Color.rgb( 17,  17,  17)
    private val C_GREY    = Color.rgb(100, 100, 100)
    private val C_LIGHT   = Color.rgb(240, 240, 240)
    private val C_WHITE   = Color.WHITE
    private val C_WARN    = Color.rgb(220,  50,  47)
    private val C_ORANGE  = Color.rgb(255, 140,   0)
    private val C_BLUE    = Color.rgb( 33, 100, 200)

    // ── Données du rapport ───────────────────────────────────────────
    data class ReportData(
        // Section 1 — En-tête
        val consultationId: Long,          // Room PK
        val timestamp: Long,               // System.currentTimeMillis()
        val isOffline: Boolean,

        // Section 2 — Profil patient
        val patientName: String = "Anonyme",
        val weightKg: Float? = null,
        val ageYears: Int? = null,
        val ageMonths: Int? = null,        // si < 2 ans
        val knownAllergies: String? = null,
        val currentTreatments: List<String> = emptyList(),

        // Section 3 — Triage
        val triage: String,                // "ROUGE" | "JAUNE" | "VERT"
        val symptomsReported: String,      // 1er message utilisateur
        val sessionTitle: String,

        // Section 4 — Protocole de soins
        val dosageResult: DosageResult? = null,

        // Section 5 — Sécurité
        val drugInteraction: String? = null,   // ex: "Aspirine + Warfarine"
        val interactionSeverity: String? = null, // "ROUGE" | "JAUNE"
        val conseilFon: String? = null,         // conseil_fon depuis fon.json

        // Section 6 — Agent
        val agentName: String = ""
    )

    // ════════════════════════════════════════════════════════════════
    // POINT D'ENTRÉE — génère le fichier PDF et retourne sa référence
    // ════════════════════════════════════════════════════════════════
    fun generateReport(context: Context, data: ReportData): File? {
        return try {
            val doc      = PdfDocument()
            val pageInfo = PdfDocument.PageInfo.Builder(PAGE_W, PAGE_H, 1).create()
            val page     = doc.startPage(pageInfo)
            val canvas   = page.canvas

            var y = 0f
            y = drawHeader(canvas, data, y)
            y = drawPatientProfile(canvas, data, y)
            y = drawClinicalAnalysis(canvas, data, y)
            y = drawCareProtocol(canvas, data, y)
            y = drawSafetySection(canvas, data, y)
            drawFooter(canvas, data)

            doc.finishPage(page)

            val ts   = SimpleDateFormat("yyyyMMdd_HHmmss", Locale.FRANCE).format(Date())
            val file = File(context.getExternalFilesDir(null), "MedVoice_$ts.pdf")
            doc.writeTo(FileOutputStream(file))
            doc.close()
            file

        } catch (e: Exception) {
            android.util.Log.e("PdfReportEngine", "Erreur PDF: ${e.message}")
            null
        }
    }

    // ════════════════════════════════════════════════════════════════
    // SECTION 1 — EN-TÊTE
    // ════════════════════════════════════════════════════════════════
    private fun drawHeader(canvas: Canvas, data: ReportData, startY: Float): Float {
        // Bandeau de fond
        val bgPaint = Paint().apply { color = C_DARK; isAntiAlias = true }
        canvas.drawRect(0f, 0f, PAGE_W.toFloat(), 72f, bgPaint)

        // Logo texte
        canvas.drawText(
            "MedVoice Africa",
            ML, 32f,
            paint(20f, C_WHITE, bold = true)
        )
        canvas.drawText(
            "Assistant médical de terrain · Protocoles OMS",
            ML, 50f,
            paint(9f, C_ACCENT)
        )

        // Statut connectivité (coin droit)
        val connText  = if (data.isOffline) "📵  MODE HORS-LIGNE" else "🌐  EN LIGNE"
        val connColor = if (data.isOffline) C_ORANGE else C_VERT
        canvas.drawText(connText, MR - 110f, 38f, paint(9f, connColor, bold = true))

        // Ligne sous bandeau
        var y = 82f
        canvas.drawLine(ML, y, MR, y, linePaint(C_ACCENT, 1.5f))
        y += 12f

        // Identifiant + Horodatage
        val dateStr = SimpleDateFormat("dd/MM/yyyy  HH:mm:ss", Locale.FRANCE)
            .format(Date(data.timestamp))
        canvas.drawText("ID Consultation :  #${data.consultationId}", ML, y, paint(9f, C_GREY))
        canvas.drawText("Date & Heure :  $dateStr", ML + 200f, y, paint(9f, C_GREY))
        y += 14f

        canvas.drawText(
            "Objet :  ${data.sessionTitle}",
            ML, y, paint(9f, C_DARK)
        )
        y += 18f

        canvas.drawLine(ML, y, MR, y, linePaint(C_LIGHT))
        return y + 10f
    }

    // ════════════════════════════════════════════════════════════════
    // SECTION 2 — PROFIL PATIENT
    // ════════════════════════════════════════════════════════════════
    private fun drawPatientProfile(canvas: Canvas, data: ReportData, startY: Float): Float {
        var y = drawSectionTitle(canvas, "2.  Profil du Patient", startY)

        // Carte de fond
        val cardTop = y
        y += 4f

        // Identité
        canvas.drawText("Nom / Identité :", ML + 4f, y, paint(9f, C_GREY))
        canvas.drawText(data.patientName, ML + 100f, y, paint(10f, C_DARK, bold = true))
        y += 16f

        // Poids + Âge côte à côte
        val weightTxt = data.weightKg?.let { "$it kg" } ?: "Non renseigné"
        val ageTxt = when {
            data.ageYears != null && data.ageMonths != null ->
                "${data.ageYears} ans  ${data.ageMonths} mois"
            data.ageYears != null -> "${data.ageYears} ans"
            data.ageMonths != null -> "${data.ageMonths} mois"
            else -> "Non renseigné"
        }
        canvas.drawText("Poids :", ML + 4f, y, paint(9f, C_GREY))
        canvas.drawText(weightTxt, ML + 55f, y, paint(10f, C_DARK, bold = true))
        canvas.drawText("Âge :", ML + 160f, y, paint(9f, C_GREY))
        canvas.drawText(ageTxt, ML + 195f, y, paint(10f, C_DARK, bold = true))
        y += 16f

        // Antécédents / traitements en cours
        if (data.currentTreatments.isNotEmpty()) {
            canvas.drawText("Traitements en cours :", ML + 4f, y, paint(9f, C_GREY))
            y += 13f
            val treatStr = data.currentTreatments.joinToString("  •  ")
            y = drawWrapped(canvas, treatStr, ML + 12f, y, COL_W - 12f, paint(9f, C_DARK))
        }

        // Allergies
        data.knownAllergies?.let {
            canvas.drawText("⚠  Allergies :", ML + 4f, y, paint(9f, C_WARN, bold = true))
            canvas.drawText(it, ML + 85f, y, paint(9f, C_WARN))
            y += 14f
        }

        // Bordure de la carte
        drawCardBorder(canvas, cardTop - 4f, y + 4f)
        y += 10f
        return y
    }

    // ════════════════════════════════════════════════════════════════
    // SECTION 3 — ANALYSE CLINIQUE & TRIAGE
    // ════════════════════════════════════════════════════════════════
    private fun drawClinicalAnalysis(canvas: Canvas, data: ReportData, startY: Float): Float {
        var y = drawSectionTitle(canvas, "3.  Analyse Clinique & Triage", startY)

        // Symptômes rapportés
        canvas.drawText("Symptômes rapportés :", ML + 4f, y, paint(9f, C_GREY))
        y += 13f
        y = drawWrapped(canvas, data.symptomsReported.take(350), ML + 10f, y,
            COL_W - 10f, paint(9.5f, C_DARK))
        y += 6f

        // ── Bandeau triage coloré ─────────────────────────────────
        val triageColor = triageColor(data.triage)
        val triageBgPaint = Paint().apply {
            color = triageColor; isAntiAlias = true; alpha = 230
        }
        val bandeauTop = y
        canvas.drawRoundRect(
            RectF(ML, bandeauTop, MR, bandeauTop + 36f),
            8f, 8f, triageBgPaint
        )

        val triageLabel = when (data.triage.uppercase()) {
            "ROUGE" -> "● TRIAGE ROUGE  —  URGENCE VITALE"
            "JAUNE" -> "● TRIAGE JAUNE  —  URGENCE STABLE"
            "VERT"  -> "● TRIAGE VERT  —  SURVEILLANCE"
            else    -> "● TRIAGE INCONNU"
        }
        canvas.drawText(triageLabel, ML + 12f, bandeauTop + 15f,
            paint(11f, C_WHITE, bold = true))

        val triageSub = when (data.triage.uppercase()) {
            "ROUGE" -> "Stabilisation immédiate requise · Alerte hôpital"
            "JAUNE" -> "Traitement de stabilisation · Réévaluation dans 24h"
            "VERT"  -> "Soins à domicile possibles · Suivi recommandé"
            else    -> ""
        }
        canvas.drawText(triageSub, ML + 12f, bandeauTop + 28f,
            paint(8.5f, C_WHITE))
        y = bandeauTop + 44f

        // ── Mention ROUGE critique ────────────────────────────────
        if (data.triage.uppercase() == "ROUGE") {
            y += 6f
            val urgPaint = Paint().apply {
                color = C_WARN; isAntiAlias = true
            }
            canvas.drawRoundRect(RectF(ML, y, MR, y + 24f), 4f, 4f,
                Paint().apply { color = Color.rgb(255, 230, 230); isAntiAlias = true })
            canvas.drawText(
                "⚠  TRANSFERT IMMÉDIAT VERS UN CENTRE DE SANTÉ REQUIS",
                ML + 8f, y + 16f,
                paint(10f, C_WARN, bold = true)
            )
            y += 32f
        }

        y += 8f
        return y
    }

    // ════════════════════════════════════════════════════════════════
    // SECTION 4 — PROTOCOLE DE SOINS
    // ════════════════════════════════════════════════════════════════
    private fun drawCareProtocol(canvas: Canvas, data: ReportData, startY: Float): Float {
        var y = drawSectionTitle(canvas, "4.  Protocole de Soins", startY)

        val dr = data.dosageResult
        if (dr == null) {
            canvas.drawText(
                "Aucun calcul de dosage effectué pour cette consultation.",
                ML + 4f, y, paint(9.5f, C_GREY)
            )
            y += 20f
        } else {
            val cardTop = y
            y += 6f

            // Nom du médicament
            canvas.drawText("Médicament :", ML + 4f, y, paint(9f, C_GREY))
            canvas.drawText(dr.medicineName, ML + 90f, y,
                paint(12f, C_ACCENT, bold = true))
            y += 18f

            if (dr.dosePerTake == "INTERDIT" || dr.dosePerTake == "STOP") {
                // Contre-indication
                val warnBg = Paint().apply { color = Color.rgb(255, 235, 235); isAntiAlias = true }
                canvas.drawRoundRect(RectF(ML + 4f, y, MR - 4f, y + 30f),
                    6f, 6f, warnBg)
                canvas.drawText(
                    "🚫  USAGE INTERDIT — CONTRE-INDIQUÉ",
                    ML + 10f, y + 19f,
                    paint(10f, C_WARN, bold = true)
                )
                y += 38f
            } else {
                // Trois pilules côte à côte
                val pilW  = (COL_W - 24f) / 3f
                val positions = listOf(ML + 4f, ML + 4f + pilW + 8f, ML + 4f + (pilW + 8f) * 2)
                val pilLabels = listOf("Dose", "Fréquence", "Durée")
                val pilValues = listOf(
                    dr.dosePerTake,
                    "${dr.frequencyPerDay}x / jour",
                    "${dr.durationDays} jours"
                )
                positions.forEachIndexed { i, px ->
                    val pilBg = Paint().apply { color = C_ACCENT; alpha = 25; isAntiAlias = true }
                    canvas.drawRoundRect(RectF(px, y, px + pilW, y + 38f),
                        8f, 8f, pilBg)
                    canvas.drawRoundRect(RectF(px, y, px + pilW, y + 38f),
                        8f, 8f, linePaint(C_ACCENT, 0.8f).apply { style = Paint.Style.STROKE })
                    canvas.drawText(
                        pilValues[i], px + pilW / 2f, y + 18f,
                        paint(12f, C_ACCENT, bold = true, align = Paint.Align.CENTER)
                    )
                    canvas.drawText(
                        pilLabels[i], px + pilW / 2f, y + 32f,
                        paint(8f, C_GREY, align = Paint.Align.CENTER)
                    )
                }
                y += 46f
            }

            // Instructions spéciales
            if (dr.specialInstructions.isNotBlank()) {
                y += 2f
                y = drawWrapped(canvas, "ℹ  ${dr.specialInstructions}", ML + 4f, y,
                    COL_W - 8f, paint(9f, C_GREY))
                y += 4f
            }

            // Avertissement OMS
            val omsBg = Paint().apply { color = Color.rgb(230, 247, 240); isAntiAlias = true }
            canvas.drawRoundRect(RectF(ML + 4f, y, MR - 4f, y + 20f),
                4f, 4f, omsBg)
            canvas.drawText(
                "✓  Calcul généré selon les protocoles OMS/PCIME — Validation clinique requise",
                ML + 8f, y + 14f, paint(8f, C_VERT)
            )
            y += 28f

            drawCardBorder(canvas, cardTop, y)
        }

        y += 10f
        return y
    }

    // ════════════════════════════════════════════════════════════════
    // SECTION 5 — SÉCURITÉ & INSTRUCTIONS EN LANGUES LOCALES
    // ════════════════════════════════════════════════════════════════
    private fun drawSafetySection(canvas: Canvas, data: ReportData, startY: Float): Float {
        var y = drawSectionTitle(canvas, "5.  Sécurité & Instructions Locales", startY)
        val cardTop = y

        // ── Interactions ─────────────────────────────────────────
        if (data.drugInteraction != null) {
            val sevColor = when (data.interactionSeverity?.uppercase()) {
                "ROUGE" -> C_WARN
                "JAUNE" -> C_ORANGE
                else    -> C_ORANGE
            }
            val sevLabel = when (data.interactionSeverity?.uppercase()) {
                "ROUGE" -> "INTERACTION DANGEREUSE — CONTRE-INDIQUÉ"
                "JAUNE" -> "INTERACTION À SURVEILLER"
                else    -> "INTERACTION DÉTECTÉE"
            }
            val intBg = Paint().apply {
                color = if (data.interactionSeverity == "ROUGE")
                    Color.rgb(255, 230, 230) else Color.rgb(255, 248, 220)
                isAntiAlias = true
            }
            canvas.drawRoundRect(RectF(ML + 2f, y, MR - 2f, y + 32f),
                6f, 6f, intBg)
            canvas.drawText("⚠  $sevLabel", ML + 8f, y + 13f,
                paint(9f, sevColor, bold = true))
            canvas.drawText(data.drugInteraction, ML + 8f, y + 26f,
                paint(9f, sevColor))
            y += 40f
        } else {
            canvas.drawText("✓  Aucune interaction médicamenteuse détectée.",
                ML + 4f, y, paint(9f, C_VERT))
            y += 16f
        }

        y += 4f

        // ── Conseils en Fon ───────────────────────────────────────
        if (data.conseilFon != null) {
            canvas.drawText("Conseils premiers secours en langue Fon :",
                ML + 4f, y, paint(9f, C_GREY))
            y += 13f

            val fonBg = Paint().apply {
                color = Color.rgb(240, 248, 255); isAntiAlias = true
            }
            val fonTop = y
            y = drawWrapped(canvas, data.conseilFon, ML + 10f, y + 6f,
                COL_W - 14f,
                paint(10f, C_DARK).apply { typeface = Typeface.create(Typeface.SERIF, Typeface.ITALIC) }
            )
            y += 6f
            canvas.drawRoundRect(RectF(ML + 2f, fonTop, MR - 2f, y),
                6f, 6f, fonBg)
            // Redessiner le texte par-dessus le fond (ordre de dessin)
            y = fonTop + 6f
            y = drawWrapped(canvas, data.conseilFon, ML + 10f, y,
                COL_W - 14f,
                paint(10f, C_BLUE).apply { typeface = Typeface.create(Typeface.SERIF, Typeface.ITALIC) }
            )
            y += 10f
        } else {
            canvas.drawText("Aucun conseil Fon disponible pour ce type d'urgence.",
                ML + 4f, y, paint(9f, C_GREY))
            y += 16f
        }

        drawCardBorder(canvas, cardTop, y + 4f)
        y += 14f
        return y
    }

    // ════════════════════════════════════════════════════════════════
    // SECTION 6 — PIED DE PAGE
    // ════════════════════════════════════════════════════════════════
    private fun drawFooter(canvas: Canvas, data: ReportData) {
        val footerY = PAGE_H - 90f

        // Ligne de séparation
        canvas.drawLine(ML, footerY, MR, footerY, linePaint(C_LIGHT, 1f))

        // Disclaimer
        val disclaimerText =
            "AVIS IMPORTANT : Ce rapport est un outil d'aide à la décision médicale de terrain. " +
                    "Il ne constitue pas un diagnostic médical final et ne remplace pas l'avis d'un professionnel de santé qualifié. " +
                    "Les dosages sont calculés selon les protocoles OMS/PCIME. La responsabilité clinique appartient à l'agent de santé."
        var dy = footerY + 12f
        dy = drawWrapped(canvas, disclaimerText, ML, dy, COL_W,
            paint(7.5f, C_GREY).apply { isAntiAlias = true })

        // Signature de l'agent
        dy += 4f
        canvas.drawText("Signature de l'agent de santé :", ML, dy, paint(8f, C_DARK))
        val sigName = if (data.agentName.isBlank()) "_______________________" else data.agentName
        canvas.drawText(sigName, ML + 155f, dy, paint(8f, C_DARK, bold = true))

        // Ligne de signature à remplir à la main
        if (data.agentName.isBlank()) {
            canvas.drawLine(ML + 152f, dy + 2f, ML + 280f, dy + 2f, linePaint(C_GREY, 0.6f))
        }

        // Bas de page
        canvas.drawText(
            "MedVoice Africa v1.0  •  Gemma 4 Edge AI  •  ${
                SimpleDateFormat("dd/MM/yyyy", Locale.FRANCE).format(Date(data.timestamp))
            }",
            PAGE_W / 2f, PAGE_H - 10f,
            paint(7.5f, C_GREY, align = Paint.Align.CENTER)
        )
    }

    // ════════════════════════════════════════════════════════════════
    // HELPERS INTERNES
    // ════════════════════════════════════════════════════════════════

    private fun drawSectionTitle(canvas: Canvas, title: String, y: Float): Float {
        // Fond titre
        val bgP = Paint().apply { color = Color.rgb(245, 245, 245); isAntiAlias = true }
        canvas.drawRect(ML - 4f, y, MR + 4f, y + 18f, bgP)
        // Trait accent gauche
        canvas.drawRect(ML - 4f, y, ML + 2f, y + 18f,
            Paint().apply { color = C_ACCENT; isAntiAlias = true })
        canvas.drawText(title.uppercase(), ML + 6f, y + 13f,
            paint(9f, C_DARK, bold = true))
        return y + 24f
    }

    private fun drawCardBorder(canvas: Canvas, top: Float, bottom: Float) {
        val strokeP = Paint().apply {
            color = C_LIGHT; style = Paint.Style.STROKE
            strokeWidth = 0.8f; isAntiAlias = true
        }
        canvas.drawRoundRect(RectF(ML, top, MR, bottom), 6f, 6f, strokeP)
    }

    private fun drawWrapped(
        canvas: Canvas, text: String,
        x: Float, startY: Float, maxWidth: Float, p: Paint
    ): Float {
        var y       = startY
        var remain  = text.replace(Regex("\\*+"), "").trim()
        while (remain.isNotEmpty()) {
            val count = p.breakText(remain, true, maxWidth, null)
            if (count <= 0) break
            canvas.drawText(remain.substring(0, count), x, y, p)
            remain = remain.substring(count).trimStart()
            y += (p.textSize * 1.45f)
        }
        return y
    }

    private fun triageColor(triage: String): Int = when (triage.uppercase()) {
        "ROUGE" -> C_ROUGE
        "JAUNE" -> C_JAUNE
        "VERT"  -> C_VERT
        else    -> C_GREY
    }

    private fun paint(
        size: Float,
        color: Int,
        bold: Boolean = false,
        align: Paint.Align = Paint.Align.LEFT
    ) = Paint().apply {
        this.textSize = size
        this.color    = color
        this.typeface = if (bold) Typeface.create(Typeface.DEFAULT, Typeface.BOLD)
        else Typeface.DEFAULT
        this.textAlign = align
        this.isAntiAlias = true
    }

    private fun linePaint(color: Int, width: Float = 0.5f) = Paint().apply {
        this.color       = color
        this.strokeWidth = width
        this.style       = Paint.Style.STROKE
        this.isAntiAlias = true
    }

    // ── Partage ──────────────────────────────────────────────────────
    fun sharePdf(context: Context, file: File) {
        val uri = FileProvider.getUriForFile(context, "${context.packageName}.provider", file)
        context.startActivity(
            Intent.createChooser(
                Intent(Intent.ACTION_SEND).apply {
                    type = "application/pdf"
                    putExtra(Intent.EXTRA_STREAM, uri)
                    addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
                },
                "Partager le rapport de consultation"
            )
        )
    }
}