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

    // กำหนดพื้นที่สนใจ (ROI) - สแกนเฉพาะกลางจอเพื่อตัด UI ของเกมออก
    int roiStartY = height / 4;
    int roiEndY = height * 3 / 4;

    // 1. Thresholding เฉพาะส่วน ROI
    for (int y = roiStartY; y < roiEndY; ++y) {
        for (int x = 0; x < width; ++x) {
            int i = y * width + x;
            uint8_t r = pixels[i * 4];
            uint8_t g = pixels[i * 4 + 1];
            uint8_t b = pixels[i * 4 + 2];
            int luma = (r * 299 + g * 587 + b * 114) / 1000;
            // ต้องเป็นสีขาวจัดๆ และไม่ใช่พื้นหลังฟ้า
            binaryBuffer[i] = (luma > 230 && r > 200 && g > 200 && b > 200) ? 1 : 0;
        }
    }

    // 2. Simple Blob Detection (หาก้อนสีขาว)
    uint32_t xSum = 0;
    uint32_t ySum = 0;
    uint32_t area = 0;

    for (int y = roiStartY; y < roiEndY; ++y) {
        for (int x = 0; x < width; ++x) {
            if (binaryBuffer[y * width + x] == 1) {
                xSum += x;
                ySum += y;
                area++;
            }
        }
    }

    // ลูกบอลในเกม 8 Ball Pool บนหน้าจอ 1080p มีขนาดพื้นที่ประมาณ 1000 - 8000 พิกเซล
    if (area > 500 && area < 8000) {
        outCueX = (float)xSum / area;
        outCueY = (float)ySum / area;
        foundCue = true;
    }
}

void AetherEngine::calculateGhostBallShot(float cueX, float cueY, float targetX, float targetY) {
    float dx = targetX - cueX;
    float dy = targetY - cueY;

    // 1. คำนวณระยะความยาวเพื่อ Normalize ทิศทาง
    float dist = std::sqrt(dx * dx + dy * dy);
    if (dist == 0.0f) dist = 1.0f;

    // ทิศทางหน่วยเดียว (Normalized)
    float dirX = dx / dist;
    float dirY = dy / dist;

    // 2. ระยะการดึงคิว (Power) แบบจำกัดไม่ให้ลากออกจอ
    // สมมติดึงสูงสุด 300 พิกเซล
    float swipeDistance = 300.0f;

    // 3. จุดเริ่มต้นคือลูกขาว
    currentCommand.startX = cueX;
    currentCommand.startY = cueY;

    // 4. จุดสิ้นสุดคือ ลูกขาว ลบด้วย ทิศทาง * ระยะดึง (ยิงไปทางเป้าหมาย = ดึงคิวไปด้านหลัง)
    currentCommand.endX = cueX - (dirX * swipeDistance);
    currentCommand.endY = cueY - (dirY * swipeDistance);

    currentCommand.durationMs = 400; // ดึงนาน 400ms
    currentCommand.hasShot = true;
}

void AetherEngine::processFrame(uint8_t* pixels, int width, int height) {
    float cueX, cueY;
    bool foundCue = false;

    // 1. หาตำแหน่งลูกขาว
    detectBalls(pixels, width, height, cueX, cueY, foundCue);

    // 2. ถ้าเจอลูกขาว ให้คำนวณการยิง
    if (foundCue) {
        // Mock Target: สมมติให้ยิงไปทางขวากลางจอ (รอ Vision หาลูกเป้าหมายในเฟสถัดไป)
        float mockTargetX = width * 0.9f;
        float mockTargetY = height * 0.5f;

        calculateGhostBallShot(cueX, cueY, mockTargetX, mockTargetY);
    }
}

AetherCommand AetherEngine::getLatestCommand() {
    return currentCommand;
}
