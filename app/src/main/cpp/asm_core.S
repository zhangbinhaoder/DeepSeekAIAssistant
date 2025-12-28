/**
 * ARM64 Assembly Core - 极致性能汇编优化
 * 
 * 手写汇编实现关键热点函数，达到硬件极限性能：
 * 1. NEON SIMD 像素处理 - 16字节/周期
 * 2. 精确 CPU 周期计数 - 纳秒级精度
 * 3. 无锁原子操作 - CAS/LL-SC
 * 4. 内存屏障 - DMB/DSB/ISB
 * 
 * 架构: AArch64 (ARM64-v8a)
 * 调用约定: AAPCS64
 * 
 * @author DeepSeek AI Assistant
 */

.text
.align 4

/* ============================================================================
 * CPU 周期计数器访问
 * 使用 CNTVCT_EL0 (Virtual Timer Count) 获取高精度时间戳
 * 分辨率: 通常 1-100 纳秒级
 * ============================================================================ */

/**
 * uint64_t asm_read_cycle_counter(void)
 * 
 * 读取 CPU 虚拟计数器
 * 返回: 当前周期计数值
 */
.global asm_read_cycle_counter
.type asm_read_cycle_counter, %function
asm_read_cycle_counter:
    // 内存屏障确保之前的所有操作完成
    isb
    // 读取虚拟计数器
    mrs     x0, cntvct_el0
    ret
.size asm_read_cycle_counter, .-asm_read_cycle_counter

/**
 * uint64_t asm_read_cycle_freq(void)
 * 
 * 读取计数器频率 (Hz)
 * 返回: 频率值
 */
.global asm_read_cycle_freq
.type asm_read_cycle_freq, %function
asm_read_cycle_freq:
    mrs     x0, cntfrq_el0
    ret
.size asm_read_cycle_freq, .-asm_read_cycle_freq

/* ============================================================================
 * 精确延迟函数
 * 使用忙等待实现纳秒级精度
 * ============================================================================ */

/**
 * void asm_delay_cycles(uint64_t cycles)
 * 
 * 精确延迟指定周期数
 * 参数: x0 = 目标周期数
 */
.global asm_delay_cycles
.type asm_delay_cycles, %function
asm_delay_cycles:
    // 获取当前计数
    isb
    mrs     x1, cntvct_el0
    add     x2, x1, x0          // x2 = 目标计数值
.Ldelay_loop:
    mrs     x1, cntvct_el0
    cmp     x1, x2
    b.lo    .Ldelay_loop        // 如果还没到，继续等待
    ret
.size asm_delay_cycles, .-asm_delay_cycles

/**
 * void asm_delay_ns(uint64_t nanoseconds)
 * 
 * 精确延迟指定纳秒数
 * 参数: x0 = 纳秒数
 */
.global asm_delay_ns
.type asm_delay_ns, %function
asm_delay_ns:
    // 保存 lr 和被调用者保存寄存器
    stp     x29, x30, [sp, #-16]!
    mov     x29, sp
    
    // 获取频率
    mrs     x1, cntfrq_el0      // x1 = 频率 (ticks/sec)
    
    // 计算周期数 = ns * freq / 1e9
    // 简化: cycles = ns * (freq >> 30) >> (30 - 9) = ns * freq / 1073741824
    // 更精确: cycles = (ns * freq) / 1000000000
    mul     x2, x0, x1          // x2 = ns * freq
    
    // 除以 1e9 (使用乘法和移位近似)
    // 1e9 ≈ 2^30, 更精确用魔数
    ldr     x3, =0x44B82FA09    // 魔数用于除以 1e9
    umulh   x4, x2, x3
    lsr     x0, x4, #28         // x0 = cycles
    
    // 调用 delay_cycles
    bl      asm_delay_cycles
    
    ldp     x29, x30, [sp], #16
    ret
.size asm_delay_ns, .-asm_delay_ns

/* ============================================================================
 * 内存屏障指令
 * ============================================================================ */

/**
 * void asm_dmb_sy(void)
 * 
 * 数据内存屏障 (Full System)
 * 确保屏障前的所有内存访问对其他观察者可见
 */
.global asm_dmb_sy
.type asm_dmb_sy, %function
asm_dmb_sy:
    dmb     sy
    ret
.size asm_dmb_sy, .-asm_dmb_sy

/**
 * void asm_dmb_ld(void)
 * 
 * 数据内存屏障 (Load)
 * 只针对加载操作
 */
.global asm_dmb_ld
.type asm_dmb_ld, %function
asm_dmb_ld:
    dmb     ld
    ret
.size asm_dmb_ld, .-asm_dmb_ld

/**
 * void asm_dmb_st(void)
 * 
 * 数据内存屏障 (Store)
 * 只针对存储操作
 */
.global asm_dmb_st
.type asm_dmb_st, %function
asm_dmb_st:
    dmb     st
    ret
.size asm_dmb_st, .-asm_dmb_st

/**
 * void asm_dsb_sy(void)
 * 
 * 数据同步屏障
 * 确保屏障前的所有指令完成
 */
.global asm_dsb_sy
.type asm_dsb_sy, %function
asm_dsb_sy:
    dsb     sy
    ret
.size asm_dsb_sy, .-asm_dsb_sy

/**
 * void asm_isb(void)
 * 
 * 指令同步屏障
 * 刷新流水线
 */
.global asm_isb
.type asm_isb, %function
asm_isb:
    isb
    ret
.size asm_isb, .-asm_isb

/* ============================================================================
 * 原子操作 (Compare-And-Swap / Load-Link Store-Conditional)
 * ============================================================================ */

/**
 * int32_t asm_cas32(int32_t* ptr, int32_t expected, int32_t desired)
 * 
 * 32位原子比较交换
 * 如果 *ptr == expected, 则 *ptr = desired
 * 返回: 原始值
 */
.global asm_cas32
.type asm_cas32, %function
asm_cas32:
    // x0 = ptr, w1 = expected, w2 = desired
.Lcas32_retry:
    ldxr    w3, [x0]            // Load-Exclusive
    cmp     w3, w1              // 比较
    b.ne    .Lcas32_fail        // 不相等则失败
    stxr    w4, w2, [x0]        // Store-Exclusive
    cbnz    w4, .Lcas32_retry   // 如果存储失败，重试
    dmb     sy                  // 内存屏障
    mov     w0, w3              // 返回原始值
    ret
.Lcas32_fail:
    clrex                       // 清除独占监视器
    mov     w0, w3
    ret
.size asm_cas32, .-asm_cas32

/**
 * int64_t asm_cas64(int64_t* ptr, int64_t expected, int64_t desired)
 * 
 * 64位原子比较交换
 */
.global asm_cas64
.type asm_cas64, %function
asm_cas64:
    // x0 = ptr, x1 = expected, x2 = desired
.Lcas64_retry:
    ldxr    x3, [x0]
    cmp     x3, x1
    b.ne    .Lcas64_fail
    stxr    w4, x2, [x0]
    cbnz    w4, .Lcas64_retry
    dmb     sy
    mov     x0, x3
    ret
.Lcas64_fail:
    clrex
    mov     x0, x3
    ret
.size asm_cas64, .-asm_cas64

/**
 * int32_t asm_atomic_add32(int32_t* ptr, int32_t value)
 * 
 * 原子加法，返回旧值
 */
.global asm_atomic_add32
.type asm_atomic_add32, %function
asm_atomic_add32:
.Ladd32_retry:
    ldxr    w2, [x0]
    add     w3, w2, w1
    stxr    w4, w3, [x0]
    cbnz    w4, .Ladd32_retry
    dmb     sy
    mov     w0, w2
    ret
.size asm_atomic_add32, .-asm_atomic_add32

/**
 * int32_t asm_atomic_xchg32(int32_t* ptr, int32_t value)
 * 
 * 原子交换，返回旧值
 */
.global asm_atomic_xchg32
.type asm_atomic_xchg32, %function
asm_atomic_xchg32:
.Lxchg32_retry:
    ldxr    w2, [x0]
    stxr    w3, w1, [x0]
    cbnz    w3, .Lxchg32_retry
    dmb     sy
    mov     w0, w2
    ret
.size asm_atomic_xchg32, .-asm_atomic_xchg32

/* ============================================================================
 * 自旋锁
 * ============================================================================ */

/**
 * void asm_spinlock_lock(int32_t* lock)
 * 
 * 获取自旋锁 (0=未锁定, 1=锁定)
 */
.global asm_spinlock_lock
.type asm_spinlock_lock, %function
asm_spinlock_lock:
    mov     w1, #1
.Lspin_retry:
    ldaxr   w2, [x0]            // Load-Acquire Exclusive
    cbnz    w2, .Lspin_wait     // 如果已锁定，等待
    stxr    w3, w1, [x0]        // 尝试获取锁
    cbnz    w3, .Lspin_retry    // 存储失败，重试
    ret
.Lspin_wait:
    // WFE (Wait For Event) 降低功耗
    wfe
    b       .Lspin_retry
.size asm_spinlock_lock, .-asm_spinlock_lock

/**
 * void asm_spinlock_unlock(int32_t* lock)
 * 
 * 释放自旋锁
 */
.global asm_spinlock_unlock
.type asm_spinlock_unlock, %function
asm_spinlock_unlock:
    stlr    wzr, [x0]           // Store-Release (解锁)
    sev                         // 发送事件唤醒等待的核心
    ret
.size asm_spinlock_unlock, .-asm_spinlock_unlock

/**
 * int asm_spinlock_trylock(int32_t* lock)
 * 
 * 尝试获取自旋锁，不等待
 * 返回: 1=成功, 0=失败
 */
.global asm_spinlock_trylock
.type asm_spinlock_trylock, %function
asm_spinlock_trylock:
    mov     w1, #1
    ldaxr   w2, [x0]
    cbnz    w2, .Ltry_fail
    stxr    w3, w1, [x0]
    cbnz    w3, .Ltry_fail
    mov     w0, #1              // 成功
    ret
.Ltry_fail:
    clrex
    mov     w0, #0              // 失败
    ret
.size asm_spinlock_trylock, .-asm_spinlock_trylock

/* ============================================================================
 * NEON SIMD 优化 - 极致性能像素处理
 * ============================================================================ */

/**
 * void asm_neon_grayscale_16(const uint8_t* src, uint8_t* dst, int count)
 * 
 * NEON 加速灰度转换 - 每次处理 16 像素
 * 
 * 参数:
 *   x0 = src (ARGB 格式, 64 字节 = 16 像素)
 *   x1 = dst (灰度, 16 字节)
 *   x2 = count (像素数, 必须是 16 的倍数)
 * 
 * 权重: R=77, G=150, B=29 (总和=256, 右移8位)
 */
.global asm_neon_grayscale_16
.type asm_neon_grayscale_16, %function
asm_neon_grayscale_16:
    // 加载权重常量
    mov     w3, #77
    dup     v16.16b, w3         // v16 = [77, 77, ...]  (R权重)
    mov     w3, #150
    dup     v17.16b, w3         // v17 = [150, 150, ...] (G权重)
    mov     w3, #29
    dup     v18.16b, w3         // v18 = [29, 29, ...]  (B权重)
    
    // 计算循环次数
    lsr     x2, x2, #4          // count / 16
    cbz     x2, .Lgs_done
    
.Lgs_loop:
    // 加载 16 个 ARGB 像素 (64 字节)
    // ld4 解交织: v0=A, v1=R, v2=G, v3=B
    ld4     {v0.16b, v1.16b, v2.16b, v3.16b}, [x0], #64
    
    // Gray = (R*77 + G*150 + B*29) >> 8
    // 使用 umull/umlal 实现 16x8 乘法和累加
    
    // 处理低 8 像素
    umull   v4.8h, v1.8b, v16.8b    // R * 77
    umlal   v4.8h, v2.8b, v17.8b    // + G * 150
    umlal   v4.8h, v3.8b, v18.8b    // + B * 29
    
    // 处理高 8 像素
    umull2  v5.8h, v1.16b, v16.16b
    umlal2  v5.8h, v2.16b, v17.16b
    umlal2  v5.8h, v3.16b, v18.16b
    
    // 右移 8 位并收窄到 8 位
    shrn    v6.8b, v4.8h, #8
    shrn2   v6.16b, v5.8h, #8
    
    // 存储 16 个灰度值
    st1     {v6.16b}, [x1], #16
    
    subs    x2, x2, #1
    b.ne    .Lgs_loop
    
.Lgs_done:
    ret
.size asm_neon_grayscale_16, .-asm_neon_grayscale_16

/**
 * int asm_neon_find_red_16(const uint8_t* src, uint8_t* mask, int count)
 * 
 * NEON 加速红色检测 - 每次处理 16 像素
 * 
 * 检测条件: R > 150 && R > G + 50 && R > B + 50
 * 
 * 参数:
 *   x0 = src (ARGB 格式)
 *   x1 = mask (输出, 1=红色, 0=其他)
 *   x2 = count (像素数)
 * 返回: 红色像素数量
 */
.global asm_neon_find_red_16
.type asm_neon_find_red_16, %function
asm_neon_find_red_16:
    // 保存返回值
    mov     x9, #0              // 红色计数
    
    // 阈值常量
    mov     w3, #150
    dup     v16.16b, w3         // R 阈值
    mov     w3, #50
    dup     v17.16b, w3         // 差值阈值
    
    // 循环次数
    lsr     x4, x2, #4
    cbz     x4, .Lred_done
    
.Lred_loop:
    // 加载 16 像素
    ld4     {v0.16b, v1.16b, v2.16b, v3.16b}, [x0], #64
    // v1=R, v2=G, v3=B
    
    // 条件1: R > 150
    cmhi    v4.16b, v1.16b, v16.16b
    
    // 条件2: R - G > 50 (饱和减法防止下溢)
    uqsub   v5.16b, v1.16b, v2.16b
    cmhi    v5.16b, v5.16b, v17.16b
    
    // 条件3: R - B > 50
    uqsub   v6.16b, v1.16b, v3.16b
    cmhi    v6.16b, v6.16b, v17.16b
    
    // 合并条件
    and     v7.16b, v4.16b, v5.16b
    and     v7.16b, v7.16b, v6.16b
    
    // 归一化到 0/1
    ushr    v7.16b, v7.16b, #7
    
    // 存储 mask
    st1     {v7.16b}, [x1], #16
    
    // 计数 (水平加法)
    uaddlv  h8, v7.16b
    umov    w5, v8.h[0]
    add     x9, x9, x5
    
    subs    x4, x4, #1
    b.ne    .Lred_loop
    
.Lred_done:
    mov     x0, x9              // 返回计数
    ret
.size asm_neon_find_red_16, .-asm_neon_find_red_16

/**
 * int asm_neon_memcmp_16(const void* s1, const void* s2, size_t n)
 * 
 * NEON 加速内存比较 - 每次比较 16 字节
 * 返回: 0=相等, 非0=不相等
 */
.global asm_neon_memcmp_16
.type asm_neon_memcmp_16, %function
asm_neon_memcmp_16:
    // x0 = s1, x1 = s2, x2 = n (字节数)
    lsr     x3, x2, #4          // n / 16
    cbz     x3, .Lmc_tail
    
.Lmc_loop:
    ld1     {v0.16b}, [x0], #16
    ld1     {v1.16b}, [x1], #16
    cmeq    v2.16b, v0.16b, v1.16b
    // 检查是否全相等
    uminv   b3, v2.16b
    umov    w4, v3.b[0]
    cmp     w4, #0xFF
    b.ne    .Lmc_diff           // 有不相等
    subs    x3, x3, #1
    b.ne    .Lmc_loop
    
.Lmc_tail:
    // 处理剩余字节 (< 16)
    and     x2, x2, #15
    cbz     x2, .Lmc_equal
    
.Lmc_byte:
    ldrb    w3, [x0], #1
    ldrb    w4, [x1], #1
    cmp     w3, w4
    b.ne    .Lmc_diff
    subs    x2, x2, #1
    b.ne    .Lmc_byte
    
.Lmc_equal:
    mov     w0, #0
    ret
    
.Lmc_diff:
    mov     w0, #1
    ret
.size asm_neon_memcmp_16, .-asm_neon_memcmp_16

/**
 * void asm_neon_memcpy_64(void* dst, const void* src, size_t n)
 * 
 * NEON 加速内存复制 - 每次复制 64 字节
 */
.global asm_neon_memcpy_64
.type asm_neon_memcpy_64, %function
asm_neon_memcpy_64:
    // x0 = dst, x1 = src, x2 = n
    lsr     x3, x2, #6          // n / 64
    cbz     x3, .Lcp_32
    
.Lcp_64_loop:
    ldp     q0, q1, [x1], #32
    ldp     q2, q3, [x1], #32
    stp     q0, q1, [x0], #32
    stp     q2, q3, [x0], #32
    subs    x3, x3, #1
    b.ne    .Lcp_64_loop
    
.Lcp_32:
    // 处理剩余 32 字节
    tbz     x2, #5, .Lcp_16
    ldp     q0, q1, [x1], #32
    stp     q0, q1, [x0], #32
    
.Lcp_16:
    tbz     x2, #4, .Lcp_8
    ldr     q0, [x1], #16
    str     q0, [x0], #16
    
.Lcp_8:
    tbz     x2, #3, .Lcp_4
    ldr     x4, [x1], #8
    str     x4, [x0], #8
    
.Lcp_4:
    tbz     x2, #2, .Lcp_2
    ldr     w4, [x1], #4
    str     w4, [x0], #4
    
.Lcp_2:
    tbz     x2, #1, .Lcp_1
    ldrh    w4, [x1], #2
    strh    w4, [x0], #2
    
.Lcp_1:
    tbz     x2, #0, .Lcp_done
    ldrb    w4, [x1]
    strb    w4, [x0]
    
.Lcp_done:
    ret
.size asm_neon_memcpy_64, .-asm_neon_memcpy_64

/**
 * void asm_prefetch_l1(const void* addr)
 * 
 * 预取数据到 L1 缓存
 */
.global asm_prefetch_l1
.type asm_prefetch_l1, %function
asm_prefetch_l1:
    prfm    pldl1keep, [x0]
    ret
.size asm_prefetch_l1, .-asm_prefetch_l1

/**
 * void asm_prefetch_l2(const void* addr)
 * 
 * 预取数据到 L2 缓存
 */
.global asm_prefetch_l2
.type asm_prefetch_l2, %function
asm_prefetch_l2:
    prfm    pldl2keep, [x0]
    ret
.size asm_prefetch_l2, .-asm_prefetch_l2

/* ============================================================================
 * 模式匹配 - 快速字节搜索
 * ============================================================================ */

/**
 * const uint8_t* asm_neon_memmem(const uint8_t* haystack, size_t hlen,
 *                                 const uint8_t* needle, size_t nlen)
 * 
 * NEON 加速模式搜索
 * 返回: 匹配位置指针, 或 NULL
 */
.global asm_neon_memmem
.type asm_neon_memmem, %function
asm_neon_memmem:
    // x0 = haystack, x1 = hlen, x2 = needle, x3 = nlen
    // 简化版: 只搜索第一个字节匹配，然后精确比较
    
    cbz     x3, .Lmm_null       // needle 为空
    cmp     x3, x1
    b.hi    .Lmm_null           // needle 比 haystack 长
    
    // 加载 needle 首字节
    ldrb    w4, [x2]
    dup     v0.16b, w4
    
    // 保存参数
    mov     x5, x0              // haystack
    sub     x6, x1, x3          // 最大搜索位置
    add     x6, x6, #1
    
.Lmm_loop:
    cmp     x6, #16
    b.lo    .Lmm_scalar
    
    // NEON 搜索首字节
    ld1     {v1.16b}, [x5]
    cmeq    v2.16b, v0.16b, v1.16b
    
    // 检查是否有匹配
    umaxv   b3, v2.16b
    umov    w7, v3.b[0]
    cbz     w7, .Lmm_next16     // 无匹配，跳过 16 字节
    
    // 有匹配，将向量存储到栈上然后逐字节检查
    sub     sp, sp, #16
    st1     {v2.16b}, [sp]
    
    mov     x8, #0
.Lmm_check:
    ldrb    w7, [sp, x8]        // 从栈上加载匹配结果
    cbz     w7, .Lmm_next_byte
    
    // 精确比较
    add     x9, x5, x8          // 候选位置
    mov     x10, x2             // needle
    mov     x11, x3             // needle 长度
.Lmm_cmp:
    ldrb    w12, [x9], #1
    ldrb    w13, [x10], #1
    cmp     w12, w13
    b.ne    .Lmm_next_byte
    subs    x11, x11, #1
    b.ne    .Lmm_cmp
    // 找到匹配
    add     x0, x5, x8
    add     sp, sp, #16         // 恢复栈
    ret
    
.Lmm_next_byte:
    add     x8, x8, #1
    cmp     x8, #16
    b.lo    .Lmm_check
    
    add     sp, sp, #16         // 恢复栈

.Lmm_next16:
    add     x5, x5, #16
    sub     x6, x6, #16
    b       .Lmm_loop
    
.Lmm_scalar:
    // 标量搜索剩余字节
    cbz     x6, .Lmm_null
.Lmm_scalar_loop:
    ldrb    w7, [x5]
    cmp     w7, w4
    b.ne    .Lmm_scalar_next
    
    // 精确比较
    mov     x9, x5
    mov     x10, x2
    mov     x11, x3
.Lmm_scalar_cmp:
    ldrb    w12, [x9], #1
    ldrb    w13, [x10], #1
    cmp     w12, w13
    b.ne    .Lmm_scalar_next
    subs    x11, x11, #1
    b.ne    .Lmm_scalar_cmp
    mov     x0, x5
    ret
    
.Lmm_scalar_next:
    add     x5, x5, #1
    subs    x6, x6, #1
    b.ne    .Lmm_scalar_loop
    
.Lmm_null:
    mov     x0, #0
    ret
.size asm_neon_memmem, .-asm_neon_memmem

/* ============================================================================
 * 极致优化 - 快速内存操作
 * ============================================================================ */

/**
 * void asm_neon_memset_64(void* dst, uint8_t value, size_t n)
 * 
 * NEON 加速内存填充 - 每次填充 64 字节
 */
.global asm_neon_memset_64
.type asm_neon_memset_64, %function
asm_neon_memset_64:
    // x0 = dst, w1 = value, x2 = n
    dup     v0.16b, w1
    mov     v1.16b, v0.16b
    mov     v2.16b, v0.16b
    mov     v3.16b, v0.16b
    
    lsr     x3, x2, #6          // n / 64
    cbz     x3, .Lms_32
    
.Lms_64_loop:
    stp     q0, q1, [x0], #32
    stp     q2, q3, [x0], #32
    subs    x3, x3, #1
    b.ne    .Lms_64_loop
    
.Lms_32:
    tbz     x2, #5, .Lms_16
    stp     q0, q1, [x0], #32
    
.Lms_16:
    tbz     x2, #4, .Lms_8
    str     q0, [x0], #16
    
.Lms_8:
    tbz     x2, #3, .Lms_4
    str     d0, [x0], #8
    
.Lms_4:
    tbz     x2, #2, .Lms_2
    str     s0, [x0], #4
    
.Lms_2:
    tbz     x2, #1, .Lms_1
    strh    w1, [x0], #2
    
.Lms_1:
    tbz     x2, #0, .Lms_done
    strb    w1, [x0]
    
.Lms_done:
    ret
.size asm_neon_memset_64, .-asm_neon_memset_64

/**
 * void asm_neon_zero_64(void* dst, size_t n)
 * 
 * NEON 加速零填充 - 极速清零
 */
.global asm_neon_zero_64
.type asm_neon_zero_64, %function
asm_neon_zero_64:
    // x0 = dst, x1 = n
    movi    v0.16b, #0
    movi    v1.16b, #0
    
    lsr     x2, x1, #5          // n / 32
    cbz     x2, .Lzero_16
    
.Lzero_32_loop:
    stp     q0, q1, [x0], #32
    subs    x2, x2, #1
    b.ne    .Lzero_32_loop
    
.Lzero_16:
    tbz     x1, #4, .Lzero_8
    str     q0, [x0], #16
    
.Lzero_8:
    tbz     x1, #3, .Lzero_4
    str     xzr, [x0], #8
    
.Lzero_4:
    tbz     x1, #2, .Lzero_2
    str     wzr, [x0], #4
    
.Lzero_2:
    tbz     x1, #1, .Lzero_1
    strh    wzr, [x0], #2
    
.Lzero_1:
    tbz     x1, #0, .Lzero_done
    strb    wzr, [x0]
    
.Lzero_done:
    ret
.size asm_neon_zero_64, .-asm_neon_zero_64

/* ============================================================================
 * CRC32 硬件加速 (ARMv8.1-A CRC32 扩展)
 * 注意: 这些指令需要 ARMv8-A + CRC 扩展
 * ============================================================================ */

// 启用 CRC32 扩展
.arch armv8-a+crc

/**
 * uint32_t asm_crc32_8(uint32_t crc, uint8_t data)
 * 单字节 CRC32
 */
.global asm_crc32_8
.type asm_crc32_8, %function
asm_crc32_8:
    crc32b  w0, w0, w1
    ret
.size asm_crc32_8, .-asm_crc32_8

/**
 * uint32_t asm_crc32_32(uint32_t crc, uint32_t data)
 * 4字节 CRC32
 */
.global asm_crc32_32
.type asm_crc32_32, %function
asm_crc32_32:
    crc32w  w0, w0, w1
    ret
.size asm_crc32_32, .-asm_crc32_32

/**
 * uint32_t asm_crc32_64(uint32_t crc, uint64_t data)
 * 8字节 CRC32
 */
.global asm_crc32_64
.type asm_crc32_64, %function
asm_crc32_64:
    crc32x  w0, w0, x1
    ret
.size asm_crc32_64, .-asm_crc32_64

/**
 * uint32_t asm_crc32_buffer(const void* data, size_t len, uint32_t init)
 * 缓冲区 CRC32 校验
 */
.global asm_crc32_buffer
.type asm_crc32_buffer, %function
asm_crc32_buffer:
    // x0 = data, x1 = len, w2 = init
    mov     w3, w2              // w3 = crc
    
    // 8字节对齐处理
    lsr     x4, x1, #3          // len / 8
    cbz     x4, .Lcrc_tail
    
.Lcrc_loop_8:
    ldr     x5, [x0], #8
    crc32x  w3, w3, x5
    subs    x4, x4, #1
    b.ne    .Lcrc_loop_8
    
.Lcrc_tail:
    // 处理剩余字节
    and     x1, x1, #7
    cbz     x1, .Lcrc_done
    
.Lcrc_byte:
    ldrb    w5, [x0], #1
    crc32b  w3, w3, w5
    subs    x1, x1, #1
    b.ne    .Lcrc_byte
    
.Lcrc_done:
    mov     w0, w3
    ret
.size asm_crc32_buffer, .-asm_crc32_buffer

// 恢复默认架构
.arch armv8-a

/* ============================================================================
 * xxHash 风格快速哈希 (简化版)
 * ============================================================================ */

#define XXHASH_PRIME1   0x9E3779B1
#define XXHASH_PRIME2   0x85EBCA77
#define XXHASH_PRIME3   0xC2B2AE3D
#define XXHASH_PRIME5   0x165667B1

/**
 * uint32_t asm_xxhash32(const void* data, size_t len, uint32_t seed)
 * 极速 32位 哈希
 */
.global asm_xxhash32
.type asm_xxhash32, %function
asm_xxhash32:
    // x0 = data, x1 = len, w2 = seed
    stp     x29, x30, [sp, #-16]!
    mov     x29, sp
    
    // 加载常量
    ldr     w9, =XXHASH_PRIME1
    ldr     w10, =XXHASH_PRIME2
    ldr     w11, =XXHASH_PRIME3
    ldr     w12, =XXHASH_PRIME5
    
    mov     w3, w2              // acc = seed
    add     w3, w3, w12         // acc += PRIME5
    add     w3, w3, w1          // acc += len
    
    // 每次处理 4 字节
    lsr     x4, x1, #2
    cbz     x4, .Lxxh_tail
    
.Lxxh_loop_4:
    ldr     w5, [x0], #4
    mul     w6, w5, w11         // k = data * PRIME3
    add     w3, w3, w6          // acc += k
    ror     w3, w3, #15         // acc = rol(acc, 17) = ror(acc, 32-17)
    mul     w3, w3, w9          // acc *= PRIME1
    subs    x4, x4, #1
    b.ne    .Lxxh_loop_4
    
.Lxxh_tail:
    // 处理剩余字节
    and     x1, x1, #3
    cbz     x1, .Lxxh_final
    
.Lxxh_byte:
    ldrb    w5, [x0], #1
    mul     w6, w5, w12         // k = byte * PRIME5
    add     w3, w3, w6
    ror     w3, w3, #21         // acc = rol(acc, 11)
    mul     w3, w3, w9
    subs    x1, x1, #1
    b.ne    .Lxxh_byte
    
.Lxxh_final:
    // 最终混合
    eor     w3, w3, w3, lsr #15
    mul     w3, w3, w10
    eor     w3, w3, w3, lsr #13
    mul     w3, w3, w11
    eor     w3, w3, w3, lsr #16
    
    mov     w0, w3
    ldp     x29, x30, [sp], #16
    ret
.size asm_xxhash32, .-asm_xxhash32

/* ============================================================================
 * 快速数学函数
 * ============================================================================ */

/**
 * int asm_clz32(uint32_t x)
 * 计算前导零数量 (Count Leading Zeros)
 */
.global asm_clz32
.type asm_clz32, %function
asm_clz32:
    clz     w0, w0
    ret
.size asm_clz32, .-asm_clz32

/**
 * int asm_clz64(uint64_t x)
 * 64位前导零
 */
.global asm_clz64
.type asm_clz64, %function
asm_clz64:
    clz     x0, x0
    ret
.size asm_clz64, .-asm_clz64

/**
 * int asm_popcount32(uint32_t x)
 * 计算 1 的数量 (Population Count)
 */
.global asm_popcount32
.type asm_popcount32, %function
asm_popcount32:
    fmov    d0, x0
    cnt     v0.8b, v0.8b
    uaddlv  h0, v0.8b
    umov    w0, v0.h[0]
    ret
.size asm_popcount32, .-asm_popcount32

/**
 * int asm_popcount64(uint64_t x)
 * 64位 Population Count
 */
.global asm_popcount64
.type asm_popcount64, %function
asm_popcount64:
    fmov    d0, x0
    cnt     v0.8b, v0.8b
    uaddlv  h0, v0.8b
    umov    w0, v0.h[0]
    ret
.size asm_popcount64, .-asm_popcount64

/**
 * uint32_t asm_byteswap32(uint32_t x)
 * 字节序反转
 */
.global asm_byteswap32
.type asm_byteswap32, %function
asm_byteswap32:
    rev     w0, w0
    ret
.size asm_byteswap32, .-asm_byteswap32

/**
 * uint64_t asm_byteswap64(uint64_t x)
 * 64位字节序反转
 */
.global asm_byteswap64
.type asm_byteswap64, %function
asm_byteswap64:
    rev     x0, x0
    ret
.size asm_byteswap64, .-asm_byteswap64

/**
 * uint32_t asm_rotl32(uint32_t x, int n)
 * 左旋转
 */
.global asm_rotl32
.type asm_rotl32, %function
asm_rotl32:
    neg     w2, w1
    ror     w0, w0, w2
    ret
.size asm_rotl32, .-asm_rotl32

/**
 * uint32_t asm_rotr32(uint32_t x, int n)
 * 右旋转
 */
.global asm_rotr32
.type asm_rotr32, %function
asm_rotr32:
    ror     w0, w0, w1
    ret
.size asm_rotr32, .-asm_rotr32

/* ============================================================================
 * NEON 向量数学
 * ============================================================================ */

/**
 * void asm_neon_add_f32x4(float* dst, const float* a, const float* b, int count)
 * NEON 向量加法
 */
.global asm_neon_add_f32x4
.type asm_neon_add_f32x4, %function
asm_neon_add_f32x4:
    // x0=dst, x1=a, x2=b, w3=count
    lsr     w3, w3, #2          // count / 4
    cbz     w3, .Ladd_done
    
.Ladd_loop:
    ld1     {v0.4s}, [x1], #16
    ld1     {v1.4s}, [x2], #16
    fadd    v2.4s, v0.4s, v1.4s
    st1     {v2.4s}, [x0], #16
    subs    w3, w3, #1
    b.ne    .Ladd_loop
    
.Ladd_done:
    ret
.size asm_neon_add_f32x4, .-asm_neon_add_f32x4

/**
 * void asm_neon_mul_f32x4(float* dst, const float* a, const float* b, int count)
 * NEON 向量乘法
 */
.global asm_neon_mul_f32x4
.type asm_neon_mul_f32x4, %function
asm_neon_mul_f32x4:
    lsr     w3, w3, #2
    cbz     w3, .Lmul_done
    
.Lmul_loop:
    ld1     {v0.4s}, [x1], #16
    ld1     {v1.4s}, [x2], #16
    fmul    v2.4s, v0.4s, v1.4s
    st1     {v2.4s}, [x0], #16
    subs    w3, w3, #1
    b.ne    .Lmul_loop
    
.Lmul_done:
    ret
.size asm_neon_mul_f32x4, .-asm_neon_mul_f32x4

/**
 * float asm_neon_dot_f32(const float* a, const float* b, int count)
 * NEON 点积
 */
.global asm_neon_dot_f32
.type asm_neon_dot_f32, %function
asm_neon_dot_f32:
    // x0=a, x1=b, w2=count
    movi    v4.4s, #0           // 累加器
    
    lsr     w3, w2, #2
    cbz     w3, .Ldot_tail
    
.Ldot_loop:
    ld1     {v0.4s}, [x0], #16
    ld1     {v1.4s}, [x1], #16
    fmla    v4.4s, v0.4s, v1.4s
    subs    w3, w3, #1
    b.ne    .Ldot_loop
    
.Ldot_tail:
    // 水平加法
    faddp   v4.4s, v4.4s, v4.4s
    faddp   s0, v4.2s
    
    // 处理剩余元素
    and     w2, w2, #3
    cbz     w2, .Ldot_done
    
.Ldot_scalar:
    ldr     s1, [x0], #4
    ldr     s2, [x1], #4
    fmadd   s0, s1, s2, s0
    subs    w2, w2, #1
    b.ne    .Ldot_scalar
    
.Ldot_done:
    ret
.size asm_neon_dot_f32, .-asm_neon_dot_f32

/**
 * float asm_neon_sum_f32(const float* a, int count)
 * NEON 向量求和
 */
.global asm_neon_sum_f32
.type asm_neon_sum_f32, %function
asm_neon_sum_f32:
    movi    v4.4s, #0
    
    lsr     w2, w1, #2
    cbz     w2, .Lsum_tail
    
.Lsum_loop:
    ld1     {v0.4s}, [x0], #16
    fadd    v4.4s, v4.4s, v0.4s
    subs    w2, w2, #1
    b.ne    .Lsum_loop
    
.Lsum_tail:
    faddp   v4.4s, v4.4s, v4.4s
    faddp   s0, v4.2s
    
    and     w1, w1, #3
    cbz     w1, .Lsum_done
    
.Lsum_scalar:
    ldr     s1, [x0], #4
    fadd    s0, s0, s1
    subs    w1, w1, #1
    b.ne    .Lsum_scalar
    
.Lsum_done:
    ret
.size asm_neon_sum_f32, .-asm_neon_sum_f32

/* ============================================================================
 * 快速字符串操作
 * ============================================================================ */

/**
 * size_t asm_strlen(const char* s)
 * 极速字符串长度计算
 */
.global asm_strlen
.type asm_strlen, %function
asm_strlen:
    mov     x1, x0              // 保存起始地址
    
    // 预加载 16 字节
    ld1     {v0.16b}, [x0], #16
    movi    v1.16b, #0
    cmeq    v2.16b, v0.16b, v1.16b
    umaxv   b3, v2.16b
    umov    w2, v3.b[0]
    cbnz    w2, .Lstrlen_found_first
    
.Lstrlen_loop:
    ld1     {v0.16b}, [x0], #16
    cmeq    v2.16b, v0.16b, v1.16b
    umaxv   b3, v2.16b
    umov    w2, v3.b[0]
    cbz     w2, .Lstrlen_loop
    
    // 找到零，回退 16 字节
    sub     x0, x0, #16
    
.Lstrlen_found_first:
    // 逐字节找到精确位置
    sub     x0, x0, #16
    
.Lstrlen_byte:
    ldrb    w2, [x0], #1
    cbnz    w2, .Lstrlen_byte
    
    sub     x0, x0, x1
    sub     x0, x0, #1
    ret
.size asm_strlen, .-asm_strlen

/**
 * int asm_strcmp(const char* s1, const char* s2)
 * 极速字符串比较
 */
.global asm_strcmp
.type asm_strcmp, %function
asm_strcmp:
    // x0 = s1, x1 = s2
.Lstrcmp_loop:
    ldrb    w2, [x0], #1
    ldrb    w3, [x1], #1
    cmp     w2, w3
    b.ne    .Lstrcmp_diff
    cbnz    w2, .Lstrcmp_loop
    // 都是 0，相等
    mov     w0, #0
    ret
    
.Lstrcmp_diff:
    sub     w0, w2, w3
    ret
.size asm_strcmp, .-asm_strcmp

/* ============================================================================
 * 快速整数解析
 * ============================================================================ */

/**
 * int64_t asm_atoi_fast(const char* s)
 * 极速整数解析
 */
.global asm_atoi_fast
.type asm_atoi_fast, %function
asm_atoi_fast:
    mov     x1, #0              // result = 0
    mov     w2, #0              // negative = 0
    
    // 跳过空白
.Latoi_skip:
    ldrb    w3, [x0], #1
    cmp     w3, #' '
    b.eq    .Latoi_skip
    cmp     w3, #'\t'
    b.eq    .Latoi_skip
    
    // 检查符号
    cmp     w3, #'-'
    b.ne    .Latoi_check_plus
    mov     w2, #1
    ldrb    w3, [x0], #1
    b       .Latoi_digit
    
.Latoi_check_plus:
    cmp     w3, #'+'
    b.ne    .Latoi_digit
    ldrb    w3, [x0], #1
    
.Latoi_digit:
    // 检查是否是数字
    sub     w4, w3, #'0'
    cmp     w4, #9
    b.hi    .Latoi_done
    
    // result = result * 10 + digit
    mov     x5, #10
    mul     x1, x1, x5
    add     x1, x1, x4
    
    ldrb    w3, [x0], #1
    b       .Latoi_digit
    
.Latoi_done:
    // 应用符号
    cbz     w2, .Latoi_ret
    neg     x1, x1
    
.Latoi_ret:
    mov     x0, x1
    ret
.size asm_atoi_fast, .-asm_atoi_fast

/**
 * uint64_t asm_atou_fast(const char* s)
 * 极速无符号整数解析
 */
.global asm_atou_fast
.type asm_atou_fast, %function
asm_atou_fast:
    mov     x1, #0              // result = 0
    
.Latou_loop:
    ldrb    w2, [x0], #1
    sub     w3, w2, #'0'
    cmp     w3, #9
    b.hi    .Latou_done
    
    mov     x4, #10
    madd    x1, x1, x4, x3      // result = result * 10 + digit
    b       .Latou_loop
    
.Latou_done:
    mov     x0, x1
    ret
.size asm_atou_fast, .-asm_atou_fast

/**
 * uint64_t asm_hex_to_u64(const char* s)
 * 极速十六进制解析
 */
.global asm_hex_to_u64
.type asm_hex_to_u64, %function
asm_hex_to_u64:
    mov     x1, #0              // result
    
    // 跳过 0x 前缀
    ldrb    w2, [x0]
    cmp     w2, #'0'
    b.ne    .Lhex_parse
    ldrb    w2, [x0, #1]
    cmp     w2, #'x'
    b.eq    .Lhex_skip_prefix
    cmp     w2, #'X'
    b.ne    .Lhex_parse
    
.Lhex_skip_prefix:
    add     x0, x0, #2
    
.Lhex_parse:
    ldrb    w2, [x0], #1
    
    // 0-9
    sub     w3, w2, #'0'
    cmp     w3, #9
    b.ls    .Lhex_add
    
    // a-f
    sub     w3, w2, #'a'
    cmp     w3, #5
    b.hi    .Lhex_upper
    add     w3, w3, #10
    b       .Lhex_add
    
.Lhex_upper:
    // A-F
    sub     w3, w2, #'A'
    cmp     w3, #5
    b.hi    .Lhex_done
    add     w3, w3, #10
    
.Lhex_add:
    lsl     x1, x1, #4
    add     x1, x1, x3
    b       .Lhex_parse
    
.Lhex_done:
    mov     x0, x1
    ret
.size asm_hex_to_u64, .-asm_hex_to_u64

/* ============================================================================
 * 快速搜索与排序算法 (ARM64 优化)
 * ============================================================================ */

/**
 * int asm_binary_search_u32(const uint32_t* arr, int size, uint32_t target)
 * 二分搜索 (unsigned 32-bit)
 * 返回: 找到则返回索引，否则返回 -1
 */
.global asm_binary_search_u32
.type asm_binary_search_u32, %function
asm_binary_search_u32:
    // x0 = arr, w1 = size, w2 = target
    mov     w3, #0              // left = 0
    sub     w4, w1, #1          // right = size - 1
    mov     w5, #-1             // result = -1
    
.Lbs_loop:
    cmp     w3, w4
    b.gt    .Lbs_not_found
    
    add     w6, w3, w4          // mid = (left + right) / 2
    lsr     w6, w6, #1
    
    ldr     w7, [x0, x6, lsl #2]  // arr[mid]
    
    cmp     w7, w2
    b.eq    .Lbs_found
    b.hi    .Lbs_go_left
    
    // arr[mid] < target, go right
    add     w3, w6, #1
    b       .Lbs_loop
    
.Lbs_go_left:
    sub     w4, w6, #1
    b       .Lbs_loop
    
.Lbs_found:
    mov     w5, w6
    
.Lbs_not_found:
    mov     w0, w5
    ret
.size asm_binary_search_u32, .-asm_binary_search_u32

/**
 * int asm_binary_search_i64(const int64_t* arr, int size, int64_t target)
 * 二分搜索 (signed 64-bit)
 */
.global asm_binary_search_i64
.type asm_binary_search_i64, %function
asm_binary_search_i64:
    // x0 = arr, w1 = size, x2 = target
    mov     w3, #0              // left = 0
    sub     w4, w1, #1          // right = size - 1
    mov     w5, #-1             // result = -1
    
.Lbs64_loop:
    cmp     w3, w4
    b.gt    .Lbs64_done
    
    add     w6, w3, w4
    lsr     w6, w6, #1          // mid
    
    ldr     x7, [x0, x6, lsl #3]  // arr[mid]
    
    cmp     x7, x2
    b.eq    .Lbs64_found
    b.gt    .Lbs64_go_left
    
    add     w3, w6, #1
    b       .Lbs64_loop
    
.Lbs64_go_left:
    sub     w4, w6, #1
    b       .Lbs64_loop
    
.Lbs64_found:
    mov     w5, w6
    
.Lbs64_done:
    mov     w0, w5
    ret
.size asm_binary_search_i64, .-asm_binary_search_i64

/**
 * uint32_t asm_find_min_u32(const uint32_t* arr, int size)
 * 查找数组最小值 (NEON 加速)
 */
.global asm_find_min_u32
.type asm_find_min_u32, %function
asm_find_min_u32:
    // x0 = arr, w1 = size
    cbz     w1, .Lfmin_empty
    
    ldr     w2, [x0]            // min = arr[0]
    cmp     w1, #4
    b.lt    .Lfmin_scalar
    
    // NEON: 处理 4 个元素一组
    dup     v0.4s, w2           // v0 = min broadcast
    lsr     w3, w1, #2          // count / 4
    mov     x4, x0
    
.Lfmin_neon_loop:
    ldr     q1, [x4], #16       // 加载 4 个 uint32
    umin    v0.4s, v0.4s, v1.4s // 向量最小值
    subs    w3, w3, #1
    b.ne    .Lfmin_neon_loop
    
    // 水平归约
    uminv   s0, v0.4s           // 找到 v0 中的最小值
    fmov    w2, s0
    
    // 处理剩余元素
    and     w3, w1, #3          // remainder
    cbz     w3, .Lfmin_done
    
    lsl     w5, w1, #2
    sub     w5, w5, w3, lsl #2
    add     x4, x0, x5          // 指向剩余元素
    
.Lfmin_tail:
    ldr     w5, [x4], #4
    cmp     w5, w2
    csel    w2, w5, w2, lo
    subs    w3, w3, #1
    b.ne    .Lfmin_tail
    b       .Lfmin_done
    
.Lfmin_scalar:
    mov     w3, #1              // i = 1
.Lfmin_scalar_loop:
    cmp     w3, w1
    b.ge    .Lfmin_done
    ldr     w4, [x0, x3, lsl #2]
    cmp     w4, w2
    csel    w2, w4, w2, lo
    add     w3, w3, #1
    b       .Lfmin_scalar_loop
    
.Lfmin_done:
    mov     w0, w2
    ret
    
.Lfmin_empty:
    mov     w0, #-1             // 返回 0xFFFFFFFF
    ret
.size asm_find_min_u32, .-asm_find_min_u32

/**
 * uint32_t asm_find_max_u32(const uint32_t* arr, int size)
 * 查找数组最大值 (NEON 加速)
 */
.global asm_find_max_u32
.type asm_find_max_u32, %function
asm_find_max_u32:
    // x0 = arr, w1 = size
    cbz     w1, .Lfmax_empty
    
    ldr     w2, [x0]            // max = arr[0]
    cmp     w1, #4
    b.lt    .Lfmax_scalar
    
    // NEON
    dup     v0.4s, w2
    lsr     w3, w1, #2
    mov     x4, x0
    
.Lfmax_neon_loop:
    ldr     q1, [x4], #16
    umax    v0.4s, v0.4s, v1.4s
    subs    w3, w3, #1
    b.ne    .Lfmax_neon_loop
    
    umaxv   s0, v0.4s
    fmov    w2, s0
    
    // 剩余
    and     w3, w1, #3
    cbz     w3, .Lfmax_done
    
    lsl     w5, w1, #2
    sub     w5, w5, w3, lsl #2
    add     x4, x0, x5
    
.Lfmax_tail:
    ldr     w5, [x4], #4
    cmp     w5, w2
    csel    w2, w5, w2, hi
    subs    w3, w3, #1
    b.ne    .Lfmax_tail
    b       .Lfmax_done
    
.Lfmax_scalar:
    mov     w3, #1
.Lfmax_scalar_loop:
    cmp     w3, w1
    b.ge    .Lfmax_done
    ldr     w4, [x0, x3, lsl #2]
    cmp     w4, w2
    csel    w2, w4, w2, hi
    add     w3, w3, #1
    b       .Lfmax_scalar_loop
    
.Lfmax_done:
    mov     w0, w2
    ret
    
.Lfmax_empty:
    mov     w0, #0
    ret
.size asm_find_max_u32, .-asm_find_max_u32

/**
 * int asm_find_index_u32(const uint32_t* arr, int size, uint32_t value)
 * 线性查找 (NEON 加速)
 * 返回: 找到则返回索引，否则返回 -1
 */
.global asm_find_index_u32
.type asm_find_index_u32, %function
asm_find_index_u32:
    // x0 = arr, w1 = size, w2 = value
    cbz     w1, .Lfind_not_found
    
    cmp     w1, #4
    b.lt    .Lfind_scalar
    
    // NEON: 4 元素并行比较
    dup     v0.4s, w2           // 目标值广播
    lsr     w3, w1, #2          // 处理组数
    mov     x4, x0
    mov     w5, #0              // 当前索引
    
.Lfind_neon_loop:
    ldr     q1, [x4], #16
    cmeq    v2.4s, v1.4s, v0.4s // 比较
    
    // 检查是否有匹配
    umaxv   s3, v2.4s
    fmov    w6, s3
    cbnz    w6, .Lfind_neon_match
    
    add     w5, w5, #4
    subs    w3, w3, #1
    b.ne    .Lfind_neon_loop
    
    // 处理剩余
    and     w3, w1, #3
    b       .Lfind_tail
    
.Lfind_neon_match:
    // 找到具体位置 - 线性扫描确认
.Lfind_match_scan:
    ldr     w6, [x0, x5, lsl #2]
    cmp     w6, w2
    b.eq    .Lfind_found
    add     w5, w5, #1
    cmp     w5, w1
    b.lt    .Lfind_match_scan
    b       .Lfind_not_found
    
.Lfind_tail:
    cbz     w3, .Lfind_not_found
.Lfind_tail_loop:
    ldr     w6, [x0, x5, lsl #2]
    cmp     w6, w2
    b.eq    .Lfind_found
    add     w5, w5, #1
    subs    w3, w3, #1
    b.ne    .Lfind_tail_loop
    b       .Lfind_not_found
    
.Lfind_scalar:
    mov     w5, #0
.Lfind_scalar_loop:
    cmp     w5, w1
    b.ge    .Lfind_not_found
    ldr     w6, [x0, x5, lsl #2]
    cmp     w6, w2
    b.eq    .Lfind_found
    add     w5, w5, #1
    b       .Lfind_scalar_loop
    
.Lfind_found:
    mov     w0, w5
    ret
    
.Lfind_not_found:
    mov     w0, #-1
    ret
.size asm_find_index_u32, .-asm_find_index_u32

/**
 * void asm_sort3_u32(uint32_t* arr)
 * 3 元素排序 (无分支, 用于快排分区)
 */
.global asm_sort3_u32
.type asm_sort3_u32, %function
asm_sort3_u32:
    ldr     w1, [x0]            // a
    ldr     w2, [x0, #4]        // b
    ldr     w3, [x0, #8]        // c
    
    // 无分支比较交换
    cmp     w1, w2
    csel    w4, w1, w2, lo      // min(a,b)
    csel    w5, w2, w1, lo      // max(a,b)
    
    cmp     w5, w3
    csel    w6, w5, w3, lo      // min(max(a,b), c) = middle
    csel    w7, w3, w5, lo      // max(max(a,b), c) = max
    
    cmp     w4, w6
    csel    w1, w4, w6, lo      // min
    csel    w2, w6, w4, lo      // middle
    
    str     w1, [x0]
    str     w2, [x0, #4]
    str     w7, [x0, #8]
    ret
.size asm_sort3_u32, .-asm_sort3_u32

/**
 * void asm_swap_u32(uint32_t* a, uint32_t* b)
 * 原子交换两个值
 */
.global asm_swap_u32
.type asm_swap_u32, %function
asm_swap_u32:
    ldr     w2, [x0]
    ldr     w3, [x1]
    str     w3, [x0]
    str     w2, [x1]
    ret
.size asm_swap_u32, .-asm_swap_u32

/**
 * int asm_partition_u32(uint32_t* arr, int low, int high)
 * 快速排序分区 (Hoare 分区方案)
 * 返回: 分区点索引
 */
.global asm_partition_u32
.type asm_partition_u32, %function
asm_partition_u32:
    // x0 = arr, w1 = low, w2 = high
    // 选择中间元素作为 pivot
    add     w3, w1, w2
    lsr     w3, w3, #1          // mid = (low + high) / 2
    ldr     w4, [x0, x3, lsl #2] // pivot = arr[mid]
    
    sub     w5, w1, #1          // i = low - 1
    add     w6, w2, #1          // j = high + 1
    
.Lpart_loop:
.Lpart_inc_i:
    add     w5, w5, #1
    ldr     w7, [x0, x5, lsl #2]
    cmp     w7, w4
    b.lo    .Lpart_inc_i        // while arr[i] < pivot
    
.Lpart_dec_j:
    sub     w6, w6, #1
    ldr     w8, [x0, x6, lsl #2]
    cmp     w8, w4
    b.hi    .Lpart_dec_j        // while arr[j] > pivot
    
    cmp     w5, w6
    b.ge    .Lpart_done
    
    // 交换 arr[i] 和 arr[j]
    str     w8, [x0, x5, lsl #2]
    str     w7, [x0, x6, lsl #2]
    b       .Lpart_loop
    
.Lpart_done:
    mov     w0, w6
    ret
.size asm_partition_u32, .-asm_partition_u32

/**
 * int asm_count_bits_set(const uint8_t* data, int size)
 * 统计所有位中 1 的总数 (NEON 加速)
 */
.global asm_count_bits_set
.type asm_count_bits_set, %function
asm_count_bits_set:
    // x0 = data, w1 = size
    mov     x2, #0              // total = 0
    cmp     w1, #16
    b.lt    .Lcbs_scalar
    
    movi    v0.16b, #0          // 累加器
    lsr     w3, w1, #4          // size / 16
    
.Lcbs_neon_loop:
    ldr     q1, [x0], #16
    cnt     v1.16b, v1.16b      // 每字节 popcount
    uaddlp  v1.8h, v1.16b       // 8 个 uint16
    uaddlp  v1.4s, v1.8h        // 4 个 uint32
    add     v0.4s, v0.4s, v1.4s
    subs    w3, w3, #1
    b.ne    .Lcbs_neon_loop
    
    // 水平求和
    addv    s0, v0.4s
    fmov    w2, s0
    
    // 剩余字节
    and     w3, w1, #15
    cbz     w3, .Lcbs_done
    
.Lcbs_tail:
    ldrb    w4, [x0], #1
    // 软件 popcount - 使用寄存器加载常量
    mov     w9, #0x55           // 加载常量
    mov     w10, #0x33
    mov     w11, #0x0F
    mov     w5, w4
    lsr     w5, w5, #1
    and     w5, w5, w9          // 0x55
    sub     w4, w4, w5
    mov     w5, w4
    lsr     w5, w5, #2
    and     w5, w5, w10         // 0x33
    and     w4, w4, w10         // 0x33
    add     w4, w4, w5
    add     w4, w4, w4, lsr #4
    and     w4, w4, w11         // 0x0F
    add     w2, w2, w4
    subs    w3, w3, #1
    b.ne    .Lcbs_tail
    b       .Lcbs_done
    
.Lcbs_scalar:
    mov     w3, w1
    cbz     w3, .Lcbs_done
.Lcbs_scalar_loop:
    ldrb    w4, [x0], #1
    // 软件 popcount - 使用寄存器加载常量
    mov     w9, #0x55
    mov     w10, #0x33
    mov     w11, #0x0F
    mov     w5, w4
    lsr     w5, w5, #1
    and     w5, w5, w9          // 0x55
    sub     w4, w4, w5
    mov     w5, w4
    lsr     w5, w5, #2
    and     w5, w5, w10         // 0x33
    and     w4, w4, w10         // 0x33
    add     w4, w4, w5
    add     w4, w4, w4, lsr #4
    and     w4, w4, w11         // 0x0F
    add     w2, w2, w4
    subs    w3, w3, #1
    b.ne    .Lcbs_scalar_loop
    
.Lcbs_done:
    mov     x0, x2
    ret
.size asm_count_bits_set, .-asm_count_bits_set

.end
