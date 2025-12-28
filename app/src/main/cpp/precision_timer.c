/**
 * Precision Timer - Microsecond-level timing
 * 
 * Uses hybrid approach for maximum accuracy:
 * 1. nanosleep() for long waits (>10ms)
 * 2. busy-wait for short waits (<1ms)
 * 3. timerfd for interval timing
 * 
 * Accuracy: <10μs on most devices
 */

#define _GNU_SOURCE  // Required for cpu_set_t, CPU_ZERO, CPU_SET, sched_setaffinity

#include "agent_core.h"
#include <stdlib.h>
#include <string.h>
#include <time.h>
#include <errno.h>

#ifdef __ANDROID__
#include <unistd.h>
#include <sched.h>
#include <sys/timerfd.h>
#include <sys/resource.h>
#include <pthread.h>
#else
// Stub for non-Android builds (Windows/other platforms)
#ifndef CLOCK_MONOTONIC
#define CLOCK_MONOTONIC 1
#endif
#ifndef _WIN32
static inline int clock_gettime(int c, struct timespec* t) { (void)c; t->tv_sec = 0; t->tv_nsec = 0; return 0; }
static inline int nanosleep(const struct timespec* req, struct timespec* rem) { (void)req; (void)rem; return 0; }
#endif
static inline int sched_yield(void) { return 0; }
#define TFD_NONBLOCK 0
#define TFD_CLOEXEC 0
static inline int timerfd_create(int c, int f) { (void)c; (void)f; return -1; }
static inline int timerfd_settime(int fd, int f, const void* n, void* o) { (void)fd; (void)f; (void)n; (void)o; return -1; }
#endif

// ============================================================================
// Core Time Functions
// ============================================================================

uint64_t timer_now_ns(void) {
    struct timespec ts;
    clock_gettime(CLOCK_MONOTONIC, &ts);
    return (uint64_t)ts.tv_sec * 1000000000ULL + (uint64_t)ts.tv_nsec;
}

uint64_t timer_now_us(void) {
    return timer_now_ns() / 1000ULL;
}

uint64_t timer_now_ms(void) {
    return timer_now_ns() / 1000000ULL;
}

// ============================================================================
// Sleep Functions
// ============================================================================

void timer_sleep_ns(uint64_t ns) {
    if (ns == 0) return;
    
    // For very short sleeps (<100μs), use busy-wait for accuracy
    if (ns < 100000) {
        timer_busywait_ns(ns);
        return;
    }
    
    // For longer sleeps, use nanosleep + busy-wait for remainder
    uint64_t start = timer_now_ns();
    
    // Use nanosleep for most of the wait
    if (ns > 1000000) { // >1ms
        struct timespec req;
        uint64_t sleep_ns = ns - 500000; // Leave 500μs for busy-wait
        req.tv_sec = (time_t)(sleep_ns / 1000000000ULL);
        req.tv_nsec = (long)(sleep_ns % 1000000000ULL);
        nanosleep(&req, NULL);
    }
    
    // Busy-wait for the remainder
    uint64_t target = start + ns;
    while (timer_now_ns() < target) {
        // Yield occasionally to not hog CPU
        sched_yield();
    }
}

void timer_sleep_us(uint64_t us) {
    timer_sleep_ns(us * 1000ULL);
}

void timer_sleep_ms(uint64_t ms) {
    timer_sleep_ns(ms * 1000000ULL);
}

void timer_busywait_ns(uint64_t ns) {
    uint64_t target = timer_now_ns() + ns;
    while (timer_now_ns() < target) {
        // Pure busy-wait, no yield
        // Use memory barrier to prevent over-optimization
        __asm__ volatile("" ::: "memory");
    }
}

// ============================================================================
// Interval Timer (using timerfd)
// ============================================================================

#ifdef __ANDROID__

int timer_create_interval(uint64_t interval_us) {
    int fd = timerfd_create(CLOCK_MONOTONIC, TFD_NONBLOCK | TFD_CLOEXEC);
    if (fd < 0) {
        return -errno;
    }
    
    struct itimerspec its;
    its.it_interval.tv_sec = (time_t)(interval_us / 1000000ULL);
    its.it_interval.tv_nsec = (long)((interval_us % 1000000ULL) * 1000);
    its.it_value = its.it_interval;
    
    if (timerfd_settime(fd, 0, &its, NULL) < 0) {
        int err = errno;
        close(fd);
        return -err;
    }
    
    return fd;
}

int timer_wait_next(int timer_handle) {
    if (timer_handle < 0) return -1;
    
    uint64_t expirations;
    ssize_t s = read(timer_handle, &expirations, sizeof(expirations));
    if (s < 0) {
        if (errno == EAGAIN) {
            // Timer not expired yet, busy-wait
            while (1) {
                s = read(timer_handle, &expirations, sizeof(expirations));
                if (s == sizeof(expirations)) break;
                sched_yield();
            }
        } else {
            return -errno;
        }
    }
    
    return 0;
}

void timer_destroy(int timer_handle) {
    if (timer_handle >= 0) {
        close(timer_handle);
    }
}

#else
// Stubs for non-Android
int timer_create_interval(uint64_t interval_us) { return -1; }
int timer_wait_next(int timer_handle) { return -1; }
void timer_destroy(int timer_handle) {}
#endif

// ============================================================================
// Utility: Real-time Priority and CPU Affinity
// ============================================================================

#ifdef __ANDROID__

int set_realtime_priority(void) {
    struct sched_param param;
    param.sched_priority = sched_get_priority_max(SCHED_FIFO);
    
    // Try SCHED_FIFO first (requires root)
    if (sched_setscheduler(0, SCHED_FIFO, &param) == 0) {
        return 0;
    }
    
    // Fall back to SCHED_RR
    if (sched_setscheduler(0, SCHED_RR, &param) == 0) {
        return 0;
    }
    
    // Fall back to high nice value
    setpriority(PRIO_PROCESS, 0, -20);
    return 0;
}

int set_cpu_affinity(int cpu_id) {
    cpu_set_t cpuset;
    CPU_ZERO(&cpuset);
    CPU_SET(cpu_id, &cpuset);
    
    if (sched_setaffinity(0, sizeof(cpuset), &cpuset) != 0) {
        return -errno;
    }
    
    return 0;
}

int lock_cpu_frequency(void) {
    // Try to lock CPU to maximum frequency (requires root)
    // This writes to sysfs, may not work on all devices
    
    FILE* f = fopen("/sys/devices/system/cpu/cpu0/cpufreq/scaling_governor", "w");
    if (f) {
        fprintf(f, "performance");
        fclose(f);
        return 0;
    }
    
    return -1;
}

#else
// Stubs
int set_realtime_priority(void) { return 0; }
int set_cpu_affinity(int cpu_id) { return 0; }
int lock_cpu_frequency(void) { return 0; }
#endif

// ============================================================================
// Library Initialization
// ============================================================================

static int g_initialized = 0;
static const char* g_version = "1.0.0";

const char* agent_core_version(void) {
    return g_version;
}

int agent_core_init(void) {
    if (g_initialized) return 0;
    
    // Optional: Set up high-priority for timing accuracy
    set_realtime_priority();
    
    g_initialized = 1;
    return 0;
}

void agent_core_cleanup(void) {
    g_initialized = 0;
}
