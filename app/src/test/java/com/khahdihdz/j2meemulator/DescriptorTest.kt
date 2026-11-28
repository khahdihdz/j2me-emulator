package com.khahdihdz.j2meemulator

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import ru.woesss.j2me.jar.Descriptor
import ru.woesss.j2me.jar.DescriptorException

/**
 * Phase 7 — unit tests for the engine's JAD/manifest parser
 * (`ru.woesss.j2me.jar.Descriptor` at upstream tag 1.8.2), the exact
 * layer `AppInstaller` uses to decide whether an imported file is a
 * game at all. These run on the JVM (`testFdroidDebugUnitTest`).
 *
 * Why this works without a device: `Descriptor` is plain Java —
 * its constructor only parses text and verifies the required
 * attributes. It touches Android (`android.util.Log`, only in the
 * error path of `init`) solely when parsing fails, and the merged
 * build sets `testOptions.unitTests.returnDefaultValues = true`
 * (Phase 7, `tools/merge-engine.py`), so those stub calls become
 * no-ops instead of throwing "Stub!". The tests below assert the
 * real `DescriptorException` on bad input, which is exactly what
 * that setting enables. `getInfo(Context)` is NOT tested here — it
 * needs a real `Context` and Android resources.
 *
 * Coverage vs the prompt: JAD parsing, JAD→JAR URL, missing
 * MIDlet-Name/Vendor/Version, missing/non-integer Jar-Size, missing
 * Jar-URL, corrupt input, duplicate detection (`equals` on
 * Name+Vendor, the rule `AppInstaller` uses), version comparison
 * (the rule behind reinstall-newer/older).
 */
class DescriptorTest {

    private val validJad = """
        MIDlet-Name: Snake
        MIDlet-Vendor: Nokia
        MIDlet-Version: 1.0
        MIDlet-Jar-URL: Snake.jar
        MIDlet-Jar-Size: 12345
        """.trimIndent()

    private val validManifest = """
        Manifest-Version: 1.0
        MIDlet-Name: Snake
        MIDlet-Vendor: Nokia
        MIDlet-Version: 1.0
        MIDlet-1: Snake, /icon.png, SnakeMIDlet
        """.trimIndent()

    @get:Rule
    val tempFolder = TemporaryFolder()

    // Valid input

    @Test
    fun `valid JAD parses`() {
        val d = Descriptor(validJad, true)
        assertEquals("Snake", d.name)
        assertEquals("Nokia", d.vendor)
        assertEquals("1.0", d.version)
        assertEquals("Snake.jar", d.jarUrl)
    }

    @Test
    fun `valid manifest parses and has no Jar-URL`() {
        val d = Descriptor(validManifest, false)
        assertEquals("Snake", d.name)
        assertEquals("Nokia", d.vendor)
        assertEquals("1.0", d.version)
        assertNull(d.jarUrl)
    }

    @Test
    fun `JAD from a real file parses`() {
        val file = tempFolder.newFile("game.jad")
        file.writeText(validJad)
        val d = Descriptor(file, true)
        assertEquals("Snake", d.name)
        assertEquals("Snake.jar", d.jarUrl)
    }

    @Test
    fun `BOM at the start of a JAD is stripped`() {
        val d = Descriptor("\uFEFF$validJad", true)
        assertEquals("Snake", d.name)
    }

    @Test
    fun `continuation lines are joined`() {
        val jad = """
            MIDlet-Name: Sna
             ke
            MIDlet-Vendor: Nokia
            MIDlet-Version: 1.0
            MIDlet-Jar-URL: Snake.jar
            MIDlet-Jar-Size: 12345
            """.trimIndent()
        assertEquals("Snake", Descriptor(jad, true).name)
    }

    @Test
    fun `Jar-URL is trimmed`() {
        val jad = validJad.replace("Snake.jar", "  Snake.jar  ")
        assertEquals("Snake.jar", Descriptor(jad, true).jarUrl)
    }

    // Missing / bad required attributes

    @Test(expected = DescriptorException::class)
    fun `JAD without MIDlet-Name fails`() {
        Descriptor(validJad.replace("MIDlet-Name: Snake\n", ""), true)
    }

    @Test(expected = DescriptorException::class)
    fun `JAD without MIDlet-Vendor fails`() {
        Descriptor(validJad.replace("MIDlet-Vendor: Nokia\n", ""), true)
    }

    @Test(expected = DescriptorException::class)
    fun `JAD without MIDlet-Version fails`() {
        Descriptor(validJad.replace("MIDlet-Version: 1.0\n", ""), true)
    }

    @Test(expected = DescriptorException::class)
    fun `JAD without MIDlet-Jar-Size fails`() {
        // NOTE: Jar-Size is the last line of validJad after
        // trimIndent(), so there is no trailing "\n" to match.
        Descriptor(validJad.replace("MIDlet-Jar-Size: 12345", ""), true)
    }

    @Test(expected = DescriptorException::class)
    fun `JAD with a non-integer MIDlet-Jar-Size fails`() {
        Descriptor(validJad.replace("12345", "twelve"), true)
    }

    @Test(expected = DescriptorException::class)
    fun `JAD without MIDlet-Jar-URL fails`() {
        Descriptor(validJad.replace("MIDlet-Jar-URL: Snake.jar\n", ""), true)
    }

    @Test(expected = DescriptorException::class)
    fun `JAD with an empty MIDlet-Jar-URL fails`() {
        Descriptor(validJad.replace("Snake.jar", "   "), true)
    }

    @Test(expected = DescriptorException::class)
    fun `manifest without MIDlet-Name fails`() {
        Descriptor(validManifest.replace("MIDlet-Name: Snake\n", ""), false)
    }

    // Corrupt input

    @Test(expected = DescriptorException::class)
    fun `garbage text fails`() {
        Descriptor("hello world\nthis is not a descriptor\n", true)
    }

    @Test(expected = DescriptorException::class)
    fun `empty input fails`() {
        Descriptor("", true)
    }

    // Version comparison (reinstall newer / older)

    @Test
    fun `compareVersion orders versions`() {
        val d = Descriptor(validJad, true)
        assertEquals(-1, d.compareVersion("1.1"))
        assertEquals(0, d.compareVersion("1.0"))
        assertEquals(1, d.compareVersion("0.9"))
        // "1.0" is older than "1.0.1" (missing part counts as 0).
        assertEquals(-1, d.compareVersion("1.0.1"))
    }

    @Test
    fun `compareVersion handles a longer version`() {
        val d = Descriptor(validJad, true)
        assertTrue(d.compareVersion("1.0.0") == 0)
        assertTrue(d.compareVersion("2.0") < 0)
    }

    // Duplicate detection (equals on Name + Vendor)

    @Test
    fun `same name and vendor are equal despite different versions`() {
        val a = Descriptor(validJad, true)
        val b = Descriptor(validJad.replace("1.0", "2.0"), true)
        assertTrue(a == b)
    }

    @Test
    fun `different vendor is not equal`() {
        val a = Descriptor(validJad, true)
        val b = Descriptor(validJad.replace("Nokia", "Siemens"), true)
        assertFalse(a == b)
    }

    @Test
    fun `different name is not equal`() {
        val a = Descriptor(validJad, true)
        val b = Descriptor(validJad.replace("Snake", "Tetris"), true)
        assertFalse(a == b)
    }
}
