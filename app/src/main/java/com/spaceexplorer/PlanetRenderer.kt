package com.spaceexplorer

import android.util.Log
import com.google.android.filament.Engine
import com.google.android.filament.EntityManager
import com.google.android.filament.LightManager
import com.google.android.filament.Scene
import com.google.android.filament.gltfio.AssetLoader
import com.google.android.filament.gltfio.FilamentAsset
import com.google.android.filament.gltfio.ResourceLoader
import com.google.android.filament.gltfio.UbershaderProvider

class PlanetRenderer(
    private val engine: Engine,
    private val scene:  Scene
) {
    companion object { private const val TAG = "PlanetRenderer" }

    private val ubershader     = UbershaderProvider(engine)
    private val assetLoader    = AssetLoader(engine, ubershader, EntityManager.get())
    private val resourceLoader = ResourceLoader(engine)
    private val lodManager     = LodManager()

    private data class PlanetEntry(
        val planet:     Planet,
        val mechanics:  OrbitalMechanics,
        var asset:      FilamentAsset,
        var rootEntity: Int
    )

    private val entries        = mutableListOf<PlanetEntry>()
    private val worldPositions = mutableMapOf<String, Triple<Float, Float, Float>>()
    private var sunLightEntity: Int   = 0
    private var lastFrameTimeNs: Long = 0L
    private var lodCheckCounter: Int  = 0

    var cameraX = 0f; var cameraY = 0f; var cameraZ = 28f

    fun setup() {
        Planet.ALL.forEach { planet ->
            loadPlanet(planet, lodManager.initialLod(planet))?.let { entries.add(it) }
        }
        setupSunPointLight()
        Log.d(TAG, "Setup complete — ${entries.size} bodies")
    }

    fun update(frameTimeNs: Long) {
        val deltaMs = if (lastFrameTimeNs == 0L) 16.67f
                      else ((frameTimeNs - lastFrameTimeNs) / 1_000_000f).coerceIn(1f, 100f)
        lastFrameTimeNs = frameTimeNs
        val deltaSec    = deltaMs / 1000f

        for (entry in entries) {
            val parentPos = entry.planet.parentId?.let { worldPositions[it] }
            val matrix = entry.mechanics.computeTransform(
                deltaSec,
                parentPos?.first  ?: 0f,
                parentPos?.second ?: 0f,
                parentPos?.third  ?: 0f
            )
            val tm       = engine.transformManager
            val instance = tm.getInstance(entry.rootEntity)
            if (instance != 0) tm.setTransform(instance, matrix)

            worldPositions[entry.planet.id] = Triple(
                entry.mechanics.worldX,
                entry.mechanics.worldY,
                entry.mechanics.worldZ
            )
        }

        moveSunPointLight()

        lodCheckCounter++
        if (lodCheckCounter >= 60) {
            lodCheckCounter = 0
            evaluateLod()
        }
    }

    fun getWorldPosition(id: String): Triple<Float, Float, Float>? = worldPositions[id]
    fun getAllPlanets(): List<Planet> = Planet.ALL

    fun destroy() {
        Log.d(TAG, "Destroying PlanetRenderer")
        entries.forEach { entry ->
            scene.removeEntities(entry.asset.entities)
            assetLoader.destroyAsset(entry.asset)
        }
        entries.clear()
        if (sunLightEntity != 0) {
            scene.remove(sunLightEntity)
            engine.lightManager.destroy(sunLightEntity)
            EntityManager.get().destroy(sunLightEntity)
            sunLightEntity = 0
        }
        resourceLoader.destroy()
        assetLoader.destroy()
        ubershader.destroyMaterials()
        Log.d(TAG, "PlanetRenderer destroyed")
    }

    private fun evaluateLod() {
        for (entry in entries) {
            val pos = worldPositions[entry.planet.id] ?: continue
            val newLod = lodManager.evaluate(
                planet  = entry.planet,
                planetX = pos.first,  planetY = pos.second,  planetZ = pos.third,
                cameraX = cameraX,    cameraY = cameraY,     cameraZ = cameraZ
            ) ?: continue
            rebuildPlanetMesh(entry, newLod)
        }
    }

    private fun rebuildPlanetMesh(entry: PlanetEntry, lod: LodManager.LodLevel) {
        Log.d(TAG, "Rebuilding ${entry.planet.id} at LOD ${lod.level}")
        val pos = worldPositions[entry.planet.id]
        scene.removeEntities(entry.asset.entities)
        assetLoader.destroyAsset(entry.asset)
        val newAsset = loadAsset(entry.planet, lod) ?: return
        entry.asset      = newAsset
        entry.rootEntity = newAsset.root
        if (pos != null) {
            val tm       = engine.transformManager
            val instance = tm.getInstance(newAsset.root)
            if (instance != 0) {
                tm.setTransform(instance, floatArrayOf(
                    1f, 0f, 0f, 0f,
                    0f, 1f, 0f, 0f,
                    0f, 0f, 1f, 0f,
                    pos.first, pos.second, pos.third, 1f
                ))
            }
        }
    }

    private fun loadPlanet(planet: Planet, lod: LodManager.LodLevel): PlanetEntry? {
        val asset = loadAsset(planet, lod) ?: return null
        return PlanetEntry(
            planet     = planet,
            mechanics  = OrbitalMechanics(planet),
            asset      = asset,
            rootEntity = asset.root
        )
    }

    private fun loadAsset(planet: Planet, lod: LodManager.LodLevel): FilamentAsset? {
        val glb   = SphereGenerator.generateGlbLod(planet, lod)
        val asset = assetLoader.createAsset(glb)
        if (asset == null) {
            Log.e(TAG, "Failed to load ${planet.id}")
            return null
        }
        resourceLoader.loadResources(asset)
        asset.releaseSourceData()

        val rm    = engine.renderableManager
        val isSun = planet.id == "sun"
        for (entity in asset.entities) {
            val inst = rm.getInstance(entity)
            if (inst != 0) {
                rm.setCastShadows(inst, !isSun)
                rm.setReceiveShadows(inst, !isSun)
                if (!isSun) rm.setScreenSpaceContactShadows(inst, true)
            }
        }

        val tm = engine.transformManager
        if (tm.getInstance(asset.root) == 0) tm.create(asset.root)
        scene.addEntities(asset.entities)
        Log.d(TAG, "Loaded ${planet.id} LOD${lod.level}")
        return asset
    }

    private fun setupSunPointLight() {
        sunLightEntity = EntityManager.get().create()
        LightManager.Builder(LightManager.Type.POINT)
            .color(1.0f, 0.95f, 0.80f)
            .intensity(2_000_000f)
            .falloff(80f)
            .castShadows(false)
            .build(engine, sunLightEntity)
        scene.addEntity(sunLightEntity)
    }

    private fun moveSunPointLight() {
        if (sunLightEntity == 0) return
        val p = worldPositions["sun"] ?: return
        val i = engine.lightManager.getInstance(sunLightEntity)
        if (i != 0) engine.lightManager.setPosition(i, p.first, p.second, p.third)
    }
}
