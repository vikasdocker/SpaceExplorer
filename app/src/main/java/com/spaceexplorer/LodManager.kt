package com.spaceexplorer

import android.util.Log
import kotlin.math.sqrt

class LodManager {

    companion object {
        private const val TAG = "LodManager"
        private val LOD_DISTANCES = floatArrayOf(3f, 8f, 20f, 50f, Float.MAX_VALUE)
        private val LOD_GEOMETRY  = arrayOf(
            Pair(64, 64), Pair(48, 48), Pair(32, 32), Pair(16, 16), Pair(8, 8)
        )
    }

    data class LodLevel(val level: Int, val stacks: Int, val slices: Int)

    private val currentLod = mutableMapOf<String, Int>()

    fun evaluate(
        planet:  Planet,
        planetX: Float, planetY: Float, planetZ: Float,
        cameraX: Float, cameraY: Float, cameraZ: Float
    ): LodLevel? {
        val dx   = planetX - cameraX
        val dy   = planetY - cameraY
        val dz   = planetZ - cameraZ
        val dist = (sqrt(dx*dx + dy*dy + dz*dz) - planet.radius).coerceAtLeast(0f)

        val lodIndex = LOD_DISTANCES.indexOfFirst { dist <= it }
            .let { if (it < 0) LOD_DISTANCES.lastIndex else it }

        val previous = currentLod[planet.id]
        return if (previous != lodIndex) {
            currentLod[planet.id] = lodIndex
            val (stacks, slices) = LOD_GEOMETRY[lodIndex]
            Log.d(TAG, "${planet.id}: LOD $previous → $lodIndex (dist=${"%.1f".format(dist)}, ${stacks}x${slices})")
            LodLevel(lodIndex, stacks, slices)
        } else null
    }

    fun reset() { currentLod.clear() }

    fun initialLod(planet: Planet): LodLevel {
        val (stacks, slices) = LOD_GEOMETRY[2]
        currentLod[planet.id] = 2
        return LodLevel(2, stacks, slices)
    }
}
