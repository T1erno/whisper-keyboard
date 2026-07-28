cmake_minimum_required(VERSION 3.22.1)
project("whisper_native")

set(CMAKE_CXX_STANDARD 17)
set(CMAKE_CXX_STANDARD_REQUIRED ON)

# 16 KB page size alignment for Android 15+ devices & Google Play compliance
add_link_options("-Wl,-z,max-page-size=16384")

# Optimize for ARM64 NEON FP16 + DotProduct hardware vectorization specifically on arm64-v8a
if(ANDROID_ABI STREQUAL "arm64-v8a")
    add_compile_options(-O3 -flto -fno-finite-math-only -march=armv8.2-a+fp16+dotprod)
else()
    add_compile_options(-O3 -flto -fno-finite-math-only)
endif()

# Use native std::thread (pthreads) to avoid non-aligned prebuilt NDK libomp.so
set(GGML_OPENMP OFF CACHE BOOL "" FORCE)
set(WHISPER_BUILD_TESTS OFF CACHE BOOL "" FORCE)
set(WHISPER_BUILD_EXAMPLES OFF CACHE BOOL "" FORCE)
set(BUILD_SHARED_LIBS OFF CACHE BOOL "" FORCE)

# Include the whisper.cpp source directory
add_subdirectory(${CMAKE_CURRENT_SOURCE_DIR}/whisper.cpp ${CMAKE_CURRENT_BINARY_DIR}/whisper_build)

# Define shared library for JNI bindings
add_library(whisper_native SHARED
    whisper_jni.cpp
)

target_include_directories(whisper_native PRIVATE
    ${CMAKE_CURRENT_SOURCE_DIR}/whisper.cpp/include
    ${CMAKE_CURRENT_SOURCE_DIR}/whisper.cpp/ggml/include
)

target_link_libraries(whisper_native
    whisper
    android
    log
)
