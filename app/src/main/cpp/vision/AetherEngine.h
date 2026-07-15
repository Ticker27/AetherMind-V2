#pragma once
#include <cstdint>
#include <atomic>

struct AetherCommand {
    std::atomic<bool> hasShot;
    float startX, startY, endX, endY;
    int durationMs;
    AetherCommand()
        : hasShot(false), startX(0), startY(0), endX(0), endY(0), durationMs(0) {}
    AetherCommand(const AetherCommand& o)
        : hasShot(o.hasShot.load()),
          startX(o.startX), startY(o.startY), endX(o.endX), endY(o.endY), durationMs(o.durationMs) {}
};

struct BallInfo { float x, y; bool isCue, isTarget; };
struct Pocket { float x, y; };

// Debug state sent back to Kotlin
struct DebugInfo {
    bool foundCue;
    bool foundTarget;
    float cueX, cueY;
    float targetX, targetY;
    int bestPocket;
    int frameCount;
};

class AetherEngine {
private:
    uint8_t* binaryBuffer;
    int screenWidth, screenHeight;
    AetherCommand currentCommand;
    Pocket pockets[6];
    DebugInfo debugInfo;
    void detectBalls(uint8_t* pixels, int width, int height, BallInfo& outCue, BallInfo& outTarget, bool& foundCue, bool& foundTarget);
    void calculateGhostBallShot(float cueX, float cueY, float targetX, float targetY, float pocketX, float pocketY);
public:
    AetherEngine(int width, int height);
    ~AetherEngine();
    void processFrame(uint8_t* screenPixels, int width, int height);
    AetherCommand getLatestCommand();
    DebugInfo getDebugInfo();
};
