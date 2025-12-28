# DeepSeek AI Assistant - 本地 AI 配置指南

## 架构概述

```
┌─────────────────────────────────────────────────────────────────────┐
│                        Android 应用层                               │
│  ┌───────────────────┐     ┌──────────────────────────────────────┐ │
│  │   ChatFragment    │     │         LocalAIManager               │ │
│  │   (聊天界面)       │ ──▶ │   (本地 AI 管理：下载/加载/推理)     │ │
│  └───────────────────┘     └──────────────────────────────────────┘ │
│                                         │                            │
│                                         ▼                            │
│                            ┌──────────────────────────────────────┐ │
│                            │           LlamaCpp                   │ │
│                            │   (JNI 绑定层：Kotlin ↔ Native)      │ │
│                            └──────────────────────────────────────┘ │
└─────────────────────────────────────────────────────────────────────┘
                                         │
                                         ▼ JNI
┌─────────────────────────────────────────────────────────────────────┐
│                        Native 层 (C++)                              │
│  ┌──────────────────────────────────────────────────────────────┐  │
│  │                   llama_android.cpp                          │  │
│  │   - JNI 函数实现                                              │  │
│  │   - 线程安全的资源管理                                         │  │
│  │   - RAII 包装器                                               │  │
│  └──────────────────────────────────────────────────────────────┘  │
│                                   │                                 │
│                                   ▼                                 │
│  ┌──────────────────────────────────────────────────────────────┐  │
│  │                     llama.cpp                                │  │
│  │   - 核心推理引擎                                              │  │
│  │   - GGUF 模型格式支持                                         │  │
│  │   - 高效 CPU 推理                                             │  │
│  └──────────────────────────────────────────────────────────────┘  │
└─────────────────────────────────────────────────────────────────────┘
```

## 快速开始

### 1. 下载 llama.cpp 源码

```batch
# 在项目根目录运行
setup_llama.bat
```

### 2. 重要注意事项

⚠️ **项目路径必须是纯英文**

错误示例：`D:\我的项目\DeepSeekAI`
正确示例：`D:\Projects\DeepSeekAI`

### 3. 构建项目

1. 在 Android Studio 中点击 `File > Sync Project with Gradle Files`
2. 执行 `Build > Rebuild Project`
3. 首次编译需要 5-10 分钟

## 运行模式说明

### 模式 1：Native 推理（完整功能）

**条件**：
- llama.cpp 源码存在且编译成功
- GGUF 模型已下载并加载

**特点**：
- 真正的离线 AI 推理
- 支持各种 GGUF 模型
- 高质量对话能力

### 模式 2：模拟模式（基础功能）

**条件**：
- Native 库不可用，或
- 模型未加载

**特点**：
- 预设回复规则
- 基础对话能力
- 时间/日期查询
- 简单数学计算

## 文件结构

```
app/src/main/
├── cpp/
│   ├── CMakeLists.txt      # CMake 构建配置
│   ├── llama_android.cpp   # JNI 绑定实现
│   └── llama.cpp/          # llama.cpp 源码（由 setup_llama.bat 下载）
│
├── java/.../local/
│   ├── LlamaCpp.kt         # JNI 绑定类（Kotlin 层）
│   └── LocalAIManager.kt   # 本地 AI 管理器
│
└── assets/models/          # 内置模型目录（可选）
```

## 推荐模型

| 模型 | 大小 | 特点 |
|------|------|------|
| TinyLlama 1.1B Q4 | ~670MB | 平衡速度和质量 |
| Qwen2 0.5B Q8 | ~530MB | 速度最快 |
| DeepSeek Coder 1.3B | ~800MB | 代码问答 |
| Microsoft Phi-2 Q4 | ~1.6GB | 质量较好 |

## 故障排除

### 问题：编译失败 "jni.h not found"

**原因**：NDK 未正确安装

**解决方案**：
1. 打开 Android Studio
2. 进入 `File > Settings > Languages & Frameworks > Android SDK`
3. 切换到 `SDK Tools` 标签
4. 勾选 `NDK (Side by side)` 并安装
5. 重新同步项目

### 问题：APK 过大

**原因**：包含了 llama.cpp 的 Native 库

**当前大小**：约 600-700 MB

**优化建议**：
- 仅支持 arm64-v8a 架构（默认配置）
- 使用 ProGuard 压缩 Java 代码
- 考虑使用 AAB 格式发布

### 问题：模型加载失败

**可能原因**：
1. 模型文件损坏
2. 存储空间不足
3. 内存不足

**解决方案**：
1. 删除并重新下载模型
2. 清理存储空间
3. 使用更小的模型

## API 参考

### LlamaCpp

```kotlin
// 检查 Native 库是否可用
LlamaCpp.isNativeAvailable(): Boolean

// 创建实例并检查状态
val llamaCpp = LlamaCpp()
llamaCpp.isRealInferenceSupported(): Boolean
llamaCpp.isModelLoaded(): Boolean
llamaCpp.getModeDescription(): String

// 加载模型
llamaCpp.loadModel(modelPath: String, nCtx: Int = 2048): Boolean

// 生成回复
llamaCpp.generate(
    prompt: String,
    maxTokens: Int = 256,
    temperature: Float = 0.7f,
    callback: GenerationCallback
)

// 停止生成
llamaCpp.stopGeneration()
```

### LocalAIManager

```kotlin
// 获取单例
val manager = LocalAIManager.getInstance(context)

// 状态查询
manager.getState(): ModelState
manager.isReady(): Boolean
manager.isRealInferenceSupported(): Boolean

// 模型管理
manager.downloadModel(config, onProgress, onComplete)
manager.loadModel(modelName, onProgress, onComplete)
manager.unloadModel()

// 推理
manager.generateResponse(
    prompt: String,
    maxTokens: Int = 512,
    temperature: Float = 0.7f,
    onToken: (String) -> Unit,
    onComplete: (String) -> Unit,
    onError: (String) -> Unit
)
```

## 版本历史

- **v4.1.0** - 重构架构，改进稳定性和错误处理
- **v4.0.0** - 添加本地 AI 模型管理功能
- **v3.0.0** - 集成 llama.cpp，支持离线推理
