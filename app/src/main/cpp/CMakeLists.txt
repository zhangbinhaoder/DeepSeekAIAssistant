# ==============================================================================
# CMakeLists.txt - llama.cpp Android 集成
# ==============================================================================
# 
# 功能：
# 1. 检测并编译 llama.cpp 源码
# 2. 创建 JNI 绑定库 (llama_android)
# 3. 处理模拟模式（llama.cpp 不可用时）
#
# 使用方法：
# 1. 运行 setup_llama.bat 下载 llama.cpp 源码
# 2. 将项目放在纯英文路径
# 3. 执行 Gradle 构建
#
# ==============================================================================

cmake_minimum_required(VERSION 3.22.1)
project(llama_android VERSION 1.0.0 LANGUAGES CXX)

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
