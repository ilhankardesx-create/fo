#include <jni.h>
#include <android/log.h>
#include <dlfcn.h>
#include <fcntl.h>
#include <unistd.h>
#include <cstdio>
#include <cstdlib>
#include <cstring>
#include <string>
#include <vector>

#define LOG_TAG "PocketDroidBridge"
#define LOGE(...) __android_log_print(ANDROID_LOG_ERROR, LOG_TAG, __VA_ARGS__)
#define LOGI(...) __android_log_print(ANDROID_LOG_INFO, LOG_TAG, __VA_ARGS__)

static void* g_qemu = nullptr;
static void* g_compat = nullptr;
static bool g_running = false;
static bool g_jni_ready = false;
static std::vector<void*> g_dep_handles;
static std::string g_storage_dir;
static std::string g_base_dir;
static int g_saved_stdout = -1;
static int g_saved_stderr = -1;

static std::string jstringToString(JNIEnv* env, jstring value) {
    if (!value) return {};
    const char* chars = env->GetStringUTFChars(value, nullptr);
    std::string out = chars ? chars : "";
    if (chars) env->ReleaseStringUTFChars(value, chars);
    return out;
}

static jstring result(JNIEnv* env, const std::string& text) {
    return env->NewStringUTF(text.c_str());
}

static std::string parentDir(const std::string& path) {
    size_t pos = path.find_last_of('/');
    if (pos == std::string::npos) return ".";
    return path.substr(0, pos);
}

static std::string stagePath(const std::string& logFile) {
    return parentDir(logFile) + "/crash-stage.txt";
}

static void writeStage(const std::string& logFile, const char* stage) {
    FILE* f = fopen(stagePath(logFile).c_str(), "w");
    if (!f) return;
    fprintf(f, "%s\n", stage);
    fflush(f);
    fsync(fileno(f));
    fclose(f);
}

static void clearStage(const std::string& logFile) {
    unlink(stagePath(logFile).c_str());
}

static void restoreStdStreams() {
    if (g_saved_stdout >= 0) {
        dup2(g_saved_stdout, STDOUT_FILENO);
        close(g_saved_stdout);
        g_saved_stdout = -1;
    }
    if (g_saved_stderr >= 0) {
        dup2(g_saved_stderr, STDERR_FILENO);
        close(g_saved_stderr);
        g_saved_stderr = -1;
    }
}

static bool redirectStdStreams(const std::string& logFile) {
    int fd = open(logFile.c_str(), O_CREAT | O_WRONLY | O_TRUNC, 0600);
    if (fd < 0) return false;
    g_saved_stdout = dup(STDOUT_FILENO);
    g_saved_stderr = dup(STDERR_FILENO);
    dup2(fd, STDOUT_FILENO);
    dup2(fd, STDERR_FILENO);
    if (fd > STDERR_FILENO) close(fd);
    setvbuf(stdout, nullptr, _IONBF, 0);
    setvbuf(stderr, nullptr, _IONBF, 0);
    return true;
}

static void* loadGlobal(const std::string& dir, const char* name, bool required) {
    std::string path = dir + "/" + name;
    dlerror();
    void* h = dlopen(path.c_str(), RTLD_NOW | RTLD_GLOBAL);
    if (!h) {
        const char* e = dlerror();
        if (required) {
            fprintf(stderr, "[PocketDroid] Gerekli kütüphane yüklenemedi %s: %s\n",
                    name, e ? e : "bilinmeyen hata");
        } else {
            fprintf(stderr, "[PocketDroid] İsteğe bağlı kütüphane atlandı %s: %s\n",
                    name, e ? e : "bulunamadı");
        }
        return nullptr;
    }
    g_dep_handles.push_back(h);
    fprintf(stderr, "[PocketDroid] ✓ %s\n", name);
    return h;
}

static bool loadRuntimeDependencies(const std::string& nativeDir) {
    if (!g_dep_handles.empty()) return g_compat != nullptr;
    g_compat = loadGlobal(nativeDir, "libcompat-limbo.so", true);
    if (!g_compat) return false;
    if (!loadGlobal(nativeDir, "libcompat-musl.so", true)) return false;
    if (!loadGlobal(nativeDir, "libglib-2.0.so", true)) return false;
    if (!loadGlobal(nativeDir, "libpixman-1.so", true)) return false;
    loadGlobal(nativeDir, "libcompat-SDL2-addons.so", false);
    loadGlobal(nativeDir, "libSDL2.so", false);
    loadGlobal(nativeDir, "libcompat-SDL2-ext.so", false);
    return true;
}

static bool prepareAndroidRuntime(JNIEnv* env, jobject thiz,
                                  const std::string& nativeDir,
                                  const std::string& storageDir,
                                  const std::string& baseDir) {
    if (!loadRuntimeDependencies(nativeDir)) return false;

    if (!g_jni_ready) {
        using set_jni_t = void (*)(JNIEnv*, jobject, jclass, const char*, const char*);
        dlerror();
        auto set_jni = reinterpret_cast<set_jni_t>(dlsym(g_compat, "set_jni"));
        const char* e = dlerror();
        if (e || !set_jni) {
            fprintf(stderr, "[PocketDroid] set_jni bulunamadı: %s\n", e ? e : "bilinmeyen hata");
            return false;
        }
        g_storage_dir = storageDir;
        g_base_dir = baseDir;
        jclass cls = env->GetObjectClass(thiz);
        set_jni(env, thiz, cls, g_storage_dir.c_str(), g_base_dir.c_str());
        env->DeleteLocalRef(cls);
        g_jni_ready = true;
        fprintf(stderr, "[PocketDroid] ✓ Android JNI uyumluluk katmanı hazır\n");
    }
    return true;
}

extern "C" JNIEXPORT jstring JNICALL
Java_com_pocketdroid_vm_QemuBridge_nativeProbeInternal(
        JNIEnv* env, jobject,
        jstring qemuLibPath,
        jstring logPath) {

    const std::string libPath = jstringToString(env, qemuLibPath);
    const std::string logFile = jstringToString(env, logPath);
    const std::string nativeDir = parentDir(libPath);

    redirectStdStreams(logFile);
    writeStage(logFile, "probe-runtime-dependencies");
    fprintf(stderr, "[PocketDroid] Güvenli motor testi başladı\n");
    fprintf(stderr, "[PocketDroid] Native dizin: %s\n", nativeDir.c_str());

    if (!loadRuntimeDependencies(nativeDir)) {
        std::string msg = "PROBE_FAIL: runtime bağımlılıkları yüklenemedi";
        fprintf(stderr, "[PocketDroid] %s\n", msg.c_str());
        restoreStdStreams();
        return result(env, msg);
    }

    writeStage(logFile, "probe-qemu-dlopen");
    dlerror();
    void* probe = dlopen(libPath.c_str(), RTLD_NOW | RTLD_GLOBAL);
    if (!probe) {
        const char* e = dlerror();
        std::string msg = "PROBE_FAIL: QEMU dlopen: ";
        msg += e ? e : "bilinmeyen hata";
        fprintf(stderr, "[PocketDroid] %s\n", msg.c_str());
        restoreStdStreams();
        return result(env, msg);
    }
    fprintf(stderr, "[PocketDroid] ✓ libqemu-system-aarch64.so açıldı\n");

    writeStage(logFile, "probe-qemu-symbols");
    dlerror();
    void* init = dlsym(probe, "qemu_init");
    const char* initErr = dlerror();
    if (!init || initErr) {
        dlerror();
        init = dlsym(probe, "main");
        const char* mainErr = dlerror();
        if (!init || mainErr) {
            std::string msg = "PROBE_FAIL: qemu_init/main bulunamadı";
            fprintf(stderr, "[PocketDroid] %s\n", msg.c_str());
            dlclose(probe);
            restoreStdStreams();
            return result(env, msg);
        }
        fprintf(stderr, "[PocketDroid] ✓ legacy main sembolü bulundu\n");
    } else {
        fprintf(stderr, "[PocketDroid] ✓ qemu_init sembolü bulundu\n");
        dlerror();
        void* loop = dlsym(probe, "qemu_main_loop");
        const char* loopErr = dlerror();
        dlerror();
        void* cleanup = dlsym(probe, "qemu_cleanup");
        const char* cleanupErr = dlerror();
        if (!loop || loopErr || !cleanup || cleanupErr) {
            std::string msg = "PROBE_FAIL: qemu_main_loop/qemu_cleanup eksik";
            fprintf(stderr, "[PocketDroid] %s\n", msg.c_str());
            dlclose(probe);
            restoreStdStreams();
            return result(env, msg);
        }
        fprintf(stderr, "[PocketDroid] ✓ qemu_main_loop + qemu_cleanup bulundu\n");
    }

    dlerror();
    void* shutdown = dlsym(probe, "qemu_system_shutdown_request");
    const char* shutdownErr = dlerror();
    if (shutdown && !shutdownErr) {
        fprintf(stderr, "[PocketDroid] ✓ shutdown sembolü bulundu\n");
    } else {
        fprintf(stderr, "[PocketDroid] ! shutdown sembolü yok (boot testi için kritik değil)\n");
    }

    dlclose(probe);
    clearStage(logFile);
    fprintf(stderr, "[PocketDroid] MOTOR TESTİ BAŞARILI\n");
    fflush(stderr);
    restoreStdStreams();
    return result(env, "PROBE_OK");
}

extern "C" JNIEXPORT jstring JNICALL
Java_com_pocketdroid_vm_QemuBridge_nativeStartInternal(
        JNIEnv* env, jobject thiz,
        jstring qemuLibPath,
        jstring logPath,
        jstring storageDir,
        jstring baseDir,
        jobjectArray javaArgs) {

    if (g_running) return result(env, "QEMU zaten çalışıyor");

    const std::string libPath = jstringToString(env, qemuLibPath);
    const std::string logFile = jstringToString(env, logPath);
    const std::string storage = jstringToString(env, storageDir);
    const std::string base = jstringToString(env, baseDir);
    const std::string nativeDir = parentDir(libPath);

    redirectStdStreams(logFile);
    writeStage(logFile, "runtime-prepare");
    fprintf(stderr, "[PocketDroid] Temiz motor başlatılıyor\n");
    fprintf(stderr, "[PocketDroid] Native dizin: %s\n", nativeDir.c_str());

    if (!prepareAndroidRuntime(env, thiz, nativeDir, storage, base)) {
        std::string msg = "Android QEMU uyumluluk katmanı hazırlanamadı";
        fprintf(stderr, "[PocketDroid] %s\n", msg.c_str());
        restoreStdStreams();
        return result(env, msg);
    }
    writeStage(logFile, "runtime-ready");

    fprintf(stderr, "[PocketDroid] QEMU motoru yükleniyor: %s\n", libPath.c_str());
    writeStage(logFile, "qemu-dlopen");
    dlerror();
    g_qemu = dlopen(libPath.c_str(), RTLD_NOW | RTLD_GLOBAL);
    if (!g_qemu) {
        const char* e = dlerror();
        std::string msg = "QEMU kütüphanesi açılamadı: ";
        msg += e ? e : "bilinmeyen hata";
        fprintf(stderr, "[PocketDroid] %s\n", msg.c_str());
        restoreStdStreams();
        return result(env, msg);
    }
    writeStage(logFile, "qemu-loaded");

    jsize argcJava = env->GetArrayLength(javaArgs);
    int argc = static_cast<int>(argcJava);
    std::vector<std::string> strings;
    strings.reserve(argc);
    std::vector<char*> argv(argc + 1, nullptr);

    for (int i = 0; i < argc; ++i) {
        auto s = (jstring) env->GetObjectArrayElement(javaArgs, i);
        strings.push_back(jstringToString(env, s));
        env->DeleteLocalRef(s);
    }
    for (int i = 0; i < argc; ++i) argv[i] = const_cast<char*>(strings[i].c_str());
    argv[argc] = nullptr;

    using init_t = void (*)(int, char**, char**);
    using loop_t = void (*)();
    using cleanup_t = void (*)();

    dlerror();
    auto qemu_init = reinterpret_cast<init_t>(dlsym(g_qemu, "qemu_init"));
    const char* initErr = dlerror();

    g_running = true;
    std::string finalMessage = "QEMU kapandı";

    if (!initErr && qemu_init) {
        dlerror();
        auto qemu_main_loop = reinterpret_cast<loop_t>(dlsym(g_qemu, "qemu_main_loop"));
        const char* loopErr = dlerror();
        dlerror();
        auto qemu_cleanup = reinterpret_cast<cleanup_t>(dlsym(g_qemu, "qemu_cleanup"));
        const char* cleanupErr = dlerror();

        if (loopErr || !qemu_main_loop || cleanupErr || !qemu_cleanup) {
            finalMessage = "QEMU ana döngü sembolleri bulunamadı";
            fprintf(stderr, "[PocketDroid] %s\n", finalMessage.c_str());
        } else {
            writeStage(logFile, "qemu-init");
            fprintf(stderr, "[PocketDroid] qemu_init() çağrılıyor\n");
            qemu_init(argc, argv.data(), nullptr);
            writeStage(logFile, "qemu-main-loop");
            fprintf(stderr, "[PocketDroid] qemu_main_loop() başladı\n");
            qemu_main_loop();
            writeStage(logFile, "qemu-cleanup");
            fprintf(stderr, "[PocketDroid] QEMU ana döngüden çıktı\n");
            qemu_cleanup();
        }
    } else {
        dlerror();
        auto qemu_main = reinterpret_cast<init_t>(dlsym(g_qemu, "main"));
        const char* mainErr = dlerror();
        if (mainErr || !qemu_main) {
            finalMessage = "QEMU giriş sembolü bulunamadı: ";
            finalMessage += mainErr ? mainErr : "qemu_init/main yok";
            fprintf(stderr, "[PocketDroid] %s\n", finalMessage.c_str());
        } else {
            writeStage(logFile, "qemu-legacy-main");
            fprintf(stderr, "[PocketDroid] legacy main() çağrılıyor\n");
            qemu_main(argc, argv.data(), nullptr);
        }
    }

    g_running = false;
    if (g_qemu) {
        dlclose(g_qemu);
        g_qemu = nullptr;
    }
    clearStage(logFile);
    fflush(stdout);
    fflush(stderr);
    restoreStdStreams();
    return result(env, finalMessage);
}

extern "C" JNIEXPORT jstring JNICALL
Java_com_pocketdroid_vm_QemuBridge_nativeStopInternal(JNIEnv* env, jobject) {
    if (!g_qemu || !g_running) return result(env, "QEMU çalışmıyor");

    using stop_t = void (*)(int);
    dlerror();
    auto shutdown = reinterpret_cast<stop_t>(dlsym(g_qemu, "qemu_system_shutdown_request"));
    const char* e = dlerror();
    if (e || !shutdown) return result(env, "QEMU durdurma sembolü bulunamadı");

    shutdown(3);
    return result(env, "Kapatma isteği gönderildi");
}
