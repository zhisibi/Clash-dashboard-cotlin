package net.zash.clashpanel

import android.app.Application
import android.content.ContentValues
import android.content.Context
import android.os.Build
import android.provider.MediaStore
import java.io.File
import java.io.PrintWriter
import java.io.StringWriter
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

class ClashPanelApp : Application() {
    override fun onCreate() {
        super.onCreate()
        CrashLog.install(this)
    }
}

object CrashLog {
    private fun dir(ctx: Context) = File(ctx.filesDir, "crash").apply { mkdirs() }

    fun install(ctx: Context) {
        val app = ctx.applicationContext
        val prev = Thread.getDefaultUncaughtExceptionHandler()
        Thread.setDefaultUncaughtExceptionHandler { t, e ->
            runCatching { write(app, t, e) }
            prev?.uncaughtException(t, e)
        }
    }

    private fun write(ctx: Context, t: Thread, e: Throwable) {
        val sw = StringWriter(); e.printStackTrace(PrintWriter(sw))
        val now = Date()
        val pi = runCatching { ctx.packageManager.getPackageInfo(ctx.packageName, 0) }.getOrNull()
        val text = buildString {
            appendLine("Clash 面板 闪退日志")
            appendLine("时间: " + SimpleDateFormat("yyyy-MM-dd HH:mm:ss Z", Locale.US).format(now))
            appendLine("应用版本: ${pi?.versionName} (${if (Build.VERSION.SDK_INT >= 28) pi?.longVersionCode else @Suppress("DEPRECATION") pi?.versionCode})")
            appendLine("设备: ${Build.MANUFACTURER} ${Build.MODEL} (${Build.DEVICE})")
            appendLine("系统: Android ${Build.VERSION.RELEASE} / SDK ${Build.VERSION.SDK_INT}")
            appendLine("ABI: ${Build.SUPPORTED_ABIS.joinToString()}")
            appendLine("线程: ${t.name}")
            appendLine()
            append(sw.toString())
        }
        val f = File(dir(ctx), "crash-" + SimpleDateFormat("yyyyMMdd-HHmmss", Locale.US).format(now) + ".txt")
        f.writeText(text)
        // keep latest 20
        list(ctx).drop(20).forEach { it.delete() }
        ctx.getSharedPreferences("crash", Context.MODE_PRIVATE).edit().putBoolean("unseen", true).commit()
    }

    fun list(ctx: Context): List<File> = dir(ctx).listFiles()?.filter { it.name.endsWith(".txt") }?.sortedByDescending { it.name } ?: emptyList()

    fun hasUnseen(ctx: Context) = ctx.getSharedPreferences("crash", Context.MODE_PRIVATE).getBoolean("unseen", false) && list(ctx).isNotEmpty()
    fun markSeen(ctx: Context) = ctx.getSharedPreferences("crash", Context.MODE_PRIVATE).edit().putBoolean("unseen", false).apply()
    fun clear(ctx: Context) { list(ctx).forEach { it.delete() }; markSeen(ctx) }

    /** Save to Downloads; returns file name. */
    fun export(ctx: Context, name: String, text: String): String {
        if (Build.VERSION.SDK_INT >= 29) {
            val cv = ContentValues().apply {
                put(MediaStore.Downloads.DISPLAY_NAME, name)
                put(MediaStore.Downloads.MIME_TYPE, "text/plain")
                put(MediaStore.Downloads.RELATIVE_PATH, android.os.Environment.DIRECTORY_DOWNLOADS)
            }
            val uri = ctx.contentResolver.insert(MediaStore.Downloads.EXTERNAL_CONTENT_URI, cv)!!
            ctx.contentResolver.openOutputStream(uri)!!.use { it.write(text.toByteArray()) }
            return "下载/$name"
        }
        val f = File(ctx.getExternalFilesDir(null), name); f.writeText(text)
        return f.path
    }

    fun share(ctx: Context, text: String) {
        val i = android.content.Intent(android.content.Intent.ACTION_SEND).apply {
            type = "text/plain"; putExtra(android.content.Intent.EXTRA_TEXT, text)
            putExtra(android.content.Intent.EXTRA_SUBJECT, "Clash 面板 闪退日志")
        }
        ctx.startActivity(android.content.Intent.createChooser(i, "分享闪退日志").addFlags(android.content.Intent.FLAG_ACTIVITY_NEW_TASK))
    }
}
