package com.example.deepseekaiassistant.agent

import android.util.Log
import org.json.JSONArray
import org.json.JSONObject
import java.io.File

/**
 * Rust Core Library - High-performance native functions
 * 
 * Provides Kotlin bindings for Rust image processing, strategy calculation,
 * and memory parsing engines.
 */
object AgentCore {
    private const val TAG = "AgentCore"
    private var isLoaded = false
    
    /**
     * Load the native library
     */
    fun load(): Boolean {
        if (isLoaded) return true
        
        return try {
            System.loadLibrary("agent_core")
            init()
            isLoaded = true
            Log.i(TAG, "Agent Core library loaded successfully, version: ${getVersion()}")
            true
        } catch (e: UnsatisfiedLinkError) {
            Log.w(TAG, "Failed to load agent_core library: ${e.message}")
            false
        }
    }
    
    /**
     * Check if library is loaded
     */
    fun isAvailable(): Boolean = isLoaded
    
    // Native methods
    private external fun init()
    external fun getVersion(): String
}

/**
 * Image Engine - High-performance image analysis
 */
object ImageEngineNative {
    
    data class DetectedElement(
        val elementType: String,
        val bounds: Rect,
        val confidence: Float,
        val extraData: String?
    )
    
    data class Rect(
        val x: Int,
        val y: Int,
        val width: Int,
        val height: Int
    ) {
        val centerX: Int get() = x + width / 2
        val centerY: Int get() = y + height / 2
    }
    
    /**
     * Detect health bars in image
     * @param pixels ARGB pixel data from Bitmap
     * @param width Image width
     * @param height Image height
     * @return List of detected health bar elements
     */
    fun detectHealthBars(pixels: ByteArray, width: Int, height: Int): List<DetectedElement> {
        if (!AgentCore.isAvailable()) return emptyList()
        
        return try {
            val json = detectHealthBarsNative(pixels, width, height)
            parseDetectedElements(json)
        } catch (e: Exception) {
            Log.e("ImageEngineNative", "detectHealthBars error: ${e.message}")
            emptyList()
        }
    }
    
    /**
     * Detect skill buttons in image
     */
    fun detectSkillButtons(pixels: ByteArray, width: Int, height: Int): List<DetectedElement> {
        if (!AgentCore.isAvailable()) return emptyList()
        
        return try {
            val json = detectSkillButtonsNative(pixels, width, height)
            parseDetectedElements(json)
        } catch (e: Exception) {
            Log.e("ImageEngineNative", "detectSkillButtons error: ${e.message}")
            emptyList()
        }
    }
    
    /**
     * Detect joystick in image
     */
    fun detectJoystick(pixels: ByteArray, width: Int, height: Int): DetectedElement? {
        if (!AgentCore.isAvailable()) return null
        
        return try {
            val json = detectJoystickNative(pixels, width, height)
            if (json == "null" || json.contains("error")) null
            else parseDetectedElement(JSONObject(json))
        } catch (e: Exception) {
            Log.e("ImageEngineNative", "detectJoystick error: ${e.message}")
            null
        }
    }
    
    /**
     * Analyze eliminate game board
     * @return 2D array of color codes (0-7)
     */
    fun analyzeEliminateBoard(
        pixels: ByteArray,
        width: Int,
        height: Int,
        gridX: Int,
        gridY: Int,
        gridW: Int,
        gridH: Int,
        rows: Int,
        cols: Int
    ): Array<IntArray> {
        if (!AgentCore.isAvailable()) return Array(rows) { IntArray(cols) }
        
        return try {
            val json = analyzeEliminateBoardNative(pixels, width, height, gridX, gridY, gridW, gridH, rows, cols)
            val arr = JSONArray(json)
            Array(arr.length()) { i ->
                val row = arr.getJSONArray(i)
                IntArray(row.length()) { j -> row.getInt(j) }
            }
        } catch (e: Exception) {
            Log.e("ImageEngineNative", "analyzeEliminateBoard error: ${e.message}")
            Array(rows) { IntArray(cols) }
        }
    }
    
    private fun parseDetectedElements(json: String): List<DetectedElement> {
        return try {
            val arr = JSONArray(json)
            (0 until arr.length()).map { parseDetectedElement(arr.getJSONObject(it)) }
        } catch (e: Exception) {
            emptyList()
        }
    }
    
    private fun parseDetectedElement(obj: JSONObject): DetectedElement {
        val bounds = obj.getJSONObject("bounds")
        return DetectedElement(
            elementType = obj.getString("element_type"),
            bounds = Rect(
                x = bounds.getInt("x"),
                y = bounds.getInt("y"),
                width = bounds.getInt("width"),
                height = bounds.getInt("height")
            ),
            confidence = obj.getDouble("confidence").toFloat(),
            extraData = obj.optString("extra_data", null)
        )
    }
    
    // Native methods
    private external fun detectHealthBarsNative(pixels: ByteArray, width: Int, height: Int): String
    private external fun detectSkillButtonsNative(pixels: ByteArray, width: Int, height: Int): String
    private external fun detectJoystickNative(pixels: ByteArray, width: Int, height: Int): String
    private external fun analyzeEliminateBoardNative(
        pixels: ByteArray, width: Int, height: Int,
        gridX: Int, gridY: Int, gridW: Int, gridH: Int,
        rows: Int, cols: Int
    ): String
}

/**
 * Strategy Engine - Game AI algorithms
 */
object StrategyEngineNative {
    
    data class EliminateMove(
        val fromRow: Int,
        val fromCol: Int,
        val toRow: Int,
        val toCol: Int,
        val score: Int,
        val eliminates: Int,
        val createsSpecial: Boolean
    )
    
    data class GridPos(val x: Int, val y: Int)
    
    data class PathResult(
        val path: List<GridPos>,
        val totalCost: Int,
        val found: Boolean
    )
    
    data class CombatDecision(
        val action: String,
        val targetPos: GridPos?,
        val priority: Int,
        val reason: String
    )
    
    /**
     * Find the best move for eliminate game
     */
    fun findBestEliminateMove(board: Array<IntArray>): EliminateMove? {
        if (!AgentCore.isAvailable()) return null
        
        return try {
            val boardJson = JSONArray(board.map { JSONArray(it.toList()) }).toString()
            val json = findBestEliminateMoveNative(boardJson)
            if (json == "null" || json.contains("error")) null
            else parseEliminateMove(JSONObject(json))
        } catch (e: Exception) {
            Log.e("StrategyEngineNative", "findBestEliminateMove error: ${e.message}")
            null
        }
    }
    
    /**
     * Find top N best moves for eliminate game
     */
    fun findBestEliminateMoves(board: Array<IntArray>, n: Int): List<EliminateMove> {
        if (!AgentCore.isAvailable()) return emptyList()
        
        return try {
            val boardJson = JSONArray(board.map { JSONArray(it.toList()) }).toString()
            val json = findBestEliminateMovesNative(boardJson, n)
            val arr = JSONArray(json)
            (0 until arr.length()).map { parseEliminateMove(arr.getJSONObject(it)) }
        } catch (e: Exception) {
            Log.e("StrategyEngineNative", "findBestEliminateMoves error: ${e.message}")
            emptyList()
        }
    }
    
    /**
     * Find path using A* algorithm
     */
    fun findPath(
        start: GridPos,
        goal: GridPos,
        obstacles: List<GridPos>,
        gridWidth: Int,
        gridHeight: Int,
        use8Dir: Boolean = false
    ): PathResult {
        if (!AgentCore.isAvailable()) {
            return PathResult(emptyList(), -1, false)
        }
        
        return try {
            val obstaclesJson = JSONArray(obstacles.map { JSONArray(listOf(it.x, it.y)) }).toString()
            val json = findPathNative(
                start.x, start.y, goal.x, goal.y,
                obstaclesJson, gridWidth, gridHeight, use8Dir
            )
            parsePathResult(JSONObject(json))
        } catch (e: Exception) {
            Log.e("StrategyEngineNative", "findPath error: ${e.message}")
            PathResult(emptyList(), -1, false)
        }
    }
    
    /**
     * Analyze combat situation for MOBA games
     */
    fun analyzeCombat(
        selfPos: GridPos,
        selfHpPercent: Float,
        enemies: List<Pair<GridPos, Float>>,
        allies: List<GridPos>,
        skillReady: List<Boolean>,
        inTowerRange: Boolean
    ): List<CombatDecision> {
        if (!AgentCore.isAvailable()) return emptyList()
        
        return try {
            val enemiesJson = JSONArray(enemies.map { 
                JSONArray(listOf(it.first.x, it.first.y, it.second)) 
            }).toString()
            val alliesJson = JSONArray(allies.map { 
                JSONArray(listOf(it.x, it.y)) 
            }).toString()
            val skillJson = JSONArray(skillReady).toString()
            
            val json = analyzeCombatNative(
                selfPos.x, selfPos.y, selfHpPercent,
                enemiesJson, alliesJson, skillJson, inTowerRange
            )
            
            val arr = JSONArray(json)
            (0 until arr.length()).map { parseCombatDecision(arr.getJSONObject(it)) }
        } catch (e: Exception) {
            Log.e("StrategyEngineNative", "analyzeCombat error: ${e.message}")
            emptyList()
        }
    }
    
    private fun parseEliminateMove(obj: JSONObject): EliminateMove {
        return EliminateMove(
            fromRow = obj.getInt("from_row"),
            fromCol = obj.getInt("from_col"),
            toRow = obj.getInt("to_row"),
            toCol = obj.getInt("to_col"),
            score = obj.getInt("score"),
            eliminates = obj.getInt("eliminates"),
            createsSpecial = obj.getBoolean("creates_special")
        )
    }
    
    private fun parsePathResult(obj: JSONObject): PathResult {
        val pathArr = obj.getJSONArray("path")
        val path = (0 until pathArr.length()).map { i ->
            val pos = pathArr.getJSONObject(i)
            GridPos(pos.getInt("x"), pos.getInt("y"))
        }
        return PathResult(
            path = path,
            totalCost = obj.getInt("total_cost"),
            found = obj.getBoolean("found")
        )
    }
    
    private fun parseCombatDecision(obj: JSONObject): CombatDecision {
        val targetPos = if (obj.has("target_pos") && !obj.isNull("target_pos")) {
            val pos = obj.getJSONObject("target_pos")
            GridPos(pos.getInt("x"), pos.getInt("y"))
        } else null
        
        return CombatDecision(
            action = obj.getString("action"),
            targetPos = targetPos,
            priority = obj.getInt("priority"),
            reason = obj.getString("reason")
        )
    }
    
    // Native methods
    private external fun findBestEliminateMoveNative(boardJson: String): String
    private external fun findBestEliminateMovesNative(boardJson: String, n: Int): String
    private external fun findPathNative(
        startX: Int, startY: Int, goalX: Int, goalY: Int,
        obstaclesJson: String, gridWidth: Int, gridHeight: Int, use8Dir: Boolean
    ): String
    private external fun analyzeCombatNative(
        selfX: Int, selfY: Int, selfHpPercent: Float,
        enemiesJson: String, alliesJson: String, skillReadyJson: String, inTowerRange: Boolean
    ): String
}

/**
 * Memory Engine - Game memory reading (Root only)
 */
object MemoryEngineNative {
    
    data class MemoryRegion(
        val startAddr: Long,
        val endAddr: Long,
        val permissions: String,
        val pathname: String
    ) {
        val isReadable: Boolean get() = permissions.startsWith('r')
        val isWritable: Boolean get() = permissions.getOrNull(1) == 'w'
        val size: Long get() = endAddr - startAddr
    }
    
    data class PatternMatch(
        val address: Long,
        val regionStart: Long,
        val offsetInRegion: Long,
        val matchedBytes: ByteArray
    )
    
    data class UnityStats(
        val hp: Float,
        val maxHp: Float,
        val mp: Float,
        val maxMp: Float
    )
    
    data class Position3D(
        val x: Float,
        val y: Float,
        val z: Float
    )
    
    /**
     * Parse memory maps for a process (requires Root)
     */
    fun parseMemoryMaps(pid: Int): List<MemoryRegion> {
        if (!AgentCore.isAvailable()) return emptyList()
        
        return try {
            val json = parseMemoryMapsNative(pid)
            val arr = JSONArray(json)
            (0 until arr.length()).map { i ->
                val obj = arr.getJSONObject(i)
                MemoryRegion(
                    startAddr = obj.getLong("start_addr"),
                    endAddr = obj.getLong("end_addr"),
                    permissions = obj.getString("permissions"),
                    pathname = obj.getString("pathname")
                )
            }
        } catch (e: Exception) {
            Log.e("MemoryEngineNative", "parseMemoryMaps error: ${e.message}")
            emptyList()
        }
    }
    
    /**
     * Search for int32 value in memory (requires Root)
     */
    fun searchInt32(pid: Int, value: Int, regions: List<MemoryRegion>, limit: Int = 100): List<PatternMatch> {
        if (!AgentCore.isAvailable()) return emptyList()
        
        return try {
            val regionsJson = serializeRegions(regions)
            val json = searchInt32Native(pid, value, regionsJson, limit)
            parsePatternMatches(json)
        } catch (e: Exception) {
            Log.e("MemoryEngineNative", "searchInt32 error: ${e.message}")
            emptyList()
        }
    }
    
    /**
     * Search for float32 value in memory (requires Root)
     */
    fun searchFloat32(pid: Int, value: Float, tolerance: Float, regions: List<MemoryRegion>, limit: Int = 100): List<PatternMatch> {
        if (!AgentCore.isAvailable()) return emptyList()
        
        return try {
            val regionsJson = serializeRegions(regions)
            val json = searchFloat32Native(pid, value, tolerance, regionsJson, limit)
            parsePatternMatches(json)
        } catch (e: Exception) {
            Log.e("MemoryEngineNative", "searchFloat32 error: ${e.message}")
            emptyList()
        }
    }
    
    /**
     * Read int32 at address (requires Root)
     */
    fun readInt32(pid: Int, address: Long): Int? {
        if (!AgentCore.isAvailable()) return null
        
        return try {
            val result = readInt32Native(pid, address)
            if (result == -1) null else result
        } catch (e: Exception) {
            null
        }
    }
    
    /**
     * Read float32 at address (requires Root)
     */
    fun readFloat32(pid: Int, address: Long): Float? {
        if (!AgentCore.isAvailable()) return null
        
        return try {
            val result = readFloat32Native(pid, address)
            if (result == -1f) null else result
        } catch (e: Exception) {
            null
        }
    }
    
    /**
     * Read string at address (requires Root)
     */
    fun readString(pid: Int, address: Long, maxLen: Int = 256): String? {
        if (!AgentCore.isAvailable()) return null
        
        return try {
            val result = readStringNative(pid, address, maxLen)
            if (result.startsWith("ERROR:")) null else result
        } catch (e: Exception) {
            null
        }
    }
    
    /**
     * Parse Unity player stats from memory data
     */
    fun parseUnityStats(data: ByteArray): UnityStats? {
        if (!AgentCore.isAvailable()) return null
        
        return try {
            val json = parseUnityStatsNative(data)
            if (json == "null" || json.contains("error")) null
            else {
                val obj = JSONObject(json)
                UnityStats(
                    hp = obj.getDouble("hp").toFloat(),
                    maxHp = obj.getDouble("maxHp").toFloat(),
                    mp = obj.getDouble("mp").toFloat(),
                    maxMp = obj.getDouble("maxMp").toFloat()
                )
            }
        } catch (e: Exception) {
            null
        }
    }
    
    /**
     * Parse position from memory data
     */
    fun parsePosition(data: ByteArray): Position3D? {
        if (!AgentCore.isAvailable()) return null
        
        return try {
            val json = parsePositionNative(data)
            if (json == "null" || json.contains("error")) null
            else {
                val obj = JSONObject(json)
                Position3D(
                    x = obj.getDouble("x").toFloat(),
                    y = obj.getDouble("y").toFloat(),
                    z = obj.getDouble("z").toFloat()
                )
            }
        } catch (e: Exception) {
            null
        }
    }
    
    private fun serializeRegions(regions: List<MemoryRegion>): String {
        val arr = JSONArray()
        for (region in regions) {
            val obj = JSONObject()
            obj.put("start_addr", region.startAddr)
            obj.put("end_addr", region.endAddr)
            obj.put("permissions", region.permissions)
            obj.put("offset", 0)
            obj.put("device", "00:00")
            obj.put("inode", 0)
            obj.put("pathname", region.pathname)
            arr.put(obj)
        }
        return arr.toString()
    }
    
    private fun parsePatternMatches(json: String): List<PatternMatch> {
        return try {
            val arr = JSONArray(json)
            (0 until arr.length()).map { i ->
                val obj = arr.getJSONObject(i)
                val bytesArr = obj.getJSONArray("matched_bytes")
                val bytes = ByteArray(bytesArr.length()) { j -> bytesArr.getInt(j).toByte() }
                PatternMatch(
                    address = obj.getLong("address"),
                    regionStart = obj.getLong("region_start"),
                    offsetInRegion = obj.getLong("offset_in_region"),
                    matchedBytes = bytes
                )
            }
        } catch (e: Exception) {
            emptyList()
        }
    }
    
    // Native methods
    private external fun parseMemoryMapsNative(pid: Int): String
    private external fun searchInt32Native(pid: Int, value: Int, regionsJson: String, limit: Int): String
    private external fun searchFloat32Native(pid: Int, value: Float, tolerance: Float, regionsJson: String, limit: Int): String
    private external fun readInt32Native(pid: Int, address: Long): Int
    private external fun readFloat32Native(pid: Int, address: Long): Float
    private external fun readStringNative(pid: Int, address: Long, maxLen: Int): String
    private external fun parseUnityStatsNative(data: ByteArray): String
    private external fun parsePositionNative(data: ByteArray): String
}
