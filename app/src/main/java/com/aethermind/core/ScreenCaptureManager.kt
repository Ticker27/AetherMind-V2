package com.aethermind.core

import android.graphics.PixelFormat
import android.hardware.display.DisplayManager
import android.hardware.display.VirtualDisplay
import android.media.Image
import android.media.ImageReader
import android.media.projection.MediaProjection
import android.util.Log
import com.aethermind.vision.AetherNativeBridge
import java.nio.ByteBuffer

class ScreenCaptureManager(
    private val width: Int,
    private val height: Int
) : ImageReader.OnImageAvailableListener {

    private var imageReader: ImageReader? = null
    private var virtualDisplay: VirtualDisplay? = null
    private var projection: MediaProjection? = null
    
    // Pre-allocated Buffer (จองครั้งเดียว)
    private val pixelBuffer = ByteBuffer.allocateDirect(width * height * 4)

    fun start(mediaProjection: MediaProjection) {
        projection = mediaProjection
        
        // ตั้งค่า ImageReader ให้อ่านภาพแบบ RGBA
        imageReader = ImageReader.newInstance(width, height, PixelFormat.RGBA_8888, 2)
        imageReader?.setOnImageAvailableListener(this, null)
        
        // เชื่อมจอภาพเข้ากับ ImageReader
        virtualDisplay = projection?.createVirtualDisplay(
            "AetherScreenCapture",
            width, height, 1,
            DisplayManager.VIRTUAL_DISPLAY_FLAG_AUTO_MIRROR,
            imageReader!!.surface, null, null
        )
        Log.d("AetherCapture", "Screen Capture Started!")
    }

    override fun onImageAvailable(reader: ImageReader?) {
        val image = reader?.acquireLatestImage() ?: return
        try {
            val plane = image.planes[0]
            val buffer = plane.buffer
            val rowStride = plane.rowStride
            
            // คัดลอกข้อมูลภาพเข้า Pre-allocated Buffer (จัดการปัญหา RowStride/Padding)
            var pos = 0
            val rowSize = width * 4
            val tempRow = ByteArray(rowSize)
            
            while (pos < height * rowSize && buffer.remaining() >= rowStride) {
                buffer.get(tempRow, 0, rowSize)
                pixelBuffer.put(tempRow)
                // ข้าม Padding (ถ้ามี)
                if (rowStride > rowSize) {
                    buffer.position(buffer.position() + (rowStride - rowSize))
                }
                pos += rowSize
            }
            
            pixelBuffer.rewind()
            
            // ส่งภาพตรงเข้า C++ (Zero-Copy)
            AetherNativeBridge.processScreenFrame(pixelBuffer, width, height)
            
        } catch (e: Exception) {
            Log.e("AetherCapture", "Error processing frame", e)
        } finally {
            image.close()
        }
    }

    fun stop() {
        virtualDisplay?.release()
        imageReader?.close()
        projection?.stop()
        virtualDisplay = null
        imageReader = null
        projection = null
    }
}
