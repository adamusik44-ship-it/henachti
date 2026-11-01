package app.henachti.verify

import android.content.Context
import android.graphics.Bitmap
import android.graphics.Color
import com.google.mediapipe.framework.image.BitmapImageBuilder
import com.google.mediapipe.tasks.components.containers.NormalizedLandmark
import com.google.mediapipe.tasks.core.BaseOptions
import com.google.mediapipe.tasks.vision.core.RunningMode
import com.google.mediapipe.tasks.vision.handlandmarker.HandLandmarker
import kotlin.math.hypot
import kotlin.math.max
import kotlin.math.min
import kotlin.math.roundToInt

/**
 * Fully on-device verification. No account, no server, no network.
 *
 * 1. MediaPipe Hand Landmarker finds a hand and its 21 landmarks. The raised-finger count
 *    (thumb excluded) must equal the random challenge the screen showed: that is the liveness check.
 * 2. Around that hand we look for the tefillin strap: dark, unsaturated bands wound across the
 *    forearm with skin between them, and dark strap pixels across the palm or the middle finger.
 */
class TefillinDetector(private val context: Context) {

    sealed class Result {
        data class Verified(val note: String) : Result()
        data class Rejected(val reason: String) : Result()
        data class Error(val message: String) : Result()
    }

    private class HandEval(
        val raised: Int,
        val forearmBands: Int,
        val palmStrap: Boolean,
        val fingerStrap: Boolean,
    ) {
        val strapEvidence: Boolean get() = forearmBands >= 2 || palmStrap || fingerStrap
        val score: Int get() = forearmBands * 2 + (if (palmStrap) 2 else 0) + (if (fingerStrap) 1 else 0)
    }

    private var landmarker: HandLandmarker? = null

    private fun landmarker(): HandLandmarker {
        landmarker?.let { return it }
        val base = BaseOptions.builder().setModelAssetPath("hand_landmarker.task").build()
        val options = HandLandmarker.HandLandmarkerOptions.builder()
            .setBaseOptions(base)
            .setRunningMode(RunningMode.IMAGE)
            .setNumHands(2)
            .setMinHandDetectionConfidence(0.35f)
            .setMinHandPresenceConfidence(0.35f)
            .build()
        return HandLandmarker.createFromOptions(context, options).also { landmarker = it }
    }

    fun close() {
        runCatching { landmarker?.close() }
        landmarker = null
    }

    fun analyze(bitmap: Bitmap, fingers: Int): Result {
        val result = try {
            landmarker().detect(BitmapImageBuilder(bitmap).build())
        } catch (e: Exception) {
            return Result.Error("מודל הזיהוי לא נטען: ${e.message}")
        }
        val hands = result.landmarks()
        if (hands.isEmpty()) {
            return Result.Rejected("לא זיהיתי כף יד. קרב את היד למצלמה, באור טוב, עם האצבעות פתוחות.")
        }
        val best = hands.map { evaluate(bitmap, it) }.maxByOrNull { it.score }!!

        if (!best.strapEvidence) {
            return Result.Rejected(
                "היד נראית, אבל לא רואה רצועות שחורות כרוכות על האמה או על כף היד. " +
                    "הראה את האמה עם הכריכות ואת כף היד באותו צילום."
            )
        }
        if (best.raised != fingers) {
            return Result.Rejected(
                "ספרתי ${best.raised} אצבעות מורמות במקום $fingers. הרם בדיוק $fingers, בלי האגודל, ונסה שוב."
            )
        }
        val where = buildString {
            if (best.forearmBands >= 2) append("${best.forearmBands} כריכות על האמה")
            if (best.palmStrap || best.fingerStrap) {
                if (isNotEmpty()) append(", ")
                append("רצועה על כף היד")
            }
        }
        return Result.Verified("זוהו $where.")
    }

    // ---- geometry helpers ----

    private fun luminance(c: Int): Float =
        0.299f * Color.red(c) + 0.587f * Color.green(c) + 0.114f * Color.blue(c)

    private fun median(values: List<Float>): Float {
        if (values.isEmpty()) return 0f
        val sorted = values.sorted()
        return sorted[sorted.size / 2]
    }

    private fun countRaised(lm: List<NormalizedLandmark>, w: Int, h: Int): Int {
        val wx = lm[0].x() * w
        val wy = lm[0].y() * h
        fun d(i: Int) = hypot(lm[i].x() * w - wx, lm[i].y() * h - wy)
        val pairs = listOf(8 to 6, 12 to 10, 16 to 14, 20 to 18) // tip to pip: index, middle, ring, pinky
        return pairs.count { (tip, pip) -> d(tip) > d(pip) * 1.12f }
    }

    private fun evaluate(bmp: Bitmap, lm: List<NormalizedLandmark>): HandEval {
        val w = bmp.width
        val h = bmp.height
        fun px(i: Int) = lm[i].x() * w
        fun py(i: Int) = lm[i].y() * h

        val raised = countRaised(lm, w, h)

        // Hand axis: from the middle-finger base through the wrist, continuing into the forearm.
        val wx = px(0)
        val wy = py(0)
        val mx = px(9)
        val my = py(9)
        var dx = wx - mx
        var dy = wy - my
        val s = hypot(dx, dy).coerceAtLeast(1f)
        dx /= s
        dy /= s
        val nx = -dy
        val ny = dx

        // Forearm: 64 slices along the axis, 9 samples across each slice.
        val steps = 64
        val across = 9
        val lum = Array(steps) { FloatArray(across) { -1f } }
        val warm = Array(steps) { BooleanArray(across) }
        val forearmAll = ArrayList<Float>()
        for (i in 0 until steps) {
            val t = (0.35f + i * (2.6f / steps)) * s
            val cx = wx + dx * t
            val cy = wy + dy * t
            for (j in 0 until across) {
                val off = (j - (across - 1) / 2f) / ((across - 1) / 2f) * 0.75f * s
                val x = (cx + nx * off).roundToInt()
                val y = (cy + ny * off).roundToInt()
                if (x < 0 || y < 0 || x >= w || y >= h) continue
                val c = bmp.getPixel(x, y)
                val l = luminance(c)
                lum[i][j] = l
                warm[i][j] = Color.red(c) >= Color.blue(c)
                forearmAll += l
            }
        }

        // Palm polygon and the middle finger.
        val palmSamples = samplePolygon(bmp, listOf(0, 1, 5, 9, 13, 17).map { px(it) to py(it) })
        val fingerSamples = sampleSegment(bmp, px(9), py(9), px(12), py(12), 0.22f * s)

        val skinRef = max(median(forearmAll), median(palmSamples.map { luminance(it) }))
        val darkThr = min(85f, skinRef * 0.5f)

        val forearmBands = if (forearmAll.size < 60) 0 else countBands(lum, warm, darkThr, skinRef * 0.8f)
        val palmStrap = palmSamples.size >= 40 &&
            palmSamples.count { luminance(it) < darkThr } >= palmSamples.size * 0.12f
        val fingerStrap = fingerSamples.size >= 30 &&
            fingerSamples.count { luminance(it) < darkThr } >= fingerSamples.size * 0.2f

        return HandEval(raised, forearmBands, palmStrap, fingerStrap)
    }

    /**
     * Each slice is DARK (mostly strap), SKIN (mostly warm bright skin) or OTHER.
     * A band is a DARK run; we need at least two of them with SKIN somewhere between.
     */
    private fun countBands(lum: Array<FloatArray>, warm: Array<BooleanArray>, darkThr: Float, skinThr: Float): Int {
        val cls = IntArray(lum.size)
        for (i in lum.indices) {
            var n = 0
            var dark = 0
            var skin = 0
            for (j in lum[i].indices) {
                val l = lum[i][j]
                if (l < 0f) continue
                n++
                if (l < darkThr) dark++ else if (l > skinThr && warm[i][j]) skin++
            }
            cls[i] = when {
                n < 5 -> 0
                dark >= n * 0.5f -> 1
                skin >= n * 0.5f -> 2
                else -> 0
            }
        }
        val seq = ArrayList<Int>()
        for (c in cls) {
            if (c == 0) continue
            if (seq.isEmpty() || seq.last() != c) seq += c
        }
        val darkRuns = seq.count { it == 1 }
        val skinRuns = seq.count { it == 2 }
        return if (skinRuns >= 1) darkRuns else 0
    }

    private fun samplePolygon(bmp: Bitmap, pts: List<Pair<Float, Float>>): List<Int> {
        val minX = pts.minOf { it.first }.coerceAtLeast(0f)
        val maxX = pts.maxOf { it.first }.coerceAtMost(bmp.width - 1f)
        val minY = pts.minOf { it.second }.coerceAtLeast(0f)
        val maxY = pts.maxOf { it.second }.coerceAtMost(bmp.height - 1f)
        if (maxX <= minX || maxY <= minY) return emptyList()
        val out = ArrayList<Int>()
        val grid = 22
        for (gy in 0 until grid) for (gx in 0 until grid) {
            val x = minX + (maxX - minX) * (gx + 0.5f) / grid
            val y = minY + (maxY - minY) * (gy + 0.5f) / grid
            if (inside(pts, x, y)) out += bmp.getPixel(x.roundToInt(), y.roundToInt())
        }
        return out
    }

    private fun inside(pts: List<Pair<Float, Float>>, x: Float, y: Float): Boolean {
        var result = false
        var j = pts.lastIndex
        for (i in pts.indices) {
            val (xi, yi) = pts[i]
            val (xj, yj) = pts[j]
            if ((yi > y) != (yj > y) && x < (xj - xi) * (y - yi) / (yj - yi + 1e-6f) + xi) result = !result
            j = i
        }
        return result
    }

    private fun sampleSegment(bmp: Bitmap, x0: Float, y0: Float, x1: Float, y1: Float, width: Float): List<Int> {
        val out = ArrayList<Int>()
        var dx = x1 - x0
        var dy = y1 - y0
        val len = hypot(dx, dy).coerceAtLeast(1f)
        dx /= len
        dy /= len
        val nx = -dy
        val ny = dx
        val along = 24
        val acrossN = 5
        for (i in 0 until along) {
            val t = len * (i + 0.5f) / along
            for (j in 0 until acrossN) {
                val off = (j - (acrossN - 1) / 2f) / ((acrossN - 1) / 2f) * width / 2f
                val x = (x0 + dx * t + nx * off).roundToInt()
                val y = (y0 + dy * t + ny * off).roundToInt()
                if (x < 0 || y < 0 || x >= bmp.width || y >= bmp.height) continue
                out += bmp.getPixel(x, y)
            }
        }
        return out
    }
}
