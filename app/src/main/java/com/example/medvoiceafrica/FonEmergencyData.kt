package com.example.medvoiceafrica

data class FonPhrase(
    val keywordsFon: List<String>,
    val keywordsFr: List<String>,
    val responseFon: String,
    val responseFr: String,
    val triageLevel: TriageLevel
)

object FonEmergencyData {

    val phrases = listOf(
        FonPhrase(
            keywordsFon = listOf("ajijimi", "ajijim", "sinsin", "dotooxwe", "tlolo"),
            keywordsFr  = listOf("urgence", "hopital", "tout de suite", "grave"),
            responseFon = "Ajijimi sɔn n syɔnsyɔn wɛ nyɛn. Mi yi dotooxwe tɛlolo.",
            responseFr  = "C'est une urgence grave. Allez à l'hôpital tout de suite.",
            triageLevel = TriageLevel.ROUGE
        ),
        FonPhrase(
            keywordsFon = listOf("zozo", "axɔ", "vɔ jɛ", "vɔjɛ", "fa"),
            keywordsFr  = listOf("fièvre", "temperature", "chaud", "brulant"),
            responseFon = "Zozo ɖaxó ɖé ɖo vǐ ɔ́ jí; a ɖo na fa'ɛ.",
            responseFr  = "L'enfant a une fièvre trop forte, il faut le refroidir. Consultez un agent de santé.",
            triageLevel = TriageLevel.ROUGE
        ),
        FonPhrase(
            keywordsFon = listOf("hun", "hùn", "hun gbɔ gbɔ"),
            keywordsFr  = listOf("saigne", "sang", "hémorragie", "perd du sang"),
            responseFon = "Enyi é ɖò hun gègě hɛn bú wɛ hǔn, ma nɔte kpɔ́n ɛ ɖò fí ó. Mi Yi dotooxwe tɛlolo.",
            responseFr  = "Si elle perd beaucoup de sang, ne l'attendez pas ici. Allez aux urgences.",
            triageLevel = TriageLevel.ROUGE
        ),
        FonPhrase(
            keywordsFon = listOf("gbigbɔ̃n", "gbigbn", "fɔ ganji", "s gbigbn"),
            keywordsFr  = listOf("respire", "respiration", "souffle", "asphyxie"),
            responseFon = "Enyi é sɔ́ ɖò gbigbɔnjɛ wɛ ganji ǎ hǔn, kplá ɛ yì dotóoxwé ajijimɛ tɔn.",
            responseFr  = "S'il ne respire plus bien, amenez-le aux urgences immédiatement.",
            triageLevel = TriageLevel.ROUGE
        ),
        FonPhrase(
            keywordsFon = listOf("ma ayi", "ko", "ɖɔ sin", "ma nyí"),
            keywordsFr  = listOf("inconscient", "évanoui", "coma", "ne réagit pas"),
            responseFon = "Enyi é ma ɖò ayi mɛ ǎ ɔ, ma na ɛ sin ɖebǔ ó. Ylo ambulance.",
            responseFr  = "S'il est inconscient, ne rien donner à boire. Appelez une ambulance.",
            triageLevel = TriageLevel.ROUGE
        ),
        FonPhrase(
            keywordsFon = listOf("jɔmji", "jmji", "convulsion"),
            keywordsFr  = listOf("convulsion", "crise", "tremble", "secousse"),
            responseFon = "Enyi é ɖó jɛ jɛmɛji (convulsions) ɔ, cyɔn alɔ ta tɔn jí. Yi dotooxwe.",
            responseFr  = "S'il fait des convulsions, protégez sa tête. Allez à l'hôpital.",
            triageLevel = TriageLevel.ROUGE
        ),
        FonPhrase(
            keywordsFon = listOf("ambulance", "mɔ̀tɔ", "yla"),
            keywordsFr  = listOf("ambulance", "voiture", "transport"),
            responseFon = "Ylɔ ambulance alǒ ba mɔ̌to dìn.",
            responseFr  = "Appelez l'ambulance ou trouvez une voiture maintenant.",
            triageLevel = TriageLevel.ROUGE
        ),
        FonPhrase(
            keywordsFon = listOf("akplɔ́", "akpl", "kwijikwiji"),
            keywordsFr  = listOf("plaie", "blessure", "mains sales"),
            responseFon = "Mi ma ɖ’alɔ akpà lɛ wu kpo alɔ kwijikwiji kpo ó.",
            responseFr  = "Ne touchez pas les plaies avec des mains sales.",
            triageLevel = TriageLevel.JAUNE
        ),
        FonPhrase(
            keywordsFon = listOf("kpɔn ayi", "kpn ayi", "gbɔ̃n"),
            keywordsFr  = listOf("surveiller", "cœur", "respiration", "pouls"),
            responseFon = "Kpɔ́n ayi tɔn kpo lee é nɔ gbɔjɛ gbɔn é kpo ɖò cɛju ɖokpo ɖokpo mɛ.",
            responseFr  = "Surveillez son cœur et sa respiration chaque minute.",
            triageLevel = TriageLevel.JAUNE
        )
    )

    fun detect(userMessage: String): FonPhrase? {
        val msg = userMessage.lowercase().trim()
        return phrases.firstOrNull { phrase ->
            phrase.keywordsFon.any { msg.contains(it.lowercase()) }
        }
    }

    fun isFonMessage(text: String): Boolean {
        val msg = text.lowercase()
        val fonChars = listOf('ɖ', 'ɔ', 'ɛ', 'ɣ', 'ŋ')
        val hasFonChars = fonChars.any { msg.contains(it) }
        val fonKeywords = phrases.flatMap { it.keywordsFon }
        return hasFonChars || fonKeywords.any { msg.contains(it.lowercase()) }
    }
}