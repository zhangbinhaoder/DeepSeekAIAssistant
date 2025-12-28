/**
 * High-Precision Touch Event Injection
 * 
 * Directly writes to /dev/input/eventX for minimal latency
 * Bypasses the Android input framework entirely
 * 
 * Latency: <1ms (vs ~10-50ms for AccessibilityService)
 * Requires Root access
 */

#include "agent_core.h"
#include <stdio.h>
#include <stdlib.h>
#include <string.h>
#include <errno.h>

#ifdef __ANDROID__
#include <fcntl.h>
#include <unistd.h>
#include <dirent.h>
#include <sys/ioctl.h>
#include <linux/input.h>
#include <linux/uinput.h>
#else
// Stub definitions for non-Android builds
#define O_RDWR 2
#define O_NONBLOCK 0
#define EV_SYN 0x00
#define EV_KEY 0x01
#define EV_ABS 0x03
#define SYN_REPORT 0
#define BTN_TOUCH 0x14a
#define ABS_X 0x00
#define ABS_Y 0x01
#define ABS_PRESSURE 0x18
#define ABS_MT_SLOT 0x2f
#define ABS_MT_POSITION_X 0x35
#define ABS_MT_POSITION_Y 0x36
#define ABS_MT_TRACKING_ID 0x39
#define ABS_MT_PRESSURE 0x3a
#define ABS_MT_TOUCH_MAJOR 0x30
struct input_absinfo { int maximum; };
struct input_event { struct { long tv_sec; long tv_usec; } time; unsigned short type; unsigned short code; int value; };
struct dirent { char d_name[256]; };
typedef void DIR;
#define EVIOCGBIT(ev, len) 0
#define EVIOCGABS(abs) 0
static inline int open(const char* p, int f) { return -1; }
static inline int close(int fd) { return 0; }
static inline int write(int fd, const void* buf, size_t len) { return -1; }
static inline int ioctl(int fd, unsigned long req, ...) { return -1; }
static inline DIR* opendir(const char* p) { return NULL; }
static inline struct dirent* readdir(DIR* d) { return NULL; }
static inline int closedir(DIR* d) { return 0; }
static inline int gettimeofday(struct { long tv_sec; long tv_usec; }* tv, void* tz) { return 0; }
#endif

// ============================================================================
// Constants
// ============================================================================

// Touch screen max coordinates (will be auto-detected)
static int touch_max_x = 1080;
static int touch_max_y = 2400;
static int touch_max_pressure = 1000;

// Tracking ID counter for multi-touch
static int next_tracking_id = 1;

// ============================================================================
// Device Detection
// ============================================================================

// Check if device is a touch screen
static int is_touchscreen(int fd) {
    unsigned long evbit[1] = {0};
    // absbit needs to be large enough to hold all ABS_* bits
    // ABS_MT_POSITION_Y is typically around 0x36, so need at least 2 unsigned longs
    unsigned long absbit[2] = {0};
    
    if (ioctl(fd, EVIOCGBIT(0, sizeof(evbit)), evbit) < 0) {
        return 0;
    }
    
    // Check for EV_ABS (absolute events, required for touch)
    if (!(evbit[0] & (1 << EV_ABS))) {
        return 0;
    }
    
    if (ioctl(fd, EVIOCGBIT(EV_ABS, sizeof(absbit)), absbit) < 0) {
        return 0;
    }
    
    // Check for ABS_MT_POSITION_X and ABS_MT_POSITION_Y (multi-touch)
    // Use 1UL to avoid shift overflow on 32-bit systems
    if ((absbit[ABS_MT_POSITION_X / 32] & (1UL << (ABS_MT_POSITION_X % 32))) &&
        (absbit[ABS_MT_POSITION_Y / 32] & (1UL << (ABS_MT_POSITION_Y % 32)))) {
        return 1;
    }
    
    // Fall back to single touch
    if ((absbit[ABS_X / 32] & (1UL << (ABS_X % 32))) &&
        (absbit[ABS_Y / 32] & (1UL << (ABS_Y % 32)))) {
        return 1;
    }
    
    return 0;
}

// Get device info
static int get_touch_info(int fd) {
    struct input_absinfo abs_x, abs_y, abs_pressure;
    
    // Get X range
    if (ioctl(fd, EVIOCGABS(ABS_MT_POSITION_X), &abs_x) == 0) {
        touch_max_x = abs_x.maximum;
    } else if (ioctl(fd, EVIOCGABS(ABS_X), &abs_x) == 0) {
        touch_max_x = abs_x.maximum;
    }
    
    // Get Y range
    if (ioctl(fd, EVIOCGABS(ABS_MT_POSITION_Y), &abs_y) == 0) {
        touch_max_y = abs_y.maximum;
    } else if (ioctl(fd, EVIOCGABS(ABS_Y), &abs_y) == 0) {
        touch_max_y = abs_y.maximum;
    }
    
    // Get pressure range
    if (ioctl(fd, EVIOCGABS(ABS_MT_PRESSURE), &abs_pressure) == 0) {
        touch_max_pressure = abs_pressure.maximum;
    } else if (ioctl(fd, EVIOCGABS(ABS_PRESSURE), &abs_pressure) == 0) {
        touch_max_pressure = abs_pressure.maximum;
    }
    
    return 0;
}

int touch_find_device(char* path_out, size_t path_size) {
    DIR* dir = opendir("/dev/input");
    if (!dir) {
        return -1;
    }
    
    struct dirent* entry;
    while ((entry = readdir(dir)) != NULL) {
        if (strncmp(entry->d_name, "event", 5) != 0) {
            continue;
        }
        
        char path[256];
        snprintf(path, sizeof(path), "/dev/input/%s", entry->d_name);
        
        int fd = open(path, O_RDWR);
        if (fd < 0) {
            continue;
        }
        
        if (is_touchscreen(fd)) {
            get_touch_info(fd);
            close(fd);
            closedir(dir);
            
            strncpy(path_out, path, path_size - 1);
            path_out[path_size - 1] = '\0';
            return 0;
        }
        
        close(fd);
    }
    
    closedir(dir);
    return -1;
}

int touch_open_device(const char* device_path) {
    int fd = open(device_path, O_RDWR | O_NONBLOCK);
    if (fd < 0) {
        return -errno;
    }
    
    get_touch_info(fd);
    return fd;
}

void touch_close_device(int fd) {
    if (fd >= 0) {
        close(fd);
    }
}

// ============================================================================
// Low-level Event Writing
// ============================================================================

// Write a single input event
static int write_event(int fd, uint16_t type, uint16_t code, int32_t value) {
    struct input_event ev;
    memset(&ev, 0, sizeof(ev));
    
    gettimeofday(&ev.time, NULL);
    ev.type = type;
    ev.code = code;
    ev.value = value;
    
    if (write(fd, &ev, sizeof(ev)) != sizeof(ev)) {
        return -errno;
    }
    
    return 0;
}

// Write sync event (required after each batch of events)
static int write_sync(int fd) {
    return write_event(fd, EV_SYN, SYN_REPORT, 0);
}

// ============================================================================
// Single Touch Operations
// ============================================================================

int touch_inject_single(int fd, TouchEventType type, int x, int y) {
    int tracking_id = (type == TOUCH_DOWN) ? next_tracking_id++ : -1;
    
    // Multi-touch protocol B
    if (type == TOUCH_DOWN || type == TOUCH_MOVE) {
        write_event(fd, EV_ABS, ABS_MT_SLOT, 0);
        write_event(fd, EV_ABS, ABS_MT_TRACKING_ID, (type == TOUCH_DOWN) ? tracking_id : tracking_id);
        write_event(fd, EV_ABS, ABS_MT_POSITION_X, x);
        write_event(fd, EV_ABS, ABS_MT_POSITION_Y, y);
        write_event(fd, EV_ABS, ABS_MT_PRESSURE, 50);
        write_event(fd, EV_ABS, ABS_MT_TOUCH_MAJOR, 5);
        
        // Legacy single-touch for compatibility
        write_event(fd, EV_ABS, ABS_X, x);
        write_event(fd, EV_ABS, ABS_Y, y);
        write_event(fd, EV_ABS, ABS_PRESSURE, 50);
        
        if (type == TOUCH_DOWN) {
            write_event(fd, EV_KEY, BTN_TOUCH, 1);
        }
    } else { // TOUCH_UP
        write_event(fd, EV_ABS, ABS_MT_SLOT, 0);
        write_event(fd, EV_ABS, ABS_MT_TRACKING_ID, -1);
        write_event(fd, EV_KEY, BTN_TOUCH, 0);
    }
    
    return write_sync(fd);
}

int touch_inject_down(int fd, int x, int y) {
    return touch_inject_single(fd, TOUCH_DOWN, x, y);
}

int touch_inject_up(int fd, int x, int y) {
    return touch_inject_single(fd, TOUCH_UP, x, y);
}

int touch_inject_move(int fd, int x, int y) {
    return touch_inject_single(fd, TOUCH_MOVE, x, y);
}

// ============================================================================
// High-level Touch Operations
// ============================================================================

int touch_inject_tap(int fd, int x, int y, int duration_us) {
    int ret = touch_inject_down(fd, x, y);
    if (ret != 0) return ret;
    
    timer_sleep_us(duration_us);
    
    return touch_inject_up(fd, x, y);
}

int touch_inject_swipe(int fd, int x1, int y1, int x2, int y2, 
                       int duration_us, int steps) {
    if (steps < 2) steps = 2;
    
    int ret = touch_inject_down(fd, x1, y1);
    if (ret != 0) return ret;
    
    int step_delay = duration_us / steps;
    
    for (int i = 1; i < steps; i++) {
        float t = (float)i / (float)(steps - 1);
        int x = x1 + (int)((x2 - x1) * t);
        int y = y1 + (int)((y2 - y1) * t);
        
        timer_sleep_us(step_delay);
        
        ret = touch_inject_move(fd, x, y);
        if (ret != 0) return ret;
    }
    
    timer_sleep_us(step_delay);
    
    return touch_inject_up(fd, x2, y2);
}

// ============================================================================
// Multi-touch Operations
// ============================================================================

int touch_inject_multi(int fd, const TouchPoint* points, int count, TouchEventType type) {
    for (int i = 0; i < count; i++) {
        write_event(fd, EV_ABS, ABS_MT_SLOT, points[i].id);
        
        if (type == TOUCH_UP) {
            write_event(fd, EV_ABS, ABS_MT_TRACKING_ID, -1);
        } else {
            if (type == TOUCH_DOWN) {
                write_event(fd, EV_ABS, ABS_MT_TRACKING_ID, next_tracking_id++);
            }
            write_event(fd, EV_ABS, ABS_MT_POSITION_X, points[i].x);
            write_event(fd, EV_ABS, ABS_MT_POSITION_Y, points[i].y);
            write_event(fd, EV_ABS, ABS_MT_PRESSURE, points[i].pressure > 0 ? points[i].pressure : 50);
            write_event(fd, EV_ABS, ABS_MT_TOUCH_MAJOR, points[i].size > 0 ? points[i].size : 5);
        }
    }
    
    if (type == TOUCH_DOWN) {
        write_event(fd, EV_KEY, BTN_TOUCH, 1);
    } else if (type == TOUCH_UP) {
        write_event(fd, EV_KEY, BTN_TOUCH, 0);
    }
    
    return write_sync(fd);
}

int touch_inject_pinch(int fd, int center_x, int center_y, 
                       int start_distance, int end_distance,
                       int duration_us, int steps) {
    if (steps < 2) steps = 2;
    
    int step_delay = duration_us / steps;
    
    for (int i = 0; i < steps; i++) {
        float t = (float)i / (float)(steps - 1);
        int distance = start_distance + (int)((end_distance - start_distance) * t);
        
        TouchPoint points[2] = {
            {0, center_x - distance / 2, center_y, 50, 5},
            {1, center_x + distance / 2, center_y, 50, 5}
        };
        
        TouchEventType type = (i == 0) ? TOUCH_DOWN : TOUCH_MOVE;
        int ret = touch_inject_multi(fd, points, 2, type);
        if (ret != 0) return ret;
        
        if (i < steps - 1) {
            timer_sleep_us(step_delay);
        }
    }
    
    // Final position and release
    TouchPoint points[2] = {
        {0, center_x - end_distance / 2, center_y, 50, 5},
        {1, center_x + end_distance / 2, center_y, 50, 5}
    };
    
    timer_sleep_us(step_delay);
    return touch_inject_multi(fd, points, 2, TOUCH_UP);
}
