package com.spaceexplorer

import android.util.Log
import com.google.android.filament.Box
import com.google.android.filament.Engine
import com.google.android.filament.EntityManager
import com.google.android.filament.IndexBuffer
import com.google.android.filament.RenderableManager
import com.google.android.filament.Scene
import com.google.android.filament.VertexBuffer
import com.google.android.filament.gltfio.AssetLoader
import com.google.android.filament.gltfio.UbershaderProvider
import java.nio.ByteBuffer
import java.nio.ByteOrder
import kotlin.math.PI
import kotlin.math.cos
import kotlin.math.sin

class PlanetHighlight(
    private val engine: Engine,
    private val scene:  Scene
) {
    companion object {
        private const val TAG        = "PlanetHighlight"
        private const val SEGMENTS   = 128
        private const val RING_SCALE = 1.40f
        private const val PULSE_SPEED = 2.5f
    }

    private var ringEntity:     Int           = 0
    private var vertexBuffer:   VertexBuffer? = null
    private var indexBuffer:    IndexBuffer?  = null
    private var selectedPlanet: Planet?       = null
    private var pulseTime:      Float         = 0f
    private var isVisible:      Boolean       = false

    private val ubershader  = UbershaderProvider(engine)
    private val assetLoader = AssetLoader(engine, ubershader, EntityManager.get())

    fun select(planet: Planet?) {
        if (planet == selectedPlanet) return
        hideRing()
        selectedPlanet = planet
        if (planet != null) {
            buildRing(planet)
            isVisible = true
            Log.d(TAG, "Highlight ring → ${planet.displayName}")
        }
    }

    fun update(deltaSeconds: Float) {
        if (!isVisible || ringEntity == 0) return
        val planet = selectedPlanet ?: return
        pulseTime  = (pulseTime + PULSE_SPEED * deltaSeconds) % (2f * PI.toFloat())

        val pos = PlanetRendererAccess.getWorldPosition(planet.id)
        if (pos != null) {
            val tm       = engine.transformManager
            val instance = tm.getInstance(ringEntity)
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

    fun destroy() {
        Log.d(TAG, "Destroying PlanetHighlight")
        hideRing()
        assetLoader.destroy()
        ubershader.destroyMaterials()
        Log.d(TAG, "PlanetHighlight destroyed")
    }

    private fun buildRing(planet: Planet) {
        val ringRadius = planet.radius * RING_SCALE
        val vertCount  = SEGMENTS + 1

        val posData = ByteBuffer.allocateDirect(vertCount * 3 * Float.SIZE_BYTES)
            .order(ByteOrder.LITTLE_ENDIAN)
        for (i in 0..SEGMENTS) {
            val angle = (2.0 * PI * i / SEGMENTS).toFloat()
            posData.putFloat(cos(angle) * ringRadius)
            posData.putFloat(0f)
            posData.putFloat(sin(angle) * ringRadius)
        }
        posData.rewind()

        val idxData = ByteBuffer.allocateDirect(vertCount * Short.SIZE_BYTES)
            .order(ByteOrder.LITTLE_ENDIAN)
        for (i in 0..SEGMENTS) idxData.putShort(i.toShort())
        idxData.rewind()

        vertexBuffer = VertexBuffer.Builder()
            .vertexCount(vertCount).bufferCount(1)
            .attribute(VertexBuffer.VertexAttribute.POSITION, 0,
                VertexBuffer.AttributeType.FLOAT3, 0, 3 * Float.SIZE_BYTES)
            .build(engine)
        vertexBuffer!!.setBufferAt(engine, 0, posData)

        indexBuffer = IndexBuffer.Builder()
            .indexCount(vertCount)
            .bufferType(IndexBuffer.Builder.IndexType.USHORT)
            .build(engine)
        indexBuffer!!.setBuffer(engine, idxData)

        ringEntity = EntityManager.get().create()

        val (er, eg, eb) = ringColour(planet)
        val mi = buildMaterialInstance(er, eg, eb)

        RenderableManager.Builder(1)
            .boundingBox(Box(0f, 0f, 0f, ringRadius, 0.01f, ringRadius))
            .geometry(0, RenderableManager.PrimitiveType.LINE_STRIP,
                vertexBuffer!!, indexBuffer!!, 0, vertCount)
            .material(0, mi)
            .castShadows(false).receiveShadows(false).culling(false)
            .build(engine, ringEntity)

        engine.transformManager.create(ringEntity)
        scene.addEntity(ringEntity)
    }

    private fun hideRing() {
        if (ringEntity != 0) {
            scene.remove(ringEntity)
            engine.renderableManager.destroy(ringEntity)
            engine.transformManager.destroy(ringEntity)
            EntityManager.get().destroy(ringEntity)
            ringEntity = 0
        }
        vertexBuffer?.let { engine.destroyVertexBuffer(it); vertexBuffer = null }
        indexBuffer?.let  { engine.destroyIndexBuffer(it);  indexBuffer  = null }
        isVisible = false
    }

    private fun ringColour(planet: Planet): Triple<Float, Float, Float> = when (planet.id) {
        "sun"   -> Triple(1.0f, 0.8f, 0.1f)
        "earth" -> Triple(0.1f, 0.8f, 1.0f)
        "moon"  -> Triple(0.8f, 0.8f, 0.8f)
        "mars"  -> Triple(1.0f, 0.3f, 0.1f)
        else    -> Triple(0.5f, 1.0f, 0.5f)
    }

    private fun buildMaterialInstance(er: Float, eg: Float, eb: Float)
            : com.google.android.filament.MaterialInstance {
        val glb   = SphereGenerator.generateGlb(
            radius=0.001f, stacks=2, slices=2,
            r=0f, g=0f, b=0f, roughness=1f, metallic=0f,
            emissiveR=er*2f, emissiveG=eg*2f, emissiveB=eb*2f
        )
        val asset = assetLoader.createAsset(glb)!!
        val rm    = engine.renderableManager
        val mi    = if (asset.entities.isNotEmpty()) {
            val inst = rm.getInstance(asset.entities[0])
            if (inst != 0) rm.getMaterialInstanceAt(inst, 0) else null
        } else null
        assetLoader.destroyAsset(asset)
        return mi ?: run {
            val fallbackGlb = SphereGenerator.generateGlb(
                radius=0.001f, stacks=2, slices=2, r=er, g=eg, b=eb,
                roughness=0.5f, metallic=0f
            )
            val fallback = assetLoader.createAsset(fallbackGlb)!!
            val fInst = rm.getInstance(fallback.entities[0])
            val fMi   = rm.getMaterialInstanceAt(fInst, 0)
            assetLoader.destroyAsset(fallback)
            fMi
        }
    }
}

object PlanetRendererAccess {
    private var renderer: PlanetRenderer? = null
    fun set(r: PlanetRenderer) { renderer = r }
    fun getWorldPosition(id: String): Triple<Float, Float, Float>? = renderer?.getWorldPosition(id)
}
