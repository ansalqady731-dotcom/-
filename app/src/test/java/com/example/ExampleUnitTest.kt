package com.example

import com.example.ui.FormatUtils
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class ExampleUnitTest {
    @Test
    fun testFormatBytesGb() {
        val bytes = (15.5 * 1024 * 1024 * 1024).toLong()
        val formatted = FormatUtils.formatBytes(bytes)
        assertTrue(formatted.contains("جيجابايت"))
    }

    @Test
    fun testFormatBytesMb() {
        val bytes = (250 * 1024 * 1024).toLong()
        val formatted = FormatUtils.formatBytes(bytes)
        assertTrue(formatted.contains("ميجابايت"))
    }

    @Test
    fun testFormatSpeed() {
        val speed = FormatUtils.formatSpeed(1500)
        assertTrue(speed.contains("ميغابت/ث"))
    }
}
