/**
 * DeepSeek AI Assistant - Android 应用构建配置
 * 
 * 功能：
 * - 联网 AI 模式（调用 DeepSeek/OpenAI API）
 * - 本地 AI 模式（使用 llama.cpp 离线推理）
 * 
 * 本地 AI 配置说明：
 * 要启用真正的本地 AI 推理，需要：
 * 1. 运行 setup_llama.bat 下载 llama.cpp 源码
 * 2. 将项目移动到纯英文路径（例如 D:\Projects\DeepSeekAI）
 * 3. 重新同步 Gradle 并构建
 * 4. 首次编译可能需要 5-10 分钟
 */

plugins {
    id("com.android.application")
    id("org.jetbrains.kotlin.android")
}

android {
    namespace = "com.example.deepseekaiassistant"
    compileSdk = 34

    defaultConfig {
        applicationId = "com.example.deepseekaiassistant"
        minSdk = 26
        targetSdk = 34
        versionCode = 4
        versionName = "4.1.0"

        testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"
        
        // 仅支持 ARM64 架构（现代手机主流架构）
        // 可以根据需要添加 "armeabi-v7a" 支持老设备
        ndk {
            abiFilters += listOf("arm64-v8a")
        }
        
        // CMake 参数
        externalNativeBuild {
            cmake {
                arguments += listOf(
                    "-DANDROID_STL=c++_shared",
                    "-DANDROID_TOOLCHAIN=clang"
                )
                // 使用 Release 配置获得更好的性能
                // 如需调试，改为 "Debug"
                arguments += "-DCMAKE_BUILD_TYPE=Release"
            }
        }
    }
    
    // ==============================================================================
    // Native 构建配置 - llama.cpp
    // ==============================================================================
    externalNativeBuild {
        cmake {
            path = file("src/main/cpp/CMakeLists.txt")
            version = "3.22.1"
        }
    }

    buildTypes {
        debug {
            isDebuggable = true
            isMinifyEnabled = false
        }
        release {
            isMinifyEnabled = false
            proguardFiles(
                getDefaultProguardFile("proguard-android-optimize.txt"),
                "proguard-rules.pro"
            )
        }
    }
    
    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }
    
    kotlinOptions {
        jvmTarget = "17"
    }
    
    buildFeatures {
        viewBinding = true
    }
    
    packaging {
        resources {
            excludes += "/META-INF/{AL2.0,LGPL2.1}"
            excludes += "/META-INF/DEPENDENCIES"
        }
        // 避免重复的 native 库
        jniLibs {
            pickFirsts += listOf("**/libc++_shared.so")
        }
    }
    
    // 大文件支持
    @Suppress("UnstableApiUsage")
    androidResources {
        noCompress += listOf("gguf", "bin")
    }
}

// ==============================================================================
// 依赖解析策略
// ==============================================================================
configurations.all {
    resolutionStrategy {
        // 解决 annotations 库冲突
        force("org.jetbrains:annotations:23.0.0")
    }
    exclude(group = "org.jetbrains", module = "annotations-java5")
}

// ==============================================================================
// 依赖配置
// ==============================================================================
dependencies {
    // ==================== 核心 AndroidX 依赖 ====================
    implementation("androidx.core:core-ktx:1.12.0")
    implementation("androidx.appcompat:appcompat:1.6.1")
    implementation("com.google.android.material:material:1.11.0")
    implementation("androidx.constraintlayout:constraintlayout:2.1.4")
    implementation("androidx.activity:activity-ktx:1.8.2")
    implementation("androidx.fragment:fragment-ktx:1.6.2")
    
    // ==================== 生命周期组件 ====================
    implementation("androidx.lifecycle:lifecycle-runtime-ktx:2.7.0")
    implementation("androidx.lifecycle:lifecycle-viewmodel-ktx:2.7.0")
    implementation("androidx.lifecycle:lifecycle-livedata-ktx:2.7.0")
    
    // ==================== 协程支持 ====================
    implementation("org.jetbrains.kotlinx:kotlinx-coroutines-android:1.7.3")
    implementation("org.jetbrains.kotlinx:kotlinx-coroutines-core:1.7.3")
    
    // ==================== 网络请求（联网 AI 模式） ====================
    implementation("com.squareup.retrofit2:retrofit:2.9.0")
    implementation("com.squareup.retrofit2:converter-gson:2.9.0")
    implementation("com.squareup.okhttp3:okhttp:4.12.0")
    implementation("com.squareup.okhttp3:logging-interceptor:4.12.0")
    implementation("com.squareup.okhttp3:okhttp-sse:4.12.0")  // SSE 流式响应
    
    // ==================== UI 组件 ====================
    // Markdown 渲染
    implementation("io.noties.markwon:core:4.6.2")
    implementation("io.noties.markwon:html:4.6.2")
    
    // 图片加载
    implementation("io.coil-kt:coil:2.5.0")
    
    // RecyclerView 适配器
    implementation("com.github.CymChad:BaseRecyclerViewAdapterHelper:3.0.7")
    implementation("androidx.recyclerview:recyclerview:1.3.2")
    
    // ViewPager2
    implementation("androidx.viewpager2:viewpager2:1.0.0")
    
    // 下拉刷新
    implementation("androidx.swiperefreshlayout:swiperefreshlayout:1.1.0")
    
    // ==================== 工具库 ====================
    // 权限申请
    implementation("com.guolindev.permissionx:permissionx:1.7.1")
    
    // 设置页面
    implementation("androidx.preference:preference-ktx:1.2.1")
    
    // 文件管理
    implementation("androidx.documentfile:documentfile:1.0.1")
    
    // ==================== 数学计算模块 ====================
    // 图表库（函数图像绘制）
    implementation("com.github.PhilJay:MPAndroidChart:v3.1.0")
    
    // 数学表达式解析（备用）
    implementation("net.objecthunter:exp4j:0.4.8")
    
    // WebView 增强（LaTeX 渲染）
    implementation("androidx.webkit:webkit:1.9.0")
    
    // ==================== 测试依赖 ====================
    testImplementation("junit:junit:4.13.2")
    androidTestImplementation("androidx.test.ext:junit:1.1.5")
    androidTestImplementation("androidx.test.espresso:espresso-core:3.5.1")
}
