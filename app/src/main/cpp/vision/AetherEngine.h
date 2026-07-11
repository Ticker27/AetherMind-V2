#pragma once
#include <cstdint>
#include <atomic>

struct AetherCommand {
    bool hasShot;
    float startX;
    float startY;
    float endX;
    float endY;
    int durationMs;
};

class AetherEngine {
private:
    uint8_t* binaryBuffer;
    int screenWidth;
    int screenHeight;
    AetherCommand currentCommand;

public:
    AetherEngine(int width, int height);
    ~AetherEngine();
    void processFrame(uint8_t* screenPixels, int width, int height);
    AetherCommand getLatestCommand();
};
