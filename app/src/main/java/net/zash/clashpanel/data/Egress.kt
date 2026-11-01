package net.zash.clashpanel.data

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.jsonObject
import okhttp3.OkHttpClient
import okhttp3.Request
import java.util.concurrent.TimeUnit

/**
 * Egress check (1.4.0): what the outside world sees as this device's exit.
 * This is the *device's* network egress — when the device's traffic goes through a proxy,
 * the result is that proxy's egress. Primary source ipwho.is; fallback Cloudflare trace.
 * Only ever runs on an explicit user tap; nothing about the configured backend is sent.
 */
data class EgressInfo(
    val ip: String,
    val country: String = "",
    val countryCode: String = "",
    val region: String = "",
    val city: String = "",
    val isp: String = "",
    val latencyMs: Long = 0,
    val checkedAt: Long = 0,
) {
    /** "Country · Region · City" skipping blanks; empty when nothing is known. */
    val locationText: String
        get() = listOf(country, region, city).filter { it.isNotBlank() }.distinct().joinToString(" · ")
    /** Short text for the home-screen widget: country/region or the raw country code. */
    val shortText: String
        get() = country.ifBlank { region }.ifBlank { countryCode }
}

object EgressChecker {
    private val client by lazy {
        OkHttpClient.Builder().connectTimeout(8, TimeUnit.SECONDS).readTimeout(12, TimeUnit.SECONDS).build()
    }
    private val json = Json { ignoreUnknownKeys = true }

    /** Throws when both sources fail. */
    suspend fun check(): EgressInfo {
        val t0 = System.currentTimeMillis()
        return try {
            fromIpwho().copy(latencyMs = System.currentTimeMillis() - t0, checkedAt = System.currentTimeMillis())
        } catch (e: Exception) {
            val t1 = System.currentTimeMillis()
            fromCloudflare().copy(latencyMs = System.currentTimeMillis() - t1, checkedAt = System.currentTimeMillis())
        }
    }

    private suspend fun fromIpwho(): EgressInfo = withContext(Dispatchers.IO) {
        val req = Request.Builder().url("https://ipwho.is/")
            .header("Accept", "application/json").header("User-Agent", "MimiPanel").build()
        client.newCall(req).execute().use { r ->
            if (!r.isSuccessful) error("HTTP ${r.code}")
            val o = json.parseToJsonElement(r.body?.string() ?: "").jsonObject
            // ipwho.is answers HTTP 200 with success=false for e.g. reserved ranges
            val ok = (o["success"] as? kotlinx.serialization.json.JsonPrimitive)?.content == "true"
            val ip = o.s("ip")
            if (!ok || ip.isBlank()) error("ipwho.is: unsuccessful")
            val conn = o.obj("connection")
            EgressInfo(
                ip = ip,
                country = o.s("country"),
                countryCode = o.s("country_code"),
                region = o.s("region"),
                city = o.s("city"),
                isp = conn?.s("isp")?.ifBlank { null } ?: conn?.s("org") ?: "",
            )
        }
    }

    private suspend fun fromCloudflare(): EgressInfo = withContext(Dispatchers.IO) {
        val req = Request.Builder().url("https://www.cloudflare.com/cdn-cgi/trace")
            .header("User-Agent", "MimiPanel").build()
        client.newCall(req).execute().use { r ->
            if (!r.isSuccessful) error("HTTP ${r.code}")
            val body = r.body?.string() ?: ""
            val kv = body.lineSequence().mapNotNull { line ->
                val i = line.indexOf('='); if (i > 0) line.substring(0, i) to line.substring(i + 1).trim() else null
            }.toMap()
            val ip = kv["ip"].orEmpty()
            if (ip.isBlank()) error("cloudflare trace: no ip")
            // trace only carries the country code; the UI shows the code / "unknown region"
            EgressInfo(ip = ip, countryCode = kv["loc"].orEmpty())
        }
    }
}
