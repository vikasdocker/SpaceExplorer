package com.spaceexplorer

import kotlin.math.cos
import kotlin.math.sin

class OrbitalMechanics(private val planet: Planet) {

    var orbitAngleDeg:   Float = planet.initialAngleDeg; private set
    var selfRotAngleDeg: Float = 0f;                     private set
    var worldX: Float = 0f; private set
    var worldY: Float = 0f; private set
    var worldZ: Float = 0f; private set

    fun computeTransform(
        deltaSeconds: Float,
        parentX: Float = 0f,
        parentY: Float = 0f,
        parentZ: Float = 0f
    ): FloatArray {
        orbitAngleDeg   = (orbitAngleDeg   + planet.orbitSpeedDeg   * deltaSeconds) % 360f
        selfRotAngleDeg = (selfRotAngleDeg + planet.selfRotSpeedDeg * deltaSeconds) % 360f

        val oRad    = Math.toRadians(orbitAngleDeg.toDouble()).toFloat()
        val tRad    = Math.toRadians(planet.orbitTiltDeg.toDouble()).toFloat()
        val flatX   = planet.orbitRadius * sin(oRad)
        val flatZ   = planet.orbitRadius * cos(oRad)
        val cosTilt = cos(tRad)
        val sinTilt = sin(tRad)

        worldX = parentX + flatX
        worldY = parentY + flatX * sinTilt
        worldZ = parentZ + flatZ * cosTilt

        val sRad = Math.toRadians(selfRotAngleDeg.toDouble()).toFloat()
        val cosS = cos(sRad); val sinS = sin(sRad)
        val spinMat = floatArrayOf(
             cosS, 0f, -sinS, 0f,
             0f,   1f,  0f,   0f,
             sinS, 0f,  cosS, 0f,
             0f,   0f,  0f,   1f
        )

        val aTiltRad = Math.toRadians(planet.axialTiltDeg.toDouble()).toFloat()
        val cosA = cos(aTiltRad); val sinA = sin(aTiltRad)
        val tiltMat = floatArrayOf(
             cosA, sinA, 0f, 0f,
            -sinA, cosA, 0f, 0f,
             0f,   0f,   1f, 0f,
             0f,   0f,   0f, 1f
        )

        val transMat = floatArrayOf(
            1f, 0f, 0f, 0f,
            0f, 1f, 0f, 0f,
            0f, 0f, 1f, 0f,
            worldX, worldY, worldZ, 1f
        )

        val rotation  = mat4Mul(tiltMat, spinMat)
        return mat4Mul(transMat, rotation)
    }

    private fun mat4Mul(a: FloatArray, b: FloatArray): FloatArray {
        val out = FloatArray(16)
        for (col in 0 until 4) {
            for (row in 0 until 4) {
                var sum = 0f
                for (k in 0 until 4) sum += a[k * 4 + row] * b[col * 4 + k]
                out[col * 4 + row] = sum
            }
        }
        return out
    }
}
