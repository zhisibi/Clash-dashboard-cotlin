package net.zash.clashpanel.data

import android.content.Context
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json

class Prefs(ctx: Context) {
    private val sp = ctx.getSharedPreferences("clash_panel", Context.MODE_PRIVATE)
    private val json = Json { ignoreUnknownKeys = true }

    var backends: List<Backend>
        get() = runCatching { json.decodeFromString<List<Backend>>(sp.getString("backends", "[]")!!) }.getOrDefault(emptyList())
        set(v) = sp.edit().putString("backends", json.encodeToString(v)).apply()

    var activeBackendId: String?
        get() = sp.getString("active_backend", null)
        set(v) = sp.edit().putString("active_backend", v).apply()

    var theme: String // system | light | dark
        get() = sp.getString("theme", "system")!!
        set(v) = sp.edit().putString("theme", v).apply()

    var testUrl: String
        get() = sp.getString("test_url", "https://www.gstatic.com/generate_204")!!
        set(v) = sp.edit().putString("test_url", v).apply()

    var testTimeout: Int
        get() = sp.getInt("test_timeout", 5000)
        set(v) = sp.edit().putInt("test_timeout", v).apply()

    var lowLatency: Int
        get() = sp.getInt("low_latency", 400)
        set(v) = sp.edit().putInt("low_latency", v).apply()

    var mediumLatency: Int
        get() = sp.getInt("medium_latency", 800)
        set(v) = sp.edit().putInt("medium_latency", v).apply()

    var groupTestUrlFirst: Boolean
        get() = sp.getBoolean("group_test_url", true)
        set(v) = sp.edit().putBoolean("group_test_url", v).apply()

    var hideUnavailable: Boolean
        get() = sp.getBoolean("hide_unavailable", false)
        set(v) = sp.edit().putBoolean("hide_unavailable", v).apply()

    var sortProxies: String // default | latency | name
        get() = sp.getString("sort_proxies", "default")!!
        set(v) = sp.edit().putString("sort_proxies", v).apply()

    var proxyCols: Int
        get() = sp.getInt("proxy_cols", 2)
        set(v) = sp.edit().putInt("proxy_cols", v).apply()

    var showGlobal: Boolean
        get() = sp.getBoolean("show_global", true)
        set(v) = sp.edit().putBoolean("show_global", v).apply()

    var showHiddenGroups: Boolean
        get() = sp.getBoolean("show_hidden", false)
        set(v) = sp.edit().putBoolean("show_hidden", v).apply()

    var logLevel: String
        get() = sp.getString("log_level", "info")!!
        set(v) = sp.edit().putString("log_level", v).apply()

    var logMax: Int
        get() = sp.getInt("log_max", 1000)
        set(v) = sp.edit().putInt("log_max", v).apply()

    var closedConnMax: Int
        get() = sp.getInt("closed_max", 500)
        set(v) = sp.edit().putInt("closed_max", v).apply()

    var connSort: String
        get() = sp.getString("conn_sort", "start")!!
        set(v) = sp.edit().putString("conn_sort", v).apply()

    var connSortDesc: Boolean
        get() = sp.getBoolean("conn_sort_desc", true)
        set(v) = sp.edit().putBoolean("conn_sort_desc", v).apply()

    var showRuleHits: Boolean
        get() = sp.getBoolean("rule_hits", true)
        set(v) = sp.edit().putBoolean("rule_hits", v).apply()

    var cardAlpha: Float
        get() = sp.getFloat("card_alpha", 0.82f)
        set(v) = sp.edit().putFloat("card_alpha", v).apply()

    var wallpaperBlur: Float
        get() = sp.getFloat("wp_blur", 0f)
        set(v) = sp.edit().putFloat("wp_blur", v).apply()

    var wallpaperDim: Float
        get() = sp.getFloat("wp_dim", 0.1f)
        set(v) = sp.edit().putFloat("wp_dim", v).apply()
}
