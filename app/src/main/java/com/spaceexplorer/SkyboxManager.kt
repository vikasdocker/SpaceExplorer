package com.spaceexplorer

import android.util.Log
import com.google.android.filament.Engine
import com.google.android.filament.IndirectLight
import com.google.android.filament.Scene
import com.google.android.filament.Skybox
import com.google.android.filament.Texture
import java.nio.ByteBuffer
import java.nio.ByteOrder

class SkyboxManager(
    private val engine: Engine,
    private val scene:  Scene
) {
    companion object { private const val TAG = "SkyboxManager" }

    private var skybox:  Skybox?        = null
    private var cubemap: Texture?       = null
    private var ibl:     IndirectLight? = null

    fun setup() {
        Log.d(TAG, "Generating starfield bitmaps…")
        val faces = StarfieldGenerator.generateFaces(seed = 137L)
        Log.d(TAG, "Uploading cubemap to GPU…")
        cubemap = buildCubemap(faces)
        ibl     = buildIbl()
        skybox  = buildSkybox()
        scene.skybox        = skybox
        scene.indirectLight = ibl
        faces.forEach { it.recycle() }
        Log.d(TAG, "Skybox active")
    }

    fun destroy() {
        Log.d(TAG, "Destroying SkyboxManager")
        scene.skybox        = null
        scene.indirectLight = null
        skybox?.let  { engine.destroySkybox(it);        skybox  = null }
        cubemap?.let { engine.destroyTexture(it);       cubemap = null }
        ibl?.let     { engine.destroyIndirectLight(it); ibl     = null }
        Log.d(TAG, "SkyboxManager destroyed")
    }

    private fun buildCubemap(faces: Array<android.graphics.Bitmap>): Texture {
        val size   = faces[0].width
        val levels = mipLevels(size)
        val tex    = Texture.Builder()
            .width(size).height(size).levels(levels)
            .sampler(Texture.Sampler.SAMPLER_CUBEMAP)
            .format(Texture.InternalFormat.RGBA8)
            .build(engine)

        for (i in 0 until 6) {
            val buf = bitmapToBuffer(faces[i])
            // Using the 3D setImage where zoffset (i) is the face index and depth is 1
            tex.setImage(engine, 0, 0, 0, i, size, size, 1,
                Texture.PixelBufferDescriptor(buf, Texture.Format.RGBA, Texture.Type.UBYTE)
            )
        }
        tex.generateMipmaps(engine)
        return tex
    }

    private fun buildIbl(): IndirectLight {
        val sh = floatArrayOf(
             0.42f,  0.41f,  0.43f,
             0.00f,  0.00f,  0.00f,
            -0.02f, -0.02f, -0.03f,
             0.00f,  0.00f,  0.00f,
             0.00f,  0.00f,  0.00f,
             0.01f,  0.01f,  0.02f,
            -0.01f, -0.01f, -0.02f,
             0.00f,  0.00f,  0.00f,
             0.01f,  0.01f,  0.00f
        )
        return IndirectLight.Builder()
            .irradiance(3, sh)
            .reflections(cubemap!!)
            .intensity(8_000f)
            .build(engine)
    }

    private fun buildSkybox(): Skybox {
        return Skybox.Builder()
            .environment(cubemap!!)
            .showSun(false)
            .build(engine)
    }

    private fun bitmapToBuffer(bmp: android.graphics.Bitmap): ByteBuffer {
        val pixels = IntArray(bmp.width * bmp.height)
        bmp.getPixels(pixels, 0, bmp.width, 0, 0, bmp.width, bmp.height)
        val buf = ByteBuffer.allocateDirect(pixels.size * 4).order(ByteOrder.LITTLE_ENDIAN)
        for (pixel in pixels) {
            buf.put(((pixel shr 16) and 0xFF).toByte())
            buf.put(((pixel shr 8)  and 0xFF).toByte())
            buf.put(( pixel         and 0xFF).toByte())
            buf.put(((pixel shr 24) and 0xFF).toByte())
        }
        buf.rewind()
        return buf
    }

    private fun mipLevels(size: Int): Int {
        var levels = 1; var s = size
        while (s > 1) { s = s shr 1; levels++ }
        return levels
    }
}
