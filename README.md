# DeepSeek AI Assistant

一款功能强大的 Android AI 助手应用，支持本地 AI 模型推理、Root 权限控制、极致性能优化、位图转矢量图、Termux 集成等多种功能。

![Platform](https://img.shields.io/badge/Platform-Android-green)
![API](https://img.shields.io/badge/API-26%2B-brightgreen)
![License](https://img.shields.io/badge/License-MIT-blue)
![Native](https://img.shields.io/badge/Native-C%2FC%2B%2B%2FNEON%2FAsm-orange)

## 📱 功能特性

### 🧠 本地 AI 推理
- **离线对话**：无需网络，完全本地运行 AI 模型
- **多模型支持**：支持 DeepSeek、Qwen、TinyLlama 等 GGUF 格式模型
- **国内镜像下载**：使用 hf-mirror.com 镜像，无需梯子
- **模型持久化**：自动记忆上次加载的模型

### 🔧 Root 控制功能（需 Root 权限）
- **40+ 操作白名单**：蓝牙、WiFi、音量、亮度、重启等
- **AI 控制权限**：可选让 AI 获得手机控制权
- **安全机制**：高危操作二次确认、恶意指令过滤
- **操作日志**：完整记录所有 AI 控制操作

### 🌐 多功能浏览器
- **多标签页**：支持同时打开多个网页
- **视频播放**：完整支持在线视频播放
- **书签收藏**：保存常用网址
- **下载管理**：支持文件下载
- **隐私模式**：无痕浏览

### 📐 数学计算模块
- **LaTeX 渲染**：支持数学公式显示
- **函数绘图**：绘制数学函数图像
- **科学计算**：支持复杂数学运算
- **步骤展示**：显示计算过程

### 🖼️ 位图转矢量图
- **多格式输入**：支持 PNG、JPG、BMP、WEBP、GIF、TIFF 等主流位图格式
- **多格式输出**：支持 SVG、PDF、EPS、DXF 等主流矢量图格式
- **智能算法**：Potrace 风格轮廓追踪 + Douglas-Peucker 路径简化
- **实时预览**：左右分栏界面，实时查看转换效果
- **参数调节**：阈值、简化程度、反转颜色、填充模式

### 🚀 极致性能优化
- **C 语言核心**：关键算法使用纯 C 实现
- **ARM NEON SIMD**：图像处理向量化加速
- **汇编优化**：ARM64 手写汇编优化热点函数
- **无分支算法**：减少 CPU 分支预测失败
- **内存预取**：优化缓存命中率

### 📱 Termux 集成
- **终端访问**：直接访问 Termux 终端
- **脚本执行**：运行自定义 Shell 脚本
- **API 调用**：调用 Termux API 功能
- **X11 支持**：支持 Termux:X11 图形界面

### 🎨 原神主题 UI
- **原神配色**：金色 + 天蓝色主题
- **RGB 流光效果**：科技感交互动画
- **Material Design 3**：现代化设计语言

---

## 📦 安装教程

### 方式一：直接安装 APK

1. 前往 [Releases](https://github.com/zhangbinhaoder/DeepSeekAIAssistant/releases) 页面
2. 下载最新版 `app-debug.apk`
3. 在手机上安装 APK（需允许安装未知来源应用）

### 方式二：自行编译

#### 环境要求
- Android Studio Hedgehog (2023.1.1) 或更高版本
- JDK 21
- Android SDK 34
- NDK 26.1.10909125
- CMake 3.22.1

#### 编译步骤

```bash
# 1. 克隆项目
git clone https://github.com/zhangbinhaoder/DeepSeekAIAssistant.git
cd DeepSeekAIAssistant

# 2. 初始化 llama.cpp 子模块
git submodule update --init --recursive

# 3. 使用 Gradle 编译
./gradlew assembleDebug

# 4. APK 输出位置
# app/build/outputs/apk/debug/app-debug.apk
```

---

## 🚀 使用教程

### 1️⃣ 首次启动

1. 打开应用，进入「聊天」页面
2. 点击右上角设置，配置 AI 模式：
   - **联网模式**：需要 DeepSeek API Key
   - **本地模式**：需要下载本地模型

### 2️⃣ 配置本地 AI 模型

1. 进入「玩机」页面
2. 找到「本地 AI 模型管理」卡片
3. 点击「下载模型」选择模型：
   - **DeepSeek Coder 1.3B**：轻量级代码模型（推荐）
   - **Qwen 1.5B**：通用对话模型
   - **TinyLlama 1.1B**：超轻量模型
4. 等待下载完成（使用国内镜像，无需梯子）
5. 点击「加载模型」启用本地 AI

### 3️⃣ 开启 AI 控制权限（可选，需 Root）

> ⚠️ **警告**：此功能需要设备已 Root，且会赋予 AI 对手机的控制能力

1. 确保设备已 Root（支持 Magisk、SuperSU）
2. 进入「玩机」页面
3. 找到「AI 控制权限」卡片
4. 开启「本地 AI 控制权限」或「云端 AI 控制权限」
5. 在弹出的 Root 授权窗口中点击「允许」

#### 支持的控制操作

| 类别 | 操作 |
|------|------|
| 硬件控制 | 蓝牙开关、WiFi开关、移动数据、飞行模式、音量调节、亮度调节 |
| 系统操作 | 重启手机、截图、开关手电筒、清理内存 |
| 应用管理 | 打开应用、关闭应用、卸载应用、冻结/解冻应用 |
| 系统设置 | 修改系统设置、开关省电模式、开关勿扰模式 |

### 4️⃣ 使用浏览器

1. 进入「浏览器」页面
2. 在地址栏输入网址或搜索内容
3. 支持功能：
   - 多标签页（点击标签数切换）
   - 书签收藏（点击菜单 → 添加书签）
   - 全屏视频播放
   - 文件下载

### 5️⃣ 使用数学模块

1. 进入「数学」页面
2. 输入数学表达式或公式
3. 支持：
   - 基础运算：`2+3*4`
   - 函数计算：`sin(pi/2)`, `sqrt(16)`
   - 方程求解：`x^2-4=0`
   - 函数绘图：输入 `plot y=x^2`

---

## ⚙️ 配置说明

### DeepSeek API 配置（联网模式）

1. 访问 [DeepSeek 开放平台](https://platform.deepseek.com/)
2. 注册并获取 API Key
3. 在应用设置中填入 API Key

### 模型存储位置

本地模型默认存储在：
```
/storage/emulated/0/Android/data/com.example.deepseekaiassistant/files/models/
```

### Root 操作日志

AI 控制操作日志存储在：
```
/storage/emulated/0/Android/data/com.example.deepseekaiassistant/files/logs/ai_control_log.txt
```

---

## 🛠️ 技术架构

```
DeepSeekAIAssistant/
├── app/src/main/
│   ├── java/com/example/deepseekaiassistant/
│   │   ├── MainActivity.kt          # 主界面
│   │   ├── ui/
│   │   │   ├── ChatFragment.kt       # 聊天页面
│   │   │   ├── BrowserFragment.kt    # 浏览器
│   │   │   ├── SceneFragment.kt      # 玩机工具
│   │   │   └── MathFragment.kt       # 数学计算
│   │   ├── local/
│   │   │   ├── LocalAIManager.kt     # 本地AI管理
│   │   │   └── LlamaCpp.kt           # JNI绑定
│   │   ├── root/
│   │   │   ├── RootManager.kt        # Root权限管理
│   │   │   ├── AIControlCommand.kt   # 控制指令
│   │   │   └── AIRootController.kt   # 执行控制器
│   │   ├── tools/
│   │   │   ├── SceneTools.kt         # 玩机工具集
│   │   │   ├── VectorizeActivity.kt  # 矢量化界面
│   │   │   └── VectorizerManager.kt  # 矢量化管理器
│   │   └── termux/
│   │       ├── TermuxActivity.kt     # Termux集成界面
│   │       └── TermuxIntegration.kt  # Termux API调用
│   ├── cpp/
│   │   ├── llama.cpp/                # llama.cpp 库
│   │   ├── llama_android.cpp         # LLM JNI 实现
│   │   ├── vectorizer.c              # 矢量化引擎 (C)
│   │   ├── simd_image.c              # NEON图像处理
│   │   ├── simd_image_adv.c          # 高级SIMD优化
│   │   ├── hp_core.c                 # 高性能核心
│   │   ├── asm_core.S                # ARM64汇编优化
│   │   ├── ultra_optim.S             # 极致汇编优化
│   │   └── CMakeLists.txt
│   └── res/                          # 资源文件
├── rust_core/                        # Rust高性能模块
│   └── src/
│       ├── image_engine.rs           # 图像处理引擎
│       ├── strategy_engine.rs        # 策略引擎
│       └── memory_engine.rs          # 内存引擎
└── build.gradle.kts
```

### 技术栈

- **语言**：Kotlin + C + ARM Assembly
- **UI 框架**：Android View + Material Design 3
- **网络**：Retrofit + OkHttp
- **本地 AI**：llama.cpp (JNI)
- **图像处理**：NEON SIMD 向量化加速
- **矢量化引擎**：纯 C 实现的 Potrace 风格算法
- **构建**：Gradle Kotlin DSL + CMake
- **最低 API**：Android 8.0 (API 26)
- **架构**：arm64-v8a

---

## ❓ 常见问题

### Q: 本地模型下载失败？
A: 应用已配置国内镜像 (hf-mirror.com)，如果仍然失败，请检查网络连接。

### Q: 模型加载后无响应？
A: 
1. 确保模型文件完整下载
2. 检查设备内存是否充足（建议至少 4GB RAM）
3. 尝试使用更小的模型（如 TinyLlama）

### Q: Root 功能无法使用？
A: 
1. 确认设备已正确 Root
2. 在 Magisk/SuperSU 中授权应用 Root 权限
3. 重启应用后重试

### Q: 视频无法播放？
A: 应用已修复 `net::ERR_UNKNOWN_SCHEME` 错误，如仍有问题：
1. 检查网络连接
2. 尝试刷新页面
3. 部分网站可能需要桌面版 UA

---

## 📄 开源协议

本项目采用 MIT 协议开源。

---

## 🙏 致谢

- [llama.cpp](https://github.com/ggerganov/llama.cpp) - 本地 AI 推理引擎
- [DeepSeek](https://www.deepseek.com/) - AI 模型与 API
- [Material Design](https://m3.material.io/) - UI 设计规范
- [Potrace](http://potrace.sourceforge.net/) - 矢量化算法灵感
- [Termux](https://termux.dev/) - Android 终端模拟器

---

## 📞 联系方式

- GitHub: [@zhangbinhaoder](https://github.com/zhangbinhaoder)
- Issues: [提交问题](https://github.com/zhangbinhaoder/DeepSeekAIAssistant/issues)
