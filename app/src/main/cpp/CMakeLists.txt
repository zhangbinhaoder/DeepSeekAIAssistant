# ==============================================================================
# CMakeLists.txt - llama.cpp Android 集成 + Agent Core 高性能模块
# ==============================================================================
# 
# 功能：
# 1. 检测并编译 llama.cpp 源码
# 2. 创建 JNI 绑定库 (llama_android)
# 3. 创建高性能 Agent Core 库 (agent_native)
# 4. 处理模拟模式（llama.cpp 不可用时）
#
# ==============================================================================

cmake_minimum_required(VERSION 3.22.1)
project(llama_android VERSION 1.0.0 LANGUAGES C CXX)

# ==============================================================================
# 编译器设置
# ==============================================================================

set(CMAKE_CXX_STANDARD 17)
set(CMAKE_CXX_STANDARD_REQUIRED ON)
set(CMAKE_CXX_EXTENSIONS OFF)

# 优化设置
if(CMAKE_BUILD_TYPE STREQUAL "Release")
    set(CMAKE_CXX_FLAGS "${CMAKE_CXX_FLAGS} -O3 -DNDEBUG")
    message(STATUS "Building in Release mode with optimizations")
else()
    set(CMAKE_CXX_FLAGS "${CMAKE_CXX_FLAGS} -O0 -g")
    message(STATUS "Building in Debug mode")
endif()

# ==============================================================================
# llama.cpp 检测和配置
# ==============================================================================

set(LLAMA_CPP_DIR ${CMAKE_SOURCE_DIR}/llama.cpp)
set(LLAMA_AVAILABLE FALSE)

message(STATUS "======================================")
message(STATUS "Checking for llama.cpp...")
message(STATUS "Expected path: ${LLAMA_CPP_DIR}")

if(EXISTS "${LLAMA_CPP_DIR}/CMakeLists.txt")
    message(STATUS "✓ Found llama.cpp at ${LLAMA_CPP_DIR}")
    
    # ==============================================================================
    # llama.cpp 构建选项 - 禁用不需要的功能以减少编译时间和 APK 大小
    # ==============================================================================
    
    # 禁用测试和示例
    set(LLAMA_BUILD_TESTS OFF CACHE BOOL "Disable llama.cpp tests" FORCE)
    set(LLAMA_BUILD_EXAMPLES OFF CACHE BOOL "Disable llama.cpp examples" FORCE)
    set(LLAMA_BUILD_SERVER OFF CACHE BOOL "Disable llama.cpp server" FORCE)
    
    # 禁用 GPU 后端（Android 主要用 CPU）
    set(GGML_METAL OFF CACHE BOOL "Disable Metal backend" FORCE)
    set(GGML_CUDA OFF CACHE BOOL "Disable CUDA backend" FORCE)
    set(GGML_VULKAN OFF CACHE BOOL "Disable Vulkan backend" FORCE)
    set(GGML_OPENCL OFF CACHE BOOL "Disable OpenCL backend" FORCE)
    set(GGML_HIPBLAS OFF CACHE BOOL "Disable HIP backend" FORCE)
    set(GGML_SYCL OFF CACHE BOOL "Disable SYCL backend" FORCE)
    
    # Android 特定优化
    set(GGML_NATIVE OFF CACHE BOOL "Disable native optimizations for cross-compilation" FORCE)
    
    # 添加 llama.cpp 作为子目录
    add_subdirectory(${LLAMA_CPP_DIR} llama.cpp)
    
    set(LLAMA_AVAILABLE TRUE)
    message(STATUS "✓ llama.cpp configured successfully")
    
else()
    message(WARNING "======================================")
    message(WARNING "llama.cpp NOT FOUND!")
    message(WARNING "Expected location: ${LLAMA_CPP_DIR}")
    message(WARNING "")
    message(WARNING "To enable real AI inference:")
    message(WARNING "  1. Run setup_llama.bat")
    message(WARNING "  2. Rebuild the project")
    message(WARNING "======================================")
    message(WARNING "Building in SIMULATION mode")
    set(LLAMA_AVAILABLE FALSE)
endif()

# ==============================================================================
# JNI 绑定库配置
# ==============================================================================

# 创建 JNI 绑定库
add_library(llama_android SHARED
    llama_android.cpp
)

# 设置库属性
set_target_properties(llama_android PROPERTIES
    CXX_STANDARD 17
    CXX_STANDARD_REQUIRED ON
)

# 查找 Android 日志库
find_library(log-lib log REQUIRED)

if(LLAMA_AVAILABLE)
    # 完整模式：链接 llama.cpp
    message(STATUS "======================================")
    message(STATUS "Building with llama.cpp support")
    message(STATUS "======================================")
    
    # 添加预处理器宏
    target_compile_definitions(llama_android PRIVATE LLAMA_AVAILABLE)
    
    # 包含目录
    target_include_directories(llama_android PRIVATE
        ${LLAMA_CPP_DIR}/include
        ${LLAMA_CPP_DIR}/ggml/include
        ${LLAMA_CPP_DIR}/common
    )
    
    # 链接库
    target_link_libraries(llama_android PRIVATE
        llama
        ${log-lib}
    )
    
else()
    # 模拟模式：只链接日志库
    message(STATUS "======================================")
    message(STATUS "Building in SIMULATION mode (no llama.cpp)")
    message(STATUS "======================================")
    
    target_link_libraries(llama_android PRIVATE
        ${log-lib}
    )
endif()

# ==============================================================================
# 构建信息输出
# ==============================================================================

message(STATUS "======================================")
message(STATUS "Build Configuration Summary:")
message(STATUS "  - Project: ${PROJECT_NAME}")
message(STATUS "  - Version: ${PROJECT_VERSION}")
message(STATUS "  - C++ Standard: ${CMAKE_CXX_STANDARD}")
message(STATUS "  - Build Type: ${CMAKE_BUILD_TYPE}")
message(STATUS "  - llama.cpp Available: ${LLAMA_AVAILABLE}")
message(STATUS "  - Target ABI: ${CMAKE_ANDROID_ARCH_ABI}")
message(STATUS "======================================")

# ==============================================================================
# Agent Core 高性能库 - SIMD/触控注入/高精度定时/二进制优化
# ==============================================================================

message(STATUS "Configuring Agent Core native library...")

# C 标准
add_library(agent_native SHARED
    simd_image.c
    simd_image_adv.c
    touch_inject.c
    precision_timer.c
    screen_memory.c
    agent_jni.c
    branchless_optim.c
    benchmark.c
    kernel_optimize.c
    hp_core.c
    vectorizer.c
)

# 包含二进制优化头文件目录
target_include_directories(agent_native PRIVATE ${CMAKE_CURRENT_SOURCE_DIR})

# 添加 ARM64 汇编文件 (只对 arm64-v8a 架构)
if(CMAKE_ANDROID_ARCH_ABI STREQUAL "arm64-v8a")
    # 启用汇编语言
    enable_language(ASM)
    
    # 添加汇编源文件
    target_sources(agent_native PRIVATE
        asm_core.S
        kernel_syscall.S
        ultra_optim.S
    )
    
    # 汇编编译选项
    set_source_files_properties(asm_core.S PROPERTIES
        LANGUAGE ASM
        COMPILE_FLAGS "-x assembler-with-cpp"
    )
    
    set_source_files_properties(ultra_optim.S PROPERTIES
        LANGUAGE ASM
        COMPILE_FLAGS "-x assembler-with-cpp"
    )
    
    set_source_files_properties(kernel_syscall.S PROPERTIES
        LANGUAGE ASM
        COMPILE_FLAGS "-x assembler-with-cpp"
    )
    
    message(STATUS "  - ARM64 Assembly enabled for arm64-v8a")
endif()

# 启用 NEON SIMD (ARM)
# 注意: arm64-v8a 默认启用 NEON，不需要 -mfpu 标志
# armeabi-v7a 需要显式启用 -mfpu=neon
if(CMAKE_ANDROID_ARCH_ABI STREQUAL "arm64-v8a")
    target_compile_definitions(agent_native PRIVATE HAS_NEON=1)
    message(STATUS "  - NEON SIMD enabled for arm64-v8a (native)")
elseif(CMAKE_ANDROID_ARCH_ABI STREQUAL "armeabi-v7a")
    target_compile_options(agent_native PRIVATE -mfpu=neon)
    target_compile_definitions(agent_native PRIVATE HAS_NEON=1)
    message(STATUS "  - NEON SIMD enabled for armeabi-v7a")
else()
    message(STATUS "  - NEON SIMD not available for ${CMAKE_ANDROID_ARCH_ABI}")
endif()

# ==============================================================================
# 二进制级优化配置
# ==============================================================================

# 基础优化标志
if(CMAKE_BUILD_TYPE STREQUAL "Release")
    target_compile_options(agent_native PRIVATE
        # 最高优化级别
        -O3
        # 快速数学运算（牺牲IEEE精度换取速度）
        -ffast-math
        # 循环展开
        -funroll-loops
        # 向量化
        -ftree-vectorize
        # 函数内联阈值提高
        -finline-limit=1000
        # 严格别名（允许更多优化）
        -fstrict-aliasing
        # 消除未使用代码
        -ffunction-sections
        -fdata-sections
        # 优化尾调用
        -foptimize-sibling-calls
    )
    
    # 链接时优化 (LTO)
    target_compile_options(agent_native PRIVATE -flto=thin)
    target_link_options(agent_native PRIVATE -flto=thin)
    
    # 消除未使用的段
    target_link_options(agent_native PRIVATE -Wl,--gc-sections)
    
    message(STATUS "  - Binary-level optimizations enabled")
    message(STATUS "    * LTO (Link-Time Optimization)")
    message(STATUS "    * Aggressive inlining")
    message(STATUS "    * Auto-vectorization")
endif()

# ==============================================================================
# PGO (Profile-Guided Optimization) 支持
# ==============================================================================
# 
# PGO 分两阶段:
# 1. 生成阶段: 编译带插桩的代码，运行收集性能数据
# 2. 使用阶段: 用性能数据重新编译，优化热点路径
#
# 使用方法:
# 阶段1 - 生成 profile:
#   cmake -DPGO_GENERATE=ON ..
#   adb push app.apk /data/local/tmp/
#   adb shell run-as <package> <运行测试>
#   adb pull /data/data/<package>/profile.profraw .
#   llvm-profdata merge -output=profile.profdata profile.profraw
#
# 阶段2 - 使用 profile:
#   cmake -DPGO_USE=ON -DPGO_PROFILE_PATH=/path/to/profile.profdata ..
# ==============================================================================

option(PGO_GENERATE "Generate PGO profile data" OFF)
option(PGO_USE "Use PGO profile data for optimization" OFF)
set(PGO_PROFILE_PATH "" CACHE PATH "Path to .profdata file for PGO")

if(PGO_GENERATE)
    message(STATUS "  - PGO: Profile generation mode enabled")
    target_compile_options(agent_native PRIVATE
        -fprofile-instr-generate
    )
    target_link_options(agent_native PRIVATE
        -fprofile-instr-generate
    )
elseif(PGO_USE AND EXISTS "${PGO_PROFILE_PATH}")
    message(STATUS "  - PGO: Using profile from ${PGO_PROFILE_PATH}")
    target_compile_options(agent_native PRIVATE
        -fprofile-instr-use=${PGO_PROFILE_PATH}
    )
    target_link_options(agent_native PRIVATE
        -fprofile-instr-use=${PGO_PROFILE_PATH}
    )
endif()

# 链接 Android 库
target_link_libraries(agent_native PRIVATE
    ${log-lib}
)

set_target_properties(agent_native PROPERTIES
    C_STANDARD 11
    C_STANDARD_REQUIRED ON
)

message(STATUS "✓ Agent Core native library configured")
