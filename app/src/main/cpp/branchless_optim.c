/**
 * Branchless Performance Optimizations
 * 
 * 无分支高性能实现
 * 消除条件跳转，最大化 CPU 流水线效率
 * 
 * @author DeepSeek AI Assistant
 */

#include "binary_optim.h"
#include <string.h>

#ifdef __ARM_NEON
#include <arm_neon.h>
#endif

// ============================================================================
// 无分支像素处理
// ============================================================================

/**
 * 无分支 RGB 转灰度 (单像素)
 * 使用定点数加速：Y = (77*R + 150*G + 29*B) >> 8
 */
FORCE_INLINE PERFORMANCE_CRITICAL
uint8_t branchless_rgb_to_gray(uint8_t r, uint8_t g, uint8_t b) {
    return (uint8_t)((77 * r + 150 * g + 29 * b) >> 8);
}

/**
 * 无分支 ARGB 解包
 */
FORCE_INLINE
void branchless_argb_unpack(uint32_t argb, 
                            uint8_t* RESTRICT a,
                            uint8_t* RESTRICT r,
                            uint8_t* RESTRICT g,
                            uint8_t* RESTRICT b) {
    *a = (argb >> 24) & 0xFF;
    *r = (argb >> 16) & 0xFF;
    *g = (argb >> 8) & 0xFF;
    *b = argb & 0xFF;
}

/**
 * 无分支颜色距离计算 (曼哈顿距离)
 * 用于颜色匹配
 */
int branchless_color_distance(uint32_t c1, uint32_t c2) {
    uint8_t r1 = (c1 >> 16) & 0xFF, r2 = (c2 >> 16) & 0xFF;
    uint8_t g1 = (c1 >> 8) & 0xFF, g2 = (c2 >> 8) & 0xFF;
    uint8_t b1 = c1 & 0xFF, b2 = c2 & 0xFF;
    
    return branchless_abs_i32(r1 - r2) +
           branchless_abs_i32(g1 - g2) +
           branchless_abs_i32(b1 - b2);
}

/**
 * 无分支颜色匹配
 * 返回 1 如果颜色在容差范围内，否则返回 0
 */
FORCE_INLINE PURE
int branchless_color_match(uint32_t c1, uint32_t c2, int tolerance) {
    int dist = branchless_color_distance(c1, c2);
    // 如果 dist <= tolerance 返回 1，否则 0
    int diff = tolerance - dist;
    return (diff >> 31) + 1;  // diff >= 0 ? 1 : 0
}

/**
 * 无分支 Alpha 混合
 * result = src * alpha + dst * (255 - alpha)
 */
FORCE_INLINE
uint32_t branchless_alpha_blend(uint32_t src, uint32_t dst, uint8_t alpha) {
    uint32_t inv_alpha = 255 - alpha;
    
    uint32_t src_r = (src >> 16) & 0xFF;
    uint32_t src_g = (src >> 8) & 0xFF;
    uint32_t src_b = src & 0xFF;
    
    uint32_t dst_r = (dst >> 16) & 0xFF;
    uint32_t dst_g = (dst >> 8) & 0xFF;
    uint32_t dst_b = dst & 0xFF;
    
    uint32_t r = mul_div255(src_r, alpha) + mul_div255(dst_r, inv_alpha);
    uint32_t g = mul_div255(src_g, alpha) + mul_div255(dst_g, inv_alpha);
    uint32_t b = mul_div255(src_b, alpha) + mul_div255(dst_b, inv_alpha);
    
    return 0xFF000000 | (r << 16) | (g << 8) | b;
}

// ============================================================================
// 无分支边界检查
// ============================================================================

/**
 * 无分支边界检查 - 点是否在矩形内
 */
FORCE_INLINE PURE
int branchless_point_in_rect(int x, int y, 
                             int rect_x, int rect_y, 
                             int rect_w, int rect_h) {
    // 所有条件必须同时满足
    int in_x = ((x - rect_x) | (rect_x + rect_w - 1 - x)) >= 0;
    int in_y = ((y - rect_y) | (rect_y + rect_h - 1 - y)) >= 0;
    return in_x & in_y;
}

/**
 * 无分支数组索引边界检查
 */
FORCE_INLINE PURE
int branchless_index_valid(int idx, int size) {
    // idx >= 0 && idx < size
    // 等价于 (unsigned)idx < (unsigned)size
    return (unsigned)idx < (unsigned)size;
}

/**
 * 无分支安全数组索引 (越界返回默认值)
 */
FORCE_INLINE
int32_t branchless_safe_index_i32(const int32_t* arr, int idx, int size, int32_t default_val) {
    int valid = branchless_index_valid(idx, size);
    // 使用掩码选择
    int32_t mask = -valid;  // valid ? -1 : 0
    return (arr[idx & (size - 1)] & mask) | (default_val & ~mask);
}

// ============================================================================
// 无分支比较和排序
// ============================================================================

/**
 * 无分支交换
 */
FORCE_INLINE
void branchless_swap_i32(int32_t* a, int32_t* b) {
    *a ^= *b;
    *b ^= *a;
    *a ^= *b;
}

/**
 * 无分支条件交换 (用于排序网络)
 */
FORCE_INLINE
void branchless_compare_swap(int32_t* a, int32_t* b) {
    int32_t va = *a, vb = *b;
    int32_t min_val = branchless_min_i32(va, vb);
    int32_t max_val = branchless_max_i32(va, vb);
    *a = min_val;
    *b = max_val;
}

/**
 * 无分支 3 元素中位数
 */
FORCE_INLINE PURE
int32_t branchless_median3_i32(int32_t a, int32_t b, int32_t c) {
    return branchless_max_i32(
        branchless_min_i32(a, b),
        branchless_min_i32(
            branchless_max_i32(a, b),
            c
        )
    );
}

/**
 * 无分支排序 3 个元素 (排序网络)
 */
FORCE_INLINE
void branchless_sort3(int32_t* arr) {
    branchless_compare_swap(&arr[0], &arr[1]);
    branchless_compare_swap(&arr[1], &arr[2]);
    branchless_compare_swap(&arr[0], &arr[1]);
}

/**
 * 无分支排序 4 个元素 (排序网络)
 */
FORCE_INLINE
void branchless_sort4(int32_t* arr) {
    branchless_compare_swap(&arr[0], &arr[1]);
    branchless_compare_swap(&arr[2], &arr[3]);
    branchless_compare_swap(&arr[0], &arr[2]);
    branchless_compare_swap(&arr[1], &arr[3]);
    branchless_compare_swap(&arr[1], &arr[2]);
}

// ============================================================================
// 无分支搜索
// ============================================================================

/**
 * 无分支二分搜索 (查找第一个 >= target 的位置)
 * 使用分支消除技术
 */
NOINLINE
int branchless_lower_bound(const int32_t* arr, int size, int32_t target) {
    int lo = 0;
    
    // 展开的二分搜索，每次迭代无分支
    while (size > 1) {
        int half = size >> 1;
        int mid = lo + half;
        
        // 无分支条件移动
        // 如果 arr[mid] < target，则 lo = mid
        int cmp = arr[mid] < target;
        lo = branchless_select_i32(cmp, mid, lo);
        
        // 更新大小
        size = size - half;
    }
    
    // 最后一次检查
    int cmp = arr[lo] < target;
    return lo + cmp;
}

/**
 * 无分支线性搜索 (使用 SIMD 加速)
 * 返回第一个匹配的索引，或 -1
 */
#ifdef __ARM_NEON
PERFORMANCE_CRITICAL
int branchless_linear_search_neon(const int32_t* arr, int size, int32_t target) {
    int32x4_t target_vec = vdupq_n_s32(target);
    int i = 0;
    
    // 4 元素一组处理
    for (; i + 4 <= size; i += 4) {
        int32x4_t data = vld1q_s32(&arr[i]);
        uint32x4_t cmp = vceqq_s32(data, target_vec);
        
        // 检查是否有匹配
        uint64_t matches = vget_lane_u64(vreinterpret_u64_u32(
            vand_u32(vget_low_u32(cmp), vget_high_u32(cmp))), 0);
        
        if (matches) {
            // 找到匹配，确定具体位置
            uint32_t mask[4];
            vst1q_u32(mask, cmp);
            for (int j = 0; j < 4; j++) {
                if (mask[j]) return i + j;
            }
        }
    }
    
    // 处理剩余元素
    for (; i < size; i++) {
        if (arr[i] == target) return i;
    }
    
    return -1;
}
#endif

// ============================================================================
// 无分支哈希
// ============================================================================

/**
 * 快速整数哈希 (无分支)
 */
uint32_t branchless_hash_u32(uint32_t x) {
    x ^= x >> 16;
    x *= 0x85EBCA6B;
    x ^= x >> 13;
    x *= 0xC2B2AE35;
    x ^= x >> 16;
    return x;
}

/**
 * 快速 64 位哈希
 */
FORCE_INLINE CONST
uint64_t branchless_hash_u64(uint64_t x) {
    x ^= x >> 33;
    x *= 0xFF51AFD7ED558CCDULL;
    x ^= x >> 33;
    x *= 0xC4CEB9FE1A85EC53ULL;
    x ^= x >> 33;
    return x;
}

/**
 * 字符串哈希 (FNV-1a, 展开优化)
 */
PERFORMANCE_CRITICAL
uint32_t branchless_hash_str(const char* str, size_t len) {
    uint32_t hash = 0x811C9DC5;  // FNV offset basis
    
    // 4 字节一组处理
    size_t i = 0;
    for (; i + 4 <= len; i += 4) {
        uint32_t chunk = load_unaligned_u32(str + i);
        hash ^= chunk & 0xFF;
        hash *= 0x01000193;
        hash ^= (chunk >> 8) & 0xFF;
        hash *= 0x01000193;
        hash ^= (chunk >> 16) & 0xFF;
        hash *= 0x01000193;
        hash ^= (chunk >> 24) & 0xFF;
        hash *= 0x01000193;
    }
    
    // 剩余字节
    for (; i < len; i++) {
        hash ^= (uint8_t)str[i];
        hash *= 0x01000193;
    }
    
    return hash;
}

// ============================================================================
// 无分支数学运算
// ============================================================================

/**
 * 快速倒数平方根 (Quake 3 算法改进版)
 */
float fast_rsqrt(float x) {
    union { float f; uint32_t u; } conv = { x };
    conv.u = 0x5F375A86 - (conv.u >> 1);
    // 一次牛顿迭代
    float half_x = x * 0.5f;
    conv.f *= (1.5f - half_x * conv.f * conv.f);
    return conv.f;
}

/**
 * 快速平方根
 */
float fast_sqrt(float x) {
    return x * fast_rsqrt(x);
}

/**
 * 快速正弦近似 (泰勒级数)
 * x 应在 [-π, π] 范围内
 */
float fast_sin(float x) {
    float x2 = x * x;
    return x * (1.0f - x2 * (0.16666667f - x2 * (0.00833333f - x2 * 0.0001984127f)));
}

/**
 * 快速余弦近似
 */
FORCE_INLINE
float fast_cos(float x) {
    float x2 = x * x;
    return 1.0f - x2 * (0.5f - x2 * (0.041666667f - x2 * 0.00138889f));
}

/**
 * 快速反正切
 */
FORCE_INLINE
float fast_atan(float x) {
    float x2 = x * x;
    return x * (1.0f - x2 * (0.333333f - x2 * (0.2f - x2 * 0.142857f)));
}

/**
 * 快速 2^x (整数部分)
 */
FORCE_INLINE CONST
uint32_t fast_exp2_i32(int n) {
    return 1u << n;
}

/**
 * 快速 log2 (浮点近似)
 */
FORCE_INLINE
float fast_log2_f32(float x) {
    union { float f; uint32_t u; } conv = { x };
    float exp = (float)((conv.u >> 23) - 127);
    conv.u = (conv.u & 0x007FFFFF) | 0x3F800000;
    return exp + (conv.f - 1.0f) * 1.4426950408f;
}

// ============================================================================
// 无分支游戏逻辑
// ============================================================================

/**
 * 无分支坐标映射 (屏幕坐标 -> 网格坐标)
 */
FORCE_INLINE
void branchless_screen_to_grid(int screen_x, int screen_y,
                               int grid_offset_x, int grid_offset_y,
                               int cell_size,
                               int* grid_x, int* grid_y) {
    *grid_x = (screen_x - grid_offset_x) / cell_size;
    *grid_y = (screen_y - grid_offset_y) / cell_size;
}

/**
 * 无分支曼哈顿距离
 */
FORCE_INLINE PURE
int branchless_manhattan_dist(int x1, int y1, int x2, int y2) {
    return branchless_abs_i32(x1 - x2) + branchless_abs_i32(y1 - y2);
}

/**
 * 无分支切比雪夫距离 (8方向移动)
 */
FORCE_INLINE PURE
int branchless_chebyshev_dist(int x1, int y1, int x2, int y2) {
    int dx = branchless_abs_i32(x1 - x2);
    int dy = branchless_abs_i32(y1 - y2);
    return branchless_max_i32(dx, dy);
}

/**
 * 无分支方向计算 (8方向)
 * 返回 0-7 的方向索引
 */
FORCE_INLINE PURE
int branchless_direction_8(int dx, int dy) {
    // 将 dx, dy 映射到 -1, 0, 1
    int sx = branchless_sign_i32(dx);
    int sy = branchless_sign_i32(dy);
    
    // 使用查找表思想
    // sx: -1->0, 0->1, 1->2
    // sy: -1->0, 0->1, 1->2
    int ix = sx + 1;
    int iy = sy + 1;
    
    // 3x3 方向表
    static const int dir_table[3][3] = {
        {5, 4, 3},  // 左上, 上, 右上
        {6, -1, 2}, // 左, 中心, 右
        {7, 0, 1}   // 左下, 下, 右下
    };
    
    return dir_table[iy][ix];
}

// ============================================================================
// 批量无分支处理 (SIMD 优化)
// ============================================================================

#ifdef __ARM_NEON

/**
 * 批量无分支最大值 (16 个字节)
 */
PERFORMANCE_CRITICAL
void branchless_max_u8x16(const uint8_t* a, const uint8_t* b, uint8_t* out) {
    uint8x16_t va = vld1q_u8(a);
    uint8x16_t vb = vld1q_u8(b);
    vst1q_u8(out, vmaxq_u8(va, vb));
}

/**
 * 批量无分支绝对差 (用于图像比较)
 */
PERFORMANCE_CRITICAL
void branchless_abs_diff_u8x16(const uint8_t* a, const uint8_t* b, uint8_t* out) {
    uint8x16_t va = vld1q_u8(a);
    uint8x16_t vb = vld1q_u8(b);
    vst1q_u8(out, vabdq_u8(va, vb));
}

/**
 * 批量无分支钳制
 */
PERFORMANCE_CRITICAL
void branchless_clamp_u8x16(const uint8_t* src, uint8_t lo, uint8_t hi, uint8_t* out) {
    uint8x16_t v = vld1q_u8(src);
    uint8x16_t vlo = vdupq_n_u8(lo);
    uint8x16_t vhi = vdupq_n_u8(hi);
    v = vmaxq_u8(v, vlo);
    v = vminq_u8(v, vhi);
    vst1q_u8(out, v);
}

/**
 * 批量颜色距离计算 (4 个 ARGB 像素)
 */
PERFORMANCE_CRITICAL
void branchless_color_dist_4px(const uint32_t* colors, uint32_t target, int32_t* distances) {
    // 提取目标颜色通道
    uint8_t tr = (target >> 16) & 0xFF;
    uint8_t tg = (target >> 8) & 0xFF;
    uint8_t tb = target & 0xFF;
    
    int16x4_t target_r = vdup_n_s16(tr);
    int16x4_t target_g = vdup_n_s16(tg);
    int16x4_t target_b = vdup_n_s16(tb);
    
    // 提取源颜色通道
    int16_t sr[4], sg[4], sb[4];
    for (int i = 0; i < 4; i++) {
        sr[i] = (colors[i] >> 16) & 0xFF;
        sg[i] = (colors[i] >> 8) & 0xFF;
        sb[i] = colors[i] & 0xFF;
    }
    
    int16x4_t src_r = vld1_s16(sr);
    int16x4_t src_g = vld1_s16(sg);
    int16x4_t src_b = vld1_s16(sb);
    
    // 计算差值绝对值并累加
    int16x4_t diff_r = vabs_s16(vsub_s16(src_r, target_r));
    int16x4_t diff_g = vabs_s16(vsub_s16(src_g, target_g));
    int16x4_t diff_b = vabs_s16(vsub_s16(src_b, target_b));
    
    int16x4_t total = vadd_s16(vadd_s16(diff_r, diff_g), diff_b);
    
    // 存储结果
    int16_t result[4];
    vst1_s16(result, total);
    for (int i = 0; i < 4; i++) {
        distances[i] = result[i];
    }
}

#endif // __ARM_NEON

// ============================================================================
// 性能计数器接口
// ============================================================================

/**
 * 获取当前 CPU 周期数 (用于精确性能测量)
 */
FORCE_INLINE
uint64_t get_cpu_cycles(void) {
    #if defined(__aarch64__)
    uint64_t cycles;
    __asm__ __volatile__("mrs %0, cntvct_el0" : "=r"(cycles));
    return cycles;
    #elif defined(__x86_64__)
    uint32_t lo, hi;
    __asm__ __volatile__("rdtsc" : "=a"(lo), "=d"(hi));
    return ((uint64_t)hi << 32) | lo;
    #else
    return 0;
    #endif
}

/**
 * 计时宏
 */
#define BENCHMARK_START()   uint64_t _bench_start = get_cpu_cycles()
#define BENCHMARK_END()     (get_cpu_cycles() - _bench_start)
