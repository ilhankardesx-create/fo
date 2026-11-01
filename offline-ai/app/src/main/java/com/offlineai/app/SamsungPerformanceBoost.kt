package com.offlineai.app

import android.content.Context
import android.os.Build
import android.os.Handler
import android.os.Looper
import dalvik.system.PathClassLoader
import java.io.File
import java.lang.reflect.Method

/**
 * Samsung Galaxy cihazlarda yalnızca üretim sırasında GPU ve RAM bus için
 * kısa süreli performans isteği verir. Desteklenmeyen cihazlarda tamamen no-op'tur.
 * Kullanıcı arayüzünde herhangi bir seçenek veya durum metni göstermez.
 */
class SamsungPerformanceBoost(context: Context) {

    companion object {
        private const val SDK_JAR = "/system/framework/perfsdk.jar"
        private const val SPERF_CLASS = "com.samsung.sdk.sperf.SPerf"
        private const val PERFORMANCE_MANAGER_CLASS = "com.samsung.sdk.sperf.PerformanceManager"
        private const val CUSTOM_PARAMS_CLASS = "com.samsung.sdk.sperf.CustomParams"

        // Samsung Galaxy Performance SDK 1.0.0 sabitleri.
        private const val TYPE_GPU_MIN = 2
        private const val TYPE_BUS_MIN = 4

        // 0..4 aralığında en yüksek performans seviyesi.
        private const val BOOST_LEVEL = 4
        private const val REQUEST_TIMEOUT_MS = 1000
        private const val RENEW_MS = 500L
        private const val RETRY_MS = 2000L
    }

    private val appContext = context.applicationContext
    private val handler = Handler(Looper.getMainLooper())
    private val isSamsung = Build.MANUFACTURER.equals("samsung", ignoreCase = true)

    private var active = false
    private var permanentlyUnavailable = !isSamsung

    private var manager: Any? = null
    private var paramsClass: Class<*>? = null
    private var addMethod: Method? = null
    private var startMethod: Method? = null
    private var stopMethod: Method? = null

    private val renew = object : Runnable {
        override fun run() {
            if (!active || permanentlyUnavailable) return

            if (!bind()) {
                if (active && !permanentlyUnavailable) {
                    handler.postDelayed(this, RETRY_MS)
                }
                return
            }

            requestBoost()
            if (active) handler.postDelayed(this, RENEW_MS)
        }
    }

    fun start() {
        if (permanentlyUnavailable) return
        active = true
        handler.removeCallbacks(renew)
        handler.post(renew)
    }

    fun stop() {
        active = false
        handler.removeCallbacks(renew)
        val pm = manager ?: return
        runCatching { stopMethod?.invoke(pm) }
    }

    fun close() {
        stop()
    }

    private fun bind(): Boolean {
        if (manager != null) return true
        if (!isSamsung) {
            permanentlyUnavailable = true
            return false
        }

        val jar = File(SDK_JAR)
        if (!jar.isFile) {
            permanentlyUnavailable = true
            return false
        }

        return runCatching {
            val loader = PathClassLoader(SDK_JAR, appContext.classLoader)
            val sperf = Class.forName(SPERF_CLASS, true, loader)
            val pmClass = Class.forName(PERFORMANCE_MANAGER_CLASS, true, loader)
            val cpClass = Class.forName(CUSTOM_PARAMS_CLASS, true, loader)

            runCatching {
                sperf.getMethod("setDebugModeEnabled", java.lang.Boolean.TYPE)
                    .invoke(null, false)
            }

            val initialized = sperf.getMethod("initialize", Context::class.java)
                .invoke(null, appContext) as? Boolean ?: false
            if (!initialized) return@runCatching false

            val pm = pmClass.getMethod("getInstance").invoke(null) ?: return@runCatching false

            manager = pm
            paramsClass = cpClass
            addMethod = cpClass.getMethod(
                "add",
                Integer.TYPE,
                Integer.TYPE,
                Integer.TYPE
            )
            startMethod = pmClass.getMethod("start", cpClass)
            stopMethod = pmClass.getMethod("stop")
            true
        }.getOrElse {
            false
        }
    }

    private fun requestBoost(): Boolean {
        val pm = manager ?: return false
        val cp = paramsClass ?: return false
        val add = addMethod ?: return false
        val start = startMethod ?: return false

        return runCatching {
            val params = cp.getDeclaredConstructor().newInstance()
            val gpuResult = add.invoke(
                params,
                TYPE_GPU_MIN,
                BOOST_LEVEL,
                REQUEST_TIMEOUT_MS
            ) as? Int
            val busResult = add.invoke(
                params,
                TYPE_BUS_MIN,
                BOOST_LEVEL,
                REQUEST_TIMEOUT_MS
            ) as? Int
            val startResult = start.invoke(pm, params) as? Int

            (gpuResult == null || gpuResult == 0) &&
                (busResult == null || busResult == 0) &&
                (startResult == null || startResult == 0)
        }.getOrDefault(false)
    }
}
