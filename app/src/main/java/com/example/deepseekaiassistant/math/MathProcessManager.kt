/**
 * 数学处理流程管理器
 * 
 * 负责协调 LaTeX 解析、代码转换、运算执行的完整流程
 * 提供实时过程展示和步骤日志
 */
package com.example.deepseekaiassistant.math

import android.content.Context
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.*
import java.io.File
import java.text.SimpleDateFormat
import java.util.*
import java.util.concurrent.atomic.AtomicBoolean

/**
 * 数学处理流程管理器
 */
object MathProcessManager {
    
    private val parser = LatexParser()
    private val engine = MathEngine()
    
    private val _currentFlow = MutableStateFlow<ProcessFlow?>(null)
    val currentFlow: StateFlow<ProcessFlow?> = _currentFlow.asStateFlow()
    
    private val _stepLogs = MutableSharedFlow<ProcessStep>(replay = 100)
    val stepLogs: SharedFlow<ProcessStep> = _stepLogs.asSharedFlow()
    
    private val _isProcessing = MutableStateFlow(false)
    val isProcessing: StateFlow<Boolean> = _isProcessing.asStateFlow()
    
    private val cancelFlag = AtomicBoolean(false)
    
    // 历史记录
    private val history = mutableListOf<MathHistoryItem>()
    private const val MAX_HISTORY = 100
    
    /**
     * 执行完整的数学处理流程
     */
    suspend fun processLatex(
        latex: String,
        operation: MathOperation = MathOperation.EVALUATE,
        variables: Map<String, Double> = emptyMap(),
        onStepUpdate: ((ProcessStep) -> Unit)? = null
    ): ProcessFlow {
        _isProcessing.value = true
        cancelFlag.set(false)
        
        val flow = ProcessFlow(inputLatex = latex)
        _currentFlow.value = flow
        
        try {
            // 步骤 1: LaTeX 语法校验
            val step1 = executeStep(flow, 1, "LaTeX 语法校验", "校验输入的 LaTeX 表达式") {
                val parseResult = parser.parse(latex)
                if (!parseResult.success) {
                    throw IllegalArgumentException(parseResult.errors.joinToString { it.message })
                }
                mapOf(
                    "expressionType" to parseResult.expressionType.name,
                    "tokenCount" to parseResult.tokens.size,
                    "normalized" to parseResult.normalizedLatex
                )
            }
            onStepUpdate?.invoke(step1)
            _stepLogs.emit(step1)
            
            if (cancelFlag.get()) return cancelFlow(flow)
            
            // 步骤 2: 表达式类型分析
            val step2 = executeStep(flow, 2, "表达式类型分析", "识别数学表达式的类型和结构") {
                val parseResult = parser.parse(latex)
                mapOf(
                    "type" to parseResult.expressionType.name,
                    "description" to getExpressionTypeDescription(parseResult.expressionType)
                )
            }
            onStepUpdate?.invoke(step2)
            _stepLogs.emit(step2)
            
            if (cancelFlag.get()) return cancelFlow(flow)
            
            // 步骤 3: 转换为机器代码
            val step3 = executeStep(flow, 3, "转换为可执行代码", "将 LaTeX 转换为可计算的表达式") {
                val execExpr = parser.toExecutableExpression(latex)
                mapOf(
                    "executableExpression" to execExpr,
                    "language" to "Kotlin Math"
                )
            }
            onStepUpdate?.invoke(step3)
            _stepLogs.emit(step3)
            
            if (cancelFlag.get()) return cancelFlow(flow)
            
            // 步骤 4: 执行运算
            val step4 = executeStep(flow, 4, "执行运算", "计算数学表达式的结果") {
                val result = when (operation) {
                    MathOperation.EVALUATE -> engine.evaluate(latex, variables)
                    MathOperation.SOLVE -> engine.solveEquation(latex)
                    MathOperation.DERIVATIVE -> engine.derivative(latex)
                    MathOperation.INTEGRATE -> {
                        val lower = variables["lower"] ?: 0.0
                        val upper = variables["upper"] ?: 1.0
                        engine.integrate(latex, "x", lower, upper)
                    }
                    MathOperation.LIMIT -> {
                        val approaches = variables["approaches"] ?: 0.0
                        engine.limit(latex, "x", approaches)
                    }
                    MathOperation.PLOT -> {
                        engine.generatePlotData(latex)
                        CalculationResult.SymbolicResult("图像数据已生成", latex)
                    }
                }
                flow.finalResult = result
                mapOf("result" to result)
            }
            onStepUpdate?.invoke(step4)
            _stepLogs.emit(step4)
            
            if (cancelFlag.get()) return cancelFlow(flow)
            
            // 步骤 5: 格式化输出
            val step5 = executeStep(flow, 5, "格式化输出", "将结果转换为可读格式") {
                val formatted = formatResult(flow.finalResult)
                mapOf("formattedResult" to formatted)
            }
            onStepUpdate?.invoke(step5)
            _stepLogs.emit(step5)
            
            // 完成
            flow.endTime = System.currentTimeMillis()
            flow.overallStatus = FlowStatus.COMPLETED
            
            // 添加到历史记录
            addToHistory(latex, flow.finalResult)
            
        } catch (e: Exception) {
            flow.overallStatus = FlowStatus.FAILED
            flow.endTime = System.currentTimeMillis()
            
            val errorStep = ProcessStep(
                stepNumber = flow.steps.size + 1,
                title = "错误",
                description = e.message ?: "未知错误",
                status = StepStatus.FAILED
            )
            flow.steps.add(errorStep)
            _stepLogs.emit(errorStep)
            
            flow.finalResult = CalculationResult.Error(
                message = e.message ?: "处理失败",
                errorCode = "PROCESS_ERROR"
            )
        } finally {
            _isProcessing.value = false
            _currentFlow.value = flow
        }
        
        return flow
    }
    
    /**
     * 取消当前处理
     */
    fun cancel() {
        cancelFlag.set(true)
    }
    
    /**
     * 获取计算历史
     */
    fun getHistory(): List<MathHistoryItem> = history.toList()
    
    /**
     * 清除历史
     */
    fun clearHistory() {
        history.clear()
    }
    
    /**
     * 导出历史到文件
     */
    fun exportHistory(context: Context): File? {
        return try {
            val dateFormat = SimpleDateFormat("yyyyMMdd_HHmmss", Locale.getDefault())
            val fileName = "math_history_${dateFormat.format(Date())}.txt"
            val file = File(context.getExternalFilesDir(null), fileName)
            
            file.writeText(buildString {
                appendLine("数学计算历史记录")
                appendLine("导出时间: ${dateFormat.format(Date())}")
                appendLine("=" .repeat(50))
                appendLine()
                
                history.forEachIndexed { index, item ->
                    appendLine("【${index + 1}】")
                    appendLine("  LaTeX: ${item.latex}")
                    appendLine("  结果: ${item.result ?: "无"}")
                    appendLine("  时间: ${SimpleDateFormat("yyyy-MM-dd HH:mm:ss", Locale.getDefault()).format(item.timestamp)}")
                    appendLine()
                }
            })
            
            file
        } catch (e: Exception) {
            null
        }
    }
    
    // ======================== 私有方法 ========================
    
    private suspend fun executeStep(
        flow: ProcessFlow,
        stepNumber: Int,
        title: String,
        description: String,
        action: suspend () -> Map<String, Any>
    ): ProcessStep {
        val step = ProcessStep(
            stepNumber = stepNumber,
            title = title,
            description = description,
            status = StepStatus.RUNNING
        )
        flow.steps.add(step)
        
        val startTime = System.currentTimeMillis()
        
        return try {
            delay(50) // 给 UI 一点时间更新
            val result = action()
            val duration = System.currentTimeMillis() - startTime
            
            step.copy(
                status = StepStatus.COMPLETED,
                duration = duration,
                output = result.entries.joinToString("\n") { "${it.key}: ${it.value}" },
                details = result
            ).also {
                flow.steps[stepNumber - 1] = it
            }
        } catch (e: Exception) {
            step.copy(
                status = StepStatus.FAILED,
                duration = System.currentTimeMillis() - startTime,
                output = "错误: ${e.message}"
            ).also {
                flow.steps[stepNumber - 1] = it
            }
            throw e
        }
    }
    
    private fun cancelFlow(flow: ProcessFlow): ProcessFlow {
        flow.overallStatus = FlowStatus.CANCELLED
        flow.endTime = System.currentTimeMillis()
        return flow
    }
    
    private fun getExpressionTypeDescription(type: ExpressionType): String {
        return when (type) {
            ExpressionType.EQUATION -> "等式方程，可求解"
            ExpressionType.INEQUALITY -> "不等式"
            ExpressionType.FUNCTION -> "函数定义"
            ExpressionType.DERIVATIVE -> "求导表达式"
            ExpressionType.INTEGRAL -> "积分表达式"
            ExpressionType.LIMIT -> "极限表达式"
            ExpressionType.SUMMATION -> "求和表达式"
            ExpressionType.PRODUCT -> "求积表达式"
            ExpressionType.MATRIX -> "矩阵表达式"
            ExpressionType.VECTOR -> "向量表达式"
            ExpressionType.TRIGONOMETRY -> "三角函数表达式"
            ExpressionType.LOGARITHM -> "对数表达式"
            ExpressionType.EXPONENTIAL -> "指数表达式"
            ExpressionType.POLYNOMIAL -> "多项式表达式"
            ExpressionType.FRACTION -> "分数表达式"
            ExpressionType.ROOT -> "根式表达式"
            else -> "数学表达式"
        }
    }
    
    private fun formatResult(result: CalculationResult?): String {
        return when (result) {
            is CalculationResult.NumericResult -> result.formattedValue
            is CalculationResult.SymbolicResult -> result.expression
            is CalculationResult.SolutionSet -> result.solutions.joinToString(", ")
            is CalculationResult.MatrixResult -> result.latex
            is CalculationResult.GraphResult -> "图像数据（${result.points.size} 个点）"
            is CalculationResult.Error -> "错误: ${result.message}"
            null -> "无结果"
        }
    }
    
    private fun addToHistory(latex: String, result: CalculationResult?) {
        val item = MathHistoryItem(
            latex = latex,
            displayedFormula = latex,
            machineCode = parser.toExecutableExpression(latex),
            result = formatResult(result),
            resultType = result?.javaClass?.simpleName ?: "Unknown"
        )
        
        history.add(0, item)
        
        // 限制历史记录数量
        while (history.size > MAX_HISTORY) {
            history.removeAt(history.lastIndex)
        }
    }
}

/**
 * 数学运算类型
 */
enum class MathOperation {
    EVALUATE,       // 求值
    SOLVE,          // 解方程
    DERIVATIVE,     // 求导
    INTEGRATE,      // 积分
    LIMIT,          // 极限
    PLOT            // 绘图
}

/**
 * LaTeX 渲染器（使用 WebView + MathJax）
 */
class LatexRenderer(private val context: Context) {
    
    /**
     * 生成 HTML 用于 WebView 渲染 LaTeX
     */
    fun generateMathJaxHtml(latex: String, displayMode: Boolean = true): String {
        val displayModeStr = if (displayMode) "true" else "false"
        return """
<!DOCTYPE html>
<html>
<head>
    <meta charset="UTF-8">
    <meta name="viewport" content="width=device-width, initial-scale=1.0">
    <script>
        MathJax = {
            tex: {
                inlineMath: [['$', '$'], ['\\(', '\\)']],
                displayMath: [['$$', '$$'], ['\\[', '\\]']],
            },
            options: {
                enableMenu: false
            },
            startup: {
                ready: function() {
                    MathJax.startup.defaultReady();
                    MathJax.startup.promise.then(function() {
                        if (window.Android) {
                            window.Android.onRenderComplete();
                        }
                    });
                }
            }
        };
    </script>
    <script src="https://cdn.jsdelivr.net/npm/mathjax@3/es5/tex-mml-chtml.js" async></script>
    <style>
        body {
            margin: 0;
            padding: 16px;
            font-family: -apple-system, BlinkMacSystemFont, 'Segoe UI', Roboto, sans-serif;
            background: transparent;
            display: flex;
            justify-content: center;
            align-items: center;
            min-height: 100vh;
        }
        #math-container {
            font-size: 24px;
            color: #333;
            text-align: center;
        }
        @media (prefers-color-scheme: dark) {
            body { background: transparent; }
            #math-container { color: #eee; }
        }
    </style>
</head>
<body>
    <div id="math-container">
        ${if (displayMode) "\\[$latex\\]" else "\\($latex\\)"}
    </div>
</body>
</html>
        """.trimIndent()
    }
    
    /**
     * 离线渲染 HTML（使用 KaTeX CDN 备用）
     */
    fun generateKaTexHtml(latex: String, displayMode: Boolean = true): String {
        return """
<!DOCTYPE html>
<html>
<head>
    <meta charset="UTF-8">
    <meta name="viewport" content="width=device-width, initial-scale=1.0">
    <link rel="stylesheet" href="https://cdn.jsdelivr.net/npm/katex@0.16.9/dist/katex.min.css">
    <script defer src="https://cdn.jsdelivr.net/npm/katex@0.16.9/dist/katex.min.js"></script>
    <style>
        body {
            margin: 0;
            padding: 16px;
            background: transparent;
            display: flex;
            justify-content: center;
            align-items: center;
            min-height: 100vh;
        }
        #math-container {
            font-size: 24px;
            color: #333;
        }
    </style>
</head>
<body>
    <div id="math-container"></div>
    <script>
        document.addEventListener('DOMContentLoaded', function() {
            katex.render("$latex", document.getElementById('math-container'), {
                displayMode: $displayMode,
                throwOnError: false
            });
        });
    </script>
</body>
</html>
        """.trimIndent()
    }
}

/**
 * 代码高亮器
 */
object CodeHighlighter {
    
    private val keywords = setOf(
        "sin", "cos", "tan", "cot", "sec", "csc",
        "log", "ln", "exp", "sqrt", "abs",
        "int", "sum", "prod", "lim", "frac", "begin", "end"
    )
    
    private val operators = setOf("+", "-", "*", "/", "^", "=", "<", ">", "(", ")", "{", "}", "[", "]")
    
    /**
     * 高亮 LaTeX 代码（返回 HTML）
     */
    fun highlightLatex(latex: String): String {
        var result = escapeHtml(latex)
        
        // 高亮命令
        result = result.replace(Regex("\\\\([a-zA-Z]+)")) { match ->
            val cmd = match.groupValues[1]
            val color = if (keywords.contains(cmd)) "#0066CC" else "#9933CC"
            "<span style='color:$color;font-weight:bold'>\\${match.groupValues[1]}</span>"
        }
        
        // 高亮数字
        result = result.replace(Regex("\\b(\\d+(\\.\\d+)?)\\b")) { match ->
            "<span style='color:#009688'>${match.value}</span>"
        }
        
        // 高亮括号
        for (op in listOf("{", "}", "(", ")", "[", "]")) {
            result = result.replace(op, "<span style='color:#FF5722'>$op</span>")
        }
        
        return "<pre style='font-family:monospace;font-size:14px;line-height:1.5;margin:0;padding:8px;background:#f5f5f5;border-radius:4px;overflow-x:auto;'>$result</pre>"
    }
    
    /**
     * 高亮可执行代码
     */
    fun highlightExecutableCode(code: String): String {
        var result = escapeHtml(code)
        
        // 高亮函数
        for (keyword in keywords) {
            result = result.replace(Regex("\\b$keyword\\b")) { match ->
                "<span style='color:#0066CC'>${match.value}</span>"
            }
        }
        
        // 高亮数字
        result = result.replace(Regex("\\b(\\d+(\\.\\d+)?)\\b")) { match ->
            "<span style='color:#009688'>${match.value}</span>"
        }
        
        // 高亮运算符
        result = result.replace(Regex("[+\\-*/^=<>]")) { match ->
            "<span style='color:#FF5722'>${match.value}</span>"
        }
        
        return "<pre style='font-family:monospace;font-size:14px;line-height:1.5;margin:0;padding:8px;background:#f5f5f5;border-radius:4px;'>$result</pre>"
    }
    
    private fun escapeHtml(text: String): String {
        return text
            .replace("&", "&amp;")
            .replace("<", "&lt;")
            .replace(">", "&gt;")
            .replace("\"", "&quot;")
    }
}
