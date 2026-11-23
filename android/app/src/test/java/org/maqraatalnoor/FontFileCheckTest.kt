package org.maqraatalnoor

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import org.maqraatalnoor.fonts.FontFileCheck

class FontFileCheckTest {

    @Test
    fun otfHeader_accepted() {
        assertTrue(FontFileCheck.looksLikeFontFile("OTTO".toByteArray(Charsets.US_ASCII)))
    }

    @Test
    fun ttfHeader_accepted() {
        assertTrue(FontFileCheck.looksLikeFontFile(byteArrayOf(0x00, 0x01, 0x00, 0x00)))
    }

    @Test
    fun trueTypeHeader_accepted() {
        assertTrue(FontFileCheck.looksLikeFontFile("true".toByteArray(Charsets.US_ASCII)))
    }

    @Test
    fun textHeader_rejected() {
        assertFalse(FontFileCheck.looksLikeFontFile("Hello, world".toByteArray(Charsets.US_ASCII)))
    }

    @Test
    fun shortHeader_rejected() {
        assertFalse(FontFileCheck.looksLikeFontFile(byteArrayOf(0x00, 0x01)))
        assertFalse(FontFileCheck.looksLikeFontFile(byteArrayOf()))
    }
}
