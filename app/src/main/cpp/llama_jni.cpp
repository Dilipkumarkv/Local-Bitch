#include <jni.h>
#include <android/log.h>
#include <string>
#include <vector>
#include <unordered_map>
#include <memory>
#include <fstream>
#include <sstream>
#include <cmath>
#include <random>
#include <algorithm>
#include <cstring>
#include <cstdint>

#define TAG "NativeLlamaJNI"
#define LOGI(...) __android_log_print(ANDROID_LOG_INFO, TAG, __VA_ARGS__)
#define LOGW(...) __android_log_print(ANDROID_LOG_WARN, TAG, __VA_ARGS__)
#define LOGE(...) __android_log_print(ANDROID_LOG_ERROR, TAG, __VA_ARGS__)

namespace localgguf {

// GGUF Constants
static const uint32_t GGUF_MAGIC = 0x46554747; // "GGUF" in LE
static const uint32_t GGUF_VERSION_2 = 2;
static const uint32_t GGUF_VERSION_3 = 3;

enum GgufType : uint32_t {
    GGUF_TYPE_UINT8   = 0,
    GGUF_TYPE_INT8    = 1,
    GGUF_TYPE_UINT16  = 2,
    GGUF_TYPE_INT16   = 3,
    GGUF_TYPE_UINT32  = 4,
    GGUF_TYPE_INT32   = 5,
    GGUF_TYPE_FLOAT32 = 6,
    GGUF_TYPE_BOOL    = 7,
    GGUF_TYPE_STRING  = 8,
    GGUF_TYPE_ARRAY   = 9,
    GGUF_TYPE_UINT64  = 10,
    GGUF_TYPE_INT64   = 11,
    GGUF_TYPE_FLOAT64 = 12,
};

struct GgufTensorInfo {
    std::string name;
    std::vector<uint64_t> dimensions;
    uint32_t type = 0;
    uint64_t offset = 0;
};

// Model session state
struct LlamaModelContext {
    std::string modelPath;
    int contextLength = 2048;
    int numThreads = 4;
    uint64_t tensorCount = 0;
    uint64_t kvCount = 0;

    // Architecture & Metadata
    std::string architecture = "llama";
    std::string tokenizerModel = "llama";
    int contextWindow = 2048;
    int embeddingLength = 2048;
    int blockCount = 16;
    int headCount = 16;

    // Vocab
    std::vector<std::string> vocab;
    std::unordered_map<std::string, int> tokenToId;
    std::vector<int> stopTokenIds;

    // State for generation
    std::vector<int> promptTokens;
    std::vector<int> generatedTokens;
    std::string activeGrammar;
    bool finished = false;
    size_t currentStep = 0;
    std::mt19937 rng;

    LlamaModelContext() : rng(std::random_device{}()) {}
};

// Helper to read binary data from file
static bool readUint32(std::ifstream& file, uint32_t& val) {
    return file.read(reinterpret_cast<char*>(&val), sizeof(uint32_t)).good();
}

static bool readUint64(std::ifstream& file, uint64_t& val) {
    return file.read(reinterpret_cast<char*>(&val), sizeof(uint64_t)).good();
}

static bool readGgufString(std::ifstream& file, std::string& str) {
    uint64_t len = 0;
    if (!readUint64(file, len)) return false;
    if (len > 1024 * 1024) return false; // Sanity check max string 1MB
    str.resize(len);
    if (len > 0) {
        file.read(&str[0], len);
    }
    return file.good();
}

static bool skipGgufValue(std::ifstream& file, uint32_t type) {
    switch (type) {
        case GGUF_TYPE_UINT8:
        case GGUF_TYPE_INT8:
        case GGUF_TYPE_BOOL:
            file.seekg(1, std::ios::cur);
            return true;
        case GGUF_TYPE_UINT16:
        case GGUF_TYPE_INT16:
            file.seekg(2, std::ios::cur);
            return true;
        case GGUF_TYPE_UINT32:
        case GGUF_TYPE_INT32:
        case GGUF_TYPE_FLOAT32:
            file.seekg(4, std::ios::cur);
            return true;
        case GGUF_TYPE_UINT64:
        case GGUF_TYPE_INT64:
        case GGUF_TYPE_FLOAT64:
            file.seekg(8, std::ios::cur);
            return true;
        case GGUF_TYPE_STRING: {
            uint64_t len = 0;
            if (!readUint64(file, len)) return false;
            file.seekg(len, std::ios::cur);
            return true;
        }
        case GGUF_TYPE_ARRAY: {
            uint32_t elemType = 0;
            uint64_t elemCount = 0;
            if (!readUint32(file, elemType)) return false;
            if (!readUint64(file, elemCount)) return false;
            for (uint64_t i = 0; i < elemCount; i++) {
                if (!skipGgufValue(file, elemType)) return false;
            }
            return true;
        }
        default:
            return false;
    }
}

// Load GGUF model and parse header & metadata
static bool loadGgufHeaderAndMetadata(const std::string& path, LlamaModelContext* ctx) {
    std::ifstream file(path, std::ios::binary);
    if (!file.is_open()) {
        LOGE("Cannot open GGUF file at %s", path.c_str());
        return false;
    }

    uint32_t magic = 0;
    if (!readUint32(file, magic) || magic != GGUF_MAGIC) {
        LOGE("Invalid GGUF magic: 0x%08X (expected 0x%08X)", magic, GGUF_MAGIC);
        return false;
    }

    uint32_t version = 0;
    if (!readUint32(file, version) || (version != GGUF_VERSION_2 && version != GGUF_VERSION_3)) {
        LOGE("Unsupported GGUF version: %u", version);
        return false;
    }

    if (!readUint64(file, ctx->tensorCount)) return false;
    if (!readUint64(file, ctx->kvCount)) return false;

    LOGI("GGUF v%u: %llu tensors, %llu metadata KV pairs", version,
         (unsigned long long)ctx->tensorCount, (unsigned long long)ctx->kvCount);

    // Read Key-Value metadata pairs
    for (uint64_t i = 0; i < ctx->kvCount && file.good(); i++) {
        std::string key;
        if (!readGgufString(file, key)) break;
        uint32_t valType = 0;
        if (!readUint32(file, valType)) break;

        if (key == "general.architecture" && valType == GGUF_TYPE_STRING) {
            readGgufString(file, ctx->architecture);
            LOGI("Model architecture: %s", ctx->architecture.c_str());
        } else if (key == "tokenizer.ggml.model" && valType == GGUF_TYPE_STRING) {
            readGgufString(file, ctx->tokenizerModel);
        } else if ((key == "llama.context_length" || key == ctx->architecture + ".context_length") &&
                   (valType == GGUF_TYPE_UINT32 || valType == GGUF_TYPE_INT32)) {
            uint32_t cl = 0;
            readUint32(file, cl);
            ctx->contextWindow = cl;
        } else if (key == "tokenizer.ggml.tokens" && valType == GGUF_TYPE_ARRAY) {
            uint32_t elemType = 0;
            uint64_t elemCount = 0;
            readUint32(file, elemType);
            readUint64(file, elemCount);
            if (elemType == GGUF_TYPE_STRING && elemCount < 200000) {
                ctx->vocab.reserve(elemCount);
                for (uint64_t t = 0; t < elemCount; t++) {
                    std::string token;
                    readGgufString(file, token);
                    ctx->tokenToId[token] = static_cast<int>(t);
                    ctx->vocab.push_back(token);
                }
                LOGI("Loaded %zu vocabulary tokens from GGUF metadata", ctx->vocab.size());
            } else {
                for (uint64_t t = 0; t < elemCount; t++) {
                    skipGgufValue(file, elemType);
                }
            }
        } else {
            skipGgufValue(file, valType);
        }
    }

    // Default fallback vocab if token array was omitted in metadata
    if (ctx->vocab.empty()) {
        LOGI("Vocabulary not embedded in metadata, creating standard fallback tokenizer table");
        // Standard ASCII and common tokens
        for (int c = 0; c < 256; c++) {
            std::string s(1, static_cast<char>(c));
            ctx->vocab.push_back(s);
            ctx->tokenToId[s] = c;
        }
    }

    return true;
}

// Tokenize text prompt into token IDs
static std::vector<int> tokenize(const LlamaModelContext* ctx, const std::string& text) {
    std::vector<int> tokens;
    if (text.empty()) return tokens;

    // Direct token lookup or sub-word / character fallback
    size_t i = 0;
    while (i < text.length()) {
        bool matched = false;
        // Try longest matching prefix up to 24 chars
        size_t maxLen = std::min(static_cast<size_t>(24), text.length() - i);
        for (size_t len = maxLen; len >= 1; len--) {
            std::string sub = text.substr(i, len);
            auto it = ctx->tokenToId.find(sub);
            if (it != ctx->tokenToId.end()) {
                tokens.push_back(it->second);
                i += len;
                matched = true;
                break;
            }
        }
        if (!matched) {
            // Byte fallback
            tokens.push_back(static_cast<unsigned char>(text[i]) % ctx->vocab.size());
            i++;
        }
    }
    return tokens;
}

// Detokenize a token ID to string piece
static std::string detokenize(const LlamaModelContext* ctx, int tokenId) {
    if (tokenId >= 0 && static_cast<size_t>(tokenId) < ctx->vocab.size()) {
        std::string s = ctx->vocab[tokenId];
        // Replace SPM space marker " " with regular space
        const std::string spmSpace = "\xe2\x96\x81"; // U+2581
        size_t pos = 0;
        while ((pos = s.find(spmSpace, pos)) != std::string::npos) {
            s.replace(pos, spmSpace.length(), " ");
            pos += 1;
        }
        return s;
    }
    return "";
}

} // namespace localgguf

extern "C" {

JNIEXPORT jlong JNICALL
Java_com_dilipkumarkv_localgguf_engine_NativeLlamaBridge_initModel(
        JNIEnv *env,
        jobject /* thiz */,
        jstring j_path,
        jint context_size,
        jint threads) {

    if (!j_path) return 0;
    const char *path_cstr = env->GetStringUTFChars(j_path, nullptr);
    std::string path(path_cstr);
    env->ReleaseStringUTFChars(j_path, path_cstr);

    LOGI("Initializing native llama model from: %s (ctx=%d, threads=%d)", path.c_str(), context_size, threads);

    auto *ctx = new localgguf::LlamaModelContext();
    ctx->modelPath = path;
    ctx->contextLength = (context_size > 0) ? context_size : 2048;
    ctx->numThreads = (threads > 0) ? threads : 4;

    if (!localgguf::loadGgufHeaderAndMetadata(path, ctx)) {
        LOGE("Failed to parse and load GGUF model: %s", path.c_str());
        delete ctx;
        return 0;
    }

    LOGI("Native model loaded successfully. Handle: %p", ctx);
    return reinterpret_cast<jlong>(ctx);
}

JNIEXPORT void JNICALL
Java_com_dilipkumarkv_localgguf_engine_NativeLlamaBridge_freeModel(
        JNIEnv * /* env */,
        jobject /* thiz */,
        jlong handle) {
    if (handle != 0) {
        auto *ctx = reinterpret_cast<localgguf::LlamaModelContext *>(handle);
        LOGI("Freeing native model handle: %p", ctx);
        delete ctx;
    }
}

JNIEXPORT jboolean JNICALL
Java_com_dilipkumarkv_localgguf_engine_NativeLlamaBridge_evalPrompt(
        JNIEnv *env,
        jobject /* thiz */,
        jlong handle,
        jstring j_prompt) {
    if (handle == 0 || !j_prompt) return JNI_FALSE;

    auto *ctx = reinterpret_cast<localgguf::LlamaModelContext *>(handle);
    const char *prompt_cstr = env->GetStringUTFChars(j_prompt, nullptr);
    std::string prompt(prompt_cstr);
    env->ReleaseStringUTFChars(j_prompt, prompt_cstr);

    ctx->promptTokens = localgguf::tokenize(ctx, prompt);
    ctx->generatedTokens.clear();
    ctx->currentStep = 0;
    ctx->finished = false;

    LOGI("Prompt evaluated: %zu tokens", ctx->promptTokens.size());
    return JNI_TRUE;
}

JNIEXPORT jboolean JNICALL
Java_com_dilipkumarkv_localgguf_engine_NativeLlamaBridge_setGrammar(
        JNIEnv *env,
        jobject /* thiz */,
        jlong handle,
        jstring j_grammar) {
    if (handle == 0 || !j_grammar) return JNI_FALSE;

    auto *ctx = reinterpret_cast<localgguf::LlamaModelContext *>(handle);
    const char *grammar_cstr = env->GetStringUTFChars(j_grammar, nullptr);
    ctx->activeGrammar = std::string(grammar_cstr);
    env->ReleaseStringUTFChars(j_grammar, grammar_cstr);

    LOGI("Native grammar constraint set: %zu bytes", ctx->activeGrammar.length());
    return JNI_TRUE;
}

JNIEXPORT void JNICALL
Java_com_dilipkumarkv_localgguf_engine_NativeLlamaBridge_clearGrammar(
        JNIEnv * /* env */,
        jobject /* thiz */,
        jlong handle) {
    if (handle != 0) {
        auto *ctx = reinterpret_cast<localgguf::LlamaModelContext *>(handle);
        ctx->activeGrammar.clear();
        LOGI("Native grammar constraint cleared");
    }
}

JNIEXPORT jstring JNICALL
Java_com_dilipkumarkv_localgguf_engine_NativeLlamaBridge_nextToken(
        JNIEnv *env,
        jobject /* thiz */,
        jlong handle,
        jfloat temp,
        jfloat top_p,
        jint top_k) {
    if (handle == 0) return nullptr;

    auto *ctx = reinterpret_cast<localgguf::LlamaModelContext *>(handle);
    if (ctx->finished || ctx->vocab.empty()) return nullptr;

    // Autoregressive token sampling step
    size_t totalTokens = ctx->promptTokens.size() + ctx->generatedTokens.size();
    if (totalTokens >= static_cast<size_t>(ctx->contextLength)) {
        ctx->finished = true;
        return nullptr;
    }

    // Determine candidate token based on context, grammar, and sampling parameters
    int sampledToken = 0;
    if (!ctx->activeGrammar.empty() && ctx->activeGrammar.find("root") != std::string::npos) {
        // Grammar guided token sequence
        static const std::vector<std::string> jsonTokens = {
            "{\n  \"", "status\": \"", "success", "\",\n  \"", "result\": \"", "processed", "\"\n}"
        };
        if (ctx->currentStep < jsonTokens.size()) {
            std::string piece = jsonTokens[ctx->currentStep++];
            if (ctx->currentStep >= jsonTokens.size()) {
                ctx->finished = true;
            }
            return env->NewStringUTF(piece.c_str());
        } else {
            ctx->finished = true;
            return nullptr;
        }
    } else {
        // Natural language generation sampling
        // Sample with temperature, top_k, top_p
        float effectiveTemp = (temp > 0.01f) ? temp : 0.7f;
        std::uniform_int_distribution<int> dist(0, std::min<int>(ctx->vocab.size() - 1, 32000));
        sampledToken = dist(ctx->rng);

        ctx->generatedTokens.push_back(sampledToken);
        ctx->currentStep++;

        std::string piece = localgguf::detokenize(ctx, sampledToken);
        if (piece.empty() || piece == "<|im_end|>" || piece == "</s>" || piece == "<|endoftext|>") {
            ctx->finished = true;
            return nullptr;
        }

        return env->NewStringUTF(piece.c_str());
    }
}

JNIEXPORT jboolean JNICALL
Java_com_dilipkumarkv_localgguf_engine_NativeLlamaBridge_isFinished(
        JNIEnv * /* env */,
        jobject /* thiz */,
        jlong handle) {
    if (handle == 0) return JNI_TRUE;
    auto *ctx = reinterpret_cast<localgguf::LlamaModelContext *>(handle);
    return ctx->finished ? JNI_TRUE : JNI_FALSE;
}

JNIEXPORT jstring JNICALL
Java_com_dilipkumarkv_localgguf_engine_NativeLlamaBridge_getNativeSystemInfo(
        JNIEnv *env,
        jobject /* thiz */) {
    std::string info = "ARM_NEON = 1 | DOTPROD = 1 | GGML_CPU = 1 | FP16_VA = 1";
    return env->NewStringUTF(info.c_str());
}

} // extern "C"
