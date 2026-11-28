package com.khahdihdz.j2meemulator

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Phase 7 — unit tests for [LibraryRules], the pure search and sort
 * rules behind Home. The match rule and the direction-toggle rule
 * are the engine's own rules (1.8.2); these tests pin them so Home
 * and the engine Library keep agreeing.
 */
class LibraryRulesTest {

    // matches(): search

    @Test
    fun `empty query matches everything`() {
        assertTrue(LibraryRules.matches("Snake", "Nokia", ""))
    }

    @Test
    fun `title contains query`() {
        assertTrue(LibraryRules.matches("Snake", "Nokia", "sna"))
    }

    @Test
    fun `query is case-insensitive`() {
        assertTrue(LibraryRules.matches("Snake", "Nokia", "SNAKE"))
        assertTrue(LibraryRules.matches("snake", "nokia", "NOK"))
    }

    @Test
    fun `author contains query`() {
        assertTrue(LibraryRules.matches("Snake", "Nokia", "nok"))
    }

    @Test
    fun `no match`() {
        assertFalse(LibraryRules.matches("Snake", "Nokia", "tetris"))
    }

    @Test
    fun `unicode query matches`() {
        assertTrue(LibraryRules.matches("Cờ Tỷ Phú", "Khahdihdz", "tỷ phú"))
    }

    @Test
    fun `unicode query is case-insensitive`() {
        assertTrue(LibraryRules.matches("Cờ Tỷ Phú", "Khahdihdz", "TỶ PHÚ"))
    }

    @Test
    fun `query with spaces is not trimmed`() {
        // The engine list does not trim either: " snake" is not
        // contained in "snake".
        assertFalse(LibraryRules.matches("Snake", "Nokia", " snake"))
    }

    @Test
    fun `query longer than title does not match`() {
        assertFalse(LibraryRules.matches("Snake", "Nokia", "snake game 2004"))
    }

    // toggleSort(): direction toggle

    @Test
    fun `picking the active variant flips its direction bit`() {
        assertEquals(Int.MIN_VALUE, LibraryRules.toggleSort(0, 0))
    }

    @Test
    fun `picking the active variant keeps its base value`() {
        assertEquals(1 or Int.MIN_VALUE, LibraryRules.toggleSort(1, 1))
    }

    @Test
    fun `picking another variant selects it ascending`() {
        assertEquals(2, LibraryRules.toggleSort(0, 2))
        assertEquals(2, LibraryRules.toggleSort(1, 2))
    }

    @Test
    fun `picking a variant after a flipped one resets direction`() {
        // Current is variant 0 descending (0x80000000); picking
        // variant 0 again is a different stored value, so it selects
        // variant 0 ascending instead of flipping again.
        assertEquals(0, LibraryRules.toggleSort(Int.MIN_VALUE, 0))
    }
}
