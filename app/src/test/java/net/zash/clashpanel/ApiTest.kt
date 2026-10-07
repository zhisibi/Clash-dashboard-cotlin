package net.zash.clashpanel

import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.take
import kotlinx.coroutines.flow.toList
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withTimeout
import net.zash.clashpanel.data.*
import org.junit.Assert.*
import org.junit.Assume
import org.junit.Test

class ApiTest {
    private val api = ClashApi(Backend("t", host = "127.0.0.1", port = "19090", secret = "test123"))

    private fun up() = Assume.assumeTrue(runCatching { java.net.Socket("127.0.0.1", 19090).close() }.isSuccess)

    @Test fun backendUrl() {
        assertEquals("http://[::1]:9090/ui", Backend("x", host = "::1", secondaryPath = "/ui/").baseUrl)
        assertEquals(1700000000123L, parseIsoMillis("2023-11-14T22:13:20.123456789Z"))
        assertTrue(parseIsoMillis("2023-11-15T06:13:20.1+08:00") > 0)
    }

    @Test fun live() = runBlocking {
        up()
        assertEquals("v1.19.32", api.version().first)
        val cfg = api.configs(); assertEquals("rule", cfg.mode); assertEquals(17890, cfg.mixedPort)
        val p = api.proxies()
        val g = p["节点选择"]!!; assertTrue(g.isGroup); assertEquals("自动选择", g.now); assertEquals("https://example.com/a.png", g.icon)
        assertEquals(listOf("节点选择", "自动选择"), p["GLOBAL"]!!.all.filter { p[it]?.isGroup == true })
        api.selectProxy("节点选择", "HK-01"); assertEquals("HK-01", api.proxies()["节点选择"]!!.now)
        api.selectProxy("节点选择", "自动选择")
        assertEquals(0, runCatching { api.proxyDelay("HK-01", "https://www.gstatic.com/generate_204", 1000) }.getOrDefault(0))
        val e = runCatching { api.groupDelay("自动选择", "https://www.gstatic.com/generate_204", 1000) }.exceptionOrNull()
        assertTrue(e is ApiException && e.code in 500..599)
        val r = api.rules(); assertEquals(3, r.size); assertEquals("google.com", r[0].payload)
        api.setRuleDisabled(0, true); assertTrue(api.rules()[0].disabled)
        api.setRuleDisabled(0, false); assertFalse(api.rules()[0].disabled)
        api.patchConfigs(kotlinx.serialization.json.buildJsonObject { put("mode", kotlinx.serialization.json.JsonPrimitive("global")) })
        assertEquals("global", api.configs().mode)
        api.patchConfigs(kotlinx.serialization.json.buildJsonObject { put("mode", kotlinx.serialization.json.JsonPrimitive("rule")) })
        api.flushDns(); api.flushFakeIp()
        assertTrue(api.ruleProviders().isEmpty()); assertTrue(api.proxyProviders().isEmpty())
        val msgs = withTimeout(5000) { api.ws("/traffic").take(2).toList() }
        assertTrue(msgs[0].contains("up"))
        val c = withTimeout(5000) { api.ws("/connections?interval=500").first() }
        assertTrue(c.contains("downloadTotal"))
        api.closeAllConnections()
        println("LIVE OK")
    }
}
