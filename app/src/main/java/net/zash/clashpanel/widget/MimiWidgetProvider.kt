package net.zash.clashpanel.widget

import android.app.PendingIntent
import android.appwidget.AppWidgetManager
import android.appwidget.AppWidgetProvider
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.graphics.Color
import android.view.View
import android.widget.RemoteViews
import net.zash.clashpanel.MainActivity
import net.zash.clashpanel.R
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

/**
 * Home-screen widget (1.4.0): status / live speeds / connections / egress at a glance, and directly
 * operable — three mode tap zones (rule / global / direct) and a node-cycle button. Taps arrive as
 * broadcasts; the provider calls the backend itself (see [WidgetActions]) via goAsync, patches the
 * snapshot and re-renders. Data otherwise comes from the snapshot MainViewModel writes
 * (see [WidgetSnapshot]); when the app has never run it shows an "open Mimi Panel" placeholder.
 * Tapping the card opens the app; the refresh button re-reads the snapshot and re-renders.
 */
class MimiWidgetProvider : AppWidgetProvider() {

    override fun onUpdate(context: Context, mgr: AppWidgetManager, ids: IntArray) {
        ids.forEach { id -> mgr.updateAppWidget(id, buildViews(context)) }
    }

    override fun onReceive(context: Context, intent: Intent) {
        super.onReceive(context, intent)
        when (intent.action) {
            ACTION_REFRESH -> renderAll(context)
            ACTION_MODE_RULE -> switchMode(context, "rule")
            ACTION_MODE_GLOBAL -> switchMode(context, "global")
            ACTION_MODE_DIRECT -> switchMode(context, "direct")
            ACTION_CYCLE_NODE -> cycleNode(context)
        }
    }

    private fun switchMode(context: Context, mode: String) {
        val pending = goAsync()
        Thread {
            try {
                val ok = WidgetActions.setMode(context, mode)
                if (ok) WidgetSnapshot.patch(context, modeKey = mode, msg = "")
                else WidgetSnapshot.patch(context, msg = context.getString(R.string.widget_failed))
                renderAll(context)
            } finally { pending.finish() }
        }.start()
    }

    private fun cycleNode(context: Context) {
        val pending = goAsync()
        Thread {
            try {
                val group = WidgetSnapshot.read(context).groupName
                val next = WidgetActions.cycleNode(context, group)
                if (next != null) WidgetSnapshot.patch(context, nodeText = next, msg = "")
                else WidgetSnapshot.patch(context, msg = context.getString(R.string.widget_failed))
                renderAll(context)
            } finally { pending.finish() }
        }.start()
    }

    companion object {
        const val ACTION_REFRESH = "net.zash.clashpanel.WIDGET_REFRESH"
        const val ACTION_MODE_RULE = "net.zash.clashpanel.WIDGET_MODE_RULE"
        const val ACTION_MODE_GLOBAL = "net.zash.clashpanel.WIDGET_MODE_GLOBAL"
        const val ACTION_MODE_DIRECT = "net.zash.clashpanel.WIDGET_MODE_DIRECT"
        const val ACTION_CYCLE_NODE = "net.zash.clashpanel.WIDGET_CYCLE_NODE"

        private fun renderAll(context: Context) {
            val mgr = AppWidgetManager.getInstance(context)
            val ids = mgr.getAppWidgetIds(ComponentName(context, MimiWidgetProvider::class.java))
            ids.forEach { id -> mgr.updateAppWidget(id, buildViews(context)) }
        }

        private fun broadcastPending(context: Context, code: Int, action: String): PendingIntent =
            PendingIntent.getBroadcast(
                context, code,
                Intent(context, MimiWidgetProvider::class.java).setAction(action),
                PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
            )

        fun buildViews(context: Context): RemoteViews {
            val d = WidgetSnapshot.read(context)
            val v = RemoteViews(context.packageName, R.layout.widget_mimi)
            v.setInt(R.id.widget_root, "setBackgroundResource", if (d.dark) R.drawable.widget_bg_dark else R.drawable.widget_bg)

            val openIntent = PendingIntent.getActivity(
                context, 0,
                Intent(context, MainActivity::class.java).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK),
                PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
            )
            v.setOnClickPendingIntent(R.id.widget_root, openIntent)
            v.setOnClickPendingIntent(R.id.widget_refresh, broadcastPending(context, 1, ACTION_REFRESH))
            v.setOnClickPendingIntent(R.id.widget_mode_rule, broadcastPending(context, 2, ACTION_MODE_RULE))
            v.setOnClickPendingIntent(R.id.widget_mode_global, broadcastPending(context, 3, ACTION_MODE_GLOBAL))
            v.setOnClickPendingIntent(R.id.widget_mode_direct, broadcastPending(context, 4, ACTION_MODE_DIRECT))
            v.setOnClickPendingIntent(R.id.widget_cycle, broadcastPending(context, 5, ACTION_CYCLE_NODE))
            v.setOnClickPendingIntent(R.id.widget_node, broadcastPending(context, 5, ACTION_CYCLE_NODE))

            if (!d.hasData) {
                v.setTextViewText(R.id.widget_status, context.getString(R.string.widget_open_hint))
                v.setTextColor(R.id.widget_dot, Color.GRAY)
                v.setTextViewText(R.id.widget_speed_down, "—")
                v.setTextViewText(R.id.widget_speed_up, "—")
                v.setTextViewText(R.id.widget_meta, "")
                v.setTextViewText(R.id.widget_node, "—")
                v.setTextViewText(R.id.widget_msg, "")
                v.setTextViewText(R.id.widget_updated, "")
                return v
            }

            v.setTextColor(R.id.widget_dot, if (d.connected) Color.parseColor("#69F0AE") else Color.parseColor("#FF8A80"))
            v.setTextViewText(
                R.id.widget_status,
                listOf(
                    context.getString(if (d.connected) R.string.widget_connected else R.string.widget_disconnected),
                    d.backendLabel,
                ).filter { it.isNotBlank() }.joinToString(" · "),
            )
            v.setTextViewText(R.id.widget_speed_down, "↓ " + fmtWidgetBytes(d.downSpeed) + "/s")
            v.setTextViewText(R.id.widget_speed_up, "↑ " + fmtWidgetBytes(d.upSpeed) + "/s")
            val meta = mutableListOf<String>()
            meta += context.getString(R.string.widget_conns, d.connCount)
            meta += "↓ " + fmtWidgetBytes(d.totalDown) + " · ↑ " + fmtWidgetBytes(d.totalUp)
            if (d.egressText.isNotBlank()) meta += "🌐 " + d.egressText + if (d.egressIp.isNotBlank()) " " + d.egressIp else ""
            v.setTextViewText(R.id.widget_meta, meta.joinToString("   "))

            // mode buttons: current mode highlighted
            modeButton(v, R.id.widget_mode_rule, d.modeKey == "rule")
            modeButton(v, R.id.widget_mode_global, d.modeKey == "global")
            modeButton(v, R.id.widget_mode_direct, d.modeKey == "direct")
            v.setTextViewText(R.id.widget_mode_rule, context.getString(R.string.widget_mode_rule))
            v.setTextViewText(R.id.widget_mode_global, context.getString(R.string.widget_mode_global))
            v.setTextViewText(R.id.widget_mode_direct, context.getString(R.string.widget_mode_direct))

            v.setTextViewText(R.id.widget_node, d.nodeText.ifBlank { "—" })
            v.setTextViewText(R.id.widget_cycle, context.getString(R.string.widget_switch))
            v.setTextViewText(R.id.widget_msg, d.msg)
            v.setViewVisibility(R.id.widget_msg, if (d.msg.isBlank()) View.GONE else View.VISIBLE)
            v.setTextViewText(
                R.id.widget_updated,
                if (d.updatedAt > 0) SimpleDateFormat("HH:mm:ss", Locale.getDefault()).format(Date(d.updatedAt)) else "",
            )
            v.setViewVisibility(R.id.widget_refresh, View.VISIBLE)
            return v
        }

        private fun modeButton(v: RemoteViews, id: Int, active: Boolean) {
            v.setInt(id, "setBackgroundResource", if (active) R.drawable.widget_btn_active else R.drawable.widget_btn)
            v.setTextColor(id, if (active) Color.parseColor("#1A237E") else Color.parseColor("#C9D2F5"))
        }
    }
}
