package com.aethermind.vision

import java.nio.ByteBuffer

object AetherNativeBridge {
    init {
        System.loadLibrary("aether_core")
    }

    external fun nativeInit(width: Int, height: Int)
    external fun nativeProcessFrame(byteBuffer: ByteBuffer, width: Int, height: Int)
    external fun nativeGetCommand(): FloatArray

    fun initEngine(screenWidth: Int, screenHeight: Int) {
        nativeInit(screenWidth, screenHeight)
    }

    fun processScreenFrame(frameBuffer: ByteBuffer, width: Int, height: Int) {
        nativeProcessFrame(frameBuffer, width, height)
    }

    fun checkForShotCommand(): FloatArray? {
        val cmd = nativeGetCommand()
        return if (cmd.isNotEmpty() && cmd[0] == 1.0f) cmd else null
    }
}
