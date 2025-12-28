/**
 * LaTeX 解析器
 * 
 * 将 LaTeX 数学表达式解析为可计算的内部表示
 * 支持：方程、函数、微积分、矩阵等高等数学表达式
 */
package com.example.deepseekaiassistant.math

import java.util.regex.Pattern

/**
 * LaTeX 解析器核心类
 */
class LatexParser {
    
    companion object {
        // LaTeX 命令模式
        private val COMMAND_PATTERN = Pattern.compile("\\\\([a-zA-Z]+)")
        private val FRACTION_PATTERN = Pattern.compile("\\\\frac\\s*\\{([^{}]*)\\}\\s*\\{([^{}]*)\\}")
        private val SQRT_PATTERN = Pattern.compile("\\\\sqrt(?:\\[([^\\[\\]]*)\\])?\\s*\\{([^{}]*)\\}")
        private val POWER_PATTERN = Pattern.compile("([a-zA-Z0-9)]+)\\s*\\^\\s*\\{?([^{}\\s]+)\\}?")
        private val SUBSCRIPT_PATTERN = Pattern.compile("([a-zA-Z0-9]+)_\\{?([^{}\\s]+)\\}?")
        private val INTEGRAL_PATTERN = Pattern.compile("\\\\int(?:_\\{([^{}]*)\\})?(?:\\^\\{([^{}]*)\\})?\\s*(.+?)\\s*d([a-zA-Z])")
        private val SUM_PATTERN = Pattern.compile("\\\\sum_\\{([^{}]*)\\}\\^\\{([^{}]*)\\}\\s*(.+)")
        private val LIMIT_PATTERN = Pattern.compile("\\\\lim_\\{([^{}]*)\\s*\\\\to\\s*([^{}]*)\\}\\s*(.+)")
        private val DERIVATIVE_PATTERN = Pattern.compile("\\\\frac\\{d\\s*(.*)\\}\\{d\\s*([a-zA-Z])\\}")
        private val MATRIX_PATTERN = Pattern.compile("\\\\begin\\{(pmatrix|bmatrix|matrix)\\}(.+?)\\\\end\\{\\1\\}", Pattern.DOTALL)
        
        // 希腊字母映射
        val GREEK_LETTERS = mapOf(
            "alpha" to "α", "beta" to "β", "gamma" to "γ", "delta" to "δ",
            "epsilon" to "ε", "zeta" to "ζ", "eta" to "η", "theta" to "θ",
            "iota" to "ι", "kappa" to "κ", "lambda" to "λ", "mu" to "μ",
            "nu" to "ν", "xi" to "ξ", "pi" to "π", "rho" to "ρ",
            "sigma" to "σ", "tau" to "τ", "upsilon" to "υ", "phi" to "φ",
            "chi" to "χ", "psi" to "ψ", "omega" to "ω",
            "Gamma" to "Γ", "Delta" to "Δ", "Theta" to "Θ", "Lambda" to "Λ",
            "Xi" to "Ξ", "Pi" to "Π", "Sigma" to "Σ", "Phi" to "Φ",
            "Psi" to "Ψ", "Omega" to "Ω"
        )
        
        // 数学函数
        val MATH_FUNCTIONS = setOf(
            "sin", "cos", "tan", "cot", "sec", "csc",
            "arcsin", "arccos", "arctan", "sinh", "cosh", "tanh",
            "log", "ln", "lg", "exp", "sqrt", "abs",
            "floor", "ceil", "round", "sign", "max", "min"
        )
        
        // 运算符优先级
        val OPERATOR_PRECEDENCE = mapOf(
            "+" to 1, "-" to 1,
            "*" to 2, "/" to 2, "\\cdot" to 2, "\\times" to 2,
            "^" to 3
        )
    }
    
    /**
     * 解析 LaTeX 表达式
     */
    fun parse(latex: String): ParseResult {
        val tokens = mutableListOf<Token>()
        val errors = mutableListOf<LatexError>()
        
        try {
            val normalized = normalizeLatex(latex)
            val tokenized = tokenize(normalized)
            tokens.addAll(tokenized)
            
            // 验证括号匹配
            val bracketResult = validateBrackets(normalized)
            if (!bracketResult.isValid) {
                errors.addAll(bracketResult.errors)
            }
            
            // 检测表达式类型
            val expressionType = detectExpressionType(normalized)
            
            return ParseResult(
                success = errors.isEmpty(),
                tokens = tokens,
                expressionType = expressionType,
                normalizedLatex = normalized,
                errors = errors
            )
        } catch (e: Exception) {
            errors.add(LatexError(0, latex.length, "解析错误: ${e.message}", "PARSE_ERROR"))
            return ParseResult(
                success = false,
                tokens = tokens,
                expressionType = ExpressionType.UNKNOWN,
                normalizedLatex = latex,
                errors = errors
            )
        }
    }
    
    /**
     * 标准化 LaTeX 表达式
     */
    private fun normalizeLatex(latex: String): String {
        var normalized = latex.trim()
        
        // 移除首尾的 $ 符号（行内公式标记）
        normalized = normalized.removePrefix("$").removeSuffix("$")
        normalized = normalized.removePrefix("$$").removeSuffix("$$")
        
        // 处理常见的空格问题
        normalized = normalized.replace("\\,", " ")
        normalized = normalized.replace("\\;", " ")
        normalized = normalized.replace("\\:", " ")
        normalized = normalized.replace("\\!", "")
        normalized = normalized.replace("\\quad", " ")
        normalized = normalized.replace("\\qquad", "  ")
        
        // 标准化乘号
        normalized = normalized.replace("\\times", "*")
        normalized = normalized.replace("\\cdot", "*")
        
        return normalized
    }
    
    /**
     * 词法分析 - 将 LaTeX 分解为 Token
     */
    private fun tokenize(latex: String): List<Token> {
        val tokens = mutableListOf<Token>()
        var pos = 0
        
        while (pos < latex.length) {
            val char = latex[pos]
            
            when {
                char.isWhitespace() -> pos++
                
                char == '\\' -> {
                    // LaTeX 命令
                    val commandEnd = findCommandEnd(latex, pos + 1)
                    val command = latex.substring(pos + 1, commandEnd)
                    tokens.add(Token(TokenType.COMMAND, command, pos))
                    pos = commandEnd
                }
                
                char.isDigit() || (char == '.' && pos + 1 < latex.length && latex[pos + 1].isDigit()) -> {
                    // 数字
                    val numberEnd = findNumberEnd(latex, pos)
                    val number = latex.substring(pos, numberEnd)
                    tokens.add(Token(TokenType.NUMBER, number, pos))
                    pos = numberEnd
                }
                
                char.isLetter() -> {
                    // 变量或函数名
                    val identEnd = findIdentifierEnd(latex, pos)
                    val ident = latex.substring(pos, identEnd)
                    val type = if (MATH_FUNCTIONS.contains(ident)) TokenType.FUNCTION else TokenType.VARIABLE
                    tokens.add(Token(type, ident, pos))
                    pos = identEnd
                }
                
                char in "+-*/" -> {
                    tokens.add(Token(TokenType.OPERATOR, char.toString(), pos))
                    pos++
                }
                
                char == '^' -> {
                    tokens.add(Token(TokenType.POWER, char.toString(), pos))
                    pos++
                }
                
                char == '_' -> {
                    tokens.add(Token(TokenType.SUBSCRIPT, char.toString(), pos))
                    pos++
                }
                
                char == '=' -> {
                    tokens.add(Token(TokenType.EQUALS, char.toString(), pos))
                    pos++
                }
                
                char in "<>" -> {
                    // 比较运算符
                    if (pos + 1 < latex.length && latex[pos + 1] == '=') {
                        tokens.add(Token(TokenType.COMPARISON, "$char=", pos))
                        pos += 2
                    } else {
                        tokens.add(Token(TokenType.COMPARISON, char.toString(), pos))
                        pos++
                    }
                }
                
                char == '(' -> {
                    tokens.add(Token(TokenType.LPAREN, char.toString(), pos))
                    pos++
                }
                
                char == ')' -> {
                    tokens.add(Token(TokenType.RPAREN, char.toString(), pos))
                    pos++
                }
                
                char == '{' -> {
                    tokens.add(Token(TokenType.LBRACE, char.toString(), pos))
                    pos++
                }
                
                char == '}' -> {
                    tokens.add(Token(TokenType.RBRACE, char.toString(), pos))
                    pos++
                }
                
                char == '[' -> {
                    tokens.add(Token(TokenType.LBRACKET, char.toString(), pos))
                    pos++
                }
                
                char == ']' -> {
                    tokens.add(Token(TokenType.RBRACKET, char.toString(), pos))
                    pos++
                }
                
                char == ',' -> {
                    tokens.add(Token(TokenType.COMMA, char.toString(), pos))
                    pos++
                }
                
                char == '|' -> {
                    tokens.add(Token(TokenType.PIPE, char.toString(), pos))
                    pos++
                }
                
                else -> {
                    // 未知字符，跳过
                    pos++
                }
            }
        }
        
        return tokens
    }
    
    private fun findCommandEnd(latex: String, start: Int): Int {
        var pos = start
        while (pos < latex.length && latex[pos].isLetter()) {
            pos++
        }
        return pos
    }
    
    private fun findNumberEnd(latex: String, start: Int): Int {
        var pos = start
        var hasDecimal = false
        while (pos < latex.length) {
            val c = latex[pos]
            when {
                c.isDigit() -> pos++
                c == '.' && !hasDecimal -> {
                    hasDecimal = true
                    pos++
                }
                else -> break
            }
        }
        return pos
    }
    
    private fun findIdentifierEnd(latex: String, start: Int): Int {
        var pos = start
        while (pos < latex.length && (latex[pos].isLetterOrDigit() || latex[pos] == '_')) {
            pos++
        }
        return pos
    }
    
    /**
     * 验证括号匹配
     */
    private fun validateBrackets(latex: String): LatexValidationResult {
        val stack = mutableListOf<Pair<Char, Int>>()
        val errors = mutableListOf<LatexError>()
        
        val pairs = mapOf(')' to '(', '}' to '{', ']' to '[')
        
        for (i in latex.indices) {
            val char = latex[i]
            when (char) {
                '(', '{', '[' -> stack.add(char to i)
                ')', '}', ']' -> {
                    if (stack.isEmpty()) {
                        errors.add(LatexError(i, 1, "未匹配的右括号 '$char'", "UNMATCHED_BRACKET"))
                    } else if (stack.last().first != pairs[char]) {
                        errors.add(LatexError(i, 1, "括号类型不匹配", "MISMATCHED_BRACKET"))
                    } else {
                        stack.removeAt(stack.lastIndex)
                    }
                }
            }
        }
        
        for ((char, pos) in stack) {
            errors.add(LatexError(pos, 1, "未闭合的左括号 '$char'", "UNCLOSED_BRACKET"))
        }
        
        return LatexValidationResult(errors.isEmpty(), errors)
    }
    
    /**
     * 检测表达式类型
     */
    private fun detectExpressionType(latex: String): ExpressionType {
        return when {
            latex.contains("\\int") -> ExpressionType.INTEGRAL
            latex.contains("\\frac{d") && latex.contains("}{d") -> ExpressionType.DERIVATIVE
            latex.contains("\\lim") -> ExpressionType.LIMIT
            latex.contains("\\sum") -> ExpressionType.SUMMATION
            latex.contains("\\prod") -> ExpressionType.PRODUCT
            latex.contains("\\begin{pmatrix}") || latex.contains("\\begin{bmatrix}") -> ExpressionType.MATRIX
            latex.contains("\\vec") || latex.contains("\\overrightarrow") -> ExpressionType.VECTOR
            latex.contains("\\sin") || latex.contains("\\cos") || latex.contains("\\tan") -> ExpressionType.TRIGONOMETRY
            latex.contains("\\log") || latex.contains("\\ln") -> ExpressionType.LOGARITHM
            latex.contains("\\sqrt") -> ExpressionType.ROOT
            latex.contains("\\frac") -> ExpressionType.FRACTION
            latex.contains("=") && !latex.contains("\\neq") -> ExpressionType.EQUATION
            latex.contains("<") || latex.contains(">") || latex.contains("\\leq") || latex.contains("\\geq") -> ExpressionType.INEQUALITY
            latex.matches(Regex(".*f\\s*\\(.*\\).*")) -> ExpressionType.FUNCTION
            else -> ExpressionType.POLYNOMIAL
        }
    }
    
    /**
     * 将 LaTeX 转换为可执行的数学表达式字符串
     */
    fun toExecutableExpression(latex: String): String {
        var expr = normalizeLatex(latex)
        
        // 转换分数
        expr = convertFractions(expr)
        
        // 转换根号
        expr = convertRoots(expr)
        
        // 转换幂
        expr = convertPowers(expr)
        
        // 转换三角函数
        expr = convertTrigFunctions(expr)
        
        // 转换对数
        expr = convertLogarithms(expr)
        
        // 转换绝对值
        expr = expr.replace("\\left|", "abs(")
        expr = expr.replace("\\right|", ")")
        expr = expr.replace("|", "")
        
        // 移除剩余的 LaTeX 命令
        expr = expr.replace(Regex("\\\\[a-zA-Z]+"), "")
        
        // 清理多余的空格和括号
        expr = expr.replace(Regex("\\s+"), "")
        expr = expr.replace("{", "(")
        expr = expr.replace("}", ")")
        
        // 添加隐式乘法
        expr = addImplicitMultiplication(expr)
        
        return expr
    }
    
    private fun convertFractions(expr: String): String {
        var result = expr
        var matcher = FRACTION_PATTERN.matcher(result)
        while (matcher.find()) {
            val numerator = matcher.group(1)
            val denominator = matcher.group(2)
            result = result.replaceFirst(Regex.escape(matcher.group(0)).toRegex(), "(($numerator)/($denominator))")
            matcher = FRACTION_PATTERN.matcher(result)
        }
        return result
    }
    
    private fun convertRoots(expr: String): String {
        var result = expr
        var matcher = SQRT_PATTERN.matcher(result)
        while (matcher.find()) {
            val index = matcher.group(1)
            val radicand = matcher.group(2)
            result = if (index != null) {
                result.replaceFirst(Regex.escape(matcher.group(0)).toRegex(), "(($radicand)^(1/$index))")
            } else {
                result.replaceFirst(Regex.escape(matcher.group(0)).toRegex(), "sqrt($radicand)")
            }
            matcher = SQRT_PATTERN.matcher(result)
        }
        return result
    }
    
    private fun convertPowers(expr: String): String {
        var result = expr
        // 处理 x^{n} 形式
        result = result.replace(Regex("\\^\\{([^{}]+)\\}"), "^($1)")
        return result
    }
    
    private fun convertTrigFunctions(expr: String): String {
        var result = expr
        val trigFuncs = listOf("sin", "cos", "tan", "cot", "sec", "csc", "arcsin", "arccos", "arctan")
        for (func in trigFuncs) {
            result = result.replace("\\$func", func)
        }
        return result
    }
    
    private fun convertLogarithms(expr: String): String {
        var result = expr
        result = result.replace("\\ln", "ln")
        result = result.replace("\\log", "log")
        result = result.replace("\\lg", "log10")
        return result
    }
    
    private fun addImplicitMultiplication(expr: String): String {
        val result = StringBuilder()
        var i = 0
        while (i < expr.length) {
            result.append(expr[i])
            if (i + 1 < expr.length) {
                val curr = expr[i]
                val next = expr[i + 1]
                // 需要添加隐式乘法的情况
                if ((curr.isDigit() && (next.isLetter() || next == '(')) ||
                    (curr == ')' && (next.isLetterOrDigit() || next == '(')) ||
                    (curr.isLetter() && next == '(')) {
                    // 检查是否是函数调用
                    if (!(curr.isLetter() && next == '(')) {
                        result.append('*')
                    }
                }
            }
            i++
        }
        return result.toString()
    }
}

/**
 * Token 类型
 */
enum class TokenType {
    NUMBER,
    VARIABLE,
    FUNCTION,
    COMMAND,
    OPERATOR,
    POWER,
    SUBSCRIPT,
    EQUALS,
    COMPARISON,
    LPAREN,
    RPAREN,
    LBRACE,
    RBRACE,
    LBRACKET,
    RBRACKET,
    COMMA,
    PIPE,
    EOF
}

/**
 * Token 数据类
 */
data class Token(
    val type: TokenType,
    val value: String,
    val position: Int
)

/**
 * 解析结果
 */
data class ParseResult(
    val success: Boolean,
    val tokens: List<Token>,
    val expressionType: ExpressionType,
    val normalizedLatex: String,
    val errors: List<LatexError>
)
