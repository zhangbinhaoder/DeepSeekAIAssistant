/**
 * 数学模块 - 数据模型定义
 * 
 * 包含 LaTeX 处理、数学运算、图形绘制所需的所有数据结构
 */
package com.example.deepseekaiassistant.math

import java.io.Serializable
import java.util.Date

// ==============================================================================
// LaTeX 相关模型
// ==============================================================================

/**
 * LaTeX 表达式模型
 */
data class LatexExpression(
    val id: String = java.util.UUID.randomUUID().toString(),
    val raw: String,                          // 原始 LaTeX 代码
    val type: ExpressionType = ExpressionType.UNKNOWN,
    val createdAt: Date = Date(),
    val metadata: Map<String, Any> = emptyMap()
) : Serializable

/**
 * 表达式类型枚举
 */
enum class ExpressionType {
    EQUATION,           // 方程式
    INEQUALITY,         // 不等式
    FUNCTION,           // 函数定义
    DERIVATIVE,         // 求导
    INTEGRAL,           // 积分
    LIMIT,              // 极限
    SUMMATION,          // 求和
    PRODUCT,            // 求积
    MATRIX,             // 矩阵
    VECTOR,             // 向量
    SET,                // 集合
    LOGIC,              // 逻辑表达式
    TRIGONOMETRY,       // 三角函数
    LOGARITHM,          // 对数
    EXPONENTIAL,        // 指数
    POLYNOMIAL,         // 多项式
    FRACTION,           // 分数
    ROOT,               // 根式
    GEOMETRY,           // 几何
    STATISTICS,         // 统计
    UNKNOWN             // 未知类型
}

/**
 * LaTeX 语法校验结果
 */
data class LatexValidationResult(
    val isValid: Boolean,
    val errors: List<LatexError> = emptyList(),
    val warnings: List<LatexWarning> = emptyList(),
    val suggestions: List<String> = emptyList()
)

data class LatexError(
    val position: Int,
    val length: Int,
    val message: String,
    val errorType: String
)

data class LatexWarning(
    val position: Int,
    val message: String
)

// ==============================================================================
// 数学运算模型
// ==============================================================================

/**
 * 可执行的机器代码表示
 */
data class MachineCode(
    val id: String = java.util.UUID.randomUUID().toString(),
    val sourceLatex: String,                  // 原始 LaTeX
    val codeType: CodeType,                   // 代码类型
    val code: String,                         // 可执行代码
    val language: String = "kotlin",          // 语言标识
    val variables: List<Variable> = emptyList(),
    val functions: List<FunctionDef> = emptyList()
) : Serializable

enum class CodeType {
    EXPRESSION,         // 简单表达式
    EQUATION_SOLVE,     // 方程求解
    DERIVATIVE,         // 求导运算
    INTEGRAL,           // 积分运算
    LIMIT,              // 极限运算
    MATRIX_OP,          // 矩阵运算
    FUNCTION_EVAL,      // 函数求值
    PLOT,               // 图形绘制
    SIMPLIFY,           // 化简
    EXPAND,             // 展开
    FACTOR              // 因式分解
}

data class Variable(
    val name: String,
    val value: Double? = null,
    val symbol: Boolean = false,
    val domain: String? = null
)

data class FunctionDef(
    val name: String,
    val parameters: List<String>,
    val expression: String
)

/**
 * 运算结果
 */
sealed class CalculationResult {
    data class NumericResult(
        val value: Double,
        val formattedValue: String,
        val precision: Int = 10
    ) : CalculationResult()
    
    data class SymbolicResult(
        val expression: String,           // 符号表达式
        val latex: String,                // LaTeX 格式
        val simplified: Boolean = false
    ) : CalculationResult()
    
    data class MatrixResult(
        val rows: Int,
        val cols: Int,
        val data: List<List<Double>>,
        val latex: String
    ) : CalculationResult()
    
    data class SolutionSet(
        val solutions: List<String>,
        val latex: String,
        val solutionType: String
    ) : CalculationResult()
    
    data class GraphResult(
        val graphType: GraphType,
        val points: List<Point2D>,
        val domain: ClosedRange<Double>,
        val range: ClosedRange<Double>
    ) : CalculationResult()
    
    data class Error(
        val message: String,
        val errorCode: String,
        val details: String? = null
    ) : CalculationResult()
}

data class Point2D(val x: Double, val y: Double) : Serializable

enum class GraphType {
    CARTESIAN,          // 直角坐标系
    POLAR,              // 极坐标系
    PARAMETRIC,         // 参数方程
    IMPLICIT,           // 隐函数
    SCATTER,            // 散点图
    BAR,                // 柱状图
    PIE                 // 饼图
}

// ==============================================================================
// 过程展示模型
// ==============================================================================

/**
 * 处理过程步骤
 */
data class ProcessStep(
    val stepNumber: Int,
    val title: String,
    val description: String,
    val status: StepStatus,
    val timestamp: Long = System.currentTimeMillis(),
    val duration: Long = 0,
    val input: String? = null,
    val output: String? = null,
    val details: Map<String, Any> = emptyMap()
)

enum class StepStatus {
    PENDING,            // 等待执行
    RUNNING,            // 正在执行
    COMPLETED,          // 完成
    FAILED,             // 失败
    SKIPPED             // 跳过
}

/**
 * 完整处理流程
 */
data class ProcessFlow(
    val id: String = java.util.UUID.randomUUID().toString(),
    val inputLatex: String,
    val steps: MutableList<ProcessStep> = mutableListOf(),
    val startTime: Long = System.currentTimeMillis(),
    var endTime: Long? = null,
    var finalResult: CalculationResult? = null,
    var overallStatus: FlowStatus = FlowStatus.PENDING
)

enum class FlowStatus {
    PENDING,
    RUNNING,
    COMPLETED,
    FAILED,
    CANCELLED
}

// ==============================================================================
// 图形化模型
// ==============================================================================

/**
 * 函数图像配置
 */
data class GraphConfig(
    val xMin: Double = -10.0,
    val xMax: Double = 10.0,
    val yMin: Double = -10.0,
    val yMax: Double = 10.0,
    val gridEnabled: Boolean = true,
    val axisEnabled: Boolean = true,
    val resolution: Int = 500,            // 采样点数
    val lineWidth: Float = 2f,
    val lineColor: Int = 0xFF2196F3.toInt(),
    val backgroundColor: Int = 0xFFFFFFFF.toInt(),
    val gridColor: Int = 0xFFE0E0E0.toInt(),
    val axisColor: Int = 0xFF000000.toInt()
)

/**
 * 绘图数据
 */
data class PlotData(
    val id: String = java.util.UUID.randomUUID().toString(),
    val expression: String,
    val points: List<Point2D>,
    val graphType: GraphType = GraphType.CARTESIAN,
    val config: GraphConfig = GraphConfig(),
    val label: String? = null,
    val color: Int = 0xFF2196F3.toInt()
)

/**
 * 多函数图形组
 */
data class PlotGroup(
    val plots: List<PlotData>,
    val config: GraphConfig = GraphConfig(),
    val title: String? = null
)

// ==============================================================================
// 历史记录模型
// ==============================================================================

/**
 * 计算历史记录
 */
data class MathHistoryItem(
    val id: String = java.util.UUID.randomUUID().toString(),
    val latex: String,
    val displayedFormula: String,       // 渲染后的公式（可为空）
    val machineCode: String?,
    val result: String?,
    val resultType: String,
    val timestamp: Date = Date(),
    val isFavorite: Boolean = false,
    val tags: List<String> = emptyList()
) : Serializable

// ==============================================================================
// 常用公式模板
// ==============================================================================

/**
 * 公式模板
 */
data class FormulaTemplate(
    val id: String,
    val name: String,
    val category: String,
    val latex: String,
    val description: String,
    val variables: List<String> = emptyList(),
    val example: String? = null
) : Serializable

/**
 * 预定义的常用公式模板分类
 */
object FormulaTemplates {
    val ALGEBRA = listOf(
        FormulaTemplate("alg_quad", "一元二次公式", "代数", "x = \\frac{-b \\pm \\sqrt{b^2-4ac}}{2a}", "一元二次方程求根公式", listOf("a", "b", "c")),
        FormulaTemplate("alg_binomial", "二项式定理", "代数", "(a+b)^n = \\sum_{k=0}^{n} \\binom{n}{k} a^{n-k} b^k", "二项式展开公式", listOf("a", "b", "n")),
        FormulaTemplate("alg_factor_diff", "平方差公式", "代数", "a^2 - b^2 = (a+b)(a-b)", "因式分解", listOf("a", "b")),
        FormulaTemplate("alg_complete_sq", "完全平方公式", "代数", "(a \\pm b)^2 = a^2 \\pm 2ab + b^2", "完全平方展开", listOf("a", "b"))
    )
    
    val CALCULUS = listOf(
        FormulaTemplate("calc_deriv_power", "幂函数求导", "微积分", "\\frac{d}{dx}x^n = nx^{n-1}", "幂函数求导法则", listOf("n")),
        FormulaTemplate("calc_deriv_chain", "链式法则", "微积分", "\\frac{d}{dx}f(g(x)) = f'(g(x)) \\cdot g'(x)", "复合函数求导", listOf("f", "g")),
        FormulaTemplate("calc_int_power", "幂函数积分", "微积分", "\\int x^n dx = \\frac{x^{n+1}}{n+1} + C, n \\neq -1", "幂函数不定积分", listOf("n")),
        FormulaTemplate("calc_fund_thm", "微积分基本定理", "微积分", "\\int_a^b f'(x)dx = f(b) - f(a)", "牛顿-莱布尼茨公式", listOf("a", "b", "f"))
    )
    
    val TRIGONOMETRY = listOf(
        FormulaTemplate("trig_pythag", "勾股定理", "三角", "\\sin^2\\theta + \\cos^2\\theta = 1", "基本三角恒等式", listOf("θ")),
        FormulaTemplate("trig_sum", "和角公式", "三角", "\\sin(\\alpha + \\beta) = \\sin\\alpha\\cos\\beta + \\cos\\alpha\\sin\\beta", "正弦和角公式", listOf("α", "β")),
        FormulaTemplate("trig_double", "二倍角公式", "三角", "\\sin 2\\theta = 2\\sin\\theta\\cos\\theta", "正弦二倍角公式", listOf("θ"))
    )
    
    val LINEAR_ALGEBRA = listOf(
        FormulaTemplate("la_det_2x2", "2×2行列式", "线性代数", "\\det\\begin{pmatrix}a & b \\\\ c & d\\end{pmatrix} = ad - bc", "二阶行列式计算", listOf("a", "b", "c", "d")),
        FormulaTemplate("la_inv_2x2", "2×2逆矩阵", "线性代数", "A^{-1} = \\frac{1}{ad-bc}\\begin{pmatrix}d & -b \\\\ -c & a\\end{pmatrix}", "二阶矩阵求逆", listOf("a", "b", "c", "d"))
    )
    
    val STATISTICS = listOf(
        FormulaTemplate("stat_mean", "算术平均", "统计", "\\bar{x} = \\frac{1}{n}\\sum_{i=1}^{n}x_i", "样本均值", listOf("x_i", "n")),
        FormulaTemplate("stat_var", "方差", "统计", "\\sigma^2 = \\frac{1}{n}\\sum_{i=1}^{n}(x_i - \\bar{x})^2", "总体方差", listOf("x_i", "n")),
        FormulaTemplate("stat_normal", "正态分布", "统计", "f(x) = \\frac{1}{\\sigma\\sqrt{2\\pi}}e^{-\\frac{(x-\\mu)^2}{2\\sigma^2}}", "正态分布概率密度函数", listOf("μ", "σ"))
    )
    
    fun getAllTemplates(): List<FormulaTemplate> = ALGEBRA + CALCULUS + TRIGONOMETRY + LINEAR_ALGEBRA + STATISTICS
    
    fun getByCategory(category: String): List<FormulaTemplate> = when(category.lowercase()) {
        "代数", "algebra" -> ALGEBRA
        "微积分", "calculus" -> CALCULUS
        "三角", "trigonometry" -> TRIGONOMETRY
        "线性代数", "linear_algebra" -> LINEAR_ALGEBRA
        "统计", "statistics" -> STATISTICS
        else -> emptyList()
    }
}
