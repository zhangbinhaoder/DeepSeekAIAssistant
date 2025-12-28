package com.example.deepseekaiassistant

import android.Manifest
import android.os.Build
import android.os.Bundle
import android.view.Menu
import android.view.MenuItem
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import androidx.viewpager2.widget.ViewPager2
import com.example.deepseekaiassistant.databinding.ActivityMainBinding
import com.example.deepseekaiassistant.ui.MainPagerAdapter
import com.permissionx.guolindev.PermissionX

/**
 * 主界面 Activity
 * 使用 ViewPager2 实现多页滑动：
 * - 第一页：AI 聊天
 * - 第二页：数学计算
 * - 第三页：Scene 玩机功能
 * - 第四页：浏览器
 * - 第五页：系统探测器
 */
class MainActivity : AppCompatActivity() {

    private lateinit var binding: ActivityMainBinding
    private lateinit var pagerAdapter: MainPagerAdapter

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityMainBinding.inflate(layoutInflater)
        setContentView(binding.root)

        setupViewPager()
        setupBottomNavigation()
        requestBasicPermissions()
        
        // 启动时执行 APP 自检
        runAppSelfCheck()
    }
    
    /**
     * 执行 APP 自检
     * 检测各模块状态，自动修复常见问题，错误时弹出提示
     */
    private fun runAppSelfCheck() {
        SelfCheckManager.runFullCheck(
            context = this,
            autoFix = true,
            showDialog = true  // 有问题时显示对话框
        ) { report ->
            if (report.hasErrors) {
                DiagnosticManager.warning("MainActivity", 
                    "自检发现 ${report.errorCount} 个错误，${report.autoFixedCount} 个已自动修复")
            }
        }
    }

    private fun setupViewPager() {
        pagerAdapter = MainPagerAdapter(this)
        binding.viewPager.apply {
            adapter = pagerAdapter
            // 预加载相邻页面以提升体验
            offscreenPageLimit = 2
            
            // 禁用滑动切换，只允许通过底部导航切换页面
            isUserInputEnabled = false
            
            // 页面切换监听
            registerOnPageChangeCallback(object : ViewPager2.OnPageChangeCallback() {
                override fun onPageSelected(position: Int) {
                    // 同步底部导航栏选中状态
                    val menuItemId = when (position) {
                        MainPagerAdapter.PAGE_CHAT -> R.id.nav_chat
                        MainPagerAdapter.PAGE_MATH -> R.id.nav_math
                        MainPagerAdapter.PAGE_SCENE -> R.id.nav_scene
                        MainPagerAdapter.PAGE_BROWSER -> R.id.nav_browser
                        MainPagerAdapter.PAGE_SYSTEM -> R.id.nav_system
                        else -> R.id.nav_chat
                    }
                    binding.bottomNavigation.selectedItemId = menuItemId
                }
            })
        }
    }

    private fun setupBottomNavigation() {
        binding.bottomNavigation.setOnItemSelectedListener { item ->
            val position = when (item.itemId) {
                R.id.nav_chat -> MainPagerAdapter.PAGE_CHAT
                R.id.nav_math -> MainPagerAdapter.PAGE_MATH
                R.id.nav_scene -> MainPagerAdapter.PAGE_SCENE
                R.id.nav_browser -> MainPagerAdapter.PAGE_BROWSER
                R.id.nav_system -> MainPagerAdapter.PAGE_SYSTEM
                else -> MainPagerAdapter.PAGE_CHAT
            }
            // 平滑滚动到对应页面
            binding.viewPager.setCurrentItem(position, true)
            true
        }
    }

    private fun requestBasicPermissions() {
        val permissions = mutableListOf(
            Manifest.permission.RECORD_AUDIO,
            Manifest.permission.CAMERA,
            Manifest.permission.INTERNET,
            Manifest.permission.ACCESS_NETWORK_STATE
        )

        // 存储权限
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            permissions.add(Manifest.permission.POST_NOTIFICATIONS)
        }

        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.Q) {
            permissions.add(Manifest.permission.WRITE_EXTERNAL_STORAGE)
            permissions.add(Manifest.permission.READ_EXTERNAL_STORAGE)
        }

        PermissionX.init(this)
            .permissions(permissions)
            .onExplainRequestReason { scope, deniedList ->
                scope.showRequestReasonDialog(
                    deniedList,
                    "这些权限对于应用的完整功能很重要：\n• 麦克风 - 语音输入\n• 相机 - 图片识别\n• 存储 - 文件管理\n• 网络 - AI 联网",
                    "授予",
                    "取消"
                )
            }
            .request { allGranted, grantedList, deniedList ->
                if (!allGranted) {
                    Toast.makeText(
                        this,
                        "部分权限未授予，某些功能可能受限",
                        Toast.LENGTH_SHORT
                    ).show()
                }
                DiagnosticManager.info("Permission", "权限请求完成", 
                    "已授予: ${grantedList.size}, 拒绝: ${deniedList.size}")
            }
    }

    /**
     * 跳转到指定页面
     */
    fun navigateToPage(page: Int) {
        binding.viewPager.setCurrentItem(page, true)
    }

    /**
     * 创建菜单
     */
    override fun onCreateOptionsMenu(menu: Menu): Boolean {
        menuInflater.inflate(R.menu.menu_main, menu)
        return true
    }
    
    /**
     * 菜单项点击处理
     */
    override fun onOptionsItemSelected(item: MenuItem): Boolean {
        return when (item.itemId) {
            R.id.action_self_check -> {
                // 手动触发自检
                SelfCheckManager.runFullCheck(
                    context = this,
                    autoFix = true,
                    showDialog = true
                )
                true
            }
            R.id.action_export_logs -> {
                // 导出日志
                val logFile = SelfCheckManager.exportLogsToFile(this)
                if (logFile != null) {
                    Toast.makeText(this, "日志已导出: ${logFile.name}", Toast.LENGTH_LONG).show()
                } else {
                    Toast.makeText(this, "日志导出失败", Toast.LENGTH_SHORT).show()
                }
                true
            }
            else -> super.onOptionsItemSelected(item)
        }
    }

    /**
     * 返回键处理：如果不在第一页，则返回第一页
     */
    @Deprecated("Deprecated in Java")
    override fun onBackPressed() {
        if (binding.viewPager.currentItem != 0) {
            binding.viewPager.setCurrentItem(0, true)
        } else {
            super.onBackPressed()
        }
    }
}
