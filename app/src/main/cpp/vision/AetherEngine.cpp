#include "AetherEngine.h"
#include <cstring>
#include <cmath>

AetherEngine::AetherEngine(int width, int height) : screenWidth(width), screenHeight(height) {
    binaryBuffer = new uint8_t[width * height]();
    currentCommand.hasShot = false;
    debugInfo.foundCue = false;
    debugInfo.foundTarget = false;
    debugInfo.cueX = 0; debugInfo.cueY = 0;
    debugInfo.targetX = 0; debugInfo.targetY = 0;
    debugInfo.bestPocket = -1;
    debugInfo.frameCount = 0;
    float pocketCoords[6][2] = { {0.05f, 0.10f}, {0.50f, 0.08f}, {0.95f, 0.10f}, {0.05f, 0.85f}, {0.50f, 0.88f}, {0.95f, 0.85f} };
    for(int i=0; i<6; ++i) { pockets[i].x = pocketCoords[i][0] * width; pockets[i].y = pocketCoords[i][1] * height; }
}

AetherEngine::~AetherEngine() { delete[] binaryBuffer; }

void AetherEngine::detectBalls(uint8_t* pixels, int width, int height, BallInfo& outCue, BallInfo& outTarget, bool& foundCue, bool& foundTarget) {
    foundCue = false; foundTarget = false;
    int roiStartY = height / 5; int roiEndY = height * 4 / 5;
    uint32_t cueXSum=0, cueYSum=0, cueArea=0, targetXSum=0, targetYSum=0, targetArea=0;

    for (int y = roiStartY; y < roiEndY; ++y) {
        for (int x = 0; x < width; ++x) {
            int i = y * width + x;
            uint8_t r = pixels[i * 4], g = pixels[i * 4 + 1], b = pixels[i * 4 + 2];
            int luma = (r * 299 + g * 587 + b * 114) / 1000;
            if (luma > 200 && r > 180 && g > 180 && b > 180) { cueXSum += x; cueYSum += y; cueArea++; } 
            else if ((r > 150 && g < 100 && b < 100) || (r > 150 && g > 150 && b < 100)) { targetXSum += x; targetYSum += y; targetArea++; }
        }
    }
    if (cueArea > 300 && cueArea < 15000) { outCue.x = (float)cueXSum / cueArea; outCue.y = (float)cueYSum / cueArea; outCue.isCue = true; foundCue = true; }
    if (targetArea > 200 && targetArea < 15000) { outTarget.x = (float)targetXSum / targetArea; outTarget.y = (float)targetYSum / targetArea; outTarget.isTarget = true; foundTarget = true; }
}

void AetherEngine::calculateGhostBallShot(float cueX, float cueY, float targetX, float targetY, float pocketX, float pocketY) {
    float dxPT = targetX - pocketX, dyPT = targetY - pocketY;
    float distPT = std::sqrt(dxPT * dxPT + dyPT * dyPT); if (distPT == 0.0f) distPT = 1.0f;
    float dirX = dxPT / distPT, dirY = dyPT / distPT;
    float ghostX = targetX + dirX * 60.0f, ghostY = targetY + dirY * 60.0f;
    float dxCG = ghostX - cueX, dyCG = ghostY - cueY;
    float distCG = std::sqrt(dxCG * dxCG + dyCG * dyCG); if (distCG == 0.0f) distCG = 1.0f;
    float cueDirX = dxCG / distCG, cueDirY = dyCG / distCG;
    float swipeDist = 400.0f;
    currentCommand.startX = cueX; currentCommand.startY = cueY;
    currentCommand.endX = cueX - (cueDirX * swipeDist); currentCommand.endY = cueY - (cueDirY * swipeDist);
    currentCommand.durationMs = 400; currentCommand.hasShot = true;
}

void AetherEngine::processFrame(uint8_t* pixels, int width, int height) {
    debugInfo.frameCount++;
    BallInfo cue, target; bool foundCue = false, foundTarget = false;
    detectBalls(pixels, width, height, cue, target, foundCue, foundTarget);

    debugInfo.foundCue = foundCue;
    debugInfo.foundTarget = foundTarget;
    debugInfo.cueX = foundCue ? cue.x : 0;
    debugInfo.cueY = foundCue ? cue.y : 0;
    debugInfo.targetX = foundTarget ? target.x : 0;
    debugInfo.targetY = foundTarget ? target.y : 0;

    if (foundCue && foundTarget) {
        float minDist = 999999.0f; int bestPocket = -1;
        for (int i = 0; i < 6; ++i) {
            float dx = target.x - pockets[i].x, dy = target.y - pockets[i].y;
            float dist = std::sqrt(dx * dx + dy * dy);
            if (dist < minDist) { minDist = dist; bestPocket = i; }
        }
        debugInfo.bestPocket = bestPocket;
        if (bestPocket != -1) calculateGhostBallShot(cue.x, cue.y, target.x, target.y, pockets[bestPocket].x, pockets[bestPocket].y);
    } else {
        debugInfo.bestPocket = -1;
    }
}

AetherCommand AetherEngine::getLatestCommand() {
    AetherCommand copy = currentCommand;
    currentCommand.hasShot.store(false);
    return copy;
}

DebugInfo AetherEngine::getDebugInfo() { return debugInfo; }
