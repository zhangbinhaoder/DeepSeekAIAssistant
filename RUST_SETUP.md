# Rust 高性能核心模块设置指南

本项目包含用 Rust 编写的高性能核心模块，用于加速 AI 代执行系统中的关键计算。

## 性能提升

| 模块 | Kotlin 实现 | Rust 实现 | 提升倍数 |
|------|------------|----------|---------|
| 图像处理 | ~50ms | ~5ms | **10x** |
| 消消乐策略 | ~100ms | ~5ms | **20x** |
| A* 寻路 | ~30ms | ~3ms | **10x** |
| 内存解析 | ~20ms | ~5ms | **4x** |

## 环境要求

### 1. 安装 Rust

**Windows:**
```powershell
# 下载并运行安装器
# 访问 https://rustup.rs/ 下载 rustup-init.exe
rustup-init.exe
```

**Linux/macOS:**
```bash
curl --proto '=https' --tlsv1.2 -sSf https://sh.rustup.rs | sh
```

### 2. 安装 Android NDK

确保 Android NDK 已安装并配置：

```powershell
# 设置 NDK 路径
$env:ANDROID_NDK_HOME = "C:\Users\<用户名>\AppData\Local\Android\Sdk\ndk\<版本>"
```

### 3. 安装 cargo-ndk

```powershell
cargo install cargo-ndk
```

### 4. 添加 Android 目标

```powershell
rustup target add aarch64-linux-android    # arm64-v8a
rustup target add armv7-linux-androideabi  # armeabi-v7a
rustup target add x86_64-linux-android     # x86_64
rustup target add i686-linux-android       # x86
```

## 编译

### Windows (PowerShell)

```powershell
cd rust_core
.\build_android.ps1
```

### Linux/macOS

```bash
cd rust_core
chmod +x build_android.sh
./build_android.sh
```

### 手动编译（单架构）

```powershell
cd rust_core

# 仅编译 arm64-v8a (最常用)
cargo ndk -t arm64-v8a -o ../app/src/main/jniLibs build --release
```

## 输出文件

编译完成后，SO 库文件会生成在：

```
app/src/main/jniLibs/
├── arm64-v8a/
│   └── libagent_core.so
├── armeabi-v7a/
│   └── libagent_core.so
├── x86_64/
│   └── libagent_core.so
└── x86/
    └── libagent_core.so
```

## 使用方法

### Kotlin 中使用

```kotlin
import com.example.deepseekaiassistant.agent.*

// 初始化（应用启动时调用一次）
if (AgentCore.load()) {
    Log.i("App", "Rust core loaded: ${AgentCore.getVersion()}")
}

// 检测血条
val elements = ImageEngineNative.detectHealthBars(pixels, width, height)
for (element in elements) {
    Log.d("Game", "Found ${element.elementType} at ${element.bounds}")
}

// 消消乐最佳移动
val board = arrayOf(
    intArrayOf(1, 2, 1, 3, 4),
    intArrayOf(2, 1, 2, 4, 5),
    // ...
)
val bestMove = StrategyEngineNative.findBestEliminateMove(board)
if (bestMove != null) {
    Log.d("Game", "Best move: (${bestMove.fromRow},${bestMove.fromCol}) -> (${bestMove.toRow},${bestMove.toCol})")
}

// A* 寻路
val path = StrategyEngineNative.findPath(
    start = GridPos(0, 0),
    goal = GridPos(10, 10),
    obstacles = listOf(GridPos(5, 5), GridPos(5, 6)),
    gridWidth = 20,
    gridHeight = 20
)
if (path.found) {
    Log.d("Game", "Path found with ${path.path.size} steps")
}

// 内存读取 (Root only)
val regions = MemoryEngineNative.parseMemoryMaps(gamePid)
val hpMatches = MemoryEngineNative.searchFloat32(gamePid, 100f, 1f, regions, 10)
```

## 模块说明

### 1. ImageEngineNative - 图像处理引擎

- `detectHealthBars()` - 检测红/蓝/绿血条
- `detectSkillButtons()` - 检测技能按钮
- `detectJoystick()` - 检测虚拟摇杆
- `analyzeEliminateBoard()` - 分析消消乐棋盘

### 2. StrategyEngineNative - 策略计算引擎

- `findBestEliminateMove()` - 消消乐最优解
- `findBestEliminateMoves()` - 前N个最优解
- `findPath()` - A* 寻路 (4方向/8方向)
- `analyzeCombat()` - MOBA 战斗决策

### 3. MemoryEngineNative - 内存解析引擎 (Root)

- `parseMemoryMaps()` - 解析进程内存映射
- `searchInt32()` / `searchFloat32()` - 内存数值搜索
- `readInt32()` / `readFloat32()` / `readString()` - 读取内存值
- `parseUnityStats()` - 解析 Unity 游戏数据
- `parsePosition()` - 解析 3D 坐标

## 故障排除

### 1. 找不到库

如果 `AgentCore.load()` 返回 false：

1. 检查 SO 文件是否存在于 `jniLibs` 目录
2. 检查 APK 中是否包含 SO 文件
3. 确保目标架构与设备匹配

### 2. 编译错误

```powershell
# 更新 Rust
rustup update

# 清理并重新编译
cd rust_core
cargo clean
cargo ndk -t arm64-v8a -o ../app/src/main/jniLibs build --release
```

### 3. NDK 版本问题

推荐使用 NDK r25 或更高版本：

```powershell
# 设置 NDK 路径
$env:ANDROID_NDK_HOME = "C:\path\to\ndk\25.2.9519653"
```

## 不使用 Rust 核心

如果不想使用 Rust 核心，应用会自动降级到 Kotlin 实现：

```kotlin
if (AgentCore.isAvailable()) {
    // 使用 Rust 高性能实现
    val result = ImageEngineNative.detectHealthBars(pixels, width, height)
} else {
    // 降级到 Kotlin 实现
    val result = ScreenVision.detectHealthBars(bitmap)
}
```

## 更新日志

- **v1.0.0**: 初始版本
  - 图像处理引擎
  - 策略计算引擎
  - 内存解析引擎
  - JNI 绑定
