cmake_minimum_required(VERSION 3.10.2)
project(aether_core CXX)

set(CMAKE_CXX_STANDARD 17)
set(CMAKE_CXX_STANDARD_REQUIRED ON)

add_library(aether_core SHARED
    vision/AetherEngine.cpp
    vision/aether_jni_bridge.cpp
)

target_include_directories(aether_core PRIVATE
    ${CMAKE_CURRENT_SOURCE_DIR}/vision
)

# ตั้งค่า Compiler Flags สำหรับ Performance สูงสุด
target_compile_options(aether_core PRIVATE
    -O3
    -ffast-math
    -fno-exceptions
    -fno-rtti
    -funroll-loops
    -fomit-frame-pointer
)

find_library(log-lib log)
target_link_libraries(aether_core ${log-lib})
