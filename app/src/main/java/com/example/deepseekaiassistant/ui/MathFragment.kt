/**
 * 数学计算 Fragment
 * 
 * 功能：
 * - LaTeX 输入和实时预览
 * - 数学表达式计算
 * - 函数图像绘制
 * - 处理过程展示
 * - 历史记录和公式模板
 */
package com.example.deepseekaiassistant.ui

import android.annotation.SuppressLint
import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.graphics.Color
import android.os.Bundle
import android.text.Editable
import android.text.TextWatcher
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.webkit.WebSettings
import android.webkit.WebView
import android.webkit.WebViewClient
import android.widget.Button
import android.widget.Toast
import androidx.core.view.isVisible
import androidx.fragment.app.Fragment
import androidx.lifecycle.lifecycleScope
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.example.deepseekaiassistant.DiagnosticManager
import com.example.deepseekaiassistant.R
import com.example.deepseekaiassistant.databinding.FragmentMathBinding
import com.example.deepseekaiassistant.math.*
import com.github.mikephil.charting.components.XAxis
import com.github.mikephil.charting.data.Entry
import com.github.mikephil.charting.data.LineData
import com.github.mikephil.charting.data.LineDataSet
import com.google.android.material.dialog.MaterialAlertDialogBuilder
import com.google.android.material.tabs.TabLayout
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

class MathFragment : Fragment() {

    private var _binding: FragmentMathBinding? = null
    private val binding get() = _binding!!

    private lateinit var latexRenderer: LatexRenderer
    private val mathEngine = MathEngine()
    private val latexParser = LatexParser()

    private var previewJob: Job? = null
    private var currentOperation = MathOperation.EVALUATE
    
    // 快捷符号
    private val quickSymbols = listOf(
        "+" to "+", "-" to "-", "×" to "*", "÷" to "/",
        "^" to "^{}", "√" to "\\sqrt{}", "∫" to "\\int_{}^{}",
        "Σ" to "\\sum_{i=1}^{n}", "π" to "\\pi", "∞" to "\\infty",
        "α" to "\\alpha", "β" to "\\beta", "θ" to "\\theta",
        "(" to "(", ")" to ")", "{" to "\\{", "}" to "\\}",
        "frac" to "\\frac{}{}", "lim" to "\\lim_{x \\to }",
        "sin" to "\\sin()", "cos" to "\\cos()", "log" to "\\log()"
    )

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        _binding = FragmentMathBinding.inflate(inflater, container, false)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        
        latexRenderer = LatexRenderer(requireContext())
        
        setupWebViews()
        setupQuickSymbols()
        setupInputListener()
        setupButtons()
        setupTabs()
        setupChart()
        observeProcessFlow()
        
        DiagnosticManager.info("MathFragment", "数学计算模块已初始化")
    }

    @SuppressLint("SetJavaScriptEnabled")
    private fun setupWebViews() {
        listOf(binding.webViewFormula, binding.webViewResult, binding.webViewCode).forEach { webView ->
            webView.settings.apply {
                javaScriptEnabled = true
                domStorageEnabled = true
                loadWithOverviewMode = true
                useWideViewPort = true
                cacheMode = WebSettings.LOAD_NO_CACHE
            }
            webView.setBackgroundColor(Color.TRANSPARENT)
            webView.webViewClient = object : WebViewClient() {
                override fun onPageFinished(view: WebView?, url: String?) {
                    super.onPageFinished(view, url)
                }
            }
        }
    }

    private fun setupQuickSymbols() {
        val container = binding.symbolBar
        container.removeAllViews()
        
        for ((display, latex) in quickSymbols) {
            val button = Button(requireContext()).apply {
                text = display
                textSize = 14f
                minWidth = 0
                minimumWidth = 0
                setPadding(24, 8, 24, 8)
                setBackgroundResource(R.drawable.bg_edit_text)
                
                val params = ViewGroup.MarginLayoutParams(
                    ViewGroup.LayoutParams.WRAP_CONTENT,
                    ViewGroup.LayoutParams.WRAP_CONTENT
                ).apply {
                    marginEnd = 8
                }
                layoutParams = params
                
                setOnClickListener {
                    insertSymbol(latex)
                }
            }
            container.addView(button)
        }
    }

    private fun insertSymbol(latex: String) {
        val editText = binding.etLatexInput
        val start = editText.selectionStart
        val end = editText.selectionEnd
        val text = editText.text ?: return
        
        text.replace(start, end, latex)
        
        // 如果符号包含空括号，将光标移到括号内
        val cursorPos = when {
            latex.endsWith("{}") -> start + latex.length - 1
            latex.endsWith("()") -> start + latex.length - 1
            latex.contains("{}{}") -> start + latex.indexOf("{}") + 1
            else -> start + latex.length
        }
        editText.setSelection(cursorPos.coerceIn(0, text.length))
    }

    private fun setupInputListener() {
        binding.etLatexInput.addTextChangedListener(object : TextWatcher {
            override fun beforeTextChanged(s: CharSequence?, start: Int, count: Int, after: Int) {}
            override fun onTextChanged(s: CharSequence?, start: Int, before: Int, count: Int) {}
            override fun afterTextChanged(s: Editable?) {
                previewJob?.cancel()
                previewJob = lifecycleScope.launch {
                    delay(300) // 防抖
                    updatePreview(s?.toString() ?: "")
                }
            }
        })
    }

    private fun updatePreview(latex: String) {
        if (latex.isBlank()) {
            binding.webViewFormula.loadData("", "text/html", "UTF-8")
            return
        }
        
        val html = latexRenderer.generateMathJaxHtml(latex)
        binding.webViewFormula.loadDataWithBaseURL(null, html, "text/html", "UTF-8", null)
    }

    private fun setupButtons() {
        binding.btnCalculate.setOnClickListener {
            calculate()
        }
        
        binding.btnPlot.setOnClickListener {
            plotGraph()
        }
        
        binding.btnCopyResult.setOnClickListener {
            copyResult()
        }
        
        binding.btnCancelProcess.setOnClickListener {
            MathProcessManager.cancel()
        }
        
        binding.btnSaveGraph.setOnClickListener {
            saveGraph()
        }
        
        binding.btnHistory.setOnClickListener {
            showHistoryDialog()
        }
        
        binding.btnTemplates.setOnClickListener {
            showTemplatesDialog()
        }
    }

    private fun setupTabs() {
        binding.tabLayout.addOnTabSelectedListener(object : TabLayout.OnTabSelectedListener {
            override fun onTabSelected(tab: TabLayout.Tab?) {
                currentOperation = when (tab?.position) {
                    0 -> MathOperation.EVALUATE
                    1 -> MathOperation.PLOT
                    2 -> MathOperation.SOLVE
                    3 -> MathOperation.DERIVATIVE
                    4 -> MathOperation.EVALUATE // 矩阵
                    else -> MathOperation.EVALUATE
                }
                
                // 更新界面提示
                binding.etLatexInput.hint = when (currentOperation) {
                    MathOperation.EVALUATE -> "例如: \\frac{1}{2} + \\sqrt{x^2+1}"
                    MathOperation.SOLVE -> "例如: x^2 - 4 = 0"
                    MathOperation.DERIVATIVE -> "例如: x^3 + 2x^2 - x"
                    MathOperation.INTEGRATE -> "例如: x^2 + 1"
                    MathOperation.PLOT -> "例如: sin(x)"
                    MathOperation.LIMIT -> "例如: \\frac{sin(x)}{x}"
                }
            }
            
            override fun onTabUnselected(tab: TabLayout.Tab?) {}
            override fun onTabReselected(tab: TabLayout.Tab?) {}
        })
    }

    private fun setupChart() {
        binding.lineChart.apply {
            description.isEnabled = false
            setTouchEnabled(true)
            isDragEnabled = true
            setScaleEnabled(true)
            setPinchZoom(true)
            
            xAxis.apply {
                position = XAxis.XAxisPosition.BOTTOM
                setDrawGridLines(true)
                granularity = 1f
            }
            
            axisLeft.apply {
                setDrawGridLines(true)
            }
            
            axisRight.isEnabled = false
            
            legend.isEnabled = true
        }
    }

    private fun observeProcessFlow() {
        lifecycleScope.launch {
            MathProcessManager.isProcessing.collectLatest { isProcessing ->
                binding.cardProcess.isVisible = isProcessing
                binding.btnCalculate.isEnabled = !isProcessing
                binding.btnPlot.isEnabled = !isProcessing
            }
        }
        
        lifecycleScope.launch {
            MathProcessManager.stepLogs.collectLatest { step ->
                // 更新步骤日志UI
                updateProcessStep(step)
            }
        }
    }

    private fun updateProcessStep(step: ProcessStep) {
        binding.tvProcessTitle.text = "${step.title} - ${step.description}"
        
        val statusIcon = when (step.status) {
            StepStatus.COMPLETED -> "✓"
            StepStatus.FAILED -> "✗"
            StepStatus.RUNNING -> "⋯"
            else -> "○"
        }
        
        DiagnosticManager.info("MathProcess", "[$statusIcon] ${step.title}: ${step.description}")
    }

    private fun calculate() {
        val latex = binding.etLatexInput.text?.toString() ?: ""
        if (latex.isBlank()) {
            Toast.makeText(requireContext(), "请输入 LaTeX 表达式", Toast.LENGTH_SHORT).show()
            return
        }
        
        lifecycleScope.launch {
            binding.cardProcess.isVisible = true
            binding.cardResult.isVisible = false
            binding.cardGraph.isVisible = false
            
            val flow = MathProcessManager.processLatex(
                latex = latex,
                operation = currentOperation,
                onStepUpdate = { step ->
                    updateProcessStep(step)
                }
            )
            
            // 显示结果
            withContext(Dispatchers.Main) {
                binding.cardProcess.isVisible = false
                displayResult(flow)
            }
        }
    }

    private fun displayResult(flow: ProcessFlow) {
        binding.cardResult.isVisible = true
        
        when (val result = flow.finalResult) {
            is CalculationResult.NumericResult -> {
                binding.ivResultIcon.setImageResource(R.drawable.ic_check_circle)
                val resultLatex = "= ${result.formattedValue}"
                val html = latexRenderer.generateMathJaxHtml(resultLatex)
                binding.webViewResult.loadDataWithBaseURL(null, html, "text/html", "UTF-8", null)
            }
            
            is CalculationResult.SymbolicResult -> {
                binding.ivResultIcon.setImageResource(R.drawable.ic_check_circle)
                val html = latexRenderer.generateMathJaxHtml(result.latex)
                binding.webViewResult.loadDataWithBaseURL(null, html, "text/html", "UTF-8", null)
            }
            
            is CalculationResult.SolutionSet -> {
                binding.ivResultIcon.setImageResource(R.drawable.ic_check_circle)
                val html = latexRenderer.generateMathJaxHtml(result.latex)
                binding.webViewResult.loadDataWithBaseURL(null, html, "text/html", "UTF-8", null)
            }
            
            is CalculationResult.MatrixResult -> {
                binding.ivResultIcon.setImageResource(R.drawable.ic_check_circle)
                val html = latexRenderer.generateMathJaxHtml(result.latex)
                binding.webViewResult.loadDataWithBaseURL(null, html, "text/html", "UTF-8", null)
            }
            
            is CalculationResult.Error -> {
                binding.ivResultIcon.setImageResource(R.drawable.ic_functions)
                binding.webViewResult.loadData(
                    "<div style='color:red;padding:16px;'>${result.message}</div>",
                    "text/html", "UTF-8"
                )
            }
            
            else -> {
                binding.webViewResult.loadData(
                    "<div style='color:gray;padding:16px;'>无结果</div>",
                    "text/html", "UTF-8"
                )
            }
        }
        
        // 显示可执行代码
        val executableCode = latexParser.toExecutableExpression(flow.inputLatex)
        val codeHtml = CodeHighlighter.highlightExecutableCode(executableCode)
        binding.webViewCode.loadData(codeHtml, "text/html", "UTF-8")
    }

    private fun plotGraph() {
        val latex = binding.etLatexInput.text?.toString() ?: ""
        if (latex.isBlank()) {
            Toast.makeText(requireContext(), "请输入函数表达式", Toast.LENGTH_SHORT).show()
            return
        }
        
        lifecycleScope.launch {
            try {
                val plotData = withContext(Dispatchers.Default) {
                    mathEngine.generatePlotData(latex)
                }
                
                if (plotData.points.isEmpty()) {
                    Toast.makeText(requireContext(), "无法生成图像数据", Toast.LENGTH_SHORT).show()
                    return@launch
                }
                
                // 转换为图表数据
                val entries = plotData.points.map { Entry(it.x.toFloat(), it.y.toFloat()) }
                
                val dataSet = LineDataSet(entries, latex).apply {
                    color = Color.parseColor("#4F46E5")
                    lineWidth = 2f
                    setDrawCircles(false)
                    setDrawValues(false)
                    mode = LineDataSet.Mode.CUBIC_BEZIER
                }
                
                binding.lineChart.data = LineData(dataSet)
                binding.lineChart.invalidate()
                
                binding.cardGraph.isVisible = true
                binding.cardResult.isVisible = false
                
                DiagnosticManager.info("MathPlot", "函数图像已生成: $latex (${plotData.points.size} 个点)")
                
            } catch (e: Exception) {
                Toast.makeText(requireContext(), "绘图失败: ${e.message}", Toast.LENGTH_SHORT).show()
                DiagnosticManager.error("MathPlot", "绘图失败", e.message)
            }
        }
    }

    private fun copyResult() {
        val clipboard = requireContext().getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
        
        val flow = MathProcessManager.currentFlow.value
        val resultText = when (val result = flow?.finalResult) {
            is CalculationResult.NumericResult -> result.formattedValue
            is CalculationResult.SymbolicResult -> result.expression
            is CalculationResult.SolutionSet -> result.solutions.joinToString(", ")
            is CalculationResult.MatrixResult -> result.latex
            else -> "无结果"
        }
        
        val clip = ClipData.newPlainText("计算结果", resultText)
        clipboard.setPrimaryClip(clip)
        Toast.makeText(requireContext(), "结果已复制", Toast.LENGTH_SHORT).show()
    }

    private fun saveGraph() {
        // TODO: 实现保存图像功能
        Toast.makeText(requireContext(), "保存图像功能开发中", Toast.LENGTH_SHORT).show()
    }

    private fun showHistoryDialog() {
        val history = MathProcessManager.getHistory()
        
        if (history.isEmpty()) {
            Toast.makeText(requireContext(), "暂无历史记录", Toast.LENGTH_SHORT).show()
            return
        }
        
        val items = history.map { "${it.latex} = ${it.result ?: "..."}" }.toTypedArray()
        
        MaterialAlertDialogBuilder(requireContext())
            .setTitle("历史记录")
            .setItems(items) { _, index ->
                binding.etLatexInput.setText(history[index].latex)
            }
            .setNeutralButton("清空") { _, _ ->
                MathProcessManager.clearHistory()
                Toast.makeText(requireContext(), "历史已清空", Toast.LENGTH_SHORT).show()
            }
            .setNegativeButton("关闭", null)
            .show()
    }

    private fun showTemplatesDialog() {
        val categories = arrayOf("代数", "微积分", "三角函数", "线性代数", "统计")
        
        MaterialAlertDialogBuilder(requireContext())
            .setTitle("公式模板分类")
            .setItems(categories) { _, index ->
                showTemplatesForCategory(categories[index])
            }
            .setNegativeButton("关闭", null)
            .show()
    }

    private fun showTemplatesForCategory(category: String) {
        val templates = when (category) {
            "代数" -> FormulaTemplates.ALGEBRA
            "微积分" -> FormulaTemplates.CALCULUS
            "三角函数" -> FormulaTemplates.TRIGONOMETRY
            "线性代数" -> FormulaTemplates.LINEAR_ALGEBRA
            "统计" -> FormulaTemplates.STATISTICS
            else -> emptyList()
        }
        
        if (templates.isEmpty()) {
            Toast.makeText(requireContext(), "该分类暂无模板", Toast.LENGTH_SHORT).show()
            return
        }
        
        val items = templates.map { "${it.name}\n${it.description}" }.toTypedArray()
        
        MaterialAlertDialogBuilder(requireContext())
            .setTitle(category)
            .setItems(items) { _, index ->
                binding.etLatexInput.setText(templates[index].latex)
            }
            .setNegativeButton("返回", null)
            .show()
    }

    override fun onDestroyView() {
        super.onDestroyView()
        previewJob?.cancel()
        _binding = null
    }
}
