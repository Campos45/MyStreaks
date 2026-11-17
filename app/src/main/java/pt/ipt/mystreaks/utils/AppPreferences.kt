package pt.ipt.mystreaks.utils

import android.content.Context
import androidx.appcompat.app.AppCompatDelegate
import androidx.core.os.LocaleListCompat

object AppPreferences {
    private const val PREFS_NAME = "mystreaks_prefs"
    private const val KEY_DARK_MODE = "dark_mode"
    private const val KEY_LANGUAGE = "app_language"
    private const val KEY_DIARY_REMINDER_HOUR = "diary_reminder_hour"
    private const val KEY_DIARY_REMINDER_MINUTE = "diary_reminder_minute"

    val SUPPORTED_LANGUAGES = listOf(
        LanguageOption("pt", "🇵🇹 Português"),
        LanguageOption("en", "🇬🇧 English"),
        LanguageOption("es", "🇪🇸 Español"),
        LanguageOption("it", "🇮🇹 Italiano"),
        LanguageOption("de", "🇩🇪 Deutsch"),
        LanguageOption("tr", "🇹🇷 Türkçe")
    )

    data class LanguageOption(val code: String, val displayName: String)

    fun isDarkMode(context: Context): Boolean {
        val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        return prefs.getBoolean(KEY_DARK_MODE, false)
    }

    fun setDarkMode(context: Context, enabled: Boolean) {
        context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
            .edit()
            .putBoolean(KEY_DARK_MODE, enabled)
            .apply()
        applyDarkMode(enabled)
    }

    fun applyDarkMode(enabled: Boolean) {
        AppCompatDelegate.setDefaultNightMode(
            if (enabled) AppCompatDelegate.MODE_NIGHT_YES else AppCompatDelegate.MODE_NIGHT_NO
        )
    }

    fun getLanguage(context: Context): String {
        val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        return prefs.getString(KEY_LANGUAGE, "pt") ?: "pt"
    }

    fun setLanguage(context: Context, languageCode: String) {
        val validCode = if (SUPPORTED_LANGUAGES.any { it.code == languageCode }) languageCode else "pt"
        context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
            .edit()
            .putString(KEY_LANGUAGE, validCode)
            .apply()
        applyLanguage(validCode)
    }

    fun applyLanguage(languageCode: String) {
        val currentLocales = AppCompatDelegate.getApplicationLocales()
        val desired = LocaleListCompat.forLanguageTags(languageCode)
        if (currentLocales.toLanguageTags() != desired.toLanguageTags()) {
            AppCompatDelegate.setApplicationLocales(desired)
        }
    }

    fun getLanguageDisplayName(code: String): String {
        return SUPPORTED_LANGUAGES.find { it.code == code }?.displayName ?: "🇵🇹 Português"
    }

    fun getDiaryReminderHour(context: Context): Int {
        return context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
            .getInt(KEY_DIARY_REMINDER_HOUR, 8)
    }

    fun getDiaryReminderMinute(context: Context): Int {
        return context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
            .getInt(KEY_DIARY_REMINDER_MINUTE, 30)
    }

    fun setDiaryReminderTime(context: Context, hour: Int, minute: Int) {
        context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
            .edit()
            .putInt(KEY_DIARY_REMINDER_HOUR, hour)
            .putInt(KEY_DIARY_REMINDER_MINUTE, minute)
            .apply()
    }
}
