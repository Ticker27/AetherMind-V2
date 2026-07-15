#include "AetherEngine.h"
#include <cstring>
#include <cmath>
#include <algorithm>

AetherEngine::AetherEngine(int width, int height) : screenWidth(width), screenHeight(height) {
    binaryBuffer = new uint8_t[width * height]();
    currentCommand.hasShot = false;
}

AetherEngine::~AetherEngine() {
    delete[] binaryBuffer;
}

void AetherEngine::detectBalls(uint8_t* pixels, int width, int height, float& outCueX, float& outCueY, bool& foundCue) {
    foundCue = false;

    // ขยายพื้นที่สแกน (ROI) ให้กว้างขึ้น ตัดเฉพาะ UI ด้านบนสุดและล่างสุดออก
    int roiStartY = height / 5;
    int roiEndY = height * 4 / 5;

    uint32_t xSum = 0;
    uint32_t ySum = 0;
    uint32_t area = 0;

    for (int y = roiStartY; y < roiEndY; ++y) {
        for (int x = 0; x < width; ++x) {
            int i = y * width + x;
            uint8_t r = pixels[i * 4];
            uint8_t g = pixels[i * 4 + 1];
            uint8_t b = pixels[i * 4 + 2];
            int luma = (r * 299 + g * 587 + b * 114) / 1000;

            // ลด Threshold ลง เพื่อให้จับสีขาวที่มีเงาหรือแสงสะท้อนในเกมได้
            if (luma > 200 && r > 180 && g > 180 && b > 180) {
                xSum += x;
                ySum += y;
                area++;
            }
        }
    }

    // ขยายขนาดกรอบที่ยอมรับว่าเป็นลูกบอล
    if (area > 300 && area < 15000) {
        outCueX = (float)xSum / area;
        outCueY = (float)ySum / area;
        foundCue = true;
    }
}

void AetherEngine::calculateGhostBallShot(float cueX, float cueY, float targetX, float targetY) {
    float dx = targetX - cueX;
    float dy = targetY - cueY;
    float dist = std::sqrt(dx * dx + dy * dy);
    if (dist == 0.0f) dist = 1.0f;

    float dirX = dx / dist;
    float dirY = dy / dist;
    float swipeDistance = 300.0f;

    currentCommand.startX = cueX;
    currentCommand.startY = cueY;
    currentCommand.endX = cueX - (dirX * swipeDistance);
    currentCommand.endY = cueY - (dirY * swipeDistance);
    currentCommand.durationMs = 400;
    currentCommand.hasShot = true;
}

void AetherEngine::processFrame(uint8_t* pixels, int width, int height) {
    float cueX, cueY;
    bool foundCue = false;

    detectBalls(pixels, width, height, cueX, cueY, foundCue);

    if (foundCue) {
        float mockTargetX = width * 0.9f;
        float mockTargetY = height * 0.5f;
        calculateGhostBallShot(cueX, cueY, mockTargetX, mockTargetY);
    }
}

AetherCommand AetherEngine::getLatestCommand() {
    return currentCommand;
}
