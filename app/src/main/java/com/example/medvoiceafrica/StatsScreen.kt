package com.example.medvoiceafrica

// ═══════════════════════════════════════════════════════════════════
// StatsScreen.kt — FINAL CORRIGÉ
// Corrections vs version précédente :
//   - Import androidx.compose.foundation.clickable ajouté
//   - Header avec bouton ← fonctionnel (clickable corrigé)
//   - ConsultationLog, ConsultationDao, PathologieCount, extractPathologie
//     conservés intacts (déjà corrects)
// ═══════════════════════════════════════════════════════════════════

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.ui.res.painterResource
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.room.*
import kotlinx.coroutines.flow.Flow
import java.text.SimpleDateFormat
import java.util.*

// ── A) Entité Room ────────────────────────────────────────────────
@Entity(tableName = "consultation_log")
data class ConsultationLog(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val timestamp: Long = System.currentTimeMillis(),
    val pathologie: String,
    val triage: String,
    val sessionId: Long = -1,
    val isOffline: Boolean = false,
    @ColumnInfo(name = "synced") val synced: Int = 0
)

// Helper pour extraire la pathologie
fun extractPathologie(protocolTitle: String?, userMessage: String): String {
    if (protocolTitle != null) {
        return when {
            protocolTitle.contains("Paludisme", ignoreCase = true) ||
                    protocolTitle.contains("Malaria", ignoreCase = true)   -> "Paludisme"
            protocolTitle.contains("Déshydratation", ignoreCase = true) ||
                    protocolTitle.contains("Diarrhée", ignoreCase = true)  -> "Diarrhée"
            protocolTitle.contains("Pneumonie", ignoreCase = true)        -> "Pneumonie"
            protocolTitle.contains("Malnutrition", ignoreCase = true)     -> "Malnutrition"
            protocolTitle.contains("Néonatal", ignoreCase = true) ||
                    protocolTitle.contains("Nouveau-né", ignoreCase = true)-> "Néonatal"
            protocolTitle.contains("Hypertension", ignoreCase = true)     -> "Hypertension"
            protocolTitle.contains("Plaie", ignoreCase = true)            -> "Plaie/Infection"
            protocolTitle.contains("Serpent", ignoreCase = true) ||
                    protocolTitle.contains("Envenimation", ignoreCase = true) -> "Envenimation"
            else -> protocolTitle.take(20)
        }
    }
    val msg = userMessage.lowercase()
    return when {
        msg.contains("palu") || msg.contains("malaria") || msg.contains("fièvre") -> "Paludisme"
        msg.contains("diarrhée") || msg.contains("selle") || msg.contains("vomis") -> "Diarrhée"
        msg.contains("toux") || msg.contains("respir") || msg.contains("pneumonie") -> "Pneumonie"
        msg.contains("maigre") || msg.contains("malnutri") -> "Malnutrition"
        msg.contains("tension") || msg.contains("hypertension") -> "Hypertension"
        msg.contains("plaie") || msg.contains("blessure") -> "Plaie/Infection"
        msg.contains("serpent") || msg.contains("morsure") -> "Envenimation"
        msg.contains("bébé") || msg.contains("nouveau-né") || msg.contains("nourrisson") -> "Néonatal"
        else -> "Autre"
    }
}

// ── B) DAO ────────────────────────────────────────────────────────
@Dao
interface ConsultationDao {

    @Insert
    suspend fun insert(log: ConsultationLog)

    @Query("SELECT COUNT(*) FROM consultation_log")
    fun totalCount(): Flow<Int>

    @Query("SELECT COUNT(*) FROM consultation_log WHERE triage = 'ROUGE'")
    fun urgencesCount(): Flow<Int>

    @Query("SELECT COUNT(*) FROM consultation_log WHERE isOffline = 1")
    fun offlineCount(): Flow<Int>

    @Query("""
        SELECT pathologie, COUNT(*) as count 
        FROM consultation_log 
        WHERE timestamp > :since
        GROUP BY pathologie 
        ORDER BY count DESC
        LIMIT 8
    """)
    fun topPathologies(since: Long = System.currentTimeMillis() - 7 * 24 * 3600 * 1000L): Flow<List<PathologieCount>>

    @Query("""
        SELECT COUNT(*) FROM consultation_log 
        WHERE triage = 'ROUGE' AND timestamp > :since
    """)
    fun urgencesThisWeek(since: Long = System.currentTimeMillis() - 7 * 24 * 3600 * 1000L): Flow<Int>

    @Query("""
        SELECT COUNT(*) FROM consultation_log 
        WHERE timestamp > :since
    """)
    fun totalThisWeek(since: Long = System.currentTimeMillis() - 7 * 24 * 3600 * 1000L): Flow<Int>

    @Query("SELECT * FROM consultation_log WHERE synced = 0 ORDER BY timestamp ASC LIMIT 50")
    suspend fun getUnsyncedLogs(): List<ConsultationLog>

    @Query("UPDATE consultation_log SET synced = 1 WHERE id IN (:ids)")
    suspend fun markAsSynced(ids: List<Long>)

}

// Projection pour GROUP BY
data class PathologieCount(
    val pathologie: String,
    val count: Int
)

// ── C) Écran Statistiques ─────────────────────────────────────────
@Composable
fun StatsScreen(
    db: AppDatabase,
    colors: MedVoiceColors,
    onBack: () -> Unit
) {
    val isFr = Locale.getDefault().language == "fr"
    val since7days = remember { System.currentTimeMillis() - 7 * 24 * 3600 * 1000L }

    val total by db.consultationDao().totalCount().collectAsStateWithLifecycle(0)
    val urgences by db.consultationDao().urgencesCount().collectAsStateWithLifecycle(0)
    val offlineCount by db.consultationDao().offlineCount().collectAsStateWithLifecycle(0)
    val pathologies by db.consultationDao().topPathologies(since7days).collectAsStateWithLifecycle(emptyList())
    val urgencesWeek by db.consultationDao().urgencesThisWeek(since7days).collectAsStateWithLifecycle(0)
    val totalWeek by db.consultationDao().totalThisWeek(since7days).collectAsStateWithLifecycle(0)

    val transferRate = if (totalWeek > 0) (urgencesWeek * 100L / totalWeek).toInt() else 0
    val offlineRate  = if (total > 0) (offlineCount * 100L / total).toInt() else 0
    val maxCount = (pathologies.maxOfOrNull { it.count } ?: 1).coerceAtLeast(1)

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(colors.bgPrimary)
            .verticalScroll(rememberScrollState())
    ) {
        // Header
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .background(colors.bgTopBar)
                .statusBarsPadding()
                .padding(horizontal = 8.dp, vertical = 10.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Box(
                modifier = Modifier
                    .clip(RoundedCornerShape(8.dp))
                    .clickable { onBack() }
                    .padding(8.dp)
            ) {
                Icon(
                    painter = painterResource(id = R.drawable.ic_arrow_back),
                    contentDescription = null,
                    modifier = Modifier.size(24.dp),
                    tint = colors.textSecondary
                )
            }
            Spacer(Modifier.width(8.dp))
            Column {
                Text(
                    if (isFr) "Journal épidémiologique" else "Epidemiological log",
                    fontSize = 15.sp, fontWeight = FontWeight.Bold, color = colors.textPrimary
                )
                val weekLabel = SimpleDateFormat("dd MMM", Locale.getDefault())
                    .format(Date(since7days)) + " – " +
                        SimpleDateFormat("dd MMM yyyy", Locale.getDefault()).format(Date())
                Text(weekLabel, fontSize = 11.sp, color = colors.textSecondary)
            }
        }
        HorizontalDivider(color = colors.divider, thickness = 0.5.dp)

        Spacer(Modifier.height(16.dp))

        // Grille de métriques
        Row(
            modifier = Modifier.fillMaxWidth().padding(horizontal = 12.dp),
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            StatCard(Modifier.weight(1f), if (isFr) "Cas traités" else "Cases treated",
                "$total", if (isFr) "total" else "total", colors.textPrimary, colors)
            StatCard(Modifier.weight(1f), if (isFr) "Urgences ROUGE" else "RED emergencies",
                "$urgences", if (isFr) "cas critiques" else "critical", Color(0xFFE24B4A), colors)
        }
        Spacer(Modifier.height(8.dp))
        Row(
            modifier = Modifier.fillMaxWidth().padding(horizontal = 12.dp),
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            StatCard(Modifier.weight(1f), if (isFr) "Taux transfert" else "Transfer rate",
                "$transferRate%", if (isFr) "cas urgents/sem." else "urgent/week", colors.accent, colors)
            StatCard(Modifier.weight(1f), if (isFr) "Mode offline" else "Offline mode",
                "$offlineRate%", if (isFr) "des consultations" else "of consults", Color(0xFFEF9F27), colors)
        }

        Spacer(Modifier.height(20.dp))

        // Graphique en barres — pathologies
        if (pathologies.isNotEmpty()) {
            Text(
                text = if (isFr) "Pathologies — 7 derniers jours" else "Pathologies — last 7 days",
                fontSize = 12.sp, fontWeight = FontWeight.Medium, color = colors.textSecondary,
                modifier = Modifier.padding(horizontal = 12.dp, vertical = 4.dp)
            )
            val barColors = listOf(
                Color(0xFFF09595), Color(0xFF85B7EB), Color(0xFF97C459), Color(0xFFEF9F27),
                Color(0xFFAFA9EC), Color(0xFF5DCAA5), Color(0xFFED93B1), Color(0xFFB4B2A9)
            )
            Column(
                modifier = Modifier.padding(horizontal = 12.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                pathologies.forEachIndexed { index, item ->
                    val fraction = item.count.toFloat() / maxCount.toFloat()
                    val barColor = barColors.getOrElse(index) { Color(0xFFB4B2A9) }
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        Text(item.pathologie, fontSize = 11.sp, color = colors.textSecondary,
                            modifier = Modifier.width(90.dp))
                        Box(
                            modifier = Modifier.weight(1f).height(18.dp)
                                .clip(RoundedCornerShape(3.dp))
                                .background(colors.bgSecondary)
                        ) {
                            Box(
                                modifier = Modifier.fillMaxHeight().fillMaxWidth(fraction)
                                    .clip(RoundedCornerShape(3.dp)).background(barColor),
                                contentAlignment = Alignment.CenterEnd
                            ) {
                                Text("${item.count}", fontSize = 9.sp, fontWeight = FontWeight.Bold,
                                    color = Color.White, modifier = Modifier.padding(end = 5.dp))
                            }
                        }
                    }
                }
            }
        } else {
            Box(
                modifier = Modifier.fillMaxWidth().padding(32.dp),
                contentAlignment = Alignment.Center
            ) {
                Text(
                    if (isFr) "Aucune donnée cette semaine\n(les stats s'alimentent au fil des consultations)"
                    else "No data this week\n(stats fill up as consultations happen)",
                    fontSize = 13.sp, color = colors.textSecondary,
                    modifier = Modifier.wrapContentWidth(Alignment.CenterHorizontally)
                )
            }
        }

        Spacer(Modifier.height(24.dp))
    }
}

// ── StatCard helper ───────────────────────────────────────────────
@Composable
private fun StatCard(
    modifier: Modifier,
    label: String,
    value: String,
    sub: String,
    valueColor: Color,
    colors: MedVoiceColors
) {
    Column(
        modifier = modifier
            .clip(RoundedCornerShape(12.dp))
            .background(colors.bgSecondary)
            .padding(12.dp)
    ) {
        Text(label, fontSize = 10.sp, color = colors.textSecondary, fontWeight = FontWeight.Medium)
        Spacer(Modifier.height(4.dp))
        Text(value, fontSize = 24.sp, fontWeight = FontWeight.Bold, color = valueColor)
        Text(sub, fontSize = 10.sp, color = colors.textSecondary)
    }
}