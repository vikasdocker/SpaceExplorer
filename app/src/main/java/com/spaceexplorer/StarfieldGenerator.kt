package com.spaceexplorer

import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.RadialGradient
import android.graphics.Shader
import kotlin.math.PI
import kotlin.math.cos
import kotlin.math.min
import kotlin.math.sin
import kotlin.random.Random

object StarfieldGenerator {

    private const val FACE_SIZE = 512

    fun generateFaces(seed: Long = 42L): Array<Bitmap> {
        return Array(6) { face -> generateFace(face, seed) }
    }

    private fun generateFace(faceIndex: Int, seed: Long): Bitmap {
        val bmp    = Bitmap.createBitmap(FACE_SIZE, FACE_SIZE, Bitmap.Config.ARGB_8888)
        val canvas = Canvas(bmp)
        val paint  = Paint(Paint.ANTI_ALIAS_FLAG)
        canvas.drawColor(Color.BLACK)
        drawMilkyWay(canvas, paint, faceIndex, seed)
        drawNebulae(canvas, paint, faceIndex, seed)
        drawStars(canvas, paint, faceIndex, seed, count = 1800, minSize = 0.4f, maxSize = 1.2f)
        drawStars(canvas, paint, faceIndex, seed + 1, count = 120, minSize = 1.4f, maxSize = 2.8f)
        drawHeroStars(canvas, paint, faceIndex, seed)
        return bmp
    }

    private fun drawMilkyWay(canvas: Canvas, paint: Paint, face: Int, seed: Long) {
        val rng  = Random(seed + face * 17L)
        val size = FACE_SIZE.toFloat()
        for (i in 0 until 14) {
            val t      = i.toFloat() / 14
            val cx     = size * t
            val cy     = size * (0.35f + 0.30f * sin(t * PI.toFloat() * 2f)) + rng.nextFloat() * size * 0.08f
            val radius = size * (0.18f + rng.nextFloat() * 0.12f)
            val alpha  = (18 + rng.nextInt(22)).toFloat() / 255f
            val milkyColour = when (rng.nextInt(3)) {
                0    -> Color.argb((alpha * 255).toInt(), 180, 190, 220)
                1    -> Color.argb((alpha * 255).toInt(), 160, 140, 200)
                else -> Color.argb((alpha * 255).toInt(), 200, 195, 215)
            }
            paint.shader = RadialGradient(cx, cy, radius, milkyColour, Color.TRANSPARENT, Shader.TileMode.CLAMP)
            paint.alpha  = 255
            canvas.drawCircle(cx, cy, radius, paint)
        }
        paint.shader = null
    }

    private fun drawNebulae(canvas: Canvas, paint: Paint, face: Int, seed: Long) {
        val rng  = Random(seed + face * 31L + 9999L)
        val size = FACE_SIZE.toFloat()
        val count = 2 + rng.nextInt(3)
        for (i in 0 until count) {
            val cx    = rng.nextFloat() * size
            val cy    = rng.nextFloat() * size
            val r     = size * (0.10f + rng.nextFloat() * 0.15f)
            val alpha = (8 + rng.nextInt(14))
            val col   = when (rng.nextInt(5)) {
                0    -> Color.argb(alpha, 220, 60,  60)
                1    -> Color.argb(alpha, 60,  90,  220)
                2    -> Color.argb(alpha, 160, 60,  220)
                3    -> Color.argb(alpha, 60,  180, 160)
                else -> Color.argb(alpha, 220, 160, 60)
            }
            paint.shader = RadialGradient(cx, cy, r, col, Color.TRANSPARENT, Shader.TileMode.CLAMP)
            canvas.drawCircle(cx, cy, r, paint)
        }
        paint.shader = null
    }

    private fun drawStars(
        canvas: Canvas, paint: Paint, face: Int, seed: Long,
        count: Int, minSize: Float, maxSize: Float
    ) {
        val rng  = Random(seed + face * 7919L)
        val size = FACE_SIZE.toFloat()
        for (i in 0 until count) {
            val x          = rng.nextFloat() * size
            val y          = rng.nextFloat() * size
            val radius     = minSize + rng.nextFloat() * (maxSize - minSize)
            val brightness = 140 + rng.nextInt(115)
            paint.shader   = null
            paint.color    = stellarColour(rng, brightness)
            paint.alpha    = brightness
            canvas.drawCircle(x, y, radius, paint)
        }
    }

    private fun stellarColour(rng: Random, brightness: Int): Int {
        return when (rng.nextInt(10)) {
            in 0..5  -> Color.argb(brightness,
                min(255, brightness + 10), min(255, brightness + 5),
                min(255, brightness - 20).coerceAtLeast(0))
            in 6..8  -> Color.argb(brightness,
                min(255, brightness - 30).coerceAtLeast(0),
                min(255, brightness - 10).coerceAtLeast(0), 255)
            else     -> Color.argb(brightness, 255,
                min(255, brightness - 40).coerceAtLeast(0),
                min(255, brightness - 80).coerceAtLeast(0))
        }
    }

    private fun drawHeroStars(canvas: Canvas, paint: Paint, face: Int, seed: Long) {
        val rng   = Random(seed + face * 1337L)
        val size  = FACE_SIZE.toFloat()
        val count = 3 + rng.nextInt(3)
        for (i in 0 until count) {
            val cx         = 30f + rng.nextFloat() * (size - 60f)
            val cy         = 30f + rng.nextFloat() * (size - 60f)
            val coreRadius = 2.5f + rng.nextFloat() * 2.0f
            val glowRadius = coreRadius * 5f
            val coreColour = stellarColour(rng, 255)
            paint.shader   = RadialGradient(
                cx, cy, glowRadius,
                intArrayOf(Color.WHITE, coreColour, Color.TRANSPARENT),
                floatArrayOf(0f, 0.25f, 1f), Shader.TileMode.CLAMP
            )
            canvas.drawCircle(cx, cy, glowRadius, paint)
            paint.shader      = null
            val spikeLen      = coreRadius * 12f
            paint.strokeWidth = 0.7f
            paint.style       = Paint.Style.STROKE
            for (angle in listOf(0f, 90f)) {
                val rad = Math.toRadians(angle.toDouble()).toFloat()
                val dx  = cos(rad) * spikeLen; val dy = sin(rad) * spikeLen
                for (s in 0 until 8) {
                    val t = s.toFloat() / 8
                    val a = (120 * (1f - t)).toInt()
                    paint.color = Color.argb(a, 200, 210, 255)
                    canvas.drawLine(cx - dx*t, cy - dy*t, cx - dx*(t+0.125f), cy - dy*(t+0.125f), paint)
                    canvas.drawLine(cx + dx*t, cy + dy*t, cx + dx*(t+0.125f), cy + dy*(t+0.125f), paint)
                }
            }
            paint.style = Paint.Style.FILL
        }
        paint.shader = null
    }
}
