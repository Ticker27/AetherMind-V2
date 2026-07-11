#include "AetherEngine.h"
#include <cstring>
#include <cmath>

AetherEngine::AetherEngine(int width, int height) : screenWidth(width), screenHeight(height) {
    binaryBuffer = new uint8_t[width * height]();
    currentCommand.hasShot = false;
}

AetherEngine::~AetherEngine() {
    delete[] binaryBuffer;
}

void AetherEngine::detectBalls(uint8_t* pixels, int width, int height, float& outCueX, float& outCueY, bool& foundCue) {
    foundCue = false;
    
    for (int i = 0; i < width * height; ++i) {
        uint8_t r = pixels[i * 4];
        uint8_t g = pixels[i * 4 + 1];
        uint8_t b = pixels[i * 4 + 2];
        int luma = (r * 299 + g * 587 + b * 114) / 1000;
        binaryBuffer[i] = (luma > 220 && r > 200 && g > 200 && b > 200) ? 1 : 0;
    }

    uint32_t xSum = 0;
    uint32_t ySum = 0;
    uint32_t area = 0;

    for (int y = 0; y < height; ++y) {
        for (int x = 0; x < width; ++x) {
            if (binaryBuffer[y * width + x] == 1) {
                xSum += x;
                ySum += y;
                area++;
            }
        }
    }

    if (area > 100 && area < 5000) {
        outCueX = (float)xSum / area;
        outCueY = (float)ySum / area;
        foundCue = true;
    }
}

void AetherEngine::calculateGhostBallShot(float cueX, float cueY, float targetX, float targetY) {
    float dx = targetX - cueX;
    float dy = targetY - cueY;
    float power = 300.0f;
    
    currentCommand.startX = cueX;
    currentCommand.startY = cueY;
    currentCommand.endX = cueX - (dx * power);
    currentCommand.endY = cueY - (dy * power);
    currentCommand.durationMs = 300;
    currentCommand.hasShot = true;
}

void AetherEngine::processFrame(uint8_t* pixels, int width, int height) {
    float cueX, cueY;
    bool foundCue = false;

    detectBalls(pixels, width, height, cueX, cueY, foundCue);

    if (foundCue) {
        float mockTargetX = width * 0.8f;
        float mockTargetY = height * 0.2f;
        calculateGhostBallShot(cueX, cueY, mockTargetX, mockTargetY);
    }
}

AetherCommand AetherEngine::getLatestCommand() {
    AetherCommand snapshot = currentCommand;     // คัดลอกได้แล้ว (มี copy ctor)
    currentCommand.hasShot.store(false);          // รีเซ็ตธงที่ engine จริง (กันยิงซ้ำไม่หยุด)
    return snapshot;
}
