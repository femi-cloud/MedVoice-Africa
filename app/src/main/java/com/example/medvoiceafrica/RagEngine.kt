package com.example.medvoiceafrica

import android.content.Context
import android.util.Log
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

class RagEngine(private val context: Context) {

    private val dao by lazy { AppDatabase.getInstance(context).omsProtocolDao() }

    companion object {
        private const val TAG = "RagEngine"
        private const val MAX_PROTOCOLS = 2
        private const val MIN_SCORE = 1

        fun score(query: String, p: OmsProtocol): Int {
            val q = query.lowercase()
            var s = 0
            p.keywords.split(",").map { it.trim().lowercase() }.forEach { kw ->
                if (kw.isNotBlank() && q.contains(kw)) s += if (kw.length > 5) 2 else 1
            }
            if (q.contains(p.category.lowercase())) s += 3
            return s
        }

        fun buildContext(protocols: List<OmsProtocol>, lang: String): String {
            if (protocols.isEmpty()) return ""
            val header = if (lang == "fr")
                "=== PROTOCOLES OMS (Base locale — utilise ces informations pour ta réponse) ==="
            else
                "=== WHO PROTOCOLS (Local knowledge base — use this information in your response) ==="
            val footer = if (lang == "fr") "=== FIN DES PROTOCOLES ===" else "=== END OF PROTOCOLS ==="
            return buildString {
                appendLine(header)
                protocols.forEach { p ->
                    appendLine()
                    appendLine("--- ${p.title.uppercase()} ---")
                    appendLine(p.protocol.trim())
                }
                appendLine(footer)
            }
        }
    }

    suspend fun findRelevant(query: String): List<OmsProtocol> = withContext(Dispatchers.IO) {
        try {
            dao.getAll()
                .map { it to score(query, it) }
                .filter { (_, s) -> s >= MIN_SCORE }
                .sortedByDescending { (_, s) -> s }
                .take(MAX_PROTOCOLS)
                .also { results ->
                    results.forEach { (p, s) ->
                        Log.d(TAG, "RAG match: '${p.title}' score=$s")
                    }
                    if (results.isEmpty()) Log.d(TAG, "RAG: no match for '$query'")
                }
                .map { (p, _) -> p }
        } catch (e: Exception) {
            Log.e(TAG, "RAG error: ${e.message}")
            emptyList()
        }
    }

    suspend fun populate() = withContext(Dispatchers.IO) {
        if (dao.count() > 0) return@withContext
        dao.insertAll(OmsProtocols.all)
        Log.d(TAG, "RAG DB populated with ${OmsProtocols.all.size} OMS protocols")
    }

    suspend fun isReady() = withContext(Dispatchers.IO) {
        try { dao.count() > 0 } catch (e: Exception) { false }
    }
}