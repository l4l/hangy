package me.kitsu.hangy.data.ble

/** A single decoded reading from the scale. */
data class ScaleReading(
    val weightKg: Double,
    /** Best-effort "measurement settled" flag decoded from the status nibble. */
    val stable: Boolean,
)

/**
 * Decodes the Weiheng WH-C06 crane scale.
 *
 * The scale exposes **no GATT services**; it broadcasts the weight inside its BLE
 * manufacturer-specific advertisement data under company id [MANUFACTURER_ID] (0x0100).
 * The payload returned by `ScanRecord.getManufacturerSpecificData(0x0100)` (which already
 * excludes the 2-byte company id, matching the Web Bluetooth `manufacturerData` view) carries
 * the weight as a big-endian **signed 16-bit two's-complement** value at [WEIGHT_OFFSET], in
 * hundredths of a kilogram. The scale can report small negative readings (e.g. after taring or
 * a slight upward force): the high bit of the 16-bit value is the sign, so a raw 0xFFFB decodes
 * to -0.05 kg rather than ~655 kg. A status nibble near [FLAGS_OFFSET] encodes stability/unit.
 *
 * Reference: Stevie-Ray/hangtime-grip-connect `wh-c06.model.ts`.
 */
object WhC06Parser {
    const val MANUFACTURER_ID = 0x0100
    const val WEIGHT_OFFSET = 10
    const val FLAGS_OFFSET = 14

    private const val HUNDREDTHS = 100.0
    private const val BYTE_MASK = 0xFF
    private const val HIGH_NIBBLE_SHIFT = 4

    /**
     * @param data manufacturer-specific payload (without the company-id bytes).
     * @return the decoded reading, or null when the payload is too short to hold a weight.
     */
    fun parse(data: ByteArray?): ScaleReading? {
        if (data == null || data.size < WEIGHT_OFFSET + 2) return null

        val high = data[WEIGHT_OFFSET].toInt() and BYTE_MASK
        val low = data[WEIGHT_OFFSET + 1].toInt() and BYTE_MASK
        // Combine as a raw u16, then reinterpret via Short so the sign bit extends: negative
        // readings arrive as two's-complement (e.g. 0xFFFB -> -5 hundredths -> -0.05 kg).
        val raw = (high shl 8) or low
        val weightKg = raw.toShort().toInt() / HUNDREDTHS

        val stable = if (data.size > FLAGS_OFFSET) {
            ((data[FLAGS_OFFSET].toInt() and BYTE_MASK) ushr HIGH_NIBBLE_SHIFT) != 0
        } else {
            true
        }

        return ScaleReading(weightKg = weightKg, stable = stable)
    }
}
