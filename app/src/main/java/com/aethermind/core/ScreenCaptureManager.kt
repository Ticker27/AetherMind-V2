package com.aethermind.core

import android.media.Image
import android.media.ImageReader
import android.view.Surface
import com.aethermind.vision.AetherNativeBridge
import java.nio.ByteBuffer

class ScreenCaptureManager(
    private val width: Int,
    private val height: Int
) : ImageReader.OnImageAvailableListener {

    private var imageReader: ImageReader? = null
    // Pre-allocated Buffer (จองครั้งเดียว ไม่สร้างขยะใน Loop)
    private val pixelBuffer = ByteBuffer.allocateDirect(width * height * 4)

    fun start(): Surface {
        imageReader = ImageReader.newInstance(width, height, 0x1 /* RGBA_8888 */, 2)
        imageReader?.setOnImageAvailableListener(this, null)
        return imageReader!!.surface
    }

    override fun onImageAvailable(reader: ImageReader?) {
        val image = reader?.acquireLatestImage() ?: return
        try {
            val plane = image.planes[0]
            val buffer = plane.buffer
            
            // คัดลอกข้อมูลหน้าจอเข้า Pre-allocated Buffer ทันที (Zero-Copy ข้าม JNI)
            buffer.get(pixelBuffer.array())
            
            // ส่งเข้า C++ Engine
            AetherNativeBridge.processScreenFrame(pixelBuffer, width, height)
        } catch (e: Exception) {
            // ข้ามเฟรมนี้ไป
        } finally {
            image.close()
        }
    }

    fun stop() {
        imageReader?.close()
        imageReader = null
    }
}
