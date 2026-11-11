package eu.wanderclan.wordquest.audio

import org.junit.Assert.*
import org.junit.Test
import java.util.Locale

class VoiceChoiceTest {
    private fun voice(name: String, locale: Locale = Locale.UK, network: Boolean = false, installed: Boolean = true) =
        VoiceInfo(name, locale, network, installed)

    @Test fun `British installed offline voice wins regardless of list order`() {
        val british = voice("uk")
        assertEquals(british, chooseVoice(listOf(voice("us", Locale.US), british)))
        assertEquals(british, chooseVoice(listOf(british, voice("us", Locale.US))))
    }

    @Test fun `network and missing voices are never chosen, even if British`() {
        val american = voice("us", Locale.US)
        assertEquals(american, chooseVoice(listOf(voice("uk-network", network = true), voice("uk-missing", installed = false), american)))
        assertNull(chooseVoice(listOf(voice("network", network = true), voice("missing", installed = false))))
    }

    @Test fun `no English voice means unavailable`() {
        assertNull(chooseVoice(listOf(voice("fr", Locale.FRANCE))))
        assertNull(chooseVoice(emptyList()))
    }
}
