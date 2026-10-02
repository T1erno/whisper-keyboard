package com.t1erno.whisperkeyboard.network

data class LanguageItem(
    val code: String,
    val name: String
) {
    val displayName: String = if (code == "auto") "$name ($code)" else "$name ($code)"
    override fun toString(): String = displayName
}

object WhisperLanguages {

    val AUTO = LanguageItem("auto", "Auto-detect")

    val ALL_LANGUAGES: List<LanguageItem> = listOf(
        AUTO,
        LanguageItem("es", "Spanish"),
        LanguageItem("en", "English"),
        LanguageItem("fr", "French"),
        LanguageItem("de", "German"),
        LanguageItem("it", "Italian"),
        LanguageItem("pt", "Portuguese"),
        LanguageItem("ja", "Japanese"),
        LanguageItem("zh", "Chinese"),
        LanguageItem("ru", "Russian"),
        LanguageItem("ar", "Arabic"),
        LanguageItem("ko", "Korean"),
        LanguageItem("af", "Afrikaans"),
        LanguageItem("sq", "Albanian"),
        LanguageItem("am", "Amharic"),
        LanguageItem("hy", "Armenian"),
        LanguageItem("as", "Assamese"),
        LanguageItem("az", "Azerbaijani"),
        LanguageItem("ba", "Bashkir"),
        LanguageItem("eu", "Basque"),
        LanguageItem("be", "Belarusian"),
        LanguageItem("bn", "Bengali"),
        LanguageItem("bs", "Bosnian"),
        LanguageItem("br", "Breton"),
        LanguageItem("bg", "Bulgarian"),
        LanguageItem("my", "Burmese"),
        LanguageItem("ca", "Catalan"),
        LanguageItem("hr", "Croatian"),
        LanguageItem("cs", "Czech"),
        LanguageItem("da", "Danish"),
        LanguageItem("nl", "Dutch"),
        LanguageItem("et", "Estonian"),
        LanguageItem("fo", "Faroese"),
        LanguageItem("fi", "Finnish"),
        LanguageItem("gl", "Galician"),
        LanguageItem("ka", "Georgian"),
        LanguageItem("el", "Greek"),
        LanguageItem("gu", "Gujarati"),
        LanguageItem("ht", "Haitian Creole"),
        LanguageItem("ha", "Hausa"),
        LanguageItem("haw", "Hawaiian"),
        LanguageItem("he", "Hebrew"),
        LanguageItem("hi", "Hindi"),
        LanguageItem("hu", "Hungarian"),
        LanguageItem("is", "Icelandic"),
        LanguageItem("id", "Indonesian"),
        LanguageItem("jw", "Javanese"),
        LanguageItem("kn", "Kannada"),
        LanguageItem("kk", "Kazakh"),
        LanguageItem("km", "Khmer"),
        LanguageItem("lo", "Lao"),
        LanguageItem("la", "Latin"),
        LanguageItem("lv", "Latvian"),
        LanguageItem("ln", "Lingala"),
        LanguageItem("lt", "Lithuanian"),
        LanguageItem("lb", "Luxembourgish"),
        LanguageItem("mk", "Macedonian"),
        LanguageItem("mg", "Malagasy"),
        LanguageItem("ms", "Malay"),
        LanguageItem("ml", "Malayalam"),
        LanguageItem("mt", "Maltese"),
        LanguageItem("mi", "Maori"),
        LanguageItem("mr", "Marathi"),
        LanguageItem("mn", "Mongolian"),
        LanguageItem("ne", "Nepali"),
        LanguageItem("no", "Norwegian"),
        LanguageItem("nn", "Norwegian Nynorsk"),
        LanguageItem("oc", "Occitan"),
        LanguageItem("pa", "Punjabi"),
        LanguageItem("ps", "Pashto"),
        LanguageItem("fa", "Persian"),
        LanguageItem("pl", "Polish"),
        LanguageItem("ro", "Romanian"),
        LanguageItem("sa", "Sanskrit"),
        LanguageItem("sr", "Serbian"),
        LanguageItem("sn", "Shona"),
        LanguageItem("sd", "Sindhi"),
        LanguageItem("si", "Sinhala"),
        LanguageItem("sk", "Slovak"),
        LanguageItem("sl", "Slovenian"),
        LanguageItem("so", "Somali"),
        LanguageItem("su", "Sundanese"),
        LanguageItem("sw", "Swahili"),
        LanguageItem("sv", "Swedish"),
        LanguageItem("tl", "Tagalog"),
        LanguageItem("tg", "Tajik"),
        LanguageItem("ta", "Tamil"),
        LanguageItem("tt", "Tatar"),
        LanguageItem("te", "Telugu"),
        LanguageItem("th", "Thai"),
        LanguageItem("bo", "Tibetan"),
        LanguageItem("tr", "Turkish"),
        LanguageItem("tk", "Turkmen"),
        LanguageItem("uk", "Ukrainian"),
        LanguageItem("ur", "Urdu"),
        LanguageItem("uz", "Uzbek"),
        LanguageItem("vi", "Vietnamese"),
        LanguageItem("cy", "Welsh"),
        LanguageItem("yi", "Yiddish"),
        LanguageItem("yo", "Yoruba")
    )

    fun findByCode(code: String): LanguageItem? {
        val trimmed = code.trim().lowercase()
        return ALL_LANGUAGES.firstOrNull { it.code.lowercase() == trimmed }
    }

    fun findByDisplayName(displayName: String): LanguageItem? {
        val trimmed = displayName.trim().lowercase()
        return ALL_LANGUAGES.firstOrNull { it.displayName.lowercase() == trimmed }
    }
}
