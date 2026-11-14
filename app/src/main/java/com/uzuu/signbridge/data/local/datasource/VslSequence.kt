package com.uzuu.signbridge.data.local.datasource

/**
 * Tiền xử lý input model VSL — phải khớp lúc train (guideline-vsl-hybrid §6, `main.py` repo nguồn).
 * Thuần Kotlin để test JVM; phần MediaPipe/TFLite nằm ở [VslRecognizerDataSource].
 */
object VslSequence {
    const val POSE_POINTS = 25
    const val HAND_POINTS = 21
    const val FEATURES = (POSE_POINTS + HAND_POINTS * 2) * 3 // 201
    const val FRAMES = 60
    private const val POSE_SIZE = POSE_POINTS * 3
    private const val HAND_SIZE = HAND_POINTS * 3

    /**
     * Ghép 1 frame theo thứ tự `[pose0.xyz … pose24.xyz | tay trái 21.xyz | tay phải 21.xyz]`.
     * Mỗi mảng là toạ độ chuẩn hoá (x, y, z) liên tiếp; null (không thấy bộ phận) → điền 0 cả khối.
     */
    fun pack(pose: FloatArray?, leftHand: FloatArray?, rightHand: FloatArray?): FloatArray {
        val out = FloatArray(FEATURES)
        pose?.copyInto(out, 0, 0, minOf(pose.size, POSE_SIZE))
        leftHand?.takeIf { it.size >= HAND_SIZE }?.copyInto(out, POSE_SIZE, 0, HAND_SIZE)
        rightHand?.takeIf { it.size >= HAND_SIZE }?.copyInto(out, POSE_SIZE + HAND_SIZE, 0, HAND_SIZE)
        return out
    }

    /** Frame có ít nhất một bàn tay được nhận ra. */
    fun hasHand(frame: FloatArray): Boolean {
        for (i in POSE_SIZE until FEATURES) if (frame[i] != 0f) return true
        return false
    }

    /**
     * Nội suy về [targetLength] frame giống `scipy.interpolate.interp1d(kind="cubic")` trên trục
     * `linspace(0, 1, n)`: spline bậc 3 điều kiện biên not-a-knot (như `make_interp_spline(k=3)`).
     * Cần ít nhất 4 frame.
     */
    fun interpolate(frames: List<FloatArray>, targetLength: Int = FRAMES): Array<FloatArray> {
        val n = frames.size
        require(n >= 4) { "Cần ít nhất 4 frame để nội suy cubic." }
        val features = frames[0].size
        require(frames.all { it.size == features })
        val out = Array(targetLength) { FloatArray(features) }
        val h = 1.0 / (n - 1)
        val y = DoubleArray(n)
        val m = DoubleArray(n)
        // Hệ số Thomas cho hàng nội 1..n-2 (dùng chung mọi feature vì trục x như nhau).
        val sub = DoubleArray(n)
        val diag = DoubleArray(n)
        val sup = DoubleArray(n)
        for (i in 1..n - 2) {
            val edge = i == 1 || i == n - 2
            // Thế điều kiện not-a-knot (M0 = 2M1 − M2, Mn−1 = 2Mn−2 − Mn−3) vào hàng đầu/cuối → 6h·M = rhs.
            sub[i] = if (edge) 0.0 else h
            diag[i] = if (edge) 6 * h else 4 * h
            sup[i] = if (edge) 0.0 else h
        }
        val cPrime = DoubleArray(n)
        val dPrime = DoubleArray(n)
        for (feature in 0 until features) {
            for (i in 0 until n) y[i] = frames[i][feature].toDouble()
            // Thomas: M1..Mn−2
            for (i in 1..n - 2) {
                val rhs = 6 * (y[i + 1] - 2 * y[i] + y[i - 1]) / h
                val denom = diag[i] - (if (i > 1) sub[i] * cPrime[i - 1] else 0.0)
                cPrime[i] = sup[i] / denom
                dPrime[i] = (rhs - (if (i > 1) sub[i] * dPrime[i - 1] else 0.0)) / denom
            }
            for (i in n - 2 downTo 1) {
                m[i] = dPrime[i] - (if (i < n - 2) cPrime[i] * m[i + 1] else 0.0)
            }
            m[0] = 2 * m[1] - m[2]
            m[n - 1] = 2 * m[n - 2] - m[n - 3]
            for (k in 0 until targetLength) {
                val t = if (targetLength == 1) 0.0 else k.toDouble() / (targetLength - 1)
                val i = minOf((t / h).toInt(), n - 2)
                val a = (i + 1) * h - t
                val b = t - i * h
                val value = m[i] * a * a * a / (6 * h) + m[i + 1] * b * b * b / (6 * h) +
                    (y[i] / h - m[i] * h / 6) * a + (y[i + 1] / h - m[i + 1] * h / 6) * b
                out[k][feature] = value.toFloat()
            }
        }
        return out
    }
}
