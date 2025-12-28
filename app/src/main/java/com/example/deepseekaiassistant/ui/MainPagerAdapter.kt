package com.example.deepseekaiassistant.ui

import androidx.fragment.app.Fragment
import androidx.fragment.app.FragmentActivity
import androidx.viewpager2.adapter.FragmentStateAdapter

/**
 * ViewPager2 适配器 - 多页滑动架构
 * 页面顺序：
 * 1. 聊天页（AI 对话）
 * 2. 数学计算
 * 3. Scene 玩机功能
 * 4. 浏览器
 * 5. 系统探测器
 */
class MainPagerAdapter(activity: FragmentActivity) : FragmentStateAdapter(activity) {

    override fun getItemCount(): Int = 5

    override fun createFragment(position: Int): Fragment {
        return when (position) {
            0 -> ChatFragment.newInstance()
            1 -> MathFragment()
            2 -> SceneFragment.newInstance()
            3 -> BrowserFragment.newInstance()
            4 -> SystemExplorerFragment.newInstance()
            else -> ChatFragment.newInstance()
        }
    }

    companion object {
        const val PAGE_CHAT = 0
        const val PAGE_MATH = 1
        const val PAGE_SCENE = 2
        const val PAGE_BROWSER = 3
        const val PAGE_SYSTEM = 4

        fun getPageTitle(position: Int): String {
            return when (position) {
                PAGE_CHAT -> "AI 助手"
                PAGE_MATH -> "数学计算"
                PAGE_SCENE -> "玩机工具"
                PAGE_BROWSER -> "浏览器"
                PAGE_SYSTEM -> "系统探测"
                else -> ""
            }
        }

        fun getPageIcon(position: Int): Int {
            return when (position) {
                PAGE_CHAT -> android.R.drawable.ic_menu_myplaces
                PAGE_MATH -> android.R.drawable.ic_menu_sort_by_size
                PAGE_SCENE -> android.R.drawable.ic_menu_manage
                PAGE_BROWSER -> android.R.drawable.ic_menu_compass
                PAGE_SYSTEM -> android.R.drawable.ic_menu_info_details
                else -> android.R.drawable.ic_menu_help
            }
        }
    }
}
