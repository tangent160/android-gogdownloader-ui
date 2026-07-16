package io.github.tangent160.gogdownloader.core

/**
 * Maps values from gog-downloader's database to the values its `download`
 * command accepts for --os / --language. The CLI validates these strictly
 * (Enum/OperatingSystem.php, Enum/Language.php) and aborts the whole run on
 * an unknown value, so anything unmappable must simply not be passed.
 */
object GogCliFilters {

    private val operatingSystems = setOf("windows", "mac", "linux")

    /** code -> local name, mirroring vendor/GogDownloader/src/Enum/Language.php */
    private val languages = mapOf(
        "en" to "English",
        "bl" to "български",
        "ru" to "русский",
        "ar" to "العربية",
        "br" to "Português do Brasil",
        "jp" to "日本語",
        "ko" to "한국어",
        "fr" to "français",
        "cn" to "中文(简体)",
        "cz" to "český",
        "hu" to "magyar",
        "pt" to "português",
        "tr" to "Türkçe",
        "nl" to "nederlands",
        "ro" to "română",
        "es" to "español",
        "pl" to "polski",
        "it" to "italiano",
        "de" to "Deutsch",
        "da" to "Dansk",
        "sv" to "svenska",
        "fi" to "suomi",
        "no" to "norsk",
        "es_mx" to "Español (AL)",
        "is" to "Íslenska",
        "uk" to "yкраїнська",
        "th" to "ไทย",
        "zh" to "中文(繁體)",
    )

    private val localNameToCode = languages.entries.associate { (code, name) -> name to code }

    fun osArgOrNull(platform: String): String? =
        platform.takeIf { it in operatingSystems }

    /** Accepts either a language code or a local name as stored in the DB. */
    fun languageArgOrNull(language: String): String? =
        if (language in languages) language else localNameToCode[language]
}
