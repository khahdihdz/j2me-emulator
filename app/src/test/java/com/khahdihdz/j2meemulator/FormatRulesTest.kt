package com.khahdihdz.j2meemulator

import java.io.File
import java.util.Locale
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder

/**
 * Phase 7 — unit tests for [FormatRules], the pure formatting and
 * filesystem helpers behind the game details screen.
 */
class FormatRulesTest {

    // formatSize() uses the default locale for "%.2f"; pin it so
    // the expected strings do not depend on the CI runner's locale.
    private lateinit var oldLocale: Locale

    @Before
    fun pinLocale() {
        oldLocale = Locale.getDefault()
        Locale.setDefault(Locale.US)
    }

    @After
    fun restoreLocale() {
        Locale.setDefault(oldLocale)
    }

    @Test
    fun `zero bytes`() {
        assertEquals("0 B", FormatRules.formatSize(0))
    }

    @Test
    fun `bytes below a kilobyte`() {
        assertEquals("512 B", FormatRules.formatSize(512))
        assertEquals("1023 B", FormatRules.formatSize(1023))
    }

    @Test
    fun `exact kilobyte`() {
        assertEquals("1.00 KB", FormatRules.formatSize(1024))
    }

    @Test
    fun `fractional kilobytes`() {
        assertEquals("1.50 KB", FormatRules.formatSize(1536))
    }

    @Test
    fun `megabytes`() {
        assertEquals("1.00 MB", FormatRules.formatSize(1024L * 1024))
        assertEquals("5.50 MB", FormatRules.formatSize(5L * 1024 * 1024 + 512L * 1024))
    }

    @Test
    fun `gigabytes`() {
        assertEquals("1.00 GB", FormatRules.formatSize(1024L * 1024 * 1024))
        assertEquals("1.50 GB", FormatRules.formatSize(3 * 1024L * 1024 * 1024 / 2))
    }

    // dirSize()

    @get:Rule
    val tempFolder = TemporaryFolder()

    @Test
    fun `empty directory is zero`() {
        assertEquals(0L, FormatRules.dirSize(tempFolder.root))
    }

    @Test
    fun `files sum up including nested directories`() {
        tempFolder.newFile("a.bin").writeBytes(ByteArray(10))
        val sub = tempFolder.newFolder("sub")
        File(sub, "b.bin").writeBytes(ByteArray(5))
        File(sub, "c.bin").writeBytes(ByteArray(7))
        assertEquals(22L, FormatRules.dirSize(tempFolder.root))
    }

    @Test
    fun `missing directory is minus one`() {
        assertEquals(-1L, FormatRules.dirSize(File(tempFolder.root, "no-such-dir")))
    }

    @Test
    fun `a regular file is minus one`() {
        val file = tempFolder.newFile("f.bin")
        assertEquals(-1L, FormatRules.dirSize(file))
    }

    // orientationLabel() / vkLayoutLabel(): in-range vs out-of-range

    private val orientationEntries = arrayOf("Default", "Auto", "Portrait", "Landscape")
    private val vkEntries = arrayOf(
        "Custom", "Phone", "Phone+arrows", "Numbers+arrows",
        "Arrows+numbers", "Numbers", "Arrows"
    )

    @Test
    fun `orientation label in range`() {
        assertEquals("Default", FormatRules.orientationLabel(orientationEntries, 0))
        assertEquals("Landscape", FormatRules.orientationLabel(orientationEntries, 3))
    }

    @Test
    fun `orientation label out of range is null`() {
        assertNull(FormatRules.orientationLabel(orientationEntries, 4))
        assertNull(FormatRules.orientationLabel(orientationEntries, -1))
    }

    @Test
    fun `vk layout label in range`() {
        assertEquals("Custom", FormatRules.vkLayoutLabel(vkEntries, 0))
        assertEquals("Arrows", FormatRules.vkLayoutLabel(vkEntries, 6))
    }

    @Test
    fun `vk layout label out of range is null`() {
        assertNull(FormatRules.vkLayoutLabel(vkEntries, 7))
        assertNull(FormatRules.vkLayoutLabel(vkEntries, -1))
    }
}
