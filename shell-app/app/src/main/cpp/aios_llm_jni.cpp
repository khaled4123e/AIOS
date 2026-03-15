#include <jni.h>
#include <string>
#include <vector>
#include <android/log.h>

#include "llama.h"

#define TAG "AIOS_LLM"
#define LOGI(...) __android_log_print(ANDROID_LOG_INFO,  TAG, __VA_ARGS__)
#define LOGE(...) __android_log_print(ANDROID_LOG_ERROR, TAG, __VA_ARGS__)

// ---------------------------------------------------------------------------
// Helper: convert jstring to std::string
// ---------------------------------------------------------------------------
static std::string jstring_to_string(JNIEnv *env, jstring jstr) {
    if (!jstr) return "";
    const char *raw = env->GetStringUTFChars(jstr, nullptr);
    std::string result(raw);
    env->ReleaseStringUTFChars(jstr, raw);
    return result;
}

// ---------------------------------------------------------------------------
// Opaque handle that wraps both model and sampler defaults
// ---------------------------------------------------------------------------
struct aios_model_handle {
    llama_model  *model   = nullptr;
    int           n_ctx   = 2048;
    int           n_threads = 4;
};

// ---------------------------------------------------------------------------
// Inline helpers for batch manipulation (removed from newer llama.cpp API)
// ---------------------------------------------------------------------------
static void batch_clear(llama_batch & batch) {
    batch.n_tokens = 0;
}

static void batch_add(llama_batch & batch, llama_token token, llama_pos pos,
                       const std::vector<llama_seq_id> & seq_ids, bool logits) {
    const int32_t i = batch.n_tokens;
    batch.token   [i] = token;
    batch.pos     [i] = pos;
    batch.n_seq_id[i] = (int32_t) seq_ids.size();
    for (size_t s = 0; s < seq_ids.size(); s++) {
        batch.seq_id[i][s] = seq_ids[s];
    }
    batch.logits  [i] = logits ? 1 : 0;
    batch.n_tokens++;
}

extern "C" {

// ---------------------------------------------------------------------------
// Load model from a GGUF file on disk.
// Returns a pointer cast to jlong (0 on failure).
// ---------------------------------------------------------------------------
JNIEXPORT jlong JNICALL
Java_dev_aios_shell_ai_LlamaEngine_nativeLoadModel(
        JNIEnv *env, jobject /* obj */,
        jstring modelPath, jint nThreads, jint nCtx, jint nGpuLayers)
{
    std::string path = jstring_to_string(env, modelPath);
    LOGI("Loading model: %s  threads=%d ctx=%d gpu_layers=%d",
         path.c_str(), nThreads, nCtx, nGpuLayers);

    // -- model params --
    llama_model_params model_params = llama_model_default_params();
    model_params.n_gpu_layers = nGpuLayers;   // 0 for CPU-only

    llama_model *model = llama_model_load_from_file(path.c_str(), model_params);
    if (!model) {
        LOGE("Failed to load model from %s", path.c_str());
        return 0;
    }

    auto *handle    = new aios_model_handle();
    handle->model   = model;
    handle->n_ctx   = nCtx > 0 ? nCtx : 2048;
    handle->n_threads = nThreads > 0 ? nThreads : 4;

    LOGI("Model loaded successfully. handle=%p", handle);
    return reinterpret_cast<jlong>(handle);
}

// ---------------------------------------------------------------------------
// Generate a completion given a prompt.
// Returns the generated text or an error string prefixed with "[ERROR]".
// ---------------------------------------------------------------------------
JNIEXPORT jstring JNICALL
Java_dev_aios_shell_ai_LlamaEngine_nativeGenerate(
        JNIEnv *env, jobject /* obj */,
        jlong modelHandle, jstring prompt, jint maxTokens, jfloat temperature)
{
    if (modelHandle == 0) {
        return env->NewStringUTF("[ERROR] Invalid model handle");
    }

    auto *handle = reinterpret_cast<aios_model_handle *>(modelHandle);
    std::string prompt_str = jstring_to_string(env, prompt);

    // -- create context for this generation --
    llama_context_params ctx_params = llama_context_default_params();
    ctx_params.n_ctx     = handle->n_ctx;
    ctx_params.n_threads = handle->n_threads;
    ctx_params.n_threads_batch = handle->n_threads;

    llama_context *ctx = llama_init_from_model(handle->model, ctx_params);
    if (!ctx) {
        LOGE("Failed to create llama context");
        return env->NewStringUTF("[ERROR] Failed to create context");
    }

    // -- apply chat template if available --
    const llama_vocab *vocab = llama_model_get_vocab(handle->model);
    std::string formatted_prompt;

    // Try to apply the chat template (user message)
    std::vector<llama_chat_message> messages;
    llama_chat_message user_msg;
    user_msg.role    = "user";
    user_msg.content = prompt_str.c_str();
    messages.push_back(user_msg);

    // First call to get required buffer size
    int32_t tmpl_len = llama_chat_apply_template(
        nullptr, messages.data(), messages.size(),
        true, nullptr, 0);

    if (tmpl_len > 0) {
        std::vector<char> buf(tmpl_len + 1);
        llama_chat_apply_template(
            nullptr, messages.data(), messages.size(),
            true, buf.data(), buf.size());
        formatted_prompt = std::string(buf.data(), tmpl_len);
        LOGI("Applied chat template, length=%d", tmpl_len);
    } else {
        // No chat template -- use the raw prompt
        formatted_prompt = prompt_str;
    }

    // -- tokenize --
    const int max_tokens_est = formatted_prompt.size() + 256;
    std::vector<llama_token> tokens(max_tokens_est);
    int n_tokens = llama_tokenize(vocab, formatted_prompt.c_str(),
                                  formatted_prompt.size(),
                                  tokens.data(), tokens.size(),
                                  true,   // add_special
                                  true);  // parse_special
    if (n_tokens < 0) {
        // Buffer was too small; resize and retry
        tokens.resize(-n_tokens);
        n_tokens = llama_tokenize(vocab, formatted_prompt.c_str(),
                                  formatted_prompt.size(),
                                  tokens.data(), tokens.size(),
                                  true, true);
    }
    if (n_tokens < 0) {
        llama_free(ctx);
        LOGE("Tokenization failed");
        return env->NewStringUTF("[ERROR] Tokenization failed");
    }
    tokens.resize(n_tokens);

    // Check context length
    if (n_tokens + maxTokens > handle->n_ctx) {
        LOGE("Prompt too long: %d tokens + %d max > %d ctx",
             n_tokens, maxTokens, handle->n_ctx);
    }

    // -- create batch and evaluate prompt tokens --
    llama_batch batch = llama_batch_init(n_tokens, 0, 1);
    for (int i = 0; i < n_tokens; i++) {
        batch_add(batch,tokens[i], i, {0}, false);
    }
    batch.logits[batch.n_tokens - 1] = true;  // we want logits for the last token

    if (llama_decode(ctx, batch) != 0) {
        llama_batch_free(batch);
        llama_free(ctx);
        LOGE("llama_decode failed for prompt");
        return env->NewStringUTF("[ERROR] Decode failed");
    }

    // -- set up sampler --
    llama_sampler *smpl = llama_sampler_chain_init(llama_sampler_chain_default_params());
    if (temperature <= 0.0f) {
        // Greedy
        llama_sampler_chain_add(smpl, llama_sampler_init_greedy());
    } else {
        llama_sampler_chain_add(smpl, llama_sampler_init_top_k(40));
        llama_sampler_chain_add(smpl, llama_sampler_init_top_p(0.95f, 1));
        llama_sampler_chain_add(smpl, llama_sampler_init_temp(temperature));
        llama_sampler_chain_add(smpl, llama_sampler_init_dist(0));
    }

    // -- autoregressive generation loop --
    std::string result;
    int n_cur = n_tokens;

    for (int i = 0; i < maxTokens; i++) {
        llama_token new_token = llama_sampler_sample(smpl, ctx, -1);

        // Check for end of generation
        if (llama_vocab_is_eog(vocab, new_token)) {
            LOGI("EOS reached after %d tokens", i);
            break;
        }

        // Detokenize
        char buf[256];
        int len = llama_token_to_piece(vocab, new_token, buf, sizeof(buf), 0, true);
        if (len > 0) {
            result.append(buf, len);
        }

        // Prepare next batch
        batch_clear(batch);
        batch_add(batch,new_token, n_cur, {0}, true);
        n_cur++;

        if (llama_decode(ctx, batch) != 0) {
            LOGE("llama_decode failed at token %d", i);
            break;
        }
    }

    llama_sampler_free(smpl);
    llama_batch_free(batch);
    llama_free(ctx);

    LOGI("Generated %zu chars", result.size());
    return env->NewStringUTF(result.c_str());
}

// ---------------------------------------------------------------------------
// Free model and handle
// ---------------------------------------------------------------------------
JNIEXPORT void JNICALL
Java_dev_aios_shell_ai_LlamaEngine_nativeFreeModel(
        JNIEnv * /* env */, jobject /* obj */, jlong modelHandle)
{
    if (modelHandle == 0) return;
    auto *handle = reinterpret_cast<aios_model_handle *>(modelHandle);
    if (handle->model) {
        llama_model_free(handle->model);
        LOGI("Model freed. handle=%p", handle);
    }
    delete handle;
}

// ---------------------------------------------------------------------------
// Return a JSON string with basic model information
// ---------------------------------------------------------------------------
JNIEXPORT jstring JNICALL
Java_dev_aios_shell_ai_LlamaEngine_nativeGetModelInfo(
        JNIEnv *env, jobject /* obj */, jlong modelHandle)
{
    if (modelHandle == 0) {
        return env->NewStringUTF("{\"error\":\"invalid handle\"}");
    }

    auto *handle = reinterpret_cast<aios_model_handle *>(modelHandle);
    const llama_model *model = handle->model;

    char desc[256] = {0};
    llama_model_desc(model, desc, sizeof(desc));

    uint64_t model_size = llama_model_size(model);
    uint64_t n_params   = llama_model_n_params(model);

    char buf[1024];
    snprintf(buf, sizeof(buf),
             "{\"description\":\"%s\","
             "\"size_bytes\":%llu,"
             "\"n_params\":%llu,"
             "\"n_ctx\":%d}",
             desc,
             (unsigned long long)model_size,
             (unsigned long long)n_params,
             handle->n_ctx);

    return env->NewStringUTF(buf);
}

} // extern "C"
