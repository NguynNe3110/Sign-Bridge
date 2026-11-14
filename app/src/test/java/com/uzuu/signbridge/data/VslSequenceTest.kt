package com.uzuu.signbridge.data

import com.uzuu.signbridge.data.local.datasource.VslSequence
import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class VslSequenceTest {
    private fun interpolate(values: List<Double>): FloatArray =
        VslSequence.interpolate(values.map { floatArrayOf(it.toFloat()) }).map { it[0] }.toFloatArray()

    private val probe = intArrayOf(0, 1, 7, 13, 29, 30, 46, 58, 59)

    /** Giá trị kỳ vọng sinh bằng scipy 1.17.1: interp1d(linspace(0,1,n), y, kind="cubic")(linspace(0,1,60)). */
    @Test fun matchesScipyCubicInterpolationUsedInTraining() {
        val cases = mapOf(
            listOf(0.10, 0.80, -0.30, 0.55) to
                floatArrayOf(0.1f, 0.2378228f, 0.791031f, 0.9518972f, 0.2725775f, 0.208721f, -0.4178553f, 0.4009305f, 0.55f),
            listOf(0.62, 0.41, 0.97, 0.05, 0.33, 0.88, 0.12) to
                floatArrayOf(0.62f, 0.4531579f, 0.2234651f, 0.6898937f, 0.082755f, 0.0236289f, 0.7781424f, 0.2978255f, 0.12f),
            listOf(0.5, 0.775869, 0.930544, 0.849829, 0.656723, 0.400596, 0.160461, 0.134292, 0.292948, 0.520362,
                0.794613, 0.895267, 0.860021, 0.664003, 0.357954, 0.170849, 0.149238, 0.261836, 0.540722,
                0.813282, 0.909879, 0.870117, 0.621247) to
                floatArrayOf(0.5f, 0.6054487f, 0.9028559f, 0.4424911f, 0.8888341f, 0.8982202f, 0.2955354f, 0.7458935f, 0.621247f),
        )
        cases.forEach { (input, expected) ->
            val out = interpolate(input)
            assertEquals(VslSequence.FRAMES, out.size)
            assertArrayEquals("n=${input.size}", expected, probe.map { out[it] }.toFloatArray(), 1e-5f)
        }
    }

    @Test fun reproducesCubicExactlyUnlikeNaturalSpline() {
        fun f(t: Double) = 2 * t * t * t - 3 * t * t + 0.5 * t + 0.25
        val n = 17
        val out = interpolate(List(n) { f(it.toDouble() / (n - 1)) })
        out.forEachIndexed { k, value -> assertEquals(f(k / 59.0).toFloat(), value, 1e-5f) }
    }

    @Test fun keepsFeaturesIndependentAndOutputShape() {
        val frames = List(9) { i -> FloatArray(VslSequence.FEATURES) { f -> (i * 0.1f) + f * 0.001f } }
        val out = VslSequence.interpolate(frames)
        assertEquals(60, out.size)
        assertTrue(out.all { it.size == 201 })
        // Dữ liệu tuyến tính theo thời gian (giá trị = 0,8·t + 0,001·feature) → cubic giữ nguyên đường thẳng.
        assertEquals(0.8f + 200 * 0.001f, out.last()[200], 1e-5f)
        assertEquals(0.8f * 29f / 59f + 5 * 0.001f, out[29][5], 1e-5f)
    }

    @Test fun packsPoseLeftRightInTrainingOrderAndZeroFillsMissingParts() {
        val pose = FloatArray(33 * 3) { it.toFloat() } // Holistic trả 33 điểm, chỉ lấy 25 đầu
        val right = FloatArray(21 * 3) { 1000f + it }
        val packed = VslSequence.pack(pose, null, right)
        assertEquals(201, packed.size)
        assertEquals(74f, packed[74])          // pose24.z
        assertEquals(0f, packed[75])           // tay trái thiếu → 0
        assertEquals(0f, packed[137])
        assertEquals(1000f, packed[138])       // right0.x
        assertEquals(1062f, packed[200])       // right20.z
        assertTrue(VslSequence.hasHand(packed))
        assertFalse(VslSequence.hasHand(VslSequence.pack(pose, null, null)))
    }

    @Test(expected = IllegalArgumentException::class)
    fun rejectsFewerThanFourFrames() {
        VslSequence.interpolate(List(3) { FloatArray(201) })
    }
}
