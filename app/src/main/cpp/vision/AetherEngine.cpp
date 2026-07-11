#include "AetherEngine.h"
#include <cstring>

AetherEngine::AetherEngine(int width, int height) : screenWidth(width), screenHeight(height) {
    binaryBuffer = new uint8_t[width * height]();
    currentCommand.hasShot = false;
}

AetherEngine::~AetherEngine() {
    delete[] binaryBuffer;
}

void AetherEngine::processFrame(uint8_t* pixels, int width, int height) {
    // Mock Logic: ตรวจจับพิกเซลสีขาวและสั่งยิง (จะเพิ่ม CCL ทีหลัง)
    bool foundWhite = false;
    float cueX = 0, cueY = 0;

    for (int y = 0; y < height; ++y) {
        for (int x = 0; x < width; ++x) {
            int idx = (y * width + x) * 4;
            uint8_t r = pixels[idx];
            uint8_t g = pixels[idx + 1];
            uint8_t b = pixels[idx + 2];
            
            // ถ้าเจอสีขาวบริเวณกลางจอ (Mock)
            if (r > 200 && g > 200 && b > 200) {
                if (y > height / 3 && y < (height * 2) / 3) {
                    cueX = x;
                    cueY = y;
                    foundWhite = true;
                    break;
                }
            }
        }
        if (foundWhite) break;
    }

    if (foundWhite) {
        currentCommand.startX = cueX;
        currentCommand.startY = cueY;
        currentCommand.endX = cueX + 300.0f;
        currentCommand.endY = cueY - 300.0f;
        currentCommand.durationMs = 300;
        currentCommand.hasShot = true;
    }
}

AetherCommand AetherEngine::getLatestCommand() {
    return currentCommand;
}
