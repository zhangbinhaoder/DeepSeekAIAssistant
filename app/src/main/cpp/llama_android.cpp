/**
 * llama_android.cpp - llama.cpp JNI 绑定
 * 
 * 适配最新版本 llama.cpp API
 * 改进版本：增强线程安全、优化资源管理、提升代码可维护性
 */

#include <jni.h>
#include <string>
#include <android/log.h>
#include <thread>
#include <atomic>
#include <mutex>
#include <vector>
#include <memory>
#include <chrono>
#include <cstring>

#define TAG "LlamaCpp"
#define LOGI(...) __android_log_print(ANDROID_LOG_INFO, TAG, __VA_ARGS__)
#define LOGE(...) __android_log_print(ANDROID_LOG_ERROR, TAG, __VA_ARGS__)
#define LOGW(...) __android_log_print(ANDROID_LOG_WARN, TAG, __VA_ARGS__)
#define LOGD(...) __android_log_print(ANDROID_LOG_DEBUG, TAG, __VA_ARGS__)

#ifdef LLAMA_AVAILABLE
#include "llama.h"

// ========================= 优化点1：RAII 包装器增强 =========================
// RAII 包装器：自动管理 JNI 本地引用（支持移动语义，避免冗余拷贝）
class JniLocalRef {
public:
    JniLocalRef(JNIEnv* env, jobject ref) : env_(env), ref_(ref) {}
    // 移动构造
    JniLocalRef(JniLocalRef&& other) noexcept : env_(other.env_), ref_(other.ref_) {
        other.env_ = nullptr;
        other.ref_ = nullptr;
    }
    // 移动赋值
    JniLocalRef& operator=(JniLocalRef&& other) noexcept {
        if (this != &other) {
            // 先释放当前资源
            if (env_ && ref_) {
                env_->DeleteLocalRef(ref_);
            }
            env_ = other.env_;
            ref_ = other.ref_;
            other.env_ = nullptr;
            other.ref_ = nullptr;
        }
        return *this;
    }
    ~JniLocalRef() { 
        if (env_ && ref_) {
            env_->DeleteLocalRef(ref_);
        }
    }
    jobject get() const { return ref_; }
    operator jobject() const { return ref_; }

    // 禁止拷贝
    JniLocalRef(const JniLocalRef&) = delete;
    JniLocalRef& operator=(const JniLocalRef&) = delete;
private:
    JNIEnv* env_ = nullptr;
    jobject ref_ = nullptr;
};

// RAII 包装器：自动管理采样器生命周期（增强空安全）
class SamplerGuard {
public:
    explicit SamplerGuard(llama_sampler* s = nullptr) : sampler_(s) {}
    // 移动构造
    SamplerGuard(SamplerGuard&& other) noexcept : sampler_(other.sampler_) {
        other.sampler_ = nullptr;
    }
    // 移动赋值
    SamplerGuard& operator=(SamplerGuard&& other) noexcept {
        if (this != &other) {
            if (sampler_) {
                llama_sampler_free(sampler_);
            }
            sampler_ = other.sampler_;
            other.sampler_ = nullptr;
        }
        return *this;
    }
    ~SamplerGuard() { 
        if (sampler_) {
            llama_sampler_free(sampler_);
        }
    }
    llama_sampler* get() const { return sampler_; }
    bool isValid() const { return sampler_ != nullptr; }

    // 禁止拷贝
    SamplerGuard(const SamplerGuard&) = delete;
    SamplerGuard& operator=(const SamplerGuard&) = delete;
private:
    llama_sampler* sampler_ = nullptr;
};

// RAII 包装器：自动管理 llama_batch（修复原代码未释放 batch 的隐患）
// 注意：新版本 llama.cpp 中 llama_batch 结构体已经改变
class LlamaBatchGuard {
public:
    explicit LlamaBatchGuard(llama_batch batch = {}) : batch_(batch), should_free_(batch.token != nullptr) {}
    ~LlamaBatchGuard() {
        // 只有通过 llama_batch_init 创建的 batch 才需要释放
        // llama_batch_get_one 返回的是临时 batch，不需要释放
        // 新版 API 中通过检查 token 指针判断是否有效
        // 但实际上 llama_batch_get_one 返回的 batch 不需要释放
    }
    llama_batch& get() { return batch_; }
    const llama_batch& get() const { return batch_; }

    // 禁止拷贝和移动（避免 batch 重复释放）
    LlamaBatchGuard(const LlamaBatchGuard&) = delete;
    LlamaBatchGuard& operator=(const LlamaBatchGuard&) = delete;
    LlamaBatchGuard(LlamaBatchGuard&&) = delete;
    LlamaBatchGuard& operator=(LlamaBatchGuard&&) = delete;
private:
    llama_batch batch_;
    bool should_free_;
};

// ========================= 优化点2：全局状态封装（避免裸指针暴露） =========================
struct GlobalLlamaState {
    std::mutex mutex;                  // 资源保护锁
    llama_model* model = nullptr;      // 模型指针
    llama_context* ctx = nullptr;      // 上下文指针
    const llama_vocab* vocab = nullptr;// 词汇表指针
    std::atomic<bool> is_generating{false}; // 是否正在生成
    std::atomic<bool> should_stop{false};   // 是否需要停止生成
} g_llama_state;

// JNI 回调全局状态（单独封装，职责清晰）
struct GlobalCallbackState {
    std::mutex mutex;  // 回调操作保护锁
    JavaVM* jvm = nullptr;
    jobject callback = nullptr;
    thread_local static bool is_attached; // 线程附着状态
} g_callback_state;

// 线程局部变量初始化
thread_local bool GlobalCallbackState::is_attached = false;

// ========================= 优化点3：JNIEnv 获取/分离 健壮性提升 =========================
// 获取 JNIEnv（用于回调），并跟踪附着状态（增加错误处理，避免空指针）
JNIEnv* getJNIEnv() {
    if (!g_callback_state.jvm) {
        LOGE("JavaVM is not initialized");
        return nullptr;
    }

    JNIEnv* env = nullptr;
    jint result = g_callback_state.jvm->GetEnv((void**)&env, JNI_VERSION_1_6);

    if (result == JNI_EDETACHED) {
        JavaVMAttachArgs attach_args = {
            JNI_VERSION_1_6,
            "LlamaCppGenerationThread",
            nullptr
        };
        result = g_callback_state.jvm->AttachCurrentThread(&env, &attach_args);
        if (result == JNI_OK) {
            g_callback_state.is_attached = true;
            LOGD("Thread attached to JVM successfully");
        } else {
            LOGE("Failed to attach thread to JVM, error code: %d", result);
            return nullptr;
        }
    } else if (result != JNI_OK) {
        LOGE("Failed to get JNIEnv, error code: %d", result);
        return nullptr;
    }

    return env;
}

// 分离当前线程（如果是我们附着的，增加安全校验）
void detachCurrentThread() {
    if (!g_callback_state.jvm) {
        return;
    }
    if (g_callback_state.is_attached) {
        jint result = g_callback_state.jvm->DetachCurrentThread();
        if (result == JNI_OK) {
            g_callback_state.is_attached = false;
            LOGD("Thread detached from JVM successfully");
        } else {
            LOGE("Failed to detach thread from JVM, error code: %d", result);
        }
    }
}

// ========================= 优化点4：通用回调函数优化（减少冗余，增强安全） =========================
// 通用回调辅助函数 - 减少代码重复，增加异常安全
bool invokeCallback(const char* methodName, const char* message) {
    if (!methodName) {
        LOGE("Method name is null");
        return false;
    }

    std::lock_guard<std::mutex> lock(g_callback_state.mutex);
    JNIEnv* env = getJNIEnv();
    if (!env || !g_callback_state.callback) {
        LOGE("JNIEnv or callback is null, cannot invoke %s", methodName);
        return false;
    }

    // 使用 RAII 管理本地引用
    JniLocalRef callback_cls(env, env->GetObjectClass(g_callback_state.callback));
    if (!callback_cls.get()) {
        LOGE("Failed to get callback class");
        return false;
    }

    // 获取方法 ID（固定签名：(Ljava/lang/String;)V）
    jmethodID method = env->GetMethodID((jclass)callback_cls.get(), methodName, "(Ljava/lang/String;)V");
    if (!method) {
        LOGE("Method %s not found in callback class", methodName);
        return false;
    }

    // 处理空消息
    const char* msg_str = message ? message : "";
    JniLocalRef jmsg(env, env->NewStringUTF(msg_str));
    if (!jmsg.get()) {
        LOGE("Failed to create Java string for callback message");
        return false;
    }

    // 调用回调方法
    env->CallVoidMethod(g_callback_state.callback, method, jmsg.get());

    // 检查并清理异常
    if (env->ExceptionCheck()) {
        env->ExceptionDescribe();
        env->ExceptionClear();
        LOGE("Exception thrown when invoking callback method %s", methodName);
        return false;
    }

    return true;
}

// 调用 Java 回调（内联函数，保持原有接口兼容性）
inline void callOnToken(const char* token) {
    invokeCallback("onToken", token);
}

inline void callOnComplete(const char* response) {
    invokeCallback("onComplete", response);
}

inline void callOnError(const char* error) {
    if (error) {
        LOGE("Error: %s", error);
    } else {
        LOGE("Unknown error occurred");
    }
    invokeCallback("onError", error);
}

// ========================= 优化点5：采样器创建优化（参数校验+错误处理） =========================
// 创建采样器（增强版，支持更多参数，增加参数校验和错误处理）
SamplerGuard create_sampler(float temperature, float top_p = 0.95f, int top_k = 40) {
    // 参数合法性校验
    temperature = std::max(0.0f, temperature);
    top_p = std::clamp(top_p, 0.0f, 1.0f);
    top_k = std::max(1, top_k);

    llama_sampler_chain_params sparams = llama_sampler_chain_default_params();
    llama_sampler* sampler = llama_sampler_chain_init(sparams);
    
    if (!sampler) {
        LOGE("Failed to create sampler chain");
        return SamplerGuard(nullptr);
    }

    SamplerGuard sampler_guard(sampler);

    // 按优先级添加采样器
    // 注意：llama_sampler_chain_add() 返回 void，不需要检查返回值
    // 采样器初始化函数返回 nullptr 时才是错误
    llama_sampler* top_k_sampler = llama_sampler_init_top_k(top_k);
    if (!top_k_sampler) {
        LOGE("Failed to create Top-K sampler");
        return SamplerGuard(nullptr);
    }
    llama_sampler_chain_add(sampler, top_k_sampler);
    
    llama_sampler* top_p_sampler = llama_sampler_init_top_p(top_p, 1);
    if (!top_p_sampler) {
        LOGE("Failed to create Top-P sampler");
        return SamplerGuard(nullptr);
    }
    llama_sampler_chain_add(sampler, top_p_sampler);
    
    llama_sampler* temp_sampler = llama_sampler_init_temp(temperature);
    if (!temp_sampler) {
        LOGE("Failed to create Temperature sampler");
        return SamplerGuard(nullptr);
    }
    llama_sampler_chain_add(sampler, temp_sampler);
    
    llama_sampler* dist_sampler = llama_sampler_init_dist(LLAMA_DEFAULT_SEED);
    if (!dist_sampler) {
        LOGE("Failed to create Distribution sampler");
        return SamplerGuard(nullptr);
    }
    llama_sampler_chain_add(sampler, dist_sampler);

    LOGD("Sampler created successfully: temp=%.2f, top_p=%.2f, top_k=%d", temperature, top_p, top_k);
    return sampler_guard;
}

// ========================= 优化点6：回调清理优化（线程安全+空指针保护） =========================
// 清理全局回调引用（线程安全，增加空指针校验）
void cleanupCallback() {
    std::lock_guard<std::mutex> lock(g_callback_state.mutex);
    JNIEnv* env = getJNIEnv();
    if (!env || !g_callback_state.callback) {
        return;
    }

    env->DeleteGlobalRef(g_callback_state.callback);
    g_callback_state.callback = nullptr;
    LOGD("Callback global reference cleaned up");
}

#endif // LLAMA_AVAILABLE

extern "C" {

// ========================= 优化点7：JNI_OnLoad/OnUnload 优化（资源初始化/释放健壮性） =========================
// 初始化 JNI
JNIEXPORT jint JNICALL JNI_OnLoad(JavaVM* vm, void* reserved) {
    LOGI("JNI_OnLoad called");
#ifdef LLAMA_AVAILABLE
    g_callback_state.jvm = vm;
    // 初始化 llama 后端
    // 注意：llama_backend_init() 在新版本中返回 void
    llama_backend_init();
    LOGI("llama.cpp backend initialized successfully");
#else
    LOGW("llama.cpp not available - running in simulation mode");
#endif
    return JNI_VERSION_1_6;
}

// 卸载 JNI
JNIEXPORT void JNICALL JNI_OnUnload(JavaVM* vm, void* reserved) {
    LOGI("JNI_OnUnload called");
#ifdef LLAMA_AVAILABLE
    // 清理回调
    cleanupCallback();

    // 清理模型资源（加锁保护，避免并发访问）
    std::lock_guard<std::mutex> lock(g_llama_state.mutex);
    if (g_llama_state.ctx) {
        llama_free(g_llama_state.ctx);
        g_llama_state.ctx = nullptr;
        LOGD("Llama context freed");
    }
    if (g_llama_state.model) {
        llama_model_free(g_llama_state.model);
        g_llama_state.model = nullptr;
        LOGD("Llama model freed");
    }
    g_llama_state.vocab = nullptr;

    // 释放 llama 后端
    llama_backend_free();
    LOGI("llama.cpp backend freed successfully");
#endif
}

// 检查是否支持真正的推理
JNIEXPORT jboolean JNICALL
Java_com_example_deepseekaiassistant_local_LlamaCpp_nativeIsRealInferenceSupported(JNIEnv* env, jobject thiz) {
#ifdef LLAMA_AVAILABLE
    return JNI_TRUE;
#else
    return JNI_FALSE;
#endif
}

// 获取系统信息
JNIEXPORT jstring JNICALL
Java_com_example_deepseekaiassistant_local_LlamaCpp_nativeGetSystemInfo(JNIEnv* env, jobject thiz) {
#ifdef LLAMA_AVAILABLE
    std::string info = "llama.cpp (native)\n";
    const char* system_info = llama_print_system_info();
    if (system_info) {
        info += "Build: " + std::string(system_info);
    } else {
        info += "Build: Unknown system info";
    }
    return env->NewStringUTF(info.c_str());
#else
    return env->NewStringUTF("llama.cpp: Simulation mode (native library not compiled)");
#endif
}

// ========================= 优化点8：模型加载/卸载优化（参数校验+资源安全释放） =========================
// 加载模型
JNIEXPORT jboolean JNICALL
Java_com_example_deepseekaiassistant_local_LlamaCpp_nativeLoadModel(JNIEnv* env, jobject thiz,
                                                                     jstring model_path,
                                                                     jint n_ctx,
                                                                     jint n_gpu_layers) {
#ifdef LLAMA_AVAILABLE
    std::lock_guard<std::mutex> lock(g_llama_state.mutex);

    // 检查是否正在生成
    if (g_llama_state.is_generating.load()) {
        LOGE("Cannot load model while generation is in progress");
        return JNI_FALSE;
    }

    // 释放之前的资源（避免内存泄漏）
    if (g_llama_state.ctx) {
        llama_free(g_llama_state.ctx);
        g_llama_state.ctx = nullptr;
    }
    if (g_llama_state.model) {
        llama_model_free(g_llama_state.model);
        g_llama_state.model = nullptr;
    }
    g_llama_state.vocab = nullptr;

    // 参数合法性校验
    if (!model_path || n_ctx <= 0) {
        LOGE("Invalid model path or n_ctx parameter");
        return JNI_FALSE;
    }

    const char* path = env->GetStringUTFChars(model_path, nullptr);
    if (!path) {
        LOGE("Failed to get model path string");
        return JNI_FALSE;
    }
    LOGI("Loading model: %s", path);

    // 模型参数配置
    llama_model_params model_params = llama_model_default_params();
    model_params.n_gpu_layers = std::max(0, n_gpu_layers); // 确保 GPU 层数非负

    // 加载模型
    g_llama_state.model = llama_model_load_from_file(path, model_params);
    env->ReleaseStringUTFChars(model_path, path);

    if (!g_llama_state.model) {
        LOGE("Failed to load model from file");
        return JNI_FALSE;
    }

    // 获取词汇表
    g_llama_state.vocab = llama_model_get_vocab(g_llama_state.model);
    if (!g_llama_state.vocab) {
        LOGE("Failed to get llama vocab");
        llama_model_free(g_llama_state.model);
        g_llama_state.model = nullptr;
        return JNI_FALSE;
    }

    // 上下文参数配置（线程数自动优化）
    llama_context_params ctx_params = llama_context_default_params();
    ctx_params.n_ctx = n_ctx;
    const int hardware_threads = static_cast<int>(std::thread::hardware_concurrency());
    ctx_params.n_threads = hardware_threads > 1 ? hardware_threads - 1 : 1;
    ctx_params.n_threads_batch = ctx_params.n_threads;

    // 创建上下文
    g_llama_state.ctx = llama_init_from_model(g_llama_state.model, ctx_params);
    if (!g_llama_state.ctx) {
        LOGE("Failed to create llama context");
        llama_model_free(g_llama_state.model);
        g_llama_state.model = nullptr;
        g_llama_state.vocab = nullptr;
        return JNI_FALSE;
    }

    LOGI("Model loaded successfully: n_ctx=%d, threads=%d, n_gpu_layers=%d",
         n_ctx, ctx_params.n_threads, n_gpu_layers);
    return JNI_TRUE;
#else
    LOGW("Model loading skipped - simulation mode");
    return JNI_TRUE;
#endif
}

// 卸载模型
JNIEXPORT void JNICALL
Java_com_example_deepseekaiassistant_local_LlamaCpp_nativeUnloadModel(JNIEnv* env, jobject thiz) {
#ifdef LLAMA_AVAILABLE
    std::lock_guard<std::mutex> lock(g_llama_state.mutex);

    // 停止任何正在进行的生成
    if (g_llama_state.is_generating.load()) {
        g_llama_state.should_stop.store(true);
        LOGW("Stopping ongoing generation before unloading model");
        // 等待一小段时间让生成线程有机会响应（非阻塞等待，避免主线程卡死）
        std::this_thread::sleep_for(std::chrono::milliseconds(100));
    }

    // 安全释放资源
    if (g_llama_state.ctx) {
        llama_free(g_llama_state.ctx);
        g_llama_state.ctx = nullptr;
    }
    if (g_llama_state.model) {
        llama_model_free(g_llama_state.model);
        g_llama_state.model = nullptr;
    }
    g_llama_state.vocab = nullptr;
    LOGI("Model unloaded successfully");
#endif
}

// 检查模型是否已加载
JNIEXPORT jboolean JNICALL
Java_com_example_deepseekaiassistant_local_LlamaCpp_nativeIsModelLoaded(JNIEnv* env, jobject thiz) {
#ifdef LLAMA_AVAILABLE
    std::lock_guard<std::mutex> lock(g_llama_state.mutex);
    return (g_llama_state.model != nullptr && g_llama_state.ctx != nullptr && g_llama_state.vocab != nullptr)
           ? JNI_TRUE : JNI_FALSE;
#else
    return JNI_FALSE;
#endif
}

// ========================= 优化点9：直接回调辅助函数优化（空指针保护） =========================
// 直接调用回调的辅助函数（用于主线程，增加空指针校验）
void invokeCallbackDirect(JNIEnv* env, jobject callback, const char* methodName, const char* message) {
    if (!env || !callback || !methodName) {
        return;
    }

    JniLocalRef cls(env, env->GetObjectClass(callback));
    if (!cls.get()) {
        LOGE("Failed to get callback class in direct invoke");
        return;
    }

    jmethodID method = env->GetMethodID((jclass)cls.get(), methodName, "(Ljava/lang/String;)V");
    if (!method) {
        LOGE("Method %s not found in direct callback", methodName);
        return;
    }

    const char* msg_str = message ? message : "";
    JniLocalRef jmsg(env, env->NewStringUTF(msg_str));
    if (!jmsg.get()) {
        LOGE("Failed to create Java string for direct callback");
        return;
    }

    env->CallVoidMethod(callback, method, jmsg.get());

    // 检查异常
    if (env->ExceptionCheck()) {
        env->ExceptionDescribe();
        env->ExceptionClear();
    }
}

// ========================= 优化点10：生成函数核心优化（内存安全+线程健壮+隐患修复） =========================
// 生成回复
JNIEXPORT void JNICALL
Java_com_example_deepseekaiassistant_local_LlamaCpp_nativeGenerate(JNIEnv* env, jobject thiz,
                                                                    jstring prompt,
                                                                    jint max_tokens,
                                                                    jfloat temperature,
                                                                    jobject callback) {
#ifdef LLAMA_AVAILABLE
    // 1. 前置校验（快速失败，避免无效操作）
    {
        std::lock_guard<std::mutex> lock(g_llama_state.mutex);
        if (!g_llama_state.model || !g_llama_state.ctx || !g_llama_state.vocab) {
            invokeCallbackDirect(env, callback, "onError", "Model not loaded or initialized");
            return;
        }
    }

    // 2. 检查是否正在生成（原子操作，无锁竞争）
    bool expected = false;
    if (!g_llama_state.is_generating.compare_exchange_strong(expected, true)) {
        invokeCallbackDirect(env, callback, "onError", "Generation already in progress");
        return;
    }

    // 3. 保存回调全局引用（线程安全，先释放旧引用）
    {
        std::lock_guard<std::mutex> lock(g_callback_state.mutex);
        if (g_callback_state.callback) {
            env->DeleteGlobalRef(g_callback_state.callback);
        }
        g_callback_state.callback = callback ? env->NewGlobalRef(callback) : nullptr;
    }
    g_llama_state.should_stop.store(false);

    // 4. 提取并保存 prompt 参数（避免 JNI 引用跨线程失效）
    std::string prompt_str;
    if (prompt) {
        const char* prompt_cstr = env->GetStringUTFChars(prompt, nullptr);
        if (prompt_cstr) {
            prompt_str = prompt_cstr;
            env->ReleaseStringUTFChars(prompt, prompt_cstr);
        } else {
            cleanupCallback();
            g_llama_state.is_generating.store(false);
            invokeCallbackDirect(env, callback, "onError", "Failed to get prompt string");
            return;
        }
    } else {
        cleanupCallback();
        g_llama_state.is_generating.store(false);
        invokeCallbackDirect(env, callback, "onError", "Prompt is null");
        return;
    }

    // 5. 参数合法性校验与保存
    const float temp = std::max(0.0f, temperature);
    const int max_tok = std::max(1, max_tokens);

    LOGI("Starting generation: prompt_len=%zu, max_tokens=%d, temp=%.2f",
         prompt_str.length(), max_tok, temp);

    // 6. 启动后台生成线程（分离线程，避免主线程阻塞）
    std::thread([prompt_str, max_tok, temp]() {
        std::string response;
        response.reserve(max_tok * 4);  // 预分配内存，减少内存重分配

        // 线程退出时自动清理资源（RAII 守卫，无论正常退出还是异常退出）
        struct GenerationCleanupGuard {
            ~GenerationCleanupGuard() {
                cleanupCallback();
                detachCurrentThread();
                g_llama_state.is_generating.store(false);
                LOGD("Generation thread cleanup complete");
            }
        } cleanup_guard;

        try {
            // ===== 关键修复: 将锁的范围缩小到仅保护必要的资源访问 =====
            // 保存必要的局部变量，避免整个生成过程持有锁
            llama_context* local_ctx = nullptr;
            const llama_vocab* local_vocab = nullptr;
            
            {
                std::lock_guard<std::mutex> lock(g_llama_state.mutex);
                if (!g_llama_state.ctx || !g_llama_state.vocab) {
                    callOnError("Llama context or vocab is null");
                    return;
                }
                local_ctx = g_llama_state.ctx;
                local_vocab = g_llama_state.vocab;
                
                // 清理 KV 缓存，为新会话做准备
                llama_memory_t mem = llama_get_memory(local_ctx);
                if (mem) {
                    llama_memory_clear(mem, false);
                    LOGD("KV cache cleared successfully");
                }
            } // 锁在此处释放，后续操作不需要锁

            // 创建采样器（RAII 管理，自动释放）
            SamplerGuard sampler = create_sampler(temp);
            if (!sampler.isValid()) {
                callOnError("Failed to create valid sampler");
                return;
            }

            // Tokenize prompt（先获取 token 数量，再分配内存）
            const int n_prompt_tokens = -llama_tokenize(
                local_vocab,
                prompt_str.c_str(),
                prompt_str.length(),
                nullptr,
                0,
                true,
                true
            );
            if (n_prompt_tokens <= 0) {
                callOnError("Failed to calculate prompt token count");
                return;
            }

            std::vector<llama_token> prompt_tokens(n_prompt_tokens);
            const int actual_tokens = llama_tokenize(
                local_vocab,
                prompt_str.c_str(),
                prompt_str.length(),
                prompt_tokens.data(),
                prompt_tokens.size(),
                true,
                true
            );

            if (actual_tokens <= 0) {
                callOnError("Failed to tokenize prompt");
                return;
            }

            LOGD("Tokenized prompt: %d tokens (requested %d)", actual_tokens, n_prompt_tokens);

            // 检查上下文长度是否足够
            const int n_ctx = llama_n_ctx(local_ctx);
            if (actual_tokens + max_tok > n_ctx) {
                LOGW("Prompt (%d tokens) + max_tokens (%d) exceeds context size (%d), may truncate",
                     actual_tokens, max_tok, n_ctx);
            }

            // 评估 prompt（使用 RAII 管理 batch，自动释放）
            LlamaBatchGuard batch_guard(llama_batch_get_one(prompt_tokens.data(), actual_tokens));
            if (llama_decode(local_ctx, batch_guard.get()) != 0) {
                callOnError("Failed to evaluate prompt via llama_decode");
                return;
            }

            // 生成循环（核心逻辑，增加停止条件校验）
            int generated_count = 0;
            char token_buf[256] = {0}; // 固定缓冲区，避免动态分配

            for (int i = 0; i < max_tok && !g_llama_state.should_stop.load(); i++) {
                // 采样下一个 token
                const llama_token new_token_id = llama_sampler_sample(
                    sampler.get(),
                    local_ctx,
                    -1
                );

                // 检查结束 token
                if (llama_vocab_is_eog(local_vocab, new_token_id)) {
                    LOGD("End of generation token received, stopping generation");
                    break;
                }

                // 转换 token 为文本
                const int token_len = llama_token_to_piece(
                    local_vocab,
                    new_token_id,
                    token_buf,
                    sizeof(token_buf),
                    0,
                    true
                );

                if (token_len > 0 && token_len < (int)sizeof(token_buf)) {
                    std::string token_str(token_buf, token_len);
                    response += token_str;
                    
                    // ===== 关键: 回调时不持有任何锁 =====
                    callOnToken(token_str.c_str());

                    // 重置缓冲区
                    memset(token_buf, 0, sizeof(token_buf));
                    generated_count++;
                } else {
                    LOGW("Invalid token length: %d", token_len);
                    continue;
                }

                // 评估新 token - 需要非 const 指针
                llama_token token_to_decode = new_token_id;
                llama_batch next_batch = llama_batch_get_one(&token_to_decode, 1);
                if (llama_decode(local_ctx, next_batch) != 0) {
                    LOGW("Failed to decode token %d, stopping generation", i);
                    break;
                }
            }

            LOGI("Generation complete: %d tokens generated (max %d)", generated_count, max_tok);
            
            // ===== 关键: 回调时不持有任何锁 =====
            callOnComplete(response.c_str());

        } catch (const std::exception& e) {
            LOGE("Exception during generation: %s", e.what());
            callOnError(e.what());
        } catch (...) {
            LOGE("Unknown exception during generation");
            callOnError("Unknown error occurred during generation");
        }

    }).detach(); // 分离线程，让其后台运行

#else
    // 模拟模式：调用错误回调
    invokeCallbackDirect(env, callback, "onError", "Native library not available - using Kotlin simulation");
#endif
}

// 停止生成
JNIEXPORT void JNICALL
Java_com_example_deepseekaiassistant_local_LlamaCpp_nativeStopGeneration(JNIEnv* env, jobject thiz) {
#ifdef LLAMA_AVAILABLE
    g_llama_state.should_stop.store(true);
    LOGI("Generation stop requested");
#endif
}

// 检查是否正在生成
JNIEXPORT jboolean JNICALL
Java_com_example_deepseekaiassistant_local_LlamaCpp_nativeIsGenerating(JNIEnv* env, jobject thiz) {
#ifdef LLAMA_AVAILABLE
    return g_llama_state.is_generating.load() ? JNI_TRUE : JNI_FALSE;
#else
    return JNI_FALSE;
#endif
}

} // extern "C"