package net.zash.clashpanel.data

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import okhttp3.OkHttpClient
import okhttp3.Request
import java.util.concurrent.TimeUnit

/** 1.2.7: About > Version checks the GitHub repo's latest release (same as HarmonyOS 1.2.7). */
object Updater {
    private const val LATEST_API = "https://api.github.com/repos/zhisibi/Clash-dashboard-cotlin/releases/latest"
    const val RELEASES_URL = "https://github.com/zhisibi/Clash-dashboard-cotlin/releases/latest"

    data class Release(val version: String, val url: String)

    private val client by lazy {
        OkHttpClient.Builder().connectTimeout(10, TimeUnit.SECONDS).readTimeout(15, TimeUnit.SECONDS).build()
    }

    /** Latest release, or throws on network / parse failure. */
    suspend fun latest(): Release = withContext(Dispatchers.IO) {
        val req = Request.Builder().url(LATEST_API)
            .header("Accept", "application/vnd.github+json").header("User-Agent", "MimiPanel").build()
        client.newCall(req).execute().use { r ->
            if (!r.isSuccessful) error("HTTP ${r.code}")
            val o = Json.parseToJsonElement(r.body?.string() ?: "").jsonObject
            val tag = o["tag_name"]?.jsonPrimitive?.content.orEmpty()
            if (tag.isBlank()) error("no release")
            Release(tag.removePrefix("v").removePrefix("V"), o["html_url"]?.jsonPrimitive?.content ?: RELEASES_URL)
        }
    }

    /** -1 a < b, 0 equal, 1 a > b; dotted numeric parts, leading v ignored. */
    fun cmp(a: String, b: String): Int {
        val pa = a.trimStart('v', 'V').split('.', '-', '+')
        val pb = b.trimStart('v', 'V').split('.', '-', '+')
        for (i in 0 until maxOf(pa.size, pb.size)) {
            val x = pa.getOrNull(i)?.toIntOrNull() ?: 0
            val y = pb.getOrNull(i)?.toIntOrNull() ?: 0
            if (x != y) return if (x < y) -1 else 1
        }
        return 0
    }
}
