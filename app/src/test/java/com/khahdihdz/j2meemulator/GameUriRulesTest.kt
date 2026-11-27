package com.khahdihdz.j2meemulator

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

/**
 * Phase 7 — unit tests for [GameUriRules], the pure import rules
 * behind Home. These run on the JVM (`testFdroidDebugUnitTest`,
 * JUnit 4); they cover the prompt's import cases at the level Home
 * decides them (scheme / extension / which Uri an intent carries).
 * Everything deeper (JAD/manifest parsing, JAR→DEX conversion,
 * duplicate handling, magic-byte sniffing) lives in the engine and
 * is covered by [DescriptorTest] and the manual checklist in
 * docs/ENGINE-INTEGRATION.md §14.
 */
class GameUriRulesTest {

    // validate(): accepted schemes and extensions

    @Test
    fun `file jar is accepted`() {
        assertNull(GameUriRules.validate("file", "/sdcard/games/snake.jar"))
    }

    @Test
    fun `content jad is accepted`() {
        assertNull(GameUriRules.validate("content", "content://com.example/snake.jad"))
    }

    @Test
    fun `http kjx is accepted`() {
        assertNull(GameUriRules.validate("http", "http://example.com/game.kjx"))
    }

    @Test
    fun `https jar is accepted`() {
        assertNull(GameUriRules.validate("https", "https://example.com/game.jar"))
    }

    @Test
    fun `uppercase scheme is accepted`() {
        assertNull(GameUriRules.validate("FILE", "/sdcard/games/snake.jar"))
    }

    @Test
    fun `uppercase extension is accepted`() {
        assertNull(GameUriRules.validate("file", "/sdcard/games/SNAKE.JAR"))
    }

    // validate(): bad schemes

    @Test
    fun `ftp scheme is rejected`() {
        assertEquals(
            GameUriRules.Error.BAD_SCHEME,
            GameUriRules.validate("ftp", "/sdcard/games/snake.jar")
        )
    }

    @Test
    fun `missing scheme is rejected`() {
        assertEquals(
            GameUriRules.Error.BAD_SCHEME,
            GameUriRules.validate(null, "/sdcard/games/snake.jar")
        )
    }

    @Test
    fun `empty scheme is rejected`() {
        assertEquals(
            GameUriRules.Error.BAD_SCHEME,
            GameUriRules.validate("", "/sdcard/games/snake.jar")
        )
    }

    // validate(): bad extensions

    @Test
    fun `apk extension is rejected`() {
        assertEquals(
            GameUriRules.Error.BAD_EXTENSION,
            GameUriRules.validate("file", "/sdcard/games/app.apk")
        )
    }

    @Test
    fun `zip extension is rejected`() {
        assertEquals(
            GameUriRules.Error.BAD_EXTENSION,
            GameUriRules.validate("file", "/sdcard/games/archive.zip")
        )
    }

    @Test
    fun `bad scheme wins over bad extension`() {
        assertEquals(
            GameUriRules.Error.BAD_SCHEME,
            GameUriRules.validate("ftp", "/sdcard/games/app.apk")
        )
    }

    // validate(): lenient cases — the engine decides these

    @Test
    fun `no extension is accepted`() {
        assertNull(GameUriRules.validate("content", "content://com.example/1234"))
    }

    @Test
    fun `trailing dot is accepted`() {
        assertNull(GameUriRules.validate("file", "/sdcard/games/game."))
    }

    @Test
    fun `null path is accepted`() {
        assertNull(GameUriRules.validate("file", null))
    }

    // pickIntentUri(): which Uri an intent carries

    @Test
    fun `history Uri is ignored`() {
        assertNull(
            GameUriRules.pickIntentUri(
                "android.intent.action.VIEW",
                "file:///sdcard/games/snake.jar", null, null, true
            )
        )
    }

    @Test
    fun `VIEW takes data`() {
        assertEquals(
            "file:///sdcard/games/snake.jar",
            GameUriRules.pickIntentUri(
                "android.intent.action.VIEW",
                "file:///sdcard/games/snake.jar", null, null, false
            )
        )
    }

    @Test
    fun `VIEW without data is null`() {
        assertNull(
            GameUriRules.pickIntentUri(
                "android.intent.action.VIEW", null, null, null, false
            )
        )
    }

    @Test
    fun `SEND takes EXTRA_STREAM`() {
        assertEquals(
            "content://com.example/snake.jar",
            GameUriRules.pickIntentUri(
                "android.intent.action.SEND",
                null, "content://com.example/snake.jar", null, false
            )
        )
    }

    @Test
    fun `SEND without stream is null`() {
        assertNull(
            GameUriRules.pickIntentUri(
                "android.intent.action.SEND", null, null, null, false
            )
        )
    }

    @Test
    fun `SEND_MULTIPLE takes the first item`() {
        assertEquals(
            "content://com.example/a.jar",
            GameUriRules.pickIntentUri(
                "android.intent.action.SEND_MULTIPLE",
                null, null,
                listOf("content://com.example/a.jar", "content://com.example/b.jar"),
                false
            )
        )
    }

    @Test
    fun `SEND_MULTIPLE with an empty list is null`() {
        assertNull(
            GameUriRules.pickIntentUri(
                "android.intent.action.SEND_MULTIPLE",
                null, null, emptyList(), false
            )
        )
    }

    @Test
    fun `SEND_MULTIPLE with a null list is null`() {
        assertNull(
            GameUriRules.pickIntentUri(
                "android.intent.action.SEND_MULTIPLE",
                null, null, null, false
            )
        )
    }

    @Test
    fun `unknown action is null`() {
        assertNull(
            GameUriRules.pickIntentUri(
                "android.intent.action.MAIN",
                "file:///sdcard/games/snake.jar", null, null, false
            )
        )
    }

    @Test
    fun `null action is null`() {
        assertNull(
            GameUriRules.pickIntentUri(
                null, "file:///sdcard/games/snake.jar", null, null, false
            )
        )
    }
}
