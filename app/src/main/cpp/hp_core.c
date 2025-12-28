/**
 * High Performance Core - C语言高性能核心模块
 * 
 * 包含:
 * 1. 高速内存池 - 避免频繁分配
 * 2. 快速字符串操作 - SIMD 加速
 * 3. 轻量级 JSON 解析器 - 零拷贝
 * 4. 环形缓冲区 - 无锁通信
 * 5. 位图索引 - 快速查找
 * 
 * @author DeepSeek AI Assistant
 */

#include <stdint.h>
#include <stddef.h>
#include <stdbool.h>
#include <string.h>
#include <stdlib.h>

// 对齐内存分配辅助函数 (手动实现，避免依赖特定 API)
static void* aligned_malloc(size_t alignment, size_t size) {
    // 分配额外空间用于对齐和存储原始指针
    void* raw = malloc(size + alignment + sizeof(void*));
    if (!raw) return NULL;
    
    // 计算对齐后的地址
    uintptr_t addr = (uintptr_t)raw + sizeof(void*) + alignment - 1;
    void* aligned = (void*)((addr) & ~(alignment - 1));
    
    // 存储原始指针用于释放
    ((void**)aligned)[-1] = raw;
    
    return aligned;
}

static void aligned_free(void* ptr) {
    if (ptr) {
        free(((void**)ptr)[-1]);
    }
}

#ifdef __ARM_NEON
#include <arm_neon.h>
#endif

// ============================================================================
// 内存池 - 固定大小块分配器
// ============================================================================

#define POOL_BLOCK_SIZE 64
#define POOL_MAX_BLOCKS 4096

typedef struct {
    uint8_t* memory;
    uint32_t* free_list;
    uint32_t free_count;
    uint32_t block_size;
    uint32_t block_count;
    uint32_t total_allocs;
    uint32_t total_frees;
} MemoryPool;

static MemoryPool pools[4];  // 64, 256, 1024, 4096 字节池
static bool pools_initialized = false;

/**
 * 初始化内存池系统
 */
int mempool_init(void) {
    if (pools_initialized) return 0;
    
    uint32_t sizes[] = {64, 256, 1024, 4096};
    uint32_t counts[] = {4096, 1024, 256, 64};
    
    for (int i = 0; i < 4; i++) {
        pools[i].block_size = sizes[i];
        pools[i].block_count = counts[i];
        pools[i].memory = (uint8_t*)aligned_malloc(64, sizes[i] * counts[i]);
        if (!pools[i].memory) return -1;
        
        pools[i].free_list = (uint32_t*)malloc(counts[i] * sizeof(uint32_t));
        if (!pools[i].free_list) {
            free(pools[i].memory);
            return -1;
        }
        
        // 初始化空闲列表
        for (uint32_t j = 0; j < counts[i]; j++) {
            pools[i].free_list[j] = j;
        }
        pools[i].free_count = counts[i];
        pools[i].total_allocs = 0;
        pools[i].total_frees = 0;
    }
    
    pools_initialized = true;
    return 0;
}

/**
 * 从池中分配内存
 */
void* mempool_alloc(size_t size) {
    if (!pools_initialized) mempool_init();
    
    // 找合适的池
    int pool_idx = -1;
    if (size <= 64) pool_idx = 0;
    else if (size <= 256) pool_idx = 1;
    else if (size <= 1024) pool_idx = 2;
    else if (size <= 4096) pool_idx = 3;
    else return malloc(size);  // 太大，用普通 malloc
    
    MemoryPool* pool = &pools[pool_idx];
    if (pool->free_count == 0) {
        return malloc(size);  // 池满，回退
    }
    
    uint32_t block_idx = pool->free_list[--pool->free_count];
    pool->total_allocs++;
    
    return pool->memory + block_idx * pool->block_size;
}

/**
 * 释放回池中
 */
void mempool_free(void* ptr, size_t size) {
    if (!ptr) return;
    
    // 检查是否属于某个池
    for (int i = 0; i < 4; i++) {
        MemoryPool* pool = &pools[i];
        if (ptr >= (void*)pool->memory && 
            ptr < (void*)(pool->memory + pool->block_size * pool->block_count)) {
            
            uint32_t block_idx = ((uint8_t*)ptr - pool->memory) / pool->block_size;
            pool->free_list[pool->free_count++] = block_idx;
            pool->total_frees++;
            return;
        }
    }
    
    // 不属于池，普通 free
    free(ptr);
}

/**
 * 获取池统计信息
 */
void mempool_stats(uint32_t* total_allocs, uint32_t* total_frees, uint32_t* pool_usage) {
    *total_allocs = 0;
    *total_frees = 0;
    
    for (int i = 0; i < 4; i++) {
        *total_allocs += pools[i].total_allocs;
        *total_frees += pools[i].total_frees;
        pool_usage[i] = pools[i].block_count - pools[i].free_count;
    }
}

// ============================================================================
// 快速字符串操作 - SIMD 加速
// ============================================================================

/**
 * SIMD 加速的 strlen
 * 每次处理 16 字节
 */
size_t fast_strlen(const char* s) {
    if (!s) return 0;
    
    const char* start = s;
    
#ifdef __ARM_NEON
    // 对齐到 16 字节边界
    while (((uintptr_t)s & 15) && *s) s++;
    if (!*s) return s - start;
    
    // SIMD 处理
    uint8x16_t zero = vdupq_n_u8(0);
    
    while (1) {
        uint8x16_t chunk = vld1q_u8((const uint8_t*)s);
        uint8x16_t cmp = vceqq_u8(chunk, zero);
        
        // 检查是否有零
        uint64x2_t cmp64 = vreinterpretq_u64_u8(cmp);
        uint64_t low = vgetq_lane_u64(cmp64, 0);
        uint64_t high = vgetq_lane_u64(cmp64, 1);
        
        if (low) {
            // 找第一个零
            for (int i = 0; i < 8; i++) {
                if (!s[i]) return s - start + i;
            }
        }
        if (high) {
            for (int i = 8; i < 16; i++) {
                if (!s[i]) return s - start + i;
            }
        }
        
        s += 16;
    }
#else
    while (*s) s++;
    return s - start;
#endif
}

/**
 * SIMD 加速的 memchr
 */
const void* fast_memchr(const void* s, int c, size_t n) {
    const uint8_t* p = (const uint8_t*)s;
    uint8_t ch = (uint8_t)c;
    
#ifdef __ARM_NEON
    uint8x16_t needle = vdupq_n_u8(ch);
    
    while (n >= 16) {
        uint8x16_t chunk = vld1q_u8(p);
        uint8x16_t cmp = vceqq_u8(chunk, needle);
        
        uint64x2_t cmp64 = vreinterpretq_u64_u8(cmp);
        uint64_t low = vgetq_lane_u64(cmp64, 0);
        uint64_t high = vgetq_lane_u64(cmp64, 1);
        
        if (low) {
            for (int i = 0; i < 8; i++) {
                if (p[i] == ch) return p + i;
            }
        }
        if (high) {
            for (int i = 8; i < 16; i++) {
                if (p[i] == ch) return p + i;
            }
        }
        
        p += 16;
        n -= 16;
    }
#endif
    
    // 剩余部分
    while (n--) {
        if (*p == ch) return p;
        p++;
    }
    
    return NULL;
}

/**
 * 快速字符串比较 (SIMD)
 */
int fast_strcmp(const char* s1, const char* s2) {
#ifdef __ARM_NEON
    while (1) {
        uint8x16_t a = vld1q_u8((const uint8_t*)s1);
        uint8x16_t b = vld1q_u8((const uint8_t*)s2);
        uint8x16_t zero = vdupq_n_u8(0);
        
        // 比较
        uint8x16_t cmp = vceqq_u8(a, b);
        uint8x16_t end_a = vceqq_u8(a, zero);
        
        // 检查是否有不同或结束
        uint8x16_t diff_or_end = vornq_u8(end_a, cmp);  // ~cmp | end_a
        
        uint64x2_t result64 = vreinterpretq_u64_u8(diff_or_end);
        if (vgetq_lane_u64(result64, 0) != 0xFFFFFFFFFFFFFFFF ||
            vgetq_lane_u64(result64, 1) != 0xFFFFFFFFFFFFFFFF) {
            // 有差异，逐个比较
            for (int i = 0; i < 16; i++) {
                if (s1[i] != s2[i]) return (unsigned char)s1[i] - (unsigned char)s2[i];
                if (!s1[i]) return 0;
            }
        }
        
        s1 += 16;
        s2 += 16;
    }
#else
    while (*s1 && *s1 == *s2) {
        s1++;
        s2++;
    }
    return (unsigned char)*s1 - (unsigned char)*s2;
#endif
}

// ============================================================================
// 轻量级 JSON 解析器 - 零拷贝
// ============================================================================

typedef enum {
    JSON_NULL,
    JSON_BOOL,
    JSON_NUMBER,
    JSON_STRING,
    JSON_ARRAY,
    JSON_OBJECT
} JsonType;

typedef struct JsonValue {
    JsonType type;
    union {
        bool bool_val;
        double num_val;
        struct { const char* str; size_t len; } string_val;
        struct { struct JsonValue* items; size_t count; } array_val;
        struct { const char** keys; struct JsonValue* values; size_t count; } object_val;
    };
} JsonValue;

typedef struct {
    const char* input;
    size_t pos;
    size_t len;
    char error[128];
} JsonParser;

// 跳过空白
static void json_skip_whitespace(JsonParser* p) {
    while (p->pos < p->len) {
        char c = p->input[p->pos];
        if (c != ' ' && c != '\t' && c != '\n' && c != '\r') break;
        p->pos++;
    }
}

// 解析字符串 (零拷贝)
static bool json_parse_string(JsonParser* p, const char** out_str, size_t* out_len) {
    if (p->input[p->pos] != '"') return false;
    p->pos++;
    
    *out_str = p->input + p->pos;
    size_t start = p->pos;
    
    while (p->pos < p->len && p->input[p->pos] != '"') {
        if (p->input[p->pos] == '\\') p->pos++;  // 跳过转义
        p->pos++;
    }
    
    *out_len = p->pos - start;
    if (p->pos < p->len) p->pos++;  // 跳过结束引号
    
    return true;
}

// 解析数字
static bool json_parse_number(JsonParser* p, double* out) {
    const char* start = p->input + p->pos;
    char* end;
    *out = strtod(start, &end);
    
    if (end == start) return false;
    p->pos += end - start;
    return true;
}

// 前向声明
static bool json_parse_value(JsonParser* p, JsonValue* out);

// 解析数组
static bool json_parse_array(JsonParser* p, JsonValue* out) {
    if (p->input[p->pos] != '[') return false;
    p->pos++;
    
    out->type = JSON_ARRAY;
    out->array_val.items = NULL;
    out->array_val.count = 0;
    
    json_skip_whitespace(p);
    if (p->pos < p->len && p->input[p->pos] == ']') {
        p->pos++;
        return true;
    }
    
    // 简化: 最多支持 64 个元素
    JsonValue* items = (JsonValue*)mempool_alloc(64 * sizeof(JsonValue));
    size_t count = 0;
    
    while (count < 64) {
        json_skip_whitespace(p);
        if (!json_parse_value(p, &items[count])) break;
        count++;
        
        json_skip_whitespace(p);
        if (p->input[p->pos] != ',') break;
        p->pos++;
    }
    
    json_skip_whitespace(p);
    if (p->input[p->pos] == ']') p->pos++;
    
    out->array_val.items = items;
    out->array_val.count = count;
    return true;
}

// 解析对象
static bool json_parse_object(JsonParser* p, JsonValue* out) {
    if (p->input[p->pos] != '{') return false;
    p->pos++;
    
    out->type = JSON_OBJECT;
    out->object_val.keys = NULL;
    out->object_val.values = NULL;
    out->object_val.count = 0;
    
    json_skip_whitespace(p);
    if (p->pos < p->len && p->input[p->pos] == '}') {
        p->pos++;
        return true;
    }
    
    // 简化: 最多 32 个键值对
    const char** keys = (const char**)mempool_alloc(32 * sizeof(char*));
    JsonValue* values = (JsonValue*)mempool_alloc(32 * sizeof(JsonValue));
    size_t count = 0;
    
    while (count < 32) {
        json_skip_whitespace(p);
        
        const char* key;
        size_t key_len;
        if (!json_parse_string(p, &key, &key_len)) break;
        keys[count] = key;
        
        json_skip_whitespace(p);
        if (p->input[p->pos] != ':') break;
        p->pos++;
        
        json_skip_whitespace(p);
        if (!json_parse_value(p, &values[count])) break;
        count++;
        
        json_skip_whitespace(p);
        if (p->input[p->pos] != ',') break;
        p->pos++;
    }
    
    json_skip_whitespace(p);
    if (p->input[p->pos] == '}') p->pos++;
    
    out->object_val.keys = keys;
    out->object_val.values = values;
    out->object_val.count = count;
    return true;
}

// 解析任意值
static bool json_parse_value(JsonParser* p, JsonValue* out) {
    json_skip_whitespace(p);
    
    if (p->pos >= p->len) return false;
    
    char c = p->input[p->pos];
    
    if (c == '"') {
        out->type = JSON_STRING;
        return json_parse_string(p, &out->string_val.str, &out->string_val.len);
    }
    
    if (c == '[') {
        return json_parse_array(p, out);
    }
    
    if (c == '{') {
        return json_parse_object(p, out);
    }
    
    if (c == 't' && strncmp(p->input + p->pos, "true", 4) == 0) {
        out->type = JSON_BOOL;
        out->bool_val = true;
        p->pos += 4;
        return true;
    }
    
    if (c == 'f' && strncmp(p->input + p->pos, "false", 5) == 0) {
        out->type = JSON_BOOL;
        out->bool_val = false;
        p->pos += 5;
        return true;
    }
    
    if (c == 'n' && strncmp(p->input + p->pos, "null", 4) == 0) {
        out->type = JSON_NULL;
        p->pos += 4;
        return true;
    }
    
    if (c == '-' || (c >= '0' && c <= '9')) {
        out->type = JSON_NUMBER;
        return json_parse_number(p, &out->num_val);
    }
    
    return false;
}

/**
 * 解析 JSON 字符串
 */
bool fast_json_parse(const char* input, size_t len, JsonValue* out) {
    JsonParser parser = {
        .input = input,
        .pos = 0,
        .len = len,
        .error = {0}
    };
    
    return json_parse_value(&parser, out);
}

// ============================================================================
// 环形缓冲区 - 无锁单生产者单消费者
// ============================================================================

#define RING_BUFFER_SIZE 4096

typedef struct {
    uint8_t data[RING_BUFFER_SIZE];
    volatile uint32_t head;  // 写指针
    volatile uint32_t tail;  // 读指针
} RingBuffer;

/**
 * 初始化环形缓冲区
 */
void ring_buffer_init(RingBuffer* rb) {
    rb->head = 0;
    rb->tail = 0;
}

/**
 * 检查是否为空
 */
bool ring_buffer_empty(RingBuffer* rb) {
    return rb->head == rb->tail;
}

/**
 * 检查是否已满
 */
bool ring_buffer_full(RingBuffer* rb) {
    return ((rb->head + 1) % RING_BUFFER_SIZE) == rb->tail;
}

/**
 * 可用空间
 */
size_t ring_buffer_available(RingBuffer* rb) {
    if (rb->head >= rb->tail) {
        return RING_BUFFER_SIZE - (rb->head - rb->tail) - 1;
    }
    return rb->tail - rb->head - 1;
}

/**
 * 写入数据 (无锁)
 */
size_t ring_buffer_write(RingBuffer* rb, const uint8_t* data, size_t len) {
    size_t written = 0;
    
    while (written < len && !ring_buffer_full(rb)) {
        rb->data[rb->head] = data[written++];
        __sync_synchronize();  // 内存屏障
        rb->head = (rb->head + 1) % RING_BUFFER_SIZE;
    }
    
    return written;
}

/**
 * 读取数据 (无锁)
 */
size_t ring_buffer_read(RingBuffer* rb, uint8_t* data, size_t len) {
    size_t read = 0;
    
    while (read < len && !ring_buffer_empty(rb)) {
        data[read++] = rb->data[rb->tail];
        __sync_synchronize();  // 内存屏障
        rb->tail = (rb->tail + 1) % RING_BUFFER_SIZE;
    }
    
    return read;
}

// ============================================================================
// 位图索引 - 快速查找
// ============================================================================

typedef struct {
    uint64_t* bits;
    size_t size;  // 位数
    size_t words; // 字数
} Bitmap;

/**
 * 创建位图
 */
Bitmap* bitmap_create(size_t size) {
    Bitmap* bm = (Bitmap*)mempool_alloc(sizeof(Bitmap));
    bm->size = size;
    bm->words = (size + 63) / 64;
    bm->bits = (uint64_t*)mempool_alloc(bm->words * sizeof(uint64_t));
    memset(bm->bits, 0, bm->words * sizeof(uint64_t));
    return bm;
}

/**
 * 设置位
 */
void bitmap_set(Bitmap* bm, size_t index) {
    if (index >= bm->size) return;
    bm->bits[index / 64] |= (1ULL << (index % 64));
}

/**
 * 清除位
 */
void bitmap_clear(Bitmap* bm, size_t index) {
    if (index >= bm->size) return;
    bm->bits[index / 64] &= ~(1ULL << (index % 64));
}

/**
 * 测试位
 */
bool bitmap_test(Bitmap* bm, size_t index) {
    if (index >= bm->size) return false;
    return (bm->bits[index / 64] & (1ULL << (index % 64))) != 0;
}

/**
 * 找第一个设置的位
 */
int64_t bitmap_find_first_set(Bitmap* bm) {
    for (size_t i = 0; i < bm->words; i++) {
        if (bm->bits[i]) {
            // 使用 __builtin_ctzll 找尾随零
            return i * 64 + __builtin_ctzll(bm->bits[i]);
        }
    }
    return -1;
}

/**
 * 找第一个清除的位
 */
int64_t bitmap_find_first_clear(Bitmap* bm) {
    for (size_t i = 0; i < bm->words; i++) {
        if (bm->bits[i] != 0xFFFFFFFFFFFFFFFF) {
            return i * 64 + __builtin_ctzll(~bm->bits[i]);
        }
    }
    return -1;
}

/**
 * 统计设置的位数
 */
size_t bitmap_popcount(Bitmap* bm) {
    size_t count = 0;
    for (size_t i = 0; i < bm->words; i++) {
        count += __builtin_popcountll(bm->bits[i]);
    }
    return count;
}

// ============================================================================
// 导出函数声明 (供 JNI 调用)
// ============================================================================

// 这些函数会在 agent_jni.c 中绑定到 JNI

int hp_mempool_init(void) {
    return mempool_init();
}

void* hp_mempool_alloc(size_t size) {
    return mempool_alloc(size);
}

void hp_mempool_free(void* ptr, size_t size) {
    mempool_free(ptr, size);
}

size_t hp_fast_strlen(const char* s) {
    return fast_strlen(s);
}

int hp_fast_strcmp(const char* s1, const char* s2) {
    return fast_strcmp(s1, s2);
}

bool hp_json_parse(const char* input, size_t len) {
    JsonValue value;
    return fast_json_parse(input, len, &value);
}
