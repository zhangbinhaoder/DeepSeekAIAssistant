package com.example.deepseekaiassistant.termux

import android.os.Bundle
import android.view.MenuItem
import android.view.View
import android.widget.*
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.app.AppCompatActivity
import androidx.cardview.widget.CardView
import androidx.core.content.ContextCompat
import androidx.recyclerview.widget.GridLayoutManager
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.example.deepseekaiassistant.DiagnosticManager
import com.example.deepseekaiassistant.R
import com.google.android.material.chip.Chip
import com.google.android.material.chip.ChipGroup
import com.google.android.material.floatingactionbutton.FloatingActionButton
import com.google.android.material.tabs.TabLayout
import com.google.android.material.textfield.TextInputEditText
import com.google.android.material.textfield.TextInputLayout

/**
 * Termux 集成功能 Activity
 * 
 * 功能页面：
 * 1. 状态检测 - 检查 Termux 及插件安装状态
 * 2. Termux-API - 设备硬件访问功能
 * 3. Termux-X11 - 图形化应用支持
 * 4. 脚本管理 - 预置脚本和自定义脚本
 * 5. 命令终端 - 快速执行命令
 */
class TermuxActivity : AppCompatActivity() {
    
    private lateinit var tabLayout: TabLayout
    private lateinit var contentContainer: FrameLayout
    
    private var currentStatus: TermuxIntegration.TermuxStatus? = null
    
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_termux)
        
        setupToolbar()
        setupTabs()
        
        // 检查 Termux 状态
        refreshStatus()
    }
    
    private fun setupToolbar() {
        supportActionBar?.apply {
            title = "Termux 集成"
            setDisplayHomeAsUpEnabled(true)
        }
    }
    
    private fun setupTabs() {
        tabLayout = findViewById(R.id.tabLayout)
        contentContainer = findViewById(R.id.contentContainer)
        
        tabLayout.addTab(tabLayout.newTab().setText("状态"))
        tabLayout.addTab(tabLayout.newTab().setText("API"))
        tabLayout.addTab(tabLayout.newTab().setText("X11"))
        tabLayout.addTab(tabLayout.newTab().setText("脚本"))
        tabLayout.addTab(tabLayout.newTab().setText("终端"))
        
        tabLayout.addOnTabSelectedListener(object : TabLayout.OnTabSelectedListener {
            override fun onTabSelected(tab: TabLayout.Tab?) {
                when (tab?.position) {
                    0 -> showStatusPage()
                    1 -> showApiPage()
                    2 -> showX11Page()
                    3 -> showScriptsPage()
                    4 -> showTerminalPage()
                }
            }
            override fun onTabUnselected(tab: TabLayout.Tab?) {}
            override fun onTabReselected(tab: TabLayout.Tab?) {}
        })
        
        // 默认显示状态页
        showStatusPage()
    }
    
    private fun refreshStatus() {
        currentStatus = TermuxIntegration.checkTermuxStatus(this)
        DiagnosticManager.info("TermuxActivity", "Termux 状态", 
            "Termux: ${currentStatus?.termuxInstalled}, API: ${currentStatus?.apiInstalled}, X11: ${currentStatus?.x11Installed}")
    }
    
    // ==================== 状态页面 ====================
    
    private fun showStatusPage() {
        contentContainer.removeAllViews()
        val view = layoutInflater.inflate(R.layout.termux_page_status, contentContainer, false)
        contentContainer.addView(view)
        
        val status = currentStatus ?: TermuxIntegration.checkTermuxStatus(this)
        
        // 设置各项状态
        fun setStatus(viewId: Int, installed: Boolean, name: String) {
            val item = view.findViewById<View>(viewId)
            val icon = item.findViewById<ImageView>(R.id.statusIcon)
            val text = item.findViewById<TextView>(R.id.statusText)
            val btn = item.findViewById<Button>(R.id.actionButton)
            
            if (installed) {
                icon.setImageResource(android.R.drawable.presence_online)
                icon.setColorFilter(ContextCompat.getColor(this, android.R.color.holo_green_dark))
                text.text = "$name ✓ 已安装"
                btn.text = "打开"
                btn.setOnClickListener {
                    when (name) {
                        "Termux" -> TermuxIntegration.launchTermux(this)
                        "Termux:X11" -> TermuxIntegration.launchTermuxX11(this)
                        else -> TermuxIntegration.launchTermux(this)
                    }
                }
            } else {
                icon.setImageResource(android.R.drawable.presence_offline)
                icon.setColorFilter(ContextCompat.getColor(this, android.R.color.holo_red_dark))
                text.text = "$name ✗ 未安装"
                btn.text = "安装"
                btn.setOnClickListener {
                    showInstallDialog(name)
                }
            }
        }
        
        // 主程序状态
        view.findViewById<TextView>(R.id.tvTermuxVersion)?.text = 
            if (status.termuxInstalled) "版本: ${status.termuxVersion ?: "未知"}" else "未安装"
        
        setStatus(R.id.statusTermux, status.termuxInstalled, "Termux")
        setStatus(R.id.statusApi, status.apiInstalled, "Termux:API")
        setStatus(R.id.statusX11, status.x11Installed, "Termux:X11")
        setStatus(R.id.statusStyling, status.stylingInstalled, "Termux:Styling")
        setStatus(R.id.statusBoot, status.bootInstalled, "Termux:Boot")
        setStatus(R.id.statusWidget, status.widgetInstalled, "Termux:Widget")
        setStatus(R.id.statusFloat, status.floatInstalled, "Termux:Float")
        
        // 刷新按钮
        view.findViewById<Button>(R.id.btnRefresh)?.setOnClickListener {
            refreshStatus()
            showStatusPage()
            Toast.makeText(this, "状态已刷新", Toast.LENGTH_SHORT).show()
        }
        
        // 安装指南按钮
        view.findViewById<Button>(R.id.btnInstallGuide)?.setOnClickListener {
            showInstallGuideDialog()
        }
    }
    
    private fun showInstallDialog(appName: String) {
        val packageName = when (appName) {
            "Termux" -> TermuxIntegration.TERMUX_PACKAGE
            "Termux:API" -> TermuxIntegration.TERMUX_API_PACKAGE
            "Termux:X11" -> TermuxIntegration.TERMUX_X11_PACKAGE
            "Termux:Styling" -> TermuxIntegration.TERMUX_STYLING_PACKAGE
            "Termux:Boot" -> TermuxIntegration.TERMUX_BOOT_PACKAGE
            "Termux:Widget" -> TermuxIntegration.TERMUX_WIDGET_PACKAGE
            "Termux:Float" -> TermuxIntegration.TERMUX_FLOAT_PACKAGE
            else -> TermuxIntegration.TERMUX_PACKAGE
        }
        
        AlertDialog.Builder(this)
            .setTitle("安装 $appName")
            .setMessage("请从以下渠道下载安装：\n\n• F-Droid（推荐）\n• GitHub Releases\n\n⚠️ 请勿使用 Google Play 版本")
            .setPositiveButton("F-Droid") { _, _ ->
                TermuxIntegration.openFDroidDownload(this, packageName)
            }
            .setNeutralButton("GitHub") { _, _ ->
                val repo = when (appName) {
                    "Termux:X11" -> "termux/termux-x11"
                    else -> "termux/termux-app"
                }
                TermuxIntegration.openGitHubReleases(this, repo)
            }
            .setNegativeButton("取消", null)
            .show()
    }
    
    private fun showInstallGuideDialog() {
        AlertDialog.Builder(this)
            .setTitle("Termux 安装指南")
            .setMessage(TermuxIntegration.getInstallGuide())
            .setPositiveButton("知道了", null)
            .show()
    }
    
    // ==================== API 页面 ====================
    
    private fun showApiPage() {
        contentContainer.removeAllViews()
        val view = layoutInflater.inflate(R.layout.termux_page_api, contentContainer, false)
        contentContainer.addView(view)
        
        val chipGroup = view.findViewById<ChipGroup>(R.id.categoryChips)
        val recyclerView = view.findViewById<RecyclerView>(R.id.apiList)
        
        // 添加分类 Chip
        TermuxIntegration.ApiCategory.values().forEach { category ->
            val chip = Chip(this).apply {
                text = "${category.icon} ${category.displayName}"
                isCheckable = true
                tag = category
            }
            chipGroup.addView(chip)
        }
        
        // 默认选中第一个
        (chipGroup.getChildAt(0) as? Chip)?.isChecked = true
        
        recyclerView.layoutManager = LinearLayoutManager(this)
        
        fun updateList(category: TermuxIntegration.ApiCategory) {
            val commands = TermuxIntegration.getApiByCategory(category)
            recyclerView.adapter = ApiCommandAdapter(commands) { cmd ->
                executeApiCommand(cmd)
            }
        }
        
        chipGroup.setOnCheckedStateChangeListener { group, checkedIds ->
            if (checkedIds.isNotEmpty()) {
                val chip = group.findViewById<Chip>(checkedIds[0])
                val category = chip?.tag as? TermuxIntegration.ApiCategory
                if (category != null) {
                    updateList(category)
                }
            }
        }
        
        // 初始显示设备信息
        updateList(TermuxIntegration.ApiCategory.DEVICE)
    }
    
    private fun executeApiCommand(cmd: TermuxIntegration.ApiCommand) {
        if (cmd.requiresArgs) {
            // 需要参数的命令，显示输入对话框
            val input = EditText(this)
            input.hint = cmd.argsHint
            
            AlertDialog.Builder(this)
                .setTitle(cmd.name)
                .setMessage("请输入参数:\n${cmd.argsHint}")
                .setView(input)
                .setPositiveButton("执行") { _, _ ->
                    val args = input.text.toString()
                    val fullCmd = "${cmd.command} $args"
                    TermuxIntegration.runInTermux(this, fullCmd, background = false)
                    Toast.makeText(this, "命令已发送", Toast.LENGTH_SHORT).show()
                }
                .setNegativeButton("取消", null)
                .show()
        } else {
            // 直接执行
            TermuxIntegration.runInTermux(this, cmd.command, background = false)
            Toast.makeText(this, "执行: ${cmd.name}", Toast.LENGTH_SHORT).show()
        }
    }
    
    // ==================== X11 页面 ====================
    
    private fun showX11Page() {
        contentContainer.removeAllViews()
        val view = layoutInflater.inflate(R.layout.termux_page_x11, contentContainer, false)
        contentContainer.addView(view)
        
        val status = currentStatus
        
        // X11 状态提示
        val tvX11Status = view.findViewById<TextView>(R.id.tvX11Status)
        if (status?.hasX11Support == true) {
            tvX11Status.text = "✓ Termux:X11 已安装，可以运行图形应用"
            tvX11Status.setTextColor(ContextCompat.getColor(this, android.R.color.holo_green_dark))
        } else {
            tvX11Status.text = "✗ 请先安装 Termux 和 Termux:X11"
            tvX11Status.setTextColor(ContextCompat.getColor(this, android.R.color.holo_red_dark))
        }
        
        // 启动 X11 按钮
        view.findViewById<Button>(R.id.btnLaunchX11)?.setOnClickListener {
            TermuxIntegration.launchTermuxX11(this)
        }
        
        // 启动桌面按钮
        view.findViewById<Button>(R.id.btnStartDesktop)?.setOnClickListener {
            showDesktopSelectionDialog()
        }
        
        // X11 应用列表
        val recyclerView = view.findViewById<RecyclerView>(R.id.x11AppList)
        recyclerView.layoutManager = GridLayoutManager(this, 2)
        recyclerView.adapter = X11AppAdapter(TermuxIntegration.x11Apps) { app ->
            showX11AppDialog(app)
        }
    }
    
    private fun showDesktopSelectionDialog() {
        val desktops = arrayOf("XFCE4 (推荐)", "LXQt", "Openbox", "Fluxbox")
        val desktopCmds = arrayOf("xfce4", "lxqt", "openbox", "fluxbox")
        
        AlertDialog.Builder(this)
            .setTitle("选择桌面环境")
            .setItems(desktops) { _, which ->
                TermuxIntegration.startX11Desktop(this, desktopCmds[which])
                Toast.makeText(this, "正在启动 ${desktops[which]} 桌面...", Toast.LENGTH_LONG).show()
            }
            .show()
    }
    
    private fun showX11AppDialog(app: TermuxIntegration.X11App) {
        AlertDialog.Builder(this)
            .setTitle("${app.icon} ${app.name}")
            .setMessage("${app.description}\n\n安装命令:\n${app.installCommand}\n\n启动命令:\n${app.launchCommand}")
            .setPositiveButton("安装") { _, _ ->
                TermuxIntegration.openTermuxWithCommand(this, app.installCommand)
            }
            .setNeutralButton("启动") { _, _ ->
                TermuxIntegration.runInTermux(this, app.launchCommand, background = true)
                TermuxIntegration.launchTermuxX11(this)
            }
            .setNegativeButton("取消", null)
            .show()
    }
    
    // ==================== 脚本页面 ====================
    
    private fun showScriptsPage() {
        contentContainer.removeAllViews()
        val view = layoutInflater.inflate(R.layout.termux_page_scripts, contentContainer, false)
        contentContainer.addView(view)
        
        val recyclerView = view.findViewById<RecyclerView>(R.id.scriptList)
        recyclerView.layoutManager = LinearLayoutManager(this)
        recyclerView.adapter = ScriptAdapter(TermuxIntegration.presetScripts) { script ->
            showScriptDialog(script)
        }
        
        // 自定义脚本按钮
        view.findViewById<FloatingActionButton>(R.id.fabAddScript)?.setOnClickListener {
            showCustomScriptDialog()
        }
    }
    
    private fun showScriptDialog(script: TermuxIntegration.TermuxScript) {
        AlertDialog.Builder(this)
            .setTitle(script.name)
            .setMessage("${script.description}\n\n脚本内容:\n${script.script}")
            .setPositiveButton("执行") { _, _ ->
                TermuxIntegration.openTermuxWithCommand(this, script.script)
            }
            .setNeutralButton("复制") { _, _ ->
                val clipboard = getSystemService(CLIPBOARD_SERVICE) as android.content.ClipboardManager
                clipboard.setPrimaryClip(android.content.ClipData.newPlainText("script", script.script))
                Toast.makeText(this, "脚本已复制到剪贴板", Toast.LENGTH_SHORT).show()
            }
            .setNegativeButton("取消", null)
            .show()
    }
    
    private fun showCustomScriptDialog() {
        val input = EditText(this).apply {
            hint = "输入要执行的命令或脚本..."
            minLines = 5
        }
        
        AlertDialog.Builder(this)
            .setTitle("自定义命令")
            .setView(input)
            .setPositiveButton("执行") { _, _ ->
                val cmd = input.text.toString()
                if (cmd.isNotBlank()) {
                    TermuxIntegration.openTermuxWithCommand(this, cmd)
                }
            }
            .setNegativeButton("取消", null)
            .show()
    }
    
    // ==================== 终端页面 ====================
    
    private fun showTerminalPage() {
        contentContainer.removeAllViews()
        val view = layoutInflater.inflate(R.layout.termux_page_terminal, contentContainer, false)
        contentContainer.addView(view)
        
        val inputCommand = view.findViewById<TextInputEditText>(R.id.inputCommand)
        val btnExecute = view.findViewById<Button>(R.id.btnExecute)
        val btnOpenTermux = view.findViewById<Button>(R.id.btnOpenTermux)
        val commandHistory = view.findViewById<RecyclerView>(R.id.commandHistory)
        
        val history = mutableListOf<String>()
        
        btnExecute.setOnClickListener {
            val cmd = inputCommand.text.toString().trim()
            if (cmd.isNotBlank()) {
                TermuxIntegration.runInTermux(this, cmd, background = false)
                history.add(0, cmd)
                inputCommand.text?.clear()
                Toast.makeText(this, "命令已发送到 Termux", Toast.LENGTH_SHORT).show()
                
                // 更新历史记录
                commandHistory.adapter?.notifyDataSetChanged()
            }
        }
        
        btnOpenTermux.setOnClickListener {
            val cmd = inputCommand.text.toString().trim()
            if (cmd.isNotBlank()) {
                TermuxIntegration.openTermuxWithCommand(this, cmd)
            } else {
                TermuxIntegration.launchTermux(this)
            }
        }
        
        // 快捷命令按钮
        val quickCommands = listOf(
            "ls -la" to "列出文件",
            "pwd" to "当前目录",
            "df -h" to "磁盘空间",
            "free -h" to "内存信息",
            "top" to "进程监控",
            "pkg update" to "更新包",
            "exit" to "退出"
        )
        
        val quickCmdContainer = view.findViewById<LinearLayout>(R.id.quickCommands)
        quickCommands.forEach { (cmd, desc) ->
            val btn = Button(this).apply {
                text = desc
                textSize = 12f
                setOnClickListener {
                    inputCommand.setText(cmd)
                }
            }
            quickCmdContainer.addView(btn)
        }
        
        // 历史记录适配器
        commandHistory.layoutManager = LinearLayoutManager(this)
        commandHistory.adapter = object : RecyclerView.Adapter<RecyclerView.ViewHolder>() {
            override fun onCreateViewHolder(parent: android.view.ViewGroup, viewType: Int): RecyclerView.ViewHolder {
                val tv = TextView(parent.context).apply {
                    setPadding(16, 8, 16, 8)
                    setTextColor(ContextCompat.getColor(context, android.R.color.white))
                    setBackgroundColor(ContextCompat.getColor(context, android.R.color.darker_gray))
                }
                return object : RecyclerView.ViewHolder(tv) {}
            }
            
            override fun onBindViewHolder(holder: RecyclerView.ViewHolder, position: Int) {
                (holder.itemView as TextView).text = "$ ${history[position]}"
                holder.itemView.setOnClickListener {
                    inputCommand.setText(history[position])
                }
            }
            
            override fun getItemCount() = history.size
        }
    }
    
    override fun onOptionsItemSelected(item: MenuItem): Boolean {
        if (item.itemId == android.R.id.home) {
            finish()
            return true
        }
        return super.onOptionsItemSelected(item)
    }
    
    // ==================== 适配器 ====================
    
    inner class ApiCommandAdapter(
        private val commands: List<TermuxIntegration.ApiCommand>,
        private val onClick: (TermuxIntegration.ApiCommand) -> Unit
    ) : RecyclerView.Adapter<ApiCommandAdapter.VH>() {
        
        inner class VH(view: View) : RecyclerView.ViewHolder(view) {
            val tvName: TextView = view.findViewById(android.R.id.text1)
            val tvDesc: TextView = view.findViewById(android.R.id.text2)
        }
        
        override fun onCreateViewHolder(parent: android.view.ViewGroup, viewType: Int): VH {
            val view = layoutInflater.inflate(android.R.layout.simple_list_item_2, parent, false)
            return VH(view)
        }
        
        override fun onBindViewHolder(holder: VH, position: Int) {
            val cmd = commands[position]
            holder.tvName.text = cmd.name
            holder.tvDesc.text = cmd.description
            holder.itemView.setOnClickListener { onClick(cmd) }
        }
        
        override fun getItemCount() = commands.size
    }
    
    inner class X11AppAdapter(
        private val apps: List<TermuxIntegration.X11App>,
        private val onClick: (TermuxIntegration.X11App) -> Unit
    ) : RecyclerView.Adapter<X11AppAdapter.VH>() {
        
        inner class VH(view: View) : RecyclerView.ViewHolder(view) {
            val tvIcon: TextView = view.findViewById(android.R.id.text1)
            val tvName: TextView = view.findViewById(android.R.id.text2)
        }
        
        override fun onCreateViewHolder(parent: android.view.ViewGroup, viewType: Int): VH {
            val view = layoutInflater.inflate(android.R.layout.simple_list_item_2, parent, false)
            return VH(view)
        }
        
        override fun onBindViewHolder(holder: VH, position: Int) {
            val app = apps[position]
            holder.tvIcon.text = "${app.icon} ${app.name}"
            holder.tvName.text = app.description
            holder.itemView.setOnClickListener { onClick(app) }
        }
        
        override fun getItemCount() = apps.size
    }
    
    inner class ScriptAdapter(
        private val scripts: List<TermuxIntegration.TermuxScript>,
        private val onClick: (TermuxIntegration.TermuxScript) -> Unit
    ) : RecyclerView.Adapter<ScriptAdapter.VH>() {
        
        inner class VH(view: View) : RecyclerView.ViewHolder(view) {
            val tvName: TextView = view.findViewById(android.R.id.text1)
            val tvDesc: TextView = view.findViewById(android.R.id.text2)
        }
        
        override fun onCreateViewHolder(parent: android.view.ViewGroup, viewType: Int): VH {
            val view = layoutInflater.inflate(android.R.layout.simple_list_item_2, parent, false)
            return VH(view)
        }
        
        override fun onBindViewHolder(holder: VH, position: Int) {
            val script = scripts[position]
            holder.tvName.text = "[${script.category}] ${script.name}"
            holder.tvDesc.text = script.description
            holder.itemView.setOnClickListener { onClick(script) }
        }
        
        override fun getItemCount() = scripts.size
    }
}
