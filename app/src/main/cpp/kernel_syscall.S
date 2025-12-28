/**
 * ARM64 Kernel Direct Syscall Implementation
 * 
 * 内核直连系统调用的手写汇编实现
 * 绕过 glibc/bionic，直接通过 svc #0 调用 Linux 内核
 * 
 * 效率提升：
 * - 避免 libc 包装函数开销
 * - 无需函数调用栈帧
 * - 直接寄存器传参
 * 
 * 系统调用约定 (ARM64/AArch64):
 * - x8: 系统调用号
 * - x0-x5: 参数 (最多6个)
 * - x0: 返回值
 * - svc #0: 触发系统调用
 * 
 * @author DeepSeek AI Assistant
 */

// ============================================================================
// ARM64 Linux 系统调用号 (来自 <asm/unistd.h>)
// ============================================================================
#define SYS_close           57
#define SYS_openat          56
#define SYS_read            63
#define SYS_write           64
#define SYS_getpid          172
#define SYS_getppid         173
#define SYS_gettid          178
#define SYS_getpriority     141
#define SYS_setpriority     140
#define SYS_mlockall        230
#define SYS_munlockall      231
#define SYS_mlock           228
#define SYS_munlock         229
#define SYS_sched_setscheduler  119
#define SYS_sched_getscheduler  120
#define SYS_sched_setaffinity   122
#define SYS_sched_getaffinity   123
#define SYS_ioprio_set      30
#define SYS_ioprio_get      31

// 优先级类型
#define PRIO_PROCESS    0
#define PRIO_PGRP       1
#define PRIO_USER       2

// mlockall 标志
#define MCL_CURRENT     1
#define MCL_FUTURE      2
#define MCL_ONFAULT     4

// ioprio 优先级类
#define IOPRIO_CLASS_RT     1
#define IOPRIO_CLASS_BE     2
#define IOPRIO_CLASS_IDLE   3

// 文件打开标志
#define O_RDONLY        0
#define O_WRONLY        1
#define O_RDWR          2
#define O_CREAT         0x40
#define O_TRUNC         0x200

// AT_FDCWD
#define AT_FDCWD        -100

.text
.align 4

// ============================================================================
// 通用系统调用包装器
// ============================================================================

/**
 * 通用6参数系统调用
 * int64_t kernel_syscall6(int64_t nr, int64_t a0, int64_t a1, 
 *                         int64_t a2, int64_t a3, int64_t a4, int64_t a5)
 */
.global kernel_syscall6
.type kernel_syscall6, %function
kernel_syscall6:
    // 保存调用者保存寄存器
    stp     x29, x30, [sp, #-16]!
    mov     x29, sp
    
    // 重新排列参数
    mov     x8, x0          // 系统调用号
    mov     x0, x1          // 参数1
    mov     x1, x2          // 参数2
    mov     x2, x3          // 参数3
    mov     x3, x4          // 参数4
    mov     x4, x5          // 参数5
    mov     x5, x6          // 参数6
    
    // 执行系统调用
    svc     #0
    
    // 恢复栈帧并返回
    ldp     x29, x30, [sp], #16
    ret
.size kernel_syscall6, .-kernel_syscall6

/**
 * 通用3参数系统调用 (最常用)
 * int64_t kernel_syscall3(int64_t nr, int64_t a0, int64_t a1, int64_t a2)
 */
.global kernel_syscall3
.type kernel_syscall3, %function
kernel_syscall3:
    mov     x8, x0          // 系统调用号
    mov     x0, x1          // 参数1
    mov     x1, x2          // 参数2
    mov     x2, x3          // 参数3
    svc     #0
    ret
.size kernel_syscall3, .-kernel_syscall3

/**
 * 无参数系统调用
 * int64_t kernel_syscall0(int64_t nr)
 */
.global kernel_syscall0
.type kernel_syscall0, %function
kernel_syscall0:
    mov     x8, x0          // 系统调用号
    svc     #0
    ret
.size kernel_syscall0, .-kernel_syscall0

// ============================================================================
// 进程/线程 ID 获取 (极速版)
// ============================================================================

/**
 * 获取当前进程ID (PID)
 * 直接系统调用，无 glibc 开销
 */
.global kernel_getpid
.type kernel_getpid, %function
kernel_getpid:
    mov     x8, #SYS_getpid
    svc     #0
    ret
.size kernel_getpid, .-kernel_getpid

/**
 * 获取当前线程ID (TID)
 */
.global kernel_gettid
.type kernel_gettid, %function
kernel_gettid:
    mov     x8, #SYS_gettid
    svc     #0
    ret
.size kernel_gettid, .-kernel_gettid

/**
 * 获取父进程ID (PPID)
 */
.global kernel_getppid
.type kernel_getppid, %function
kernel_getppid:
    mov     x8, #SYS_getppid
    svc     #0
    ret
.size kernel_getppid, .-kernel_getppid

// ============================================================================
// 进程优先级调整 (Nice 值)
// ============================================================================

/**
 * 获取进程优先级 (nice 值)
 * int kernel_getpriority(int which, int who)
 * which: PRIO_PROCESS(0), PRIO_PGRP(1), PRIO_USER(2)
 * who: 进程ID (0表示当前进程)
 * 返回: nice值 (成功) 或 负数错误码
 */
.global kernel_getpriority
.type kernel_getpriority, %function
kernel_getpriority:
    mov     x8, #SYS_getpriority
    // x0 = which, x1 = who (已经在正确位置)
    svc     #0
    // 需要处理返回值：内核返回 20 - nice，需要转换
    // 如果错误返回负值，直接返回
    cmp     x0, #0
    b.lt    1f
    // 成功：返回 20 - x0 得到实际 nice 值
    mov     x1, #20
    sub     x0, x1, x0
1:
    ret
.size kernel_getpriority, .-kernel_getpriority

/**
 * 设置进程优先级 (nice 值)
 * int kernel_setpriority(int which, int who, int prio)
 * prio: -20 (最高优先级) 到 19 (最低优先级)
 * 返回: 0 (成功) 或 负数错误码
 */
.global kernel_setpriority
.type kernel_setpriority, %function
kernel_setpriority:
    mov     x8, #SYS_setpriority
    // x0 = which, x1 = who, x2 = prio (已经在正确位置)
    svc     #0
    ret
.size kernel_setpriority, .-kernel_setpriority

/**
 * 快速设置当前进程 nice 值
 * int kernel_set_nice(int nice_value)
 */
.global kernel_set_nice
.type kernel_set_nice, %function
kernel_set_nice:
    mov     x2, x0          // prio = nice_value
    mov     x0, #PRIO_PROCESS
    mov     x1, #0          // 当前进程
    mov     x8, #SYS_setpriority
    svc     #0
    ret
.size kernel_set_nice, .-kernel_set_nice

// ============================================================================
// 内存锁定 (防止换出到 swap)
// ============================================================================

/**
 * 锁定所有当前和未来的内存页
 * int kernel_mlockall(int flags)
 * flags: MCL_CURRENT(1) | MCL_FUTURE(2) | MCL_ONFAULT(4)
 * 返回: 0 (成功) 或 负数错误码
 */
.global kernel_mlockall
.type kernel_mlockall, %function
kernel_mlockall:
    mov     x8, #SYS_mlockall
    // x0 = flags (已经在正确位置)
    svc     #0
    ret
.size kernel_mlockall, .-kernel_mlockall

/**
 * 解锁所有内存页
 * int kernel_munlockall(void)
 */
.global kernel_munlockall
.type kernel_munlockall, %function
kernel_munlockall:
    mov     x8, #SYS_munlockall
    svc     #0
    ret
.size kernel_munlockall, .-kernel_munlockall

/**
 * 锁定指定内存区域
 * int kernel_mlock(void *addr, size_t len)
 */
.global kernel_mlock
.type kernel_mlock, %function
kernel_mlock:
    mov     x8, #SYS_mlock
    // x0 = addr, x1 = len (已经在正确位置)
    svc     #0
    ret
.size kernel_mlock, .-kernel_mlock

/**
 * 解锁指定内存区域
 * int kernel_munlock(void *addr, size_t len)
 */
.global kernel_munlock
.type kernel_munlock, %function
kernel_munlock:
    mov     x8, #SYS_munlock
    // x0 = addr, x1 = len (已经在正确位置)
    svc     #0
    ret
.size kernel_munlock, .-kernel_munlock

// ============================================================================
// 文件操作 (读取 /proc, /sys 文件系统)
// ============================================================================

/**
 * 打开文件 (相对路径)
 * int kernel_open(const char *path, int flags, int mode)
 * 使用 openat(AT_FDCWD, path, flags, mode)
 */
.global kernel_open
.type kernel_open, %function
kernel_open:
    mov     x3, x2          // mode
    mov     x2, x1          // flags
    mov     x1, x0          // path
    mov     x0, #AT_FDCWD   // dirfd = AT_FDCWD (-100)
    movn    x0, #99         // AT_FDCWD = -100
    mov     x8, #SYS_openat
    svc     #0
    ret
.size kernel_open, .-kernel_open

/**
 * 读取文件
 * ssize_t kernel_read(int fd, void *buf, size_t count)
 */
.global kernel_read
.type kernel_read, %function
kernel_read:
    mov     x8, #SYS_read
    // x0 = fd, x1 = buf, x2 = count (已经在正确位置)
    svc     #0
    ret
.size kernel_read, .-kernel_read

/**
 * 写入文件
 * ssize_t kernel_write(int fd, const void *buf, size_t count)
 */
.global kernel_write
.type kernel_write, %function
kernel_write:
    mov     x8, #SYS_write
    // x0 = fd, x1 = buf, x2 = count (已经在正确位置)
    svc     #0
    ret
.size kernel_write, .-kernel_write

/**
 * 关闭文件
 * int kernel_close(int fd)
 */
.global kernel_close
.type kernel_close, %function
kernel_close:
    mov     x8, #SYS_close
    // x0 = fd (已经在正确位置)
    svc     #0
    ret
.size kernel_close, .-kernel_close

// ============================================================================
// 调度器控制
// ============================================================================

/**
 * 设置调度策略
 * int kernel_sched_setscheduler(pid_t pid, int policy, 
 *                               const struct sched_param *param)
 */
.global kernel_sched_setscheduler
.type kernel_sched_setscheduler, %function
kernel_sched_setscheduler:
    mov     x8, #SYS_sched_setscheduler
    // x0 = pid, x1 = policy, x2 = param (已经在正确位置)
    svc     #0
    ret
.size kernel_sched_setscheduler, .-kernel_sched_setscheduler

/**
 * 获取调度策略
 * int kernel_sched_getscheduler(pid_t pid)
 */
.global kernel_sched_getscheduler
.type kernel_sched_getscheduler, %function
kernel_sched_getscheduler:
    mov     x8, #SYS_sched_getscheduler
    // x0 = pid (已经在正确位置)
    svc     #0
    ret
.size kernel_sched_getscheduler, .-kernel_sched_getscheduler

/**
 * 设置 CPU 亲和性
 * int kernel_sched_setaffinity(pid_t pid, size_t cpusetsize, 
 *                              const cpu_set_t *mask)
 */
.global kernel_sched_setaffinity
.type kernel_sched_setaffinity, %function
kernel_sched_setaffinity:
    mov     x8, #SYS_sched_setaffinity
    // x0 = pid, x1 = cpusetsize, x2 = mask (已经在正确位置)
    svc     #0
    ret
.size kernel_sched_setaffinity, .-kernel_sched_setaffinity

// ============================================================================
// I/O 优先级
// ============================================================================

/**
 * 设置 I/O 优先级
 * int kernel_ioprio_set(int which, int who, int ioprio)
 */
.global kernel_ioprio_set
.type kernel_ioprio_set, %function
kernel_ioprio_set:
    mov     x8, #SYS_ioprio_set
    // x0 = which, x1 = who, x2 = ioprio (已经在正确位置)
    svc     #0
    ret
.size kernel_ioprio_set, .-kernel_ioprio_set

/**
 * 获取 I/O 优先级
 * int kernel_ioprio_get(int which, int who)
 */
.global kernel_ioprio_get
.type kernel_ioprio_get, %function
kernel_ioprio_get:
    mov     x8, #SYS_ioprio_get
    // x0 = which, x1 = who (已经在正确位置)
    svc     #0
    ret
.size kernel_ioprio_get, .-kernel_ioprio_get

// ============================================================================
// 快速文件内容读取 (用于 /sys, /proc 温度等)
// ============================================================================

/**
 * 快速读取小文件全部内容
 * int kernel_read_file(const char *path, char *buf, int max_len)
 * 返回: 读取的字节数，或负数错误码
 */
.global kernel_read_file
.type kernel_read_file, %function
kernel_read_file:
    // 保存寄存器
    stp     x29, x30, [sp, #-32]!
    mov     x29, sp
    stp     x19, x20, [sp, #16]
    
    // 保存参数
    mov     x19, x1         // buf
    mov     x20, x2         // max_len
    
    // 打开文件
    mov     x1, #O_RDONLY
    mov     x2, #0
    bl      kernel_open
    
    // 检查打开是否成功
    cmp     x0, #0
    b.lt    .Lread_file_error
    
    // 保存 fd
    mov     x3, x0          // fd
    
    // 读取内容
    mov     x0, x3          // fd
    mov     x1, x19         // buf
    mov     x2, x20         // max_len
    mov     x8, #SYS_read
    svc     #0
    
    // 保存读取结果
    mov     x4, x0          // bytes_read
    
    // 关闭文件
    mov     x0, x3          // fd
    mov     x8, #SYS_close
    svc     #0
    
    // 返回读取的字节数
    mov     x0, x4
    b       .Lread_file_done
    
.Lread_file_error:
    // 返回错误码
    
.Lread_file_done:
    // 恢复寄存器
    ldp     x19, x20, [sp, #16]
    ldp     x29, x30, [sp], #32
    ret
.size kernel_read_file, .-kernel_read_file

/**
 * 快速写入小文件
 * int kernel_write_file(const char *path, const char *buf, int len)
 * 返回: 写入的字节数，或负数错误码
 */
.global kernel_write_file
.type kernel_write_file, %function
kernel_write_file:
    // 保存寄存器
    stp     x29, x30, [sp, #-32]!
    mov     x29, sp
    stp     x19, x20, [sp, #16]
    
    // 保存参数
    mov     x19, x1         // buf
    mov     x20, x2         // len
    
    // 打开文件 (写入模式)
    mov     x1, #O_WRONLY
    orr     x1, x1, #O_TRUNC
    mov     x2, #0644
    bl      kernel_open
    
    // 检查打开是否成功
    cmp     x0, #0
    b.lt    .Lwrite_file_error
    
    // 保存 fd
    mov     x3, x0          // fd
    
    // 写入内容
    mov     x0, x3          // fd
    mov     x1, x19         // buf
    mov     x2, x20         // len
    mov     x8, #SYS_write
    svc     #0
    
    // 保存写入结果
    mov     x4, x0          // bytes_written
    
    // 关闭文件
    mov     x0, x3          // fd
    mov     x8, #SYS_close
    svc     #0
    
    // 返回写入的字节数
    mov     x0, x4
    b       .Lwrite_file_done
    
.Lwrite_file_error:
    // 返回错误码
    
.Lwrite_file_done:
    // 恢复寄存器
    ldp     x19, x20, [sp, #16]
    ldp     x29, x30, [sp], #32
    ret
.size kernel_write_file, .-kernel_write_file

// ============================================================================
// 高精度时间获取 (用于性能测量)
// ============================================================================

/**
 * 读取 CPU 周期计数器
 */
.global kernel_read_cycles
.type kernel_read_cycles, %function
kernel_read_cycles:
    isb
    mrs     x0, cntvct_el0
    ret
.size kernel_read_cycles, .-kernel_read_cycles

/**
 * 读取计数器频率
 */
.global kernel_get_freq
.type kernel_get_freq, %function
kernel_get_freq:
    mrs     x0, cntfrq_el0
    ret
.size kernel_get_freq, .-kernel_get_freq
