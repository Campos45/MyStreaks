package pt.ipt.mystreaks

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import pt.ipt.mystreaks.utils.AppPreferences

class AppPreferencesTest {

    @Test
    fun supportedLanguages_containsAllSixRequestedLanguages() {
        val codes = AppPreferences.SUPPORTED_LANGUAGES.map { it.code }.toSet()
        assertEquals(6, codes.size)
        assertTrue(codes.contains("pt"))
        assertTrue(codes.contains("en"))
        assertTrue(codes.contains("es"))
        assertTrue(codes.contains("it"))
        assertTrue(codes.contains("de"))
        assertTrue(codes.contains("tr"))
    }

    @Test
    fun getLanguageDisplayName_returnsExpectedLabelsAndFallback() {
        assertEquals("🇵🇹 Português", AppPreferences.getLanguageDisplayName("pt"))
        assertEquals("🇬🇧 English", AppPreferences.getLanguageDisplayName("en"))
        assertEquals("🇪🇸 Español", AppPreferences.getLanguageDisplayName("es"))
        assertEquals("🇮🇹 Italiano", AppPreferences.getLanguageDisplayName("it"))
        assertEquals("🇩🇪 Deutsch", AppPreferences.getLanguageDisplayName("de"))
        assertEquals("🇹🇷 Türkçe", AppPreferences.getLanguageDisplayName("tr"))
        assertEquals("🇵🇹 Português", AppPreferences.getLanguageDisplayName("unknown"))
    }
}
