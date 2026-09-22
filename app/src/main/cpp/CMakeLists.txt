cmake_minimum_required(VERSION 3.22.1)

project("localgguf_native")

set(CMAKE_CXX_STANDARD 17)
set(CMAKE_CXX_STANDARD_REQUIRED ON)

add_definitions(-DGGML_USE_CPU)
if(ANDROID_ABI STREQUAL "arm64-v8a")
    add_definitions(-D__ARM_NEON -D__ARM_FEATURE_DOTPROD)
endif()

find_library(log-lib log)
find_library(android-lib android)

add_library(
    llama-android
    SHARED
    llama-bridge.cpp
)

target_include_directories(
    llama-android
    PRIVATE
    ${CMAKE_CURRENT_SOURCE_DIR}
)

target_link_libraries(
    llama-android
    ${log-lib}
    ${android-lib}
)
