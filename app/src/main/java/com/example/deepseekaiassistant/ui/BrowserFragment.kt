package com.example.deepseekaiassistant.ui

import android.Manifest
import android.annotation.SuppressLint
import android.app.Activity
import android.app.DownloadManager
import android.content.*
import android.content.pm.ActivityInfo
import android.graphics.Bitmap
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.os.Environment
import android.view.*
import android.view.inputmethod.EditorInfo
import android.webkit.*
import android.widget.*
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AlertDialog
import androidx.core.view.isVisible
import androidx.fragment.app.Fragment
import com.example.deepseekaiassistant.DiagnosticManager
import com.example.deepseekaiassistant.R
import com.example.deepseekaiassistant.databinding.FragmentBrowserBinding
import com.google.android.material.dialog.MaterialAlertDialogBuilder
import java.text.SimpleDateFormat
import java.util.*

/**
 * 完整功能浏览器
 * 
 * 支持功能：
 * - 视频/音频播放（支持B站、YouTube等）
 * - 全屏视频播放
 * - 书签和历史记录
 * - 多标签页
 * - UA切换（桌面/移动）
 * - 下载管理
 * - 隐私模式
 */
class BrowserFragment : Fragment() {
    
    private var _binding: FragmentBrowserBinding? = null
    private val binding get() = _binding!!
    
    // 默认主页
    private val defaultUrl = "https://m.bilibili.com"
    private val defaultHomeUrl = "https://www.bing.com"
    
    // 历史记录
    private val historyList = mutableListOf<HistoryItem>()
    
    // 书签
    private val bookmarkList = mutableListOf<BookmarkItem>()
    
    // 多标签页
    private val tabList = mutableListOf<TabInfo>()
    private var currentTabIndex = 0
    
    // 全屏视频相关
    private var customView: View? = null
    private var customViewCallback: WebChromeClient.CustomViewCallback? = null
    private var originalOrientation: Int = ActivityInfo.SCREEN_ORIENTATION_UNSPECIFIED
    
    // UA 模式
    private var isDesktopMode = false
    private val mobileUA by lazy { WebSettings.getDefaultUserAgent(requireContext()) }
    private val desktopUA = "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/120.0.0.0 Safari/537.36"
    
    // 隐私模式
    private var isPrivateMode = false
    
    // 文件选择
    private var filePathCallback: ValueCallback<Array<Uri>>? = null
    private val fileChooserLauncher = registerForActivityResult(
        ActivityResultContracts.GetMultipleContents()
    ) { uris ->
        filePathCallback?.onReceiveValue(uris.toTypedArray())
        filePathCallback = null
    }
    
    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        _binding = FragmentBrowserBinding.inflate(inflater, container, false)
        return binding.root
    }
    
    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        
        originalOrientation = activity?.requestedOrientation ?: ActivityInfo.SCREEN_ORIENTATION_UNSPECIFIED
        
        loadSavedData()
        setupWebView()
        setupControls()
        setupQuickLinks()
        
        // 创建第一个标签页
        if (tabList.isEmpty()) {
            createNewTab(defaultUrl)
        } else {
            loadUrl(tabList[currentTabIndex].url)
        }
        
        updateTabCount()
        
        DiagnosticManager.info("Browser", "浏览器初始化完成")
    }
    
    @SuppressLint("SetJavaScriptEnabled")
    private fun setupWebView() {
        binding.webView.apply {
            settings.apply {
                // === 基础设置 ===
                javaScriptEnabled = true
                domStorageEnabled = true
                databaseEnabled = true
                
                // === 缓存设置 ===
                cacheMode = if (isPrivateMode) WebSettings.LOAD_NO_CACHE else WebSettings.LOAD_DEFAULT
                
                // === 视图设置 ===
                useWideViewPort = true
                loadWithOverviewMode = true
                builtInZoomControls = true
                displayZoomControls = false
                setSupportZoom(true)
                
                // === 文件访问 ===
                allowFileAccess = true
                allowContentAccess = true
                
                // === 媒体设置（关键：支持视频播放） ===
                mediaPlaybackRequiresUserGesture = false  // 允许自动播放
                mixedContentMode = WebSettings.MIXED_CONTENT_ALWAYS_ALLOW
                
                // === 其他 ===
                setSupportMultipleWindows(true)
                javaScriptCanOpenWindowsAutomatically = true
                loadsImagesAutomatically = true
                blockNetworkImage = false
                
                // === 字体设置 ===
                textZoom = 100
                minimumFontSize = 8
                
                // === User Agent ===
                userAgentString = if (isDesktopMode) desktopUA else {
                    // 移除 wv 标识，让网站认为是正常浏览器
                    mobileUA.replace("; wv", "")
                        .replace("Version/4.0 ", "")
                }
                
                // === 地理位置 ===
                setGeolocationEnabled(true)
            }
            
            // 滚动条
            isScrollbarFadingEnabled = true
            scrollBarStyle = View.SCROLLBARS_INSIDE_OVERLAY
            
            // 硬件加速（视频播放需要）
            setLayerType(View.LAYER_TYPE_HARDWARE, null)
            
            // WebView 客户端
            webViewClient = createWebViewClient()
            webChromeClient = createWebChromeClient()
            
            // 下载监听
            setDownloadListener { url, userAgent, contentDisposition, mimetype, contentLength ->
                handleDownload(url, userAgent, contentDisposition, mimetype, contentLength)
            }
            
            // 长按菜单
            setOnLongClickListener { v ->
                val result = (v as WebView).hitTestResult
                when (result.type) {
                    WebView.HitTestResult.IMAGE_TYPE,
                    WebView.HitTestResult.SRC_IMAGE_ANCHOR_TYPE -> {
                        showImageContextMenu(result.extra)
                        true
                    }
                    WebView.HitTestResult.SRC_ANCHOR_TYPE,
                    WebView.HitTestResult.ANCHOR_TYPE -> {
                        showLinkContextMenu(result.extra)
                        true
                    }
                    else -> false
                }
            }
        }
    }
    
    private fun createWebViewClient(): WebViewClient {
        return object : WebViewClient() {
            override fun onPageStarted(view: WebView?, url: String?, favicon: Bitmap?) {
                super.onPageStarted(view, url, favicon)
                binding.progressBar.isVisible = true
                binding.etUrl.setText(url)
                binding.btnClearOrRefresh.setImageResource(android.R.drawable.ic_menu_close_clear_cancel)
                
                // 更新安全图标
                url?.let {
                    binding.ivSecure.isVisible = it.startsWith("https://")
                }
            }
            
            override fun onPageFinished(view: WebView?, url: String?) {
                super.onPageFinished(view, url)
                binding.progressBar.isVisible = false
                binding.btnClearOrRefresh.setImageResource(android.R.drawable.stat_notify_sync)
                updateNavigationButtons()
                
                // 添加到历史
                if (!isPrivateMode && url != null && view?.title != null) {
                    addToHistory(url, view.title ?: url)
                }
                
                // 更新当前标签页信息
                if (tabList.isNotEmpty() && currentTabIndex < tabList.size) {
                    tabList[currentTabIndex] = tabList[currentTabIndex].copy(
                        url = url ?: "",
                        title = view?.title ?: ""
                    )
                }
                
                // 注入 CSS 修复某些网站样式
                injectCustomCss(view)
            }
            
            override fun shouldOverrideUrlLoading(view: WebView?, request: WebResourceRequest?): Boolean {
                val url = request?.url?.toString() ?: return false
                
                // === 关键修复: 允许 WebView 内部处理的协议 ===
                // blob: 和 javascript: 是视频播放必需的
                if (url.startsWith("blob:") || url.startsWith("javascript:") || 
                    url.startsWith("data:") || url.startsWith("about:")) {
                    return false  // 让 WebView 自己处理
                }
                
                // 处理特殊协议
                when {
                    url.startsWith("tel:") || url.startsWith("mailto:") ||
                    url.startsWith("sms:") || url.startsWith("geo:") -> {
                        try {
                            startActivity(Intent(Intent.ACTION_VIEW, Uri.parse(url)))
                        } catch (e: Exception) {
                            Toast.makeText(requireContext(), "无法打开链接", Toast.LENGTH_SHORT).show()
                        }
                        return true
                    }
                    url.startsWith("intent:") -> {
                        try {
                            val intent = Intent.parseUri(url, Intent.URI_INTENT_SCHEME)
                            if (intent.resolveActivity(requireContext().packageManager) != null) {
                                startActivity(intent)
                            } else {
                                // 尝试打开应用商店
                                val packageName = intent.`package`
                                    ?: url.substringAfter("package=").substringBefore(";")
                                if (packageName.isNotEmpty()) {
                                    try {
                                        startActivity(Intent(Intent.ACTION_VIEW,
                                            Uri.parse("market://details?id=$packageName")))
                                    } catch (_: Exception) {
                                        Toast.makeText(requireContext(), "未安装该应用", Toast.LENGTH_SHORT).show()
                                    }
                                }
                            }
                        } catch (e: Exception) {
                            DiagnosticManager.error("Browser", "Intent解析失败", e.message)
                        }
                        return true
                    }
                    // 哔哩哔哩应用协议
                    url.startsWith("bilibili://") -> {
                        try {
                            startActivity(Intent(Intent.ACTION_VIEW, Uri.parse(url)))
                        } catch (e: Exception) {
                            // 未安装B站APP，返回让WebView继续处理
                            return false
                        }
                        return true
                    }
                    // 微信、支付宝等常见协议
                    url.startsWith("weixin://") || url.startsWith("alipays://") ||
                    url.startsWith("alipay://") || url.startsWith("tbopen://") -> {
                        try {
                            startActivity(Intent(Intent.ACTION_VIEW, Uri.parse(url)))
                        } catch (e: Exception) {
                            Toast.makeText(requireContext(), "未安装相关应用", Toast.LENGTH_SHORT).show()
                        }
                        return true
                    }
                }
                
                // 其他 http/https 链接让 WebView 自己处理
                return false
            }
            
            override fun onReceivedError(view: WebView?, request: WebResourceRequest?, error: WebResourceError?) {
                super.onReceivedError(view, request, error)
                if (request?.isForMainFrame == true) {
                    binding.progressBar.isVisible = false
                }
            }
            
            @Deprecated("Deprecated in Java")
            override fun onReceivedError(view: WebView?, errorCode: Int, description: String?, failingUrl: String?) {
                super.onReceivedError(view, errorCode, description, failingUrl)
                binding.progressBar.isVisible = false
            }
        }
    }
    
    private fun createWebChromeClient(): WebChromeClient {
        return object : WebChromeClient() {
            override fun onProgressChanged(view: WebView?, newProgress: Int) {
                binding.progressBar.progress = newProgress
                if (newProgress >= 100) {
                    binding.progressBar.isVisible = false
                }
            }
            
            override fun onReceivedTitle(view: WebView?, title: String?) {
                binding.tvTitle.text = title ?: "浏览器"
            }
            
            override fun onReceivedIcon(view: WebView?, icon: Bitmap?) {
                // 可以用于显示网站图标
            }
            
            // === 全屏视频支持（关键） ===
            override fun onShowCustomView(view: View?, callback: CustomViewCallback?) {
                if (customView != null) {
                    callback?.onCustomViewHidden()
                    return
                }
                
                customView = view
                customViewCallback = callback
                
                // 隐藏浏览器UI
                binding.browserContainer.isVisible = false
                
                // 显示全屏容器
                binding.fullscreenContainer.isVisible = true
                binding.fullscreenContainer.addView(view)
                
                // 设置横屏
                activity?.requestedOrientation = ActivityInfo.SCREEN_ORIENTATION_SENSOR_LANDSCAPE
                
                // 隐藏系统UI
                activity?.window?.let { window ->
                    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
                        window.insetsController?.let { controller ->
                            controller.hide(WindowInsets.Type.statusBars() or WindowInsets.Type.navigationBars())
                            controller.systemBarsBehavior = WindowInsetsController.BEHAVIOR_SHOW_TRANSIENT_BARS_BY_SWIPE
                        }
                    } else {
                        @Suppress("DEPRECATION")
                        window.decorView.systemUiVisibility = (
                            View.SYSTEM_UI_FLAG_FULLSCREEN or
                            View.SYSTEM_UI_FLAG_HIDE_NAVIGATION or
                            View.SYSTEM_UI_FLAG_IMMERSIVE_STICKY
                        )
                    }
                }
                
                DiagnosticManager.info("Browser", "进入全屏视频模式")
            }
            
            override fun onHideCustomView() {
                if (customView == null) return
                
                // 恢复方向
                activity?.requestedOrientation = originalOrientation
                
                // 恢复系统UI
                activity?.window?.let { window ->
                    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
                        window.insetsController?.show(WindowInsets.Type.statusBars() or WindowInsets.Type.navigationBars())
                    } else {
                        @Suppress("DEPRECATION")
                        window.decorView.systemUiVisibility = View.SYSTEM_UI_FLAG_VISIBLE
                    }
                }
                
                // 移除全屏视图
                binding.fullscreenContainer.removeView(customView)
                binding.fullscreenContainer.isVisible = false
                
                // 显示浏览器UI
                binding.browserContainer.isVisible = true
                
                customViewCallback?.onCustomViewHidden()
                customView = null
                customViewCallback = null
                
                DiagnosticManager.info("Browser", "退出全屏视频模式")
            }
            
            // === 文件选择 ===
            override fun onShowFileChooser(
                webView: WebView?,
                filePathCallback: ValueCallback<Array<Uri>>?,
                fileChooserParams: FileChooserParams?
            ): Boolean {
                this@BrowserFragment.filePathCallback?.onReceiveValue(null)
                this@BrowserFragment.filePathCallback = filePathCallback
                
                try {
                    fileChooserLauncher.launch("*/*")
                } catch (e: Exception) {
                    Toast.makeText(requireContext(), "无法打开文件选择器", Toast.LENGTH_SHORT).show()
                    return false
                }
                return true
            }
            
            // === 地理位置权限 ===
            override fun onGeolocationPermissionsShowPrompt(
                origin: String?,
                callback: GeolocationPermissions.Callback?
            ) {
                MaterialAlertDialogBuilder(requireContext())
                    .setTitle("位置权限")
                    .setMessage("\"$origin\" 请求获取您的位置信息")
                    .setPositiveButton("允许") { _, _ ->
                        callback?.invoke(origin, true, true)
                    }
                    .setNegativeButton("拒绝") { _, _ ->
                        callback?.invoke(origin, false, false)
                    }
                    .show()
            }
            
            // === JavaScript 对话框 ===
            override fun onJsAlert(view: WebView?, url: String?, message: String?, result: JsResult?): Boolean {
                MaterialAlertDialogBuilder(requireContext())
                    .setTitle("提示")
                    .setMessage(message)
                    .setPositiveButton("确定") { _, _ -> result?.confirm() }
                    .setOnCancelListener { result?.cancel() }
                    .show()
                return true
            }
            
            override fun onJsConfirm(view: WebView?, url: String?, message: String?, result: JsResult?): Boolean {
                MaterialAlertDialogBuilder(requireContext())
                    .setTitle("确认")
                    .setMessage(message)
                    .setPositiveButton("确定") { _, _ -> result?.confirm() }
                    .setNegativeButton("取消") { _, _ -> result?.cancel() }
                    .setOnCancelListener { result?.cancel() }
                    .show()
                return true
            }
            
            override fun onJsPrompt(
                view: WebView?,
                url: String?,
                message: String?,
                defaultValue: String?,
                result: JsPromptResult?
            ): Boolean {
                val editText = EditText(requireContext()).apply {
                    setText(defaultValue)
                }
                MaterialAlertDialogBuilder(requireContext())
                    .setTitle(message)
                    .setView(editText)
                    .setPositiveButton("确定") { _, _ -> result?.confirm(editText.text.toString()) }
                    .setNegativeButton("取消") { _, _ -> result?.cancel() }
                    .setOnCancelListener { result?.cancel() }
                    .show()
                return true
            }
            
            // === 控制台日志 ===
            override fun onConsoleMessage(consoleMessage: ConsoleMessage?): Boolean {
                consoleMessage?.let {
                    DiagnosticManager.info("BrowserConsole", 
                        "[${it.messageLevel()}] ${it.message()} at ${it.sourceId()}:${it.lineNumber()}")
                }
                return true
            }
        }
    }
    
    private fun setupControls() {
        // 后退
        binding.btnBack.setOnClickListener {
            if (binding.webView.canGoBack()) {
                binding.webView.goBack()
            }
        }
        
        // 前进
        binding.btnForward.setOnClickListener {
            if (binding.webView.canGoForward()) {
                binding.webView.goForward()
            }
        }
        
        // 主页
        binding.btnHome.setOnClickListener {
            loadUrl(defaultHomeUrl)
        }
        
        // 历史
        binding.btnHistory.setOnClickListener {
            showHistoryDialog()
        }
        
        // 分享
        binding.btnShare.setOnClickListener {
            shareCurrentPage()
        }
        
        // 刷新/停止
        binding.btnClearOrRefresh.setOnClickListener {
            if (binding.progressBar.isVisible) {
                binding.webView.stopLoading()
            } else {
                binding.webView.reload()
            }
        }
        
        // 书签
        binding.btnBookmark.setOnClickListener {
            showBookmarkDialog()
        }
        
        // 标签页
        binding.btnTabs.setOnClickListener {
            showTabsDialog()
        }
        
        // 菜单
        binding.btnMenu.setOnClickListener {
            showMainMenu()
        }
        
        // 地址栏回车
        binding.etUrl.setOnEditorActionListener { _, actionId, _ ->
            if (actionId == EditorInfo.IME_ACTION_GO) {
                val input = binding.etUrl.text.toString().trim()
                if (input.isNotEmpty()) {
                    loadUrl(formatUrl(input))
                }
                true
            } else {
                false
            }
        }
        
        // 地址栏获得焦点时全选
        binding.etUrl.setOnFocusChangeListener { _, hasFocus ->
            if (hasFocus) {
                binding.etUrl.selectAll()
            }
        }
    }
    
    private fun setupQuickLinks() {
        binding.chipBilibili.setOnClickListener { loadUrl("https://m.bilibili.com") }
        binding.chipBaidu.setOnClickListener { loadUrl("https://www.baidu.com") }
        binding.chipGoogle.setOnClickListener { loadUrl("https://www.google.com") }
        binding.chipYoutube.setOnClickListener { loadUrl("https://m.youtube.com") }
        binding.chipWeibo.setOnClickListener { loadUrl("https://m.weibo.cn") }
        binding.chipZhihu.setOnClickListener { loadUrl("https://www.zhihu.com") }
        binding.chipTaobao.setOnClickListener { loadUrl("https://m.taobao.com") }
        binding.chipJd.setOnClickListener { loadUrl("https://m.jd.com") }
        binding.chipGithub.setOnClickListener { loadUrl("https://github.com") }
    }
    
    private fun loadUrl(url: String) {
        binding.webView.loadUrl(url)
    }
    
    private fun formatUrl(input: String): String {
        return when {
            input.startsWith("http://") || input.startsWith("https://") -> input
            input.startsWith("file://") -> input
            input.contains(".") && !input.contains(" ") -> "https://$input"
            else -> "https://www.bing.com/search?q=${Uri.encode(input)}"
        }
    }
    
    private fun updateNavigationButtons() {
        binding.btnBack.isEnabled = binding.webView.canGoBack()
        binding.btnForward.isEnabled = binding.webView.canGoForward()
        binding.btnBack.alpha = if (binding.webView.canGoBack()) 1f else 0.4f
        binding.btnForward.alpha = if (binding.webView.canGoForward()) 1f else 0.4f
    }
    
    private fun updateTabCount() {
        binding.tvTabCount.text = tabList.size.toString()
    }
    
    // ==================== 标签页管理 ====================
    
    private fun createNewTab(url: String) {
        tabList.add(TabInfo(url = url, title = "新标签页"))
        currentTabIndex = tabList.size - 1
        loadUrl(url)
        updateTabCount()
    }
    
    private fun switchToTab(index: Int) {
        if (index in 0 until tabList.size) {
            currentTabIndex = index
            loadUrl(tabList[index].url)
        }
    }
    
    private fun closeTab(index: Int) {
        if (tabList.size <= 1) {
            Toast.makeText(requireContext(), "至少保留一个标签页", Toast.LENGTH_SHORT).show()
            return
        }
        
        tabList.removeAt(index)
        if (currentTabIndex >= tabList.size) {
            currentTabIndex = tabList.size - 1
        }
        switchToTab(currentTabIndex)
        updateTabCount()
    }
    
    private fun showTabsDialog() {
        val items = tabList.mapIndexed { index, tab ->
            "${if (index == currentTabIndex) "● " else "○ "}${tab.title.ifEmpty { tab.url }}"
        }.toTypedArray()
        
        MaterialAlertDialogBuilder(requireContext())
            .setTitle("标签页 (${tabList.size})")
            .setItems(items) { _, index ->
                switchToTab(index)
            }
            .setPositiveButton("新建标签页") { _, _ ->
                createNewTab(defaultHomeUrl)
            }
            .setNeutralButton("关闭当前") { _, _ ->
                closeTab(currentTabIndex)
            }
            .setNegativeButton("取消", null)
            .show()
    }
    
    // ==================== 书签管理 ====================
    
    private fun showBookmarkDialog() {
        val currentUrl = binding.webView.url ?: return
        val currentTitle = binding.webView.title ?: currentUrl
        
        val isBookmarked = bookmarkList.any { it.url == currentUrl }
        
        val items = if (isBookmarked) {
            arrayOf("移除书签", "查看所有书签")
        } else {
            arrayOf("添加书签", "查看所有书签")
        }
        
        MaterialAlertDialogBuilder(requireContext())
            .setTitle("书签")
            .setItems(items) { _, index ->
                when (index) {
                    0 -> {
                        if (isBookmarked) {
                            bookmarkList.removeAll { it.url == currentUrl }
                            Toast.makeText(requireContext(), "已移除书签", Toast.LENGTH_SHORT).show()
                        } else {
                            bookmarkList.add(BookmarkItem(title = currentTitle, url = currentUrl))
                            Toast.makeText(requireContext(), "已添加书签", Toast.LENGTH_SHORT).show()
                        }
                        saveData()
                    }
                    1 -> showAllBookmarks()
                }
            }
            .setNegativeButton("取消", null)
            .show()
    }
    
    private fun showAllBookmarks() {
        if (bookmarkList.isEmpty()) {
            Toast.makeText(requireContext(), "暂无书签", Toast.LENGTH_SHORT).show()
            return
        }
        
        val items = bookmarkList.map { it.title }.toTypedArray()
        
        MaterialAlertDialogBuilder(requireContext())
            .setTitle("书签列表")
            .setItems(items) { _, index ->
                loadUrl(bookmarkList[index].url)
            }
            .setNegativeButton("关闭", null)
            .show()
    }
    
    // ==================== 历史记录 ====================
    
    private fun addToHistory(url: String, title: String) {
        // 避免重复
        historyList.removeAll { it.url == url }
        
        historyList.add(0, HistoryItem(
            title = title,
            url = url,
            timestamp = System.currentTimeMillis()
        ))
        
        // 限制历史数量
        while (historyList.size > 200) {
            historyList.removeAt(historyList.lastIndex)
        }
        
        saveData()
    }
    
    private fun showHistoryDialog() {
        if (historyList.isEmpty()) {
            Toast.makeText(requireContext(), "暂无历史记录", Toast.LENGTH_SHORT).show()
            return
        }
        
        val dateFormat = SimpleDateFormat("MM-dd HH:mm", Locale.getDefault())
        val items = historyList.take(50).map { 
            "${it.title}\n${dateFormat.format(Date(it.timestamp))}"
        }.toTypedArray()
        
        MaterialAlertDialogBuilder(requireContext())
            .setTitle("历史记录")
            .setItems(items) { _, index ->
                loadUrl(historyList[index].url)
            }
            .setNeutralButton("清空历史") { _, _ ->
                historyList.clear()
                saveData()
                Toast.makeText(requireContext(), "历史已清空", Toast.LENGTH_SHORT).show()
            }
            .setNegativeButton("关闭", null)
            .show()
    }
    
    // ==================== 主菜单 ====================
    
    private fun showMainMenu() {
        val items = arrayOf(
            if (isDesktopMode) "✓ 桌面版网站" else "○ 桌面版网站",
            if (isPrivateMode) "✓ 隐私模式" else "○ 隐私模式",
            "查找页面内容",
            "添加到主屏幕",
            "在外部浏览器打开",
            "页面信息",
            "清除浏览数据"
        )
        
        MaterialAlertDialogBuilder(requireContext())
            .setTitle("菜单")
            .setItems(items) { _, index ->
                when (index) {
                    0 -> toggleDesktopMode()
                    1 -> togglePrivateMode()
                    2 -> showFindInPage()
                    3 -> addToHomeScreen()
                    4 -> openInExternalBrowser()
                    5 -> showPageInfo()
                    6 -> clearBrowsingData()
                }
            }
            .setNegativeButton("取消", null)
            .show()
    }
    
    private fun toggleDesktopMode() {
        isDesktopMode = !isDesktopMode
        binding.webView.settings.userAgentString = if (isDesktopMode) desktopUA else mobileUA
        binding.webView.reload()
        
        Toast.makeText(requireContext(), 
            if (isDesktopMode) "已切换到桌面版" else "已切换到移动版", 
            Toast.LENGTH_SHORT).show()
    }
    
    private fun togglePrivateMode() {
        isPrivateMode = !isPrivateMode
        binding.webView.settings.cacheMode = 
            if (isPrivateMode) WebSettings.LOAD_NO_CACHE else WebSettings.LOAD_DEFAULT
        
        Toast.makeText(requireContext(), 
            if (isPrivateMode) "隐私模式已开启" else "隐私模式已关闭", 
            Toast.LENGTH_SHORT).show()
    }
    
    private fun showFindInPage() {
        val editText = EditText(requireContext()).apply {
            hint = "搜索内容"
        }
        
        MaterialAlertDialogBuilder(requireContext())
            .setTitle("页面内搜索")
            .setView(editText)
            .setPositiveButton("搜索") { _, _ ->
                val query = editText.text.toString()
                if (query.isNotEmpty()) {
                    binding.webView.findAllAsync(query)
                }
            }
            .setNegativeButton("取消", null)
            .show()
    }
    
    private fun addToHomeScreen() {
        Toast.makeText(requireContext(), "功能开发中", Toast.LENGTH_SHORT).show()
    }
    
    private fun openInExternalBrowser() {
        val url = binding.webView.url ?: return
        try {
            startActivity(Intent(Intent.ACTION_VIEW, Uri.parse(url)))
        } catch (e: Exception) {
            Toast.makeText(requireContext(), "无法打开外部浏览器", Toast.LENGTH_SHORT).show()
        }
    }
    
    private fun showPageInfo() {
        val url = binding.webView.url ?: "未知"
        val title = binding.webView.title ?: "未知"
        val isSecure = url.startsWith("https://")
        
        MaterialAlertDialogBuilder(requireContext())
            .setTitle("页面信息")
            .setMessage("""
                标题: $title
                
                网址: $url
                
                安全连接: ${if (isSecure) "是 (HTTPS)" else "否 (HTTP)"}
            """.trimIndent())
            .setPositiveButton("确定", null)
            .setNeutralButton("复制网址") { _, _ ->
                val clipboard = requireContext().getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
                clipboard.setPrimaryClip(ClipData.newPlainText("URL", url))
                Toast.makeText(requireContext(), "网址已复制", Toast.LENGTH_SHORT).show()
            }
            .show()
    }
    
    private fun clearBrowsingData() {
        MaterialAlertDialogBuilder(requireContext())
            .setTitle("清除浏览数据")
            .setMessage("确定要清除所有浏览数据吗？\n\n这将清除缓存、Cookie和历史记录。")
            .setPositiveButton("清除") { _, _ ->
                binding.webView.clearCache(true)
                binding.webView.clearHistory()
                binding.webView.clearFormData()
                CookieManager.getInstance().removeAllCookies(null)
                historyList.clear()
                saveData()
                Toast.makeText(requireContext(), "浏览数据已清除", Toast.LENGTH_SHORT).show()
            }
            .setNegativeButton("取消", null)
            .show()
    }
    
    private fun shareCurrentPage() {
        val url = binding.webView.url ?: return
        val title = binding.webView.title ?: ""
        
        val intent = Intent(Intent.ACTION_SEND).apply {
            type = "text/plain"
            putExtra(Intent.EXTRA_SUBJECT, title)
            putExtra(Intent.EXTRA_TEXT, "$title\n$url")
        }
        startActivity(Intent.createChooser(intent, "分享链接"))
    }
    
    // ==================== 下载处理 ====================
    
    private fun handleDownload(
        url: String,
        userAgent: String,
        contentDisposition: String,
        mimetype: String,
        contentLength: Long
    ) {
        val fileName = URLUtil.guessFileName(url, contentDisposition, mimetype)
        
        MaterialAlertDialogBuilder(requireContext())
            .setTitle("下载文件")
            .setMessage("是否下载: $fileName")
            .setPositiveButton("下载") { _, _ ->
                try {
                    val request = DownloadManager.Request(Uri.parse(url)).apply {
                        setMimeType(mimetype)
                        addRequestHeader("User-Agent", userAgent)
                        setTitle(fileName)
                        setDescription("正在下载...")
                        setNotificationVisibility(DownloadManager.Request.VISIBILITY_VISIBLE_NOTIFY_COMPLETED)
                        setDestinationInExternalPublicDir(Environment.DIRECTORY_DOWNLOADS, fileName)
                    }
                    
                    val dm = requireContext().getSystemService(Context.DOWNLOAD_SERVICE) as DownloadManager
                    dm.enqueue(request)
                    
                    Toast.makeText(requireContext(), "开始下载: $fileName", Toast.LENGTH_SHORT).show()
                } catch (e: Exception) {
                    Toast.makeText(requireContext(), "下载失败: ${e.message}", Toast.LENGTH_SHORT).show()
                }
            }
            .setNegativeButton("取消", null)
            .show()
    }
    
    // ==================== 长按菜单 ====================
    
    private fun showImageContextMenu(imageUrl: String?) {
        imageUrl ?: return
        
        MaterialAlertDialogBuilder(requireContext())
            .setTitle("图片")
            .setItems(arrayOf("保存图片", "复制图片链接", "在新标签页打开")) { _, index ->
                when (index) {
                    0 -> handleDownload(imageUrl, mobileUA, "", "image/*", 0)
                    1 -> {
                        val clipboard = requireContext().getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
                        clipboard.setPrimaryClip(ClipData.newPlainText("ImageURL", imageUrl))
                        Toast.makeText(requireContext(), "链接已复制", Toast.LENGTH_SHORT).show()
                    }
                    2 -> createNewTab(imageUrl)
                }
            }
            .show()
    }
    
    private fun showLinkContextMenu(linkUrl: String?) {
        linkUrl ?: return
        
        MaterialAlertDialogBuilder(requireContext())
            .setTitle("链接")
            .setItems(arrayOf("在新标签页打开", "复制链接", "分享链接")) { _, index ->
                when (index) {
                    0 -> createNewTab(linkUrl)
                    1 -> {
                        val clipboard = requireContext().getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
                        clipboard.setPrimaryClip(ClipData.newPlainText("URL", linkUrl))
                        Toast.makeText(requireContext(), "链接已复制", Toast.LENGTH_SHORT).show()
                    }
                    2 -> {
                        val intent = Intent(Intent.ACTION_SEND).apply {
                            type = "text/plain"
                            putExtra(Intent.EXTRA_TEXT, linkUrl)
                        }
                        startActivity(Intent.createChooser(intent, "分享链接"))
                    }
                }
            }
            .show()
    }
    
    // ==================== 辅助功能 ====================
    
    private fun injectCustomCss(webView: WebView?) {
        // 修复某些网站的样式问题
        webView?.evaluateJavascript("""
            (function() {
                // 确保视频可以全屏
                var videos = document.getElementsByTagName('video');
                for (var i = 0; i < videos.length; i++) {
                    videos[i].setAttribute('playsinline', '');
                    videos[i].setAttribute('webkit-playsinline', '');
                }
            })();
        """.trimIndent(), null)
    }
    
    // ==================== 数据持久化 ====================
    
    private fun loadSavedData() {
        try {
            val prefs = requireContext().getSharedPreferences("browser_data", Context.MODE_PRIVATE)
            
            // 加载书签
            val bookmarksJson = prefs.getString("bookmarks", null)
            if (!bookmarksJson.isNullOrEmpty()) {
                // 简单解析
                bookmarksJson.split("|||").filter { it.isNotEmpty() }.forEach { item ->
                    val parts = item.split("^^^")
                    if (parts.size >= 2) {
                        bookmarkList.add(BookmarkItem(parts[0], parts[1]))
                    }
                }
            }
            
            // 加载历史
            val historyJson = prefs.getString("history", null)
            if (!historyJson.isNullOrEmpty()) {
                historyJson.split("|||").filter { it.isNotEmpty() }.forEach { item ->
                    val parts = item.split("^^^")
                    if (parts.size >= 3) {
                        historyList.add(HistoryItem(parts[0], parts[1], parts[2].toLongOrNull() ?: 0))
                    }
                }
            }
        } catch (e: Exception) {
            DiagnosticManager.error("Browser", "加载数据失败", e.message)
        }
    }
    
    private fun saveData() {
        try {
            val prefs = requireContext().getSharedPreferences("browser_data", Context.MODE_PRIVATE)
            
            // 保存书签
            val bookmarksJson = bookmarkList.joinToString("|||") { "${it.title}^^^${it.url}" }
            prefs.edit().putString("bookmarks", bookmarksJson).apply()
            
            // 保存历史（只保存最近50条）
            val historyJson = historyList.take(50).joinToString("|||") { 
                "${it.title}^^^${it.url}^^^${it.timestamp}" 
            }
            prefs.edit().putString("history", historyJson).apply()
        } catch (e: Exception) {
            DiagnosticManager.error("Browser", "保存数据失败", e.message)
        }
    }
    
    // ==================== 生命周期 ====================
    
    fun canGoBack(): Boolean {
        return if (customView != null) {
            true
        } else {
            binding.webView.canGoBack()
        }
    }
    
    fun goBack(): Boolean {
        return if (customView != null) {
            // 先退出全屏
            (binding.webView.webChromeClient as? WebChromeClient)?.onHideCustomView()
            true
        } else if (binding.webView.canGoBack()) {
            binding.webView.goBack()
            true
        } else {
            false
        }
    }
    
    override fun onPause() {
        super.onPause()
        binding.webView.onPause()
        saveData()
    }
    
    override fun onResume() {
        super.onResume()
        binding.webView.onResume()
    }
    
    override fun onDestroyView() {
        super.onDestroyView()
        
        // 退出全屏
        customView?.let {
            binding.fullscreenContainer.removeView(it)
            customViewCallback?.onCustomViewHidden()
        }
        
        binding.webView.apply {
            stopLoading()
            clearHistory()
            removeAllViews()
            destroy()
        }
        
        _binding = null
    }
    
    companion object {
        fun newInstance() = BrowserFragment()
    }
    
    // ==================== 数据类 ====================
    
    data class TabInfo(
        val url: String,
        val title: String = ""
    )
    
    data class BookmarkItem(
        val title: String,
        val url: String
    )
    
    data class HistoryItem(
        val title: String,
        val url: String,
        val timestamp: Long
    )
}
