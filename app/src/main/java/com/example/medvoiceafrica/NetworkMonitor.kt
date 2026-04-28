package com.example.medvoiceafrica

// ═══════════════════════════════════════════════════════════════════
// 3. PASTILLE RÉSEAU DYNAMIQUE
//
// Ce fichier contient :
//   A) NetworkMonitor     → observe ConnectivityManager en temps réel
//   B) StatusChip         → composable remplaçant le chip statique
//
// INTÉGRATION :
//   1. Dans MainActivity.onCreate(), initialiser NetworkMonitor :
//        private val networkMonitor by lazy { NetworkMonitor(this) }
//
//   2. Passer isOnline dans MedVoiceApp/MedVoiceChatScreen :
//        val isOnline by networkMonitor.isOnline.collectAsStateWithLifecycle(true)
//
//   3. Remplacer le chip statique (patch TopBar) par :
//        StatusChip(isOnline = isOnline, colors = colors)
//
// DÉPENDANCES dans AndroidManifest.xml — ajouter si absent :
//   <uses-permission android:name="android.permission.ACCESS_NETWORK_STATE"/>
// ═══════════════════════════════════════════════════════════════════

import android.content.Context
import android.net.ConnectivityManager
import android.net.Network
import android.net.NetworkCapabilities
import android.net.NetworkRequest
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.callbackFlow
import kotlinx.coroutines.flow.conflate
import java.util.Locale

// ── A) NetworkMonitor ─────────────────────────────────────────────
class NetworkMonitor(context: Context) {

    private val connectivityManager =
        context.getSystemService(Context.CONNECTIVITY_SERVICE) as ConnectivityManager

    /** Flow<Boolean> : true = en ligne, false = hors ligne */
    val isOnline: Flow<Boolean> = callbackFlow {
        // Émettre l'état initial immédiatement
        trySend(currentlyOnline())

        val callback = object : ConnectivityManager.NetworkCallback() {
            override fun onAvailable(network: Network) { trySend(true) }
            override fun onLost(network: Network) { trySend(currentlyOnline()) }
            override fun onCapabilitiesChanged(
                network: Network,
                caps: NetworkCapabilities
            ) {
                trySend(
                    caps.hasCapability(NetworkCapabilities.NET_CAPABILITY_INTERNET) &&
                            caps.hasCapability(NetworkCapabilities.NET_CAPABILITY_VALIDATED)
                )
            }
        }

        val request = NetworkRequest.Builder()
            .addCapability(NetworkCapabilities.NET_CAPABILITY_INTERNET)
            .build()
        connectivityManager.registerNetworkCallback(request, callback)

        awaitClose { connectivityManager.unregisterNetworkCallback(callback) }
    }.conflate()

    private fun currentlyOnline(): Boolean {
        val net = connectivityManager.activeNetwork ?: return false
        val caps = connectivityManager.getNetworkCapabilities(net) ?: return false
        return caps.hasCapability(NetworkCapabilities.NET_CAPABILITY_INTERNET) &&
                caps.hasCapability(NetworkCapabilities.NET_CAPABILITY_VALIDATED)
    }
}

// ── B) StatusChip — Composable ────────────────────────────────────
/**
 * Chip dynamique qui change de couleur et de texte selon la connectivité.
 *
 * Usage dans MedVoiceChatScreen (remplace le Row du patch TopBar) :
 *
 *   Row(
 *       modifier = Modifier.fillMaxWidth()...,
 *       horizontalArrangement = Arrangement.SpaceBetween
 *   ) {
 *       StatusChip(isOnline = isOnline, colors = colors)
 *       // bouton engrenage...
 *   }
 */
@Composable
fun StatusChip(
    isOnline: Boolean,
    colors: MedVoiceColors,

    forceOffline: Boolean = false   // pour le switch "Mode Survie" dans Paramètres
) {
    val isFr = Locale.getDefault().language == "fr"
    val effectivelyOnline = isOnline && !forceOffline

    // Couleur animée de la pastille
    val dotColor by animateColorAsState(
        targetValue = if (effectivelyOnline) Color(0xFF1D9E75) else Color(0xFFE24B4A),
        animationSpec = tween(durationMillis = 600),
        label = "dot_color"
    )

    val chipText = when {
        forceOffline -> if (isFr) "Mode Survie · Protocoles locaux" else "Survival Mode · Local protocols"
        effectivelyOnline -> if (isFr) "En ligne · Protocoles OMS v2026" else "Online · WHO Protocols v2026"
        else -> if (isFr) "Hors ligne · Mode Survie activé" else "Offline · Survival Mode active"
    }

    Surface(
        shape = RoundedCornerShape(20.dp),
        color = colors.bgSecondary,
        border = BorderStroke(0.5.dp, colors.divider)
    ) {
        Row(
            modifier = Modifier.padding(horizontal = 10.dp, vertical = 5.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Box(
                modifier = Modifier
                    .size(6.dp)
                    .background(dotColor, CircleShape)
            )
            Spacer(Modifier.width(6.dp))
            Text(
                text = chipText,
                fontSize = 11.sp,
                color = colors.textSecondary
            )
        }
    }
}