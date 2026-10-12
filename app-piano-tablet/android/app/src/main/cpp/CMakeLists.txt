cmake_minimum_required(VERSION 3.22.1)
project(pianoengine LANGUAGES CXX)

set(CMAKE_CXX_STANDARD 17)
set(CMAKE_CXX_STANDARD_REQUIRED ON)

add_library(pianoengine SHARED
    engine.cpp
    audio_stream.cpp
    jni_bridge.cpp)

target_compile_options(pianoengine PRIVATE -O3 -Wall -Wextra -Wno-unused-parameter)
target_link_libraries(pianoengine aaudio log android)
