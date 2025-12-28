/**
 * ARM64 Assembly Core - C Header
 * 
 * 极致性能汇编函数声明
 * 
 * @author DeepSeek AI Assistant
 */

#ifndef ASM_CORE_H
#define ASM_CORE_H

#include <stdint.h>
#include <stddef.h>

#ifdef __cplusplus
extern "C" {
#endif

// ============================================================================
// CPU 周期计数器
// ============================================================================

/**
 * 读取 CPU 虚拟计数器 (高精度时间戳)
 * @return 当前周期计数值
 */
uint64_t asm_read_cycle_counter(void);

/**
 * 读取计数器频率 (Hz)
 * @return 频率值 (通常 24MHz - 1GHz+)
 */
uint64_t asm_read_cycle_freq(void);

// ============================================================================
// 精确延迟
// ============================================================================

/**
 * 精确延迟指定周期数
 * @param cycles 目标周期数
 */
void asm_delay_cycles(uint64_t cycles);

/**
 * 精确延迟指定纳秒数
 * @param nanoseconds 纳秒数
 */
void asm_delay_ns(uint64_t nanoseconds);

/**
 * 将纳秒转换为周期数
 */
static inline uint64_t asm_ns_to_cycles(uint64_t ns) {
    uint64_t freq = asm_read_cycle_freq();
    return (ns * freq) / 1000000000ULL;
}

/**
 * 将周期转换为纳秒
 */
static inline uint64_t asm_cycles_to_ns(uint64_t cycles) {
    uint64_t freq = asm_read_cycle_freq();
    return (cycles * 1000000000ULL) / freq;
}

// ============================================================================
// 内存屏障
// ============================================================================

/**
 * 数据内存屏障 (Full System)
 * 确保屏障前的所有内存访问对其他观察者可见
 */
void asm_dmb_sy(void);

/**
 * 数据内存屏障 (Load only)
 */
void asm_dmb_ld(void);

/**
 * 数据内存屏障 (Store only)
 */
void asm_dmb_st(void);

/**
 * 数据同步屏障
 * 确保屏障前的所有指令完成执行
 */
void asm_dsb_sy(void);

/**
 * 指令同步屏障
 * 刷新指令流水线
 */
void asm_isb(void);

// ============================================================================
// 原子操作
// ============================================================================

/**
 * 32位原子比较交换 (Compare-And-Swap)
 * @param ptr 目标地址
 * @param expected 期望值
 * @param desired 新值
 * @return 原始值
 */
int32_t asm_cas32(int32_t* ptr, int32_t expected, int32_t desired);

/**
 * 64位原子比较交换
 */
int64_t asm_cas64(int64_t* ptr, int64_t expected, int64_t desired);

/**
 * 原子加法
 * @return 旧值
 */
int32_t asm_atomic_add32(int32_t* ptr, int32_t value);

/**
 * 原子交换
 * @return 旧值
 */
int32_t asm_atomic_xchg32(int32_t* ptr, int32_t value);

// ============================================================================
// 自旋锁
// ============================================================================

/**
 * 获取自旋锁 (阻塞)
 * @param lock 锁变量地址 (0=未锁, 1=已锁)
 */
void asm_spinlock_lock(int32_t* lock);

/**
 * 释放自旋锁
 */
void asm_spinlock_unlock(int32_t* lock);

/**
 * 尝试获取自旋锁 (非阻塞)
 * @return 1=成功, 0=失败
 */
int asm_spinlock_trylock(int32_t* lock);

// ============================================================================
// NEON SIMD 优化函数
// ============================================================================

/**
 * NEON 加速灰度转换
 * 每次处理 16 像素，性能约 16字节/周期
 * 
 * @param src ARGB 源数据 (需 16 字节对齐)
 * @param dst 灰度输出 (需 16 字节对齐)
 * @param count 像素数 (必须是 16 的倍数)
 */
void asm_neon_grayscale_16(const uint8_t* src, uint8_t* dst, int count);

/**
 * NEON 加速红色检测
 * 检测条件: R > 150 && R > G + 50 && R > B + 50
 * 
 * @param src ARGB 源数据
 * @param mask 输出掩码 (1=红色, 0=其他)
 * @param count 像素数 (必须是 16 的倍数)
 * @return 红色像素数量
 */
int asm_neon_find_red_16(const uint8_t* src, uint8_t* mask, int count);

/**
 * NEON 加速内存比较
 * 每次比较 16 字节
 * 
 * @return 0=相等, 非0=不相等
 */
int asm_neon_memcmp_16(const void* s1, const void* s2, size_t n);

/**
 * NEON 加速内存复制
 * 每次复制 64 字节
 */
void asm_neon_memcpy_64(void* dst, const void* src, size_t n);

// ============================================================================
// 缓存预取
// ============================================================================

/**
 * 预取数据到 L1 缓存
 */
void asm_prefetch_l1(const void* addr);

/**
 * 预取数据到 L2 缓存
 */
void asm_prefetch_l2(const void* addr);

// ============================================================================
// 模式匹配
// ============================================================================

/**
 * NEON 加速模式搜索 (类似 memmem)
 * 
 * @param haystack 搜索区域
 * @param hlen 搜索区域长度
 * @param needle 要搜索的模式
 * @param nlen 模式长度
 * @return 匹配位置指针, 或 NULL
 */
const uint8_t* asm_neon_memmem(const uint8_t* haystack, size_t hlen,
                               const uint8_t* needle, size_t nlen);

// ============================================================================
// 便捷宏
// ============================================================================

/**
 * 获取当前时间 (纳秒)
 */
static inline uint64_t asm_now_ns(void) {
    return asm_cycles_to_ns(asm_read_cycle_counter());
}

/**
 * 编译器内存屏障 (不产生指令)
 */
#define ASM_COMPILER_BARRIER() __asm__ __volatile__("" ::: "memory")

/**
 * CPU 暂停提示 (用于自旋等待)
 */
#define ASM_PAUSE() __asm__ __volatile__("yield" ::: "memory")

/**
 * 预取地址
 */
#define ASM_PREFETCH(addr) asm_prefetch_l1(addr)

// ============================================================================
// 快速内存操作
// ============================================================================

/**
 * NEON 加速内存填充
 * @param dst 目标地址
 * @param value 填充值
 * @param n 字节数
 */
void asm_neon_memset_64(void* dst, uint8_t value, size_t n);

/**
 * NEON 加速零填充
 */
void asm_neon_zero_64(void* dst, size_t n);

// ============================================================================
// CRC32 硬件加速
// ============================================================================

/**
 * 单字节 CRC32
 */
uint32_t asm_crc32_8(uint32_t crc, uint8_t data);

/**
 * 4字节 CRC32
 */
uint32_t asm_crc32_32(uint32_t crc, uint32_t data);

/**
 * 8字节 CRC32
 */
uint32_t asm_crc32_64(uint32_t crc, uint64_t data);

/**
 * 缓冲区 CRC32 校验
 * @param data 数据指针
 * @param len 数据长度
 * @param init 初始值
 * @return CRC32 值
 */
uint32_t asm_crc32_buffer(const void* data, size_t len, uint32_t init);

// ============================================================================
// 快速哈希
// ============================================================================

/**
 * 极速 32位哈希 (xxHash 风格)
 * @param data 数据指针
 * @param len 数据长度
 * @param seed 种子
 * @return 32位哈希值
 */
uint32_t asm_xxhash32(const void* data, size_t len, uint32_t seed);

// ============================================================================
// 快速数学函数
// ============================================================================

/**
 * 计算前导零数量 (Count Leading Zeros)
 */
int asm_clz32(uint32_t x);
int asm_clz64(uint64_t x);

/**
 * 计算 1 的数量 (Population Count)
 */
int asm_popcount32(uint32_t x);
int asm_popcount64(uint64_t x);

/**
 * 字节序反转
 */
uint32_t asm_byteswap32(uint32_t x);
uint64_t asm_byteswap64(uint64_t x);

/**
 * 左旋转/右旋转
 */
uint32_t asm_rotl32(uint32_t x, int n);
uint32_t asm_rotr32(uint32_t x, int n);

// ============================================================================
// NEON 向量数学
// ============================================================================

/**
 * NEON 向量加法 (float32 x 4)
 */
void asm_neon_add_f32x4(float* dst, const float* a, const float* b, int count);

/**
 * NEON 向量乘法 (float32 x 4)
 */
void asm_neon_mul_f32x4(float* dst, const float* a, const float* b, int count);

/**
 * NEON 点积
 */
float asm_neon_dot_f32(const float* a, const float* b, int count);

/**
 * NEON 向量求和
 */
float asm_neon_sum_f32(const float* a, int count);

// ============================================================================
// 快速字符串操作
// ============================================================================

/**
 * 极速字符串长度计算 (NEON 优化)
 */
size_t asm_strlen(const char* s);

/**
 * 极速字符串比较
 */
int asm_strcmp(const char* s1, const char* s2);

/**
 * 极速整数解析
 */
int64_t asm_atoi_fast(const char* s);
uint64_t asm_atou_fast(const char* s);

/**
 * 极速十六进制解析
 */
uint64_t asm_hex_to_u64(const char* s);

// ============================================================================
// 快速搜索与排序
// ============================================================================

/**
 * 二分搜索 (unsigned 32-bit 数组)
 * @param arr 有序数组
 * @param size 数组大小
 * @param target 目标值
 * @return 找到则返回索引，否则返回 -1
 */
int asm_binary_search_u32(const uint32_t* arr, int size, uint32_t target);

/**
 * 二分搜索 (signed 64-bit 数组)
 */
int asm_binary_search_i64(const int64_t* arr, int size, int64_t target);

/**
 * 查找数组最小值 (NEON 加速)
 */
uint32_t asm_find_min_u32(const uint32_t* arr, int size);

/**
 * 查找数组最大值 (NEON 加速)
 */
uint32_t asm_find_max_u32(const uint32_t* arr, int size);

/**
 * 线性查找索引 (NEON 加速)
 * @return 找到则返回索引，否则返回 -1
 */
int asm_find_index_u32(const uint32_t* arr, int size, uint32_t value);

/**
 * 3 元素排序 (无分支优化)
 */
void asm_sort3_u32(uint32_t* arr);

/**
 * 原子交换两个 uint32 值
 */
void asm_swap_u32(uint32_t* a, uint32_t* b);

/**
 * 快速排序分区 (Hoare 分区方案)
 * @return 分区点索引
 */
int asm_partition_u32(uint32_t* arr, int low, int high);

/**
 * 统计位中 1 的总数 (NEON 加速)
 */
int asm_count_bits_set(const uint8_t* data, int size);

// ============================================================================
// 便捷宏 - 搜索排序
// ============================================================================

/**
 * 快速二分查找
 */
#define ASM_BSEARCH_U32(arr, size, target) asm_binary_search_u32((arr), (size), (target))

/**
 * 快速最小值
 */
#define ASM_MIN_U32(arr, size) asm_find_min_u32((arr), (size))

/**
 * 快速最大值
 */
#define ASM_MAX_U32(arr, size) asm_find_max_u32((arr), (size))

// ============================================================================
// 便捷宏 - 扩展
// ============================================================================

/**
 * 快速清零
 */
#define ASM_ZERO(ptr, size) asm_neon_zero_64((ptr), (size))

/**
 * 快速复制
 */
#define ASM_COPY(dst, src, size) asm_neon_memcpy_64((dst), (src), (size))

/**
 * 快速比较
 */
#define ASM_CMP(a, b, size) asm_neon_memcmp_16((a), (b), (size))

/**
 * 快速 CRC32
 */
#define ASM_CRC32(data, len) asm_crc32_buffer((data), (len), 0xFFFFFFFF)

/**
 * 快速哈希
 */
#define ASM_HASH32(data, len) asm_xxhash32((data), (len), 0)

#ifdef __cplusplus
}
#endif

#endif // ASM_CORE_H
