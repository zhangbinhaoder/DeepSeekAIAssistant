/**
 * Agent JNI Bridge - Kotlin/Java 与 C 核心模块的桥接层
 * 
 * 将 NativeAgentCore.kt 中的 JNI 调用转发到底层 C 函数
 * 
 * @author DeepSeek AI Assistant
 */

#include <jni.h>
#include <android/log.h>
#include <string.h>
#include <stdlib.h>
#include "agent_core.h"

#define TAG "AgentJNI"
#define LOGD(...) __android_log_print(ANDROID_LOG_DEBUG, TAG, __VA_ARGS__)
#define LOGE(...) __android_log_print(ANDROID_LOG_ERROR, TAG, __VA_ARGS__)

// ============================================================================
// NativeAgentCore - 初始化和信息
// ============================================================================

JNIEXPORT jint JNICALL
Java_com_example_deepseekaiassistant_agent_NativeAgentCore_nativeInit(
        JNIEnv *env, jobject thiz) {
    LOGD("Initializing Agent Core...");
    return agent_core_init();
}

JNIEXPORT void JNICALL
Java_com_example_deepseekaiassistant_agent_NativeAgentCore_nativeCleanup(
        JNIEnv *env, jobject thiz) {
    LOGD("Cleaning up Agent Core...");
    agent_core_cleanup();
}

JNIEXPORT jboolean JNICALL
Java_com_example_deepseekaiassistant_agent_NativeAgentCore_nativeHasSimd(
        JNIEnv *env, jobject thiz) {
    return agent_core_has_simd();
}

JNIEXPORT jstring JNICALL
Java_com_example_deepseekaiassistant_agent_NativeAgentCore_nativeGetVersion(
        JNIEnv *env, jobject thiz) {
    const char* version = agent_core_version();
    return (*env)->NewStringUTF(env, version);
}

// ============================================================================
// SimdImageEngine - SIMD 加速图像处理
// ============================================================================

JNIEXPORT jint JNICALL
Java_com_example_deepseekaiassistant_agent_SimdImageEngine_nativeArgbToGrayscale(
        JNIEnv *env, jobject thiz,
        jbyteArray src, jbyteArray dst, jint width, jint height) {
    
    jbyte* srcData = (*env)->GetByteArrayElements(env, src, NULL);
    jbyte* dstData = (*env)->GetByteArrayElements(env, dst, NULL);
    
    if (!srcData || !dstData) {
        if (srcData) (*env)->ReleaseByteArrayElements(env, src, srcData, JNI_ABORT);
        if (dstData) (*env)->ReleaseByteArrayElements(env, dst, dstData, JNI_ABORT);
        return -1;
    }
    
    int result = simd_argb_to_grayscale((const uint8_t*)srcData, (uint8_t*)dstData, width, height);
    
    (*env)->ReleaseByteArrayElements(env, src, srcData, JNI_ABORT);
    (*env)->ReleaseByteArrayElements(env, dst, dstData, 0);
    
    return result;
}

JNIEXPORT jint JNICALL
Java_com_example_deepseekaiassistant_agent_SimdImageEngine_nativeDetectRedRegions(
        JNIEnv *env, jobject thiz,
        jbyteArray src, jint width, jint height,
        jintArray output, jint maxElements) {
    
    jbyte* srcData = (*env)->GetByteArrayElements(env, src, NULL);
    jint* outData = (*env)->GetIntArrayElements(env, output, NULL);
    
    if (!srcData || !outData) {
        if (srcData) (*env)->ReleaseByteArrayElements(env, src, srcData, JNI_ABORT);
        if (outData) (*env)->ReleaseIntArrayElements(env, output, outData, JNI_ABORT);
        return 0;
    }
    
    DetectedElement* elements = (DetectedElement*)malloc(maxElements * sizeof(DetectedElement));
    if (!elements) {
        (*env)->ReleaseByteArrayElements(env, src, srcData, JNI_ABORT);
        (*env)->ReleaseIntArrayElements(env, output, outData, JNI_ABORT);
        return 0;
    }
    
    int count = simd_detect_red_regions((const uint8_t*)srcData, width, height, elements, maxElements);
    
    // 转换为输出数组格式: [x, y, width, height, confidence_bits, 0] * count
    for (int i = 0; i < count && i < maxElements; i++) {
        outData[i * 6 + 0] = elements[i].bounds.x;
        outData[i * 6 + 1] = elements[i].bounds.y;
        outData[i * 6 + 2] = elements[i].bounds.width;
        outData[i * 6 + 3] = elements[i].bounds.height;
        // 将 float 转换为 int bits
        union { float f; int32_t i; } conv;
        conv.f = elements[i].confidence;
        outData[i * 6 + 4] = conv.i;
        outData[i * 6 + 5] = 0;
    }
    
    free(elements);
    (*env)->ReleaseByteArrayElements(env, src, srcData, JNI_ABORT);
    (*env)->ReleaseIntArrayElements(env, output, outData, 0);
    
    return count;
}

JNIEXPORT jint JNICALL
Java_com_example_deepseekaiassistant_agent_SimdImageEngine_nativeDetectBlueRegions(
        JNIEnv *env, jobject thiz,
        jbyteArray src, jint width, jint height,
        jintArray output, jint maxElements) {
    
    jbyte* srcData = (*env)->GetByteArrayElements(env, src, NULL);
    jint* outData = (*env)->GetIntArrayElements(env, output, NULL);
    
    if (!srcData || !outData) {
        if (srcData) (*env)->ReleaseByteArrayElements(env, src, srcData, JNI_ABORT);
        if (outData) (*env)->ReleaseIntArrayElements(env, output, outData, JNI_ABORT);
        return 0;
    }
    
    DetectedElement* elements = (DetectedElement*)malloc(maxElements * sizeof(DetectedElement));
    if (!elements) {
        (*env)->ReleaseByteArrayElements(env, src, srcData, JNI_ABORT);
        (*env)->ReleaseIntArrayElements(env, output, outData, JNI_ABORT);
        return 0;
    }
    
    int count = simd_detect_blue_regions((const uint8_t*)srcData, width, height, elements, maxElements);
    
    for (int i = 0; i < count && i < maxElements; i++) {
        outData[i * 6 + 0] = elements[i].bounds.x;
        outData[i * 6 + 1] = elements[i].bounds.y;
        outData[i * 6 + 2] = elements[i].bounds.width;
        outData[i * 6 + 3] = elements[i].bounds.height;
        union { float f; int32_t i; } conv;
        conv.f = elements[i].confidence;
        outData[i * 6 + 4] = conv.i;
        outData[i * 6 + 5] = 0;
    }
    
    free(elements);
    (*env)->ReleaseByteArrayElements(env, src, srcData, JNI_ABORT);
    (*env)->ReleaseIntArrayElements(env, output, outData, 0);
    
    return count;
}

JNIEXPORT jint JNICALL
Java_com_example_deepseekaiassistant_agent_SimdImageEngine_nativeDetectGreenRegions(
        JNIEnv *env, jobject thiz,
        jbyteArray src, jint width, jint height,
        jintArray output, jint maxElements) {
    
    jbyte* srcData = (*env)->GetByteArrayElements(env, src, NULL);
    jint* outData = (*env)->GetIntArrayElements(env, output, NULL);
    
    if (!srcData || !outData) {
        if (srcData) (*env)->ReleaseByteArrayElements(env, src, srcData, JNI_ABORT);
        if (outData) (*env)->ReleaseIntArrayElements(env, output, outData, JNI_ABORT);
        return 0;
    }
    
    DetectedElement* elements = (DetectedElement*)malloc(maxElements * sizeof(DetectedElement));
    if (!elements) {
        (*env)->ReleaseByteArrayElements(env, src, srcData, JNI_ABORT);
        (*env)->ReleaseIntArrayElements(env, output, outData, JNI_ABORT);
        return 0;
    }
    
    int count = simd_detect_green_regions((const uint8_t*)srcData, width, height, elements, maxElements);
    
    for (int i = 0; i < count && i < maxElements; i++) {
        outData[i * 6 + 0] = elements[i].bounds.x;
        outData[i * 6 + 1] = elements[i].bounds.y;
        outData[i * 6 + 2] = elements[i].bounds.width;
        outData[i * 6 + 3] = elements[i].bounds.height;
        union { float f; int32_t i; } conv;
        conv.f = elements[i].confidence;
        outData[i * 6 + 4] = conv.i;
        outData[i * 6 + 5] = 0;
    }
    
    free(elements);
    (*env)->ReleaseByteArrayElements(env, src, srcData, JNI_ABORT);
    (*env)->ReleaseIntArrayElements(env, output, outData, 0);
    
    return count;
}

JNIEXPORT jint JNICALL
Java_com_example_deepseekaiassistant_agent_SimdImageEngine_nativeImageDiff(
        JNIEnv *env, jobject thiz,
        jbyteArray img1, jbyteArray img2, jint pixelCount, jint threshold) {
    
    jbyte* data1 = (*env)->GetByteArrayElements(env, img1, NULL);
    jbyte* data2 = (*env)->GetByteArrayElements(env, img2, NULL);
    
    if (!data1 || !data2) {
        if (data1) (*env)->ReleaseByteArrayElements(env, img1, data1, JNI_ABORT);
        if (data2) (*env)->ReleaseByteArrayElements(env, img2, data2, JNI_ABORT);
        return -1;
    }
    
    // 创建临时差异缓冲区
    uint8_t* diff = (uint8_t*)malloc(pixelCount);
    if (!diff) {
        (*env)->ReleaseByteArrayElements(env, img1, data1, JNI_ABORT);
        (*env)->ReleaseByteArrayElements(env, img2, data2, JNI_ABORT);
        return -1;
    }
    
    int result = simd_image_diff((const uint8_t*)data1, (const uint8_t*)data2, diff, pixelCount, threshold);
    
    free(diff);
    (*env)->ReleaseByteArrayElements(env, img1, data1, JNI_ABORT);
    (*env)->ReleaseByteArrayElements(env, img2, data2, JNI_ABORT);
    
    return result;
}

JNIEXPORT jint JNICALL
Java_com_example_deepseekaiassistant_agent_SimdImageEngine_nativeBoxBlur(
        JNIEnv *env, jobject thiz,
        jbyteArray src, jbyteArray dst, jint width, jint height, jint radius) {
    
    jbyte* srcData = (*env)->GetByteArrayElements(env, src, NULL);
    jbyte* dstData = (*env)->GetByteArrayElements(env, dst, NULL);
    
    if (!srcData || !dstData) {
        if (srcData) (*env)->ReleaseByteArrayElements(env, src, srcData, JNI_ABORT);
        if (dstData) (*env)->ReleaseByteArrayElements(env, dst, dstData, JNI_ABORT);
        return -1;
    }
    
    int result = simd_box_blur((const uint8_t*)srcData, (uint8_t*)dstData, width, height, radius);
    
    (*env)->ReleaseByteArrayElements(env, src, srcData, JNI_ABORT);
    (*env)->ReleaseByteArrayElements(env, dst, dstData, 0);
    
    return result;
}

// ============================================================================
// TouchInjector - 触控注入
// ============================================================================

JNIEXPORT jint JNICALL
Java_com_example_deepseekaiassistant_agent_TouchInjector_nativeOpenDevice(
        JNIEnv *env, jobject thiz, jstring path) {
    
    const char* pathStr = (*env)->GetStringUTFChars(env, path, NULL);
    int fd = touch_open_device(pathStr);
    (*env)->ReleaseStringUTFChars(env, path, pathStr);
    
    return fd;
}

JNIEXPORT void JNICALL
Java_com_example_deepseekaiassistant_agent_TouchInjector_nativeCloseDevice(
        JNIEnv *env, jobject thiz, jint fd) {
    touch_close_device(fd);
}

JNIEXPORT jint JNICALL
Java_com_example_deepseekaiassistant_agent_TouchInjector_nativeFindTouchDevice(
        JNIEnv *env, jobject thiz, jbyteArray pathOut, jint pathSize) {
    
    jbyte* pathData = (*env)->GetByteArrayElements(env, pathOut, NULL);
    if (!pathData) return -1;
    
    int result = touch_find_device((char*)pathData, pathSize);
    
    (*env)->ReleaseByteArrayElements(env, pathOut, pathData, 0);
    return result;
}

JNIEXPORT jint JNICALL
Java_com_example_deepseekaiassistant_agent_TouchInjector_nativeInjectDown(
        JNIEnv *env, jobject thiz, jint fd, jint x, jint y) {
    return touch_inject_down(fd, x, y);
}

JNIEXPORT jint JNICALL
Java_com_example_deepseekaiassistant_agent_TouchInjector_nativeInjectUp(
        JNIEnv *env, jobject thiz, jint fd, jint x, jint y) {
    return touch_inject_up(fd, x, y);
}

JNIEXPORT jint JNICALL
Java_com_example_deepseekaiassistant_agent_TouchInjector_nativeInjectMove(
        JNIEnv *env, jobject thiz, jint fd, jint x, jint y) {
    return touch_inject_move(fd, x, y);
}

JNIEXPORT jint JNICALL
Java_com_example_deepseekaiassistant_agent_TouchInjector_nativeInjectTap(
        JNIEnv *env, jobject thiz, jint fd, jint x, jint y, jint durationUs) {
    return touch_inject_tap(fd, x, y, durationUs);
}

JNIEXPORT jint JNICALL
Java_com_example_deepseekaiassistant_agent_TouchInjector_nativeInjectSwipe(
        JNIEnv *env, jobject thiz,
        jint fd, jint x1, jint y1, jint x2, jint y2, jint durationUs, jint steps) {
    return touch_inject_swipe(fd, x1, y1, x2, y2, durationUs, steps);
}

JNIEXPORT jint JNICALL
Java_com_example_deepseekaiassistant_agent_TouchInjector_nativeInjectPinch(
        JNIEnv *env, jobject thiz,
        jint fd, jint centerX, jint centerY, jint startDist, jint endDist,
        jint durationUs, jint steps) {
    return touch_inject_pinch(fd, centerX, centerY, startDist, endDist, durationUs, steps);
}

JNIEXPORT jint JNICALL
Java_com_example_deepseekaiassistant_agent_TouchInjector_nativeInjectMulti(
        JNIEnv *env, jobject thiz,
        jint fd, jintArray points, jint count, jint type) {
    
    jint* pointsData = (*env)->GetIntArrayElements(env, points, NULL);
    if (!pointsData) return -1;
    
    // 转换为 TouchPoint 数组
    TouchPoint* touchPoints = (TouchPoint*)malloc(count * sizeof(TouchPoint));
    if (!touchPoints) {
        (*env)->ReleaseIntArrayElements(env, points, pointsData, JNI_ABORT);
        return -1;
    }
    
    for (int i = 0; i < count; i++) {
        touchPoints[i].id = pointsData[i * 4 + 0];
        touchPoints[i].x = pointsData[i * 4 + 1];
        touchPoints[i].y = pointsData[i * 4 + 2];
        touchPoints[i].pressure = pointsData[i * 4 + 3];
        touchPoints[i].size = 1;
    }
    
    int result = touch_inject_multi(fd, touchPoints, count, (TouchEventType)type);
    
    free(touchPoints);
    (*env)->ReleaseIntArrayElements(env, points, pointsData, JNI_ABORT);
    
    return result;
}

// ============================================================================
// PrecisionTimer - 高精度定时
// ============================================================================

JNIEXPORT jlong JNICALL
Java_com_example_deepseekaiassistant_agent_PrecisionTimer_nativeNowNs(
        JNIEnv *env, jobject thiz) {
    return (jlong)timer_now_ns();
}

JNIEXPORT jlong JNICALL
Java_com_example_deepseekaiassistant_agent_PrecisionTimer_nativeNowUs(
        JNIEnv *env, jobject thiz) {
    return (jlong)timer_now_us();
}

JNIEXPORT jlong JNICALL
Java_com_example_deepseekaiassistant_agent_PrecisionTimer_nativeNowMs(
        JNIEnv *env, jobject thiz) {
    return (jlong)timer_now_ms();
}

JNIEXPORT void JNICALL
Java_com_example_deepseekaiassistant_agent_PrecisionTimer_nativeSleepNs(
        JNIEnv *env, jobject thiz, jlong ns) {
    timer_sleep_ns((uint64_t)ns);
}

JNIEXPORT void JNICALL
Java_com_example_deepseekaiassistant_agent_PrecisionTimer_nativeSleepUs(
        JNIEnv *env, jobject thiz, jlong us) {
    timer_sleep_us((uint64_t)us);
}

JNIEXPORT void JNICALL
Java_com_example_deepseekaiassistant_agent_PrecisionTimer_nativeSleepMs(
        JNIEnv *env, jobject thiz, jlong ms) {
    timer_sleep_ms((uint64_t)ms);
}

JNIEXPORT jint JNICALL
Java_com_example_deepseekaiassistant_agent_PrecisionTimer_nativeCreateIntervalTimer(
        JNIEnv *env, jobject thiz, jlong intervalUs) {
    return timer_create_interval((uint64_t)intervalUs);
}

JNIEXPORT jint JNICALL
Java_com_example_deepseekaiassistant_agent_PrecisionTimer_nativeWaitNextTimer(
        JNIEnv *env, jobject thiz, jint handle) {
    return timer_wait_next(handle);
}

JNIEXPORT void JNICALL
Java_com_example_deepseekaiassistant_agent_PrecisionTimer_nativeDestroyTimer(
        JNIEnv *env, jobject thiz, jint handle) {
    timer_destroy(handle);
}

// ============================================================================
// FastScreenCapture - 快速截图
// ============================================================================

JNIEXPORT jint JNICALL
Java_com_example_deepseekaiassistant_agent_FastScreenCapture_nativeGetScreenInfo(
        JNIEnv *env, jobject thiz, jintArray info) {
    
    jint* infoData = (*env)->GetIntArrayElements(env, info, NULL);
    if (!infoData) return -1;
    
    ScreenInfo screenInfo;
    int result = screen_get_info(&screenInfo);
    
    if (result == 0) {
        infoData[0] = screenInfo.width;
        infoData[1] = screenInfo.height;
        infoData[2] = screenInfo.stride;
        infoData[3] = screenInfo.format;
        infoData[4] = screenInfo.bpp;
    }
    
    (*env)->ReleaseIntArrayElements(env, info, infoData, 0);
    return result;
}

JNIEXPORT jint JNICALL
Java_com_example_deepseekaiassistant_agent_FastScreenCapture_nativeCaptureScreen(
        JNIEnv *env, jobject thiz, jbyteArray buffer, jint bufferSize) {
    
    jbyte* bufferData = (*env)->GetByteArrayElements(env, buffer, NULL);
    if (!bufferData) return -1;
    
    int result = screen_capture((uint8_t*)bufferData, (size_t)bufferSize, NULL);
    
    (*env)->ReleaseByteArrayElements(env, buffer, bufferData, 0);
    return result;
}

JNIEXPORT jint JNICALL
Java_com_example_deepseekaiassistant_agent_FastScreenCapture_nativeCaptureRegion(
        JNIEnv *env, jobject thiz,
        jbyteArray buffer, jint bufferSize,
        jint x, jint y, jint width, jint height) {
    
    jbyte* bufferData = (*env)->GetByteArrayElements(env, buffer, NULL);
    if (!bufferData) return -1;
    
    int result = screen_capture_region((uint8_t*)bufferData, (size_t)bufferSize, x, y, width, height);
    
    (*env)->ReleaseByteArrayElements(env, buffer, bufferData, 0);
    return result;
}

// ============================================================================
// MemorySearchEngine - 内存搜索
// ============================================================================

JNIEXPORT jint JNICALL
Java_com_example_deepseekaiassistant_agent_MemorySearchEngine_nativeSearchPattern(
        JNIEnv *env, jobject thiz,
        jint pid, jlong startAddr, jlong endAddr,
        jbyteArray pattern, jint patternLen,
        jlongArray results, jint maxResults) {
    
    jbyte* patternData = (*env)->GetByteArrayElements(env, pattern, NULL);
    jlong* resultsData = (*env)->GetLongArrayElements(env, results, NULL);
    
    if (!patternData || !resultsData) {
        if (patternData) (*env)->ReleaseByteArrayElements(env, pattern, patternData, JNI_ABORT);
        if (resultsData) (*env)->ReleaseLongArrayElements(env, results, resultsData, JNI_ABORT);
        return 0;
    }
    
    uint64_t* resultsBuffer = (uint64_t*)malloc(maxResults * sizeof(uint64_t));
    if (!resultsBuffer) {
        (*env)->ReleaseByteArrayElements(env, pattern, patternData, JNI_ABORT);
        (*env)->ReleaseLongArrayElements(env, results, resultsData, JNI_ABORT);
        return 0;
    }
    
    int count = memory_search_pattern(pid, (uint64_t)startAddr, (uint64_t)endAddr,
                                       (const uint8_t*)patternData, (size_t)patternLen,
                                       resultsBuffer, maxResults);
    
    for (int i = 0; i < count && i < maxResults; i++) {
        resultsData[i] = (jlong)resultsBuffer[i];
    }
    
    free(resultsBuffer);
    (*env)->ReleaseByteArrayElements(env, pattern, patternData, JNI_ABORT);
    (*env)->ReleaseLongArrayElements(env, results, resultsData, 0);
    
    return count;
}

JNIEXPORT jint JNICALL
Java_com_example_deepseekaiassistant_agent_MemorySearchEngine_nativeReadMemory(
        JNIEnv *env, jobject thiz,
        jint pid, jlong address, jbyteArray buffer, jint size) {
    
    jbyte* bufferData = (*env)->GetByteArrayElements(env, buffer, NULL);
    if (!bufferData) return -1;
    
    int result = memory_read(pid, (uint64_t)address, bufferData, (size_t)size);
    
    (*env)->ReleaseByteArrayElements(env, buffer, bufferData, 0);
    return result;
}

JNIEXPORT jint JNICALL
Java_com_example_deepseekaiassistant_agent_MemorySearchEngine_nativeWriteMemory(
        JNIEnv *env, jobject thiz,
        jint pid, jlong address, jbyteArray data, jint size) {
    
    jbyte* dataBuffer = (*env)->GetByteArrayElements(env, data, NULL);
    if (!dataBuffer) return -1;
    
    int result = memory_write(pid, (uint64_t)address, dataBuffer, (size_t)size);
    
    (*env)->ReleaseByteArrayElements(env, data, dataBuffer, JNI_ABORT);
    return result;
}

// ============================================================================
// PerformanceUtils - 性能工具
// ============================================================================

JNIEXPORT jint JNICALL
Java_com_example_deepseekaiassistant_agent_PerformanceUtils_nativeSetRealtimePriority(
        JNIEnv *env, jobject thiz) {
    return set_realtime_priority();
}

JNIEXPORT jint JNICALL
Java_com_example_deepseekaiassistant_agent_PerformanceUtils_nativeSetCpuAffinity(
        JNIEnv *env, jobject thiz, jint cpuId) {
    return set_cpu_affinity(cpuId);
}

JNIEXPORT jint JNICALL
Java_com_example_deepseekaiassistant_agent_PerformanceUtils_nativeLockCpuFrequency(
        JNIEnv *env, jobject thiz) {
    return lock_cpu_frequency();
}

// ============================================================================
// 极致优化函数 - 汇编级别
// ============================================================================

#if defined(__aarch64__)
// ARM64 汇编函数声明
extern void asm_neon_memset_64(void* dst, uint8_t value, size_t n);
extern void asm_neon_zero_64(void* dst, size_t n);
extern void asm_neon_memcpy_64(void* dst, const void* src, size_t n);
extern int asm_neon_memcmp_16(const void* s1, const void* s2, size_t n);
extern uint32_t asm_crc32_buffer(const void* data, size_t len, uint32_t init);
extern uint32_t asm_xxhash32(const void* data, size_t len, uint32_t seed);
extern int asm_clz32(uint32_t x);
extern int asm_clz64(uint64_t x);
extern int asm_popcount32(uint32_t x);
extern int asm_popcount64(uint64_t x);
extern uint32_t asm_byteswap32(uint32_t x);
extern uint64_t asm_byteswap64(uint64_t x);
extern size_t asm_strlen(const char* s);
extern int64_t asm_atoi_fast(const char* s);
extern uint64_t asm_hex_to_u64(const char* s);
extern float asm_neon_dot_f32(const float* a, const float* b, int count);
extern float asm_neon_sum_f32(const float* a, int count);
extern uint64_t asm_read_cycle_counter(void);
extern uint64_t asm_read_cycle_freq(void);
#endif

// 快速内存清零
JNIEXPORT void JNICALL
Java_com_example_deepseekaiassistant_agent_NativeAgentCore_nativeFastZero(
        JNIEnv *env, jobject thiz, jbyteArray buffer) {
#if defined(__aarch64__)
    jbyte* data = (*env)->GetByteArrayElements(env, buffer, NULL);
    if (data) {
        jsize len = (*env)->GetArrayLength(env, buffer);
        asm_neon_zero_64(data, (size_t)len);
        (*env)->ReleaseByteArrayElements(env, buffer, data, 0);
    }
#else
    jbyte* data = (*env)->GetByteArrayElements(env, buffer, NULL);
    if (data) {
        jsize len = (*env)->GetArrayLength(env, buffer);
        memset(data, 0, len);
        (*env)->ReleaseByteArrayElements(env, buffer, data, 0);
    }
#endif
}

// 快速内存复制
JNIEXPORT void JNICALL
Java_com_example_deepseekaiassistant_agent_NativeAgentCore_nativeFastCopy(
        JNIEnv *env, jobject thiz, jbyteArray dst, jbyteArray src) {
#if defined(__aarch64__)
    jbyte* dstData = (*env)->GetByteArrayElements(env, dst, NULL);
    jbyte* srcData = (*env)->GetByteArrayElements(env, src, NULL);
    if (dstData && srcData) {
        jsize len = (*env)->GetArrayLength(env, src);
        asm_neon_memcpy_64(dstData, srcData, (size_t)len);
    }
    if (srcData) (*env)->ReleaseByteArrayElements(env, src, srcData, JNI_ABORT);
    if (dstData) (*env)->ReleaseByteArrayElements(env, dst, dstData, 0);
#else
    jbyte* dstData = (*env)->GetByteArrayElements(env, dst, NULL);
    jbyte* srcData = (*env)->GetByteArrayElements(env, src, NULL);
    if (dstData && srcData) {
        jsize len = (*env)->GetArrayLength(env, src);
        memcpy(dstData, srcData, len);
    }
    if (srcData) (*env)->ReleaseByteArrayElements(env, src, srcData, JNI_ABORT);
    if (dstData) (*env)->ReleaseByteArrayElements(env, dst, dstData, 0);
#endif
}

// CRC32 校验
JNIEXPORT jint JNICALL
Java_com_example_deepseekaiassistant_agent_NativeAgentCore_nativeCrc32(
        JNIEnv *env, jobject thiz, jbyteArray data) {
#if defined(__aarch64__)
    jbyte* bytes = (*env)->GetByteArrayElements(env, data, NULL);
    jint result = 0;
    if (bytes) {
        jsize len = (*env)->GetArrayLength(env, data);
        result = (jint)asm_crc32_buffer(bytes, (size_t)len, 0xFFFFFFFF);
        (*env)->ReleaseByteArrayElements(env, data, bytes, JNI_ABORT);
    }
    return result;
#else
    // 备用实现
    return 0;
#endif
}

// 快速哈希
JNIEXPORT jint JNICALL
Java_com_example_deepseekaiassistant_agent_NativeAgentCore_nativeXxHash32(
        JNIEnv *env, jobject thiz, jbyteArray data, jint seed) {
#if defined(__aarch64__)
    jbyte* bytes = (*env)->GetByteArrayElements(env, data, NULL);
    jint result = 0;
    if (bytes) {
        jsize len = (*env)->GetArrayLength(env, data);
        result = (jint)asm_xxhash32(bytes, (size_t)len, (uint32_t)seed);
        (*env)->ReleaseByteArrayElements(env, data, bytes, JNI_ABORT);
    }
    return result;
#else
    return 0;
#endif
}

// 快速字符串长度
JNIEXPORT jint JNICALL
Java_com_example_deepseekaiassistant_agent_NativeAgentCore_nativeFastStrlen(
        JNIEnv *env, jobject thiz, jstring str) {
#if defined(__aarch64__)
    const char* cstr = (*env)->GetStringUTFChars(env, str, NULL);
    jint result = 0;
    if (cstr) {
        result = (jint)asm_strlen(cstr);
        (*env)->ReleaseStringUTFChars(env, str, cstr);
    }
    return result;
#else
    const char* cstr = (*env)->GetStringUTFChars(env, str, NULL);
    jint result = 0;
    if (cstr) {
        result = (jint)strlen(cstr);
        (*env)->ReleaseStringUTFChars(env, str, cstr);
    }
    return result;
#endif
}

// 快速整数解析
JNIEXPORT jlong JNICALL
Java_com_example_deepseekaiassistant_agent_NativeAgentCore_nativeFastAtoi(
        JNIEnv *env, jobject thiz, jstring str) {
#if defined(__aarch64__)
    const char* cstr = (*env)->GetStringUTFChars(env, str, NULL);
    jlong result = 0;
    if (cstr) {
        result = (jlong)asm_atoi_fast(cstr);
        (*env)->ReleaseStringUTFChars(env, str, cstr);
    }
    return result;
#else
    const char* cstr = (*env)->GetStringUTFChars(env, str, NULL);
    jlong result = 0;
    if (cstr) {
        result = strtoll(cstr, NULL, 10);
        (*env)->ReleaseStringUTFChars(env, str, cstr);
    }
    return result;
#endif
}

// 快速十六进制解析
JNIEXPORT jlong JNICALL
Java_com_example_deepseekaiassistant_agent_NativeAgentCore_nativeFastHexToLong(
        JNIEnv *env, jobject thiz, jstring str) {
#if defined(__aarch64__)
    const char* cstr = (*env)->GetStringUTFChars(env, str, NULL);
    jlong result = 0;
    if (cstr) {
        result = (jlong)asm_hex_to_u64(cstr);
        (*env)->ReleaseStringUTFChars(env, str, cstr);
    }
    return result;
#else
    const char* cstr = (*env)->GetStringUTFChars(env, str, NULL);
    jlong result = 0;
    if (cstr) {
        result = (jlong)strtoul(cstr, NULL, 16);
        (*env)->ReleaseStringUTFChars(env, str, cstr);
    }
    return result;
#endif
}

// 向量点积
JNIEXPORT jfloat JNICALL
Java_com_example_deepseekaiassistant_agent_NativeAgentCore_nativeDotProduct(
        JNIEnv *env, jobject thiz, jfloatArray a, jfloatArray b) {
#if defined(__aarch64__)
    jfloat* aData = (*env)->GetFloatArrayElements(env, a, NULL);
    jfloat* bData = (*env)->GetFloatArrayElements(env, b, NULL);
    jfloat result = 0.0f;
    if (aData && bData) {
        jsize len = (*env)->GetArrayLength(env, a);
        result = asm_neon_dot_f32(aData, bData, (int)len);
    }
    if (aData) (*env)->ReleaseFloatArrayElements(env, a, aData, JNI_ABORT);
    if (bData) (*env)->ReleaseFloatArrayElements(env, b, bData, JNI_ABORT);
    return result;
#else
    jfloat* aData = (*env)->GetFloatArrayElements(env, a, NULL);
    jfloat* bData = (*env)->GetFloatArrayElements(env, b, NULL);
    jfloat result = 0.0f;
    if (aData && bData) {
        jsize len = (*env)->GetArrayLength(env, a);
        for (int i = 0; i < len; i++) {
            result += aData[i] * bData[i];
        }
    }
    if (aData) (*env)->ReleaseFloatArrayElements(env, a, aData, JNI_ABORT);
    if (bData) (*env)->ReleaseFloatArrayElements(env, b, bData, JNI_ABORT);
    return result;
#endif
}

// 向量求和
JNIEXPORT jfloat JNICALL
Java_com_example_deepseekaiassistant_agent_NativeAgentCore_nativeVectorSum(
        JNIEnv *env, jobject thiz, jfloatArray a) {
#if defined(__aarch64__)
    jfloat* aData = (*env)->GetFloatArrayElements(env, a, NULL);
    jfloat result = 0.0f;
    if (aData) {
        jsize len = (*env)->GetArrayLength(env, a);
        result = asm_neon_sum_f32(aData, (int)len);
        (*env)->ReleaseFloatArrayElements(env, a, aData, JNI_ABORT);
    }
    return result;
#else
    jfloat* aData = (*env)->GetFloatArrayElements(env, a, NULL);
    jfloat result = 0.0f;
    if (aData) {
        jsize len = (*env)->GetArrayLength(env, a);
        for (int i = 0; i < len; i++) {
            result += aData[i];
        }
        (*env)->ReleaseFloatArrayElements(env, a, aData, JNI_ABORT);
    }
    return result;
#endif
}

// CPU 周期计数
JNIEXPORT jlong JNICALL
Java_com_example_deepseekaiassistant_agent_NativeAgentCore_nativeReadCycles(
        JNIEnv *env, jobject thiz) {
#if defined(__aarch64__)
    return (jlong)asm_read_cycle_counter();
#else
    return 0;
#endif
}

// CPU 频率
JNIEXPORT jlong JNICALL
Java_com_example_deepseekaiassistant_agent_NativeAgentCore_nativeGetCycleFreq(
        JNIEnv *env, jobject thiz) {
#if defined(__aarch64__)
    return (jlong)asm_read_cycle_freq();
#else
    return 0;
#endif
}

// 前导零计数
JNIEXPORT jint JNICALL
Java_com_example_deepseekaiassistant_agent_NativeAgentCore_nativeClz32(
        JNIEnv *env, jobject thiz, jint x) {
#if defined(__aarch64__)
    return (jint)asm_clz32((uint32_t)x);
#else
    if (x == 0) return 32;
    int n = 0;
    if ((x & 0xFFFF0000) == 0) { n += 16; x <<= 16; }
    if ((x & 0xFF000000) == 0) { n += 8; x <<= 8; }
    if ((x & 0xF0000000) == 0) { n += 4; x <<= 4; }
    if ((x & 0xC0000000) == 0) { n += 2; x <<= 2; }
    if ((x & 0x80000000) == 0) { n += 1; }
    return n;
#endif
}

// 人口计数
JNIEXPORT jint JNICALL
Java_com_example_deepseekaiassistant_agent_NativeAgentCore_nativePopcount32(
        JNIEnv *env, jobject thiz, jint x) {
#if defined(__aarch64__)
    return (jint)asm_popcount32((uint32_t)x);
#else
    uint32_t v = (uint32_t)x;
    v = v - ((v >> 1) & 0x55555555);
    v = (v & 0x33333333) + ((v >> 2) & 0x33333333);
    v = (v + (v >> 4)) & 0x0F0F0F0F;
    return (jint)((v * 0x01010101) >> 24);
#endif
}

// 字节序反转
JNIEXPORT jint JNICALL
Java_com_example_deepseekaiassistant_agent_NativeAgentCore_nativeByteSwap32(
        JNIEnv *env, jobject thiz, jint x) {
#if defined(__aarch64__)
    return (jint)asm_byteswap32((uint32_t)x);
#else
    uint32_t v = (uint32_t)x;
    return (jint)((v >> 24) | ((v >> 8) & 0xFF00) | ((v << 8) & 0xFF0000) | (v << 24));
#endif
}
