package com.spaceexplorer

import android.util.Log
import com.google.android.filament.Engine
import com.google.android.filament.EntityManager
import com.google.android.filament.LightManager
import com.google.android.filament.Scene
import kotlin.math.cos
import kotlin.math.sin

class LightingManager(
    private val engine: Engine,
    private val scene:  Scene
) {
    companion object {
        private const val TAG            = "LightingManager"
        private const val SUN_INTENSITY  = 110_000f
        private const val FILL_INTENSITY = 800f
    }

    private var sunEntity:       Int = 0
    private var fillLightEntity: Int = 0

    fun setup() {
        setupSun()
        setupFillLight()
        Log.d(TAG, "Lighting setup complete")
    }

    fun updateSunDirection(angleDeg: Float) {
        if (sunEntity == 0) return
        val rad = Math.toRadians(angleDeg.toDouble()).toFloat()
        engine.lightManager.setDirection(
            engine.lightManager.getInstance(sunEntity),
            -cos(rad), -0.50f, -sin(rad)
        )
    }

    fun destroy() {
        Log.d(TAG, "Destroying LightingManager")
        if (sunEntity != 0) {
            scene.remove(sunEntity)
            engine.lightManager.destroy(sunEntity)
            EntityManager.get().destroy(sunEntity)
            sunEntity = 0
        }
        if (fillLightEntity != 0) {
            scene.remove(fillLightEntity)
            engine.lightManager.destroy(fillLightEntity)
            EntityManager.get().destroy(fillLightEntity)
            fillLightEntity = 0
        }
        Log.d(TAG, "LightingManager destroyed")
    }

    private fun setupSun() {
        sunEntity = EntityManager.get().create()
        LightManager.Builder(LightManager.Type.SUN)
            .color(1.0f, 0.975f, 0.85f)
            .intensity(SUN_INTENSITY)
            .direction(-0.70f, -0.55f, -0.40f)
            .castShadows(true)
            .shadowOptions(LightManager.ShadowOptions().apply {
                mapSize        = 2048
                shadowCascades = 1
            })
            .sunAngularRadius(0.545f)
            .sunHaloSize(10.0f)
            .sunHaloFalloff(80.0f)
            .build(engine, sunEntity)
        scene.addEntity(sunEntity)
        Log.d(TAG, "Sun light ready")
    }

    private fun setupFillLight() {
        fillLightEntity = EntityManager.get().create()
        LightManager.Builder(LightManager.Type.DIRECTIONAL)
            .color(0.30f, 0.42f, 0.90f)
            .intensity(FILL_INTENSITY)
            .direction(0.70f, 0.30f, 0.55f)
            .castShadows(false)
            .build(engine, fillLightEntity)
        scene.addEntity(fillLightEntity)
        Log.d(TAG, "Fill light ready")
    }
}
