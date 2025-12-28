package com.example.deepseekaiassistant.tools

import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Environment
import android.webkit.MimeTypeMap
import androidx.core.content.FileProvider
import java.io.File
import java.text.SimpleDateFormat
import java.util.*

/**
 * 文件管理器
 */
class FileManager(private val context: Context) {
    
    companion object {
        // 常用目录
        val ROOT_PATHS = listOf(
            FileLocation("内部存储", Environment.getExternalStorageDirectory().absolutePath),
            FileLocation("下载", Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_DOWNLOADS).absolutePath),
            FileLocation("图片", Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_PICTURES).absolutePath),
            FileLocation("文档", Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_DOCUMENTS).absolutePath),
            FileLocation("音乐", Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_MUSIC).absolutePath),
            FileLocation("视频", Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_MOVIES).absolutePath),
            FileLocation("DCIM", Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_DCIM).absolutePath)
        )
    }
    
    data class FileLocation(val name: String, val path: String)
    
    /**
     * 文件信息
     */
    data class FileInfo(
        val name: String,
        val path: String,
        val isDirectory: Boolean,
        val size: Long,
        val lastModified: Long,
        val extension: String,
        val mimeType: String?,
        val isHidden: Boolean,
        val canRead: Boolean,
        val canWrite: Boolean,
        val childCount: Int = 0
    ) {
        val formattedSize: String get() = formatFileSize(size)
        val formattedDate: String get() = formatDate(lastModified)
        val icon: FileIcon get() = getFileIcon(extension, isDirectory)
    }
    
    enum class FileIcon {
        FOLDER, IMAGE, VIDEO, AUDIO, DOCUMENT, CODE, ARCHIVE, APK, PDF, TEXT, UNKNOWN
    }
    
    enum class SortBy {
        NAME, SIZE, DATE, TYPE
    }
    
    /**
     * 获取目录内容
     */
    fun listFiles(
        path: String,
        showHidden: Boolean = false,
        sortBy: SortBy = SortBy.NAME,
        ascending: Boolean = true
    ): List<FileInfo> {
        val directory = File(path)
        
        if (!directory.exists() || !directory.isDirectory || !directory.canRead()) {
            return emptyList()
        }
        
        val files = directory.listFiles() ?: return emptyList()
        
        return files
            .filter { showHidden || !it.isHidden }
            .map { file ->
                FileInfo(
                    name = file.name,
                    path = file.absolutePath,
                    isDirectory = file.isDirectory,
                    size = if (file.isFile) file.length() else 0,
                    lastModified = file.lastModified(),
                    extension = file.extension.lowercase(),
                    mimeType = getMimeType(file),
                    isHidden = file.isHidden,
                    canRead = file.canRead(),
                    canWrite = file.canWrite(),
                    childCount = if (file.isDirectory) file.listFiles()?.size ?: 0 else 0
                )
            }
            .sortedWith(compareBy<FileInfo> { !it.isDirectory }.then(
                when (sortBy) {
                    SortBy.NAME -> if (ascending) compareBy { it.name.lowercase() } else compareByDescending { it.name.lowercase() }
                    SortBy.SIZE -> if (ascending) compareBy { it.size } else compareByDescending { it.size }
                    SortBy.DATE -> if (ascending) compareBy { it.lastModified } else compareByDescending { it.lastModified }
                    SortBy.TYPE -> if (ascending) compareBy { it.extension } else compareByDescending { it.extension }
                }
            ))
    }
    
    /**
     * 搜索文件
     */
    fun searchFiles(
        path: String,
        query: String,
        recursive: Boolean = true,
        maxResults: Int = 100
    ): List<FileInfo> {
        val results = mutableListOf<FileInfo>()
        searchFilesRecursive(File(path), query.lowercase(), recursive, results, maxResults)
        return results
    }
    
    private fun searchFilesRecursive(
        directory: File,
        query: String,
        recursive: Boolean,
        results: MutableList<FileInfo>,
        maxResults: Int
    ) {
        if (results.size >= maxResults) return
        
        val files = directory.listFiles() ?: return
        
        for (file in files) {
            if (results.size >= maxResults) break
            
            if (file.name.lowercase().contains(query)) {
                results.add(FileInfo(
                    name = file.name,
                    path = file.absolutePath,
                    isDirectory = file.isDirectory,
                    size = if (file.isFile) file.length() else 0,
                    lastModified = file.lastModified(),
                    extension = file.extension.lowercase(),
                    mimeType = getMimeType(file),
                    isHidden = file.isHidden,
                    canRead = file.canRead(),
                    canWrite = file.canWrite()
                ))
            }
            
            if (recursive && file.isDirectory && file.canRead()) {
                searchFilesRecursive(file, query, recursive, results, maxResults)
            }
        }
    }
    
    /**
     * 创建目录
     */
    fun createDirectory(path: String, name: String): Boolean {
        val newDir = File(path, name)
        return newDir.mkdirs()
    }
    
    /**
     * 创建文件
     */
    fun createFile(path: String, name: String): Boolean {
        val newFile = File(path, name)
        return try {
            newFile.createNewFile()
        } catch (e: Exception) {
            false
        }
    }
    
    /**
     * 删除文件/目录
     */
    fun delete(path: String): Boolean {
        val file = File(path)
        return if (file.isDirectory) {
            file.deleteRecursively()
        } else {
            file.delete()
        }
    }
    
    /**
     * 重命名
     */
    fun rename(path: String, newName: String): Boolean {
        val file = File(path)
        val newFile = File(file.parent, newName)
        return file.renameTo(newFile)
    }
    
    /**
     * 复制文件
     */
    fun copy(sourcePath: String, destPath: String): Boolean {
        return try {
            val source = File(sourcePath)
            val dest = File(destPath, source.name)
            
            if (source.isDirectory) {
                source.copyRecursively(dest, overwrite = true)
            } else {
                source.copyTo(dest, overwrite = true)
            }
            true
        } catch (e: Exception) {
            false
        }
    }
    
    /**
     * 移动文件
     */
    fun move(sourcePath: String, destPath: String): Boolean {
        return if (copy(sourcePath, destPath)) {
            delete(sourcePath)
        } else {
            false
        }
    }
    
    /**
     * 获取目录大小
     */
    fun getDirectorySize(path: String): Long {
        val directory = File(path)
        return if (directory.isDirectory) {
            directory.walkTopDown().filter { it.isFile }.map { it.length() }.sum()
        } else {
            directory.length()
        }
    }
    
    /**
     * 打开文件
     */
    fun openFile(path: String): Intent? {
        val file = File(path)
        if (!file.exists() || file.isDirectory) return null
        
        val uri = FileProvider.getUriForFile(
            context,
            "${context.packageName}.fileprovider",
            file
        )
        
        val mimeType = getMimeType(file) ?: "*/*"
        
        return Intent(Intent.ACTION_VIEW).apply {
            setDataAndType(uri, mimeType)
            addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
        }
    }
    
    /**
     * 分享文件
     */
    fun shareFile(path: String): Intent? {
        val file = File(path)
        if (!file.exists() || file.isDirectory) return null
        
        val uri = FileProvider.getUriForFile(
            context,
            "${context.packageName}.fileprovider",
            file
        )
        
        val mimeType = getMimeType(file) ?: "*/*"
        
        return Intent(Intent.ACTION_SEND).apply {
            type = mimeType
            putExtra(Intent.EXTRA_STREAM, uri)
            addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
        }
    }
    
    /**
     * 获取存储空间信息
     */
    fun getStorageInfo(): StorageInfo {
        val path = Environment.getExternalStorageDirectory()
        val stat = android.os.StatFs(path.absolutePath)
        
        val total = stat.blockSizeLong * stat.blockCountLong
        val free = stat.blockSizeLong * stat.availableBlocksLong
        val used = total - free
        
        return StorageInfo(
            total = total,
            used = used,
            free = free,
            usedPercent = (used * 100 / total).toInt()
        )
    }
    
    data class StorageInfo(
        val total: Long,
        val used: Long,
        val free: Long,
        val usedPercent: Int
    ) {
        val formattedTotal: String get() = formatFileSize(total)
        val formattedUsed: String get() = formatFileSize(used)
        val formattedFree: String get() = formatFileSize(free)
    }
    
    // 辅助方法
    private fun getMimeType(file: File): String? {
        val extension = file.extension.lowercase()
        return MimeTypeMap.getSingleton().getMimeTypeFromExtension(extension)
    }
}

// 文件大小格式化
fun formatFileSize(bytes: Long): String {
    return when {
        bytes >= 1_000_000_000 -> String.format("%.2f GB", bytes / 1_000_000_000.0)
        bytes >= 1_000_000 -> String.format("%.2f MB", bytes / 1_000_000.0)
        bytes >= 1_000 -> String.format("%.2f KB", bytes / 1_000.0)
        else -> "$bytes B"
    }
}

// 日期格式化
fun formatDate(timestamp: Long): String {
    val sdf = SimpleDateFormat("yyyy-MM-dd HH:mm", Locale.getDefault())
    return sdf.format(Date(timestamp))
}

// 文件图标判断
fun getFileIcon(extension: String, isDirectory: Boolean): FileManager.FileIcon {
    if (isDirectory) return FileManager.FileIcon.FOLDER
    
    return when (extension.lowercase()) {
        "jpg", "jpeg", "png", "gif", "bmp", "webp", "svg" -> FileManager.FileIcon.IMAGE
        "mp4", "mkv", "avi", "mov", "wmv", "flv", "webm" -> FileManager.FileIcon.VIDEO
        "mp3", "wav", "flac", "aac", "ogg", "m4a", "wma" -> FileManager.FileIcon.AUDIO
        "doc", "docx", "xls", "xlsx", "ppt", "pptx", "odt" -> FileManager.FileIcon.DOCUMENT
        "pdf" -> FileManager.FileIcon.PDF
        "txt", "md", "log", "ini", "cfg", "conf" -> FileManager.FileIcon.TEXT
        "zip", "rar", "7z", "tar", "gz", "bz2" -> FileManager.FileIcon.ARCHIVE
        "apk" -> FileManager.FileIcon.APK
        "java", "kt", "py", "js", "ts", "c", "cpp", "h", "xml", "json", "html", "css" -> FileManager.FileIcon.CODE
        else -> FileManager.FileIcon.UNKNOWN
    }
}
