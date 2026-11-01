package net.zash.clashpanel

import android.app.Application
import android.content.ContentValues
import android.content.Context
import android.os.Build
import android.os.Environment
import android.provider.MediaStore
import java.io.File
import java.io.PrintWriter
import java.io.StringWriter
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import net.zash.clashpanel.i18n.t

class MimiApp : Application() {
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

    private fun write(ctx: Context, thread: Thread, e: Throwable) {
        val sw = StringWriter(); e.printStackTrace(PrintWriter(sw))
        val now = Date()
        val pi = runCatching { ctx.packageManager.getPackageInfo(ctx.packageName, 0) }.getOrNull()
        val text = buildString {
            appendLine(t("crash_header"))
            appendLine(t("crash_time") + ": " + SimpleDateFormat("yyyy-MM-dd HH:mm:ss Z", Locale.US).format(now))
            appendLine(t("crash_app_ver") + ": ${pi?.versionName} (${if (Build.VERSION.SDK_INT >= 28) pi?.longVersionCode else @Suppress("DEPRECATION") pi?.versionCode})")
            appendLine(t("crash_device") + ": ${Build.MANUFACTURER} ${Build.MODEL} (${Build.DEVICE})")
            appendLine(t("crash_os") + ": Android ${Build.VERSION.RELEASE} / SDK ${Build.VERSION.SDK_INT}")
            appendLine("ABI: ${Build.SUPPORTED_ABIS.joinToString()}")
            appendLine(t("crash_kind") + ": " + t("crash_kind_jvm") + " (${e.javaClass.name})")
            appendLine(t("crash_thread") + ": ${thread.name}")
            appendLine()
            append(sw.toString())
        }
        val f = File(dir(ctx), "crash-" + SimpleDateFormat("yyyyMMdd-HHmmss", Locale.US).format(now) + ".txt")
        f.writeText(text)
        // Mirror a copy into the public Download/MimiPanel folder (1.4.15): when the app
        // itself will not open, the user can still grab the log with any file manager.
        // Best effort only — a mirror failure must never disturb the crash flow.
        runCatching { mirrorToDownloads(ctx, f.name, text) }
        // keep latest 20
        list(ctx).drop(20).forEach { it.delete() }
        ctx.getSharedPreferences("crash", Context.MODE_PRIVATE).edit().putBoolean("unseen", true).commit()
    }

    /**
     * Writes [text] as Download/MimiPanel/[name]. Scoped storage: on API 29+ a MediaStore
     * Downloads insert needs no permission; on API 26-28 the public Downloads directory is
     * written directly (WRITE_EXTERNAL_STORAGE, manifest-capped at API 28). Any failure
     * (permission denied, volume missing) propagates to the caller's runCatching.
     */
    private fun mirrorToDownloads(ctx: Context, name: String, text: String) {
        if (Build.VERSION.SDK_INT >= 29) {
            val values = ContentValues().apply {
                put(MediaStore.Downloads.DISPLAY_NAME, name)
                put(MediaStore.Downloads.MIME_TYPE, "text/plain")
                put(MediaStore.Downloads.RELATIVE_PATH, Environment.DIRECTORY_DOWNLOADS + "/MimiPanel")
            }
            val uri = ctx.contentResolver.insert(MediaStore.Downloads.EXTERNAL_CONTENT_URI, values)
                ?: error("MediaStore insert returned null")
            ctx.contentResolver.openOutputStream(uri, "wt")!!.use { it.write(text.toByteArray()) }
        } else {
            @Suppress("DEPRECATION")
            val pub = Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_DOWNLOADS)
            val folder = File(pub, "MimiPanel").apply { mkdirs() }
            File(folder, name).writeText(text)
        }
    }

    fun list(ctx: Context): List<File> = dir(ctx).listFiles()?.filter { it.name.endsWith(".txt") }?.sortedByDescending { it.name } ?: emptyList()

    fun hasUnseen(ctx: Context) = ctx.getSharedPreferences("crash", Context.MODE_PRIVATE).getBoolean("unseen", false) && list(ctx).isNotEmpty()
    fun markSeen(ctx: Context) = ctx.getSharedPreferences("crash", Context.MODE_PRIVATE).edit().putBoolean("unseen", false).apply()
    fun clear(ctx: Context) { list(ctx).forEach { it.delete() }; markSeen(ctx) }

    /** Writes text to a document the user picked in the system "Save as" picker (Storage Access Framework). */
    fun writeUri(ctx: Context, uri: android.net.Uri, text: String) {
        ctx.contentResolver.openOutputStream(uri, "wt")!!.use { it.write(text.toByteArray()) }
    }

    fun share(ctx: Context, text: String) {
        val i = android.content.Intent(android.content.Intent.ACTION_SEND).apply {
            type = "text/plain"; putExtra(android.content.Intent.EXTRA_TEXT, text)
            putExtra(android.content.Intent.EXTRA_SUBJECT, t("crash_share_title"))
        }
        ctx.startActivity(android.content.Intent.createChooser(i, t("share_crash_chooser")).addFlags(android.content.Intent.FLAG_ACTIVITY_NEW_TASK))
    }
}
