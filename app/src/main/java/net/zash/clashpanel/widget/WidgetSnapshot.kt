package net.zash.clashpanel.widget

import android.appwidget.AppWidgetManager
import android.content.ComponentName
import android.content.Context
import android.content.Intent

/**
 * Home-screen widget snapshot (1.4.0).
 *
 * The widget process cannot reach MainViewModel, so the view model throttles a small snapshot of the
 * overview into its own SharedPreferences file ("widget_snapshot", separate from the app settings file)
 * and pings AppWidgetManager; the provider re-reads the snapshot and renders RemoteViews from it.
 * Everything here is already on the device (backend label only — never the secret or address).
 */
data class WidgetData(
    val hasData: Boolean = false,
    val connected: Boolean = false,
    val backendLabel: String = "",
    val upSpeed: Long = 0,
    val downSpeed: Long = 0,
    val connCount: Int = 0,
    val totalUp: Long = 0,
    val totalDown: Long = 0,
    val dark: Boolean = false,
    val updatedAt: Long = 0,
    // Interactive widget (1.4.0): current proxy mode, main selector group and its resolved node,
    // plus a transient status line written by the provider after a tap action ("" = nothing to show).
    val modeKey: String = "",
    val groupName: String = "",
    val nodeText: String = "",
    val msg: String = "",
)

object WidgetSnapshot {
    private const val FILE = "widget_snapshot"

    fun write(ctx: Context, d: WidgetData) {
        ctx.getSharedPreferences(FILE, Context.MODE_PRIVATE).edit()
            .putBoolean("has", true)
            .putBoolean("connected", d.connected)
            .putString("label", d.backendLabel)
            .putLong("up", d.upSpeed)
            .putLong("down", d.downSpeed)
            .putInt("conns", d.connCount)
            .putLong("total_up", d.totalUp)
            .putLong("total_down", d.totalDown)
            .putBoolean("dark", d.dark)
            .putLong("updated", System.currentTimeMillis())
            .putString("mode", d.modeKey)
            .putString("group", d.groupName)
            .putString("node", d.nodeText)
            .putString("msg", d.msg)
            .apply()
    }

    /** Provider-side patch after a tap action: updates only the interactive fields + status line. */
    fun patch(ctx: Context, modeKey: String? = null, groupName: String? = null, nodeText: String? = null, msg: String? = null) {
        val e = ctx.getSharedPreferences(FILE, Context.MODE_PRIVATE).edit()
        if (modeKey != null) e.putString("mode", modeKey)
        if (groupName != null) e.putString("group", groupName)
        if (nodeText != null) e.putString("node", nodeText)
        if (msg != null) e.putString("msg", msg)
        e.putLong("updated", System.currentTimeMillis())
        e.apply()
    }

    fun read(ctx: Context): WidgetData {
        val sp = ctx.getSharedPreferences(FILE, Context.MODE_PRIVATE)
        return WidgetData(
            hasData = sp.getBoolean("has", false),
            connected = sp.getBoolean("connected", false),
            backendLabel = sp.getString("label", "") ?: "",
            upSpeed = sp.getLong("up", 0),
            downSpeed = sp.getLong("down", 0),
            connCount = sp.getInt("conns", 0),
            totalUp = sp.getLong("total_up", 0),
            totalDown = sp.getLong("total_down", 0),
            dark = sp.getBoolean("dark", false),
            updatedAt = sp.getLong("updated", 0),
            modeKey = sp.getString("mode", "") ?: "",
            groupName = sp.getString("group", "") ?: "",
            nodeText = sp.getString("node", "") ?: "",
            msg = sp.getString("msg", "") ?: "",
        )
    }

    /** Ask the provider to re-render every placed widget from the freshest snapshot. */
    fun requestUpdate(ctx: Context) {
        val mgr = AppWidgetManager.getInstance(ctx)
        val ids = mgr.getAppWidgetIds(ComponentName(ctx, MimiWidgetProvider::class.java))
        if (ids.isEmpty()) return
        ctx.sendBroadcast(
            Intent(ctx, MimiWidgetProvider::class.java)
                .setAction(AppWidgetManager.ACTION_APPWIDGET_UPDATE)
                .putExtra(AppWidgetManager.EXTRA_APPWIDGET_IDS, ids)
        )
    }
}

/** Same byte formatting as the Compose UI (ui/Common.kt fmtBytes), duplicated for RemoteViews. */
fun fmtWidgetBytes(b: Long): String {
    if (b < 1024) return "${b.coerceAtLeast(0)} B"
    val units = arrayOf("KB", "MB", "GB", "TB", "PB")
    var v = b / 1024.0; var i = 0
    while (v >= 1024 && i < units.size - 1) { v /= 1024; i++ }
    return String.format(java.util.Locale.US, if (v >= 100) "%.0f %s" else if (v >= 10) "%.1f %s" else "%.2f %s", v, units[i])
}
