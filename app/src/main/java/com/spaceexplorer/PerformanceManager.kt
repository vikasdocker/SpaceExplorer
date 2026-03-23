package com.spaceexplorer

import android.app.ActivityManager
import android.content.Context
import android.os.Build
import android.util.Log
import android.view.WindowManager
import com.google.android.filament.View

class PerformanceManager(private val context: Context) {

    companion object {
        private const val TAG           = "PerformanceManager"
        private const val FPS_LOW       = 30
        private const val FPS_MID       = 60
        private const val FPS_HIGH      = 90
        private const val SCALE_ULTRA   = 1.0f
        private const val SCALE_HIGH    = 0.85f
        private const val SCALE_MID     = 0.70f
        private const val SCALE_LOW     = 0.55f
        private const val DROP_RATIO    = 0.80f
        private const val RISE_RATIO    = 0.95f
        private const val SAMPLE_WINDOW = 90
    }

    enum class GpuTier { LOW, MID, HIGH }

    val gpuTier: GpuTier by lazy { detectGpuTier() }

    var targetFps: Int = FPS_MID; private set

    private var frameIntervalNs: Long = 1_000_000_000L / FPS_MID
    private var lastRenderNs:    Long = 0L
    private var currentScaleIndex = 2
    private val qualityLevels     = listOf(SCALE_LOW, SCALE_MID, SCALE_HIGH, SCALE_ULTRA)
    private var filamentView: View? = null
    private var sampleFrameCount  = 0
    private var sampleStartNs     = 0L
    private var measuredFps       = 0f
    private var slowSeconds       = 0
    private var throttleActive    = false

    var statsLine: String = ""; private set

    fun configure(view: View) {
        filamentView = view
        val wm = context.getSystemService(Context.WINDOW_SERVICE) as WindowManager
        val refreshHz: Float = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
            context.display?.refreshRate ?: 60f
        } else {
            @Suppress("DEPRECATION")
            wm.defaultDisplay.refreshRate
        }
        targetFps = when {
            refreshHz >= 110f -> FPS_HIGH
            refreshHz >= 55f  -> FPS_MID
            else              -> FPS_LOW
        }
        frameIntervalNs = 1_000_000_000L / targetFps
        currentScaleIndex = when (gpuTier) {
            GpuTier.HIGH -> 3
            GpuTier.MID  -> 2
            GpuTier.LOW  -> 1
        }
        applyQualityLevel(currentScaleIndex)
        Log.d(TAG, "GPU: $gpuTier | Target FPS: $targetFps | Scale: ${qualityLevels[currentScaleIndex]}")
    }

    fun onFrame(nowNs: Long): Boolean {
        val elapsed = nowNs - lastRenderNs
        if (elapsed < frameIntervalNs - 500_000L) return false
        lastRenderNs = nowNs

        if (sampleStartNs == 0L) sampleStartNs = nowNs
        sampleFrameCount++

        if (sampleFrameCount >= SAMPLE_WINDOW) {
            val windowNs = nowNs - sampleStartNs
            measuredFps  = sampleFrameCount * 1_000_000_000f / windowNs
            adaptQuality(measuredFps)
            sampleFrameCount = 0
            sampleStartNs    = nowNs
        }

        if (sampleFrameCount % 30 == 0) statsLine = buildStatsLine()
        return true
    }

    private fun adaptQuality(fps: Float) {
        val dropThreshold = targetFps * DROP_RATIO
        val riseThreshold = targetFps * RISE_RATIO
        when {
            fps < dropThreshold && currentScaleIndex > 0 -> {
                slowSeconds++
                if (slowSeconds >= 3 || throttleActive) {
                    currentScaleIndex--
                    applyQualityLevel(currentScaleIndex)
                    throttleActive = true
                    slowSeconds    = 0
                    Log.d(TAG, "Quality DOWN → ${qualityLevels[currentScaleIndex]}")
                }
            }
            fps > riseThreshold && currentScaleIndex < qualityLevels.lastIndex && !throttleActive -> {
                currentScaleIndex++
                applyQualityLevel(currentScaleIndex)
                slowSeconds = 0
                Log.d(TAG, "Quality UP → ${qualityLevels[currentScaleIndex]}")
            }
            fps > riseThreshold -> {
                slowSeconds    = 0
                throttleActive = false
            }
        }
    }

    private fun applyQualityLevel(index: Int) {
        val scale = qualityLevels[index]
        filamentView?.apply {
            renderQuality = View.RenderQuality().apply {
                hdrColorBuffer = when {
                    scale >= SCALE_ULTRA -> View.QualityLevel.ULTRA
                    scale >= SCALE_HIGH  -> View.QualityLevel.HIGH
                    scale >= SCALE_MID   -> View.QualityLevel.MEDIUM
                    else                 -> View.QualityLevel.LOW
                }
            }
        }
    }

    private fun detectGpuTier(): GpuTier {
        val am    = context.getSystemService(Context.ACTIVITY_SERVICE) as ActivityManager
        val ramMb = am.memoryClass
        return when {
            ramMb >= 512 -> GpuTier.HIGH
            ramMb >= 256 -> GpuTier.MID
            else         -> GpuTier.LOW
        }.also { Log.d(TAG, "GPU tier $it (RAM=${ramMb}MB)") }
    }

    private fun buildStatsLine(): String {
        val scale    = qualityLevels[currentScaleIndex]
        val scaleStr = when (scale) {
            SCALE_ULTRA -> "Ultra"; SCALE_HIGH -> "High"
            SCALE_MID   -> "Med";  else -> "Low"
        }
        return "FPS: ${measuredFps.toInt()}/${targetFps}  Quality: $scaleStr${if (throttleActive) " 🌡" else ""}  GPU: $gpuTier"
    }

    fun forceQuality(index: Int) {
        currentScaleIndex = index.coerceIn(0, qualityLevels.lastIndex)
        applyQualityLevel(currentScaleIndex)
        throttleActive = false
    }
}
