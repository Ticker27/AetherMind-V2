#pragma once
#include <cstdint>
#include <atomic>

struct AetherCommand {
    std::atomic<bool> hasShot;
    float startX;
    float startY;
    float endX;
    float endY;
    int durationMs;

    // std::atomic<> ไม่คัดลอกได้โดยอัตโนมัติ -> ต้องเขียน copy ctor เอง ไม่งั้น getLatestCommand() คอมไพล์พัง
    AetherCommand()
        : hasShot(false), startX(0), startY(0), endX(0), endY(0), durationMs(0) {}
    AetherCommand(const AetherCommand& o)
        : hasShot(o.hasShot.load()),
          startX(o.startX), startY(o.startY), endX(o.endX), endY(o.endY), durationMs(o.durationMs) {}
};

class AetherEngine {
private:
    uint8_t* binaryBuffer;
    int screenWidth;
    int screenHeight;
    AetherCommand currentCommand;

    void detectBalls(uint8_t* pixels, int width, int height, float& outCueX, float& outCueY, bool& foundCue);
    void calculateGhostBallShot(float cueX, float cueY, float targetX, float targetY);

public:
    AetherEngine(int width, int height);
    ~AetherEngine();

    void processFrame(uint8_t* screenPixels, int width, int height);
    AetherCommand getLatestCommand();
};
