/**
 * Kernel Optimize Native Implementation
 * 
 * 内核级优化的 C 层实现和 JNI 接口
 * 通过汇编直连内核系统调用，实现极速优化
 * 
 * 功能：
 * 1. 进程优先级调整 (nice 值 + oom_adj)
 * 2. 内存锁定 (mlockall)
 * 3. 温度监控 (读取 sysfs)
 * 4. CPU 亲和性设置
 * 5. I/O 优先级设置
 * 
 * @author DeepSeek AI Assistant
 */

#define _GNU_SOURCE
#include <stdint.h>
#include <jni.h>
#include <stdio.h>
#include <stdlib.h>
#include <string.h>
#include <unistd.h>
#include <fcntl.h>
#include <errno.h>
#include <android/log.h>

#define LOG_TAG "KernelOptimize"
#define LOGI(...) __android_log_print(ANDROID_LOG_INFO, LOG_TAG, __VA_ARGS__)
#define LOGW(...) __android_log_print(ANDROID_LOG_WARN, LOG_TAG, __VA_ARGS__)
#define LOGE(...) __android_log_print(ANDROID_LOG_ERROR, LOG_TAG, __VA_ARGS__)

// ============================================================================
// 汇编函数声明 (kernel_syscall.S)
// ============================================================================

extern int64_t kernel_syscall6(int64_t nr, int64_t a0, int64_t a1, 
                               int64_t a2, int64_t a3, int64_t a4, int64_t a5);
extern int64_t kernel_syscall3(int64_t nr, int64_t a0, int64_t a1, int64_t a2);
extern int64_t kernel_syscall0(int64_t nr);

extern int32_t kernel_getpid(void);
extern int32_t kernel_gettid(void);
extern int32_t kernel_getppid(void);

extern int32_t kernel_getpriority(int which, int who);
extern int32_t kernel_setpriority(int which, int who, int prio);
extern int32_t kernel_set_nice(int nice_value);

extern int32_t kernel_mlockall(int flags);
extern int32_t kernel_munlockall(void);
extern int32_t kernel_mlock(void* addr, size_t len);
extern int32_t kernel_munlock(void* addr, size_t len);

extern int32_t kernel_open(const char* path, int flags, int mode);
extern int64_t kernel_read(int fd, void* buf, size_t count);
extern int64_t kernel_write(int fd, const void* buf, size_t count);
extern int32_t kernel_close(int fd);

extern int32_t kernel_sched_setscheduler(int pid, int policy, const void* param);
extern int32_t kernel_sched_getscheduler(int pid);
extern int32_t kernel_sched_setaffinity(int pid, size_t cpusetsize, const void* mask);

extern int32_t kernel_ioprio_set(int which, int who, int ioprio);
extern int32_t kernel_ioprio_get(int which, int who);

extern int64_t kernel_read_file(const char* path, char* buf, int max_len);
extern int64_t kernel_write_file(const char* path, const char* buf, int len);

extern uint64_t kernel_read_cycles(void);
extern uint64_t kernel_get_freq(void);

// ============================================================================
// 常量定义
// ============================================================================

#define PRIO_PROCESS    0
#define PRIO_PGRP       1
#define PRIO_USER       2

#define MCL_CURRENT     1
#define MCL_FUTURE      2
#define MCL_ONFAULT     4

#define IOPRIO_WHO_PROCESS  1
#define IOPRIO_WHO_PGRP     2
#define IOPRIO_WHO_USER     3

#define IOPRIO_CLASS_SHIFT  13
#define IOPRIO_PRIO_VALUE(class, data)  (((class) << IOPRIO_CLASS_SHIFT) | (data))

#define SCHED_NORMAL    0
#define SCHED_FIFO      1
#define SCHED_RR        2
#define SCHED_BATCH     3
#define SCHED_IDLE      5

// ============================================================================
// 温度节点路径 (适配多种设备)
// ============================================================================

static const char* CPU_TEMP_PATHS[] = {
    "/sys/class/thermal/thermal_zone0/temp",
    "/sys/devices/virtual/thermal/thermal_zone0/temp",
    "/sys/class/hwmon/hwmon0/temp1_input",
    "/sys/devices/platform/coretemp.0/hwmon/hwmon0/temp1_input",
    NULL
};

static const char* BATTERY_TEMP_PATHS[] = {
    "/sys/class/power_supply/battery/temp",
    "/sys/class/power_supply/Battery/temp",
    "/sys/class/thermal/thermal_zone1/temp",
    NULL
};

static const char* GPU_TEMP_PATHS[] = {
    "/sys/class/thermal/thermal_zone3/temp",
    "/sys/class/kgsl/kgsl-3d0/temp",
    "/sys/devices/virtual/thermal/thermal_zone3/temp",
    NULL
};

// ============================================================================
// 辅助函数
// ============================================================================

/**
 * 尝试多个路径读取温度
 */
static int read_temperature_from_paths(const char** paths) {
    char buf[32];
    
    for (int i = 0; paths[i] != NULL; i++) {
        int64_t bytes = kernel_read_file(paths[i], buf, sizeof(buf) - 1);
        if (bytes > 0) {
            buf[bytes] = '\0';
            int temp = atoi(buf);
            // 部分设备返回毫摄氏度
            if (temp > 1000) {
                temp /= 1000;
            }
            return temp;
        }
    }
    
    return -1;
}

/**
 * 通过 /proc 文件系统设置 oom_adj
 */
static int set_oom_adj(int pid, int adj) {
    char path[64];
    char value[16];
    int len;
    
    // 尝试 oom_score_adj (Android 6+)
    snprintf(path, sizeof(path), "/proc/%d/oom_score_adj", pid);
    // 将 oom_adj (-17~15) 转换为 oom_score_adj (-1000~1000)
    int score_adj = adj * 1000 / 17;
    if (score_adj > 1000) score_adj = 1000;
    if (score_adj < -1000) score_adj = -1000;
    
    len = snprintf(value, sizeof(value), "%d", score_adj);
    
    int64_t result = kernel_write_file(path, value, len);
    if (result > 0) {
        LOGI("Set oom_score_adj to %d for pid %d", score_adj, pid);
        return 0;
    }
    
    // 回退到旧的 oom_adj (Android 5 及更早)
    snprintf(path, sizeof(path), "/proc/%d/oom_adj", pid);
    len = snprintf(value, sizeof(value), "%d", adj);
    
    result = kernel_write_file(path, value, len);
    if (result > 0) {
        LOGI("Set oom_adj to %d for pid %d", adj, pid);
        return 0;
    }
    
    LOGW("Failed to set oom_adj for pid %d", pid);
    return -1;
}

/**
 * 读取当前 oom_adj 值
 */
static int get_oom_adj(int pid) {
    char path[64];
    char buf[32];
    
    // 尝试 oom_score_adj
    snprintf(path, sizeof(path), "/proc/%d/oom_score_adj", pid);
    int64_t bytes = kernel_read_file(path, buf, sizeof(buf) - 1);
    if (bytes > 0) {
        buf[bytes] = '\0';
        int score_adj = atoi(buf);
        // 转换回 oom_adj 范围
        return score_adj * 17 / 1000;
    }
    
    // 回退到旧接口
    snprintf(path, sizeof(path), "/proc/%d/oom_adj", pid);
    bytes = kernel_read_file(path, buf, sizeof(buf) - 1);
    if (bytes > 0) {
        buf[bytes] = '\0';
        return atoi(buf);
    }
    
    return -999;
}

// ============================================================================
// JNI 接口实现
// ============================================================================

/**
 * 初始化内核优化模块
 */
JNIEXPORT jint JNICALL
Java_com_example_deepseekaiassistant_kernel_KernelOptimize_nativeInit(
        JNIEnv* env, jobject thiz) {
    LOGI("Kernel Optimize Module initialized");
    LOGI("  PID: %d, TID: %d", kernel_getpid(), kernel_gettid());
    return 0;
}

/**
 * 获取当前进程 PID
 */
JNIEXPORT jint JNICALL
Java_com_example_deepseekaiassistant_kernel_KernelOptimize_nativeGetPid(
        JNIEnv* env, jobject thiz) {
    return kernel_getpid();
}

/**
 * 获取当前线程 TID
 */
JNIEXPORT jint JNICALL
Java_com_example_deepseekaiassistant_kernel_KernelOptimize_nativeGetTid(
        JNIEnv* env, jobject thiz) {
    return kernel_gettid();
}

// ============================================================================
// 进程优先级调整 JNI
// ============================================================================

/**
 * 设置进程 nice 值
 * @param pid 进程ID (0 表示当前进程)
 * @param nice nice 值 (-20 到 19)
 * @return 0 成功，负数失败
 */
JNIEXPORT jint JNICALL
Java_com_example_deepseekaiassistant_kernel_KernelOptimize_nativeSetNice(
        JNIEnv* env, jobject thiz, jint pid, jint nice) {
    if (pid == 0) {
        int result = kernel_set_nice(nice);
        LOGI("Set nice to %d for current process: result=%d", nice, result);
        return result;
    }
    
    int result = kernel_setpriority(PRIO_PROCESS, pid, nice);
    LOGI("Set nice to %d for pid %d: result=%d", nice, pid, result);
    return result;
}

/**
 * 获取进程 nice 值
 */
JNIEXPORT jint JNICALL
Java_com_example_deepseekaiassistant_kernel_KernelOptimize_nativeGetNice(
        JNIEnv* env, jobject thiz, jint pid) {
    if (pid == 0) pid = kernel_getpid();
    return kernel_getpriority(PRIO_PROCESS, pid);
}

/**
 * 设置 oom_adj 值 (影响 OOM Killer 优先级)
 * @param pid 进程ID (0 表示当前进程)
 * @param adj oom_adj 值 (-17 到 15，负值更不容易被杀)
 */
JNIEXPORT jint JNICALL
Java_com_example_deepseekaiassistant_kernel_KernelOptimize_nativeSetOomAdj(
        JNIEnv* env, jobject thiz, jint pid, jint adj) {
    if (pid == 0) pid = kernel_getpid();
    return set_oom_adj(pid, adj);
}

/**
 * 获取 oom_adj 值
 */
JNIEXPORT jint JNICALL
Java_com_example_deepseekaiassistant_kernel_KernelOptimize_nativeGetOomAdj(
        JNIEnv* env, jobject thiz, jint pid) {
    if (pid == 0) pid = kernel_getpid();
    return get_oom_adj(pid);
}

/**
 * 一键最高优先级 (nice=-10, oom_adj=-10)
 * 注意：需要 Root 权限
 */
JNIEXPORT jint JNICALL
Java_com_example_deepseekaiassistant_kernel_KernelOptimize_nativeBoostPriority(
        JNIEnv* env, jobject thiz) {
    int pid = kernel_getpid();
    int r1 = kernel_set_nice(-10);
    int r2 = set_oom_adj(pid, -10);
    
    LOGI("Boost priority: nice=%d, oom_adj=%d", r1, r2);
    return (r1 < 0 || r2 < 0) ? -1 : 0;
}

/**
 * 恢复默认优先级
 */
JNIEXPORT jint JNICALL
Java_com_example_deepseekaiassistant_kernel_KernelOptimize_nativeRestorePriority(
        JNIEnv* env, jobject thiz) {
    int pid = kernel_getpid();
    int r1 = kernel_set_nice(0);
    int r2 = set_oom_adj(pid, 0);
    
    LOGI("Restore priority: nice=%d, oom_adj=%d", r1, r2);
    return (r1 < 0 || r2 < 0) ? -1 : 0;
}

// ============================================================================
// 内存锁定 JNI
// ============================================================================

/**
 * 锁定所有当前内存 (防止换出到 swap)
 * @param lockFuture 是否同时锁定未来分配的内存
 */
JNIEXPORT jint JNICALL
Java_com_example_deepseekaiassistant_kernel_KernelOptimize_nativeLockMemory(
        JNIEnv* env, jobject thiz, jboolean lockFuture) {
    int flags = MCL_CURRENT;
    if (lockFuture) {
        flags |= MCL_FUTURE;
    }
    
    int result = kernel_mlockall(flags);
    LOGI("mlockall(flags=%d): result=%d", flags, result);
    return result;
}

/**
 * 解锁所有内存
 */
JNIEXPORT jint JNICALL
Java_com_example_deepseekaiassistant_kernel_KernelOptimize_nativeUnlockMemory(
        JNIEnv* env, jobject thiz) {
    int result = kernel_munlockall();
    LOGI("munlockall: result=%d", result);
    return result;
}

/**
 * 锁定指定大小的内存 (分配并锁定)
 * 返回: 锁定的内存大小 (MB)，失败返回 -1
 */
JNIEXPORT jint JNICALL
Java_com_example_deepseekaiassistant_kernel_KernelOptimize_nativeLockMemorySize(
        JNIEnv* env, jobject thiz, jint sizeMB) {
    size_t size = (size_t)sizeMB * 1024 * 1024;
    
    // 分配内存
    void* ptr = malloc(size);
    if (ptr == NULL) {
        LOGE("Failed to allocate %d MB memory", sizeMB);
        return -1;
    }
    
    // 触摸所有页面确保分配
    memset(ptr, 0, size);
    
    // 锁定内存
    int result = kernel_mlock(ptr, size);
    if (result < 0) {
        LOGE("Failed to mlock %d MB: %d", sizeMB, result);
        free(ptr);
        return -1;
    }
    
    LOGI("Locked %d MB memory at %p", sizeMB, ptr);
    // 注意：这里不释放内存，保持锁定状态
    // 实际使用中应该保存指针以便后续解锁
    return sizeMB;
}

// ============================================================================
// 温度监控 JNI
// ============================================================================

/**
 * 获取 CPU 温度 (摄氏度)
 */
JNIEXPORT jint JNICALL
Java_com_example_deepseekaiassistant_kernel_KernelOptimize_nativeGetCpuTemp(
        JNIEnv* env, jobject thiz) {
    return read_temperature_from_paths(CPU_TEMP_PATHS);
}

/**
 * 获取电池温度 (摄氏度)
 */
JNIEXPORT jint JNICALL
Java_com_example_deepseekaiassistant_kernel_KernelOptimize_nativeGetBatteryTemp(
        JNIEnv* env, jobject thiz) {
    int temp = read_temperature_from_paths(BATTERY_TEMP_PATHS);
    // 电池温度通常以 0.1°C 为单位
    if (temp > 100) {
        temp /= 10;
    }
    return temp;
}

/**
 * 获取 GPU 温度 (摄氏度)
 */
JNIEXPORT jint JNICALL
Java_com_example_deepseekaiassistant_kernel_KernelOptimize_nativeGetGpuTemp(
        JNIEnv* env, jobject thiz) {
    return read_temperature_from_paths(GPU_TEMP_PATHS);
}

/**
 * 获取所有温度信息 (返回 int[3]: CPU, Battery, GPU)
 */
JNIEXPORT jintArray JNICALL
Java_com_example_deepseekaiassistant_kernel_KernelOptimize_nativeGetAllTemps(
        JNIEnv* env, jobject thiz) {
    jint temps[3];
    temps[0] = read_temperature_from_paths(CPU_TEMP_PATHS);
    temps[1] = read_temperature_from_paths(BATTERY_TEMP_PATHS);
    if (temps[1] > 100) temps[1] /= 10;
    temps[2] = read_temperature_from_paths(GPU_TEMP_PATHS);
    
    jintArray result = (*env)->NewIntArray(env, 3);
    (*env)->SetIntArrayRegion(env, result, 0, 3, temps);
    return result;
}

// ============================================================================
// CPU 亲和性 JNI
// ============================================================================

/**
 * 绑定当前线程到指定 CPU 核心
 * @param cpuMask CPU 掩码 (位图，bit0=CPU0, bit1=CPU1...)
 */
JNIEXPORT jint JNICALL
Java_com_example_deepseekaiassistant_kernel_KernelOptimize_nativeSetCpuAffinity(
        JNIEnv* env, jobject thiz, jlong cpuMask) {
    int tid = kernel_gettid();
    int result = kernel_sched_setaffinity(tid, sizeof(cpuMask), &cpuMask);
    LOGI("Set CPU affinity to 0x%llx for tid %d: result=%d", 
         (unsigned long long)cpuMask, tid, result);
    return result;
}

/**
 * 绑定到大核 (假设后半部分核心是大核)
 */
JNIEXPORT jint JNICALL
Java_com_example_deepseekaiassistant_kernel_KernelOptimize_nativeBindToBigCores(
        JNIEnv* env, jobject thiz) {
    // 获取 CPU 数量
    int num_cpus = sysconf(_SC_NPROCESSORS_CONF);
    if (num_cpus <= 0) num_cpus = 8;
    
    // 假设后半部分是大核
    uint64_t mask = 0;
    for (int i = num_cpus / 2; i < num_cpus; i++) {
        mask |= (1ULL << i);
    }
    
    int tid = kernel_gettid();
    int result = kernel_sched_setaffinity(tid, sizeof(mask), &mask);
    LOGI("Bind to big cores (mask=0x%llx): result=%d", 
         (unsigned long long)mask, result);
    return result;
}

// ============================================================================
// I/O 优先级 JNI
// ============================================================================

/**
 * 设置 I/O 优先级
 * @param classType 类型: 1=RT(实时), 2=BE(普通), 3=IDLE(空闲)
 * @param priority 优先级: 0-7
 */
JNIEXPORT jint JNICALL
Java_com_example_deepseekaiassistant_kernel_KernelOptimize_nativeSetIoPriority(
        JNIEnv* env, jobject thiz, jint classType, jint priority) {
    int ioprio = IOPRIO_PRIO_VALUE(classType, priority);
    int pid = kernel_getpid();
    int result = kernel_ioprio_set(IOPRIO_WHO_PROCESS, pid, ioprio);
    LOGI("Set I/O priority (class=%d, prio=%d) for pid %d: result=%d",
         classType, priority, pid, result);
    return result;
}

// ============================================================================
// 调度策略 JNI
// ============================================================================

/**
 * 设置调度策略
 * @param policy 策略: 0=NORMAL, 1=FIFO, 2=RR, 3=BATCH, 5=IDLE
 * @param priority 优先级 (FIFO/RR 需要 1-99)
 */
JNIEXPORT jint JNICALL
Java_com_example_deepseekaiassistant_kernel_KernelOptimize_nativeSetScheduler(
        JNIEnv* env, jobject thiz, jint policy, jint priority) {
    struct {
        int sched_priority;
    } param;
    param.sched_priority = priority;
    
    int pid = kernel_getpid();
    int result = kernel_sched_setscheduler(pid, policy, &param);
    LOGI("Set scheduler (policy=%d, prio=%d) for pid %d: result=%d",
         policy, priority, pid, result);
    return result;
}

/**
 * 获取当前调度策略
 */
JNIEXPORT jint JNICALL
Java_com_example_deepseekaiassistant_kernel_KernelOptimize_nativeGetScheduler(
        JNIEnv* env, jobject thiz) {
    return kernel_sched_getscheduler(kernel_getpid());
}

// ============================================================================
// 性能测量 JNI
// ============================================================================

/**
 * 读取 CPU 周期数
 */
JNIEXPORT jlong JNICALL
Java_com_example_deepseekaiassistant_kernel_KernelOptimize_nativeReadCycles(
        JNIEnv* env, jobject thiz) {
    return (jlong)kernel_read_cycles();
}

/**
 * 获取 CPU 频率 (Hz)
 */
JNIEXPORT jlong JNICALL
Java_com_example_deepseekaiassistant_kernel_KernelOptimize_nativeGetCpuFreq(
        JNIEnv* env, jobject thiz) {
    return (jlong)kernel_get_freq();
}

// ============================================================================
// 综合优化 JNI
// ============================================================================

/**
 * 一键极致优化
 * 同时启用：最高优先级 + 内存锁定 + 绑定大核 + 高 I/O 优先级
 */
JNIEXPORT jint JNICALL
Java_com_example_deepseekaiassistant_kernel_KernelOptimize_nativeEnableExtreme(
        JNIEnv* env, jobject thiz) {
    int errors = 0;
    int pid = kernel_getpid();
    int tid = kernel_gettid();
    
    LOGI("=== Enabling Extreme Optimization ===");
    
    // 1. 最高 nice 优先级
    if (kernel_set_nice(-10) < 0) {
        LOGW("Failed to set nice");
        errors++;
    }
    
    // 2. 最高 oom_adj 保护
    if (set_oom_adj(pid, -10) < 0) {
        LOGW("Failed to set oom_adj");
        errors++;
    }
    
    // 3. 锁定当前内存
    if (kernel_mlockall(MCL_CURRENT) < 0) {
        LOGW("Failed to lock memory");
        errors++;
    }
    
    // 4. 绑定到大核
    int num_cpus = sysconf(_SC_NPROCESSORS_CONF);
    if (num_cpus > 0) {
        uint64_t mask = 0;
        for (int i = num_cpus / 2; i < num_cpus; i++) {
            mask |= (1ULL << i);
        }
        if (kernel_sched_setaffinity(tid, sizeof(mask), &mask) < 0) {
            LOGW("Failed to set CPU affinity");
            // 不算错误，可能没有权限
        }
    }
    
    // 5. 高 I/O 优先级 (Best-Effort, 最高)
    kernel_ioprio_set(IOPRIO_WHO_PROCESS, pid, IOPRIO_PRIO_VALUE(2, 0));
    
    LOGI("=== Extreme Optimization %s (errors: %d) ===", 
         errors == 0 ? "Enabled" : "Partially Enabled", errors);
    
    return errors == 0 ? 0 : -errors;
}

/**
 * 禁用极致优化，恢复默认
 */
JNIEXPORT jint JNICALL
Java_com_example_deepseekaiassistant_kernel_KernelOptimize_nativeDisableExtreme(
        JNIEnv* env, jobject thiz) {
    int pid = kernel_getpid();
    
    LOGI("=== Disabling Extreme Optimization ===");
    
    // 1. 恢复 nice
    kernel_set_nice(0);
    
    // 2. 恢复 oom_adj
    set_oom_adj(pid, 0);
    
    // 3. 解锁内存
    kernel_munlockall();
    
    // 4. 恢复默认 I/O 优先级
    kernel_ioprio_set(IOPRIO_WHO_PROCESS, pid, IOPRIO_PRIO_VALUE(2, 4));
    
    LOGI("=== Extreme Optimization Disabled ===");
    return 0;
}

/**
 * 获取优化状态
 * 返回: 位图 (bit0=高优先级, bit1=内存锁定, bit2=绑定大核)
 */
JNIEXPORT jint JNICALL
Java_com_example_deepseekaiassistant_kernel_KernelOptimize_nativeGetOptimizeStatus(
        JNIEnv* env, jobject thiz) {
    int status = 0;
    int pid = kernel_getpid();
    
    // 检查优先级
    int nice = kernel_getpriority(PRIO_PROCESS, pid);
    if (nice < 0) {
        status |= 0x01;  // 高优先级已启用
    }
    
    // 检查 oom_adj
    int oom = get_oom_adj(pid);
    if (oom < 0) {
        status |= 0x02;  // OOM 保护已启用
    }
    
    return status;
}
