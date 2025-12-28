/**
 * Screen Capture and Memory Operations
 * 
 * Provides fast screen capture via framebuffer and memory operations
 * for game memory reading/writing.
 * 
 * Note: Most operations require Root access.
 */

#include "agent_core.h"
#include <stdlib.h>
#include <string.h>
#include <errno.h>

#ifdef __ANDROID__
#include <unistd.h>
#include <fcntl.h>
#include <sys/mman.h>
#include <sys/stat.h>
#include <linux/fb.h>
#include <sys/ioctl.h>
#endif

// ============================================================================
// Screen Capture Implementation
// ============================================================================

#ifdef __ANDROID__

static int fb_fd = -1;
static void* fb_ptr = NULL;
static ScreenInfo cached_screen_info = {0};
static size_t fb_size = 0;

int screen_get_info(ScreenInfo* info) {
    if (!info) return -1;
    
    // Try to open framebuffer
    int fd = open("/dev/graphics/fb0", O_RDONLY);
    if (fd < 0) {
        // Try alternative path
        fd = open("/dev/fb0", O_RDONLY);
        if (fd < 0) {
            return -errno;
        }
    }
    
    struct fb_var_screeninfo vinfo;
    if (ioctl(fd, FBIOGET_VSCREENINFO, &vinfo) < 0) {
        close(fd);
        return -errno;
    }
    
    info->width = vinfo.xres;
    info->height = vinfo.yres;
    info->bpp = vinfo.bits_per_pixel;
    info->stride = info->width * (info->bpp / 8);
    
    // Detect format
    if (info->bpp == 32) {
        info->format = 1; // RGBA_8888
    } else if (info->bpp == 24) {
        info->format = 3; // RGB_888
    } else if (info->bpp == 16) {
        info->format = 4; // RGB_565
    } else {
        info->format = 0; // Unknown
    }
    
    cached_screen_info = *info;
    close(fd);
    return 0;
}

int screen_capture(uint8_t* buffer, size_t buffer_size, ScreenInfo* info) {
    if (!buffer) return -1;
    
    // Get screen info if not provided
    ScreenInfo local_info;
    if (!info) {
        if (screen_get_info(&local_info) < 0) {
            return -1;
        }
        info = &local_info;
    }
    
    size_t needed_size = info->stride * info->height;
    if (buffer_size < needed_size) {
        return -1;
    }
    
    // Open framebuffer
    int fd = open("/dev/graphics/fb0", O_RDONLY);
    if (fd < 0) {
        fd = open("/dev/fb0", O_RDONLY);
        if (fd < 0) {
            return -errno;
        }
    }
    
    // Read screen data
    ssize_t bytes_read = read(fd, buffer, needed_size);
    close(fd);
    
    return (int)bytes_read;
}

int screen_capture_region(uint8_t* buffer, size_t buffer_size,
                          int x, int y, int width, int height) {
    if (!buffer || width <= 0 || height <= 0) return -1;
    
    // Get full screen first
    ScreenInfo info;
    if (screen_get_info(&info) < 0) {
        return -1;
    }
    
    // Validate bounds
    if (x < 0) x = 0;
    if (y < 0) y = 0;
    if (x + width > info.width) width = info.width - x;
    if (y + height > info.height) height = info.height - y;
    
    int bytes_per_pixel = info.bpp / 8;
    size_t row_size = width * bytes_per_pixel;
    size_t needed_size = row_size * height;
    
    if (buffer_size < needed_size) {
        return -1;
    }
    
    // Capture full screen to temp buffer
    size_t full_size = info.stride * info.height;
    uint8_t* full_buffer = (uint8_t*)malloc(full_size);
    if (!full_buffer) {
        return -1;
    }
    
    int result = screen_capture(full_buffer, full_size, &info);
    if (result < 0) {
        free(full_buffer);
        return result;
    }
    
    // Copy region
    for (int row = 0; row < height; row++) {
        uint8_t* src = full_buffer + (y + row) * info.stride + x * bytes_per_pixel;
        uint8_t* dst = buffer + row * row_size;
        memcpy(dst, src, row_size);
    }
    
    free(full_buffer);
    return (int)needed_size;
}

void* screen_map_framebuffer(ScreenInfo* info) {
    if (fb_ptr) {
        // Already mapped
        if (info) *info = cached_screen_info;
        return fb_ptr;
    }
    
    if (screen_get_info(&cached_screen_info) < 0) {
        return NULL;
    }
    
    fb_fd = open("/dev/graphics/fb0", O_RDONLY);
    if (fb_fd < 0) {
        fb_fd = open("/dev/fb0", O_RDONLY);
        if (fb_fd < 0) {
            return NULL;
        }
    }
    
    fb_size = cached_screen_info.stride * cached_screen_info.height;
    fb_ptr = mmap(NULL, fb_size, PROT_READ, MAP_SHARED, fb_fd, 0);
    
    if (fb_ptr == MAP_FAILED) {
        fb_ptr = NULL;
        close(fb_fd);
        fb_fd = -1;
        return NULL;
    }
    
    if (info) *info = cached_screen_info;
    return fb_ptr;
}

void screen_unmap_framebuffer(void* ptr, size_t size) {
    if (ptr && ptr == fb_ptr) {
        munmap(fb_ptr, fb_size);
        fb_ptr = NULL;
        if (fb_fd >= 0) {
            close(fb_fd);
            fb_fd = -1;
        }
    }
}

#else
// Stubs for non-Android platforms
int screen_get_info(ScreenInfo* info) { return -1; }
int screen_capture(uint8_t* buffer, size_t buffer_size, ScreenInfo* info) { return -1; }
int screen_capture_region(uint8_t* buffer, size_t buffer_size, int x, int y, int width, int height) { return -1; }
void* screen_map_framebuffer(ScreenInfo* info) { return NULL; }
void screen_unmap_framebuffer(void* ptr, size_t size) {}
#endif

// ============================================================================
// Memory Operations Implementation
// ============================================================================

#ifdef __ANDROID__

int memory_search_pattern(int pid, uint64_t start_addr, uint64_t end_addr,
                          const uint8_t* pattern, size_t pattern_len,
                          uint64_t* results, int max_results) {
    if (!pattern || pattern_len == 0 || !results || max_results <= 0) {
        return 0;
    }
    
    char mem_path[64];
    snprintf(mem_path, sizeof(mem_path), "/proc/%d/mem", pid);
    
    int fd = open(mem_path, O_RDONLY);
    if (fd < 0) {
        return 0;
    }
    
    int count = 0;
    const size_t chunk_size = 4096;
    uint8_t* buffer = (uint8_t*)malloc(chunk_size + pattern_len);
    if (!buffer) {
        close(fd);
        return 0;
    }
    
    uint64_t addr = start_addr;
    size_t overlap = 0;
    
    while (addr < end_addr && count < max_results) {
        // Seek to address
        if (lseek64(fd, addr, SEEK_SET) < 0) {
            addr += chunk_size;
            continue;
        }
        
        // Move overlap data to beginning
        if (overlap > 0) {
            memmove(buffer, buffer + chunk_size, overlap);
        }
        
        // Read chunk
        ssize_t bytes_read = read(fd, buffer + overlap, chunk_size);
        if (bytes_read <= 0) {
            addr += chunk_size;
            continue;
        }
        
        // Search for pattern
        size_t search_len = overlap + bytes_read - pattern_len + 1;
        for (size_t i = 0; i < search_len && count < max_results; i++) {
            if (memcmp(buffer + i, pattern, pattern_len) == 0) {
                results[count++] = addr - overlap + i;
            }
        }
        
        addr += bytes_read;
        overlap = pattern_len - 1;
    }
    
    free(buffer);
    close(fd);
    return count;
}

int memory_read(int pid, uint64_t addr, void* buffer, size_t size) {
    if (!buffer || size == 0) return -1;
    
    char mem_path[64];
    snprintf(mem_path, sizeof(mem_path), "/proc/%d/mem", pid);
    
    int fd = open(mem_path, O_RDONLY);
    if (fd < 0) {
        return -errno;
    }
    
    if (lseek64(fd, addr, SEEK_SET) < 0) {
        close(fd);
        return -errno;
    }
    
    ssize_t bytes_read = read(fd, buffer, size);
    close(fd);
    
    if (bytes_read < 0) {
        return -errno;
    }
    
    return (bytes_read == (ssize_t)size) ? 0 : -1;
}

int memory_write(int pid, uint64_t addr, const void* data, size_t size) {
    if (!data || size == 0) return -1;
    
    char mem_path[64];
    snprintf(mem_path, sizeof(mem_path), "/proc/%d/mem", pid);
    
    int fd = open(mem_path, O_WRONLY);
    if (fd < 0) {
        return -errno;
    }
    
    if (lseek64(fd, addr, SEEK_SET) < 0) {
        close(fd);
        return -errno;
    }
    
    ssize_t bytes_written = write(fd, data, size);
    close(fd);
    
    if (bytes_written < 0) {
        return -errno;
    }
    
    return (bytes_written == (ssize_t)size) ? 0 : -1;
}

#else
// Stubs for non-Android platforms
int memory_search_pattern(int pid, uint64_t start_addr, uint64_t end_addr,
                          const uint8_t* pattern, size_t pattern_len,
                          uint64_t* results, int max_results) { return 0; }
int memory_read(int pid, uint64_t addr, void* buffer, size_t size) { return -1; }
int memory_write(int pid, uint64_t addr, const void* data, size_t size) { return -1; }
#endif
