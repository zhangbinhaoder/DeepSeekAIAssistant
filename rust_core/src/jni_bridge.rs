//! JNI Bridge - Kotlin/Java bindings for Rust functions
//! 
//! This module provides the JNI interface for calling Rust functions from Android.
//! All functions follow the JNI naming convention: Java_<package>_<class>_<method>

use jni::objects::{JByteArray, JClass, JIntArray, JObject, JString};
use jni::sys::{jboolean, jbyteArray, jfloat, jint, jlong, jstring, JNI_TRUE, JNI_FALSE};
use jni::JNIEnv;

use crate::image_engine::{DetectedElement, ElementType, ImageData, ImageEngine, Rect};
use crate::strategy_engine::{CombatEngine, EliminateEngine, EliminateMove, GridPos, PathfindingEngine};
use crate::memory_engine::{GameDataStructures, MemoryEngine, MemoryRegion};
use rustc_hash::FxHashSet;

// Package path for JNI functions
const PACKAGE: &str = "com_example_deepseekaiassistant_agent";

/// Initialize the Rust core library
/// JNI: AgentCore.init()
#[no_mangle]
pub extern "system" fn Java_com_example_deepseekaiassistant_agent_AgentCore_init(
    _env: JNIEnv,
    _class: JClass,
) {
    crate::init_library();
}

/// Get library version
/// JNI: AgentCore.getVersion(): String
#[no_mangle]
pub extern "system" fn Java_com_example_deepseekaiassistant_agent_AgentCore_getVersion<'local>(
    mut env: JNIEnv<'local>,
    _class: JClass<'local>,
) -> jstring {
    let version = env.new_string(crate::VERSION).expect("Failed to create string");
    version.into_raw()
}

// ============================================================================
// Image Engine JNI Functions
// ============================================================================

/// Detect health bars in image
/// JNI: ImageEngineNative.detectHealthBars(pixels: ByteArray, width: Int, height: Int): String (JSON)
#[no_mangle]
pub extern "system" fn Java_com_example_deepseekaiassistant_agent_ImageEngineNative_detectHealthBars<'local>(
    mut env: JNIEnv<'local>,
    _class: JClass<'local>,
    pixels: JByteArray<'local>,
    width: jint,
    height: jint,
) -> jstring {
    let result = (|| -> Result<String, String> {
        let bytes = env.convert_byte_array(&pixels)
            .map_err(|e| format!("Failed to convert byte array: {}", e))?;
        
        let image = ImageData::from_argb_bytes(&bytes, width as usize, height as usize);
        let elements = ImageEngine::detect_health_bars(&image);
        
        serde_json::to_string(&elements)
            .map_err(|e| format!("JSON error: {}", e))
    })();

    match result {
        Ok(json) => env.new_string(&json).unwrap().into_raw(),
        Err(e) => env.new_string(&format!("{{\"error\":\"{}\"}}", e)).unwrap().into_raw(),
    }
}

/// Detect skill buttons in image
/// JNI: ImageEngineNative.detectSkillButtons(pixels: ByteArray, width: Int, height: Int): String (JSON)
#[no_mangle]
pub extern "system" fn Java_com_example_deepseekaiassistant_agent_ImageEngineNative_detectSkillButtons<'local>(
    mut env: JNIEnv<'local>,
    _class: JClass<'local>,
    pixels: JByteArray<'local>,
    width: jint,
    height: jint,
) -> jstring {
    let result = (|| -> Result<String, String> {
        let bytes = env.convert_byte_array(&pixels)
            .map_err(|e| format!("Failed to convert byte array: {}", e))?;
        
        let image = ImageData::from_argb_bytes(&bytes, width as usize, height as usize);
        let elements = ImageEngine::detect_skill_buttons(&image);
        
        serde_json::to_string(&elements)
            .map_err(|e| format!("JSON error: {}", e))
    })();

    match result {
        Ok(json) => env.new_string(&json).unwrap().into_raw(),
        Err(e) => env.new_string(&format!("{{\"error\":\"{}\"}}", e)).unwrap().into_raw(),
    }
}

/// Detect joystick in image
/// JNI: ImageEngineNative.detectJoystick(pixels: ByteArray, width: Int, height: Int): String (JSON)
#[no_mangle]
pub extern "system" fn Java_com_example_deepseekaiassistant_agent_ImageEngineNative_detectJoystick<'local>(
    mut env: JNIEnv<'local>,
    _class: JClass<'local>,
    pixels: JByteArray<'local>,
    width: jint,
    height: jint,
) -> jstring {
    let result = (|| -> Result<String, String> {
        let bytes = env.convert_byte_array(&pixels)
            .map_err(|e| format!("Failed to convert byte array: {}", e))?;
        
        let image = ImageData::from_argb_bytes(&bytes, width as usize, height as usize);
        let element = ImageEngine::detect_joystick(&image);
        
        serde_json::to_string(&element)
            .map_err(|e| format!("JSON error: {}", e))
    })();

    match result {
        Ok(json) => env.new_string(&json).unwrap().into_raw(),
        Err(e) => env.new_string(&format!("{{\"error\":\"{}\"}}", e)).unwrap().into_raw(),
    }
}

/// Analyze eliminate game board
/// JNI: ImageEngineNative.analyzeEliminateBoard(pixels: ByteArray, width: Int, height: Int, 
///                                              gridX: Int, gridY: Int, gridW: Int, gridH: Int,
///                                              rows: Int, cols: Int): String (JSON 2D array)
#[no_mangle]
pub extern "system" fn Java_com_example_deepseekaiassistant_agent_ImageEngineNative_analyzeEliminateBoard<'local>(
    mut env: JNIEnv<'local>,
    _class: JClass<'local>,
    pixels: JByteArray<'local>,
    width: jint,
    height: jint,
    grid_x: jint,
    grid_y: jint,
    grid_w: jint,
    grid_h: jint,
    rows: jint,
    cols: jint,
) -> jstring {
    let result = (|| -> Result<String, String> {
        let bytes = env.convert_byte_array(&pixels)
            .map_err(|e| format!("Failed to convert byte array: {}", e))?;
        
        let image = ImageData::from_argb_bytes(&bytes, width as usize, height as usize);
        let grid_bounds = Rect::new(grid_x, grid_y, grid_w, grid_h);
        let board = ImageEngine::analyze_eliminate_board(&image, &grid_bounds, rows as usize, cols as usize);
        
        serde_json::to_string(&board)
            .map_err(|e| format!("JSON error: {}", e))
    })();

    match result {
        Ok(json) => env.new_string(&json).unwrap().into_raw(),
        Err(e) => env.new_string(&format!("{{\"error\":\"{}\"}}", e)).unwrap().into_raw(),
    }
}

// ============================================================================
// Strategy Engine JNI Functions
// ============================================================================

/// Find best move for eliminate game
/// JNI: StrategyEngineNative.findBestEliminateMove(boardJson: String): String (JSON EliminateMove)
#[no_mangle]
pub extern "system" fn Java_com_example_deepseekaiassistant_agent_StrategyEngineNative_findBestEliminateMove<'local>(
    mut env: JNIEnv<'local>,
    _class: JClass<'local>,
    board_json: JString<'local>,
) -> jstring {
    let result = (|| -> Result<String, String> {
        let board_str: String = env.get_string(&board_json)
            .map_err(|e| format!("Failed to get string: {}", e))?
            .into();
        
        let board: Vec<Vec<u8>> = serde_json::from_str(&board_str)
            .map_err(|e| format!("JSON parse error: {}", e))?;
        
        let best_move = EliminateEngine::find_best_move(&board);
        
        serde_json::to_string(&best_move)
            .map_err(|e| format!("JSON error: {}", e))
    })();

    match result {
        Ok(json) => env.new_string(&json).unwrap().into_raw(),
        Err(e) => env.new_string(&format!("{{\"error\":\"{}\"}}", e)).unwrap().into_raw(),
    }
}

/// Find top N best moves for eliminate game
/// JNI: StrategyEngineNative.findBestEliminateMoves(boardJson: String, n: Int): String (JSON Array)
#[no_mangle]
pub extern "system" fn Java_com_example_deepseekaiassistant_agent_StrategyEngineNative_findBestEliminateMoves<'local>(
    mut env: JNIEnv<'local>,
    _class: JClass<'local>,
    board_json: JString<'local>,
    n: jint,
) -> jstring {
    let result = (|| -> Result<String, String> {
        let board_str: String = env.get_string(&board_json)
            .map_err(|e| format!("Failed to get string: {}", e))?
            .into();
        
        let board: Vec<Vec<u8>> = serde_json::from_str(&board_str)
            .map_err(|e| format!("JSON parse error: {}", e))?;
        
        let moves = EliminateEngine::find_best_moves(&board, n as usize);
        
        serde_json::to_string(&moves)
            .map_err(|e| format!("JSON error: {}", e))
    })();

    match result {
        Ok(json) => env.new_string(&json).unwrap().into_raw(),
        Err(e) => env.new_string(&format!("{{\"error\":\"{}\"}}", e)).unwrap().into_raw(),
    }
}

/// Find path using A* algorithm
/// JNI: StrategyEngineNative.findPath(startX: Int, startY: Int, goalX: Int, goalY: Int,
///                                    obstaclesJson: String, gridWidth: Int, gridHeight: Int,
///                                    use8Dir: Boolean): String (JSON PathResult)
#[no_mangle]
pub extern "system" fn Java_com_example_deepseekaiassistant_agent_StrategyEngineNative_findPath<'local>(
    mut env: JNIEnv<'local>,
    _class: JClass<'local>,
    start_x: jint,
    start_y: jint,
    goal_x: jint,
    goal_y: jint,
    obstacles_json: JString<'local>,
    grid_width: jint,
    grid_height: jint,
    use_8dir: jboolean,
) -> jstring {
    let result = (|| -> Result<String, String> {
        let obstacles_str: String = env.get_string(&obstacles_json)
            .map_err(|e| format!("Failed to get string: {}", e))?
            .into();
        
        let obstacles_vec: Vec<(i32, i32)> = serde_json::from_str(&obstacles_str)
            .map_err(|e| format!("JSON parse error: {}", e))?;
        
        let obstacles: FxHashSet<GridPos> = obstacles_vec.into_iter()
            .map(|(x, y)| GridPos::new(x, y))
            .collect();
        
        let start = GridPos::new(start_x, start_y);
        let goal = GridPos::new(goal_x, goal_y);
        
        let path_result = if use_8dir == JNI_TRUE {
            PathfindingEngine::find_path_8dir(start, goal, &obstacles, grid_width, grid_height)
        } else {
            PathfindingEngine::find_path(start, goal, &obstacles, grid_width, grid_height)
        };
        
        serde_json::to_string(&path_result)
            .map_err(|e| format!("JSON error: {}", e))
    })();

    match result {
        Ok(json) => env.new_string(&json).unwrap().into_raw(),
        Err(e) => env.new_string(&format!("{{\"error\":\"{}\"}}", e)).unwrap().into_raw(),
    }
}

/// Analyze combat situation
/// JNI: StrategyEngineNative.analyzeCombat(selfX: Int, selfY: Int, selfHpPercent: Float,
///                                         enemiesJson: String, alliesJson: String,
///                                         skillReadyJson: String, inTowerRange: Boolean): String
#[no_mangle]
pub extern "system" fn Java_com_example_deepseekaiassistant_agent_StrategyEngineNative_analyzeCombat<'local>(
    mut env: JNIEnv<'local>,
    _class: JClass<'local>,
    self_x: jint,
    self_y: jint,
    self_hp_percent: jfloat,
    enemies_json: JString<'local>,
    allies_json: JString<'local>,
    skill_ready_json: JString<'local>,
    in_tower_range: jboolean,
) -> jstring {
    let result = (|| -> Result<String, String> {
        let enemies_str: String = env.get_string(&enemies_json)
            .map_err(|e| format!("Failed to get string: {}", e))?
            .into();
        let allies_str: String = env.get_string(&allies_json)
            .map_err(|e| format!("Failed to get string: {}", e))?
            .into();
        let skill_str: String = env.get_string(&skill_ready_json)
            .map_err(|e| format!("Failed to get string: {}", e))?
            .into();
        
        let enemies_vec: Vec<(i32, i32, f32)> = serde_json::from_str(&enemies_str)
            .map_err(|e| format!("JSON parse error: {}", e))?;
        let allies_vec: Vec<(i32, i32)> = serde_json::from_str(&allies_str)
            .map_err(|e| format!("JSON parse error: {}", e))?;
        let skill_ready: Vec<bool> = serde_json::from_str(&skill_str)
            .map_err(|e| format!("JSON parse error: {}", e))?;
        
        let enemies: Vec<(GridPos, f32)> = enemies_vec.into_iter()
            .map(|(x, y, hp)| (GridPos::new(x, y), hp))
            .collect();
        let allies: Vec<GridPos> = allies_vec.into_iter()
            .map(|(x, y)| GridPos::new(x, y))
            .collect();
        
        let self_pos = GridPos::new(self_x, self_y);
        
        let decisions = CombatEngine::analyze_combat(
            self_pos,
            self_hp_percent,
            &enemies,
            &allies,
            &skill_ready,
            in_tower_range == JNI_TRUE,
        );
        
        serde_json::to_string(&decisions)
            .map_err(|e| format!("JSON error: {}", e))
    })();

    match result {
        Ok(json) => env.new_string(&json).unwrap().into_raw(),
        Err(e) => env.new_string(&format!("{{\"error\":\"{}\"}}", e)).unwrap().into_raw(),
    }
}

// ============================================================================
// Memory Engine JNI Functions (Root only)
// ============================================================================

/// Parse memory maps for a process
/// JNI: MemoryEngineNative.parseMemoryMaps(pid: Int): String (JSON Array)
#[no_mangle]
pub extern "system" fn Java_com_example_deepseekaiassistant_agent_MemoryEngineNative_parseMemoryMaps<'local>(
    mut env: JNIEnv<'local>,
    _class: JClass<'local>,
    pid: jint,
) -> jstring {
    let result = MemoryEngine::parse_memory_maps(pid as u32);
    
    match result {
        Ok(regions) => {
            let json = serde_json::to_string(&regions).unwrap_or_else(|_| "[]".to_string());
            env.new_string(&json).unwrap().into_raw()
        }
        Err(e) => env.new_string(&format!("{{\"error\":\"{}\"}}", e)).unwrap().into_raw(),
    }
}

/// Search for int32 value in memory
/// JNI: MemoryEngineNative.searchInt32(pid: Int, value: Int, regionsJson: String, limit: Int): String
#[no_mangle]
pub extern "system" fn Java_com_example_deepseekaiassistant_agent_MemoryEngineNative_searchInt32<'local>(
    mut env: JNIEnv<'local>,
    _class: JClass<'local>,
    pid: jint,
    value: jint,
    regions_json: JString<'local>,
    limit: jint,
) -> jstring {
    let result = (|| -> Result<String, String> {
        let regions_str: String = env.get_string(&regions_json)
            .map_err(|e| format!("Failed to get string: {}", e))?
            .into();
        
        let regions: Vec<MemoryRegion> = serde_json::from_str(&regions_str)
            .map_err(|e| format!("JSON parse error: {}", e))?;
        
        let matches = MemoryEngine::search_int32(pid as u32, value, &regions, limit as usize)?;
        
        serde_json::to_string(&matches)
            .map_err(|e| format!("JSON error: {}", e))
    })();

    match result {
        Ok(json) => env.new_string(&json).unwrap().into_raw(),
        Err(e) => env.new_string(&format!("{{\"error\":\"{}\"}}", e)).unwrap().into_raw(),
    }
}

/// Search for float32 value in memory
/// JNI: MemoryEngineNative.searchFloat32(pid: Int, value: Float, tolerance: Float, 
///                                        regionsJson: String, limit: Int): String
#[no_mangle]
pub extern "system" fn Java_com_example_deepseekaiassistant_agent_MemoryEngineNative_searchFloat32<'local>(
    mut env: JNIEnv<'local>,
    _class: JClass<'local>,
    pid: jint,
    value: jfloat,
    tolerance: jfloat,
    regions_json: JString<'local>,
    limit: jint,
) -> jstring {
    let result = (|| -> Result<String, String> {
        let regions_str: String = env.get_string(&regions_json)
            .map_err(|e| format!("Failed to get string: {}", e))?
            .into();
        
        let regions: Vec<MemoryRegion> = serde_json::from_str(&regions_str)
            .map_err(|e| format!("JSON parse error: {}", e))?;
        
        let matches = MemoryEngine::search_float32(pid as u32, value, tolerance, &regions, limit as usize)?;
        
        serde_json::to_string(&matches)
            .map_err(|e| format!("JSON error: {}", e))
    })();

    match result {
        Ok(json) => env.new_string(&json).unwrap().into_raw(),
        Err(e) => env.new_string(&format!("{{\"error\":\"{}\"}}", e)).unwrap().into_raw(),
    }
}

/// Read int32 at address
/// JNI: MemoryEngineNative.readInt32(pid: Int, address: Long): Int
#[no_mangle]
pub extern "system" fn Java_com_example_deepseekaiassistant_agent_MemoryEngineNative_readInt32(
    _env: JNIEnv,
    _class: JClass,
    pid: jint,
    address: jlong,
) -> jint {
    MemoryEngine::read_int32(pid as u32, address as u64).unwrap_or(-1)
}

/// Read float32 at address
/// JNI: MemoryEngineNative.readFloat32(pid: Int, address: Long): Float
#[no_mangle]
pub extern "system" fn Java_com_example_deepseekaiassistant_agent_MemoryEngineNative_readFloat32(
    _env: JNIEnv,
    _class: JClass,
    pid: jint,
    address: jlong,
) -> jfloat {
    MemoryEngine::read_float32(pid as u32, address as u64).unwrap_or(-1.0)
}

/// Read string at address
/// JNI: MemoryEngineNative.readString(pid: Int, address: Long, maxLen: Int): String
#[no_mangle]
pub extern "system" fn Java_com_example_deepseekaiassistant_agent_MemoryEngineNative_readString<'local>(
    mut env: JNIEnv<'local>,
    _class: JClass<'local>,
    pid: jint,
    address: jlong,
    max_len: jint,
) -> jstring {
    match MemoryEngine::read_string(pid as u32, address as u64, max_len as usize) {
        Ok(s) => env.new_string(&s).unwrap().into_raw(),
        Err(e) => env.new_string(&format!("ERROR: {}", e)).unwrap().into_raw(),
    }
}

/// Parse Unity player stats from memory data
/// JNI: MemoryEngineNative.parseUnityStats(data: ByteArray): String (JSON)
#[no_mangle]
pub extern "system" fn Java_com_example_deepseekaiassistant_agent_MemoryEngineNative_parseUnityStats<'local>(
    mut env: JNIEnv<'local>,
    _class: JClass<'local>,
    data: JByteArray<'local>,
) -> jstring {
    let result = (|| -> Result<String, String> {
        let bytes = env.convert_byte_array(&data)
            .map_err(|e| format!("Failed to convert byte array: {}", e))?;
        
        if let Some((hp, max_hp, mp, max_mp)) = GameDataStructures::parse_unity_stats(&bytes) {
            Ok(format!("{{\"hp\":{},\"maxHp\":{},\"mp\":{},\"maxMp\":{}}}", hp, max_hp, mp, max_mp))
        } else {
            Ok("null".to_string())
        }
    })();

    match result {
        Ok(json) => env.new_string(&json).unwrap().into_raw(),
        Err(e) => env.new_string(&format!("{{\"error\":\"{}\"}}", e)).unwrap().into_raw(),
    }
}

/// Parse position from memory data
/// JNI: MemoryEngineNative.parsePosition(data: ByteArray): String (JSON)
#[no_mangle]
pub extern "system" fn Java_com_example_deepseekaiassistant_agent_MemoryEngineNative_parsePosition<'local>(
    mut env: JNIEnv<'local>,
    _class: JClass<'local>,
    data: JByteArray<'local>,
) -> jstring {
    let result = (|| -> Result<String, String> {
        let bytes = env.convert_byte_array(&data)
            .map_err(|e| format!("Failed to convert byte array: {}", e))?;
        
        if let Some((x, y, z)) = GameDataStructures::parse_position(&bytes) {
            Ok(format!("{{\"x\":{},\"y\":{},\"z\":{}}}", x, y, z))
        } else {
            Ok("null".to_string())
        }
    })();

    match result {
        Ok(json) => env.new_string(&json).unwrap().into_raw(),
        Err(e) => env.new_string(&format!("{{\"error\":\"{}\"}}", e)).unwrap().into_raw(),
    }
}
