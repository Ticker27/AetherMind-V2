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

void AetherEngine::detectBalls(uint8_t* pixels, int width, int height, BallInfo& outCue, BallInfo& outTarget, bool& foundCue, bool& foundTarget) {
    foundCue = false;
    foundTarget = false;

    int roiStartY = height / 5;
    int roiEndY = height * 4 / 5;

    // ตัวแปรสำหรับเก็บ Centroid ของลูกขาว
    uint32_t cueXSum = 0, cueYSum = 0, cueArea = 0;
    // ตัวแปรสำหรับเก็บ Centroid ของลูกเป้าหมาย (สีแดง/เหลือง)
    uint32_t targetXSum = 0, targetYSum = 0, targetArea = 0;

    for (int y = roiStartY; y < roiEndY; ++y) {
        for (int x = 0; x < width; ++x) {
            int i = y * width + x;
            uint8_t r = pixels[i * 4];
            uint8_t g = pixels[i * 4 + 1];
            uint8_t b = pixels[i * 4 + 2];
            int luma = (r * 299 + g * 587 + b * 114) / 1000;

            // 1. ตรวจจับลูกขาว (สว่างมาก และไม่มีสีชัดเจน)
            if (luma > 200 && r > 180 && g > 180 && b > 180) {
                cueXSum += x;
                cueYSum += y;
                cueArea++;
            }
            // 2. ตรวจจับลูกเป้าหมาย (สีแดง หรือ สีเหลือง)
            else if ((r > 150 && g < 100 && b < 100) || (r > 150 && g > 150 && b < 100)) {
                targetXSum += x;
                targetYSum += y;
                targetArea++;
            }
        }
    }

    // ยอมรับว่าป็นลูกบอลถ้าขนาดพื้นที่อยู่ในเกณฑ์ที่เหมาะสม
    if (cueArea > 300 && cueArea < 15000) {
        outCue.x = (float)cueXSum / cueArea;
        outCue.y = (float)cueYSum / cueArea;
        outCue.isCue = true;
        foundCue = true;
    }

    if (targetArea > 200 && targetArea < 15000) {
        outTarget.x = (float)targetXSum / targetArea;
        outTarget.y = (float)targetYSum / targetArea;
        outTarget.isTarget = true;
        foundTarget = true;
    }
}

void AetherEngine::calculateGhostBallShot(float cueX, float cueY, float targetX, float targetY) {
    // ทิศทางจากลูกเป้าหมาย ย้อนกลับมาหาลูกขาว (เพื่อหาจุดที่จะชน)
    float dx = cueX - targetX;
    float dy = cueY - targetY;
    float dist = std::sqrt(dx * dx + dy * dy);
    if (dist == 0.0f) dist = 1.0f;

    // ทิศทางหน่วยเดียว
    float dirX = dx / dist;
    float dirY = dy / dist;

    // ระยะดึงคิว (Power) - ตอนนี้ตั้งไว้ 300 พิกเซล
    float swipeDistance = 300.0f;

    // จุดเริ่มต้นคือลูกขาว
    currentCommand.startX = cueX;
    currentCommand.startY = cueY;

    // จุดสิ้นสุดคือ ลูกขาว ลบด้วย ทิศทาง * ระยะดึง
    // (เพื่อให้ลูกขาววิ่งไปชนลูกเป้าหมาย)
    currentCommand.endX = cueX - (dirX * swipeDistance);
    currentCommand.endY = cueY - (dirY * swipeDistance);

    currentCommand.durationMs = 400;
    currentCommand.hasShot = true;
}

void AetherEngine::processFrame(uint8_t* pixels, int width, int height) {
    BallInfo cue, target;
    bool foundCue = false;
    bool foundTarget = false;

    // 1. ค้นหาลูกขาวและลูกเป้าหมาย
    detectBalls(pixels, width, height, cue, target, foundCue, foundTarget);

    // 2. ถ้าเจอทั้งคู่ ให้คำนวณเส้นทางยิงทันที
    if (foundCue && foundTarget) {
        calculateGhostBallShot(cue.x, cue.y, target.x, target.y);
    }
}

AetherCommand AetherEngine::getLatestCommand() {
    return currentCommand;
}
