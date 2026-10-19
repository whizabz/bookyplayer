package xyz.saltedchips.bookyplayer.tts

/** A Kokoro v1.0 speaker. [sid] is the speaker id the engine expects. */
enum class VoiceAccent(val label: String) { US("US"), UK("UK") }

enum class VoiceGender(val label: String) { Female("Female"), Male("Male") }

data class KokoroVoice(val sid: Int, val name: String, val label: String) {
    val accent: VoiceAccent get() = if (name.startsWith("a")) VoiceAccent.US else VoiceAccent.UK
    val gender: VoiceGender get() = if (name.getOrNull(1) == 'f') VoiceGender.Female else VoiceGender.Male
    val displayName: String get() = label.substringBefore(" ·")
}

/** The US and UK English voices of kokoro-multi-lang-v1_0 (speaker ids 0-27). */
object KokoroVoices {
    val english: List<KokoroVoice> = listOf(
        KokoroVoice(3, "af_heart", "Heart · US, female"),
        KokoroVoice(2, "af_bella", "Bella · US, female"),
        KokoroVoice(9, "af_sarah", "Sarah · US, female"),
        KokoroVoice(6, "af_nicole", "Nicole · US, female"),
        KokoroVoice(7, "af_nova", "Nova · US, female"),
        KokoroVoice(0, "af_alloy", "Alloy · US, female"),
        KokoroVoice(1, "af_aoede", "Aoede · US, female"),
        KokoroVoice(4, "af_jessica", "Jessica · US, female"),
        KokoroVoice(5, "af_kore", "Kore · US, female"),
        KokoroVoice(8, "af_river", "River · US, female"),
        KokoroVoice(10, "af_sky", "Sky · US, female"),
        KokoroVoice(16, "am_michael", "Michael · US, male"),
        KokoroVoice(11, "am_adam", "Adam · US, male"),
        KokoroVoice(12, "am_echo", "Echo · US, male"),
        KokoroVoice(13, "am_eric", "Eric · US, male"),
        KokoroVoice(14, "am_fenrir", "Fenrir · US, male"),
        KokoroVoice(15, "am_liam", "Liam · US, male"),
        KokoroVoice(17, "am_onyx", "Onyx · US, male"),
        KokoroVoice(18, "am_puck", "Puck · US, male"),
        KokoroVoice(19, "am_santa", "Santa · US, male"),
        KokoroVoice(21, "bf_emma", "Emma · UK, female"),
        KokoroVoice(20, "bf_alice", "Alice · UK, female"),
        KokoroVoice(22, "bf_isabella", "Isabella · UK, female"),
        KokoroVoice(23, "bf_lily", "Lily · UK, female"),
        KokoroVoice(26, "bm_george", "George · UK, male"),
        KokoroVoice(24, "bm_daniel", "Daniel · UK, male"),
        KokoroVoice(25, "bm_fable", "Fable · UK, male"),
        KokoroVoice(27, "bm_lewis", "Lewis · UK, male"),
    )

    val default: KokoroVoice = english.first()

    fun bySid(sid: Int): KokoroVoice = english.firstOrNull { it.sid == sid } ?: default
}
