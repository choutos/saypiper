package eu.wanderclan.wordquest

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import org.w3c.dom.Element
import java.io.File
import javax.xml.parsers.DocumentBuilderFactory

/** Asserts ADR section 7 on the merged manifest of the debug app, as Gradle builds it. */
class ManifestTest {
    private val android = "http://schemas.android.com/apk/res/android"
    private val manifest = run {
        val path = requireNotNull(System.getProperty("wordquest.mergedManifest")) { "Run through Gradle: the build passes the manifest path" }
        DocumentBuilderFactory.newInstance().apply { isNamespaceAware = true }.newDocumentBuilder().parse(File(path)).documentElement
    }

    private fun elements(tag: String): List<Element> =
        manifest.getElementsByTagName(tag).let { list -> List(list.length) { list.item(it) as Element } }

    @Test
    fun `no permissions are requested or declared`() {
        val requested = listOf("uses-permission", "uses-permission-sdk-23", "permission")
            .flatMap(::elements).map { it.getAttributeNS(android, "name") }
        assertEquals(emptyList<String>(), requested)
    }

    @Test
    fun `backup and device transfer are off`() {
        val application = elements("application").single()
        assertEquals("false", application.getAttributeNS(android, "allowBackup"))
        assertTrue(application.getAttributeNS(android, "dataExtractionRules").isNotEmpty())
    }

    @Test
    fun `the emoji2 initialiser is removed`() {
        val names = elements("meta-data").map { it.getAttributeNS(android, "name") } +
            elements("provider").map { it.getAttributeNS(android, "name") }
        assertTrue(names.none { it.startsWith("androidx.emoji2") })
    }

    @Test
    fun `backup rules exclude everything from cloud backup and device transfer`() {
        val rules = DocumentBuilderFactory.newInstance().newDocumentBuilder()
            .parse(File("src/main/res/xml/data_extraction_rules.xml")).documentElement
        for (section in listOf("cloud-backup", "device-transfer")) {
            val element = rules.getElementsByTagName(section).item(0) as Element
            assertEquals(0, element.getElementsByTagName("include").length)
            val excluded = element.getElementsByTagName("exclude").let { l -> List(l.length) { (l.item(it) as Element).getAttribute("domain") } }
            assertTrue(excluded.containsAll(listOf("root", "file", "database", "sharedpref", "external")))
        }
    }
}
