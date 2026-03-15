cmake_minimum_required(VERSION 3.22)
project(aios_llm LANGUAGES C CXX)

set(LLAMA_CPP_DIR ${CMAKE_CURRENT_SOURCE_DIR}/llama.cpp)

# ---------------------------------------------------------------------------
# Build options – CPU-only for now.
# Future: set GGML_VULKAN or GGML_OPENCL to ON when GPU compute is desired.
# ---------------------------------------------------------------------------
set(GGML_VULKAN  OFF CACHE BOOL "Enable Vulkan backend (future)"  FORCE)
set(GGML_OPENCL  OFF CACHE BOOL "Enable OpenCL backend (future)"  FORCE)
set(GGML_CUDA    OFF CACHE BOOL "Disable CUDA (not for Android)"  FORCE)
set(GGML_METAL   OFF CACHE BOOL "Disable Metal (not for Android)" FORCE)

set(LLAMA_BUILD_TESTS    OFF CACHE BOOL "" FORCE)
set(LLAMA_BUILD_EXAMPLES OFF CACHE BOOL "" FORCE)
set(LLAMA_BUILD_SERVER   OFF CACHE BOOL "" FORCE)
set(BUILD_SHARED_LIBS    OFF CACHE BOOL "" FORCE)

# ---------------------------------------------------------------------------
# Compiler flags
# ---------------------------------------------------------------------------
set(CMAKE_C_FLAGS   "${CMAKE_C_FLAGS}   -O3 -DNDEBUG -DGGML_USE_CPU")
set(CMAKE_CXX_FLAGS "${CMAKE_CXX_FLAGS} -O3 -DNDEBUG -DGGML_USE_CPU -std=c++17")

# ---------------------------------------------------------------------------
# Add llama.cpp via its own CMakeLists.txt (builds ggml + llama as static libs)
# ---------------------------------------------------------------------------
add_subdirectory(${LLAMA_CPP_DIR} ${CMAKE_CURRENT_BINARY_DIR}/llama.cpp)

# ---------------------------------------------------------------------------
# JNI shared library
# ---------------------------------------------------------------------------
add_library(aios_llm SHARED
    ${CMAKE_CURRENT_SOURCE_DIR}/aios_llm_jni.cpp
)

target_include_directories(aios_llm PRIVATE
    ${LLAMA_CPP_DIR}/include
    ${LLAMA_CPP_DIR}/ggml/include
)

target_link_libraries(aios_llm PRIVATE
    llama
    ggml
    android
    log
)
