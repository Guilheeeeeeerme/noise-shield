cmake_minimum_required(VERSION 3.22.1)
project(noise_shield_audio)

set(CMAKE_CXX_STANDARD 17)
set(CMAKE_CXX_STANDARD_REQUIRED ON)

include(FetchContent)
FetchContent_Declare(
    oboe
    GIT_REPOSITORY https://github.com/google/oboe.git
    GIT_TAG 1.9.0
)
FetchContent_MakeAvailable(oboe)

add_library(
    noise_shield_audio
    SHARED
    jni/native_bridge.cpp
    player/masking_player.cpp
    capture/mic_capture.cpp
    analysis/heuristic_analyzer.cpp
    analysis/noise_synth.cpp
)

target_include_directories(
    noise_shield_audio
    PRIVATE
    ${CMAKE_CURRENT_SOURCE_DIR}
)

target_link_libraries(
    noise_shield_audio
    oboe
    android
    log
)
