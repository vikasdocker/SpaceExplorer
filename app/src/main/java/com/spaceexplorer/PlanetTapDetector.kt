package com.spaceexplorer

import android.util.Log
import com.google.android.filament.Camera
import kotlin.math.abs
import kotlin.math.sqrt

class PlanetTapDetector(
    private val camera:         Camera,
    private val planetRenderer: PlanetRenderer
) {
    companion object { private const val TAG = "PlanetTapDetector" }

    fun detectTap(tapX: Float, tapY: Float, screenW: Int, screenH: Int): Planet? {
        val ndcX =  (tapX / screenW) * 2f - 1f
        val ndcY = -((tapY / screenH) * 2f - 1f)

        val proj    = DoubleArray(16); camera.getProjectionMatrix(proj)
        val view    = DoubleArray(16); camera.getViewMatrix(view)
        val invProj = DoubleArray(16)
        if (!invertMat4(proj, invProj)) return null

        val rayViewNear = transformVec4(invProj, doubleArrayOf(ndcX.toDouble(), ndcY.toDouble(), -1.0, 1.0))
        val rayViewFar  = transformVec4(invProj, doubleArrayOf(ndcX.toDouble(), ndcY.toDouble(),  1.0, 1.0))
        val rnX = rayViewNear[0] / rayViewNear[3]; val rnY = rayViewNear[1] / rayViewNear[3]; val rnZ = rayViewNear[2] / rayViewNear[3]
        val rfX = rayViewFar[0]  / rayViewFar[3];  val rfY = rayViewFar[1]  / rayViewFar[3];  val rfZ = rayViewFar[2]  / rayViewFar[3]

        val invView = DoubleArray(16)
        if (!invertMat4(view, invView)) return null

        val worldNear = transformVec4(invView, doubleArrayOf(rnX, rnY, rnZ, 1.0))
        val worldFar  = transformVec4(invView, doubleArrayOf(rfX, rfY, rfZ, 1.0))

        val originX = worldNear[0] / worldNear[3]
        val originY = worldNear[1] / worldNear[3]
        val originZ = worldNear[2] / worldNear[3]

        var dirX = worldFar[0] / worldFar[3] - originX
        var dirY = worldFar[1] / worldFar[3] - originY
        var dirZ = worldFar[2] / worldFar[3] - originZ
        val len  = sqrt(dirX*dirX + dirY*dirY + dirZ*dirZ)
        if (len < 1e-6) return null
        dirX /= len; dirY /= len; dirZ /= len

        var bestT: Double = Double.MAX_VALUE
        var bestPlanet: Planet? = null

        for (planet in planetRenderer.getAllPlanets()) {
            val pos = planetRenderer.getWorldPosition(planet.id) ?: continue
            val ocX = originX - pos.first.toDouble()
            val ocY = originY - pos.second.toDouble()
            val ocZ = originZ - pos.third.toDouble()
            val a   = dirX*dirX + dirY*dirY + dirZ*dirZ
            val b   = 2.0 * (ocX*dirX + ocY*dirY + ocZ*dirZ)
            val r   = planet.radius.toDouble() * 1.15
            val c   = ocX*ocX + ocY*ocY + ocZ*ocZ - r*r
            val discriminant = b*b - 4.0*a*c
            if (discriminant < 0.0) continue
            val t = (-b - sqrt(discriminant)) / (2.0 * a)
            if (t > 0.0 && t < bestT) { bestT = t; bestPlanet = planet }
        }

        if (bestPlanet != null) Log.d(TAG, "Hit: ${bestPlanet.displayName}")
        return bestPlanet
    }

    private fun transformVec4(m: DoubleArray, v: DoubleArray): DoubleArray = doubleArrayOf(
        m[0]*v[0]+m[4]*v[1]+m[8]*v[2] +m[12]*v[3],
        m[1]*v[0]+m[5]*v[1]+m[9]*v[2] +m[13]*v[3],
        m[2]*v[0]+m[6]*v[1]+m[10]*v[2]+m[14]*v[3],
        m[3]*v[0]+m[7]*v[1]+m[11]*v[2]+m[15]*v[3]
    )

    private fun invertMat4(m: DoubleArray, out: DoubleArray): Boolean {
        val inv = DoubleArray(16)
        inv[0]  =  m[5]*m[10]*m[15]-m[5]*m[11]*m[14]-m[9]*m[6]*m[15]+m[9]*m[7]*m[14]+m[13]*m[6]*m[11]-m[13]*m[7]*m[10]
        inv[4]  = -m[4]*m[10]*m[15]+m[4]*m[11]*m[14]+m[8]*m[6]*m[15]-m[8]*m[7]*m[14]-m[12]*m[6]*m[11]+m[12]*m[7]*m[10]
        inv[8]  =  m[4]*m[9]*m[15] -m[4]*m[11]*m[13]-m[8]*m[5]*m[15]+m[8]*m[7]*m[13]+m[12]*m[5]*m[11]-m[12]*m[7]*m[9]
        inv[12] = -m[4]*m[9]*m[14] +m[4]*m[10]*m[13]+m[8]*m[5]*m[14]-m[8]*m[6]*m[13]-m[12]*m[5]*m[10]+m[12]*m[6]*m[9]
        inv[1]  = -m[1]*m[10]*m[15]+m[1]*m[11]*m[14]+m[9]*m[2]*m[15]-m[9]*m[3]*m[14]-m[13]*m[2]*m[11]+m[13]*m[3]*m[10]
        inv[5]  =  m[0]*m[10]*m[15]-m[0]*m[11]*m[14]-m[8]*m[2]*m[15]+m[8]*m[3]*m[14]+m[12]*m[2]*m[11]-m[12]*m[3]*m[10]
        inv[9]  = -m[0]*m[9]*m[15] +m[0]*m[11]*m[13]+m[8]*m[1]*m[15]-m[8]*m[3]*m[13]-m[12]*m[1]*m[11]+m[12]*m[3]*m[9]
        inv[13] =  m[0]*m[9]*m[14] -m[0]*m[10]*m[13]-m[8]*m[1]*m[14]+m[8]*m[2]*m[13]+m[12]*m[1]*m[10]-m[12]*m[2]*m[9]
        inv[2]  =  m[1]*m[6]*m[15] -m[1]*m[7]*m[14] -m[5]*m[2]*m[15]+m[5]*m[3]*m[14]+m[13]*m[2]*m[7] -m[13]*m[3]*m[6]
        inv[6]  = -m[0]*m[6]*m[15] +m[0]*m[7]*m[14] +m[4]*m[2]*m[15]-m[4]*m[3]*m[14]-m[12]*m[2]*m[7] +m[12]*m[3]*m[6]
        inv[10] =  m[0]*m[5]*m[15] -m[0]*m[7]*m[13] -m[4]*m[1]*m[15]+m[4]*m[3]*m[13]+m[12]*m[1]*m[7] -m[12]*m[3]*m[5]
        inv[14] = -m[0]*m[5]*m[14] +m[0]*m[6]*m[13] +m[4]*m[1]*m[14]-m[4]*m[2]*m[13]-m[12]*m[1]*m[6] +m[12]*m[2]*m[5]
        inv[3]  = -m[1]*m[6]*m[11] +m[1]*m[7]*m[10] +m[5]*m[2]*m[11]-m[5]*m[3]*m[10]-m[9]*m[2]*m[7]  +m[9]*m[3]*m[6]
        inv[7]  =  m[0]*m[6]*m[11] -m[0]*m[7]*m[10] -m[4]*m[2]*m[11]+m[4]*m[3]*m[10]+m[8]*m[2]*m[7]  -m[8]*m[3]*m[6]
        inv[11] = -m[0]*m[5]*m[11] +m[0]*m[7]*m[9]  +m[4]*m[1]*m[11]-m[4]*m[3]*m[9] -m[8]*m[1]*m[7]  +m[8]*m[3]*m[5]
        inv[15] =  m[0]*m[5]*m[10] -m[0]*m[6]*m[9]  -m[4]*m[1]*m[10]+m[4]*m[2]*m[9] +m[8]*m[1]*m[6]  -m[8]*m[2]*m[5]
        val det = m[0]*inv[0]+m[1]*inv[4]+m[2]*inv[8]+m[3]*inv[12]
        if (abs(det) < 1e-8) return false
        val invDet = 1.0 / det
        for (i in 0 until 16) out[i] = inv[i] * invDet
        return true
    }
}
