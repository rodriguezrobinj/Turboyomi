package mihon.desktop.app.settings

import java.util.Locale

/**
 * The app's own language (spec 11d §2). moko resolves every string with Locale.getDefault(), so choosing a language
 * is setting it; an empty tag goes back to Windows' language, read before anything changed it.
 */
object AppLanguage {
    private val system: Locale = Locale.getDefault()

    /** English (the base strings) and the translations in i18n/src/commonMain/moko-resources, as BCP 47 tags. */
    val TAGS = listOf(
        "en", "am", "ar", "as", "az", "be", "bg", "bn", "ca", "ceb", "cs", "cv", "da", "de", "el", "eo", "es", "eu",
        "fa", "fi", "fil", "fr", "gl", "he", "hi", "hr", "hu", "in", "it", "ja", "jv", "ka-GE", "kk", "km", "kmr", "kn",
        "ko", "lt", "lv", "ml", "mr", "ms", "my", "nb-NO", "ne", "nl", "nn", "pl", "pt", "pt-BR", "ro", "ru", "sa",
        "sah", "sc", "sdh", "sk", "sq", "sr", "sv", "ta", "te", "th", "tr", "tt", "uk", "uz", "vi", "zh-CN", "zh-TW",
    )

    fun apply(tag: String) {
        Locale.setDefault(if (tag.isEmpty()) system else Locale.forLanguageTag(tag))
    }
}
