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

// ════════════════════════════════════════════════════════════════════
// PdfReportEngine.kt — FINAL CORRIGÉ
// Corrections vs version précédente :
//   - Bug P1 : gestion multi-pages (startNewPage + ensureSpace)
//   - Bug P2 : conseilFon dessiné 1 seule fois (dryRun sur drawWrapped)
// ════════════════════════════════════════════════════════════════════

object PdfReportEngine {

    private const val PAGE_W = 595
    private const val PAGE_H = 842
    private const val ML     = 48f
    private const val MR     = 547f
    private const val COL_W  = MR - ML

    private val C_ROUGE  = Color.rgb(220, 50,  47)
    private val C_JAUNE  = Color.rgb(203, 153,  0)
    private val C_VERT   = Color.rgb( 29, 158, 117)
    private val C_ACCENT = Color.rgb( 29, 158, 117)
    private val C_DARK   = Color.rgb( 17,  17,  17)
    private val C_GREY   = Color.rgb(100, 100, 100)
    private val C_LIGHT  = Color.rgb(240, 240, 240)
    private val C_WHITE  = Color.WHITE
    private val C_WARN   = Color.rgb(220,  50,  47)
    private val C_ORANGE = Color.rgb(255, 140,   0)
    private val C_BLUE   = Color.rgb( 33, 100, 200)

    // ── Toutes les chaînes bilingues ─────────────────────────────────
    private fun t(fr: String, en: String, lang: String) = if (lang == "fr") fr else en

    data class ReportData(
        val consultationId: Long,
        val timestamp: Long,
        val isOffline: Boolean,
        val lang: String = "fr",
        val patientName: String = "Anonyme",
        val weightKg: Float? = null,
        val ageYears: Int? = null,
        val ageMonths: Int? = null,
        val knownAllergies: String? = null,
        val currentTreatments: List<String> = emptyList(),
        val triage: String,
        val symptomsReported: String,
        val sessionTitle: String,
        val dosageResult: DosageResult? = null,
        val drugInteraction: String? = null,
        val interactionSeverity: String? = null,
        val conseilFon: String? = null,
        val agentName: String = ""
    )

    // ════════════════════════════════════════════════════════════════
    // GESTION MULTI-PAGES — état interne du rendu
    // ════════════════════════════════════════════════════════════════

    // Ces variables sont réinitialisées à chaque appel de generateReport
    private var doc: PdfDocument = PdfDocument()
    private var currentPage: PdfDocument.Page? = null
    private var currentCanvas: Canvas? = null
    private var currentPageNumber: Int = 0
    private lateinit var currentData: ReportData   // pour redessiner l'en-tête sur chaque page

    /**
     * Ferme la page courante et ouvre une nouvelle.
     * Redessine un mini-en-tête en haut de chaque nouvelle page (sauf la 1ère).
     * Retourne le nouveau Canvas et la position y de départ.
     */
    private fun startNewPage(): Pair<Canvas, Float> {
        // Fermer la page précédente
        currentPage?.let { doc.finishPage(it) }

        currentPageNumber++
        val pageInfo = PdfDocument.PageInfo.Builder(PAGE_W, PAGE_H, currentPageNumber).create()
        currentPage  = doc.startPage(pageInfo)
        currentCanvas = currentPage!!.canvas

        // Mini-en-tête de continuation sur page 2+
        var startY = 0f
        if (currentPageNumber > 1) {
            val bgPaint = Paint().apply { color = C_DARK; isAntiAlias = true }
            currentCanvas!!.drawRect(0f, 0f, PAGE_W.toFloat(), 30f, bgPaint)
            currentCanvas!!.drawText(
                "MedVoice Africa  —  Suite de rapport (#${currentData.consultationId})",
                ML, 20f, paint(8f, C_WHITE)
            )
            currentCanvas!!.drawLine(ML, 34f, MR, 34f, linePaint(C_ACCENT, 1f))
            startY = 44f
        }

        return Pair(currentCanvas!!, startY)
    }

    /**
     * Vérifie qu'il reste assez de place pour `neededSpace` pixels.
     * Si non, crée une nouvelle page.
     * Retourne le Canvas actif et le y courant (potentiellement remis à 0 sur nouvelle page).
     */
    private fun ensureSpace(y: Float, neededSpace: Float = 60f): Pair<Canvas, Float> {
        // Réserve 80px en bas pour le footer de la dernière page
        return if (y + neededSpace > PAGE_H - 80f) {
            startNewPage()
        } else {
            Pair(currentCanvas!!, y)
        }
    }

    // ════════════════════════════════════════════════════════════════
    fun generateReport(context: Context, data: ReportData): File? {
        return try {
            // Initialisation de l'état interne
            doc               = PdfDocument()
            currentPage       = null
            currentCanvas     = null
            currentPageNumber = 0
            currentData       = data

            // Démarrer la page 1
            val (canvas1, _) = startNewPage()
            var canvas = canvas1
            var y = 0f

            // ── Section 1 : En-tête ──────────────────────────────────
            y = drawHeader(canvas, data, y)

            // ── Section 2 : Profil patient ───────────────────────────
            val (c2, y2) = ensureSpace(y, neededSpace = 120f)
            canvas = c2; y = y2
            y = drawPatientProfile(canvas, data, y)

            // ── Section 3 : Analyse clinique ─────────────────────────
            val (c3, y3) = ensureSpace(y, neededSpace = 130f)
            canvas = c3; y = y3
            y = drawClinicalAnalysis(canvas, data, y)

            // ── Section 4 : Protocole de soins ───────────────────────
            val (c4, y4) = ensureSpace(y, neededSpace = 160f)
            canvas = c4; y = y4
            y = drawCareProtocol(canvas, data, y)

            // ── Section 5 : Sécurité & Fon ───────────────────────────
            val (c5, y5) = ensureSpace(y, neededSpace = 140f)
            canvas = c5; y = y5
            y = drawSafetySection(canvas, data, y)

            // ── Footer uniquement sur la dernière page ────────────────
            drawFooter(canvas, data)

            // Fermer la dernière page
            currentPage?.let { doc.finishPage(it) }

            val locale  = if (data.lang == "fr") Locale.FRANCE else Locale.ENGLISH
            val ts      = SimpleDateFormat("yyyyMMdd_HHmmss", locale).format(Date())
            val file    = File(context.getExternalFilesDir(null), "MedVoice_$ts.pdf")
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
        val L = data.lang

        val bgPaint = Paint().apply { color = C_DARK; isAntiAlias = true }
        canvas.drawRect(0f, 0f, PAGE_W.toFloat(), 72f, bgPaint)

        canvas.drawText("MedVoice Africa", ML, 32f, paint(20f, C_WHITE, bold = true))
        canvas.drawText(
            t("Assistant médical de terrain · Protocoles OMS",
                "Field Medical Assistant · WHO Protocols", L),
            ML, 50f, paint(9f, C_ACCENT)
        )

        val connText  = if (data.isOffline) t("MODE HORS-LIGNE", "OFFLINE MODE", L)
        else t("EN LIGNE", "ONLINE", L)
        val connColor = if (data.isOffline) C_ORANGE else C_VERT
        canvas.drawText(connText, MR - 110f, 38f, paint(9f, connColor, bold = true))

        var y = 82f
        canvas.drawLine(ML, y, MR, y, linePaint(C_ACCENT, 1.5f))
        y += 12f

        val locale  = if (L == "fr") Locale.FRANCE else Locale.ENGLISH
        val dateStr = SimpleDateFormat(
            if (L == "fr") "dd/MM/yyyy  HH:mm:ss" else "MM/dd/yyyy  HH:mm:ss", locale
        ).format(Date(data.timestamp))

        canvas.drawText(
            t("ID Consultation :  #${data.consultationId}",
                "Consultation ID :  #${data.consultationId}", L),
            ML, y, paint(9f, C_GREY)
        )
        canvas.drawText(
            t("Date & Heure :  $dateStr", "Date & Time :  $dateStr", L),
            ML + 200f, y, paint(9f, C_GREY)
        )
        y += 14f

        canvas.drawText(
            t("Objet :  ${data.sessionTitle}", "Subject :  ${data.sessionTitle}", L),
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
        val L = data.lang
        var y = drawSectionTitle(canvas,
            t("2.  PROFIL DU PATIENT", "2.  PATIENT PROFILE", L), startY)
        val cardTop = y
        y += 4f

        canvas.drawText(t("Nom / Identité :", "Name / Identity :", L), ML + 4f, y, paint(9f, C_GREY))
        canvas.drawText(data.patientName, ML + 110f, y, paint(10f, C_DARK, bold = true))
        y += 16f

        val weightTxt = data.weightKg?.let { "$it kg" } ?: t("Non renseigné", "Not provided", L)
        val ageTxt = when {
            data.ageYears != null && data.ageMonths != null ->
                if (L == "fr") "${data.ageYears} ans  ${data.ageMonths} mois"
                else "${data.ageYears} yrs  ${data.ageMonths} mo"
            data.ageYears != null ->
                if (L == "fr") "${data.ageYears} ans" else "${data.ageYears} yrs"
            data.ageMonths != null ->
                if (L == "fr") "${data.ageMonths} mois" else "${data.ageMonths} mo"
            else -> t("Non renseigné", "Not provided", L)
        }

        canvas.drawText(t("Poids :", "Weight :", L), ML + 4f, y, paint(9f, C_GREY))
        canvas.drawText(weightTxt, ML + 55f, y, paint(10f, C_DARK, bold = true))
        canvas.drawText(t("Âge :", "Age :", L), ML + 160f, y, paint(9f, C_GREY))
        canvas.drawText(ageTxt, ML + 195f, y, paint(10f, C_DARK, bold = true))
        y += 16f

        if (data.currentTreatments.isNotEmpty()) {
            canvas.drawText(
                t("Traitements en cours :", "Current treatments :", L),
                ML + 4f, y, paint(9f, C_GREY)
            )
            y += 13f
            val treatStr = data.currentTreatments.joinToString("  •  ")
            y = drawWrapped(canvas, treatStr, ML + 12f, y, COL_W - 12f, paint(9f, C_DARK))
        }

        data.knownAllergies?.let {
            canvas.drawText(
                t("Allergies :", "Allergies :", L),
                ML + 4f, y, paint(9f, C_WARN, bold = true)
            )
            canvas.drawText(it, ML + 75f, y, paint(9f, C_WARN))
            y += 14f
        }

        drawCardBorder(canvas, cardTop - 4f, y + 4f)
        y += 10f
        return y
    }

    // ════════════════════════════════════════════════════════════════
    // SECTION 3 — ANALYSE CLINIQUE & TRIAGE
    // ════════════════════════════════════════════════════════════════
    private fun drawClinicalAnalysis(canvas: Canvas, data: ReportData, startY: Float): Float {
        val L = data.lang
        var y = drawSectionTitle(canvas,
            t("3.  ANALYSE CLINIQUE & TRIAGE", "3.  CLINICAL ANALYSIS & TRIAGE", L), startY)

        canvas.drawText(
            t("Symptômes rapportés :", "Reported symptoms :", L),
            ML + 4f, y, paint(9f, C_GREY)
        )
        y += 13f
        y = drawWrapped(canvas, data.symptomsReported.take(350),
            ML + 10f, y, COL_W - 10f, paint(9.5f, C_DARK))
        y += 6f

        val triageColor   = triageColor(data.triage)
        val triageBgPaint = Paint().apply { color = triageColor; isAntiAlias = true; alpha = 230 }
        val bandeauTop    = y
        canvas.drawRoundRect(RectF(ML, bandeauTop, MR, bandeauTop + 36f), 8f, 8f, triageBgPaint)

        val triageLabel = when (data.triage.uppercase()) {
            "ROUGE" -> t("● TRIAGE ROUGE  —  URGENCE VITALE",   "● TRIAGE RED  —  LIFE-THREATENING", L)
            "JAUNE" -> t("● TRIAGE JAUNE  —  URGENCE STABLE",   "● TRIAGE YELLOW  —  STABLE EMERGENCY", L)
            "VERT"  -> t("● TRIAGE VERT  —  SURVEILLANCE",       "● TRIAGE GREEN  —  MONITORING", L)
            else    -> t("● TRIAGE INCONNU",                     "● UNKNOWN TRIAGE", L)
        }
        canvas.drawText(triageLabel, ML + 12f, bandeauTop + 15f, paint(11f, C_WHITE, bold = true))

        val triageSub = when (data.triage.uppercase()) {
            "ROUGE" -> t("Stabilisation immédiate requise · Alerte hôpital",
                "Immediate stabilization required · Hospital alert", L)
            "JAUNE" -> t("Traitement de stabilisation · Réévaluation dans 24h",
                "Stabilization treatment · Re-evaluation within 24h", L)
            "VERT"  -> t("Soins à domicile possibles · Suivi recommandé",
                "Home care possible · Follow-up recommended", L)
            else -> ""
        }
        canvas.drawText(triageSub, ML + 12f, bandeauTop + 28f, paint(8.5f, C_WHITE))
        y = bandeauTop + 44f

        if (data.triage.uppercase() == "ROUGE") {
            y += 6f
            canvas.drawRoundRect(RectF(ML, y, MR, y + 24f), 4f, 4f,
                Paint().apply { color = Color.rgb(255, 230, 230); isAntiAlias = true })
            canvas.drawText(
                t("TRANSFERT IMMÉDIAT VERS UN CENTRE DE SANTÉ REQUIS",
                    "IMMEDIATE TRANSFER TO A HEALTH FACILITY REQUIRED", L),
                ML + 8f, y + 16f, paint(10f, C_WARN, bold = true)
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
        val L = data.lang
        var y = drawSectionTitle(canvas,
            t("4.  PROTOCOLE DE SOINS", "4.  CARE PROTOCOL", L), startY)

        val dr = data.dosageResult
        if (dr == null) {
            canvas.drawText(
                t("Aucun calcul de dosage effectué pour cette consultation.",
                    "No dosage calculation performed for this consultation.", L),
                ML + 4f, y, paint(9.5f, C_GREY)
            )
            y += 20f
        } else {
            val cardTop = y
            y += 6f

            canvas.drawText(
                t("Médicament :", "Medication :", L),
                ML + 4f, y, paint(9f, C_GREY)
            )
            canvas.drawText(dr.medicineName, ML + 90f, y, paint(12f, C_ACCENT, bold = true))
            y += 18f

            if (dr.dosePerTake == "INTERDIT" || dr.dosePerTake == "STOP" ||
                dr.dosePerTake == "FORBIDDEN") {
                val warnBg = Paint().apply { color = Color.rgb(255, 235, 235); isAntiAlias = true }
                canvas.drawRoundRect(RectF(ML + 4f, y, MR - 4f, y + 30f), 6f, 6f, warnBg)
                canvas.drawText(
                    t("USAGE INTERDIT — CONTRE-INDIQUÉ",
                        "FORBIDDEN USE — CONTRAINDICATED", L),
                    ML + 10f, y + 19f, paint(10f, C_WARN, bold = true)
                )
                y += 38f
            } else {
                val pilW      = (COL_W - 24f) / 3f
                val positions = listOf(ML + 4f, ML + 4f + pilW + 8f, ML + 4f + (pilW + 8f) * 2)
                val pilLabels = listOf(
                    t("Dose", "Dose", L),
                    t("Fréquence", "Frequency", L),
                    t("Durée", "Duration", L)
                )
                val pilValues = listOf(
                    dr.dosePerTake,
                    t("${dr.frequencyPerDay}x / jour", "${dr.frequencyPerDay}x / day", L),
                    t("${dr.durationDays} jours", "${dr.durationDays} days", L)
                )
                positions.forEachIndexed { i, px ->
                    val pilBg = Paint().apply { color = C_ACCENT; alpha = 25; isAntiAlias = true }
                    canvas.drawRoundRect(RectF(px, y, px + pilW, y + 38f), 8f, 8f, pilBg)
                    canvas.drawRoundRect(
                        RectF(px, y, px + pilW, y + 38f), 8f, 8f,
                        linePaint(C_ACCENT, 0.8f).apply { style = Paint.Style.STROKE }
                    )
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

            if (dr.specialInstructions.isNotBlank()) {
                y += 2f
                y = drawWrapped(canvas, dr.specialInstructions, ML + 4f, y,
                    COL_W - 8f, paint(9f, C_GREY))
                y += 4f
            }

            if (dr.warningMessage.isNotBlank()) {
                val warnBg = Paint().apply { color = Color.rgb(255, 248, 220); isAntiAlias = true }
                canvas.drawRoundRect(RectF(ML + 4f, y, MR - 4f, y + 24f), 4f, 4f, warnBg)
                canvas.drawText(
                    dr.warningMessage.take(120),
                    ML + 10f, y + 16f, paint(8.5f, C_ORANGE)
                )
                y += 30f
            }

            val omsBg = Paint().apply { color = Color.rgb(230, 247, 240); isAntiAlias = true }
            canvas.drawRoundRect(RectF(ML + 4f, y, MR - 4f, y + 20f), 4f, 4f, omsBg)
            canvas.drawText(
                t("Calcul généré selon les protocoles OMS/PCIME — Validation clinique requise",
                    "Calculation based on WHO/IMCI protocols — Clinical validation required", L),
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
        val L = data.lang
        var y = drawSectionTitle(canvas,
            t("5.  SÉCURITÉ & INSTRUCTIONS LOCALES",
                "5.  SAFETY & LOCAL INSTRUCTIONS", L), startY)
        val cardTop = y

        if (data.drugInteraction != null) {
            val sevColor = when (data.interactionSeverity?.uppercase()) {
                "ROUGE" -> C_WARN
                "JAUNE" -> C_ORANGE
                else    -> C_ORANGE
            }
            val sevLabel = when (data.interactionSeverity?.uppercase()) {
                "ROUGE" -> t("INTERACTION DANGEREUSE — CONTRE-INDIQUÉ",
                    "DANGEROUS INTERACTION — CONTRAINDICATED", L)
                "JAUNE" -> t("INTERACTION À SURVEILLER",
                    "INTERACTION TO MONITOR", L)
                else    -> t("INTERACTION DÉTECTÉE", "INTERACTION DETECTED", L)
            }
            val intBg = Paint().apply {
                color = if (data.interactionSeverity == "ROUGE")
                    Color.rgb(255, 230, 230) else Color.rgb(255, 248, 220)
                isAntiAlias = true
            }
            canvas.drawRoundRect(RectF(ML + 2f, y, MR - 2f, y + 32f), 6f, 6f, intBg)
            canvas.drawText("$sevLabel", ML + 8f, y + 13f, paint(9f, sevColor, bold = true))
            canvas.drawText(data.drugInteraction, ML + 8f, y + 26f, paint(9f, sevColor))
            y += 40f
        } else {
            canvas.drawText(
                t("Aucune interaction médicamenteuse détectée.",
                    "No drug interaction detected.", L),
                ML + 4f, y, paint(9f, C_VERT)
            )
            y += 16f
        }

        y += 4f

        if (data.conseilFon != null) {
            canvas.drawText(
                t("Conseils premiers secours en langue Fon :",
                    "First aid advice in Fon language :", L),
                ML + 4f, y, paint(9f, C_GREY)
            )
            y += 13f

            // ── FIX P2 : mesurer SANS dessiner (dryRun = true), puis fond, puis texte ──
            val fonTextPaint = paint(10f, C_BLUE).apply {
                typeface = Typeface.create(Typeface.SERIF, Typeface.ITALIC)
            }
            val fonBg  = Paint().apply { color = Color.rgb(240, 248, 255); isAntiAlias = true }
            val fonTop = y

            // Étape 1 : mesurer la hauteur sans rien dessiner
            val tempY = drawWrapped(
                canvas    = canvas,
                text      = data.conseilFon,
                x         = ML + 10f,
                startY    = fonTop + 6f,
                maxWidth  = COL_W - 14f,
                p         = fonTextPaint,
                dryRun    = true          // ← ne dessine pas
            )

            // Étape 2 : dessiner le fond avec les bonnes dimensions
            canvas.drawRoundRect(
                RectF(ML + 2f, fonTop, MR - 2f, tempY + 6f),
                6f, 6f, fonBg
            )

            // Étape 3 : dessiner le texte UNE SEULE FOIS par-dessus le fond
            y = drawWrapped(
                canvas   = canvas,
                text     = data.conseilFon,
                x        = ML + 10f,
                startY   = fonTop + 6f,
                maxWidth = COL_W - 14f,
                p        = fonTextPaint,
                dryRun   = false         // ← dessine pour de vrai
            )
            y += 10f
        } else {
            canvas.drawText(
                t("Aucun conseil Fon disponible pour ce type d'urgence.",
                    "No Fon advice available for this type of emergency.", L),
                ML + 4f, y, paint(9f, C_GREY)
            )
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
        val L       = data.lang
        val footerY = PAGE_H - 90f

        canvas.drawLine(ML, footerY, MR, footerY, linePaint(C_LIGHT, 1f))

        val disclaimerText = t(
            "AVIS IMPORTANT : Ce rapport est un outil d'aide à la décision médicale de terrain. " +
                    "Il ne constitue pas un diagnostic médical final et ne remplace pas l'avis d'un " +
                    "professionnel de santé qualifié. Les dosages sont calculés selon les protocoles " +
                    "OMS/PCIME. La responsabilité clinique appartient à l'agent de santé.",
            "IMPORTANT NOTICE: This report is a field medical decision support tool. " +
                    "It does not constitute a final medical diagnosis and does not replace the advice " +
                    "of a qualified health professional. Dosages are calculated according to WHO/IMCI " +
                    "protocols. Clinical responsibility belongs to the health worker.",
            L
        )

        var dy = footerY + 12f
        dy = drawWrapped(canvas, disclaimerText, ML, dy, COL_W,
            paint(7.5f, C_GREY).apply { isAntiAlias = true })

        dy += 4f
        canvas.drawText(
            t("Signature de l'agent de santé :", "Health worker signature :", L),
            ML, dy, paint(8f, C_DARK)
        )
        val sigName = if (data.agentName.isBlank()) "_______________________" else data.agentName
        canvas.drawText(sigName, ML + 165f, dy, paint(8f, C_DARK, bold = true))

        if (data.agentName.isBlank()) {
            canvas.drawLine(ML + 162f, dy + 2f, ML + 290f, dy + 2f, linePaint(C_GREY, 0.6f))
        }

        val locale  = if (L == "fr") Locale.FRANCE else Locale.ENGLISH
        val dateStr = SimpleDateFormat(
            if (L == "fr") "dd/MM/yyyy" else "MM/dd/yyyy", locale
        ).format(Date(data.timestamp))

        canvas.drawText(
            "MedVoice Africa v1.0  •  Gemma 4 Edge AI  •  $dateStr",
            PAGE_W / 2f, PAGE_H - 10f,
            paint(7.5f, C_GREY, align = Paint.Align.CENTER)
        )
    }

    // ════════════════════════════════════════════════════════════════
    // HELPERS INTERNES
    // ════════════════════════════════════════════════════════════════

    private fun drawSectionTitle(canvas: Canvas, title: String, y: Float): Float {
        val bgP = Paint().apply { color = Color.rgb(245, 245, 245); isAntiAlias = true }
        canvas.drawRect(ML - 4f, y, MR + 4f, y + 18f, bgP)
        canvas.drawRect(ML - 4f, y, ML + 2f, y + 18f,
            Paint().apply { color = C_ACCENT; isAntiAlias = true })
        canvas.drawText(title, ML + 6f, y + 13f, paint(9f, C_DARK, bold = true))
        return y + 24f
    }

    private fun drawCardBorder(canvas: Canvas, top: Float, bottom: Float) {
        val strokeP = Paint().apply {
            color = C_LIGHT; style = Paint.Style.STROKE
            strokeWidth = 0.8f; isAntiAlias = true
        }
        canvas.drawRoundRect(RectF(ML, top, MR, bottom), 6f, 6f, strokeP)
    }

    /**
     * Dessine du texte wrappé ligne à ligne.
     *
     * @param dryRun si true, calcule la hauteur finale SANS dessiner quoi que ce soit.
     *               Utiliser pour mesurer avant de dessiner un fond (fix Bug P2).
     */
    private fun drawWrapped(
        canvas: Canvas,
        text: String,
        x: Float,
        startY: Float,
        maxWidth: Float,
        p: Paint,
        dryRun: Boolean = false        // ← NOUVEAU paramètre
    ): Float {
        var y      = startY
        var remain = text.replace(Regex("\\*+"), "").trim()
        while (remain.isNotEmpty()) {
            val count = p.breakText(remain, true, maxWidth, null)
            if (count <= 0) break
            if (!dryRun) {
                canvas.drawText(remain.substring(0, count), x, y, p)
            }
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
        this.textSize   = size
        this.color      = color
        this.typeface   = if (bold) Typeface.create(Typeface.DEFAULT, Typeface.BOLD)
        else Typeface.DEFAULT
        this.textAlign  = align
        this.isAntiAlias = true
    }

    private fun linePaint(color: Int, width: Float = 0.5f) = Paint().apply {
        this.color       = color
        this.strokeWidth = width
        this.style       = Paint.Style.STROKE
        this.isAntiAlias = true
    }

    // ── Partage ───────────────────────────────────────────────────────
    fun sharePdf(context: Context, file: File) {
        val uri = FileProvider.getUriForFile(
            context, "${context.packageName}.provider", file
        )
        context.startActivity(
            Intent.createChooser(
                Intent(Intent.ACTION_SEND).apply {
                    type = "application/pdf"
                    putExtra(Intent.EXTRA_STREAM, uri)
                    addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
                },
                "Partager le rapport / Share report"
            )
        )
    }
}