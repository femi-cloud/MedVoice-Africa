package com.example.medvoiceafrica

object MedProtocols {
    data class DosageInfo(
        val mgPerKg: Double,
        val maxPerDayMg: Double,
        val dosesPerDay: Int,
        val instruction: String,
        val instructionEn: String = ""
    )
    fun DosageInfo.getInstruction(isFr: Boolean): String {
        if (isFr || instructionEn.isBlank()) return instruction
        return instructionEn.ifBlank { autoTranslate(instruction) }
    }

    private fun autoTranslate(fr: String): String = fr
        .replace("Toutes les 6 heures", "Every 6 hours")
        .replace("Toutes les 6h", "Every 6 hours")
        .replace("Toutes les 8 heures", "Every 8 hours")
        .replace("Toutes les 8h", "Every 8 hours")
        .replace("Toutes les 4-5h", "Every 4-5 hours")
        .replace("Matin et soir", "Morning and evening")
        .replace("matin et soir", "morning and evening")
        .replace("avec repas", "with food")
        .replace("avec repas gras", "with a fatty meal")
        .replace("à jeun", "on an empty stomach")
        .replace("À jeun", "On an empty stomach")
        .replace("1 fois/jour", "Once daily")
        .replace("1 fois par jour", "Once daily")
        .replace("Dose unique", "Single dose")
        .replace("dose unique", "single dose")
        .replace("Le matin", "In the morning")
        .replace("le matin", "in the morning")
        .replace("Le soir", "In the evening")
        .replace("le soir", "in the evening")
        .replace("Au coucher", "At bedtime")
        .replace("Réservé adulte", "Adults only")
        .replace("Réservé usage hospitalier", "Hospital use only")
        .replace("Hospitalier", "Hospital setting")
        .replace("hospitalier", "hospital setting")
        .replace("Éviter", "Avoid")
        .replace("Surveiller", "Monitor")
        .replace("Compléter le traitement", "Complete the full course")
        .replace("avant repas", "before meals")
        .replace("après repas", "after meals")
        .replace("Risque dépendance", "Risk of dependence")
        .replace("Ne pas arrêter brutalement", "Do not stop abruptly")
        .replace("30min avant repas", "30 min before meals")
        .replace("Éviter alcool", "Avoid alcohol")
        .replace("Colore les urines", "May discolor urine")
        .replace("Colore urines", "May discolor urine")
        .replace("Compléter le traitement", "Complete the full course")
        .replace("Protocole Coartem", "Coartem protocol")
        .replace("Résistances fréquentes", "Frequent resistance")
        .replace("Vérifier sensibilité locale", "Check local sensitivity")

    // ═══════════════════════════════════════════════════════════════
    // Protocoles de base (OMS) — 100+ molécules
    // Couverture : Bénin / Afrique de l'Ouest / Monde
    // Sources : OMS, CHMP, ANSM, formulaires nationaux africains
    // ═══════════════════════════════════════════════════════════════
    val protocols = mapOf(

        // ── ANTALGIQUES / ANTIPYRÉTIQUES ─────────────────────────

        "paracétamol"   to DosageInfo(15.0,  3000.0, 4,  "Toutes les 6 heures"),
        "paracetamol"   to DosageInfo(15.0,  3000.0, 4,  "Toutes les 6 heures"),
        "doliprane"     to DosageInfo(15.0,  3000.0, 4,  "Toutes les 6 heures"),
        "efferalgan"    to DosageInfo(15.0,  3000.0, 4,  "Toutes les 6 heures"),
        "dafalgan"      to DosageInfo(15.0,  3000.0, 4,  "Toutes les 6 heures"),
        "tylenol"       to DosageInfo(15.0,  3000.0, 4,  "Toutes les 6 heures"),
        "acetaminophen" to DosageInfo(15.0,  3000.0, 4,  "Toutes les 6 heures"),

        "ibuprofène"    to DosageInfo(10.0,  1200.0, 3,  "Toutes les 8 heures avec repas"),
        "ibuprofen"     to DosageInfo(10.0,  1200.0, 3,  "Toutes les 8 heures avec repas"),
        "advil"         to DosageInfo(10.0,  1200.0, 3,  "Toutes les 8 heures avec repas"),
        "nurofen"       to DosageInfo(10.0,  1200.0, 3,  "Toutes les 8 heures avec repas"),
        "brufen"        to DosageInfo(10.0,  1200.0, 3,  "Toutes les 8 heures avec repas"),

        "aspirine"      to DosageInfo(10.0,  4000.0, 4,  "Toutes les 6 heures avec repas. Éviter < 15 ans."),
        "aspirin"       to DosageInfo(10.0,  4000.0, 4,  "Toutes les 6 heures avec repas. Éviter < 15 ans."),
        "aspégic"       to DosageInfo(10.0,  4000.0, 4,  "Toutes les 6 heures avec repas. Éviter < 15 ans."),
        "cardioaspirin" to DosageInfo(1.0,   100.0,  1,  "1 fois par jour (usage cardio). Ne pas écraser."),

        "diclofénac"    to DosageInfo(1.0,   150.0,  3,  "Toutes les 8 heures avec repas. Réservé adulte."),
        "diclofenac"    to DosageInfo(1.0,   150.0,  3,  "Toutes les 8 heures avec repas."),
        "voltaren"      to DosageInfo(1.0,   150.0,  3,  "Toutes les 8 heures avec repas."),

        "kétoprofène"   to DosageInfo(1.0,   200.0,  3,  "Toutes les 8 heures avec repas."),
        "ketoprofene"   to DosageInfo(1.0,   200.0,  3,  "Toutes les 8 heures avec repas."),
        "profenid"      to DosageInfo(1.0,   200.0,  3,  "Toutes les 8 heures avec repas."),

        "naproxène"     to DosageInfo(5.0,   1000.0, 2,  "Matin et soir avec repas."),
        "naproxen"      to DosageInfo(5.0,   1000.0, 2,  "Matin et soir avec repas."),
        "apranax"       to DosageInfo(5.0,   1000.0, 2,  "Matin et soir avec repas."),

        "tramadol"      to DosageInfo(1.5,   400.0,  4,  "Toutes les 6h. Réservé adulte. Risque dépendance."),
        "contramal"     to DosageInfo(1.5,   400.0,  4,  "Toutes les 6h. Réservé adulte."),

        "codéine"       to DosageInfo(0.5,   240.0,  4,  "Toutes les 6h. Éviter < 12 ans. Risque dépendance."),
        "codeine"       to DosageInfo(0.5,   240.0,  4,  "Toutes les 6h. Éviter < 12 ans."),

        "morphine"      to DosageInfo(0.3,   200.0,  4,  "Réservé usage hospitalier. Titration stricte."),

        "phloroglucinol" to DosageInfo(0.0,  240.0,  3,  "Antispasmodique. 80mg 3x/j adulte. Sans alimentation."),
        "spasfon"       to DosageInfo(0.0,   240.0,  3,  "Antispasmodique. 80mg 3x/j adulte."),

        // ── ANTIBIOTIQUES — PÉNICILLINES ─────────────────────────

        "amoxicilline"          to DosageInfo(50.0,  3000.0, 3,  "Toutes les 8 heures. Compléter le traitement."),
        "amoxil"                to DosageInfo(50.0,  3000.0, 3,  "Toutes les 8 heures. Compléter le traitement."),
        "clamoxyl"              to DosageInfo(50.0,  3000.0, 3,  "Toutes les 8 heures. Compléter le traitement."),
        "augmentin"             to DosageInfo(45.0,  3000.0, 3,  "Toutes les 8h avec repas. Amoxicilline + acide clavulanique."),
        "amoxicilline-clavulanate" to DosageInfo(45.0, 3000.0, 3, "Toutes les 8h avec repas."),
        "ampicilline"           to DosageInfo(50.0,  4000.0, 4,  "Toutes les 6h à jeun. Voie IV en milieu hospitalier."),
        "ampicillin"            to DosageInfo(50.0,  4000.0, 4,  "Toutes les 6h à jeun."),
        "cloxacilline"          to DosageInfo(25.0,  4000.0, 4,  "Toutes les 6h à jeun. Anti-staphylocoque."),
        "oxacilline"            to DosageInfo(25.0,  4000.0, 4,  "Toutes les 6h à jeun. Anti-staphylocoque."),
        "benzylpénicilline"     to DosageInfo(0.0,   0.0,    4,  "Pénicilline G — voie IV/IM uniquement. Hospitalier."),
        "phenoxymethylpenicilline" to DosageInfo(12.5, 2000.0, 4, "Pénicilline V orale. Toutes les 6h à jeun."),

        // ── ANTIBIOTIQUES — CÉPHALOSPORINES ──────────────────────

        "céfalexine"        to DosageInfo(25.0,  4000.0, 4,  "Toutes les 6h. 1ère génération."),
        "cefalexine"        to DosageInfo(25.0,  4000.0, 4,  "Toutes les 6h. 1ère génération."),
        "keflex"            to DosageInfo(25.0,  4000.0, 4,  "Toutes les 6h."),
        "céfadroxil"        to DosageInfo(30.0,  2000.0, 2,  "Matin et soir. 1ère génération."),
        "cefadroxil"        to DosageInfo(30.0,  2000.0, 2,  "Matin et soir."),
        "céfuroxime"        to DosageInfo(20.0,  1500.0, 2,  "Matin et soir avec repas. 2ème génération."),
        "cefuroxime"        to DosageInfo(20.0,  1500.0, 2,  "Matin et soir avec repas."),
        "zinnat"            to DosageInfo(20.0,  1500.0, 2,  "Matin et soir avec repas."),
        "céfixime"          to DosageInfo(8.0,   400.0,  1,  "1 fois par jour. 3ème génération. (cystite, ORL)"),
        "cefixime"          to DosageInfo(8.0,   400.0,  1,  "1 fois par jour. 3ème génération."),
        "oroken"            to DosageInfo(8.0,   400.0,  1,  "1 fois par jour."),
        "ceftriaxone"       to DosageInfo(50.0,  4000.0, 1,  "1 fois par jour IV/IM. Hospitalier. 3ème génération."),
        "cefotaxime"        to DosageInfo(100.0, 12000.0,3,  "Toutes les 8h IV. Méningites/sepsis. Hospitalier."),
        "céfépime"          to DosageInfo(50.0,  6000.0, 2,  "Matin et soir IV. 4ème génération. Hospitalier."),

        // ── ANTIBIOTIQUES — MACROLIDES ────────────────────────────

        "azithromycine"     to DosageInfo(10.0,  500.0,  1,  "1 fois/jour pendant 3-5 jours. À jeun de préférence."),
        "azithromycin"      to DosageInfo(10.0,  500.0,  1,  "1 fois/jour pendant 3-5 jours."),
        "zithromax"         to DosageInfo(10.0,  500.0,  1,  "1 fois/jour pendant 3-5 jours."),
        "sumamed"           to DosageInfo(10.0,  500.0,  1,  "1 fois/jour pendant 3-5 jours."),
        "érythromycine"     to DosageInfo(30.0,  4000.0, 4,  "Toutes les 6h. Allergie pénicilline."),
        "erythromycine"     to DosageInfo(30.0,  4000.0, 4,  "Toutes les 6h. Allergie pénicilline."),
        "clarithromycine"   to DosageInfo(7.5,   1000.0, 2,  "Matin et soir avec repas."),
        "clarithromycin"    to DosageInfo(7.5,   1000.0, 2,  "Matin et soir avec repas."),
        "spiramycine"       to DosageInfo(75000.0, 9000000.0, 3, "Toutes les 8h. Toxoplasmose, infections ORL."),
        "rovamycine"        to DosageInfo(75000.0, 9000000.0, 3, "Toutes les 8h."),

        // ── ANTIBIOTIQUES — FLUOROQUINOLONES ─────────────────────

        "ciprofloxacine"    to DosageInfo(15.0,  1500.0, 2,  "Matin et soir. Réservé adulte. Éviter soleil."),
        "ciprofloxacin"     to DosageInfo(15.0,  1500.0, 2,  "Matin et soir. Réservé adulte."),
        "cipro"             to DosageInfo(15.0,  1500.0, 2,  "Matin et soir. Réservé adulte."),
        "ofloxacine"        to DosageInfo(7.5,   800.0,  2,  "Matin et soir. Réservé adulte."),
        "ofloxacin"         to DosageInfo(7.5,   800.0,  2,  "Matin et soir. Réservé adulte."),
        "norfloxacine"      to DosageInfo(0.0,   800.0,  2,  "Matin et soir à jeun. Infections urinaires adulte."),
        "norfloxacin"       to DosageInfo(0.0,   800.0,  2,  "Matin et soir à jeun."),
        "lévofloxacine"     to DosageInfo(0.0,   750.0,  1,  "1 fois/jour. Pneumonies, infections graves. Adulte."),
        "levofloxacin"      to DosageInfo(0.0,   750.0,  1,  "1 fois/jour. Réservé adulte."),
        "moxifloxacine"     to DosageInfo(0.0,   400.0,  1,  "1 fois/jour. Pneumonies communautaires. Adulte."),
        "moxifloxacin"      to DosageInfo(0.0,   400.0,  1,  "1 fois/jour. Adulte."),

        // ── ANTIBIOTIQUES — TÉTRACYCLINES ────────────────────────

        "doxycycline"       to DosageInfo(2.2,   200.0,  2,  "Matin et soir avec eau abondante. Éviter < 8 ans."),
        "doxycyclin"        to DosageInfo(2.2,   200.0,  2,  "Matin et soir avec eau abondante. Éviter < 8 ans."),
        "vibramycine"       to DosageInfo(2.2,   200.0,  2,  "Matin et soir avec eau abondante."),
        "tétracycline"      to DosageInfo(25.0,  2000.0, 4,  "Toutes les 6h à jeun. Éviter < 8 ans, grossesse."),
        "tetracycline"      to DosageInfo(25.0,  2000.0, 4,  "Toutes les 6h à jeun."),

        // ── ANTIBIOTIQUES — NITROIMIDAZOLES ──────────────────────

        "métronidazole"     to DosageInfo(15.0,  2400.0, 3,  "Toutes les 8h avec repas. Éviter alcool +48h après."),
        "metronidazole"     to DosageInfo(15.0,  2400.0, 3,  "Toutes les 8h avec repas. Éviter alcool +48h après."),
        "flagyl"            to DosageInfo(15.0,  2400.0, 3,  "Toutes les 8h avec repas. Éviter alcool."),
        "tinidazole"        to DosageInfo(50.0,  2000.0, 1,  "1 fois/jour. Amibiase, trichomonase."),
        "secnidazole"       to DosageInfo(30.0,  2000.0, 1,  "Dose unique. Amibiase intestinale."),
        "flagentyl"         to DosageInfo(30.0,  2000.0, 1,  "Dose unique."),

        // ── ANTIBIOTIQUES — SULFAMIDES / COTRIMOXAZOLE ───────────

        "cotrimoxazole"     to DosageInfo(6.0,   1920.0, 2,  "Matin et soir avec eau. Éviter < 6 semaines."),
        "bactrim"           to DosageInfo(6.0,   1920.0, 2,  "Matin et soir avec eau. Éviter < 6 semaines."),
        "septrin"           to DosageInfo(6.0,   1920.0, 2,  "Matin et soir avec eau."),
        "triméthoprime"     to DosageInfo(4.0,   320.0,  2,  "Matin et soir. Infections urinaires."),
        "trimethoprime"     to DosageInfo(4.0,   320.0,  2,  "Matin et soir."),

        // ── ANTIBIOTIQUES — AMINOSIDES ───────────────────────────

        "gentamicine"       to DosageInfo(5.0,   240.0,  1,  "1 fois/jour IV/IM. Hospitalier. Surveiller la rénale."),
        "gentamicin"        to DosageInfo(5.0,   240.0,  1,  "Hospitalier. Surveiller fonction rénale."),
        "amikacine"         to DosageInfo(15.0,  1500.0, 1,  "1 fois/jour IV. Hospitalier. Néphrotoxique."),
        "amikacin"          to DosageInfo(15.0,  1500.0, 1,  "Hospitalier. Néphrotoxique."),
        "streptomycine"     to DosageInfo(15.0,  1000.0, 1,  "IM uniquement. Tuberculose. Hospitalisé."),
        "streptomycin"      to DosageInfo(15.0,  1000.0, 1,  "IM uniquement. Tuberculose."),

        // ── ANTIBIOTIQUES — AUTRES ────────────────────────────────

        "rifampicine"       to DosageInfo(10.0,  600.0,  1,  "À jeun le matin. Tuberculose. Colore les urines en rouge."),
        "rifampicin"        to DosageInfo(10.0,  600.0,  1,  "À jeun le matin. Tuberculose."),
        "rifadine"          to DosageInfo(10.0,  600.0,  1,  "À jeun le matin. Tuberculose."),
        "isoniazide"        to DosageInfo(5.0,   300.0,  1,  "1 fois/jour à jeun. Tuberculose. Avec vit B6."),
        "isoniazid"         to DosageInfo(5.0,   300.0,  1,  "1 fois/jour à jeun. Tuberculose."),
        "pyrazinamide"      to DosageInfo(25.0,  2000.0, 1,  "1 fois/jour. Tuberculose (phase initiale)."),
        "éthambutol"        to DosageInfo(15.0,  2500.0, 1,  "1 fois/jour. Tuberculose. Surveiller la vision."),
        "ethambutol"        to DosageInfo(15.0,  2500.0, 1,  "1 fois/jour. Surveiller la vision."),
        "chloramphénicol"   to DosageInfo(25.0,  4000.0, 4,  "Toutes les 6h. Méningites, fièvre typhoïde. Surveiller NFS."),
        "chloramphenicol"   to DosageInfo(25.0,  4000.0, 4,  "Toutes les 6h. Surveiller NFS."),
        "clindamycine"      to DosageInfo(10.0,  1800.0, 3,  "Toutes les 8h. Infections dentaires, cutanées."),
        "clindamycin"       to DosageInfo(10.0,  1800.0, 3,  "Toutes les 8h."),
        "dalacine"          to DosageInfo(10.0,  1800.0, 3,  "Toutes les 8h."),
        "nitrofurantoïne"   to DosageInfo(3.0,   400.0,  4,  "Toutes les 6h avec repas. Cystites. Colore urines."),
        "nitrofurantoin"    to DosageInfo(3.0,   400.0,  4,  "Toutes les 6h avec repas."),
        "fosfomycine"       to DosageInfo(0.0,   3000.0, 1,  "Dose unique. Cystite aiguë femme. Sachet dans eau."),
        "monuril"           to DosageInfo(0.0,   3000.0, 1,  "Dose unique. Cystite aiguë."),
        "vancomycine"       to DosageInfo(40.0,  4000.0, 4,  "IV lent. Hospitalier. Infections à SARM."),
        "vancomycin"        to DosageInfo(40.0,  4000.0, 4,  "IV lent. Hospitalier."),

        // ── ANTIPALUDIQUES ────────────────────────────────────────

        "artémether"        to DosageInfo(3.2,   80.0,   1,  "Protocole Coartem : 6 prises sur 3 jours avec repas gras."),
        "artemether"        to DosageInfo(3.2,   80.0,   1,  "Protocole Coartem : 6 prises sur 3 jours avec repas."),
        "coartem"           to DosageInfo(3.2,   80.0,   1,  "Artémether+luméfantrine. Prendre avec aliment gras."),
        "artésunate"        to DosageInfo(4.0,   200.0,  1,  "Paludisme grave : IV/IM hospitalier. Protocole OMS."),
        "artesunate"        to DosageInfo(4.0,   200.0,  1,  "Paludisme grave. Hospitalier."),
        "luméfantrine"      to DosageInfo(0.0,   480.0,  2,  "Combiné avec artémether (Coartem). Ne pas prendre seul."),
        "artémisinine"      to DosageInfo(10.0,  500.0,  1,  "Monotonie déconseillée (résistances). Toujours en combinaison."),
        "artemisinine"      to DosageInfo(10.0,  500.0,  1,  "Toujours en combinaison ACT."),
        "chloroquine"       to DosageInfo(10.0,  600.0,  1,  "Résistances fréquentes en Afrique de l'Ouest. Vérifier sensibilité locale."),
        "chloroquin"        to DosageInfo(10.0,  600.0,  1,  "Vérifier sensibilité locale."),
        "nivaquine"         to DosageInfo(10.0,  600.0,  1,  "Vérifier sensibilité locale."),
        "quinine"           to DosageInfo(10.0,  1800.0, 3,  "Toutes les 8h. Paludisme grave. Surveiller glycémie et QT."),
        "quininesulfate"    to DosageInfo(10.0,  1800.0, 3,  "Toutes les 8h. Surveiller glycémie."),
        "méfloquine"        to DosageInfo(15.0,  1500.0, 1,  "1 fois/semaine (prophylaxie) ou protocole curatif. Avec repas."),
        "mefloquine"        to DosageInfo(15.0,  1500.0, 1,  "1 fois/semaine. Avec repas."),
        "lariam"            to DosageInfo(15.0,  1500.0, 1,  "Avec repas et eau abondante."),
        "amodiaquine"       to DosageInfo(10.0,  600.0,  1,  "1 fois/jour 3 jours (ASAQ). Souvent combinée avec artésunate."),
        "doxycycline-malaria" to DosageInfo(1.5, 100.0,  1,  "Prophylaxie paludisme : 100mg/j adulte à partir de 8 ans."),
        "atovaquone"        to DosageInfo(5.0,   1000.0, 1,  "Avec repas gras. Combiné proguanil (Malarone)."),
        "malarone"          to DosageInfo(5.0,   1000.0, 1,  "Avec repas. Prophylaxie ou traitement."),
        "proguanil"         to DosageInfo(3.0,   200.0,  1,  "Combiné atovaquone. 1 fois/jour avec repas."),
        "primaquine"        to DosageInfo(0.5,   30.0,   1,  "Anti-rechute Plasmodium vivax/ovale. Vérifier G6PD avant."),

        // ── ANTIFONGIQUES ─────────────────────────────────────────

        "fluconazole"       to DosageInfo(6.0,   800.0,  1,  "1 fois/jour. Candidoses, méningite cryptococcique."),
        "fluconazol"        to DosageInfo(6.0,   800.0,  1,  "1 fois/jour."),
        "triflucan"         to DosageInfo(6.0,   800.0,  1,  "1 fois/jour."),
        "kétoconazole"      to DosageInfo(5.0,   400.0,  1,  "1 fois/jour avec repas. Usage limité (hépatotoxicité)."),
        "ketoconazole"      to DosageInfo(5.0,   400.0,  1,  "1 fois/jour avec repas."),
        "griséofulvine"     to DosageInfo(10.0,  1000.0, 2,  "Matin et soir avec repas gras. Teigne, mycoses des ongles."),
        "griseofulvine"     to DosageInfo(10.0,  1000.0, 2,  "Matin et soir avec repas gras."),
        "grisovin"          to DosageInfo(10.0,  1000.0, 2,  "Avec repas gras."),
        "nystatine"         to DosageInfo(0.0,   6000000.0, 4, "Suspension buccale : 500 000 UI/prise. Garder en bouche."),
        "nystatin"          to DosageInfo(0.0,   6000000.0, 4, "Suspension buccale. Garder avant d'avaler."),
        "nystatine-buvable" to DosageInfo(0.0,   6000000.0, 4, "Muguet nourrisson : 100 000 UI 4x/j. Garder en bouche."),
        "amphotéricine"     to DosageInfo(0.5,   50.0,   1,  "IV lent hospitalier. Cryptococcose, aspergillose. Néphrotoxique."),
        "amphotericine"     to DosageInfo(0.5,   50.0,   1,  "IV hospitalier. Néphrotoxique."),
        "itraconazole"      to DosageInfo(5.0,   400.0,  2,  "Matin et soir après repas. Mycoses profondes."),
        "itraconazol"       to DosageInfo(5.0,   400.0,  2,  "Après repas. Mycoses profondes."),
        "sporanox"          to DosageInfo(5.0,   400.0,  2,  "Après repas."),
        "terbinafine"       to DosageInfo(0.0,   250.0,  1,  "250mg/j adulte. Teigne, onyxomycose. Durée 6-12 semaines."),
        "lamisil"           to DosageInfo(0.0,   250.0,  1,  "250mg/j adulte. Teigne, mycoses ongles."),
        "clotrimazole"      to DosageInfo(0.0,   0.0,    3,  "Usage topique uniquement. Crème ou ovule. 3x/j en local."),
        "miconazole"        to DosageInfo(0.0,   0.0,    3,  "Usage topique. Mycoses cutanées et vaginales."),
        "econazole"         to DosageInfo(0.0,   0.0,    2,  "Usage topique. 2x/j en local."),
        "voriconazole"      to DosageInfo(8.0,   800.0,  2,  "Matin et soir à jeun. Aspergillose. Hospitalier."),
        "voriconazol"       to DosageInfo(8.0,   800.0,  2,  "À jeun. Hospitalier."),

        // ── ANTIPARASITAIRES / ANTIHELMINTHIQUES ─────────────────

        "albendazole"       to DosageInfo(7.5,   400.0,  1,  "Dose unique 400mg (> 2 ans). Avec repas gras pour absorption."),
        "albendazol"        to DosageInfo(7.5,   400.0,  1,  "Dose unique. Avec repas gras."),
        "zentel"            to DosageInfo(7.5,   400.0,  1,  "Dose unique 400mg."),
        "mébendazole"       to DosageInfo(0.0,   200.0,  2,  "100mg 2x/j pendant 3 jours ou 500mg dose unique. Oxyures."),
        "mebendazole"       to DosageInfo(0.0,   200.0,  2,  "Matin et soir 3 jours ou dose unique."),
        "vermox"            to DosageInfo(0.0,   200.0,  2,  "100mg 2x/j 3 jours."),
        "ivermectine"       to DosageInfo(0.2,   12.0,   1,  "Dose unique à jeun. Onchocercose, strongyloïdose. Répéter si besoin."),
        "ivermectin"        to DosageInfo(0.2,   12.0,   1,  "Dose unique à jeun. Onchocercose."),
        "stromectol"        to DosageInfo(0.2,   12.0,   1,  "Dose unique à jeun."),
        "praziquantel"      to DosageInfo(40.0,  3000.0, 3,  "3 prises sur 1 jour. Bilharziose, téniase. Avec repas."),
        "praziquantel-bilharziose" to DosageInfo(40.0, 3000.0, 3, "Avec repas. Traitement bilharziose en 1 jour."),
        "niclosamide"       to DosageInfo(50.0,  2000.0, 1,  "Dose unique mâchée avant avalement. Téniase. À jeun."),
        "pyrantel"          to DosageInfo(10.0,  1000.0, 1,  "Dose unique. Oxyures, ankylostomes. Avec ou sans repas."),
        "combantrin"        to DosageInfo(10.0,  1000.0, 1,  "Dose unique. Oxyures."),
        "diéthylcarbamazine" to DosageInfo(6.0,  400.0,  3,  "Toutes les 8h. Filariose, loase. Hospitalier."),
        "suramine"          to DosageInfo(0.0,   0.0,    1,  "IV hospitalier uniquement. Trypanosomiase stade 1."),
        "pentamidine"       to DosageInfo(4.0,   300.0,  1,  "IV/IM. Trypanosomiase, leishmaniose. Hospitalier. Surveiller glycémie."),
        "méglumine antimoniate" to DosageInfo(20.0, 0.0, 1,  "IM/IV. Leishmaniose viscérale. Hospitalier."),

        // ── ANTIVIRAUX ────────────────────────────────────────────

        "aciclovir"         to DosageInfo(20.0,  4000.0, 5,  "Toutes les 4-5h. Herpès, zona, varicelle."),
        "acyclovir"         to DosageInfo(20.0,  4000.0, 5,  "Toutes les 4-5h."),
        "zovirax"           to DosageInfo(20.0,  4000.0, 5,  "Toutes les 4-5h."),
        "valaciclovir"      to DosageInfo(20.0,  3000.0, 3,  "Toutes les 8h. Forme orale d'aciclovir."),
        "valacyclovir"      to DosageInfo(20.0,  3000.0, 3,  "Toutes les 8h."),
        "zelitrex"          to DosageInfo(20.0,  3000.0, 3,  "Toutes les 8h."),
        "ritonavir"         to DosageInfo(0.0,   400.0,  2,  "Matin et soir avec repas. Booster pharmacocinétique ARV."),
        "lopinavir"         to DosageInfo(0.0,   800.0,  2,  "Matin et soir avec repas. Kaletra (lopinavir+ritonavir)."),
        "efavirenz"         to DosageInfo(0.0,   600.0,  1,  "Au coucher à jeun. ARV. Effets neuropsychiques nocturnes."),
        "névirapine"        to DosageInfo(4.0,   400.0,  2,  "Matin et soir. ARV première ligne. Surveiller bilan hépatique."),
        "nevirapine"        to DosageInfo(4.0,   400.0,  2,  "Matin et soir. Surveiller bilan hépatique."),
        "tenofovir"         to DosageInfo(0.0,   300.0,  1,  "1 fois/jour avec repas. ARV. Surveiller créatinine."),
        "emtricitabine"     to DosageInfo(0.0,   200.0,  1,  "1 fois/jour. ARV."),
        "lamivudine"        to DosageInfo(4.0,   300.0,  1,  "1 fois/jour. ARV et hépatite B."),
        "dolutegravir"      to DosageInfo(0.0,   50.0,   1,  "1 fois/jour. ARV intégrateur. Avec ou sans repas."),
        "abacavir"          to DosageInfo(8.0,   600.0,  1,  "1 fois/jour. ARV. Tester HLA-B*5701 avant."),
        "zidovudine"        to DosageInfo(4.0,   600.0,  3,  "Toutes les 8h. ARV. Surveiller NFS (anémie)."),
        "azt"               to DosageInfo(4.0,   600.0,  3,  "Toutes les 8h. Surveiller NFS."),
        "oseltamivir"       to DosageInfo(2.0,   150.0,  2,  "Matin et soir 5 jours. Grippe. Débuter < 48h."),
        "tamiflu"           to DosageInfo(2.0,   150.0,  2,  "Matin et soir 5 jours. Grippe."),
        "ribavirin"         to DosageInfo(15.0,  1200.0, 2,  "Matin et soir avec repas. Hépatite C, FHV. Tératogène."),
        "ribavirine"        to DosageInfo(15.0,  1200.0, 2,  "Matin et soir avec repas. Tératogène."),
        "sofosbuvir"        to DosageInfo(0.0,   400.0,  1,  "1 fois/jour. Hépatite C. Avec daclatasvir ou ribavirine."),
        "daclatasvir"       to DosageInfo(0.0,   60.0,   1,  "1 fois/jour. Hépatite C. Avec sofosbuvir."),

        // ── ANTITUBERCULEUX ───────────────────────────────────────

        "rifampicine-tb"    to DosageInfo(10.0,  600.0,  1,  "TB : à jeun le matin. Phase intensive + continuation."),
        "isoniazide-tb"     to DosageInfo(5.0,   300.0,  1,  "TB : 1 fois/jour. Avec pyridoxine (vit B6) 25mg/j."),
        "pyrazinamide-tb"   to DosageInfo(25.0,  2000.0, 1,  "TB : phase intensive 2 mois. Surveiller acide urique."),
        "éthambutol-tb"     to DosageInfo(15.0,  2500.0, 1,  "TB : phase intensive. Contrôle ophtalmologique."),
        "rhez"              to DosageInfo(0.0,   0.0,    1,  "Quadrithérapie TB (RHZE) : combinaison fixe dose."),

        // ── ANTIDIABÉTIQUES ───────────────────────────────────────

        "metformine"        to DosageInfo(0.0,   3000.0, 3,  "Toutes les 8h avec repas. Diabète type 2. Débuter 500mg/j."),
        "metformin"         to DosageInfo(0.0,   3000.0, 3,  "Toutes les 8h avec repas."),
        "glucophage"        to DosageInfo(0.0,   3000.0, 3,  "Toutes les 8h avec repas."),
        "glibenclamide"     to DosageInfo(0.0,   20.0,   2,  "Matin et soir avant repas. Sulfonylurée. Surveiller glycémie."),
        "daonil"            to DosageInfo(0.0,   20.0,   2,  "Avant repas. Surveiller hypoglycémies."),
        "glimepiride"       to DosageInfo(0.0,   8.0,    1,  "1 fois/jour avant petit-déjeuner. Sulfonylurée."),
        "amaryl"            to DosageInfo(0.0,   8.0,    1,  "Avant petit-déjeuner."),
        "gliclazide"        to DosageInfo(0.0,   120.0,  1,  "1 fois/jour avant repas. Sulfonylurée (LP)."),
        "diamicron"         to DosageInfo(0.0,   120.0,  1,  "1 fois/jour. Sulfonylurée LP."),
        "glipizide"         to DosageInfo(0.0,   20.0,   2,  "Avant repas matin et soir. Sulfonylurée."),
        "insuline"          to DosageInfo(0.0,   0.0,    1,  "Dose individuelle. SC. Surveiller glycémie strictement."),
        "insulin"           to DosageInfo(0.0,   0.0,    1,  "Dose individuelle. Sous-cutané. Protocole hospitalo."),
        "sitagliptine"      to DosageInfo(0.0,   100.0,  1,  "1 fois/jour. DPP4. Avec ou sans repas."),
        "januvia"           to DosageInfo(0.0,   100.0,  1,  "1 fois/jour."),

        // ── ANTIHYPERTENSEURS ─────────────────────────────────────

        "amlodipine"        to DosageInfo(0.0,   10.0,   1,  "1 fois/jour. Inhibiteur calcique. Matin de préférence."),
        "amlor"             to DosageInfo(0.0,   10.0,   1,  "1 fois/jour. Matin."),
        "captopril"         to DosageInfo(0.0,   150.0,  3,  "Toutes les 8h à jeun. IEC. Surveiller kaliémie."),
        "lopril"            to DosageInfo(0.0,   150.0,  3,  "Toutes les 8h à jeun."),
        "énalapril"         to DosageInfo(0.0,   40.0,   2,  "Matin et soir. IEC. Surveiller kaliémie."),
        "enalapril"         to DosageInfo(0.0,   40.0,   2,  "Matin et soir."),
        "renitec"           to DosageInfo(0.0,   40.0,   2,  "Matin et soir."),
        "lisinopril"        to DosageInfo(0.0,   40.0,   1,  "1 fois/jour. IEC."),
        "losartan"          to DosageInfo(0.0,   100.0,  1,  "1 fois/jour. ARA2. Avec ou sans repas."),
        "cozaar"            to DosageInfo(0.0,   100.0,  1,  "1 fois/jour."),
        "valsartan"         to DosageInfo(0.0,   320.0,  1,  "1 fois/jour. ARA2."),
        "nifédipine"        to DosageInfo(0.0,   120.0,  3,  "Toutes les 8h. Inhibiteur calcique. Forme LP préférable."),
        "nifedipine"        to DosageInfo(0.0,   120.0,  3,  "Toutes les 8h ou 1x/j forme LP."),
        "adalat"            to DosageInfo(0.0,   120.0,  1,  "Forme LP 1 fois/jour. Inhibiteur calcique."),
        "aténolol"          to DosageInfo(0.0,   100.0,  1,  "1 fois/jour. Bêtabloquant. Pas d'arrêt brutal."),
        "atenolol"          to DosageInfo(0.0,   100.0,  1,  "1 fois/jour. Ne pas arrêter brutalement."),
        "tenormine"         to DosageInfo(0.0,   100.0,  1,  "1 fois/jour."),
        "métoprolol"        to DosageInfo(0.0,   200.0,  2,  "Matin et soir. Bêtabloquant."),
        "metoprolol"        to DosageInfo(0.0,   200.0,  2,  "Matin et soir."),
        "propranolol"       to DosageInfo(0.0,   320.0,  3,  "Toutes les 8h. Bêtabloquant. Ne pas arrêter brutalement."),
        "avlocardyl"        to DosageInfo(0.0,   320.0,  3,  "Toutes les 8h."),
        "hydrochlorothiazide" to DosageInfo(0.0, 50.0,   1,  "1 fois/jour le matin. Diurétique thiazidique."),
        "hctz"              to DosageInfo(0.0,   50.0,   1,  "Le matin. Diurétique."),
        "furosémide"        to DosageInfo(1.0,   80.0,   1,  "Le matin. Diurétique de l'anse. Surveiller kaliémie."),
        "furosemide"        to DosageInfo(1.0,   80.0,   1,  "Le matin. Surveiller kaliémie."),
        "lasilix"           to DosageInfo(1.0,   80.0,   1,  "Le matin. Surveiller kaliémie."),
        "spironolactone"    to DosageInfo(0.0,   200.0,  2,  "Matin et soir avec repas. Diurétique épargneur K+."),
        "aldactone"         to DosageInfo(0.0,   200.0,  2,  "Avec repas. Épargneur potassium."),
        "méthyldopa"        to DosageInfo(10.0,  3000.0, 3,  "Toutes les 8h. Hypertension grossesse. Central."),
        "methyldopa"        to DosageInfo(10.0,  3000.0, 3,  "Toutes les 8h. Grossesse."),
        "aldomet"           to DosageInfo(10.0,  3000.0, 3,  "Toutes les 8h. Grossesse."),

        // ── CARDIOTONIQUES / ANTI-ANGINEUX ───────────────────────

        "digoxine"          to DosageInfo(0.0,   0.25,   1,  "Dose de charge puis entretien. Surveiller kaliémie + ECG."),
        "digoxin"           to DosageInfo(0.0,   0.25,   1,  "Marge thérapeutique étroite. ECG + kaliémie."),
        "nitroglycerine"    to DosageInfo(0.0,   0.0,    3,  "Sublingual. 0,5mg en cas de crise angineuse. Max 3 en 15min."),
        "trinitrine"        to DosageInfo(0.0,   0.0,    3,  "Sublingual à la demande. Angine."),
        "isosorbide"        to DosageInfo(0.0,   120.0,  3,  "Toutes les 8h. Prévention angine. Tolérance si continu."),
        "cordarone"         to DosageInfo(0.0,   400.0,  3,  "Phase charge puis entretien 200mg/j. Surveiller thyroïde."),
        "amiodarone"        to DosageInfo(0.0,   400.0,  3,  "Charge puis entretien. Surveiller thyroïde et poumons."),
        "simvastatine"      to DosageInfo(0.0,   80.0,   1,  "Le soir. Statine. Surveiller CPK. Éviter pamplemousse."),
        "simvastatin"       to DosageInfo(0.0,   80.0,   1,  "Le soir."),
        "atorvastatine"     to DosageInfo(0.0,   80.0,   1,  "1 fois/jour. Statine. Surveiller CPK."),
        "atorvastatin"      to DosageInfo(0.0,   80.0,   1,  "1 fois/jour."),
        "tahor"             to DosageInfo(0.0,   80.0,   1,  "1 fois/jour."),
        "rosuvastatine"     to DosageInfo(0.0,   40.0,   1,  "1 fois/jour. Statine."),
        "rosuvastatin"      to DosageInfo(0.0,   40.0,   1,  "1 fois/jour."),

        // ── ANTICOAGULANTS / ANTIAGRÉGANTS ───────────────────────

        "warfarine"         to DosageInfo(0.0,   10.0,   1,  "1 fois/jour. Adapter selon INR cible. Interactions multiples."),
        "warfarin"          to DosageInfo(0.0,   10.0,   1,  "1 fois/jour. Surveiller INR."),
        "coumadine"         to DosageInfo(0.0,   10.0,   1,  "Adapter à l'INR. Interactions multiples."),
        "héparine"          to DosageInfo(0.0,   0.0,    1,  "IV/SC. Dose selon poids et indication. Hospitalier."),
        "heparine"          to DosageInfo(0.0,   0.0,    1,  "IV/SC. Hospitalier. Surveiller TCA."),
        "enoxaparine"       to DosageInfo(1.0,   0.0,    1,  "SC 1 fois/jour (prophylaxie) ou 2x/j (curatif). HBPM."),
        "lovenox"           to DosageInfo(1.0,   0.0,    1,  "SC selon indication."),
        "clopidogrel"       to DosageInfo(0.0,   75.0,   1,  "1 fois/jour. Anti-agrégant. Post-SCA, stent."),
        "plavix"            to DosageInfo(0.0,   75.0,   1,  "1 fois/jour. Antiagrégant."),
        "dabigatran"        to DosageInfo(0.0,   300.0,  2,  "Matin et soir. Anticoagulant oral direct. Capsule entière."),
        "pradaxa"           to DosageInfo(0.0,   300.0,  2,  "Matin et soir. Capsule entière."),
        "rivaroxaban"       to DosageInfo(0.0,   40.0,   1,  "1 fois/jour avec repas. Anticoagulant oral direct."),
        "xarelto"           to DosageInfo(0.0,   40.0,   1,  "Avec repas."),

        // ── BRONCHODILATATEURS / RESPIRATOIRE ────────────────────

        "salbutamol"        to DosageInfo(0.1,   1.6,    4,  "Bronchodilatateur : 2 bouffées à la demande. Max 8/j."),
        "albuterol"         to DosageInfo(0.1,   1.6,    4,  "2 bouffées à la demande."),
        "ventoline"         to DosageInfo(0.1,   1.6,    4,  "2 bouffées à la demande. Max 8/j."),
        "terbutaline"       to DosageInfo(0.075, 10.0,   3,  "Toutes les 8h. Bronchodilatateur. 5mg nébulisation."),
        "bricanyl"          to DosageInfo(0.075, 10.0,   3,  "Toutes les 8h."),
        "béclométasone"     to DosageInfo(0.0,   2000.0, 2,  "Inhalation matin et soir. Corticoïde local. Rincer bouche."),
        "becotide"          to DosageInfo(0.0,   2000.0, 2,  "Matin et soir inhalé. Rincer bouche après."),
        "budésonide"        to DosageInfo(0.0,   1600.0, 2,  "Inhalation matin et soir. Corticoïde local."),
        "budesonide"        to DosageInfo(0.0,   1600.0, 2,  "Matin et soir inhalé."),
        "fluticasone"       to DosageInfo(0.0,   1000.0, 2,  "Inhalation matin et soir. Asthme."),
        "flixotide"         to DosageInfo(0.0,   1000.0, 2,  "Inhalation matin et soir."),
        "théophylline"      to DosageInfo(5.0,   900.0,  3,  "Toutes les 8h (forme LP 2x/j). Marge étroite. Surveiller."),
        "theophylline"      to DosageInfo(5.0,   900.0,  3,  "Toutes les 8h. Marge thérapeutique étroite."),
        "montélukast"       to DosageInfo(0.0,   10.0,   1,  "1 fois/jour le soir. Asthme/rhinite allergique."),
        "montelukast"       to DosageInfo(0.0,   10.0,   1,  "Le soir."),
        "singulair"         to DosageInfo(0.0,   10.0,   1,  "Le soir."),
        "ipratropium"       to DosageInfo(0.0,   0.5,    4,  "Nébulisation ou MDI 2 bouffées 4x/j. BPCO, bronchospasme."),
        "atrovent"          to DosageInfo(0.0,   0.5,    4,  "2 bouffées 4x/j."),
        "tiotropium"        to DosageInfo(0.0,   18.0,   1,  "1 capsule/j inhalée. BPCO. Ne pas avaler."),
        "spiriva"           to DosageInfo(0.0,   18.0,   1,  "1 capsule inhalée/j. BPCO."),
        "prednisolone-asthme" to DosageInfo(1.0, 60.0,   1,  "1 fois/jour le matin. Crise asthme. Cure courte 5-7j."),
        "dexamethasone-resp" to DosageInfo(0.15, 12.0,   1,  "Oedème laryngé, faux-croup. Dose unique IM ou orale."),

        // ── CORTICOÏDES SYSTÉMIQUES ───────────────────────────────

        "prednisone"        to DosageInfo(0.5,   60.0,   1,  "1 fois/jour le matin. Diminuer progressivement."),
        "prednisolone"      to DosageInfo(0.5,   60.0,   1,  "1 fois/jour le matin. Diminution progressive obligatoire."),
        "cortancyl"         to DosageInfo(0.5,   60.0,   1,  "Le matin. Ne pas arrêter brutalement."),
        "solupred"          to DosageInfo(0.5,   60.0,   1,  "Le matin. Comprimés effervescents. Ne pas arrêter brutalement."),
        "dexaméthasone"     to DosageInfo(0.15,  24.0,   1,  "Puissant. Méningites bactériennes, urgences. Potassium++."),
        "dexamethasone"     to DosageInfo(0.15,  24.0,   1,  "1 à 2 fois/jour selon indication."),
        "hydrocortisone"    to DosageInfo(1.0,   300.0,  3,  "Toutes les 8h. Insuffisance surrénale. Hospitalier en urgence."),
        "hydrocortisone-crise" to DosageInfo(2.0, 400.0, 4,  "Crise surrénale : 100mg IV bolus puis 200mg/24h."),
        "bétaméthasone"     to DosageInfo(0.1,   8.0,    1,  "Très puissant. Maturation pulmonaire foetale : 2 x 12mg IM."),
        "betamethasone"     to DosageInfo(0.1,   8.0,    1,  "Usage ciblé. Maturation pulmonaire."),
        "triamcinolone"     to DosageInfo(0.0,   0.0,    1,  "Injection intra-articulaire ou locale. Spécialiste."),
        "methylprednisolone" to DosageInfo(0.5,  1000.0, 1,  "Bolus IV : 1g/j x 3j (pousse-seringue). Affections sévères."),
        "solumedrol"        to DosageInfo(0.5,   1000.0, 1,  "Bolus IV. Hospitalier."),

        // ── GASTRO-ENTÉROLOGIE ────────────────────────────────────

        "oméprazole"        to DosageInfo(0.0,   40.0,   1,  "1 fois/jour à jeun. IPP. Ulcère, reflux."),
        "omeprazole"        to DosageInfo(0.0,   40.0,   1,  "À jeun."),
        "mopral"            to DosageInfo(0.0,   40.0,   1,  "À jeun."),
        "pantoprazole"      to DosageInfo(0.0,   80.0,   1,  "1 fois/jour à jeun. IPP."),
        "ésoméprazole"      to DosageInfo(0.0,   40.0,   1,  "1 fois/jour à jeun. IPP."),
        "esomeprazole"      to DosageInfo(0.0,   40.0,   1,  "À jeun."),
        "nexium"            to DosageInfo(0.0,   40.0,   1,  "À jeun."),
        "ranitidine"        to DosageInfo(2.0,   300.0,  2,  "Matin et soir. Anti-H2. Ulcère, reflux."),
        "cimétidine"        to DosageInfo(5.0,   800.0,  2,  "Matin et soir. Anti-H2."),
        "cimetidine"        to DosageInfo(5.0,   800.0,  2,  "Matin et soir."),
        "métoclopramide"    to DosageInfo(0.1,   30.0,   3,  "30min avant repas. Antiémétique. Éviter > 5j (dyskinésie)."),
        "metoclopramide"    to DosageInfo(0.1,   30.0,   3,  "30min avant repas. Éviter > 5 jours."),
        "primperan"         to DosageInfo(0.1,   30.0,   3,  "30min avant repas."),
        "dompéridone"       to DosageInfo(0.25,  30.0,   3,  "Avant repas. Antiémétique. Éviter > 7j."),
        "domperidone"       to DosageInfo(0.25,  30.0,   3,  "Avant repas."),
        "motilium"          to DosageInfo(0.25,  30.0,   3,  "Avant repas."),
        "ondansétron"       to DosageInfo(0.1,   24.0,   3,  "Toutes les 8h. Antiémétique puissant. Post-chimio, postop."),
        "ondansetron"       to DosageInfo(0.1,   24.0,   3,  "Toutes les 8h."),
        "zophren"           to DosageInfo(0.1,   24.0,   3,  "Toutes les 8h."),
        "lopéramide"        to DosageInfo(0.0,   16.0,   4,  "2mg après chaque selle liquide. Max 16mg/j adulte."),
        "loperamide"        to DosageInfo(0.0,   16.0,   4,  "2mg après chaque selle liquide."),
        "imodium"           to DosageInfo(0.0,   16.0,   4,  "2mg après selle liquide."),
        "bismuth"           to DosageInfo(0.0,   2400.0, 4,  "Gastrite, Helicobacter. Noircit les selles."),
        "sro"               to DosageInfo(0.0,   0.0,    5,  "Réhydratation orale : 50-100ml/kg en 4h (déshydratation légère)."),
        "ors"               to DosageInfo(0.0,   0.0,    5,  "OMS : 50-100ml/kg/4h. Diarrhée aiguë."),
        "zinc"              to DosageInfo(0.0,   20.0,   1,  "< 6 mois : 10mg/j. ≥ 6 mois : 20mg/j. 10-14 jours. OMS."),
        "cholestyramine"    to DosageInfo(0.0,   24000.0,3,  "3x/j avant repas. Résine. Diarrhée chronique, prurit cholestatique."),
        "lactulose"         to DosageInfo(0.0,   60.0,   2,  "Matin et soir. Laxatif. Encéphalopathie hépatique."),
        "duphalac"          to DosageInfo(0.0,   60.0,   2,  "Matin et soir. Constipation, encéphalopathie."),

        // ── NEUROLOGIE / PSYCHIATRIE ──────────────────────────────

        "phénobarbital"     to DosageInfo(3.0,   200.0,  2,  "Matin et soir. Épilepsie. Sédatif. Ne pas arrêter brutalement."),
        "phenobarbital"     to DosageInfo(3.0,   200.0,  2,  "Matin et soir. Épilepsie."),
        "gardenal"          to DosageInfo(3.0,   200.0,  2,  "Matin et soir. Épilepsie."),
        "phénytoïne"        to DosageInfo(5.0,   400.0,  2,  "Matin et soir. Épilepsie. Marge étroite. Surveiller taux."),
        "phenytoine"        to DosageInfo(5.0,   400.0,  2,  "Matin et soir. Surveiller taux sanguin."),
        "dilantin"          to DosageInfo(5.0,   400.0,  2,  "Matin et soir."),
        "carbamazépine"     to DosageInfo(5.0,   1200.0, 3,  "Toutes les 8h. Épilepsie, névralgie. Surveiller NFS."),
        "carbamazepine"     to DosageInfo(5.0,   1200.0, 3,  "Toutes les 8h."),
        "tegretol"          to DosageInfo(5.0,   1200.0, 3,  "Toutes les 8h."),
        "valproate"         to DosageInfo(20.0,  2500.0, 2,  "Matin et soir. Épilepsie. Tératogène. Contraception obligatoire."),
        "acide valproique"  to DosageInfo(20.0,  2500.0, 2,  "Matin et soir. Surveiller NH3 et bilan hépatique."),
        "dépakine"          to DosageInfo(20.0,  2500.0, 2,  "Matin et soir. Contre-indiqué grossesse."),
        "lamotrigine"       to DosageInfo(5.0,   400.0,  2,  "Matin et soir. Épilepsie. Augmenter très progressivement."),
        "lamictal"          to DosageInfo(5.0,   400.0,  2,  "Augmenter progressivement."),
        "lévétiracétam"     to DosageInfo(20.0,  3000.0, 2,  "Matin et soir. Épilepsie. Bien toléré."),
        "levetiracetam"     to DosageInfo(20.0,  3000.0, 2,  "Matin et soir."),
        "keppra"            to DosageInfo(20.0,  3000.0, 2,  "Matin et soir."),
        "diazépam"          to DosageInfo(0.3,   30.0,   3,  "Convulsions fébriles : 0,5mg/kg IR. Max 10mg. Usage ponctuel."),
        "diazepam"          to DosageInfo(0.3,   30.0,   3,  "Convulsions : voie rectale de préférence."),
        "valium"            to DosageInfo(0.3,   30.0,   3,  "Dépendance. Usage limité."),
        "lorazépam"         to DosageInfo(0.05,  4.0,    3,  "État de mal épileptique : 0,1mg/kg IV lent. Hospitalier."),
        "lorazepam"         to DosageInfo(0.05,  4.0,    3,  "État de mal épileptique. IV lent."),
        "clonazépam"        to DosageInfo(0.05,  20.0,   2,  "Matin et soir. Épilepsie. Dépendance. Ne pas arrêter brutalement."),
        "clonazepam"        to DosageInfo(0.05,  20.0,   2,  "Matin et soir."),
        "rivotril"          to DosageInfo(0.05,  20.0,   2,  "Matin et soir."),
        "halopéridol"       to DosageInfo(0.05,  20.0,   3,  "Toutes les 8h. Antipsychotique. Effets extrapyramidaux."),
        "haloperidol"       to DosageInfo(0.05,  20.0,   3,  "Toutes les 8h. Surveiller effets extrapyramidaux."),
        "haldol"            to DosageInfo(0.05,  20.0,   3,  "Toutes les 8h."),
        "chlorpromazine"    to DosageInfo(1.0,   600.0,  3,  "Toutes les 8h. Psychose. Photosensibilisant."),
        "largactil"         to DosageInfo(1.0,   600.0,  3,  "Toutes les 8h."),
        "olanzapine"        to DosageInfo(0.0,   20.0,   1,  "1 fois/jour le soir. Antipsychotique atypique."),
        "zyprexa"           to DosageInfo(0.0,   20.0,   1,  "Le soir."),
        "risperidone"       to DosageInfo(0.0,   16.0,   2,  "Matin et soir. Antipsychotique atypique."),
        "risperdal"         to DosageInfo(0.0,   16.0,   2,  "Matin et soir."),
        "amitriptyline"     to DosageInfo(0.0,   150.0,  3,  "Toutes les 8h ou dose unique le soir. Antidépresseur tricyclique."),
        "laroxyl"           to DosageInfo(0.0,   150.0,  3,  "Toutes les 8h ou au coucher."),
        "fluoxétine"        to DosageInfo(0.0,   80.0,   1,  "1 fois/jour. ISRS. Dépression."),
        "fluoxetine"        to DosageInfo(0.0,   80.0,   1,  "1 fois/jour."),
        "prozac"            to DosageInfo(0.0,   80.0,   1,  "1 fois/jour."),
        "sertraline"        to DosageInfo(0.0,   200.0,  1,  "1 fois/jour. ISRS. Dépression, anxiété."),
        "zoloft"            to DosageInfo(0.0,   200.0,  1,  "1 fois/jour."),
        "paroxétine"        to DosageInfo(0.0,   50.0,   1,  "1 fois/jour le matin. ISRS."),
        "paroxetine"        to DosageInfo(0.0,   50.0,   1,  "Le matin."),
        "deroxat"           to DosageInfo(0.0,   50.0,   1,  "Le matin."),
        "citalopram"        to DosageInfo(0.0,   40.0,   1,  "1 fois/jour. ISRS."),
        "escitalopram"      to DosageInfo(0.0,   20.0,   1,  "1 fois/jour. ISRS."),
        "lexapro"           to DosageInfo(0.0,   20.0,   1,  "1 fois/jour."),
        "lithium"           to DosageInfo(0.0,   1800.0, 3,  "Toutes les 8h. Marge thérapeutique étroite. Dosage litthiémie."),
        "carbolithium"      to DosageInfo(0.0,   1800.0, 3,  "Toutes les 8h. Surveiller litthiémie."),

        // ── ANTIHISTAMINIQUES ─────────────────────────────────────

        "loratadine"        to DosageInfo(0.0,   10.0,   1,  "1 fois/jour. Non sédatif. Rhinite, urticaire."),
        "clarityne"         to DosageInfo(0.0,   10.0,   1,  "1 fois/jour."),
        "cétirizine"        to DosageInfo(0.0,   10.0,   1,  "1 fois/jour. Légèrement sédatif. Rhinite."),
        "cetirizine"        to DosageInfo(0.0,   10.0,   1,  "1 fois/jour."),
        "zyrtec"            to DosageInfo(0.0,   10.0,   1,  "1 fois/jour."),
        "féxofénadine"      to DosageInfo(0.0,   180.0,  1,  "1 fois/jour. Non sédatif."),
        "fexofenadine"      to DosageInfo(0.0,   180.0,  1,  "1 fois/jour."),
        "telfast"           to DosageInfo(0.0,   180.0,  1,  "1 fois/jour."),
        "desloratadine"     to DosageInfo(0.0,   5.0,    1,  "1 fois/jour. Non sédatif."),
        "aérius"            to DosageInfo(0.0,   5.0,    1,  "1 fois/jour."),
        "chlorphéniramine"  to DosageInfo(0.1,   24.0,   4,  "Toutes les 6h. Sédatif. Allergie, urticaire."),
        "polaramine"        to DosageInfo(0.1,   24.0,   4,  "Toutes les 6h. Sédatif."),
        "dexchlorphéniramine" to DosageInfo(0.1, 12.0,   3,  "Toutes les 8h. Sédatif."),
        "prométhazine"      to DosageInfo(0.5,   50.0,   1,  "Au coucher. Sédatif puissant. Éviter < 2 ans."),
        "phenergan"         to DosageInfo(0.5,   50.0,   1,  "Au coucher. Éviter < 2 ans."),
        "hydroxyzine"       to DosageInfo(1.0,   100.0,  3,  "Toutes les 8h ou nocturne. Anxiété, prurit. Sédatif."),
        "atarax"            to DosageInfo(1.0,   100.0,  3,  "Toutes les 8h. Sédatif."),

        // ── VITAMINES ET SUPPLÉMENTS ───────────────────────────────

        "vitamine-c"        to DosageInfo(0.0,   1000.0, 1,  "1 fois/jour. Supplémentation. Scorbut : 100-300mg/j."),
        "vitamine-a"        to DosageInfo(0.0,   200000.0, 1, "Supplémentation : 100 000 UI < 1 an, 200 000 UI > 1 an. Tous les 6 mois."),
        "vitamine-d"        to DosageInfo(0.0,   4000.0, 1,  "1 fois/jour. Rachitisme : 1000-2000 UI/j."),
        "vitamine-k"        to DosageInfo(0.0,   20.0,   1,  "Nourrisson : 2mg dose unique à la naissance. Adulte : 10mg si AVK."),
        "acide-folique"     to DosageInfo(0.0,   5000.0, 1,  "Grossesse : 400 mcg/j. Carence : 5mg/j."),
        "acide folique"     to DosageInfo(0.0,   5000.0, 1,  "400 mcg/j en prévention. 5mg/j en carence."),
        "fer"               to DosageInfo(3.0,   200.0,  2,  "Matin et soir à jeun si possible. Fer ferreux. Colore selles."),
        "sulfate de fer"    to DosageInfo(3.0,   200.0,  2,  "À jeun. Colore les selles en noir."),
        "fer-acide folique" to DosageInfo(2.0,   120.0,  1,  "1 fois/jour. Grossesse. OMS : 60mg Fe + 400mcg folate."),
        "zinc-supplement"   to DosageInfo(0.0,   20.0,   1,  "< 6 mois : 10mg/j. ≥ 6 mois : 20mg/j. Diarrhée OMS."),
        "pyridoxine"        to DosageInfo(0.5,   100.0,  1,  "Vit B6. Avec isoniazide TB. Nausées grossesse."),
        "thiamine"          to DosageInfo(0.0,   300.0,  3,  "Vit B1. Béribéri : 100mg 3x/j. Grossesse : 10mg/j."),
        "riboflavine"       to DosageInfo(0.0,   30.0,   2,  "Vit B2. Carence : 10-15mg/j. Matin et soir."),
        "nicotinamide"      to DosageInfo(0.0,   500.0,  3,  "Vit B3/PP. Pellagre : 100mg 3x/j."),
        "cyanocobalamine"   to DosageInfo(0.0,   1000.0, 1,  "Vit B12. Carence : 1000mcg IM/j x 7j puis mensuel."),
        "vitamine-b12"      to DosageInfo(0.0,   1000.0, 1,  "Anémie pernicieuse : IM. Carence : 1000mcg/mois."),

        // ── OBSTÉTRIQUE / GYNÉCOLOGIE ─────────────────────────────

        "ocytocine"         to DosageInfo(0.0,   0.0,    1,  "IV. Déclenchement travail, prévention hémorragie. Hospitalier."),
        "oxytocin"          to DosageInfo(0.0,   0.0,    1,  "IV. Obstétrical. Hospitalier strict."),
        "misoprostol"       to DosageInfo(0.0,   0.0,    1,  "600mcg sublinguale après accouchement. Prévention HPP."),
        "ergométrine"       to DosageInfo(0.0,   0.6,    2,  "0,2mg IM/IV après délivrance. Hémorragie post-partum."),
        "ergometrine"       to DosageInfo(0.0,   0.6,    2,  "Hémorragie post-partum. IM."),
        "magnésium-sulf"    to DosageInfo(0.0,   0.0,    1,  "Pré-éclampsie sévère : 4g IV lent puis 1g/h. Hospitalier."),
        "sulfate de magnésium" to DosageInfo(0.0, 0.0,   1,  "Pré-éclampsie, éclampsie. IV hospitalier strict."),
        "nifédipine-tocolyse" to DosageInfo(0.0, 120.0, 3,  "Tocolyse : 20mg toutes les 4-6h. Menace d'accouchement préterm."),
        "progestérone"      to DosageInfo(0.0,   400.0,  1,  "Voie vaginale. Menace fausse couche, prématurité."),
        "progesterone"      to DosageInfo(0.0,   400.0,  1,  "Vaginal de préférence."),
        "contraceptif-oestroprogestatif" to DosageInfo(0.0, 0.0, 1, "1 comprimé/j. Débuter J1 des règles. Préservatif si oubli."),
        "levonorgestrel"    to DosageInfo(0.0,   1.5,    1,  "Contraception urgence : 1,5mg dose unique < 72h. Efficacité diminue avec le temps."),
        "norlevo"           to DosageInfo(0.0,   1.5,    1,  "Dose unique < 72h. Pilule du lendemain."),
        "médroxyprogesterone" to DosageInfo(0.0, 0.0,    1,  "Injection IM 150mg tous les 3 mois. Contraception longue durée."),
        "depo-provera"      to DosageInfo(0.0,   0.0,    1,  "150mg IM tous les 3 mois."),

        // ── PÉDIATRIE SPÉCIFIQUE ──────────────────────────────────

        "caféine-apnée"     to DosageInfo(20.0,  12.0,   1,  "Charge 20mg/kg puis 5mg/kg/j. Apnée du nourrisson prématuré."),
        "surfactant"        to DosageInfo(100.0, 0.0,    1,  "100mg/kg IT. SDR du prématuré. Usage spécialisé NICU."),
        "phytoménadione"    to DosageInfo(0.0,   0.0,    1,  "Vit K1 : 2mg PO naissance, J5, J30. Prévention MHNN."),
        "érythropoïétine"   to DosageInfo(150.0, 0.0,    3,  "SC 3x/semaine. Anémie chronique (insuffisance rénale, chimio)."),
        "fer-buvable"       to DosageInfo(3.0,   120.0,  2,  "Matin et soir. Anémie ferriprive enfant. À jeun si possible."),
        "acide-folinique"   to DosageInfo(0.0,   25.0,   1,  "Antidote méthotrexate. Toxoplasmose (avec pyriméthamine)."),

        // ── SOINS D'URGENCE ───────────────────────────────────────

        "adrénaline"        to DosageInfo(0.01,  1.0,    1,  "Anaphylaxie : 0,01mg/kg IM face antéro-externe cuisse. Répéter 5min."),
        "adrenaline"        to DosageInfo(0.01,  1.0,    1,  "Anaphylaxie. IM cuisse. Peut répéter à 5min."),
        "épinéphrine"       to DosageInfo(0.01,  1.0,    1,  "Arrêt cardiaque : 1mg IV toutes les 3-5min."),
        "epinephrine"       to DosageInfo(0.01,  1.0,    1,  "Anaphylaxie, arrêt cardiaque."),
        "atropine"          to DosageInfo(0.02,  3.0,    1,  "Bradycardie : 0,02mg/kg IV. Min 0,1mg. Hospitalier."),
        "naloxone"          to DosageInfo(0.01,  2.0,    1,  "Surdosage opioïdes : 0,4-2mg IV/IM/SC. Répéter 2-3min."),
        "narcan"            to DosageInfo(0.01,  2.0,    1,  "Intoxication aux opioïdes. Répétable."),
        "flumazénil"        to DosageInfo(0.01,  1.0,    1,  "Intox benzodiazépines : 0,2mg IV, répéter jusqu'à 1mg. Hospitalier."),
        "glucose-50"        to DosageInfo(0.0,   0.0,    1,  "Hypoglycémie sévère : 1ml/kg G50 IV lent. Hospitalier."),
        "gluconate-calcium" to DosageInfo(0.0,   0.0,    1,  "Hypocalcémie, tétanie : 10ml G10% IV lent. Surveiller ECG."),
        "dextrose"          to DosageInfo(0.0,   0.0,    1,  "Hypoglycémie : D50W 1ml/kg IV. Ou G10 2,5ml/kg chez NN."),
        "charbon-actif"     to DosageInfo(1.0,   50.0,   1,  "Intoxication : 1g/kg PO dès que possible. Max 50g."),
        "charbon actif"     to DosageInfo(1.0,   50.0,   1,  "Intoxication orale. Dans les 2h. Avec eau."),
        "sérum-antivenimeux" to DosageInfo(0.0,  0.0,    1,  "Morsure serpent : 2-4 ampoules IV lent selon espèce. Hospitalier."),

        // ── OPHTALMOLOGIE ─────────────────────────────────────────

        "tétracycline-oeil" to DosageInfo(0.0,   0.0,    4,  "Pommade ophtalmique 1%. 1 application 4x/j. Trachome, conjonctivite."),
        "chloramphénicol-oeil" to DosageInfo(0.0, 0.0,   4,  "Collyres 0,5% ou pommade 1%. Conjonctivites bactériennes."),
        "ciprofloxacine-oeil" to DosageInfo(0.0, 0.0,    4,  "Collyre 0,3%. 1-2 gouttes 4x/j. Conjonctivite."),
        "povidone-iodee"    to DosageInfo(0.0,   0.0,    1,  "Collyre 5% ou solution. Prévention ophtalm neonatorum."),
        "azithromycine-oeil" to DosageInfo(0.0,  0.0,    2,  "Collyre 1,5%. 1 goutte 2x/j 3 jours. Trachome, conjonctivite."),
        "timolol"           to DosageInfo(0.0,   0.0,    2,  "Collyre 0,25-0,5%. Glaucome. Matin et soir."),
        "latanoprost"       to DosageInfo(0.0,   0.0,    1,  "Collyre 0,005%. 1 goutte/soir. Glaucome à angle ouvert."),
        "dexamethasone-oeil" to DosageInfo(0.0,  0.0,    4,  "Collyre 0,1%. Uvéite, post-chirurgie. Prescrire < 10j sans avis."),

        // ── DERMATOLOGIE ──────────────────────────────────────────

        "benzoate-benzyle"  to DosageInfo(0.0,   0.0,    1,  "Application cutanée 25%. Gale. 1 application, laisser 24h, rincer."),
        "benzoate de benzyle" to DosageInfo(0.0, 0.0,    1,  "Gale : application corps entier après bain. Renouveler J8."),
        "ivermectine-gale"  to DosageInfo(0.2,   12.0,   1,  "Gale : dose unique per os 200mcg/kg. Renouveler J15."),
        "permethrine"       to DosageInfo(0.0,   0.0,    1,  "Crème 5%. Gale : application 8-14h. Poux : shampoing."),
        "perméthrine"       to DosageInfo(0.0,   0.0,    1,  "Gale. Application corps entier 8-14h. Rincer."),
        "hexachlorophène"   to DosageInfo(0.0,   0.0,    2,  "Antiseptique cutané. 2 applications/jour en local."),
        "calamine"          to DosageInfo(0.0,   0.0,    3,  "Lotion. Prurit, varicelle, piqûres. Usage topique."),
        "zinc-pommade"      to DosageInfo(0.0,   0.0,    3,  "Pommade à l'oxyde de zinc. Érythème fessier nourrisson."),

        // ── ANTIDOULEURS EN SOINS PALLIATIFS ──────────────────────

        "fentanyl"          to DosageInfo(0.001, 0.1,    1,  "Réservé aux douleurs sévères oncologiques. Patch, IV. Titration spécialiste."),
        "oxycodone"         to DosageInfo(0.1,   0.0,    4,  "Toutes les 6h ou LP 2x/j. Douleurs sévères. Titration."),
        "buprenorphine"     to DosageInfo(0.004, 1.6,    2,  "Sublinguale matin et soir. Douleur ou substitution opioïde."),
        "buprénorphine"     to DosageInfo(0.004, 1.6,    2,  "Sublinguale matin et soir."),
        "méthadone"         to DosageInfo(0.0,   0.0,    1,  "Substitution opioïde. Dose initiale 10-30mg/j. Spécialiste."),
        "methadone"         to DosageInfo(0.0,   0.0,    1,  "Substitution opioïde. Spécialiste."),
        "ketamine"          to DosageInfo(0.5,   0.0,    1,  "IV/IM. Analgésie procédurale, anesthésie. Hospitalier."),
        "kétamine"          to DosageInfo(0.5,   0.0,    1,  "IV/IM. Anesthésie dissociative. Hospitalier."),

        // ── DIVERS / USAGE COURANT ────────────────────────────────

        "caféine-comprimés" to DosageInfo(0.0,   400.0,  3,  "Toutes les 8h. Céphalées de tension, migraine. Max 400mg/j."),
        "ergotamine"        to DosageInfo(0.0,   4.0,    1,  "Crise migraineuse : 1-2mg sublinguals. Max 4 crises/mois."),
        "sumatriptan"       to DosageInfo(0.0,   300.0,  2,  "Crise migraineuse : 50-100mg. Max 2/24h. Oral ou SC."),
        "imigrane"          to DosageInfo(0.0,   300.0,  2,  "50-100mg en crise. Max 2 par 24h."),
        "colchicine"        to DosageInfo(0.0,   3.0,    3,  "Crise de goutte : 1mg à J1 puis 0,5mg 3x/j. Diarrhée fréquente."),
        "allopurinol"       to DosageInfo(0.0,   800.0,  1,  "1 fois/jour. Goutte chronique. Débuter à distance d'une crise."),
        "zyloric"           to DosageInfo(0.0,   800.0,  1,  "1 fois/jour."),
        "chlorure de sodium" to DosageInfo(0.0,  0.0,    3,  "Sérum physiologique. NaCl 0,9%. Perfusion, lavage. Hospitalier."),
        "ringer-lactate"    to DosageInfo(0.0,   0.0,    1,  "Choc hypovolémique : 20ml/kg en 15-20min. Répéter si besoin."),
        "albumine"          to DosageInfo(0.0,   0.0,    1,  "Hypoalbuminémie sévère, choc. Hospitalier. 5% ou 20%."),
        "dexaméthasone-oedem" to DosageInfo(0.15, 12.0,  3,  "Oedème cérébral tumoral : 0,1-0,5mg/kg/j. Hospitalier."),
        "acétazolamide"     to DosageInfo(5.0,   750.0,  3,  "Toutes les 8h. Mal aigu des montagnes, glaucome."),
        "diamox"            to DosageInfo(5.0,   750.0,  3,  "Altitude sickness, glaucome. Toutes les 8h."),
        "thérapie-ors-zinc" to DosageInfo(0.0,   0.0,    5,  "OMS diarrhée : SRO + zinc 10-20mg/j x 10-14j. Combinaison obligatoire."),
        "mebendazole-adulte" to DosageInfo(0.0,  500.0,  1,  "500mg dose unique. Oxyures, ankylostomes adulte (ou enfant > 2 ans)."),
        "sel-de-réhydratation" to DosageInfo(0.0, 0.0,   5,  "1 sachet dans 1L eau bouillie refroidie. Boire librement.")
    )

    // Lookup robuste : accents + alias + correspondance partielle
        // L'IA sort parfois 'paracetamol' sans accent, 'amoxicillin' sans 'e', etc.
        // Cette map relie les variantes sans accents aux entrees existantes
        val DRUG_ALIASES: Map<String, String> = mapOf(
    // paracetamol
        "paracetamol"      to "paracétamol",
    "acetaminophen"    to "paracétamol",
    "acetaminoph"      to "paracétamol",
    // ibuprofene
    "ibuprofen"        to "ibuprofene",
    "ibuprofene"       to "ibuprofene",
    // amoxicilline
    "amoxicillin"      to "amoxicilline",
    "amoxil"           to "amoxicilline",
    // metronidazole
    "metronidazol"     to "metronidazole",
    "metronidazole"    to "metronidazole",
    "flagyl"           to "metronidazole",
    // ciprofloxacine
    "ciprofloxacin"    to "ciprofloxacine",
    "cipro"            to "ciprofloxacine",
    // artemether
    "artemether"       to "artemether",
    "coartem"          to "artemether",
    // chloroquine
    "chloroquin"       to "chloroquine",
    "nivaquine"        to "chloroquine",
    // doxycycline
    "doxycyclin"       to "doxycycline",
    "vibramycin"       to "doxycycline",
    // azithromycine
    "azithromycin"     to "azithromycine",
    "zithromax"        to "azithromycine",
    // metformine
    "metformin"        to "metformine",
    "glucophage"       to "metformine",
    // furosemide
    "furosemid"        to "furosemide",
    "lasilix"          to "furosemide",
    // prednisolone
    "prednisolon"      to "prednisolone",
    "cortancyl"        to "prednisolone",
    // dexamethasone
    "dexamethasone"    to "dexamethasone",
    // cetirizine
    "cetirizin"        to "cetirizine",
    "zyrtec"           to "cetirizine",
    // omeprazole
    "omeprazol"        to "omeprazole",
    "mopral"           to "omeprazole",
    // salbutamol
    "salbutamol"       to "salbutamol",
    "ventolin"         to "salbutamol",
    "albuterol"        to "salbutamol",
    // albendazole
    "albendazol"       to "albendazole",
    "zentel"           to "albendazole",
    // fluconazole
    "fluconazol"       to "fluconazole",
    // cotrimoxazole
    "cotrimoxazol"     to "cotrimoxazole",
    "bactrim"          to "cotrimoxazole",
    "septrin"          to "cotrimoxazole",
    // erythromycine
    "erythromycin"     to "erythromycine",
    // zinc
    "zinc"             to "zinc",
    // sro
    "ors"              to "sro",
    )

    // Fonction de lookup a utiliser PARTOUT a la place de protocols[drug]
    fun findProtocol(drug: String): DosageInfo? {
        val key = drug.lowercase().trim()
        // 1. Correspondance directe (avec ou sans accents)
        protocols[key]?.let { return it }
        // 2. Via alias (variante sans accent ou nom commercial)
        DRUG_ALIASES[key]?.let { canonicalKey ->
            protocols[canonicalKey]?.let { return it }
        }
        // 3. Correspondance partielle sur la cle (ex: 'paracetamol' trouve 'doliprane' non, mais trouve 'paracetamol')
        protocols.entries.firstOrNull { (k, _) -> k.contains(key) || key.contains(k) }?.value?.let { return it }
        // 4. Alias partiel
        DRUG_ALIASES.entries.firstOrNull { (k, _) -> k.contains(key) || key.contains(k) }?.value?.let { aliasKey ->
            protocols[aliasKey]?.let { return it }
        }
        return null
    }
}