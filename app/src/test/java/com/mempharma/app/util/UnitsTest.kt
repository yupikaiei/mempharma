package com.mempharma.app.util

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * The unit-label marker logic decides whether we print the localized
 * "comprimido/comprimidos" or the person's own wording, so it must be exact —
 * getting it wrong either mangles a custom label or resurrects the
 * "comprimido(s)" text this whole change set out to remove.
 */
class UnitsTest {

    @Test
    fun `blank label counts as default`() {
        assertTrue(Units.isDefaultLabel(null))
        assertTrue(Units.isDefaultLabel(""))
        assertTrue(Units.isDefaultLabel("   "))
    }

    @Test
    fun `current Portuguese default is recognised`() {
        assertTrue(Units.isDefaultLabel("comprimidos"))
        assertTrue(Units.isDefaultLabel("comprimido"))
    }

    @Test
    fun `legacy parenthesised defaults are recognised`() {
        assertTrue(Units.isDefaultLabel("comprimido(s)"))
        assertTrue(Units.isDefaultLabel("pill(s)"))
    }

    @Test
    fun `English defaults are recognised`() {
        assertTrue(Units.isDefaultLabel("pill"))
        assertTrue(Units.isDefaultLabel("pills"))
    }

    @Test
    fun `matching is case and whitespace insensitive`() {
        assertTrue(Units.isDefaultLabel("  Comprimidos "))
        assertTrue(Units.isDefaultLabel("PILLS"))
    }

    @Test
    fun `custom labels are never treated as default`() {
        assertFalse(Units.isDefaultLabel("gotas"))
        assertFalse(Units.isDefaultLabel("cápsulas"))
        assertFalse(Units.isDefaultLabel("sachês"))
        assertFalse(Units.isDefaultLabel("ml"))
    }

    @Test
    fun `custom labels that merely contain a default word are kept`() {
        assertFalse(Units.isDefaultLabel("comprimidos efervescentes"))
        assertFalse(Units.isDefaultLabel("meio comprimido"))
    }
}
