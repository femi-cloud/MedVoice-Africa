package com.example.medvoiceafrica

import android.content.Context
import androidx.room.*
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

// ── Room Entity ───────────────────────────────────────────────────
@Entity(tableName = "oms_protocols")
data class OmsProtocol(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val title: String,
    val category: String,
    val keywords: String,   // virgule-séparé pour le matching
    val protocol: String,   // texte complet injecté dans Gemma
    val severity: String    // ROUGE / JAUNE / VERT
)

// ── DAO ───────────────────────────────────────────────────────────
@Dao
interface OmsProtocolDao {
    @Query("SELECT * FROM oms_protocols")
    suspend fun getAll(): List<OmsProtocol>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertAll(protocols: List<OmsProtocol>)

    @Query("SELECT COUNT(*) FROM oms_protocols")
    suspend fun count(): Int
}

// ── Protocoles OMS embarqués ──────────────────────────────────────
// Sources : OMS PCIME, guides nationaux de santé du Bénin
object OmsProtocols {
    val all = listOf(

        OmsProtocol(
            title = "Paludisme grave (enfant < 5 ans)",
            category = "paludisme",
            keywords = "paludisme,malaria,fièvre,convulsion,inconscient,vomissement,pâleur,artésunate,quinine",
            severity = "ROUGE",
            protocol = """
PROTOCOLE OMS — PALUDISME GRAVE (Enfant < 5 ans)
Critères d'urgence (≥1 = ROUGE) :
- Inconscience ou prostration (ne peut pas s'asseoir seul)
- Convulsions répétées (>2 épisodes/24h)
- Détresse respiratoire sévère
- Anémie sévère (pâleur palmaire extrême)
- Hypoglycémie (glycémie < 2,2 mmol/L)

Traitement immédiat :
1. Artésunate IV/IM 2,4 mg/kg à 0h, 12h, 24h puis toutes les 24h
   Si indisponible : Quinine IV 20 mg/kg en charge (30 min) + 10 mg/kg/8h
2. Antipyrétique : Paracétamol 15 mg/kg/6h (JAMAIS d'aspirine < 12 ans)
3. Si hypoglycémie : Glucose 10% IV 5 mL/kg en bolus lent
4. Si convulsions : Diazépam rectal 0,5 mg/kg (max 10 mg)
5. TRANSFERT IMMÉDIAT en centre de référence avec fiche de transfert

Paludisme simple (JAUNE) :
- ACT Artéméther-Luméfantrine (Coartem) :
  5–14 kg : 1 cp × 2/j pendant 3 jours
  15–24 kg : 2 cp × 2/j pendant 3 jours
- Paracétamol si fièvre
- Réévaluation à 48h obligatoire
""".trimIndent()
        ),

        OmsProtocol(
            title = "Déshydratation sévère (enfant)",
            category = "déshydratation",
            keywords = "déshydratation,diarrhée,vomissement,SRO,soluté,yeux enfoncés,pli cutané,fontanelle,léthargie",
            severity = "ROUGE",
            protocol = """
PROTOCOLE OMS — DÉSHYDRATATION (Enfant)
Classification :
ROUGE — Déshydratation sévère :
- Yeux très enfoncés, pli cutané > 2 secondes
- Léthargie ou inconscience, boit peu ou pas
→ Ringer lactate IV 100 mL/kg en 3h (< 12 mois) ou 30 min (> 12 mois)
→ Perfusion : 30 mL/kg en 30 min, puis 70 mL/kg en 2h30
→ TRANSFERT si état ne s'améliore pas en 30 min

JAUNE — Déshydratation modérée :
- Yeux légèrement enfoncés, pli cutané 1–2 secondes
- Agité, boit avidement
→ SRO 75 mL/kg sur 4 heures (donner lentement à la cuillère)
→ Continuer allaitement
→ Réévaluation toutes les heures

VERT — Pas de déshydratation :
- Yeux normaux, pli cutané normal
→ SRO 10 mL/kg après chaque selle liquide
→ Zinc 20 mg/j pendant 10 jours (enfant > 6 mois)
→ Continuer alimentation normale

SRO maison si sachet indisponible :
1 litre d'eau bouillie + 6 cuillères à café de sucre + 1/2 cuillère de sel
""".trimIndent()
        ),

        OmsProtocol(
            title = "Pneumonie (enfant < 5 ans)",
            category = "pneumonie",
            keywords = "pneumonie,toux,respiration rapide,tirage,détresse respiratoire,fièvre,poumon,amoxicilline",
            severity = "ROUGE",
            protocol = """
PROTOCOLE OMS — PNEUMONIE (Enfant < 5 ans)
Classification par fréquence respiratoire :
ROUGE — Pneumonie grave :
- Tirage sous-costal visible, stridor au repos, cyanose
- Fréquence > 70/min (< 2 mois) ou > 60/min (2–12 mois)
→ Amoxicilline IV/IM 50 mg/kg/12h + Gentamicine 7,5 mg/kg/j
→ Oxygène si disponible (SpO2 < 90%)
→ TRANSFERT URGENT

JAUNE — Pneumonie simple :
- FR > 50/min (2–12 mois) ou > 40/min (1–5 ans) sans tirage
→ Amoxicilline orale 40 mg/kg/j en 2 prises × 5 jours
→ Réévaluation à 48h

VERT — Toux simple (pas de pneumonie) :
- FR normale, pas de tirage
→ Analgésique si inconfort
→ Miel 1 c. à café pour la toux (enfant > 1 an)
→ Éviter expositions fumée

Comptage FR : compter sur 1 minute complète, enfant calme
""".trimIndent()
        ),

        OmsProtocol(
            title = "Malnutrition aiguë sévère (MAS)",
            category = "malnutrition",
            keywords = "malnutrition,marasme,kwashiorkor,amaigrissement,œdème,poids,périmètre brachial,PB,ATPE",
            severity = "ROUGE",
            protocol = """
PROTOCOLE OMS — MALNUTRITION AIGUË SÉVÈRE
Critères de MAS (≥1 critère) :
- Périmètre brachial (PB) < 115 mm (enfant 6–59 mois)
- Rapport poids/taille < -3 Z-scores
- Œdèmes bilatéraux prenant le godet (kwashiorkor)

ROUGE — Avec complications :
- Anorexie, fièvre > 38,5°C, léthargie, vomissements
→ TRANSFERT en CRENI (Centre de Récupération Nutritionnelle Intensif)
→ Phase de stabilisation : F-75 (75 kcal/100 mL) selon protocole
→ Ne pas donner fer en phase aiguë

JAUNE — Sans complications :
- Appétit conservé (test à l'ATPE positif)
→ CRENAS ambulatoire
→ ATPE (Plumpy'Nut) :
  4–6 kg : 1/2 sachet/j
  6–10 kg : 1 sachet/j (92 g)
  > 10 kg : 1,5 sachet/j
→ Amoxicilline préventive 50 mg/kg/j × 7 jours
→ Suivi hebdomadaire

Critères de guérison : PB ≥ 125 mm pendant 2 semaines consécutives
""".trimIndent()
        ),

        OmsProtocol(
            title = "Fièvre néonatale (nouveau-né < 28 jours)",
            category = "néonatal",
            keywords = "nouveau-né,néonatal,nourrisson,fièvre,sepsis,infection,convulsion,fontanelle,hypoglycémie,ictère",
            severity = "ROUGE",
            protocol = """
PROTOCOLE OMS — FIÈVRE NÉONATALE (< 28 jours)
TOUT SIGNE D'ALARME CHEZ LE NOUVEAU-NÉ = ROUGE URGENT

Signes d'alarme :
- Température > 37,5°C ou < 36°C (hypothermie)
- Refus de téter, léthargie
- Convulsions, tremblements
- Fontanelle bombée
- Ictère avant 24h ou ictère intense
- Détresse respiratoire (geignement, cyanose)
- Ombilic rouge/suintant, pustules cutanées

Conduite à tenir :
1. Maintien au chaud contre la peau de la mère (peau à peau)
2. Continuer allaitement si possible
3. Ampicilline IV 50 mg/kg/12h + Gentamicine IV 7,5 mg/kg/j
4. Glucose IV si hypoglycémie suspectée
5. TRANSFERT EN URGENCE — ne pas attendre

Prévention : vaccination BCG + polio à la naissance, hygiène ombilicale (alcool 70°)
""".trimIndent()
        ),

        OmsProtocol(
            title = "Hypertension artérielle et prééclampsie (adulte)",
            category = "hypertension",
            keywords = "hypertension,tension,TA,prééclampsie,éclampsie,convulsion,grossesse,maux de tête,vision,protéinurie",
            severity = "ROUGE",
            protocol = """
PROTOCOLE OMS — HYPERTENSION / PRÉÉCLAMPSIE
Définitions :
- HTA : TA ≥ 140/90 mmHg à 2 mesures espacées de 15 min
- Prééclampsie : HTA + protéinurie chez femme enceinte > 20 SA
- Éclampsie : Convulsions sur prééclampsie = URGENCE ABSOLUE

ROUGE — Urgence hypertensive (TA ≥ 160/110) :
→ Nifédipine LP 30 mg per os ou Labétalol IV si disponible
→ Si éclampsie : Sulfate de magnésium IV 4g en 20 min (dose de charge)
  Puis 1g/h en entretien
→ TRANSFERT IMMÉDIAT maternité de référence
→ Surveillance : réflexes ostéotendineux (si absents → arrêt MgSO4)

JAUNE — HTA modérée sans signes de gravité :
→ Méthyldopa 250–500 mg × 3/j (grossesse)
→ Repos au lit en décubitus latéral gauche
→ Contrôle tensionnel toutes les 4h
→ Régime hyposodé

Signes de gravité à surveiller :
Céphalées intenses, phosphènes, douleur épigastrique en barre, œdèmes
""".trimIndent()
        ),

        OmsProtocol(
            title = "Plaie et infection cutanée",
            category = "plaie",
            keywords = "plaie,blessure,coupure,infection,pus,abcès,gangrène,tétanos,morsure,brûlure",
            severity = "JAUNE",
            protocol = """
PROTOCOLE OMS — PLAIES ET INFECTIONS CUTANÉES
ROUGE — Urgences :
- Gangrène gazeuse (crépitation, odeur fétide) → Amputation possible
- Septicémie (fièvre + hypotension + tachycardie) → TRANSFERT
- Morsure animale (chien, singe) → Protocole antirabique
- Tétanos (rigidité musculaire, trismus) → TRANSFERT + SAT + anatoxine

Nettoyage plaie simple :
1. Lavage abondant eau propre + savon (5 min minimum)
2. Sérum physiologique pour rincer
3. Antiseptique : Povidone iodée 10% ou Chlorhexidine
4. Pansement stérile non serré
5. Vérifier vaccination tétanos (rappel si > 5 ans ou inconnue)

JAUNE — Plaie infectée (pus, rougeur, chaleur) :
→ Amoxicilline-clavulanate 875/125 mg × 2/j × 7 jours
→ Métronidazole si odeur anaérobie
→ Drainage si abcès fluctuant
→ Réévaluation à 48h

Brûlures :
- 1er degré : Eau froide 15 min, crème hydratante
- 2e degré : Pansement gras, douleur = JAUNE, référer si > 10% surface
- 3e degré : ROUGE, TRANSFERT immédiat, ne pas percer les cloques
""".trimIndent()
        ),

        OmsProtocol(
            title = "Paludisme (adulte)",
            category = "paludisme",
            keywords = "paludisme,malaria,fièvre,frissons,adulte,artémisinine,coartem,quinine,test rapide,TDR",
            severity = "JAUNE",
            protocol = """
PROTOCOLE OMS — PALUDISME ADULTE
Test diagnostique rapide (TDR) recommandé avant traitement.

ROUGE — Paludisme grave adulte :
- Trouble de conscience, convulsions
- Détresse respiratoire, ictère
- Saignements anormaux, hémoglobinurie
→ Artésunate IV 2,4 mg/kg à 0h, 12h, 24h puis 1×/j
→ Si IV impossible : Artésunate IM ou Artéméther IM
→ TRANSFERT en urgence

JAUNE — Paludisme simple adulte :
→ ACT Artéméther-Luméfantrine (Coartem) :
  4 cp × 2/j pendant 3 jours (avec nourriture grasse)
  Ou Artésunate-Amodiaquine selon disponibilité
→ Paracétamol 1g/6h si fièvre
→ Hydratation orale suffisante (au moins 2L/j)
→ Réévaluation à 72h si persistance

Prévention (zones rurales) :
- Moustiquaire imprégnée insecticide
- Chimioprophylaxie si grossesse (SP intermittente)
""".trimIndent()
        ),

        OmsProtocol(
            title = "Diarrhée aiguë (adulte)",
            category = "diarrhée",
            keywords = "diarrhée,gastroentérite,selles,vomissement,cholera,dysenterie,sang,déshydratation adulte",
            severity = "JAUNE",
            protocol = """
PROTOCOLE OMS — DIARRHÉE AIGUË ADULTE
ROUGE — Déshydratation sévère adulte :
- Pouls filant ou absent, hypotension, yeux très enfoncés
- Pli cutané > 2 secondes, confusion
→ Ringer lactate IV 30 mL/kg en 30 min, puis 70 mL/kg en 2h30
→ TRANSFERT si signes persistants

ROUGE — Cholera suspect :
- Diarrhée eau de riz, vomissements, crampes musculaires
→ Réhydratation IV massive + Doxycycline 300 mg dose unique
→ Déclaration obligatoire + isolement

JAUNE — Dysenterie (sang dans les selles) :
→ Ciprofloxacine 500 mg × 2/j × 3 jours
→ Métronidazole 500 mg × 3/j × 7 jours si Entamoeba suspectée
→ SRO

VERT — Diarrhée simple :
→ SRO : 200–400 mL après chaque selle liquide
→ Continuer alimentation normale
→ Zinc 20 mg/j × 10 jours
→ Consulter si > 3 jours, sang, fièvre > 38,5°C, arrêt mictions
""".trimIndent()
        ),

        OmsProtocol(
            title = "Serpent et envenimations",
            category = "envenimation",
            keywords = "serpent,morsure,vipère,cobra,envenimation,venin,gonflement,nécrose,antivenin",
            severity = "ROUGE",
            protocol = """
PROTOCOLE OMS — MORSURES DE SERPENT
TOUTE MORSURE DE SERPENT = URGENCE POTENTIELLE

Conduite immédiate (sur place) :
1. Immobiliser le membre atteint (attelle improvisée)
2. JAMAIS de garrot, d'incision, de succion, d'alcool
3. Retirer bagues, bracelets (œdème à venir)
4. Transport ALLONGÉ vers le centre de santé
5. Identifier le serpent si possible (ne pas capturer)

Centre de santé — évaluation :
ROUGE — Envenimation sévère :
- Gonflement progressif au-delà du coude/genou
- Nécrose, saignements (gencives, urine rosée)
- Troubles neurologiques (ptosis, paralysie)
→ Sérum antivenin polyvalent (selon disponibilité)
  Diluer dans 100 mL sérum physiologique, IV lent 30 min
  Adrénaline 0,5 mg SC en prémédication
→ TRANSFERT URGENT hôpital de référence

JAUNE — Envenimation légère :
- Douleur locale, légère rougeur, pas d'extension
→ Surveillance 24h
→ Paracétamol (pas d'ibuprofène)
→ Amoxicilline 1g × 2/j préventif

VERT — Morsure sèche (pas de venin injecté) :
- Pas de douleur progressive, pas d'œdème
→ Nettoyage plaie, vaccination antitétanique
→ Surveillance 12h
""".trimIndent()
        )
    )
}