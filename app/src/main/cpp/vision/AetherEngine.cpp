#include "AetherEngine.h"
#include <cstring>
#include <cmath>
#include <algorithm>

AetherEngine::AetherEngine(int width, int height) : screenWidth(width), screenHeight(height) {
    binaryBuffer = new uint8_t[width * height]();
    objVis       = new uint8_t[width * height]();
    objStackX    = new int[width * height]();
    objStackY    = new int[width * height]();
    currentCommand.hasShot = false;
}

AetherEngine::~AetherEngine() {
    delete[] binaryBuffer;
    delete[] objVis;
    delete[] objStackX;
    delete[] objStackY;
}

void AetherEngine::detectBalls(uint8_t* pixels, int width, int height, float& outCueX, float& outCueY, bool& foundCue) {
    foundCue = false;

    // ROI: กลางจอ ตัด UI บน/ล่าง
    int roiStartY = height / 5;
    int roiEndY   = height * 4 / 5;

    uint32_t xSum = 0, ySum = 0, area = 0;

    for (int y = roiStartY; y < roiEndY; ++y) {
        for (int x = 0; x < width; ++x) {
            int i = y * width + x;
            uint8_t r = pixels[i * 4];
            uint8_t g = pixels[i * 4 + 1];
            uint8_t b = pixels[i * 4 + 2];
            int luma = (r * 299 + g * 587 + b * 114) / 1000;

            // ลูกคิว = สีขาวจัด (มีเงา/สะท้อนก็ยังแตะเกณฑ์)
            if (luma > 200 && r > 180 && g > 180 && b > 180) {
                xSum += x; ySum += y; area++;
            }
        }
    }

    if (area > 300 && area < 15000) {
        outCueX = (float)xSum / area;
        outCueY = (float)ySum / area;
        foundCue = true;
    }
}

// หาลูกเป้าสี (object ball) ที่ใกล้ลูกคิวที่สุด ด้วย flood-fill แบบ windowed
void AetherEngine::findObjectBall(uint8_t* pixels, int width, int height,
                                   float cueX, float cueY,
                                   float& outObjX, float& outObjY, bool& foundObj) {
    foundObj = false;
    int roiStartY = height / 5;
    int roiEndY   = height * 4 / 5;

    // เงื่อนไข "เป็นลูกสี" : มีสีสด (ช่องใดช่องหนึ่ง >120) + ไม่ขาว + ไม่ใช่โต๊ะเขียว
    auto isObjAt = [&](int x, int y) -> bool {
        if (y < roiStartY || y >= roiEndY || x < 0 || x >= width) return false;
        int i = y * width + x;
        uint8_t r = pixels[i * 4], g = pixels[i * 4 + 1], b = pixels[i * 4 + 2];
        bool colored   = (r > 120 || g > 120 || b > 120);
        bool white     = (r > 180 && g > 180 && b > 180);
        bool greenTbl  = (g > r + 40 && g > b + 40 && g > 80);
        return colored && !white && !greenTbl;
    };

    // 1. หาเม็ดสีที่ใกล้ลูกคิวที่สุด
    int seedX = -1, seedY = -1;
    float bestD2 = 1e18f;
    for (int y = roiStartY; y < roiEndY; ++y) {
        for (int x = 0; x < width; ++x) {
            if (!isObjAt(x, y)) continue;
            float dx = x - cueX, dy = y - cueY;
            float d2 = dx * dx + dy * dy;
            if (d2 < bestD2) { bestD2 = d2; seedX = x; seedY = y; }
        }
    }
    if (seedX < 0) return;

    // 2. flood-fill แบบจำกัดหน้าต่างรอบ seed (ลูกบอลไม่ใหญ่กว่า ~160px)
    const int W = 160;
    int x0 = std::max(0, seedX - W), x1 = std::min(width - 1, seedX + W);
    int y0 = std::max(roiStartY, seedY - W), y1 = std::min(roiEndY - 1, seedY + W);
    for (int yy = y0; yy <= y1; ++yy)
        for (int xx = x0; xx <= x1; ++xx)
            objVis[yy * width + xx] = 0;

    int sp = 0;
    objStackX[sp] = seedX; objStackY[sp] = seedY; sp++;
    objVis[seedY * width + seedX] = 1;
    long sx = 0, sy = 0; uint32_t area = 0;

    while (sp > 0 && area < 15000) {
        int x = objStackX[--sp], y = objStackY[sp];
        sx += x; sy += y; area++;
        int nx[4] = { x + 1, x - 1, x, x };
        int ny[4] = { y,     y,     y + 1, y - 1 };
        for (int k = 0; k < 4; ++k) {
            int xx = nx[k], yy = ny[k];
            if (xx < x0 || xx > x1 || yy < y0 || yy > y1) continue;
            int j = yy * width + xx;
            if (objVis[j]) continue;
            if (isObjAt(xx, yy)) { objVis[j] = 1; objStackX[sp] = xx; objStackY[sp] = yy; sp++; }
        }
    }

    if (area > 200 && area < 15000) {
        outObjX = (float)sx / area;
        outObjY = (float)sy / area;
        foundObj = true;
    }
}

void AetherEngine::calculateGhostBallShot(float cueX, float cueY, float targetX, float targetY) {
    float dx = targetX - cueX;
    float dy = targetY - cueY;
    float dist = std::sqrt(dx * dx + dy * dy);
    if (dist < 1.0f) dist = 1.0f;

    float dirX = dx / dist;
    float dirY = dy / dist;
    float swipeDistance = 300.0f;

    currentCommand.startX = cueX;
    currentCommand.startY = cueY;
    // ดึงคิวกลับจากลูกเป้า (ghost-ball: คิววิ่งผ่านลูกเป้า)
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
        float objX, objY;
        bool foundObj = false;
        findObjectBall(pixels, width, height, cueX, cueY, objX, objY, foundObj);
        if (foundObj) {
            // เล็งใส่ลูกเป้าจริง (ไม่ใช่จุดจำลอง)
            calculateGhostBallShot(cueX, cueY, objX, objY);
        } else {
            currentCommand.hasShot = false; // ไม่มีลูกเป้า อย่าติง
        }
    } else {
        currentCommand.hasShot = false; // ไม่เห็นลูกคิว อย่าติง
    }
}

AetherCommand AetherEngine::getLatestCommand() {
    return currentCommand; // คืนโดยไม่รีเซ็ต -> autoplay ยิงซ้ำได้ (มี guard ใน a11y + หน่วง 4 วิ)
}
