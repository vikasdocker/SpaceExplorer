package com.spaceexplorer

import android.util.Log
import android.view.Surface
import android.view.SurfaceView
import com.google.android.filament.Camera
import com.google.android.filament.ColorGrading
import com.google.android.filament.Engine
import com.google.android.filament.EntityManager
import com.google.android.filament.Renderer
import com.google.android.filament.Scene
import com.google.android.filament.ToneMapper
import com.google.android.filament.View
import com.google.android.filament.Viewport
import com.google.android.filament.android.DisplayHelper
import com.google.android.filament.android.UiHelper

class FilamentHelper(private val surfaceView: SurfaceView) {

    companion object {
        private const val TAG = "FilamentHelper"
        fun init() { com.google.android.filament.utils.Utils.init() }
    }

    lateinit var engine:   Engine;    private set
    lateinit var renderer: Renderer;  private set
    lateinit var scene:    Scene;     private set
    lateinit var view:     View;      private set
    lateinit var camera:   Camera;    private set

    private var cameraEntity: Int           = 0
    private var swapChain:    Any?          = null
    private var colorGrading: ColorGrading? = null

    private lateinit var uiHelper:      UiHelper
    private lateinit var displayHelper: DisplayHelper

    var surfaceWidth  = 1;  private set
    var surfaceHeight = 1;  private set

    private var isRunning = false
    private var renderCallback: ((Long) -> Unit)? = null

    var currentFps    = 0;  private set
    private var lastFrameTime = 0L

    fun setRenderCallback(cb: (Long) -> Unit) { renderCallback = cb }

    fun attach() {
        Log.d(TAG, "attach()")
        engine   = Engine.create()
        renderer = engine.createRenderer()
        scene    = engine.createScene()
        view     = engine.createView()
        cameraEntity = EntityManager.get().create()
        camera       = engine.createCamera(cameraEntity)
        view.scene  = scene
        view.camera = camera

        renderer.clearOptions = Renderer.ClearOptions().apply {
            clearColor = floatArrayOf(0f, 0f, 0f, 1f)
            clear      = false
        }

        colorGrading = ColorGrading.Builder()
            .toneMapper(ToneMapper.ACES())
            .build(engine)

        view.apply {
            this.colorGrading = this@FilamentHelper.colorGrading
            renderQuality = View.RenderQuality().apply {
                hdrColorBuffer = View.QualityLevel.HIGH
            }
            multiSampleAntiAliasingOptions = View.MultiSampleAntiAliasingOptions().apply {
                enabled     = true
                sampleCount = 4
            }
            antiAliasing = View.AntiAliasing.FXAA
            ambientOcclusionOptions = View.AmbientOcclusionOptions().apply {
                enabled    = true
                radius     = 0.3f
                power      = 1.0f
                bias       = 0.0025f
                resolution = 0.5f
                intensity  = 1.0f
            }
            bloomOptions = View.BloomOptions().apply {
                enabled    = true
                strength   = 0.22f
                resolution = 512
                levels     = 7
                blendMode  = View.BloomOptions.BlendMode.ADD
            }
            dynamicResolutionOptions = View.DynamicResolutionOptions().apply {
                enabled = true
                minScale = 0.5f
                maxScale = 1.0f
            }
            dithering = View.Dithering.TEMPORAL
        }

        uiHelper = UiHelper(UiHelper.ContextErrorPolicy.DONT_CHECK)
        uiHelper.renderCallback = object : UiHelper.RendererCallback {
            override fun onNativeWindowChanged(surface: Surface) {
                (swapChain as? com.google.android.filament.SwapChain)?.let {
                    engine.destroySwapChain(it)
                }
                swapChain = engine.createSwapChain(surface)
                displayHelper.attach(renderer, surfaceView.display)
            }
            override fun onDetachedFromSurface() {
                displayHelper.detach()
                (swapChain as? com.google.android.filament.SwapChain)?.let {
                    engine.destroySwapChain(it)
                    engine.flushAndWait()
                }
                swapChain = null
            }
            override fun onResized(width: Int, height: Int) {
                surfaceWidth  = width
                surfaceHeight = height
                view.viewport = Viewport(0, 0, width, height)
                updateCameraProjection(width, height)
            }
        }

        displayHelper = DisplayHelper(surfaceView.context)
        uiHelper.attachTo(surfaceView)
        isRunning = true
        Log.d(TAG, "attach() complete")
    }

    fun updateCameraProjection(width: Int, height: Int, fovDeg: Double = 45.0) {
        camera.setProjection(
            fovDeg,
            width.toDouble() / height.toDouble(),
            0.1, 10_000.0,
            Camera.Fov.VERTICAL
        )
    }

    fun updateFov(radius: Float) {
        val fov = (20.0 + (radius / 80.0) * 35.0).coerceIn(20.0, 55.0)
        updateCameraProjection(surfaceWidth, surfaceHeight, fov)
    }

    fun setCameraPosition(
        eyeX: Float, eyeY: Float, eyeZ: Float,
        targetX: Float = 0f, targetY: Float = 0f, targetZ: Float = 0f
    ) {
        camera.lookAt(
            eyeX.toDouble(),    eyeY.toDouble(),    eyeZ.toDouble(),
            targetX.toDouble(), targetY.toDouble(), targetZ.toDouble(),
            0.0, 1.0, 0.0
        )
    }

    fun getEyePosition(): Triple<Float, Float, Float> {
        val viewMat = FloatArray(16)
        camera.getViewMatrix(viewMat)
        val x = -(viewMat[0]*viewMat[12] + viewMat[1]*viewMat[13] + viewMat[2]*viewMat[14])
        val y = -(viewMat[4]*viewMat[12] + viewMat[5]*viewMat[13] + viewMat[6]*viewMat[14])
        val z = -(viewMat[8]*viewMat[12] + viewMat[9]*viewMat[13] + viewMat[10]*viewMat[14])
        return Triple(x, y, z)
    }

    fun render(frameTimeNanos: Long, shouldRender: Boolean = true) {
        if (!isRunning) return
        val sc = swapChain as? com.google.android.filament.SwapChain ?: return
        renderCallback?.invoke(frameTimeNanos)
        if (lastFrameTime != 0L) {
            val d = frameTimeNanos - lastFrameTime
            if (d > 0) currentFps = (1_000_000_000L / d).toInt()
        }
        lastFrameTime = frameTimeNanos
        if (shouldRender) {
            if (renderer.beginFrame(sc, frameTimeNanos)) {
                renderer.render(view)
                renderer.endFrame()
            }
        }
    }

    fun pause()  { isRunning = false }
    fun resume() { isRunning = true  }

    fun destroy() {
        Log.d(TAG, "destroy()")
        isRunning = false
        uiHelper.detach()
        colorGrading?.let { engine.destroyColorGrading(it) }
        engine.destroyView(view)
        engine.destroyScene(scene)
        engine.destroyRenderer(renderer)
        engine.destroyCameraComponent(cameraEntity)
        EntityManager.get().destroy(cameraEntity)
        (swapChain as? com.google.android.filament.SwapChain)?.let {
            engine.destroySwapChain(it)
        }
        engine.flushAndWait()
        engine.destroy()
        Log.d(TAG, "destroy() complete")
    }
}
