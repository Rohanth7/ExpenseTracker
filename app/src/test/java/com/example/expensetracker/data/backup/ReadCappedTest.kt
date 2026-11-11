package com.example.expensetracker.data.backup

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test
import java.io.InputStream

/** B-24: the restore picker takes any file, and a 200 MB video was read whole into memory. */
class ReadCappedTest {
    private fun read(text: String, max: Int) = BackupManager.readCapped(text.byteInputStream(), max)

    @Test fun `a file within the limit is read whole`() {
        assertEquals("", read("", 10))
        assertEquals("{\"version\":3}", read("{\"version\":3}", 13))
        assertEquals("₹ café", read("₹ café", 100))
        val long = "x".repeat(200_000)
        assertEquals(long, read(long, 200_000))
    }

    @Test fun `a file over the limit is refused`() {
        assertNull(read("12345678901", 10))
        assertNull(read("x".repeat(200_001), 200_000))
    }

    @Test fun `reading stops at the limit instead of taking the whole file`() {
        var served = 0L
        val endless = object : InputStream() {
            override fun read(): Int { served++; return 'x'.code }
            override fun read(b: ByteArray, off: Int, len: Int): Int { served += len; b.fill('x'.code.toByte(), off, off + len); return len }
        }
        assertNull(BackupManager.readCapped(endless, 1_000_000))
        assert(served < 2_000_000) { "read $served bytes" }
    }
}
