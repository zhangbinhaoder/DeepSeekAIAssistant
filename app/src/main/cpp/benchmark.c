/**
 * Performance Benchmark Module
 * 
 * 性能基准测试模块
 * 用于测量和验证二进制级优化的效果
 * 
 * @author DeepSeek AI Assistant
 */

#define _POSIX_C_SOURCE 199309L

#include "binary_optim.h"
#include <stdlib.h>
#include <string.h>

#if defined(__ANDROID__) || defined(__linux__)
#include <time.h>
#endif

#ifdef __ANDROID__
#include <android/log.h>
#define BENCH_LOG(fmt, ...) __android_log_print(ANDROID_LOG_INFO, "Benchmark", fmt, ##__VA_ARGS__)
#else
#include <stdio.h>
#define BENCH_LOG(fmt, ...) printf(fmt "\n", ##__VA_ARGS__)
#endif

#ifdef __ARM_NEON
#include <arm_neon.h>
#endif

// ============================================================================
// 基准测试结果结构
// ============================================================================

typedef struct {
    const char* name;
    uint64_t cycles_total;
    uint64_t iterations;
    double ns_per_op;
    double cycles_per_op;
} BenchmarkResult;

#define MAX_BENCH_RESULTS 32
static BenchmarkResult g_results[MAX_BENCH_RESULTS];
static int g_result_count = 0;

// ============================================================================
// 计时工具
// ============================================================================

static FORCE_INLINE uint64_t bench_read_cycles(void) {
#if defined(__aarch64__)
    uint64_t cycles;
    __asm__ __volatile__("mrs %0, cntvct_el0" : "=r"(cycles));
    return cycles;
#elif defined(__ANDROID__) || defined(__linux__)
    struct timespec ts;
    clock_gettime(CLOCK_MONOTONIC, &ts);
    return (uint64_t)ts.tv_sec * 1000000000ULL + ts.tv_nsec;
#else
    return 0;
#endif
}

static FORCE_INLINE uint64_t bench_get_freq(void) {
#if defined(__aarch64__)
    uint64_t freq;
    __asm__ __volatile__("mrs %0, cntfrq_el0" : "=r"(freq));
    return freq;
#else
    return 1000000000ULL;  // 纳秒
#endif
}

// ============================================================================
// 基准测试宏
// ============================================================================

#define BENCHMARK_ITERS     1000000

// 基准测试辅助函数
static void record_benchmark(const char* name, uint64_t elapsed) {
    double freq = (double)bench_get_freq();
    double ns_per_op = (elapsed * 1000000000.0) / (freq * BENCHMARK_ITERS);
    double cycles_per_op = (double)elapsed / BENCHMARK_ITERS;
    
    if (g_result_count < MAX_BENCH_RESULTS) {
        g_results[g_result_count].name = name;
        g_results[g_result_count].cycles_total = elapsed;
        g_results[g_result_count].iterations = BENCHMARK_ITERS;
        g_results[g_result_count].ns_per_op = ns_per_op;
        g_results[g_result_count].cycles_per_op = cycles_per_op;
        g_result_count++;
    }
    BENCH_LOG("  %-30s: %.2f ns/op (%.1f cycles/op)", name, ns_per_op, cycles_per_op);
}

// ============================================================================
// 被测函数
// ============================================================================

// 标准库版本 (用于对比)
static int std_min(int a, int b) { return a < b ? a : b; }
static int std_max(int a, int b) { return a > b ? a : b; }
static int std_abs(int x) { return x < 0 ? -x : x; }
static int std_clamp(int x, int lo, int hi) { return std_max(std_min(x, hi), lo); }

// 外部无分支版本 (在 branchless_optim.c 中定义)
extern int32_t branchless_min_i32(int32_t a, int32_t b);
extern int32_t branchless_max_i32(int32_t a, int32_t b);
extern int32_t branchless_abs_i32(int32_t x);
extern int32_t branchless_clamp_i32(int32_t x, int32_t lo, int32_t hi);
extern uint32_t branchless_hash_u32(uint32_t x);
extern float fast_rsqrt(float x);
extern float fast_sqrt(float x);
extern float fast_sin(float x);

// 颜色距离
static int color_dist_branched(uint32_t c1, uint32_t c2) {
    int r1 = (c1 >> 16) & 0xFF, r2 = (c2 >> 16) & 0xFF;
    int g1 = (c1 >> 8) & 0xFF, g2 = (c2 >> 8) & 0xFF;
    int b1 = c1 & 0xFF, b2 = c2 & 0xFF;
    return std_abs(r1 - r2) + std_abs(g1 - g2) + std_abs(b1 - b2);
}

extern int branchless_color_distance(uint32_t c1, uint32_t c2);

// ============================================================================
// 基准测试套件
// ============================================================================

/**
 * 运行所有基准测试
 */
void run_all_benchmarks(void) {
    g_result_count = 0;
    
    // 测试数据
    volatile int a = 12345, b = 67890, x = -9999;
    volatile float fx = 2.0f;
    volatile uint32_t c1 = 0xFFAA5533, c2 = 0xFF995544;
    volatile uint32_t hash_input = 0xDEADBEEF;
    
    // 防止编译器优化掉结果
    volatile int result_i = 0;
    volatile float result_f = 0.0f;
    volatile uint32_t result_u = 0;
    
    BENCH_LOG("=====================================");
    BENCH_LOG("Performance Benchmark Results");
    BENCH_LOG("Iterations per test: %d", BENCHMARK_ITERS);
    BENCH_LOG("=====================================");
    
    // --------------------------------------
    // 1. 基础算术操作对比
    // --------------------------------------
    BENCH_LOG("\n[1] Basic Arithmetic (Branched vs Branchless):");
    
    // std_min (branched)
    {
        COMPILER_BARRIER();
        uint64_t start = bench_read_cycles();
        for (int i = 0; i < BENCHMARK_ITERS; i++) {
            result_i = std_min(a, b);
        }
        COMPILER_BARRIER();
        record_benchmark("std_min (branched)", bench_read_cycles() - start);
    }
    
    // branchless_min
    {
        COMPILER_BARRIER();
        uint64_t start = bench_read_cycles();
        for (int i = 0; i < BENCHMARK_ITERS; i++) {
            result_i = branchless_min_i32(a, b);
        }
        COMPILER_BARRIER();
        record_benchmark("branchless_min", bench_read_cycles() - start);
    }
    
    // std_max (branched)
    {
        COMPILER_BARRIER();
        uint64_t start = bench_read_cycles();
        for (int i = 0; i < BENCHMARK_ITERS; i++) {
            result_i = std_max(a, b);
        }
        COMPILER_BARRIER();
        record_benchmark("std_max (branched)", bench_read_cycles() - start);
    }
    
    // branchless_max
    {
        COMPILER_BARRIER();
        uint64_t start = bench_read_cycles();
        for (int i = 0; i < BENCHMARK_ITERS; i++) {
            result_i = branchless_max_i32(a, b);
        }
        COMPILER_BARRIER();
        record_benchmark("branchless_max", bench_read_cycles() - start);
    }
    
    // std_abs (branched)
    {
        COMPILER_BARRIER();
        uint64_t start = bench_read_cycles();
        for (int i = 0; i < BENCHMARK_ITERS; i++) {
            result_i = std_abs(x);
        }
        COMPILER_BARRIER();
        record_benchmark("std_abs (branched)", bench_read_cycles() - start);
    }
    
    // branchless_abs
    {
        COMPILER_BARRIER();
        uint64_t start = bench_read_cycles();
        for (int i = 0; i < BENCHMARK_ITERS; i++) {
            result_i = branchless_abs_i32(x);
        }
        COMPILER_BARRIER();
        record_benchmark("branchless_abs", bench_read_cycles() - start);
    }
    
    // std_clamp (branched)
    {
        COMPILER_BARRIER();
        uint64_t start = bench_read_cycles();
        for (int i = 0; i < BENCHMARK_ITERS; i++) {
            result_i = std_clamp(x, 0, 1000);
        }
        COMPILER_BARRIER();
        record_benchmark("std_clamp (branched)", bench_read_cycles() - start);
    }
    
    // branchless_clamp
    {
        COMPILER_BARRIER();
        uint64_t start = bench_read_cycles();
        for (int i = 0; i < BENCHMARK_ITERS; i++) {
            result_i = branchless_clamp_i32(x, 0, 1000);
        }
        COMPILER_BARRIER();
        record_benchmark("branchless_clamp", bench_read_cycles() - start);
    }
    
    // --------------------------------------
    // 2. 颜色处理
    // --------------------------------------
    BENCH_LOG("\n[2] Color Processing:");
    
    {
        COMPILER_BARRIER();
        uint64_t start = bench_read_cycles();
        for (int i = 0; i < BENCHMARK_ITERS; i++) {
            result_i = color_dist_branched(c1, c2);
        }
        COMPILER_BARRIER();
        record_benchmark("color_dist (branched)", bench_read_cycles() - start);
    }
    
    {
        COMPILER_BARRIER();
        uint64_t start = bench_read_cycles();
        for (int i = 0; i < BENCHMARK_ITERS; i++) {
            result_i = branchless_color_distance(c1, c2);
        }
        COMPILER_BARRIER();
        record_benchmark("color_dist (branchless)", bench_read_cycles() - start);
    }
    
    // --------------------------------------
    // 3. 哈希函数
    // --------------------------------------
    BENCH_LOG("\n[3] Hash Functions:");
    
    {
        COMPILER_BARRIER();
        uint64_t start = bench_read_cycles();
        for (int i = 0; i < BENCHMARK_ITERS; i++) {
            result_u = branchless_hash_u32(hash_input);
            hash_input = result_u;
        }
        COMPILER_BARRIER();
        record_benchmark("branchless_hash_u32", bench_read_cycles() - start);
    }
    
    // --------------------------------------
    // 4. 数学函数
    // --------------------------------------
    BENCH_LOG("\n[4] Math Functions:");
    
    {
        COMPILER_BARRIER();
        uint64_t start = bench_read_cycles();
        for (int i = 0; i < BENCHMARK_ITERS; i++) {
            result_f = fast_rsqrt(fx);
        }
        COMPILER_BARRIER();
        record_benchmark("fast_rsqrt", bench_read_cycles() - start);
    }
    
    {
        COMPILER_BARRIER();
        uint64_t start = bench_read_cycles();
        for (int i = 0; i < BENCHMARK_ITERS; i++) {
            result_f = fast_sqrt(fx);
        }
        COMPILER_BARRIER();
        record_benchmark("fast_sqrt", bench_read_cycles() - start);
    }
    
    {
        COMPILER_BARRIER();
        uint64_t start = bench_read_cycles();
        for (int i = 0; i < BENCHMARK_ITERS; i++) {
            result_f = fast_sin(fx);
        }
        COMPILER_BARRIER();
        record_benchmark("fast_sin", bench_read_cycles() - start);
    }
    
#ifdef __ARM_NEON
    // --------------------------------------
    // 5. SIMD 操作
    // --------------------------------------
    BENCH_LOG("\n[5] SIMD Operations:");
    
    CACHE_ALIGNED uint8_t simd_a[16] = {0};
    CACHE_ALIGNED uint8_t simd_b[16] = {0};
    CACHE_ALIGNED uint8_t simd_out[16] = {0};
    
    for (int i = 0; i < 16; i++) {
        simd_a[i] = i * 10;
        simd_b[i] = i * 5;
    }
    
    {
        COMPILER_BARRIER();
        uint64_t start = bench_read_cycles();
        for (int i = 0; i < BENCHMARK_ITERS; i++) {
            uint8x16_t va = vld1q_u8(simd_a);
            uint8x16_t vb = vld1q_u8(simd_b);
            vst1q_u8(simd_out, vmaxq_u8(va, vb));
        }
        COMPILER_BARRIER();
        record_benchmark("NEON max 16 bytes", bench_read_cycles() - start);
    }
    
    {
        COMPILER_BARRIER();
        uint64_t start = bench_read_cycles();
        for (int i = 0; i < BENCHMARK_ITERS; i++) {
            uint8x16_t va = vld1q_u8(simd_a);
            uint8x16_t vb = vld1q_u8(simd_b);
            vst1q_u8(simd_out, vabdq_u8(va, vb));
        }
        COMPILER_BARRIER();
        record_benchmark("NEON abs_diff 16 bytes", bench_read_cycles() - start);
    }
    
    // RGB 转灰度
    CACHE_ALIGNED uint8_t rgba[64] = {0};
    CACHE_ALIGNED uint8_t gray[16] = {0};
    
    for (int i = 0; i < 64; i++) {
        rgba[i] = (i * 17) & 0xFF;
    }
    
    {
        COMPILER_BARRIER();
        uint64_t start = bench_read_cycles();
        for (int i = 0; i < BENCHMARK_ITERS; i++) {
            uint8x16x4_t src = vld4q_u8(rgba);
            uint16x8_t low = vmull_u8(vget_low_u8(src.val[0]), vdup_n_u8(77));
            low = vmlal_u8(low, vget_low_u8(src.val[1]), vdup_n_u8(150));
            low = vmlal_u8(low, vget_low_u8(src.val[2]), vdup_n_u8(29));
            
            uint16x8_t high = vmull_u8(vget_high_u8(src.val[0]), vdup_n_u8(77));
            high = vmlal_u8(high, vget_high_u8(src.val[1]), vdup_n_u8(150));
            high = vmlal_u8(high, vget_high_u8(src.val[2]), vdup_n_u8(29));
            
            uint8x8_t low_result = vshrn_n_u16(low, 8);
            uint8x8_t high_result = vshrn_n_u16(high, 8);
            
            vst1_u8(gray, low_result);
            vst1_u8(gray + 8, high_result);
        }
        COMPILER_BARRIER();
        record_benchmark("NEON grayscale 16px", bench_read_cycles() - start);
    }
#endif
    
    // --------------------------------------
    // 内存预取测试
    // --------------------------------------
    BENCH_LOG("\n[6] Memory Prefetch:");
    
    #define MEM_SIZE (1024 * 1024)  // 1MB
    volatile char* mem = (volatile char*)malloc(MEM_SIZE);
    if (mem) {
        // 初始化
        for (int i = 0; i < MEM_SIZE; i += 64) {
            mem[i] = (char)i;
        }
        
        volatile int sum = 0;
        
        {
            COMPILER_BARRIER();
            uint64_t start = bench_read_cycles();
            for (int iter = 0; iter < 100; iter++) {
                sum = 0;
                for (int i = 0; i < MEM_SIZE; i += 64) {
                    sum += mem[i];
                }
            }
            COMPILER_BARRIER();
            record_benchmark("linear read (no prefetch)", bench_read_cycles() - start);
        }
        
        {
            COMPILER_BARRIER();
            uint64_t start = bench_read_cycles();
            for (int iter = 0; iter < 100; iter++) {
                sum = 0;
                for (int i = 0; i < MEM_SIZE; i += 64) {
                    PREFETCH_R(&mem[i + 256]);
                    sum += mem[i];
                }
            }
            COMPILER_BARRIER();
            record_benchmark("linear read (with prefetch)", bench_read_cycles() - start);
        }
        
        free((void*)mem);
    }
    
    // --------------------------------------
    // 总结
    // --------------------------------------
    BENCH_LOG("\n=====================================");
    BENCH_LOG("Benchmark Summary:");
    BENCH_LOG("Total tests run: %d", g_result_count);
    
    // 计算加速比
    if (g_result_count >= 2) {
        BENCH_LOG("\nSpeedup Analysis:");
        
        // 找到对应的测试对
        for (int i = 0; i < g_result_count - 1; i++) {
            const char* name1 = g_results[i].name;
            const char* name2 = g_results[i + 1].name;
            
            // 检查是否是对比测试
            if (strstr(name1, "branched") && strstr(name2, "branchless")) {
                double speedup = g_results[i].ns_per_op / g_results[i + 1].ns_per_op;
                BENCH_LOG("  %s -> %s: %.2fx speedup", name1, name2, speedup);
            }
        }
    }
    
    BENCH_LOG("=====================================\n");
    
    // 防止未使用警告
    (void)result_i;
    (void)result_f;
    (void)result_u;
}

/**
 * 获取基准测试结果数量
 */
int get_benchmark_result_count(void) {
    return g_result_count;
}

/**
 * 获取指定索引的结果
 */
int get_benchmark_result(int index, 
                         const char** name,
                         double* ns_per_op,
                         double* cycles_per_op) {
    if (index < 0 || index >= g_result_count) {
        return -1;
    }
    
    if (name) *name = g_results[index].name;
    if (ns_per_op) *ns_per_op = g_results[index].ns_per_op;
    if (cycles_per_op) *cycles_per_op = g_results[index].cycles_per_op;
    
    return 0;
}

/**
 * 运行快速自检
 * 验证优化函数的正确性
 */
int run_correctness_check(void) {
    int errors = 0;
    
    BENCH_LOG("Running correctness checks...");
    
    // min/max
    if (branchless_min_i32(5, 10) != 5) errors++;
    if (branchless_min_i32(10, 5) != 5) errors++;
    if (branchless_min_i32(-5, 5) != -5) errors++;
    
    if (branchless_max_i32(5, 10) != 10) errors++;
    if (branchless_max_i32(10, 5) != 10) errors++;
    if (branchless_max_i32(-5, 5) != 5) errors++;
    
    // abs
    if (branchless_abs_i32(5) != 5) errors++;
    if (branchless_abs_i32(-5) != 5) errors++;
    if (branchless_abs_i32(0) != 0) errors++;
    
    // clamp
    if (branchless_clamp_i32(5, 0, 10) != 5) errors++;
    if (branchless_clamp_i32(-5, 0, 10) != 0) errors++;
    if (branchless_clamp_i32(15, 0, 10) != 10) errors++;
    
    // color distance
    if (branchless_color_distance(0xFF000000, 0xFF000000) != 0) errors++;
    if (branchless_color_distance(0xFFFF0000, 0xFF000000) != 255) errors++;
    
    // 快速数学
    float rsqrt = fast_rsqrt(4.0f);
    if (rsqrt < 0.45f || rsqrt > 0.55f) errors++;  // 应该约等于 0.5
    
    if (errors == 0) {
        BENCH_LOG("All correctness checks PASSED");
    } else {
        BENCH_LOG("FAILED: %d errors found", errors);
    }
    
    return errors;
}
