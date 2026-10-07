package net.zash.clashpanel.data

import kotlinx.serialization.Serializable
import kotlinx.serialization.json.*

@Serializable
data class Backend(
    val id: String,
    val protocol: String = "http",
    val host: String = "127.0.0.1",
    val port: String = "9090",
    val secondaryPath: String = "",
    val secret: String = "",
    val label: String = "",
) {
    val baseUrl: String
        get() {
            val path = secondaryPath.trim().trim('/').let { if (it.isEmpty()) "" else "/$it" }
            val h = if (host.contains(':') && !host.startsWith("[")) "[$host]" else host
            return "$protocol://$h:$port$path"
        }
    val display: String get() = label.ifBlank { "$host:$port" }
}

data class DelayHistory(val time: String, val delay: Int)

data class Proxy(
    val name: String,
    val type: String,
    val now: String? = null,
    val all: List<String> = emptyList(),
    val history: List<DelayHistory> = emptyList(),
    val extraDelay: Map<String, Int> = emptyMap(),
    val icon: String? = null,
    val udp: Boolean = false,
    val xudp: Boolean = false,
    val tfo: Boolean = false,
    val hidden: Boolean = false,
    val testUrl: String? = null,
    val alive: Boolean = true,
    val providerName: String? = null,
    val fixed: String? = null,
) {
    val isGroup: Boolean get() = all.isNotEmpty() || type.lowercase() in GROUP_TYPES
    val lastDelay: Int get() = history.lastOrNull()?.delay ?: 0
    fun delayFor(url: String?): Int {
        if (url != null) extraDelay[url]?.let { return it }
        return lastDelay
    }
    companion object {
        val GROUP_TYPES = setOf("selector", "urltest", "fallback", "loadbalance", "relay", "smart")
    }
}

data class ProxyProvider(
    val name: String,
    val type: String,
    val vehicleType: String,
    val proxies: List<Proxy>,
    val updatedAt: String?,
    val testUrl: String?,
    val subUpload: Long = 0, val subDownload: Long = 0, val subTotal: Long = 0, val subExpire: Long = 0,
)

data class Rule(
    val index: Int,
    val type: String,
    val payload: String,
    val proxy: String,
    val size: Int = -1,
    val disabled: Boolean = false,
    val hitCount: Long = 0,
)

data class RuleProvider(
    val name: String,
    val behavior: String,
    val format: String,
    val vehicleType: String,
    val ruleCount: Int,
    val updatedAt: String?,
)

data class ConnMeta(
    val network: String = "",
    val type: String = "",
    val sourceIP: String = "",
    val destinationIP: String = "",
    val sourcePort: String = "",
    val destinationPort: String = "",
    val inboundName: String = "",
    val inboundUser: String = "",
    val host: String = "",
    val sniffHost: String = "",
    val process: String = "",
    val processPath: String = "",
    val remoteDestination: String = "",
    val dnsMode: String = "",
    val uid: Int? = null,
) {
    val displayHost: String
        get() = host.ifBlank { sniffHost.ifBlank { destinationIP } }
}

data class Connection(
    val id: String,
    val meta: ConnMeta,
    val upload: Long,
    val download: Long,
    val start: String,
    val chains: List<String>,
    val rule: String,
    val rulePayload: String,
    val uploadSpeed: Long = 0,
    val downloadSpeed: Long = 0,
    val closedAt: Long = 0,
    val raw: String = "",
) {
    val startMillis: Long by lazy { parseIsoMillis(start) }
}

data class LogEntry(val seq: Long, val type: String, val payload: String, val time: Long)

data class Traffic(val up: Long = 0, val down: Long = 0, val upTotal: Long = 0, val downTotal: Long = 0)

data class CoreConfig(
    val mode: String = "rule",
    val logLevel: String = "info",
    val allowLan: Boolean = false,
    val ipv6: Boolean = false,
    val tunEnable: Boolean = false,
    val tunStack: String = "",
    val mixedPort: Int = 0,
    val port: Int = 0,
    val socksPort: Int = 0,
    val redirPort: Int = 0,
    val tproxyPort: Int = 0,
    val bindAddress: String = "",
    val modes: List<String> = listOf("rule", "global", "direct"),
)

// ---------- parsing helpers ----------

private val isoRegex = Regex("""(\d{4})-(\d{2})-(\d{2})T(\d{2}):(\d{2}):(\d{2})(\.\d+)?(Z|[+-]\d{2}:?\d{2})?""")

fun parseIsoMillis(s: String?): Long {
    if (s.isNullOrBlank()) return 0
    return try {
        val m = isoRegex.find(s) ?: return 0
        val g = m.groupValues
        val frac = g[7].removePrefix(".").take(3).padEnd(3, '0')
        val base = "${g[1]}-${g[2]}-${g[3]}T${g[4]}:${g[5]}:${g[6]}.${frac}"
        val zone = g[8].ifEmpty { "Z" }
        java.time.OffsetDateTime.parse(base + (if (zone == "Z") "Z" else if (zone.contains(':')) zone else zone.substring(0, 3) + ":" + zone.substring(3)))
            .toInstant().toEpochMilli()
    } catch (e: Exception) { 0 }
}

fun JsonElement?.str(): String? = (this as? JsonPrimitive)?.takeIf { it.isString || it !is JsonNull }?.contentOrNull
fun JsonObject.s(key: String): String = this[key].str() ?: ""
fun JsonObject.l(key: String): Long = (this[key] as? JsonPrimitive)?.longOrNull ?: (this[key] as? JsonPrimitive)?.doubleOrNull?.toLong() ?: 0L
fun JsonObject.i(key: String): Int = l(key).toInt()
fun JsonObject.b(key: String): Boolean = (this[key] as? JsonPrimitive)?.booleanOrNull ?: false
fun JsonObject.obj(key: String): JsonObject? = this[key] as? JsonObject
fun JsonObject.arr(key: String): JsonArray? = this[key] as? JsonArray

fun parseHistory(a: JsonArray?): List<DelayHistory> =
    a?.mapNotNull { (it as? JsonObject)?.let { o -> DelayHistory(o.s("time"), o.i("delay")) } } ?: emptyList()

fun parseProxy(name: String, o: JsonObject): Proxy {
    val extra = mutableMapOf<String, Int>()
    o.obj("extra")?.forEach { (url, v) ->
        val h = parseHistory((v as? JsonObject)?.arr("history"))
        h.lastOrNull()?.let { extra[url] = it.delay }
    }
    return Proxy(
        name = o.s("name").ifBlank { name },
        type = o.s("type"),
        now = o["now"].str(),
        all = o.arr("all")?.mapNotNull { it.str() } ?: emptyList(),
        history = parseHistory(o.arr("history")),
        extraDelay = extra,
        icon = o["icon"].str()?.takeIf { it.isNotBlank() },
        udp = o.b("udp"),
        xudp = o.b("xudp"),
        tfo = o.b("tfo"),
        hidden = o.b("hidden"),
        testUrl = o["testUrl"].str()?.takeIf { it.isNotBlank() },
        alive = (o["alive"] as? JsonPrimitive)?.booleanOrNull ?: true,
        providerName = o["provider-name"].str(),
        fixed = o["fixed"].str()?.takeIf { it.isNotBlank() },
    )
}

fun parseConnection(o: JsonObject): Connection {
    val m = o.obj("metadata") ?: JsonObject(emptyMap())
    val meta = ConnMeta(
        network = m.s("network"), type = m.s("type"), sourceIP = m.s("sourceIP"),
        destinationIP = m.s("destinationIP"), sourcePort = m.s("sourcePort"),
        destinationPort = m.s("destinationPort"), inboundName = m.s("inboundName"),
        inboundUser = m.s("inboundUser"), host = m.s("host"), sniffHost = m.s("sniffHost"),
        process = m.s("process"), processPath = m.s("processPath"),
        remoteDestination = m.s("remoteDestination"), dnsMode = m.s("dnsMode"),
        uid = (m["uid"] as? JsonPrimitive)?.intOrNull,
    )
    return Connection(
        id = o.s("id"), meta = meta, upload = o.l("upload"), download = o.l("download"),
        start = o.s("start"), chains = o.arr("chains")?.mapNotNull { it.str() } ?: emptyList(),
        rule = o.s("rule"), rulePayload = o.s("rulePayload"), raw = o.toString(),
    )
}
