package com.spaceexplorer

import java.nio.ByteBuffer
import java.nio.ByteOrder
import kotlin.math.PI
import kotlin.math.cos
import kotlin.math.sin

object SphereGenerator {

    fun generateGlb(
        radius:    Float = 1.0f,
        stacks:    Int   = 48,
        slices:    Int   = 48,
        r:         Float = 0.3f,
        g:         Float = 0.5f,
        b:         Float = 0.9f,
        roughness: Float = 0.65f,
        metallic:  Float = 0.0f,
        emissiveR: Float = 0.0f,
        emissiveG: Float = 0.0f,
        emissiveB: Float = 0.0f
    ): ByteBuffer {

        val safeRoughness = roughness.coerceIn(0.045f, 1.0f)
        val safeMetallic  = metallic.coerceIn(0.0f, 1.0f)
        val vertexCount   = (stacks + 1) * (slices + 1)
        val positions     = FloatArray(vertexCount * 3)
        val normals       = FloatArray(vertexCount * 3)
        val texcoords     = FloatArray(vertexCount * 2)

        var vi = 0; var ti = 0

        for (stack in 0..stacks) {
            val phi    = PI * stack.toDouble() / stacks
            val sinPhi = sin(phi).toFloat()
            val cosPhi = cos(phi).toFloat()
            for (slice in 0..slices) {
                val theta    = 2.0 * PI * slice.toDouble() / slices
                val cosTheta = cos(theta).toFloat()
                val sinTheta = sin(theta).toFloat()
                val nx = cosTheta * sinPhi
                val ny = cosPhi
                val nz = sinTheta * sinPhi
                positions[vi]     = nx * radius
                positions[vi + 1] = ny * radius
                positions[vi + 2] = nz * radius
                normals[vi]       = nx
                normals[vi + 1]   = ny
                normals[vi + 2]   = nz
                vi += 3
                texcoords[ti]     = slice.toFloat() / slices
                texcoords[ti + 1] = stack.toFloat() / stacks
                ti += 2
            }
        }

        val indexCount = stacks * slices * 6
        val indices    = ShortArray(indexCount)
        var ii = 0
        for (stack in 0 until stacks) {
            for (slice in 0 until slices) {
                val v0 = stack * (slices + 1) + slice
                val v1 = v0 + slices + 1
                indices[ii++] = v0.toShort()
                indices[ii++] = v1.toShort()
                indices[ii++] = (v0 + 1).toShort()
                indices[ii++] = (v0 + 1).toShort()
                indices[ii++] = v1.toShort()
                indices[ii++] = (v1 + 1).toShort()
            }
        }

        return packGlb(positions, normals, texcoords, indices,
            r, g, b, safeRoughness, safeMetallic, emissiveR, emissiveG, emissiveB)
    }

    fun generateGlbLod(planet: Planet, lod: LodManager.LodLevel): ByteBuffer {
        return generateGlb(
            radius    = planet.radius,
            stacks    = lod.stacks,
            slices    = lod.slices,
            r         = planet.colorR,
            g         = planet.colorG,
            b         = planet.colorB,
            roughness = planet.roughness,
            metallic  = planet.metallic,
            emissiveR = planet.emissiveR,
            emissiveG = planet.emissiveG,
            emissiveB = planet.emissiveB
        )
    }

    private fun packGlb(
        positions: FloatArray, normals: FloatArray,
        texcoords: FloatArray, indices: ShortArray,
        r: Float, g: Float, b: Float,
        roughness: Float, metallic: Float,
        emR: Float, emG: Float, emB: Float
    ): ByteBuffer {
        val vertexCount = positions.size / 3
        val indexCount  = indices.size

        var minX = Float.MAX_VALUE; var maxX = -Float.MAX_VALUE
        var minY = Float.MAX_VALUE; var maxY = -Float.MAX_VALUE
        var minZ = Float.MAX_VALUE; var maxZ = -Float.MAX_VALUE
        var i = 0
        while (i < positions.size) {
            if (positions[i]     < minX) minX = positions[i]
            if (positions[i]     > maxX) maxX = positions[i]
            if (positions[i + 1] < minY) minY = positions[i + 1]
            if (positions[i + 1] > maxY) maxY = positions[i + 1]
            if (positions[i + 2] < minZ) minZ = positions[i + 2]
            if (positions[i + 2] > maxZ) maxZ = positions[i + 2]
            i += 3
        }

        val posBytes  = positions.size * Float.SIZE_BYTES
        val normBytes = normals.size   * Float.SIZE_BYTES
        val uvBytes   = texcoords.size * Float.SIZE_BYTES
        val idxBytes  = indices.size   * Short.SIZE_BYTES
        val posOff    = 0
        val normOff   = posBytes
        val uvOff     = posBytes + normBytes
        val idxOff    = posBytes + normBytes + uvBytes
        val binLen    = align4(posBytes + normBytes + uvBytes + idxBytes)

        val bin = ByteBuffer.allocate(binLen).order(ByteOrder.LITTLE_ENDIAN)
        positions.forEach  { bin.putFloat(it) }
        normals.forEach    { bin.putFloat(it) }
        texcoords.forEach  { bin.putFloat(it) }
        indices.forEach    { bin.putShort(it) }
        repeat(binLen - (posBytes + normBytes + uvBytes + idxBytes)) { bin.put(0) }
        bin.rewind()

        val emissiveKey = if (emR > 0f || emG > 0f || emB > 0f)
            ""","emissiveFactor":[$emR,$emG,$emB]""" else ""

        val json = """{"asset":{"version":"2.0","generator":"SpaceExplorer-v8"},"scene":0,"scenes":[{"nodes":[0]}],"nodes":[{"mesh":0}],"meshes":[{"name":"Sphere","primitives":[{"attributes":{"POSITION":0,"NORMAL":1,"TEXCOORD_0":2},"indices":3,"material":0,"mode":4}]}],"materials":[{"name":"PlanetMat","pbrMetallicRoughness":{"baseColorFactor":[$r,$g,$b,1.0],"metallicFactor":$metallic,"roughnessFactor":$roughness}$emissiveKey,"doubleSided":false}],"accessors":[{"bufferView":0,"byteOffset":0,"componentType":5126,"count":$vertexCount,"type":"VEC3","min":[$minX,$minY,$minZ],"max":[$maxX,$maxY,$maxZ]},{"bufferView":1,"byteOffset":0,"componentType":5126,"count":$vertexCount,"type":"VEC3"},{"bufferView":2,"byteOffset":0,"componentType":5126,"count":$vertexCount,"type":"VEC2"},{"bufferView":3,"byteOffset":0,"componentType":5123,"count":$indexCount,"type":"SCALAR"}],"bufferViews":[{"buffer":0,"byteOffset":$posOff,"byteLength":$posBytes,"target":34962},{"buffer":0,"byteOffset":$normOff,"byteLength":$normBytes,"target":34962},{"buffer":0,"byteOffset":$uvOff,"byteLength":$uvBytes,"target":34962},{"buffer":0,"byteOffset":$idxOff,"byteLength":$idxBytes,"target":34963}],"buffers":[{"byteLength":$binLen}]}"""

        val jsonBytes  = json.toByteArray(Charsets.UTF_8)
        val jsonPadded = align4(jsonBytes.size)
        val totalSize  = 12 + 8 + jsonPadded + 8 + binLen
        val glb        = ByteBuffer.allocate(totalSize).order(ByteOrder.LITTLE_ENDIAN)

        glb.putInt(0x46546C67); glb.putInt(2); glb.putInt(totalSize)
        glb.putInt(jsonPadded); glb.putInt(0x4E4F534A)
        glb.put(jsonBytes)
        repeat(jsonPadded - jsonBytes.size) { glb.put(0x20) }
        glb.putInt(binLen); glb.putInt(0x004E4942)
        glb.put(bin)
        glb.rewind()
        return glb
    }

    private fun align4(n: Int): Int = (n + 3) and 3.inv()
}
