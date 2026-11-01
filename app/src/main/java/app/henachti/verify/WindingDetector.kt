package app.henachti.verify

import android.graphics.Bitmap
import android.graphics.Color
import kotlin.math.max
import kotlin.math.min

/**
 * Looks for the tefillin strap wound around the forearm: dark, unsaturated bands crossing the
 * arm with skin between them. Works on the live preview, inside the on-screen guide region,
 * in either orientation (arm lying across the frame, or along it). No models, no network.
 */
object WindingDetector {

    /** How many windings must cross the forearm: the full seven, so the whole forearm has to be in frame. */
    const val REQUIRED = 7

    data class Reading(val bands: Int, val horizontal: Boolean)

    fun read(bmp: Bitmap): Reading {
        val w = bmp.width
        val h = bmp.height
        val horizontal = profileBands(
            bmp,
            x0 = (w * 0.06f).toInt(), x1 = (w * 0.94f).toInt(),
            y0 = (h * 0.30f).toInt(), y1 = (h * 0.70f).toInt(),
            alongX = true,
        )
        val vertical = profileBands(
            bmp,
            x0 = (w * 0.30f).toInt(), x1 = (w * 0.70f).toInt(),
            y0 = (h * 0.06f).toInt(), y1 = (h * 0.94f).toInt(),
            alongX = false,
        )
        return if (horizontal >= vertical) Reading(horizontal, true) else Reading(vertical, false)
    }

    private fun lum(c: Int): Float =
        0.299f * Color.red(c) + 0.587f * Color.green(c) + 0.114f * Color.blue(c)

    private class Run(val cls: Int, var len: Int)

    /**
     * Slices the region along one axis. Each slice is DARK (mostly strap), SKIN (mostly warm,
     * bright skin) or OTHER. A winding is a DARK run of plausible width with SKIN on both sides.
     */
    private fun profileBands(bmp: Bitmap, x0: Int, x1: Int, y0: Int, y1: Int, alongX: Boolean): Int {
        val axisLen = if (alongX) x1 - x0 else y1 - y0
        val crossLen = if (alongX) y1 - y0 else x1 - x0
        if (axisLen < 40 || crossLen < 20) return 0
        val stepAxis = max(1, axisLen / 160)
        val stepCross = max(1, crossLen / 24)

        val lums = ArrayList<FloatArray>()
        val warms = ArrayList<BooleanArray>()
        var a = 0
        while (a < axisLen) {
            val cap = crossLen / stepCross + 1
            val lumRow = FloatArray(cap)
            val warmRow = BooleanArray(cap)
            var k = 0
            var c = 0
            while (c < crossLen && k < cap) {
                val x = if (alongX) x0 + a else x0 + c
                val y = if (alongX) y0 + c else y0 + a
                if (x in 0 until bmp.width && y in 0 until bmp.height) {
                    val p = bmp.getPixel(x, y)
                    lumRow[k] = lum(p)
                    warmRow[k] = Color.red(p) >= Color.blue(p)
                    k++
                }
                c += stepCross
            }
            lums += lumRow.copyOf(k)
            warms += warmRow.copyOf(k)
            a += stepAxis
        }

        val all = lums.flatMap { it.asList() }.sorted()
        if (all.size < 100) return 0
        val median = all[all.size / 2]
        val darkThr = min(90f, median * 0.55f)
        val skinThr = median * 0.75f

        val cls = IntArray(lums.size)
        for (i in lums.indices) {
            val n = lums[i].size
            if (n == 0) continue
            var dark = 0
            var skin = 0
            for (j in 0 until n) {
                val l = lums[i][j]
                if (l < darkThr) dark++ else if (l > skinThr && warms[i][j]) skin++
            }
            cls[i] = when {
                dark >= n * 0.45f -> 1
                skin >= n * 0.45f -> 2
                else -> 0
            }
        }

        val runs = ArrayList<Run>()
        for (c in cls) {
            if (c == 0) continue
            if (runs.isNotEmpty() && runs.last().cls == c) runs.last().len++ else runs += Run(c, 1)
        }

        val total = cls.size
        val minDark = max(2, (total * 0.015f).toInt())
        val maxDark = max(minDark + 1, (total * 0.22f).toInt())
        val minSkin = max(2, (total * 0.015f).toInt())

        var bands = 0
        for (i in runs.indices) {
            val r = runs[i]
            if (r.cls != 1 || r.len < minDark || r.len > maxDark) continue
            val before = runs.getOrNull(i - 1)
            val after = runs.getOrNull(i + 1)
            if (before != null && before.cls == 2 && before.len >= minSkin &&
                after != null && after.cls == 2 && after.len >= minSkin
            ) bands++
        }
        return bands
    }
}
