package net.zash.clashpanel.i18n

import android.content.res.Resources
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue

/**
 * In-app language (1.2.0, same model as the HarmonyOS app).
 *
 * Every user-visible string goes through [t]. The tables are generated into Strings.kt from i18n/strings_*.json.
 * [I18n.lang] is snapshot state, so every composable that calls t() recomposes when it changes; the root also
 * re-creates its tree keyed by the language, so switching is immediate and complete without restarting.
 *
 * pref: "system" | "zh" | "en" (SharedPreferences key `lang`); lang: the effective "zh" | "en".
 */
object I18n {
    val PREFS = listOf("system", "zh", "en")

    var pref by mutableStateOf("system"); private set
    var lang by mutableStateOf("zh"); private set

    /** Current system language mapped to a supported UI language: Chinese for zh-*, English for everything else. */
    fun systemLang(): String {
        val l = runCatching { Resources.getSystem().configuration.locales[0].language }.getOrNull() ?: "zh"
        return if (l.lowercase().startsWith("zh")) "zh" else "en"
    }

    private fun resolve(p: String) = if (p == "zh" || p == "en") p else systemLang()

    fun init(p: String) {
        pref = if (p in PREFS) p else "system"
        lang = resolve(pref)
    }

    /** User picked a language in Settings: applies immediately. */
    fun choose(p: String) {
        pref = if (p in PREFS) p else "system"
        val next = resolve(pref)
        if (next != lang) lang = next
    }

    /** System language may have changed (activity created / configuration changed). */
    fun onSystemChanged() {
        if (pref != "system") return
        val next = systemLang()
        if (next != lang) lang = next
    }

    val isEn get() = lang == "en"

    /** Native name of a language option, shown the same in every UI language. */
    fun optionLabel(key: String): String = when (key) {
        "zh" -> ZH["lang_name_zh"]!!
        "en" -> EN["lang_name_en"]!!
        else -> t("lang_system")
    }

    /** The text of a key in both languages (settings search matches either language). */
    fun both(key: String): String = (ZH[key] ?: "") + " " + (EN[key] ?: "")

    fun privacy(): List<String> {
        val src = if (isEn) PRIVACY_EN else PRIVACY_ZH
        if (net.zash.clashpanel.BuildConfig.STORE != "huawei") return src
        // AppGallery build: the update check opens AppGallery instead of contacting GitHub
        return src.map { l ->
            when {
                l.startsWith("- 检查更新：") -> "- 检查更新：仅在您点击“设置 → 关于 → 版本”时，本应用会打开华为应用市场（AppGallery）中本应用的详情页，由应用市场提供更新；本应用自身不访问任何更新服务器，也不发送任何个人信息。华为应用市场按其自身隐私政策处理相关信息。"
                l.startsWith("- Update check:") -> "- Update check: only when you tap Settings > About > Version, the App opens its page in Huawei AppGallery, which provides updates. The App itself contacts no update server and sends no personal information. AppGallery handles any related information under its own privacy policy."
                else -> l
            }
        }
    }
    fun agreement(): List<String> = if (isEn) AGREEMENT_EN else AGREEMENT_ZH
}

/** Look up a UI string; `{0}`, `{1}` … are replaced by args. Reading I18n.lang makes callers depend on the language. */
fun t(key: String, vararg args: Any?): String {
    val table = if (I18n.lang == "en") EN else ZH
    var s = table[key] ?: ZH[key] ?: return key
    args.forEachIndexed { i, a -> s = s.replace("{$i}", a.toString()) }
    return s
}

const val LEGAL_DEVELOPER = "张世博"
const val LEGAL_CONTACT = "https://github.com/zhisibi"
/** Privacy policy version: bump on material changes so users are asked to accept again. */
const val PRIVACY_VERSION = 2
