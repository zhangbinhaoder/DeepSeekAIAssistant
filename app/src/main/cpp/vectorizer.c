/**
 * vectorizer.c - 位图转矢量图核心引擎
 * 
 * 实现 Potrace 风格的位图矢量化算法
 * - 边缘检测与轮廓追踪
 * - 贝塞尔曲线拟合
 * - SVG/PDF/EPS 输出
 */

#include <stdio.h>
#include <stdlib.h>
#include <string.h>
#include <stdint.h>
#include <math.h>
#include <jni.h>

// ============================================================================
// 数据结构定义
// ============================================================================

typedef struct {
    double x;
    double y;
} Point;

typedef struct {
    Point* points;
    int count;
    int capacity;
    int closed;  // 是否闭合路径
} Path;

typedef struct {
    Path* paths;
    int count;
    int capacity;
} PathList;

typedef struct {
    uint8_t* data;      // 二值化图像数据
    int width;
    int height;
    int threshold;      // 二值化阈值
} BitmapData;

typedef struct {
    double turnpolicy;  // 转弯策略
    double alphamax;    // 曲线平滑度
    double opttolerance; // 优化容差
    int turdsize;       // 小斑点过滤阈值
} VectorizeParams;

// ============================================================================
// 内存管理
// ============================================================================

static Path* path_create(int initial_capacity) {
    Path* path = (Path*)malloc(sizeof(Path));
    if (!path) return NULL;
    
    path->capacity = initial_capacity > 0 ? initial_capacity : 64;
    path->points = (Point*)malloc(sizeof(Point) * path->capacity);
    path->count = 0;
    path->closed = 0;
    
    if (!path->points) {
        free(path);
        return NULL;
    }
    return path;
}

static void path_destroy(Path* path) {
    if (path) {
        if (path->points) free(path->points);
        free(path);
    }
}

static int path_add_point(Path* path, double x, double y) {
    if (path->count >= path->capacity) {
        int new_cap = path->capacity * 2;
        Point* new_points = (Point*)realloc(path->points, sizeof(Point) * new_cap);
        if (!new_points) return -1;
        path->points = new_points;
        path->capacity = new_cap;
    }
    path->points[path->count].x = x;
    path->points[path->count].y = y;
    path->count++;
    return 0;
}

static PathList* pathlist_create(void) {
    PathList* list = (PathList*)malloc(sizeof(PathList));
    if (!list) return NULL;
    
    list->capacity = 32;
    list->paths = (Path*)malloc(sizeof(Path) * list->capacity);
    list->count = 0;
    
    if (!list->paths) {
        free(list);
        return NULL;
    }
    return list;
}

static void pathlist_destroy(PathList* list) {
    if (list) {
        if (list->paths) {
            for (int i = 0; i < list->count; i++) {
                if (list->paths[i].points) {
                    free(list->paths[i].points);
                }
            }
            free(list->paths);
        }
        free(list);
    }
}

// ============================================================================
// 图像预处理
// ============================================================================

// 转换为灰度图
static uint8_t* to_grayscale(const uint8_t* rgba, int width, int height) {
    uint8_t* gray = (uint8_t*)malloc(width * height);
    if (!gray) return NULL;
    
    for (int i = 0; i < width * height; i++) {
        int r = rgba[i * 4];
        int g = rgba[i * 4 + 1];
        int b = rgba[i * 4 + 2];
        // 加权灰度转换
        gray[i] = (uint8_t)((r * 77 + g * 150 + b * 29) >> 8);
    }
    return gray;
}

// 二值化
static uint8_t* binarize(const uint8_t* gray, int width, int height, int threshold) {
    uint8_t* binary = (uint8_t*)malloc(width * height);
    if (!binary) return NULL;
    
    for (int i = 0; i < width * height; i++) {
        binary[i] = gray[i] < threshold ? 1 : 0;
    }
    return binary;
}

// 自适应阈值（Otsu方法）
static int otsu_threshold(const uint8_t* gray, int width, int height) {
    int histogram[256] = {0};
    int total = width * height;
    
    // 计算直方图
    for (int i = 0; i < total; i++) {
        histogram[gray[i]]++;
    }
    
    double sum = 0;
    for (int i = 0; i < 256; i++) {
        sum += i * histogram[i];
    }
    
    double sumB = 0;
    int wB = 0, wF = 0;
    double maxVar = 0;
    int threshold = 0;
    
    for (int t = 0; t < 256; t++) {
        wB += histogram[t];
        if (wB == 0) continue;
        
        wF = total - wB;
        if (wF == 0) break;
        
        sumB += t * histogram[t];
        
        double mB = sumB / wB;
        double mF = (sum - sumB) / wF;
        
        double var = (double)wB * wF * (mB - mF) * (mB - mF);
        
        if (var > maxVar) {
            maxVar = var;
            threshold = t;
        }
    }
    
    return threshold;
}

// ============================================================================
// 轮廓追踪 (Moore-Neighbor tracing)
// ============================================================================

// 方向定义 (8-邻域)
static const int dx8[] = {1, 1, 0, -1, -1, -1, 0, 1};
static const int dy8[] = {0, 1, 1, 1, 0, -1, -1, -1};

static inline int get_pixel(const uint8_t* data, int width, int height, int x, int y) {
    if (x < 0 || x >= width || y < 0 || y >= height) return 0;
    return data[y * width + x];
}

static inline void set_pixel(uint8_t* data, int width, int x, int y, uint8_t val) {
    data[y * width + x] = val;
}

// 追踪单个轮廓
static Path* trace_contour(const uint8_t* binary, uint8_t* visited, 
                           int width, int height, int startX, int startY) {
    Path* path = path_create(128);
    if (!path) return NULL;
    
    int x = startX, y = startY;
    int dir = 0;  // 起始方向
    
    do {
        path_add_point(path, (double)x, (double)y);
        visited[y * width + x] = 1;
        
        // 寻找下一个边界像素
        int found = 0;
        for (int i = 0; i < 8; i++) {
            int newDir = (dir + 6 + i) % 8;  // 从左后方开始搜索
            int nx = x + dx8[newDir];
            int ny = y + dy8[newDir];
            
            if (get_pixel(binary, width, height, nx, ny)) {
                x = nx;
                y = ny;
                dir = newDir;
                found = 1;
                break;
            }
        }
        
        if (!found) break;
        
    } while (x != startX || y != startY);
    
    if (path->count > 2) {
        path->closed = 1;
    }
    
    return path;
}

// 提取所有轮廓
static PathList* extract_contours(const uint8_t* binary, int width, int height, int turdsize) {
    PathList* list = pathlist_create();
    if (!list) return NULL;
    
    uint8_t* visited = (uint8_t*)calloc(width * height, 1);
    if (!visited) {
        pathlist_destroy(list);
        return NULL;
    }
    
    // 扫描图像寻找轮廓起点
    for (int y = 0; y < height; y++) {
        for (int x = 0; x < width; x++) {
            if (binary[y * width + x] && !visited[y * width + x]) {
                // 检查是否为边界像素（有邻居为0）
                int isBorder = 0;
                for (int d = 0; d < 8; d++) {
                    if (!get_pixel(binary, width, height, x + dx8[d], y + dy8[d])) {
                        isBorder = 1;
                        break;
                    }
                }
                
                if (isBorder) {
                    Path* contour = trace_contour(binary, visited, width, height, x, y);
                    if (contour && contour->count >= turdsize) {
                        // 添加到列表
                        if (list->count >= list->capacity) {
                            int new_cap = list->capacity * 2;
                            Path* new_paths = (Path*)realloc(list->paths, sizeof(Path) * new_cap);
                            if (new_paths) {
                                list->paths = new_paths;
                                list->capacity = new_cap;
                            }
                        }
                        list->paths[list->count] = *contour;
                        list->count++;
                        free(contour);  // 只释放结构体，点数据已转移
                    } else if (contour) {
                        path_destroy(contour);
                    }
                } else {
                    visited[y * width + x] = 1;
                }
            }
        }
    }
    
    free(visited);
    return list;
}

// ============================================================================
// 曲线拟合 (Douglas-Peucker + 贝塞尔)
// ============================================================================

// 点到线段距离
static double point_line_distance(Point p, Point a, Point b) {
    double dx = b.x - a.x;
    double dy = b.y - a.y;
    double d = dx * dx + dy * dy;
    
    if (d < 1e-10) {
        return sqrt((p.x - a.x) * (p.x - a.x) + (p.y - a.y) * (p.y - a.y));
    }
    
    double t = ((p.x - a.x) * dx + (p.y - a.y) * dy) / d;
    t = t < 0 ? 0 : (t > 1 ? 1 : t);
    
    double px = a.x + t * dx;
    double py = a.y + t * dy;
    
    return sqrt((p.x - px) * (p.x - px) + (p.y - py) * (p.y - py));
}

// Douglas-Peucker 简化算法
static void simplify_dp(Point* points, int start, int end, double epsilon, int* keep) {
    if (end <= start + 1) return;
    
    double maxDist = 0;
    int maxIdx = start;
    
    for (int i = start + 1; i < end; i++) {
        double dist = point_line_distance(points[i], points[start], points[end]);
        if (dist > maxDist) {
            maxDist = dist;
            maxIdx = i;
        }
    }
    
    if (maxDist > epsilon) {
        keep[maxIdx] = 1;
        simplify_dp(points, start, maxIdx, epsilon, keep);
        simplify_dp(points, maxIdx, end, epsilon, keep);
    }
}

static Path* simplify_path(Path* path, double tolerance) {
    if (path->count < 3) return path;
    
    int* keep = (int*)calloc(path->count, sizeof(int));
    if (!keep) return path;
    
    keep[0] = 1;
    keep[path->count - 1] = 1;
    
    simplify_dp(path->points, 0, path->count - 1, tolerance, keep);
    
    // 创建简化后的路径
    Path* simplified = path_create(path->count);
    if (simplified) {
        for (int i = 0; i < path->count; i++) {
            if (keep[i]) {
                path_add_point(simplified, path->points[i].x, path->points[i].y);
            }
        }
        simplified->closed = path->closed;
    }
    
    free(keep);
    return simplified;
}

// ============================================================================
// SVG 输出
// ============================================================================

static int generate_svg(PathList* paths, int width, int height, 
                       char* buffer, int bufferSize) {
    int offset = 0;
    
    // SVG 头部
    offset += snprintf(buffer + offset, bufferSize - offset,
        "<?xml version=\"1.0\" encoding=\"UTF-8\"?>\n"
        "<svg xmlns=\"http://www.w3.org/2000/svg\" "
        "width=\"%d\" height=\"%d\" viewBox=\"0 0 %d %d\">\n"
        "  <g fill=\"black\" stroke=\"none\">\n",
        width, height, width, height);
    
    // 输出每条路径
    for (int p = 0; p < paths->count; p++) {
        Path* path = &paths->paths[p];
        if (path->count < 2) continue;
        
        offset += snprintf(buffer + offset, bufferSize - offset, "    <path d=\"M");
        
        for (int i = 0; i < path->count; i++) {
            if (i == 0) {
                offset += snprintf(buffer + offset, bufferSize - offset,
                    "%.2f,%.2f", path->points[i].x, path->points[i].y);
            } else {
                offset += snprintf(buffer + offset, bufferSize - offset,
                    " L%.2f,%.2f", path->points[i].x, path->points[i].y);
            }
        }
        
        if (path->closed) {
            offset += snprintf(buffer + offset, bufferSize - offset, " Z");
        }
        
        offset += snprintf(buffer + offset, bufferSize - offset, "\"/>\n");
        
        if (offset >= bufferSize - 100) break;  // 防止溢出
    }
    
    // SVG 尾部
    offset += snprintf(buffer + offset, bufferSize - offset,
        "  </g>\n</svg>\n");
    
    return offset;
}

// ============================================================================
// PDF 输出
// ============================================================================

static int generate_pdf(PathList* paths, int width, int height,
                       char* buffer, int bufferSize) {
    int offset = 0;
    
    // PDF 头部
    offset += snprintf(buffer + offset, bufferSize - offset,
        "%%PDF-1.4\n"
        "1 0 obj\n<< /Type /Catalog /Pages 2 0 R >>\nendobj\n"
        "2 0 obj\n<< /Type /Pages /Kids [3 0 R] /Count 1 >>\nendobj\n"
        "3 0 obj\n<< /Type /Page /Parent 2 0 R /MediaBox [0 0 %d %d] /Contents 4 0 R >>\nendobj\n"
        "4 0 obj\n<< /Length 5 0 R >>\nstream\n",
        width, height);
    
    int streamStart = offset;
    
    // 设置填充颜色
    offset += snprintf(buffer + offset, bufferSize - offset, "0 0 0 rg\n");
    
    // 输出路径
    for (int p = 0; p < paths->count; p++) {
        Path* path = &paths->paths[p];
        if (path->count < 2) continue;
        
        // PDF 坐标系 Y 轴翻转
        offset += snprintf(buffer + offset, bufferSize - offset,
            "%.2f %.2f m\n", path->points[0].x, height - path->points[0].y);
        
        for (int i = 1; i < path->count; i++) {
            offset += snprintf(buffer + offset, bufferSize - offset,
                "%.2f %.2f l\n", path->points[i].x, height - path->points[i].y);
        }
        
        if (path->closed) {
            offset += snprintf(buffer + offset, bufferSize - offset, "h f\n");
        } else {
            offset += snprintf(buffer + offset, bufferSize - offset, "S\n");
        }
        
        if (offset >= bufferSize - 100) break;
    }
    
    int streamLen = offset - streamStart;
    
    // PDF 尾部
    offset += snprintf(buffer + offset, bufferSize - offset,
        "endstream\nendobj\n"
        "5 0 obj\n%d\nendobj\n"
        "xref\n0 6\n"
        "0000000000 65535 f \n"
        "0000000009 00000 n \n"
        "0000000058 00000 n \n"
        "0000000115 00000 n \n"
        "0000000214 00000 n \n"
        "trailer\n<< /Size 6 /Root 1 0 R >>\n"
        "startxref\n%d\n%%%%EOF\n",
        streamLen, offset - 50);
    
    return offset;
}

// ============================================================================
// EPS 输出
// ============================================================================

static int generate_eps(PathList* paths, int width, int height,
                       char* buffer, int bufferSize) {
    int offset = 0;
    
    // EPS 头部
    offset += snprintf(buffer + offset, bufferSize - offset,
        "%%!PS-Adobe-3.0 EPSF-3.0\n"
        "%%%%BoundingBox: 0 0 %d %d\n"
        "%%%%Title: Vectorized Image\n"
        "%%%%Creator: DeepSeek AI Assistant\n"
        "%%%%EndComments\n\n"
        "/l { lineto } def\n"
        "/m { moveto } def\n"
        "/c { closepath } def\n"
        "/f { fill } def\n\n"
        "0 0 0 setrgbcolor\n\n",
        width, height);
    
    // 输出路径
    for (int p = 0; p < paths->count; p++) {
        Path* path = &paths->paths[p];
        if (path->count < 2) continue;
        
        offset += snprintf(buffer + offset, bufferSize - offset,
            "newpath\n%.2f %.2f m\n", path->points[0].x, height - path->points[0].y);
        
        for (int i = 1; i < path->count; i++) {
            offset += snprintf(buffer + offset, bufferSize - offset,
                "%.2f %.2f l\n", path->points[i].x, height - path->points[i].y);
        }
        
        if (path->closed) {
            offset += snprintf(buffer + offset, bufferSize - offset, "c f\n\n");
        } else {
            offset += snprintf(buffer + offset, bufferSize - offset, "stroke\n\n");
        }
        
        if (offset >= bufferSize - 100) break;
    }
    
    offset += snprintf(buffer + offset, bufferSize - offset,
        "showpage\n%%%%EOF\n");
    
    return offset;
}

// ============================================================================
// JNI 接口
// ============================================================================

/**
 * 位图转矢量图主函数
 * 
 * @param rgba RGBA像素数据
 * @param width 图像宽度
 * @param height 图像高度
 * @param threshold 二值化阈值 (0=自动)
 * @param simplifyTolerance 简化容差 (推荐1.0-5.0)
 * @param turdsize 小斑点过滤阈值
 * @param outputFormat 0=SVG, 1=PDF, 2=EPS
 * @return 输出字符串
 */
JNIEXPORT jstring JNICALL
Java_com_example_deepseekaiassistant_tools_VectorizerManager_nativeVectorize(
    JNIEnv* env, jobject thiz,
    jbyteArray pixelData, jint width, jint height,
    jint threshold, jdouble simplifyTolerance, jint turdsize,
    jint outputFormat) {
    
    // 获取像素数据
    jbyte* pixels = (*env)->GetByteArrayElements(env, pixelData, NULL);
    if (!pixels) return NULL;
    
    // 转换为灰度
    uint8_t* gray = to_grayscale((uint8_t*)pixels, width, height);
    (*env)->ReleaseByteArrayElements(env, pixelData, pixels, JNI_ABORT);
    
    if (!gray) return NULL;
    
    // 计算阈值
    int thresh = threshold > 0 ? threshold : otsu_threshold(gray, width, height);
    
    // 二值化
    uint8_t* binary = binarize(gray, width, height, thresh);
    free(gray);
    
    if (!binary) return NULL;
    
    // 提取轮廓
    PathList* contours = extract_contours(binary, width, height, turdsize > 0 ? turdsize : 2);
    free(binary);
    
    if (!contours) return NULL;
    
    // 简化路径
    for (int i = 0; i < contours->count; i++) {
        if (simplifyTolerance > 0) {
            Path* simplified = simplify_path(&contours->paths[i], simplifyTolerance);
            if (simplified && simplified != &contours->paths[i]) {
                free(contours->paths[i].points);
                contours->paths[i] = *simplified;
                free(simplified);
            }
        }
    }
    
    // 生成输出
    int bufferSize = 1024 * 1024 * 10;  // 10MB 缓冲区
    char* buffer = (char*)malloc(bufferSize);
    if (!buffer) {
        pathlist_destroy(contours);
        return NULL;
    }
    
    int len = 0;
    switch (outputFormat) {
        case 1:
            len = generate_pdf(contours, width, height, buffer, bufferSize);
            break;
        case 2:
            len = generate_eps(contours, width, height, buffer, bufferSize);
            break;
        default:
            len = generate_svg(contours, width, height, buffer, bufferSize);
            break;
    }
    
    pathlist_destroy(contours);
    
    jstring result = (*env)->NewStringUTF(env, buffer);
    free(buffer);
    
    return result;
}

/**
 * 获取图像统计信息（用于预览）
 */
JNIEXPORT jintArray JNICALL
Java_com_example_deepseekaiassistant_tools_VectorizerManager_nativeAnalyze(
    JNIEnv* env, jobject thiz,
    jbyteArray pixelData, jint width, jint height) {
    
    jbyte* pixels = (*env)->GetByteArrayElements(env, pixelData, NULL);
    if (!pixels) return NULL;
    
    uint8_t* gray = to_grayscale((uint8_t*)pixels, width, height);
    (*env)->ReleaseByteArrayElements(env, pixelData, pixels, JNI_ABORT);
    
    if (!gray) return NULL;
    
    int threshold = otsu_threshold(gray, width, height);
    
    // 统计黑白像素数
    int blackCount = 0, whiteCount = 0;
    for (int i = 0; i < width * height; i++) {
        if (gray[i] < threshold) blackCount++;
        else whiteCount++;
    }
    
    free(gray);
    
    // 返回 [threshold, blackCount, whiteCount]
    jintArray result = (*env)->NewIntArray(env, 3);
    jint stats[] = {threshold, blackCount, whiteCount};
    (*env)->SetIntArrayRegion(env, result, 0, 3, stats);
    
    return result;
}
