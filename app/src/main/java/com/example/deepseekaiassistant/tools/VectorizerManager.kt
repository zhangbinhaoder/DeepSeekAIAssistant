package com.example.deepseekaiassistant.tools

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Canvas
import android.graphics.Color
import android.net.Uri
import android.util.Log
import kotlinx.coroutines.*
import java.io.*
import java.nio.ByteBuffer

/**
 * VectorizerManager - 位图转矢量图管理器
 * 
 * 支持的输入格式: PNG, JPG, JPEG, BMP, WEBP, GIF
 * 支持的输出格式: SVG, PDF, EPS
 */
class VectorizerManager private constructor(private val context: Context) {
    
    companion object {
        private const val TAG = "VectorizerManager"
        
        @Volatile
        private var instance: VectorizerManager? = null
        
        fun getInstance(context: Context): VectorizerManager {
            return instance ?: synchronized(this) {
                instance ?: VectorizerManager(context.applicationContext).also { instance = it }
            }
        }
        
        // 支持的输入格式
        val SUPPORTED_INPUT_FORMATS = listOf(
            "png", "jpg", "jpeg", "bmp", "webp", "gif", "ico", "tiff", "tif"
        )
        
        // 支持的输出格式
        val SUPPORTED_OUTPUT_FORMATS = listOf(
            OutputFormat.SVG,
            OutputFormat.PDF,
            OutputFormat.EPS,
            OutputFormat.DXF  // CAD 格式
        )
    }
    
    // 输出格式枚举
    enum class OutputFormat(val extension: String, val mimeType: String, val displayName: String) {
        SVG("svg", "image/svg+xml", "SVG (可缩放矢量图形)"),
        PDF("pdf", "application/pdf", "PDF (便携式文档格式)"),
        EPS("eps", "application/postscript", "EPS (封装的PostScript)"),
        DXF("dxf", "application/dxf", "DXF (AutoCAD格式)")
    }
    
    // 矢量化参数
    data class VectorizeParams(
        val threshold: Int = 0,           // 二值化阈值 (0=自动)
        val simplifyTolerance: Double = 2.0,  // 简化容差 (推荐1.0-5.0)
        val turdSize: Int = 2,            // 小斑点过滤阈值
        val outputFormat: OutputFormat = OutputFormat.SVG,
        val invertColors: Boolean = false, // 是否反转颜色
        val strokeWidth: Float = 1.0f,    // 描边宽度 (用于非填充模式)
        val fillMode: Boolean = true       // 填充模式
    )
    
    // 分析结果
    data class AnalysisResult(
        val width: Int,
        val height: Int,
        val autoThreshold: Int,
        val blackPixelCount: Int,
        val whitePixelCount: Int,
        val blackRatio: Float,
        val estimatedPathCount: Int
    )
    
    // 矢量化结果
    data class VectorizeResult(
        val success: Boolean,
        val outputPath: String? = null,
        val outputFormat: OutputFormat? = null,
        val svgContent: String? = null,
        val errorMessage: String? = null,
        val processingTimeMs: Long = 0
    )
    
    // 进度回调
    interface ProgressCallback {
        fun onProgress(stage: String, progress: Int)
        fun onComplete(result: VectorizeResult)
        fun onError(error: String)
    }
    
    private var nativeLoaded = false
    
    init {
        try {
            System.loadLibrary("deepseek_native")
            nativeLoaded = true
            Log.i(TAG, "[VECTORIZER] Native library loaded successfully")
        } catch (e: UnsatisfiedLinkError) {
            Log.w(TAG, "[VECTORIZER] Native library not available, using Java fallback")
            nativeLoaded = false
        }
    }
    
    // Native 方法声明
    private external fun nativeVectorize(
        pixelData: ByteArray,
        width: Int,
        height: Int,
        threshold: Int,
        simplifyTolerance: Double,
        turdSize: Int,
        outputFormat: Int
    ): String?
    
    private external fun nativeAnalyze(
        pixelData: ByteArray,
        width: Int,
        height: Int
    ): IntArray?
    
    /**
     * 从 Uri 加载位图
     */
    fun loadBitmap(uri: Uri): Bitmap? {
        return try {
            context.contentResolver.openInputStream(uri)?.use { input ->
                BitmapFactory.decodeStream(input)
            }
        } catch (e: Exception) {
            Log.e(TAG, "[VECTORIZER] Failed to load bitmap: ${e.message}")
            null
        }
    }
    
    /**
     * 从文件路径加载位图
     */
    fun loadBitmap(filePath: String): Bitmap? {
        return try {
            BitmapFactory.decodeFile(filePath)
        } catch (e: Exception) {
            Log.e(TAG, "[VECTORIZER] Failed to load bitmap from file: ${e.message}")
            null
        }
    }
    
    /**
     * 分析图像
     */
    fun analyze(bitmap: Bitmap): AnalysisResult {
        val width = bitmap.width
        val height = bitmap.height
        
        try {
            val pixels = extractPixelData(bitmap)
            
            if (nativeLoaded && pixels != null) {
                val result = nativeAnalyze(pixels, width, height)
                if (result != null && result.size >= 3) {
                    return AnalysisResult(
                        width = width,
                        height = height,
                        autoThreshold = result[0],
                        blackPixelCount = result[1],
                        whitePixelCount = result[2],
                        blackRatio = result[1].toFloat() / (width * height),
                        estimatedPathCount = estimatePathCount(result[1], width, height)
                    )
                }
            }
        } catch (e: Exception) {
            Log.e(TAG, "[VECTORIZER] Native analyze failed: ${e.message}")
        }
        
        // Java 回退实现
        return analyzeJava(bitmap)
    }
    
    private fun analyzeJava(bitmap: Bitmap): AnalysisResult {
        val width = bitmap.width
        val height = bitmap.height
        val histogram = IntArray(256)
        
        // 计算灰度直方图
        for (y in 0 until height) {
            for (x in 0 until width) {
                val pixel = bitmap.getPixel(x, y)
                val gray = (Color.red(pixel) * 77 + Color.green(pixel) * 150 + Color.blue(pixel) * 29) shr 8
                histogram[gray]++
            }
        }
        
        // Otsu 阈值计算
        val total = width * height
        var sum = 0.0
        for (i in 0 until 256) {
            sum += i * histogram[i]
        }
        
        var sumB = 0.0
        var wB = 0
        var maxVar = 0.0
        var threshold = 128
        
        for (t in 0 until 256) {
            wB += histogram[t]
            if (wB == 0) continue
            
            val wF = total - wB
            if (wF == 0) break
            
            sumB += t * histogram[t]
            val mB = sumB / wB
            val mF = (sum - sumB) / wF
            
            val variance = wB.toDouble() * wF * (mB - mF) * (mB - mF)
            if (variance > maxVar) {
                maxVar = variance
                threshold = t
            }
        }
        
        // 统计黑白像素
        var blackCount = 0
        for (i in 0 until threshold) {
            blackCount += histogram[i]
        }
        
        return AnalysisResult(
            width = width,
            height = height,
            autoThreshold = threshold,
            blackPixelCount = blackCount,
            whitePixelCount = total - blackCount,
            blackRatio = blackCount.toFloat() / total,
            estimatedPathCount = estimatePathCount(blackCount, width, height)
        )
    }
    
    private fun estimatePathCount(blackPixels: Int, width: Int, height: Int): Int {
        // 估计路径数量（基于黑色区域的连通分量）
        val avgRegionSize = 100  // 假设平均每个区域100个像素
        return maxOf(1, blackPixels / avgRegionSize)
    }
    
    /**
     * 执行矢量化
     */
    suspend fun vectorize(
        bitmap: Bitmap,
        params: VectorizeParams,
        outputDir: File,
        outputFileName: String,
        callback: ProgressCallback? = null
    ): VectorizeResult = withContext(Dispatchers.Default) {
        val startTime = System.currentTimeMillis()
        var tempBitmap: Bitmap? = null  // 用于追踪需要回收的临时bitmap
        
        try {
            callback?.onProgress("准备图像数据...", 10)
            
            val processBitmap = if (params.invertColors) {
                invertBitmap(bitmap).also { tempBitmap = it }
            } else {
                bitmap
            }
            
            if (processBitmap == null) {
                throw Exception("Failed to process bitmap: memory allocation failed")
            }
            
            val pixels = extractPixelData(processBitmap)
            if (pixels.isEmpty()) {
                throw Exception("Failed to extract pixel data")
            }
            
            val width = processBitmap.width
            val height = processBitmap.height
            
            callback?.onProgress("正在矢量化...", 30)
            
            val vectorContent: String = if (nativeLoaded) {
                val formatCode = when (params.outputFormat) {
                    OutputFormat.SVG -> 0
                    OutputFormat.PDF -> 1
                    OutputFormat.EPS -> 2
                    OutputFormat.DXF -> 3
                }
                
                nativeVectorize(
                    pixels, width, height,
                    params.threshold,
                    params.simplifyTolerance,
                    params.turdSize,
                    formatCode
                ) ?: throw Exception("Native vectorization failed")
            } else {
                // Java 回退实现
                vectorizeJava(processBitmap, params)
            }
            
            callback?.onProgress("生成输出文件...", 80)
            
            // 确保输出目录存在
            if (!outputDir.exists()) {
                outputDir.mkdirs()
            }
            
            // 保存输出文件
            val outputFile = File(outputDir, "$outputFileName.${params.outputFormat.extension}")
            outputFile.writeText(vectorContent)
            
            callback?.onProgress("完成!", 100)
            
            val result = VectorizeResult(
                success = true,
                outputPath = outputFile.absolutePath,
                outputFormat = params.outputFormat,
                svgContent = if (params.outputFormat == OutputFormat.SVG) vectorContent else null,
                processingTimeMs = System.currentTimeMillis() - startTime
            )
            
            callback?.onComplete(result)
            result
            
        } catch (e: Exception) {
            Log.e(TAG, "[VECTORIZER] Vectorization failed: ${e.message}", e)
            val result = VectorizeResult(
                success = false,
                errorMessage = e.message ?: "Unknown error",
                processingTimeMs = System.currentTimeMillis() - startTime
            )
            callback?.onError(e.message ?: "Unknown error")
            result
        } finally {
            // 回收临时创建的bitmap，避免内存泄漏
            tempBitmap?.let {
                if (!it.isRecycled) {
                    it.recycle()
                }
            }
        }
    }
    
    /**
     * Java 回退实现
     */
    private fun vectorizeJava(bitmap: Bitmap, params: VectorizeParams): String {
        val width = bitmap.width
        val height = bitmap.height
        
        // 分析获取阈值
        val analysis = analyzeJava(bitmap)
        val threshold = if (params.threshold > 0) params.threshold else analysis.autoThreshold
        
        // 转换为二值化图像并追踪轮廓
        val contours = traceContoursJava(bitmap, threshold, params.turdSize)
        
        // 简化路径
        val simplified = contours.map { path ->
            simplifyPath(path, params.simplifyTolerance)
        }
        
        // 生成输出
        return when (params.outputFormat) {
            OutputFormat.SVG -> generateSvg(simplified, width, height, params)
            OutputFormat.PDF -> generatePdf(simplified, width, height, params)
            OutputFormat.EPS -> generateEps(simplified, width, height, params)
            OutputFormat.DXF -> generateDxf(simplified, width, height, params)
        }
    }
    
    /**
     * 轮廓追踪 (Moore-Neighbor)
     */
    private fun traceContoursJava(bitmap: Bitmap, threshold: Int, turdSize: Int): List<List<Pair<Float, Float>>> {
        val width = bitmap.width
        val height = bitmap.height
        
        // 创建二值图
        val binary = Array(height) { y ->
            BooleanArray(width) { x ->
                val pixel = bitmap.getPixel(x, y)
                val gray = (Color.red(pixel) * 77 + Color.green(pixel) * 150 + Color.blue(pixel) * 29) shr 8
                gray < threshold
            }
        }
        
        val visited = Array(height) { BooleanArray(width) }
        val contours = mutableListOf<List<Pair<Float, Float>>>()
        
        // 8-邻域方向
        val dx = intArrayOf(1, 1, 0, -1, -1, -1, 0, 1)
        val dy = intArrayOf(0, 1, 1, 1, 0, -1, -1, -1)
        
        fun isBorder(x: Int, y: Int): Boolean {
            if (x < 0 || x >= width || y < 0 || y >= height) return false
            if (!binary[y][x]) return false
            
            for (d in 0 until 8) {
                val nx = x + dx[d]
                val ny = y + dy[d]
                if (nx < 0 || nx >= width || ny < 0 || ny >= height || !binary[ny][nx]) {
                    return true
                }
            }
            return false
        }
        
        fun traceContour(startX: Int, startY: Int): List<Pair<Float, Float>> {
            val path = mutableListOf<Pair<Float, Float>>()
            var x = startX
            var y = startY
            var dir = 0
            
            do {
                path.add(Pair(x.toFloat(), y.toFloat()))
                visited[y][x] = true
                
                var found = false
                for (i in 0 until 8) {
                    val newDir = (dir + 6 + i) % 8
                    val nx = x + dx[newDir]
                    val ny = y + dy[newDir]
                    
                    if (nx in 0 until width && ny in 0 until height && binary[ny][nx]) {
                        x = nx
                        y = ny
                        dir = newDir
                        found = true
                        break
                    }
                }
                
                if (!found) break
                
            } while (x != startX || y != startY)
            
            return path
        }
        
        // 扫描所有像素
        for (y in 0 until height) {
            for (x in 0 until width) {
                if (binary[y][x] && !visited[y][x] && isBorder(x, y)) {
                    val contour = traceContour(x, y)
                    if (contour.size >= turdSize) {
                        contours.add(contour)
                    }
                }
            }
        }
        
        return contours
    }
    
    /**
     * Douglas-Peucker 路径简化
     */
    private fun simplifyPath(path: List<Pair<Float, Float>>, tolerance: Double): List<Pair<Float, Float>> {
        if (path.size < 3) return path
        
        val keep = BooleanArray(path.size)
        keep[0] = true
        keep[path.size - 1] = true
        
        fun simplifyDP(start: Int, end: Int) {
            if (end <= start + 1) return
            
            var maxDist = 0.0
            var maxIdx = start
            
            val a = path[start]
            val b = path[end]
            
            for (i in start + 1 until end) {
                val p = path[i]
                val dist = pointLineDistance(p, a, b)
                if (dist > maxDist) {
                    maxDist = dist
                    maxIdx = i
                }
            }
            
            if (maxDist > tolerance) {
                keep[maxIdx] = true
                simplifyDP(start, maxIdx)
                simplifyDP(maxIdx, end)
            }
        }
        
        simplifyDP(0, path.size - 1)
        
        return path.filterIndexed { index, _ -> keep[index] }
    }
    
    private fun pointLineDistance(p: Pair<Float, Float>, a: Pair<Float, Float>, b: Pair<Float, Float>): Double {
        val dx = b.first - a.first
        val dy = b.second - a.second
        val d = dx * dx + dy * dy
        
        if (d < 1e-10) {
            return kotlin.math.sqrt(
                ((p.first - a.first) * (p.first - a.first) + 
                 (p.second - a.second) * (p.second - a.second)).toDouble()
            )
        }
        
        var t = ((p.first - a.first) * dx + (p.second - a.second) * dy) / d
        t = t.coerceIn(0.0f, 1.0f)
        
        val px = a.first + t * dx
        val py = a.second + t * dy
        
        return kotlin.math.sqrt(
            ((p.first - px) * (p.first - px) + 
             (p.second - py) * (p.second - py)).toDouble()
        )
    }
    
    /**
     * 生成 SVG
     */
    private fun generateSvg(paths: List<List<Pair<Float, Float>>>, width: Int, height: Int, params: VectorizeParams): String {
        val sb = StringBuilder()
        sb.append("""<?xml version="1.0" encoding="UTF-8"?>
<svg xmlns="http://www.w3.org/2000/svg" width="$width" height="$height" viewBox="0 0 $width $height">
  <g fill="${if (params.fillMode) "black" else "none"}" stroke="${if (params.fillMode) "none" else "black"}" stroke-width="${params.strokeWidth}">
""")
        
        for (path in paths) {
            if (path.size < 2) continue
            
            sb.append("    <path d=\"M")
            path.forEachIndexed { index, point ->
                if (index == 0) {
                    sb.append("${point.first},${point.second}")
                } else {
                    sb.append(" L${point.first},${point.second}")
                }
            }
            sb.append(" Z\"/>\n")
        }
        
        sb.append("  </g>\n</svg>\n")
        return sb.toString()
    }
    
    /**
     * 生成 PDF
     */
    private fun generatePdf(paths: List<List<Pair<Float, Float>>>, width: Int, height: Int, params: VectorizeParams): String {
        val sb = StringBuilder()
        sb.append("""%PDF-1.4
1 0 obj
<< /Type /Catalog /Pages 2 0 R >>
endobj
2 0 obj
<< /Type /Pages /Kids [3 0 R] /Count 1 >>
endobj
3 0 obj
<< /Type /Page /Parent 2 0 R /MediaBox [0 0 $width $height] /Contents 4 0 R >>
endobj
4 0 obj
<< /Length 5 0 R >>
stream
0 0 0 rg
""")
        
        for (path in paths) {
            if (path.size < 2) continue
            
            sb.append("${path[0].first} ${height - path[0].second} m\n")
            for (i in 1 until path.size) {
                sb.append("${path[i].first} ${height - path[i].second} l\n")
            }
            sb.append("h f\n")
        }
        
        val streamContent = sb.toString().substringAfter("stream\n")
        val streamLen = streamContent.length
        
        sb.append("""endstream
endobj
5 0 obj
$streamLen
endobj
xref
0 6
trailer
<< /Size 6 /Root 1 0 R >>
startxref
0
%%EOF
""")
        
        return sb.toString()
    }
    
    /**
     * 生成 EPS
     */
    private fun generateEps(paths: List<List<Pair<Float, Float>>>, width: Int, height: Int, params: VectorizeParams): String {
        val sb = StringBuilder()
        sb.append("""%!PS-Adobe-3.0 EPSF-3.0
%%BoundingBox: 0 0 $width $height
%%Title: Vectorized Image
%%Creator: DeepSeek AI Assistant
%%EndComments

/l { lineto } def
/m { moveto } def
/c { closepath } def
/f { fill } def

0 0 0 setrgbcolor

""")
        
        for (path in paths) {
            if (path.size < 2) continue
            
            sb.append("newpath\n")
            sb.append("${path[0].first} ${height - path[0].second} m\n")
            for (i in 1 until path.size) {
                sb.append("${path[i].first} ${height - path[i].second} l\n")
            }
            sb.append("c f\n\n")
        }
        
        sb.append("showpage\n%%EOF\n")
        return sb.toString()
    }
    
    /**
     * 生成 DXF (AutoCAD 格式)
     */
    private fun generateDxf(paths: List<List<Pair<Float, Float>>>, width: Int, height: Int, params: VectorizeParams): String {
        val sb = StringBuilder()
        
        // DXF 头部
        sb.append("""0
SECTION
2
HEADER
0
ENDSEC
0
SECTION
2
ENTITIES
""")
        
        for (path in paths) {
            if (path.size < 2) continue
            
            // POLYLINE
            sb.append("""0
POLYLINE
8
0
66
1
70
1
""")
            
            for (point in path) {
                sb.append("""0
VERTEX
8
0
10
${point.first}
20
${height - point.second}
30
0
""")
            }
            
            sb.append("""0
SEQEND
""")
        }
        
        sb.append("""0
ENDSEC
0
EOF
""")
        
        return sb.toString()
    }
    
    /**
     * 提取像素数据 (RGBA)
     */
    private fun extractPixelData(bitmap: Bitmap): ByteArray {
        val width = bitmap.width
        val height = bitmap.height
        
        // 验证bitmap有效性
        if (width <= 0 || height <= 0 || bitmap.isRecycled) {
            Log.e(TAG, "[VECTORIZER] Invalid bitmap: width=$width, height=$height, recycled=${bitmap.isRecycled}")
            return ByteArray(0)
        }
        
        var copiedBitmap: Bitmap? = null
        
        try {
            // 确保是 ARGB_8888 格式
            val argbBitmap: Bitmap = if (bitmap.config != Bitmap.Config.ARGB_8888) {
                val copied = bitmap.copy(Bitmap.Config.ARGB_8888, false)
                if (copied == null) {
                    Log.e(TAG, "[VECTORIZER] Failed to copy bitmap to ARGB_8888 format")
                    return ByteArray(0)
                }
                copiedBitmap = copied
                copied
            } else {
                bitmap
            }
            
            val buffer = ByteBuffer.allocate(width * height * 4)
            argbBitmap.copyPixelsToBuffer(buffer)
            return buffer.array()
            
        } catch (e: Exception) {
            Log.e(TAG, "[VECTORIZER] Failed to extract pixels: ${e.message}", e)
            return ByteArray(0)
        } finally {
            // 如果创建了副本，用完后回收
            copiedBitmap?.let {
                if (!it.isRecycled) {
                    it.recycle()
                }
            }
        }
    }
    
    /**
     * 反转位图颜色
     */
    private fun invertBitmap(source: Bitmap): Bitmap? {
        return try {
            if (source.isRecycled) {
                Log.e(TAG, "[VECTORIZER] Cannot invert recycled bitmap")
                return null
            }
            
            val result = Bitmap.createBitmap(source.width, source.height, Bitmap.Config.ARGB_8888)
            if (result == null) {
                Log.e(TAG, "[VECTORIZER] Failed to create bitmap for inversion")
                return null
            }
            
            // 使用批量像素操作提高效率
            val width = source.width
            val height = source.height
            val pixels = IntArray(width * height)
            source.getPixels(pixels, 0, width, 0, 0, width, height)
            
            for (i in pixels.indices) {
                val pixel = pixels[i]
                pixels[i] = Color.argb(
                    Color.alpha(pixel),
                    255 - Color.red(pixel),
                    255 - Color.green(pixel),
                    255 - Color.blue(pixel)
                )
            }
            
            result.setPixels(pixels, 0, width, 0, 0, width, height)
            result
            
        } catch (e: Exception) {
            Log.e(TAG, "[VECTORIZER] Failed to invert bitmap: ${e.message}", e)
            null
        }
    }
    
    /**
     * 检查文件格式是否支持
     */
    fun isSupportedInputFormat(fileName: String): Boolean {
        val ext = fileName.substringAfterLast('.').lowercase()
        return ext in SUPPORTED_INPUT_FORMATS
    }
    
    /**
     * 获取输出文件 MIME 类型
     */
    fun getMimeType(format: OutputFormat): String = format.mimeType
}
