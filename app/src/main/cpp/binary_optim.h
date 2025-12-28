/**
 * Binary-Level Optimization Macros
 * 
 * 二进制层面的编译器优化宏和内联函数
 * 这些优化直接影响生成的机器码质量
 * 
 * 包含:
 * 1. 分支预测提示 (likely/unlikely)
 * 2. 内存对齐 (缓存行对齐)
 * 3. 预取指令
 * 4. 无分支操作
 * 5. 循环优化
 * 6. 编译器屏障
 * 
 * @author DeepSeek AI Assistant
 */

#ifndef BINARY_OPTIM_H
#define BINARY_OPTIM_H

#include <stdint.h>
#include <stddef.h>

#ifdef __cplusplus
extern "C" {
#endif

// ============================================================================
// 分支预测提示
// 告诉编译器哪个分支更可能执行，优化流水线
// ============================================================================

/**
 * 分支预测: 条件很可能为真
 * 编译器会将 true 分支放在更优的位置
 */
#if defined(__GNUC__) || defined(__clang__)
    #define LIKELY(x)       __builtin_expect(!!(x), 1)
    #define UNLIKELY(x)     __builtin_expect(!!(x), 0)
#else
    #define LIKELY(x)       (x)
    #define UNLIKELY(x)     (x)
#endif

/**
 * 假设条件为真 (更激进的优化)
 * 如果条件为假，行为未定义
 */
#if defined(__clang__)
    #define ASSUME(x)       __builtin_assume(x)
#elif defined(__GNUC__) && __GNUC__ >= 13
    #define ASSUME(x)       __attribute__((assume(x)))
#else
    #define ASSUME(x)       do { if (!(x)) __builtin_unreachable(); } while(0)
#endif

/**
 * 标记不可达代码
 * 帮助编译器消除死代码
 */
#if defined(__GNUC__) || defined(__clang__)
    #define UNREACHABLE()   __builtin_unreachable()
#else
    #define UNREACHABLE()   do {} while(0)
#endif

// ============================================================================
// 内存对齐
// ============================================================================

/**
 * 缓存行大小 (ARM64 通常是 64 字节)
 */
#define CACHE_LINE_SIZE     64

/**
 * 对齐到缓存行边界
 * 避免 false sharing，提高缓存命中率
 */
#define CACHE_ALIGNED       __attribute__((aligned(CACHE_LINE_SIZE)))

/**
 * 对齐到指定字节边界
 */
#define ALIGN(n)            __attribute__((aligned(n)))

/**
 * SIMD 对齐 (16 字节)
 */
#define SIMD_ALIGNED        __attribute__((aligned(16)))

/**
 * 页面对齐 (4KB)
 */
#define PAGE_ALIGNED        __attribute__((aligned(4096)))

/**
 * 紧凑结构体 (无填充)
 */
#define PACKED              __attribute__((packed))

// ============================================================================
// 函数属性
// ============================================================================

/**
 * 强制内联
 */
#define FORCE_INLINE        __attribute__((always_inline)) inline

/**
 * 禁止内联
 */
#define NOINLINE            __attribute__((noinline))

/**
 * 热点函数 (放入热代码段)
 */
#define HOT                 __attribute__((hot))

/**
 * 冷函数 (放入冷代码段)
 */
#define COLD                __attribute__((cold))

/**
 * 纯函数 (无副作用，只依赖参数)
 */
#define PURE                __attribute__((pure))

/**
 * 常量函数 (纯函数且不读取全局状态)
 */
#define CONST               __attribute__((const))

/**
 * 返回值不为空
 */
#define RETURNS_NONNULL     __attribute__((returns_nonnull))

/**
 * 函数不返回 (如 exit, abort)
 */
#define NORETURN            __attribute__((noreturn))

// ============================================================================
// 预取指令
// ============================================================================

/**
 * 预取数据到 L1 缓存 (读取)
 */
#define PREFETCH_R(addr)    __builtin_prefetch((addr), 0, 3)

/**
 * 预取数据到 L1 缓存 (写入)
 */
#define PREFETCH_W(addr)    __builtin_prefetch((addr), 1, 3)

/**
 * 预取数据到 L2 缓存
 */
#define PREFETCH_L2(addr)   __builtin_prefetch((addr), 0, 2)

/**
 * 非临时预取 (不污染缓存)
 */
#define PREFETCH_NTA(addr)  __builtin_prefetch((addr), 0, 0)

/**
 * 批量预取
 */
#define PREFETCH_RANGE(addr, size) do { \
    const char* _p = (const char*)(addr); \
    const char* _end = _p + (size); \
    for (; _p < _end; _p += CACHE_LINE_SIZE) { \
        PREFETCH_R(_p); \
    } \
} while(0)

// ============================================================================
// 编译器/内存屏障
// ============================================================================

/**
 * 编译器屏障 (不产生指令)
 * 防止编译器重排序
 */
#define COMPILER_BARRIER()  __asm__ __volatile__("" ::: "memory")

/**
 * 读屏障
 */
#define READ_BARRIER()      __asm__ __volatile__("dmb ld" ::: "memory")

/**
 * 写屏障
 */
#define WRITE_BARRIER()     __asm__ __volatile__("dmb st" ::: "memory")

/**
 * 全屏障
 */
#define FULL_BARRIER()      __asm__ __volatile__("dmb sy" ::: "memory")

// ============================================================================
// 无分支操作 (Branchless Operations)
// 消除条件跳转，提高流水线效率
// ============================================================================

/**
 * 无分支最大值
 */
FORCE_INLINE int32_t branchless_max_i32(int32_t a, int32_t b) {
    int32_t diff = a - b;
    int32_t mask = diff >> 31;  // 如果 a < b, mask = -1, 否则 mask = 0
    return a - (diff & mask);   // a < b 时返回 b, 否则返回 a
}

/**
 * 无分支最小值
 */
FORCE_INLINE int32_t branchless_min_i32(int32_t a, int32_t b) {
    int32_t diff = a - b;
    int32_t mask = diff >> 31;
    return b + (diff & mask);
}

/**
 * 无分支绝对值
 */
FORCE_INLINE int32_t branchless_abs_i32(int32_t x) {
    int32_t mask = x >> 31;
    return (x ^ mask) - mask;
}

/**
 * 无分支符号函数 (-1, 0, 1)
 */
FORCE_INLINE int32_t branchless_sign_i32(int32_t x) {
    return (x > 0) - (x < 0);
}

/**
 * 无分支钳制 (clamp)
 */
FORCE_INLINE int32_t branchless_clamp_i32(int32_t x, int32_t lo, int32_t hi) {
    return branchless_min_i32(branchless_max_i32(x, lo), hi);
}

/**
 * 无分支条件选择
 * cond ? a : b (cond 应为 0 或 1)
 */
FORCE_INLINE int32_t branchless_select_i32(int32_t cond, int32_t a, int32_t b) {
    return b + (cond * (a - b));
}

/**
 * 无分支条件选择 (使用掩码)
 * mask 应为全 0 或全 1 (-1)
 */
FORCE_INLINE int32_t branchless_select_mask_i32(int32_t mask, int32_t a, int32_t b) {
    return b ^ ((a ^ b) & mask);
}

/**
 * 无分支比较: 返回 -1 (a<b), 0 (a==b), 1 (a>b)
 */
FORCE_INLINE int32_t branchless_cmp_i32(int32_t a, int32_t b) {
    return (a > b) - (a < b);
}

// 浮点版本
FORCE_INLINE float branchless_max_f32(float a, float b) {
    return a > b ? a : b;  // 现代编译器会用 fmax 指令
}

FORCE_INLINE float branchless_min_f32(float a, float b) {
    return a < b ? a : b;
}

FORCE_INLINE float branchless_abs_f32(float x) {
    union { float f; uint32_t u; } u = { x };
    u.u &= 0x7FFFFFFF;  // 清除符号位
    return u.f;
}

FORCE_INLINE float branchless_clamp_f32(float x, float lo, float hi) {
    return branchless_min_f32(branchless_max_f32(x, lo), hi);
}

// ============================================================================
// 位操作优化
// ============================================================================

/**
 * 前导零计数
 */
FORCE_INLINE int clz32(uint32_t x) {
    return x ? __builtin_clz(x) : 32;
}

FORCE_INLINE int clz64(uint64_t x) {
    return x ? __builtin_clzll(x) : 64;
}

/**
 * 尾随零计数
 */
FORCE_INLINE int ctz32(uint32_t x) {
    return x ? __builtin_ctz(x) : 32;
}

FORCE_INLINE int ctz64(uint64_t x) {
    return x ? __builtin_ctzll(x) : 64;
}

/**
 * 位计数 (popcount)
 */
FORCE_INLINE int popcount32(uint32_t x) {
    return __builtin_popcount(x);
}

FORCE_INLINE int popcount64(uint64_t x) {
    return __builtin_popcountll(x);
}

/**
 * 是否为 2 的幂
 */
FORCE_INLINE int is_power_of_2(uint32_t x) {
    return x && !(x & (x - 1));
}

/**
 * 向上取整到 2 的幂
 */
FORCE_INLINE uint32_t next_power_of_2(uint32_t x) {
    if (x == 0) return 1;
    x--;
    x |= x >> 1;
    x |= x >> 2;
    x |= x >> 4;
    x |= x >> 8;
    x |= x >> 16;
    return x + 1;
}

/**
 * 快速对数 (以 2 为底)
 */
FORCE_INLINE int log2_floor(uint32_t x) {
    return x ? 31 - clz32(x) : -1;
}

FORCE_INLINE int log2_ceil(uint32_t x) {
    if (x <= 1) return 0;
    return 32 - clz32(x - 1);
}

// ============================================================================
// 字节序操作
// ============================================================================

/**
 * 字节交换 (大小端转换)
 */
FORCE_INLINE uint16_t bswap16(uint16_t x) {
    return __builtin_bswap16(x);
}

FORCE_INLINE uint32_t bswap32(uint32_t x) {
    return __builtin_bswap32(x);
}

FORCE_INLINE uint64_t bswap64(uint64_t x) {
    return __builtin_bswap64(x);
}

/**
 * 字节旋转
 */
FORCE_INLINE uint32_t rotl32(uint32_t x, int n) {
    return (x << n) | (x >> (32 - n));
}

FORCE_INLINE uint32_t rotr32(uint32_t x, int n) {
    return (x >> n) | (x << (32 - n));
}

// ============================================================================
// 快速整数运算
// ============================================================================

/**
 * 快速除以 255 (用于颜色计算)
 * x 应在 [0, 65535] 范围内
 */
FORCE_INLINE uint32_t div255(uint32_t x) {
    return ((x + 1) + ((x + 1) >> 8)) >> 8;
}

/**
 * 快速除以 256
 */
FORCE_INLINE int32_t div256(int32_t x) {
    return (x + ((x >> 31) & 255)) >> 8;
}

/**
 * 快速乘法后除以 255 (用于 alpha 混合)
 * 计算 (a * b) / 255
 */
FORCE_INLINE uint32_t mul_div255(uint32_t a, uint32_t b) {
    uint32_t t = a * b + 128;
    return (t + (t >> 8)) >> 8;
}

/**
 * 饱和加法 (8位)
 */
FORCE_INLINE uint8_t sat_add_u8(uint8_t a, uint8_t b) {
    uint32_t sum = a + b;
    return sum > 255 ? 255 : (uint8_t)sum;
}

/**
 * 饱和减法 (8位)
 */
FORCE_INLINE uint8_t sat_sub_u8(uint8_t a, uint8_t b) {
    return a > b ? a - b : 0;
}

// ============================================================================
// 循环优化提示
// ============================================================================

/**
 * 告诉编译器指针不重叠 (允许更激进的优化)
 */
#define RESTRICT    __restrict__

/**
 * 循环展开提示
 */
#if defined(__clang__)
    #define UNROLL(n)       _Pragma("clang loop unroll_count(" #n ")")
    #define UNROLL_FULL     _Pragma("clang loop unroll(full)")
    #define VECTORIZE       _Pragma("clang loop vectorize(enable)")
    #define NO_VECTORIZE    _Pragma("clang loop vectorize(disable)")
#elif defined(__GNUC__)
    #define UNROLL(n)       _Pragma("GCC unroll " #n)
    #define UNROLL_FULL     _Pragma("GCC unroll 128")
    #define VECTORIZE
    #define NO_VECTORIZE
#else
    #define UNROLL(n)
    #define UNROLL_FULL
    #define VECTORIZE
    #define NO_VECTORIZE
#endif

/**
 * 循环计数保证 (帮助自动向量化)
 */
#define LOOP_COUNT_GE(n)    ASSUME((n) >= 0)

// ============================================================================
// 内存操作优化
// ============================================================================

/**
 * 对齐加载 (假设地址已对齐)
 */
FORCE_INLINE uint32_t load_aligned_u32(const void* ptr) {
    return *(const uint32_t*)ptr;
}

FORCE_INLINE uint64_t load_aligned_u64(const void* ptr) {
    return *(const uint64_t*)ptr;
}

/**
 * 非对齐加载
 */
FORCE_INLINE uint32_t load_unaligned_u32(const void* ptr) {
    uint32_t val;
    __builtin_memcpy(&val, ptr, sizeof(val));
    return val;
}

FORCE_INLINE uint64_t load_unaligned_u64(const void* ptr) {
    uint64_t val;
    __builtin_memcpy(&val, ptr, sizeof(val));
    return val;
}

/**
 * 非临时存储 (绕过缓存)
 * 用于大量写入不会很快再读取的数据
 */
FORCE_INLINE void store_nontemporal_u64(void* ptr, uint64_t val) {
    #if defined(__aarch64__)
    __asm__ __volatile__("stnp %0, %0, [%1]" : : "r"(val), "r"(ptr) : "memory");
    #else
    *(uint64_t*)ptr = val;
    #endif
}

// ============================================================================
// 调试和断言
// ============================================================================

/**
 * 编译时断言
 */
#define STATIC_ASSERT(cond, msg)    _Static_assert(cond, msg)

/**
 * 运行时断言 (Release 版本中消失)
 */
#ifdef NDEBUG
    #define DEBUG_ASSERT(cond)      ASSUME(cond)
#else
    #define DEBUG_ASSERT(cond)      do { if (UNLIKELY(!(cond))) __builtin_trap(); } while(0)
#endif

/**
 * 性能关键代码标记
 */
#define PERFORMANCE_CRITICAL    HOT

#ifdef __cplusplus
}
#endif

#endif // BINARY_OPTIM_H
