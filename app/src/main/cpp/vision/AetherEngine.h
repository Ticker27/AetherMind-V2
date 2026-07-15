#pragma once
#include <cstdint>
#include <atomic>

struct AetherCommand {
    std::atomic<bool> hasShot;
    float startX, startY, endX, endY;
    int durationMs;
};

struct BallInfo { float x, y; bool isCue, isTarget; };
struct Pocket { float x, y; };

class AetherEngine {
private:
    uint8_t* binaryBuffer;
    int screenWidth, screenHeight;
    AetherCommand currentCommand;
    Pocket pockets[6];
    void detectBalls(uint8_t* pixels, int width, int height, BallInfo& outCue, BallInfo& outTarget, bool& foundCue, bool& foundTarget);
    void calculateGhostBallShot(float cueX, float cueY, float targetX, float targetY, float pocketX, float pocketY);
public:
    AetherEngine(int width, int height);
    ~AetherEngine();
    void processFrame(uint8_t* screenPixels, int width, int height);
    AetherCommand getLatestCommand();
};
