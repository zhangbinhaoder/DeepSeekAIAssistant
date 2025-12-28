/**
 * 数学运算引擎
 * 
 * 支持：
 * - 表达式求值（数值计算）
 * - 符号运算（化简、展开、因式分解）
 * - 方程求解
 * - 微积分（求导、积分）
 * - 矩阵运算
 * - 函数图像数据生成
 */
package com.example.deepseekaiassistant.math

import kotlin.math.*

/**
 * 数学运算引擎核心类
 */
class MathEngine {
    
    private val parser = LatexParser()
    private val expressionEvaluator = ExpressionEvaluator()
    
    /**
     * 计算表达式的数值结果
     */
    fun evaluate(latex: String, variables: Map<String, Double> = emptyMap()): CalculationResult {
        return try {
            val executable = parser.toExecutableExpression(latex)
            val value = expressionEvaluator.evaluate(executable, variables)
            CalculationResult.NumericResult(
                value = value,
                formattedValue = formatNumber(value)
            )
        } catch (e: Exception) {
            CalculationResult.Error(
                message = "计算错误: ${e.message}",
                errorCode = "EVAL_ERROR"
            )
        }
    }
    
    /**
     * 求解方程 (形如 f(x) = 0)
     */
    fun solveEquation(latex: String, variable: String = "x"): CalculationResult {
        return try {
            // 分离等式两边
            val parts = latex.split("=")
            if (parts.size != 2) {
                return CalculationResult.Error("无效的方程格式", "INVALID_EQUATION")
            }
            
            val leftExpr = parser.toExecutableExpression(parts[0])
            val rightExpr = parser.toExecutableExpression(parts[1])
            
            // 使用数值方法求解
            val solutions = numericalSolve(leftExpr, rightExpr, variable)
            
            if (solutions.isEmpty()) {
                CalculationResult.SolutionSet(
                    solutions = emptyList(),
                    latex = "\\text{无实数解}",
                    solutionType = "NO_SOLUTION"
                )
            } else {
                CalculationResult.SolutionSet(
                    solutions = solutions.map { formatNumber(it) },
                    latex = solutions.joinToString(", ") { "$variable = ${formatNumber(it)}" },
                    solutionType = "REAL"
                )
            }
        } catch (e: Exception) {
            CalculationResult.Error(
                message = "求解错误: ${e.message}",
                errorCode = "SOLVE_ERROR"
            )
        }
    }
    
    /**
     * 数值求导
     */
    fun derivative(latex: String, variable: String = "x", point: Double? = null): CalculationResult {
        return try {
            val expr = parser.toExecutableExpression(latex)
            
            if (point != null) {
                // 在指定点求导数值
                val h = 1e-8
                val f1 = expressionEvaluator.evaluate(expr, mapOf(variable to (point + h)))
                val f0 = expressionEvaluator.evaluate(expr, mapOf(variable to (point - h)))
                val deriv = (f1 - f0) / (2 * h)
                
                CalculationResult.NumericResult(
                    value = deriv,
                    formattedValue = formatNumber(deriv)
                )
            } else {
                // 返回符号导数（简化实现）
                val symbolicDeriv = symbolicDerivative(latex, variable)
                CalculationResult.SymbolicResult(
                    expression = symbolicDeriv,
                    latex = symbolicDeriv
                )
            }
        } catch (e: Exception) {
            CalculationResult.Error(
                message = "求导错误: ${e.message}",
                errorCode = "DERIV_ERROR"
            )
        }
    }
    
    /**
     * 数值积分 (定积分)
     */
    fun integrate(latex: String, variable: String = "x", lower: Double, upper: Double): CalculationResult {
        return try {
            val expr = parser.toExecutableExpression(latex)
            
            // 使用 Simpson 法则
            val n = 1000
            val h = (upper - lower) / n
            var sum = 0.0
            
            for (i in 0..n) {
                val x = lower + i * h
                val fx = expressionEvaluator.evaluate(expr, mapOf(variable to x))
                
                sum += when {
                    i == 0 || i == n -> fx
                    i % 2 == 1 -> 4 * fx
                    else -> 2 * fx
                }
            }
            
            val result = sum * h / 3
            
            CalculationResult.NumericResult(
                value = result,
                formattedValue = formatNumber(result)
            )
        } catch (e: Exception) {
            CalculationResult.Error(
                message = "积分错误: ${e.message}",
                errorCode = "INTEGRAL_ERROR"
            )
        }
    }
    
    /**
     * 求极限
     */
    fun limit(latex: String, variable: String, approaches: Double): CalculationResult {
        return try {
            val expr = parser.toExecutableExpression(latex)
            
            // 数值逼近
            val deltas = listOf(0.1, 0.01, 0.001, 0.0001, 0.00001)
            val leftValues = mutableListOf<Double>()
            val rightValues = mutableListOf<Double>()
            
            for (delta in deltas) {
                try {
                    leftValues.add(expressionEvaluator.evaluate(expr, mapOf(variable to (approaches - delta))))
                } catch (_: Exception) {}
                try {
                    rightValues.add(expressionEvaluator.evaluate(expr, mapOf(variable to (approaches + delta))))
                } catch (_: Exception) {}
            }
            
            val leftLimit = if (leftValues.isNotEmpty()) leftValues.last() else Double.NaN
            val rightLimit = if (rightValues.isNotEmpty()) rightValues.last() else Double.NaN
            
            when {
                leftLimit.isNaN() && rightLimit.isNaN() -> 
                    CalculationResult.Error("极限不存在", "LIMIT_DNE")
                abs(leftLimit - rightLimit) < 1e-6 -> 
                    CalculationResult.NumericResult(
                        value = (leftLimit + rightLimit) / 2,
                        formattedValue = formatNumber((leftLimit + rightLimit) / 2)
                    )
                leftLimit.isInfinite() || rightLimit.isInfinite() ->
                    CalculationResult.SymbolicResult(
                        expression = if (leftLimit > 0 || rightLimit > 0) "+∞" else "-∞",
                        latex = if (leftLimit > 0 || rightLimit > 0) "+\\infty" else "-\\infty"
                    )
                else -> 
                    CalculationResult.Error("极限不存在（左右极限不等）", "LIMIT_DNE")
            }
        } catch (e: Exception) {
            CalculationResult.Error(
                message = "求极限错误: ${e.message}",
                errorCode = "LIMIT_ERROR"
            )
        }
    }
    
    /**
     * 生成函数图像数据点
     */
    fun generatePlotData(latex: String, config: GraphConfig = GraphConfig()): PlotData {
        val expr = parser.toExecutableExpression(latex)
        val points = mutableListOf<Point2D>()
        
        val step = (config.xMax - config.xMin) / config.resolution
        var x = config.xMin
        
        while (x <= config.xMax) {
            try {
                val y = expressionEvaluator.evaluate(expr, mapOf("x" to x))
                if (y.isFinite() && y >= config.yMin && y <= config.yMax) {
                    points.add(Point2D(x, y))
                }
            } catch (_: Exception) {
                // 跳过无法计算的点
            }
            x += step
        }
        
        return PlotData(
            expression = latex,
            points = points,
            config = config
        )
    }
    
    /**
     * 矩阵运算
     */
    fun matrixOperation(operation: String, matrices: List<List<List<Double>>>): CalculationResult {
        return try {
            when (operation.lowercase()) {
                "add" -> {
                    if (matrices.size < 2) throw IllegalArgumentException("加法需要至少两个矩阵")
                    val result = matrixAdd(matrices[0], matrices[1])
                    matrixToResult(result)
                }
                "multiply", "mul" -> {
                    if (matrices.size < 2) throw IllegalArgumentException("乘法需要至少两个矩阵")
                    val result = matrixMultiply(matrices[0], matrices[1])
                    matrixToResult(result)
                }
                "determinant", "det" -> {
                    val det = determinant(matrices[0])
                    CalculationResult.NumericResult(det, formatNumber(det))
                }
                "transpose", "t" -> {
                    val result = transpose(matrices[0])
                    matrixToResult(result)
                }
                "inverse", "inv" -> {
                    val result = inverse2x2(matrices[0])
                    matrixToResult(result)
                }
                else -> CalculationResult.Error("未知矩阵运算: $operation", "UNKNOWN_OP")
            }
        } catch (e: Exception) {
            CalculationResult.Error(
                message = "矩阵运算错误: ${e.message}",
                errorCode = "MATRIX_ERROR"
            )
        }
    }
    
    // ======================== 私有辅助方法 ========================
    
    private fun formatNumber(value: Double): String {
        return when {
            value.isNaN() -> "NaN"
            value.isInfinite() -> if (value > 0) "+∞" else "-∞"
            abs(value) < 1e-10 -> "0"
            abs(value) >= 1e6 || abs(value) < 1e-4 -> String.format("%.6e", value)
            value == value.toLong().toDouble() -> value.toLong().toString()
            else -> String.format("%.10f", value).trimEnd('0').trimEnd('.')
        }
    }
    
    private fun numericalSolve(leftExpr: String, rightExpr: String, variable: String): List<Double> {
        val solutions = mutableListOf<Double>()
        val searchRange = -100.0..100.0
        val step = 0.1
        
        var x = searchRange.start
        while (x <= searchRange.endInclusive) {
            try {
                val leftVal = expressionEvaluator.evaluate(leftExpr, mapOf(variable to x))
                val rightVal = expressionEvaluator.evaluate(rightExpr, mapOf(variable to x))
                val f = leftVal - rightVal
                
                // 检查下一个点
                val nextX = x + step
                val nextLeftVal = expressionEvaluator.evaluate(leftExpr, mapOf(variable to nextX))
                val nextRightVal = expressionEvaluator.evaluate(rightExpr, mapOf(variable to nextX))
                val nextF = nextLeftVal - nextRightVal
                
                // 如果符号改变，使用二分法精确求解
                if (f * nextF < 0) {
                    val root = bisection(leftExpr, rightExpr, variable, x, nextX)
                    if (root != null && solutions.none { abs(it - root) < 1e-6 }) {
                        solutions.add(root)
                    }
                } else if (abs(f) < 1e-10 && solutions.none { abs(it - x) < 1e-6 }) {
                    solutions.add(x)
                }
            } catch (_: Exception) {}
            x += step
        }
        
        return solutions.sorted()
    }
    
    private fun bisection(leftExpr: String, rightExpr: String, variable: String, a: Double, b: Double): Double? {
        var low = a
        var high = b
        
        repeat(100) {
            val mid = (low + high) / 2
            val fLow = expressionEvaluator.evaluate(leftExpr, mapOf(variable to low)) - 
                       expressionEvaluator.evaluate(rightExpr, mapOf(variable to low))
            val fMid = expressionEvaluator.evaluate(leftExpr, mapOf(variable to mid)) -
                       expressionEvaluator.evaluate(rightExpr, mapOf(variable to mid))
            
            if (abs(fMid) < 1e-10) return mid
            
            if (fLow * fMid < 0) {
                high = mid
            } else {
                low = mid
            }
        }
        
        return (low + high) / 2
    }
    
    private fun symbolicDerivative(latex: String, variable: String): String {
        // 简化的符号求导（仅支持基本形式）
        return when {
            latex.matches(Regex("\\s*$variable\\s*\\^\\s*\\{?(\\d+)\\}?\\s*")) -> {
                val n = Regex("\\^\\s*\\{?(\\d+)\\}?").find(latex)?.groupValues?.get(1)?.toInt() ?: 1
                if (n == 1) "1"
                else if (n == 2) "2$variable"
                else "$n$variable^{${n-1}}"
            }
            latex.trim() == variable -> "1"
            latex.matches(Regex("\\d+(\\.\\d+)?")) -> "0"
            latex.contains("\\sin") -> latex.replace("\\sin", "\\cos")
            latex.contains("\\cos") -> "-\\sin($variable)"
            latex.contains("\\ln") -> "\\frac{1}{$variable}"
            latex.contains("e^") -> latex
            else -> "\\frac{d}{d$variable}($latex)"
        }
    }
    
    // 矩阵辅助方法
    private fun matrixAdd(a: List<List<Double>>, b: List<List<Double>>): List<List<Double>> {
        require(a.size == b.size && a[0].size == b[0].size) { "矩阵维度不匹配" }
        return a.mapIndexed { i, row -> row.mapIndexed { j, v -> v + b[i][j] } }
    }
    
    private fun matrixMultiply(a: List<List<Double>>, b: List<List<Double>>): List<List<Double>> {
        require(a[0].size == b.size) { "矩阵维度不匹配" }
        val rows = a.size
        val cols = b[0].size
        val k = b.size
        
        return List(rows) { i ->
            List(cols) { j ->
                (0 until k).sumOf { l -> a[i][l] * b[l][j] }
            }
        }
    }
    
    private fun determinant(matrix: List<List<Double>>): Double {
        require(matrix.size == matrix[0].size) { "行列式需要方阵" }
        return when (matrix.size) {
            1 -> matrix[0][0]
            2 -> matrix[0][0] * matrix[1][1] - matrix[0][1] * matrix[1][0]
            else -> {
                var det = 0.0
                for (j in matrix[0].indices) {
                    val minor = matrix.drop(1).map { row -> row.filterIndexed { index, _ -> index != j } }
                    det += matrix[0][j] * determinant(minor) * if (j % 2 == 0) 1 else -1
                }
                det
            }
        }
    }
    
    private fun transpose(matrix: List<List<Double>>): List<List<Double>> {
        return List(matrix[0].size) { j ->
            List(matrix.size) { i ->
                matrix[i][j]
            }
        }
    }
    
    private fun inverse2x2(matrix: List<List<Double>>): List<List<Double>> {
        require(matrix.size == 2 && matrix[0].size == 2) { "目前仅支持2x2矩阵求逆" }
        val det = determinant(matrix)
        require(abs(det) > 1e-10) { "矩阵不可逆" }
        
        return listOf(
            listOf(matrix[1][1] / det, -matrix[0][1] / det),
            listOf(-matrix[1][0] / det, matrix[0][0] / det)
        )
    }
    
    private fun matrixToResult(matrix: List<List<Double>>): CalculationResult.MatrixResult {
        val latex = buildString {
            append("\\begin{pmatrix}")
            matrix.forEachIndexed { i, row ->
                append(row.joinToString(" & ") { formatNumber(it) })
                if (i < matrix.size - 1) append(" \\\\ ")
            }
            append("\\end{pmatrix}")
        }
        return CalculationResult.MatrixResult(
            rows = matrix.size,
            cols = matrix[0].size,
            data = matrix,
            latex = latex
        )
    }
}

/**
 * 表达式求值器
 * 支持基本数学运算和函数
 */
class ExpressionEvaluator {
    
    /**
     * 计算表达式的值
     */
    fun evaluate(expression: String, variables: Map<String, Double> = emptyMap()): Double {
        val expr = substituteVariables(expression, variables)
        return parseExpression(expr.toCharArray(), intArrayOf(0))
    }
    
    private fun substituteVariables(expression: String, variables: Map<String, Double>): String {
        var result = expression
        // 按变量名长度降序排序，避免替换冲突
        val sortedVars = variables.entries.sortedByDescending { it.key.length }
        for ((name, value) in sortedVars) {
            result = result.replace(Regex("\\b${Regex.escape(name)}\\b"), value.toString())
        }
        return result
    }
    
    private fun parseExpression(chars: CharArray, pos: IntArray): Double {
        var result = parseTerm(chars, pos)
        
        while (pos[0] < chars.size) {
            skipWhitespace(chars, pos)
            if (pos[0] >= chars.size) break
            
            when (chars[pos[0]]) {
                '+' -> {
                    pos[0]++
                    result += parseTerm(chars, pos)
                }
                '-' -> {
                    pos[0]++
                    result -= parseTerm(chars, pos)
                }
                else -> break
            }
        }
        
        return result
    }
    
    private fun parseTerm(chars: CharArray, pos: IntArray): Double {
        var result = parsePower(chars, pos)
        
        while (pos[0] < chars.size) {
            skipWhitespace(chars, pos)
            if (pos[0] >= chars.size) break
            
            when (chars[pos[0]]) {
                '*' -> {
                    pos[0]++
                    result *= parsePower(chars, pos)
                }
                '/' -> {
                    pos[0]++
                    val divisor = parsePower(chars, pos)
                    if (abs(divisor) < 1e-15) throw ArithmeticException("除以零")
                    result /= divisor
                }
                else -> break
            }
        }
        
        return result
    }
    
    private fun parsePower(chars: CharArray, pos: IntArray): Double {
        var base = parseFactor(chars, pos)
        
        skipWhitespace(chars, pos)
        if (pos[0] < chars.size && chars[pos[0]] == '^') {
            pos[0]++
            val exponent = parsePower(chars, pos) // 右结合
            base = base.pow(exponent)
        }
        
        return base
    }
    
    private fun parseFactor(chars: CharArray, pos: IntArray): Double {
        skipWhitespace(chars, pos)
        
        if (pos[0] >= chars.size) throw IllegalArgumentException("表达式不完整")
        
        // 处理负号
        if (chars[pos[0]] == '-') {
            pos[0]++
            return -parseFactor(chars, pos)
        }
        
        // 处理正号
        if (chars[pos[0]] == '+') {
            pos[0]++
            return parseFactor(chars, pos)
        }
        
        // 处理括号
        if (chars[pos[0]] == '(') {
            pos[0]++
            val result = parseExpression(chars, pos)
            skipWhitespace(chars, pos)
            if (pos[0] < chars.size && chars[pos[0]] == ')') {
                pos[0]++
            }
            return result
        }
        
        // 处理函数
        val funcStart = pos[0]
        while (pos[0] < chars.size && chars[pos[0]].isLetter()) {
            pos[0]++
        }
        
        if (pos[0] > funcStart) {
            val funcName = String(chars, funcStart, pos[0] - funcStart)
            skipWhitespace(chars, pos)
            
            if (pos[0] < chars.size && chars[pos[0]] == '(') {
                pos[0]++
                val arg = parseExpression(chars, pos)
                skipWhitespace(chars, pos)
                if (pos[0] < chars.size && chars[pos[0]] == ')') {
                    pos[0]++
                }
                return evaluateFunction(funcName, arg)
            } else {
                // 可能是常量
                return when (funcName.lowercase()) {
                    "pi" -> PI
                    "e" -> E
                    else -> throw IllegalArgumentException("未知变量或函数: $funcName")
                }
            }
        }
        
        // 处理数字
        return parseNumber(chars, pos)
    }
    
    private fun parseNumber(chars: CharArray, pos: IntArray): Double {
        skipWhitespace(chars, pos)
        
        val start = pos[0]
        
        // 整数部分
        while (pos[0] < chars.size && chars[pos[0]].isDigit()) {
            pos[0]++
        }
        
        // 小数部分
        if (pos[0] < chars.size && chars[pos[0]] == '.') {
            pos[0]++
            while (pos[0] < chars.size && chars[pos[0]].isDigit()) {
                pos[0]++
            }
        }
        
        // 科学计数法
        if (pos[0] < chars.size && (chars[pos[0]] == 'e' || chars[pos[0]] == 'E')) {
            pos[0]++
            if (pos[0] < chars.size && (chars[pos[0]] == '+' || chars[pos[0]] == '-')) {
                pos[0]++
            }
            while (pos[0] < chars.size && chars[pos[0]].isDigit()) {
                pos[0]++
            }
        }
        
        if (pos[0] == start) {
            throw IllegalArgumentException("期望数字，但在位置 $start 处遇到 '${if (pos[0] < chars.size) chars[pos[0]] else "EOF"}'")
        }
        
        return String(chars, start, pos[0] - start).toDouble()
    }
    
    private fun evaluateFunction(name: String, arg: Double): Double {
        return when (name.lowercase()) {
            "sin" -> sin(arg)
            "cos" -> cos(arg)
            "tan" -> tan(arg)
            "cot" -> 1.0 / tan(arg)
            "sec" -> 1.0 / cos(arg)
            "csc" -> 1.0 / sin(arg)
            "asin", "arcsin" -> asin(arg)
            "acos", "arccos" -> acos(arg)
            "atan", "arctan" -> atan(arg)
            "sinh" -> sinh(arg)
            "cosh" -> cosh(arg)
            "tanh" -> tanh(arg)
            "sqrt" -> sqrt(arg)
            "cbrt" -> cbrt(arg)
            "abs" -> abs(arg)
            "ln" -> ln(arg)
            "log", "log10" -> log10(arg)
            "log2" -> log2(arg)
            "exp" -> exp(arg)
            "floor" -> floor(arg)
            "ceil" -> ceil(arg)
            "round" -> round(arg)
            "sign" -> sign(arg)
            else -> throw IllegalArgumentException("未知函数: $name")
        }
    }
    
    private fun skipWhitespace(chars: CharArray, pos: IntArray) {
        while (pos[0] < chars.size && chars[pos[0]].isWhitespace()) {
            pos[0]++
        }
    }
}
