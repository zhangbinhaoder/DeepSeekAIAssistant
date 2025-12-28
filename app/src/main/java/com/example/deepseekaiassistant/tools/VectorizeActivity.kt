package com.example.deepseekaiassistant.tools

import android.Manifest
import android.content.Intent
import android.content.pm.PackageManager
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.os.Environment
import android.provider.MediaStore
import android.util.Log
import android.view.View
import android.webkit.WebView
import android.widget.*
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.app.AppCompatActivity
import androidx.core.content.ContextCompat
import androidx.core.content.FileProvider
import androidx.lifecycle.lifecycleScope
import com.example.deepseekaiassistant.R
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File
import java.text.SimpleDateFormat
import java.util.*

/**
 * VectorizeActivity - 位图转矢量图图形化界面
 * 
 * 左边框: 导入位图图片
 * 右边框: 输出矢量图预览和格式选择
 */
class VectorizeActivity : AppCompatActivity() {
    
    companion object {
        private const val TAG = "VectorizeActivity"
    }
    
    private lateinit var vectorizer: VectorizerManager
    
    // UI 组件
    private lateinit var inputImageView: ImageView
    private lateinit var outputPreview: WebView
    private lateinit var importButton: Button
    private lateinit var convertButton: Button
    private lateinit var saveButton: Button
    private lateinit var formatSpinner: Spinner
    private lateinit var thresholdSeekBar: SeekBar
    private lateinit var thresholdLabel: TextView
    private lateinit var simplifySeekBar: SeekBar
    private lateinit var simplifyLabel: TextView
    private lateinit var progressBar: ProgressBar
    private lateinit var statusText: TextView
    private lateinit var inputInfoText: TextView
    private lateinit var outputInfoText: TextView
    private lateinit var invertSwitch: Switch
    private lateinit var fillModeSwitch: Switch
    
    // 状态
    private var currentBitmap: Bitmap? = null
    private var currentAnalysis: VectorizerManager.AnalysisResult? = null
    private var lastResult: VectorizerManager.VectorizeResult? = null
    private var selectedFormat = VectorizerManager.OutputFormat.SVG
    
    // 图片选择器
    private val imagePicker = registerForActivityResult(
        ActivityResultContracts.GetContent()
    ) { uri: Uri? ->
        uri?.let { loadImage(it) }
    }
    
    // 权限请求
    private val permissionLauncher = registerForActivityResult(
        ActivityResultContracts.RequestMultiplePermissions()
    ) { permissions ->
        if (permissions.all { it.value }) {
            lastResult?.let { saveVectorFile(it) }
        } else {
            Toast.makeText(this, "需要存储权限才能保存文件", Toast.LENGTH_SHORT).show()
        }
    }
    
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_vectorize)
        
        vectorizer = VectorizerManager.getInstance(this)
        
        initViews()
        setupListeners()
        setupFormatSpinner()
        
        Log.i(TAG, "[VECTORIZE] Activity created")
    }
    
    private fun initViews() {
        // 左侧 - 输入区域
        inputImageView = findViewById(R.id.inputImageView)
        importButton = findViewById(R.id.importButton)
        inputInfoText = findViewById(R.id.inputInfoText)
        
        // 参数设置
        thresholdSeekBar = findViewById(R.id.thresholdSeekBar)
        thresholdLabel = findViewById(R.id.thresholdLabel)
        simplifySeekBar = findViewById(R.id.simplifySeekBar)
        simplifyLabel = findViewById(R.id.simplifyLabel)
        invertSwitch = findViewById(R.id.invertSwitch)
        fillModeSwitch = findViewById(R.id.fillModeSwitch)
        
        // 右侧 - 输出区域
        outputPreview = findViewById(R.id.outputPreview)
        formatSpinner = findViewById(R.id.formatSpinner)
        convertButton = findViewById(R.id.convertButton)
        saveButton = findViewById(R.id.saveButton)
        outputInfoText = findViewById(R.id.outputInfoText)
        
        // 状态
        progressBar = findViewById(R.id.progressBar)
        statusText = findViewById(R.id.statusText)
        
        // 配置 WebView 用于 SVG 预览
        outputPreview.settings.apply {
            javaScriptEnabled = true
            useWideViewPort = true
            loadWithOverviewMode = true
            builtInZoomControls = true
            displayZoomControls = false
        }
        outputPreview.setBackgroundColor(0xFFFFFFFF.toInt())
        
        // 初始状态
        convertButton.isEnabled = false
        saveButton.isEnabled = false
        thresholdSeekBar.max = 255
        thresholdSeekBar.progress = 0  // 0 表示自动
        simplifySeekBar.max = 100
        simplifySeekBar.progress = 20  // 默认 2.0
        fillModeSwitch.isChecked = true
    }
    
    private fun setupListeners() {
        importButton.setOnClickListener {
            showImportOptions()
        }
        
        convertButton.setOnClickListener {
            startConversion()
        }
        
        saveButton.setOnClickListener {
            lastResult?.let { saveVectorFile(it) }
        }
        
        thresholdSeekBar.setOnSeekBarChangeListener(object : SeekBar.OnSeekBarChangeListener {
            override fun onProgressChanged(seekBar: SeekBar?, progress: Int, fromUser: Boolean) {
                thresholdLabel.text = if (progress == 0) "阈值: 自动" else "阈值: $progress"
            }
            override fun onStartTrackingTouch(seekBar: SeekBar?) {}
            override fun onStopTrackingTouch(seekBar: SeekBar?) {
                // 重新分析预览
                currentBitmap?.let { updatePreview(it) }
            }
        })
        
        simplifySeekBar.setOnSeekBarChangeListener(object : SeekBar.OnSeekBarChangeListener {
            override fun onProgressChanged(seekBar: SeekBar?, progress: Int, fromUser: Boolean) {
                val value = progress / 10.0
                simplifyLabel.text = "简化: %.1f".format(value)
            }
            override fun onStartTrackingTouch(seekBar: SeekBar?) {}
            override fun onStopTrackingTouch(seekBar: SeekBar?) {}
        })
        
        inputImageView.setOnClickListener {
            if (currentBitmap != null) {
                showImportOptions()
            }
        }
    }
    
    private fun setupFormatSpinner() {
        val formats = VectorizerManager.SUPPORTED_OUTPUT_FORMATS
        val adapter = ArrayAdapter(
            this,
            android.R.layout.simple_spinner_item,
            formats.map { it.displayName }
        )
        adapter.setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item)
        formatSpinner.adapter = adapter
        
        formatSpinner.onItemSelectedListener = object : AdapterView.OnItemSelectedListener {
            override fun onItemSelected(parent: AdapterView<*>?, view: View?, position: Int, id: Long) {
                selectedFormat = formats[position]
            }
            override fun onNothingSelected(parent: AdapterView<*>?) {}
        }
    }
    
    private fun showImportOptions() {
        val options = arrayOf(
            "从相册选择",
            "拍照",
            "从文件选择"
        )
        
        AlertDialog.Builder(this)
            .setTitle("导入图片")
            .setItems(options) { _, which ->
                when (which) {
                    0 -> imagePicker.launch("image/*")
                    1 -> openCamera()
                    2 -> openFilePicker()
                }
            }
            .show()
    }
    
    private fun openCamera() {
        // TODO: 实现相机拍照
        Toast.makeText(this, "相机功能开发中...", Toast.LENGTH_SHORT).show()
    }
    
    private fun openFilePicker() {
        imagePicker.launch("image/*")
    }
    
    private fun loadImage(uri: Uri) {
        lifecycleScope.launch {
            try {
                showProgress("正在加载图片...")
                
                val bitmap = withContext(Dispatchers.IO) {
                    vectorizer.loadBitmap(uri)
                }
                
                if (bitmap != null) {
                    currentBitmap = bitmap
                    inputImageView.setImageBitmap(bitmap)
                    convertButton.isEnabled = true
                    
                    // 分析图像
                    analyzeImage(bitmap)
                } else {
                    showError("无法加载图片")
                }
                
            } catch (e: Exception) {
                showError("加载失败: ${e.message}")
            } finally {
                hideProgress()
            }
        }
    }
    
    private fun analyzeImage(bitmap: Bitmap) {
        lifecycleScope.launch {
            try {
                val analysis = withContext(Dispatchers.Default) {
                    vectorizer.analyze(bitmap)
                }
                
                currentAnalysis = analysis
                
                // 更新显示信息
                inputInfoText.text = buildString {
                    append("尺寸: ${analysis.width} × ${analysis.height}\n")
                    append("自动阈值: ${analysis.autoThreshold}\n")
                    append("黑色占比: %.1f%%\n".format(analysis.blackRatio * 100))
                    append("预估路径: ~${analysis.estimatedPathCount} 条")
                }
                
                // 设置推荐阈值
                if (thresholdSeekBar.progress == 0) {
                    thresholdLabel.text = "阈值: 自动 (${analysis.autoThreshold})"
                }
                
                updatePreview(bitmap)
                
            } catch (e: Exception) {
                Log.e(TAG, "Analysis failed: ${e.message}")
            }
        }
    }
    
    private fun updatePreview(bitmap: Bitmap) {
        // 可以在这里添加二值化预览
        lifecycleScope.launch {
            val threshold = if (thresholdSeekBar.progress == 0) {
                currentAnalysis?.autoThreshold ?: 128
            } else {
                thresholdSeekBar.progress
            }
            
            // 显示预览 (二值化效果)
            statusText.text = "阈值预览: $threshold"
        }
    }
    
    private fun startConversion() {
        val bitmap = currentBitmap ?: return
        
        lifecycleScope.launch {
            try {
                showProgress("正在转换...")
                convertButton.isEnabled = false
                
                val params = VectorizerManager.VectorizeParams(
                    threshold = thresholdSeekBar.progress,
                    simplifyTolerance = simplifySeekBar.progress / 10.0,
                    turdSize = 2,
                    outputFormat = selectedFormat,
                    invertColors = invertSwitch.isChecked,
                    fillMode = fillModeSwitch.isChecked
                )
                
                val outputDir = File(cacheDir, "vectorize")
                val outputName = "vector_${System.currentTimeMillis()}"
                
                val callback = object : VectorizerManager.ProgressCallback {
                    override fun onProgress(stage: String, progress: Int) {
                        runOnUiThread {
                            statusText.text = stage
                            progressBar.progress = progress
                        }
                    }
                    
                    override fun onComplete(result: VectorizerManager.VectorizeResult) {
                        runOnUiThread {
                            handleConversionResult(result)
                        }
                    }
                    
                    override fun onError(error: String) {
                        runOnUiThread {
                            showError(error)
                        }
                    }
                }
                
                val result = vectorizer.vectorize(
                    bitmap, params, outputDir, outputName, callback
                )
                
                // callback 已处理结果
                
            } catch (e: Exception) {
                showError("转换失败: ${e.message}")
            } finally {
                hideProgress()
                convertButton.isEnabled = true
            }
        }
    }
    
    private fun handleConversionResult(result: VectorizerManager.VectorizeResult) {
        lastResult = result
        
        if (result.success) {
            saveButton.isEnabled = true
            
            // 更新输出信息
            outputInfoText.text = buildString {
                append("格式: ${result.outputFormat?.displayName}\n")
                append("用时: ${result.processingTimeMs}ms\n")
                result.outputPath?.let { path ->
                    val file = File(path)
                    append("大小: %.2f KB".format(file.length() / 1024.0))
                }
            }
            
            // 预览 SVG
            if (result.outputFormat == VectorizerManager.OutputFormat.SVG && result.svgContent != null) {
                val html = """
                    <html>
                    <head>
                        <style>
                            body { margin: 0; padding: 10px; background: #f5f5f5; }
                            svg { max-width: 100%; height: auto; background: white; box-shadow: 0 2px 4px rgba(0,0,0,0.2); }
                        </style>
                    </head>
                    <body>${result.svgContent}</body>
                    </html>
                """.trimIndent()
                outputPreview.loadDataWithBaseURL(null, html, "text/html", "UTF-8", null)
            } else {
                // 其他格式显示完成提示
                val html = """
                    <html>
                    <body style="display:flex;align-items:center;justify-content:center;height:100%;font-family:sans-serif;">
                        <div style="text-align:center;color:#666;">
                            <h2>✓ 转换完成</h2>
                            <p>格式: ${result.outputFormat?.extension?.uppercase()}</p>
                            <p>点击"保存"导出文件</p>
                        </div>
                    </body>
                    </html>
                """.trimIndent()
                outputPreview.loadDataWithBaseURL(null, html, "text/html", "UTF-8", null)
            }
            
            statusText.text = "转换完成!"
            Toast.makeText(this, "转换成功!", Toast.LENGTH_SHORT).show()
            
        } else {
            showError(result.errorMessage ?: "未知错误")
        }
    }
    
    private fun saveVectorFile(result: VectorizerManager.VectorizeResult) {
        // 检查权限
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.Q) {
            if (ContextCompat.checkSelfPermission(this, Manifest.permission.WRITE_EXTERNAL_STORAGE) 
                != PackageManager.PERMISSION_GRANTED) {
                permissionLauncher.launch(arrayOf(Manifest.permission.WRITE_EXTERNAL_STORAGE))
                return
            }
        }
        
        lifecycleScope.launch {
            try {
                val sourceFile = File(result.outputPath ?: return@launch)
                
                // 保存到下载目录
                val downloadsDir = Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_DOWNLOADS)
                val destFile = File(downloadsDir, "Vector_${SimpleDateFormat("yyyyMMdd_HHmmss", Locale.getDefault()).format(Date())}.${result.outputFormat?.extension}")
                
                withContext(Dispatchers.IO) {
                    sourceFile.copyTo(destFile, overwrite = true)
                }
                
                Toast.makeText(this@VectorizeActivity, "已保存到: ${destFile.absolutePath}", Toast.LENGTH_LONG).show()
                
                // 提供分享选项
                showShareDialog(destFile, result.outputFormat!!)
                
            } catch (e: Exception) {
                showError("保存失败: ${e.message}")
            }
        }
    }
    
    private fun showShareDialog(file: File, format: VectorizerManager.OutputFormat) {
        AlertDialog.Builder(this)
            .setTitle("保存成功")
            .setMessage("文件已保存到: ${file.absolutePath}")
            .setPositiveButton("分享") { _, _ ->
                shareFile(file, format)
            }
            .setNegativeButton("关闭", null)
            .show()
    }
    
    private fun shareFile(file: File, format: VectorizerManager.OutputFormat) {
        try {
            val uri = FileProvider.getUriForFile(
                this,
                "${packageName}.fileprovider",
                file
            )
            
            val intent = Intent(Intent.ACTION_SEND).apply {
                type = format.mimeType
                putExtra(Intent.EXTRA_STREAM, uri)
                addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
            }
            
            startActivity(Intent.createChooser(intent, "分享矢量图"))
            
        } catch (e: Exception) {
            Toast.makeText(this, "分享失败: ${e.message}", Toast.LENGTH_SHORT).show()
        }
    }
    
    private fun showProgress(message: String) {
        progressBar.visibility = View.VISIBLE
        progressBar.isIndeterminate = true
        statusText.text = message
    }
    
    private fun hideProgress() {
        progressBar.visibility = View.GONE
    }
    
    private fun showError(message: String) {
        statusText.text = "错误: $message"
        Toast.makeText(this, message, Toast.LENGTH_SHORT).show()
    }
    
    override fun onDestroy() {
        super.onDestroy()
        currentBitmap?.recycle()
        currentBitmap = null
    }
}
