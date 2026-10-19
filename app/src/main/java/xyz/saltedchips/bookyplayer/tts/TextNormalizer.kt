package xyz.saltedchips.bookyplayer.tts

/**
 * Spells out abbreviations before speech, so "St. Bernard" is read as "Saint Bernard" and the
 * period isn't mistaken for the end of a sentence.
 */
object TextNormalizer {
    private data class Rule(val pattern: Regex, val replacement: String)

    private val rules: List<Rule> = listOf(
        // "St." before a capitalised name is Saint; after a name it is Street.
        Rule(Regex("""\bSt\.(?=\s+[A-Z])"""), "Saint"),
        Rule(Regex("""(?<=[A-Z][a-z]{1,20}\s)St\.(?!\s+[A-Z])"""), "Street"),
        Rule(Regex("""\bMr\."""), "Mister"),
        Rule(Regex("""\bMrs\."""), "Missus"),
        Rule(Regex("""\bMs\."""), "Miss"),
        Rule(Regex("""\bDr\.(?=\s+[A-Z])"""), "Doctor"),
        Rule(Regex("""\bProf\.(?=\s+[A-Z])"""), "Professor"),
        Rule(Regex("""\bCapt\.(?=\s+[A-Z])"""), "Captain"),
        Rule(Regex("""\bLt\.(?=\s+[A-Z])"""), "Lieutenant"),
        Rule(Regex("""\bGen\.(?=\s+[A-Z])"""), "General"),
        Rule(Regex("""\bSgt\.(?=\s+[A-Z])"""), "Sergeant"),
        Rule(Regex("""\bRev\.(?=\s+[A-Z])"""), "Reverend"),
        Rule(Regex("""\bMt\.(?=\s+[A-Z])"""), "Mount"),
        Rule(Regex("""\bSr\.(?=\s|,|$)"""), "Senior"),
        Rule(Regex("""\bJr\.(?=\s|,|$)"""), "Junior"),
        Rule(Regex("""\bvs\.""", RegexOption.IGNORE_CASE), "versus"),
        Rule(Regex("""\be\.g\.""", RegexOption.IGNORE_CASE), "for example"),
        Rule(Regex("""\bi\.e\.""", RegexOption.IGNORE_CASE), "that is"),
        Rule(Regex("""\betc\.(?=\s+[A-Z]|\s*$)"""), "et cetera."),
        Rule(Regex("""\betc\."""), "et cetera"),
    )

    private val timeAtSentenceEnd = Regex("""\b([AaPp])\.[Mm]\.(?=\s+[A-Z]|\s*$)""")
    private val timeMidSentence = Regex("""\b([AaPp])\.[Mm]\.""")

    fun normalize(text: String): String {
        var result = text
        for (rule in rules) result = rule.pattern.replace(result, rule.replacement)
        // "a.m." and "p.m." become "AM" and "PM", keeping a sentence-ending period.
        result = timeAtSentenceEnd.replace(result) { it.groupValues[1].uppercase() + "M." }
        return timeMidSentence.replace(result) { it.groupValues[1].uppercase() + "M" }
    }
}
