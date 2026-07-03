package me.kitsu.hangy.data.ble

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class WhC06ParserTest {

    /** Builds a payload with the weight (in hundredths of kg) placed at the documented offset. */
    private fun payload(hundredthsKg: Int, size: Int = 16, flags: Int = 0x00): ByteArray {
        val data = ByteArray(size)
        data[WhC06Parser.WEIGHT_OFFSET] = ((hundredthsKg shr 8) and 0xFF).toByte()
        data[WhC06Parser.WEIGHT_OFFSET + 1] = (hundredthsKg and 0xFF).toByte()
        if (size > WhC06Parser.FLAGS_OFFSET) data[WhC06Parser.FLAGS_OFFSET] = flags.toByte()
        return data
    }

    @Test
    fun `decodes big-endian hundredths of a kilogram`() {
        val reading = WhC06Parser.parse(payload(4230))
        assertEquals(42.30, reading!!.weightKg, 1e-6)
    }

    @Test
    fun `decodes zero`() {
        assertEquals(0.0, WhC06Parser.parse(payload(0))!!.weightKg, 1e-6)
    }

    @Test
    fun `decodes near full scale`() {
        // 300.00 kg = 30000 hundredths, still within the unsigned 16-bit range.
        assertEquals(300.00, WhC06Parser.parse(payload(30000))!!.weightKg, 1e-6)
    }

    @Test
    fun `decodes a small negative reading after taring`() {
        // -0.05 kg = -5 hundredths, encoded as two's-complement 0xFFFB. Under the old unsigned
        // decoding this was wrongly read as 65531 hundredths (~655 kg).
        val reading = WhC06Parser.parse(payload(-5))
        assertEquals(-0.05, reading!!.weightKg, 1e-6)
    }

    @Test
    fun `decodes a larger negative reading`() {
        assertEquals(-42.30, WhC06Parser.parse(payload(-4230))!!.weightKg, 1e-6)
    }

    @Test
    fun `decodes minus one hundredth boundary`() {
        // 0xFFFF must decode to -0.01 kg, not 655.35 kg.
        assertEquals(-0.01, WhC06Parser.parse(payload(-1))!!.weightKg, 1e-6)
    }

    @Test
    fun `decodes the signed 16-bit extremes`() {
        // 0x7FFF is the largest positive value; 0x8000 is the most negative.
        assertEquals(327.67, WhC06Parser.parse(payload(32767))!!.weightKg, 1e-6)
        assertEquals(-327.68, WhC06Parser.parse(payload(-32768))!!.weightKg, 1e-6)
    }

    @Test
    fun `null payload returns null`() {
        assertNull(WhC06Parser.parse(null))
    }

    @Test
    fun `payload too short to hold a weight returns null`() {
        assertNull(WhC06Parser.parse(ByteArray(WhC06Parser.WEIGHT_OFFSET + 1)))
    }

    @Test
    fun `stable flag decoded from high nibble`() {
        assertTrue(WhC06Parser.parse(payload(1000, flags = 0x10))!!.stable)
        assertTrue(!WhC06Parser.parse(payload(1000, flags = 0x00))!!.stable)
    }

    @Test
    fun `manufacturer id constant matches protocol`() {
        assertEquals(0x0100, WhC06Parser.MANUFACTURER_ID)
    }
}
