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
static bool g_running = false;

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

extern "C" JNIEXPORT jstring JNICALL
Java_com_pocketdroid_vm_QemuBridge_nativeStart(
        JNIEnv* env, jclass,
        jstring qemuLibPath,
        jstring logPath,
        jobjectArray javaArgs) {

    if (g_running) return result(env, "QEMU zaten çalışıyor");

    const std::string libPath = jstringToString(env, qemuLibPath);
    const std::string logFile = jstringToString(env, logPath);

    int fd = open(logFile.c_str(), O_CREAT | O_WRONLY | O_APPEND, 0600);
    if (fd >= 0) {
        dup2(fd, STDOUT_FILENO);
        dup2(fd, STDERR_FILENO);
        if (fd > STDERR_FILENO) close(fd);
        setvbuf(stdout, nullptr, _IONBF, 0);
        setvbuf(stderr, nullptr, _IONBF, 0);
    }

    fprintf(stderr, "[PocketDroid] QEMU motoru yükleniyor: %s\n", libPath.c_str());

    dlerror();
    g_qemu = dlopen(libPath.c_str(), RTLD_NOW | RTLD_GLOBAL);
    if (!g_qemu) {
        const char* e = dlerror();
        std::string msg = "QEMU kütüphanesi açılamadı: ";
        msg += e ? e : "bilinmeyen hata";
        fprintf(stderr, "[PocketDroid] %s\n", msg.c_str());
        return result(env, msg);
    }

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
    for (int i = 0; i < argc; ++i) {
        argv[i] = const_cast<char*>(strings[i].c_str());
    }
    argv[argc] = nullptr;

    using init_t = void (*)(int, char**, char**);
    using loop_t = void (*)();
    using cleanup_t = void (*)();

    dlerror();
    auto qemu_init = reinterpret_cast<init_t>(dlsym(g_qemu, "qemu_init"));
    const char* initErr = dlerror();

    g_running = true;

    if (!initErr && qemu_init) {
        auto qemu_main_loop = reinterpret_cast<loop_t>(dlsym(g_qemu, "qemu_main_loop"));
        const char* loopErr = dlerror();
        auto qemu_cleanup = reinterpret_cast<cleanup_t>(dlsym(g_qemu, "qemu_cleanup"));
        const char* cleanupErr = dlerror();

        if (loopErr || !qemu_main_loop || cleanupErr || !qemu_cleanup) {
            std::string msg = "QEMU ana döngü sembolleri bulunamadı";
            fprintf(stderr, "[PocketDroid] %s\n", msg.c_str());
            g_running = false;
            dlclose(g_qemu);
            g_qemu = nullptr;
            return result(env, msg);
        }

        fprintf(stderr, "[PocketDroid] qemu_init() çağrılıyor\n");
        qemu_init(argc, argv.data(), nullptr);
        fprintf(stderr, "[PocketDroid] qemu_main_loop() başladı\n");
        qemu_main_loop();
        fprintf(stderr, "[PocketDroid] QEMU ana döngüden çıktı\n");
        qemu_cleanup();
    } else {
        dlerror();
        auto qemu_main = reinterpret_cast<init_t>(dlsym(g_qemu, "main"));
        const char* mainErr = dlerror();
        if (mainErr || !qemu_main) {
            std::string msg = "QEMU giriş sembolü bulunamadı: ";
            msg += mainErr ? mainErr : "qemu_init/main yok";
            fprintf(stderr, "[PocketDroid] %s\n", msg.c_str());
            g_running = false;
            dlclose(g_qemu);
            g_qemu = nullptr;
            return result(env, msg);
        }
        fprintf(stderr, "[PocketDroid] legacy main() çağrılıyor\n");
        qemu_main(argc, argv.data(), nullptr);
    }

    g_running = false;
    if (g_qemu) {
        dlclose(g_qemu);
        g_qemu = nullptr;
    }
    return result(env, "QEMU kapandı");
}

extern "C" JNIEXPORT jstring JNICALL
Java_com_pocketdroid_vm_QemuBridge_nativeStop(JNIEnv* env, jclass) {
    if (!g_qemu || !g_running) return result(env, "QEMU çalışmıyor");

    using stop_t = void (*)(int);
    dlerror();
    auto shutdown = reinterpret_cast<stop_t>(dlsym(g_qemu, "qemu_system_shutdown_request"));
    const char* e = dlerror();
    if (e || !shutdown) return result(env, "QEMU durdurma sembolü bulunamadı");

    shutdown(3);
    return result(env, "Kapatma isteği gönderildi");
}
