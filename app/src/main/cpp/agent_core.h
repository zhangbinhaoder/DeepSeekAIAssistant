/**
 * Agent Core - High Performance C/C++ Library
 * 
 * Provides ultra-low-latency operations for AI Agent system:
 * - SIMD accelerated image processing (NEON/SSE)
 * - Direct touch event injection via /dev/input
 * - Microsecond precision timing
 * - Fast framebuffer screenshot
 * 
 * Copyright (c) 2024 DeepSeek AI Assistant
 */

#ifndef AGENT_CORE_H
#define AGENT_CORE_H

#include <stdint.h>
#include <stdbool.h>
#include <stddef.h>

#ifdef __cplusplus
extern "C" {
#endif

// ============================================================================
// Version and Initialization
// ============================================================================

#define AGENT_CORE_VERSION_MAJOR 1
#define AGENT_CORE_VERSION_MINOR 0
#define AGENT_CORE_VERSION_PATCH 0

/**
 * Get library version string
 */
const char* agent_core_version(void);

/**
 * Initialize the library
 * @return 0 on success, negative on error
 */
int agent_core_init(void);

/**
 * Cleanup and release resources
 */
void agent_core_cleanup(void);

/**
 * Check if SIMD (NEON/SSE) is available
 */
bool agent_core_has_simd(void);

// ============================================================================
// SIMD Image Processing
// ============================================================================

/**
 * RGB pixel structure (packed)
 */
typedef struct {
    uint8_t r, g, b;
} __attribute__((packed)) RGB24;

/**
 * ARGB pixel structure (packed)
 */
typedef struct {
    uint8_t a, r, g, b;
} __attribute__((packed)) ARGB32;

/**
 * HSV color structure
 */
typedef struct {
    float h;  // 0-360
    float s;  // 0-1
    float v;  // 0-1
} HSV;

/**
 * Rectangle structure
 */
typedef struct {
    int32_t x, y;
    int32_t width, height;
} Rect;

/**
 * Detected element structure
 */
typedef struct {
    Rect bounds;
    int type;       // 0=health_bar_enemy, 1=health_bar_ally, 2=skill_button, etc.
    float confidence;
} DetectedElement;

/**
 * Convert ARGB image to grayscale using SIMD
 * @param src Source ARGB pixels
 * @param dst Destination grayscale buffer
 * @param width Image width
 * @param height Image height
 * @return 0 on success
 */
int simd_argb_to_grayscale(const uint8_t* src, uint8_t* dst, int width, int height);

/**
 * Convert ARGB image to HSV using SIMD
 * @param src Source ARGB pixels
 * @param dst Destination HSV buffer
 * @param pixel_count Number of pixels
 * @return 0 on success
 */
int simd_argb_to_hsv(const uint8_t* src, HSV* dst, int pixel_count);

/**
 * Find pixels matching color range (SIMD accelerated)
 * @param src Source ARGB pixels
 * @param mask Output binary mask (1=match, 0=no match)
 * @param pixel_count Number of pixels
 * @param target_hsv Target HSV color
 * @param h_tolerance Hue tolerance (degrees)
 * @param s_tolerance Saturation tolerance
 * @param v_tolerance Value tolerance
 * @return Number of matching pixels
 */
int simd_find_color(const uint8_t* src, uint8_t* mask, int pixel_count,
                    HSV target_hsv, float h_tolerance, float s_tolerance, float v_tolerance);

/**
 * Detect red color regions (enemy health bars)
 * @param src Source ARGB pixels
 * @param width Image width
 * @param height Image height
 * @param elements Output detected elements
 * @param max_elements Maximum elements to detect
 * @return Number of detected elements
 */
int simd_detect_red_regions(const uint8_t* src, int width, int height,
                            DetectedElement* elements, int max_elements);

/**
 * Detect blue color regions (ally health bars)
 */
int simd_detect_blue_regions(const uint8_t* src, int width, int height,
                             DetectedElement* elements, int max_elements);

/**
 * Detect green color regions (self health bars)
 */
int simd_detect_green_regions(const uint8_t* src, int width, int height,
                              DetectedElement* elements, int max_elements);

/**
 * Fast image difference (SIMD)
 * @param img1 First image ARGB
 * @param img2 Second image ARGB
 * @param diff Output difference magnitude
 * @param pixel_count Number of pixels
 * @param threshold Difference threshold
 * @return Number of pixels exceeding threshold
 */
int simd_image_diff(const uint8_t* img1, const uint8_t* img2, 
                    uint8_t* diff, int pixel_count, int threshold);

/**
 * Fast box blur (SIMD)
 */
int simd_box_blur(const uint8_t* src, uint8_t* dst, int width, int height, int radius);

// ============================================================================
// Touch Event Injection
// ============================================================================

/**
 * Touch event types
 */
typedef enum {
    TOUCH_DOWN = 0,
    TOUCH_UP = 1,
    TOUCH_MOVE = 2
} TouchEventType;

/**
 * Touch point structure
 */
typedef struct {
    int32_t id;         // Tracking ID (for multi-touch)
    int32_t x;          // X coordinate
    int32_t y;          // Y coordinate
    int32_t pressure;   // Pressure (0-1000)
    int32_t size;       // Touch size
} TouchPoint;

/**
 * Open touch input device
 * @param device_path Path like /dev/input/event0
 * @return File descriptor, or negative on error
 */
int touch_open_device(const char* device_path);

/**
 * Close touch device
 */
void touch_close_device(int fd);

/**
 * Auto-detect touch device
 * @param path_out Buffer to store device path
 * @param path_size Size of path buffer
 * @return 0 on success
 */
int touch_find_device(char* path_out, size_t path_size);

/**
 * Inject single touch event
 * @param fd Device file descriptor
 * @param type Event type (down/up/move)
 * @param x X coordinate
 * @param y Y coordinate
 * @return 0 on success
 */
int touch_inject_single(int fd, TouchEventType type, int x, int y);

/**
 * Inject touch down
 */
int touch_inject_down(int fd, int x, int y);

/**
 * Inject touch up
 */
int touch_inject_up(int fd, int x, int y);

/**
 * Inject touch move
 */
int touch_inject_move(int fd, int x, int y);

/**
 * Inject tap (down + delay + up)
 * @param fd Device file descriptor
 * @param x X coordinate
 * @param y Y coordinate
 * @param duration_us Duration in microseconds
 * @return 0 on success
 */
int touch_inject_tap(int fd, int x, int y, int duration_us);

/**
 * Inject swipe
 * @param fd Device file descriptor
 * @param x1, y1 Start coordinates
 * @param x2, y2 End coordinates
 * @param duration_us Duration in microseconds
 * @param steps Number of intermediate steps
 * @return 0 on success
 */
int touch_inject_swipe(int fd, int x1, int y1, int x2, int y2, 
                       int duration_us, int steps);

/**
 * Inject multi-touch event
 * @param fd Device file descriptor
 * @param points Array of touch points
 * @param count Number of touch points
 * @param type Event type
 * @return 0 on success
 */
int touch_inject_multi(int fd, const TouchPoint* points, int count, TouchEventType type);

/**
 * Inject pinch gesture
 */
int touch_inject_pinch(int fd, int center_x, int center_y, 
                       int start_distance, int end_distance,
                       int duration_us, int steps);

// ============================================================================
// Precision Timer
// ============================================================================

/**
 * Get current time in nanoseconds (monotonic)
 */
uint64_t timer_now_ns(void);

/**
 * Get current time in microseconds
 */
uint64_t timer_now_us(void);

/**
 * Get current time in milliseconds
 */
uint64_t timer_now_ms(void);

/**
 * High precision sleep (nanoseconds)
 * Uses hybrid approach: nanosleep + busy-wait for accuracy
 */
void timer_sleep_ns(uint64_t ns);

/**
 * High precision sleep (microseconds)
 */
void timer_sleep_us(uint64_t us);

/**
 * High precision sleep (milliseconds)
 */
void timer_sleep_ms(uint64_t ms);

/**
 * Busy-wait for exact timing (use sparingly, CPU intensive)
 */
void timer_busywait_ns(uint64_t ns);

/**
 * Create a timer that fires at specified interval
 * @param interval_us Interval in microseconds
 * @return Timer handle, or negative on error
 */
int timer_create_interval(uint64_t interval_us);

/**
 * Wait for next timer tick
 * @param timer_handle Timer handle from timer_create_interval
 * @return 0 on success
 */
int timer_wait_next(int timer_handle);

/**
 * Destroy interval timer
 */
void timer_destroy(int timer_handle);

// ============================================================================
// Fast Screenshot (Framebuffer)
// ============================================================================

/**
 * Screen info structure
 */
typedef struct {
    int32_t width;
    int32_t height;
    int32_t stride;
    int32_t format;     // RGBA_8888=1, RGBX_8888=2, RGB_888=3, etc.
    int32_t bpp;        // Bits per pixel
} ScreenInfo;

/**
 * Get screen information
 * @param info Output screen info
 * @return 0 on success
 */
int screen_get_info(ScreenInfo* info);

/**
 * Capture screen to buffer (Root required)
 * Uses /dev/graphics/fb0 or SurfaceFlinger
 * @param buffer Output buffer (must be pre-allocated)
 * @param buffer_size Size of buffer
 * @param info Screen info (optional, for validation)
 * @return Number of bytes written, or negative on error
 */
int screen_capture(uint8_t* buffer, size_t buffer_size, ScreenInfo* info);

/**
 * Capture screen region (faster than full capture + crop)
 * @param buffer Output buffer
 * @param buffer_size Size of buffer
 * @param x, y, width, height Region to capture
 * @return Number of bytes written
 */
int screen_capture_region(uint8_t* buffer, size_t buffer_size,
                          int x, int y, int width, int height);

/**
 * Map framebuffer for direct access (fastest, requires Root)
 * @param info Output screen info
 * @return Pointer to framebuffer, or NULL on error
 */
void* screen_map_framebuffer(ScreenInfo* info);

/**
 * Unmap framebuffer
 */
void screen_unmap_framebuffer(void* fb_ptr, size_t size);

// ============================================================================
// Memory Operations (Root)
// ============================================================================

/**
 * Fast memory search using SIMD
 * @param pid Target process ID
 * @param start_addr Start address
 * @param end_addr End address
 * @param pattern Pattern to search
 * @param pattern_len Pattern length
 * @param results Output array of matching addresses
 * @param max_results Maximum results
 * @return Number of matches found
 */
int memory_search_pattern(int pid, uint64_t start_addr, uint64_t end_addr,
                          const uint8_t* pattern, size_t pattern_len,
                          uint64_t* results, int max_results);

/**
 * Read memory from process (Root required)
 */
int memory_read(int pid, uint64_t addr, void* buffer, size_t size);

/**
 * Write memory to process (Root required)
 */
int memory_write(int pid, uint64_t addr, const void* data, size_t size);

// ============================================================================
// Utility Functions
// ============================================================================

/**
 * Set thread to high priority for real-time operations
 */
int set_realtime_priority(void);

/**
 * Pin thread to specific CPU core
 */
int set_cpu_affinity(int cpu_id);

/**
 * Disable CPU frequency scaling (requires Root)
 */
int lock_cpu_frequency(void);

#ifdef __cplusplus
}
#endif

#endif // AGENT_CORE_H
