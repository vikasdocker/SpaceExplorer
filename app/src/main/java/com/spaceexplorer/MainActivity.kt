package com.spaceexplorer

import android.os.Bundle
import android.util.Log
import android.view.Choreographer
import android.view.MotionEvent
import android.view.View
import android.view.WindowManager
import androidx.appcompat.app.AppCompatActivity
import androidx.cardview.widget.CardView
import com.spaceexplorer.databinding.ActivityMainBinding

class MainActivity : AppCompatActivity() {

    companion object {
        private const val TAG         = "SpaceExplorer"
        private const val TAP_SLOP_PX = 12f
    }

    private lateinit var binding:            ActivityMainBinding
    private lateinit var filamentHelper:     FilamentHelper
    private lateinit var skyboxManager:      SkyboxManager
    private lateinit var lightingManager:    LightingManager
    private lateinit var planetRenderer:     PlanetRenderer
    private lateinit var cameraController:   CameraController
    private lateinit var tapDetector:        PlanetTapDetector
    private lateinit var planetHighlight:    PlanetHighlight
    private lateinit var infoPanelManager:   InfoPanelManager
    private lateinit var performanceManager: PerformanceManager

    private val choreographer = Choreographer.getInstance()
    private var fpsTick       = 0
    private var prevRadius    = 0f
    private var lastFrameNs   = 0L
    private var touchDownX    = 0f
    private var touchDownY    = 0f
    private var touchMoved    = false

    private val frameCallback = object : Choreographer.FrameCallback {
        override fun doFrame(frameTimeNanos: Long) {
            if (isDestroyed) return
            val shouldRender = performanceManager.onFrame(frameTimeNanos)
            filamentHelper.render(frameTimeNanos, shouldRender)

            val r = cameraController.radius
            if (kotlin.math.abs(r - prevRadius) > 0.05f) {
                filamentHelper.updateFov(r)
                prevRadius = r
            }

            if (++fpsTick >= 30) {
                fpsTick = 0
                binding.tvFps.text       = "FPS: ${filamentHelper.currentFps}"
                binding.tvPerfStats.text = performanceManager.statsLine
            }
            choreographer.postFrameCallback(this)
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        window.addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
        @Suppress("DEPRECATION")
        window.decorView.systemUiVisibility = (
            View.SYSTEM_UI_FLAG_FULLSCREEN
                or View.SYSTEM_UI_FLAG_HIDE_NAVIGATION
                or View.SYSTEM_UI_FLAG_IMMERSIVE_STICKY
        )

        binding = ActivityMainBinding.inflate(layoutInflater)
        setContentView(binding.root)

        Log.d(TAG, "=== Space Explorer FINAL ===")
        FilamentHelper.init()

        filamentHelper = FilamentHelper(binding.surfaceView)
        filamentHelper.attach()

        performanceManager = PerformanceManager(this)
        performanceManager.configure(filamentHelper.view)

        cameraController = CameraController(filamentHelper.camera)
        cameraController.setInitialPosition(thetaDeg = 25f, phiDeg = 35f, radius = 40f)
        prevRadius = 40f

        skyboxManager = SkyboxManager(filamentHelper.engine, filamentHelper.scene)
        skyboxManager.setup()

        lightingManager = LightingManager(filamentHelper.engine, filamentHelper.scene)
        lightingManager.setup()

        planetRenderer = PlanetRenderer(filamentHelper.engine, filamentHelper.scene)
        planetRenderer.setup()
        PlanetRendererAccess.set(planetRenderer)

        tapDetector   = PlanetTapDetector(filamentHelper.camera, planetRenderer)
        planetHighlight = PlanetHighlight(filamentHelper.engine, filamentHelper.scene)

        val infoCard = binding.root.findViewById<CardView>(R.id.cardPlanetInfo)
        infoPanelManager = InfoPanelManager(
            context   = this,
            card      = infoCard,
            onDismiss = { planetHighlight.select(null) }
        )

        filamentHelper.setRenderCallback { frameTimeNanos ->
            val deltaSec = if (lastFrameNs == 0L) 0.016f
                           else ((frameTimeNanos - lastFrameNs) / 1_000_000_000f).coerceIn(0.001f, 0.1f)
            lastFrameNs = frameTimeNanos
            cameraController.update()
            val eye = filamentHelper.getEyePosition()
            planetRenderer.cameraX = eye.first
            planetRenderer.cameraY = eye.second
            planetRenderer.cameraZ = eye.third
            planetRenderer.update(frameTimeNanos)
            planetHighlight.update(deltaSec)
        }

        binding.btnZoomIn.setOnClickListener  { cameraController.zoom(2.5f)  }
        binding.btnZoomOut.setOnClickListener { cameraController.zoom(-2.5f) }
        binding.btnReset.setOnClickListener   {
            cameraController.reset()
            cameraController.setInitialPosition(thetaDeg = 25f, phiDeg = 35f, radius = 40f)
            infoPanelManager.dismiss()
            planetHighlight.select(null)
        }

        binding.surfaceView.setOnTouchListener { _, event -> handleTouch(event) }

        binding.tvHint.postDelayed({
            binding.tvHint.animate().alpha(0f).setDuration(1000)
                .withEndAction { binding.tvHint.visibility = View.GONE }.start()
        }, 5000)

        binding.surfaceView.post {
            binding.progressBar.visibility = View.GONE
            Log.d(TAG, "🚀 Space Explorer READY — GPU: ${performanceManager.gpuTier} | ${performanceManager.targetFps}fps")
        }
    }

    private fun handleTouch(event: MotionEvent): Boolean {
        when (event.actionMasked) {
            MotionEvent.ACTION_DOWN -> {
                touchDownX = event.x; touchDownY = event.y; touchMoved = false
            }
            MotionEvent.ACTION_MOVE -> {
                val dx = event.x - touchDownX; val dy = event.y - touchDownY
                if (dx*dx + dy*dy > TAP_SLOP_PX * TAP_SLOP_PX) touchMoved = true
            }
            MotionEvent.ACTION_UP -> {
                if (!touchMoved && event.pointerCount == 1) onTap(event.x, event.y)
            }
        }
        return cameraController.onTouchEvent(event)
    }

    private fun onTap(tapX: Float, tapY: Float) {
        val w = filamentHelper.surfaceWidth; val h = filamentHelper.surfaceHeight
        if (w == 0 || h == 0) return
        val hit = tapDetector.detectTap(tapX, tapY, w, h)
        if (hit != null) {
            planetHighlight.select(hit)
            runOnUiThread { infoPanelManager.show(hit) }
        } else if (infoPanelManager.isVisible()) {
            runOnUiThread { infoPanelManager.dismiss() }
            planetHighlight.select(null)
        }
    }

    override fun onTouchEvent(event: MotionEvent): Boolean {
        return cameraController.onTouchEvent(event) || super.onTouchEvent(event)
    }

    override fun onResume() {
        super.onResume()
        filamentHelper.resume()
        choreographer.postFrameCallback(frameCallback)
    }

    override fun onPause() {
        super.onPause()
        choreographer.removeFrameCallback(frameCallback)
        filamentHelper.pause()
    }

    override fun onDestroy() {
        super.onDestroy()
        choreographer.removeFrameCallback(frameCallback)
        if (::planetHighlight.isInitialized)   planetHighlight.destroy()
        if (::planetRenderer.isInitialized)    planetRenderer.destroy()
        if (::skyboxManager.isInitialized)     skyboxManager.destroy()
        if (::lightingManager.isInitialized)   lightingManager.destroy()
        if (::filamentHelper.isInitialized)    filamentHelper.destroy()
        Log.d(TAG, "All resources destroyed")
    }
}
