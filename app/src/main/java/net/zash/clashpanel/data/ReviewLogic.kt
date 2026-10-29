package net.zash.clashpanel.data

/**
 * Pure rate-limit logic behind the gentle AppGallery review prompt (1.3.0, same spec as the HarmonyOS app's
 * common/ReviewLogic.ets and MimiFTP 1.3.0 / MimiSSH 1.5.11).
 *
 * Rules:
 * - Trigger: installed >= 3 days + >= 5 launches + >= 3 successful backend connections, all three; never on the
 *   first launch, never while a connection attempt is running; failed connections do not count.
 * - Rate limit: >= 90 days between prompts, at most 3 prompts in the app's lifetime; "Later" snoozes 60 days;
 *   "Rate" or "Don't remind me" turn automatic prompts off for good (the permanent entry in
 *   Settings > About is not affected).
 * This file only decides and mutates the plain data object; storage / UI live in MainViewModel / ReviewCard.
 */

/** Minimum install age before the first prompt: 3 days */
const val REVIEW_MIN_AGE_MS: Long = 3 * 24 * 60 * 60 * 1000L
/** Minimum cumulative launches before the first prompt */
const val REVIEW_MIN_LAUNCHES: Int = 5
/** Minimum successful backend connections before the first prompt */
const val REVIEW_MIN_SUCCESSES: Int = 3
/** Minimum interval between two automatic prompts: 90 days */
const val REVIEW_INTERVAL_MS: Long = 90 * 24 * 60 * 60 * 1000L
/** Snooze after "Later": 60 days (the 90-day interval still applies, so the next prompt is >= 90 days out) */
const val REVIEW_SNOOZE_MS: Long = 60 * 24 * 60 * 60 * 1000L
/** Maximum automatic prompts in the app's lifetime */
const val REVIEW_MAX_SHOWS: Int = 3

class ReviewData {
    /** First launch timestamp (ms) recorded by this feature; 0 = not recorded yet */
    var firstSeen: Long = 0
    /** Cumulative launches (counted once per process start) */
    var launches: Int = 0
    /** Cumulative successful backend connections */
    var successes: Int = 0
    /** Automatic prompts shown so far */
    var shown: Int = 0
    /** Last prompt timestamp (ms); 0 = never shown */
    var lastShown: Long = 0
    /** Snooze expiry timestamp (ms); 0 = no snooze */
    var snoozeUntil: Long = 0
    /** pending | rated | never */
    var life: String = "pending"
}

/**
 * Why an automatic prompt cannot show right now: every unmet condition as a short code (for logs);
 * an empty list means it can show.
 * Codes: life-done / life-never / busy / age / launches / successes / max-shows / interval / snooze
 */
fun reviewBlockers(d: ReviewData, now: Long, busy: Boolean): List<String> {
    val out = mutableListOf<String>()
    if (d.life == "rated") out.add("life-done")
    if (d.life == "never") out.add("life-never")
    if (busy) out.add("busy")
    if (d.firstSeen <= 0 || now - d.firstSeen < REVIEW_MIN_AGE_MS) out.add("age")
    if (d.launches < REVIEW_MIN_LAUNCHES) out.add("launches")
    if (d.successes < REVIEW_MIN_SUCCESSES) out.add("successes")
    if (d.shown >= REVIEW_MAX_SHOWS) out.add("max-shows")
    if (d.lastShown > 0 && now - d.lastShown < REVIEW_INTERVAL_MS) out.add("interval")
    if (d.snoozeUntil > now) out.add("snooze")
    return out
}

/** Whether an automatic prompt may show right now (all conditions met) */
fun reviewShouldShow(d: ReviewData, now: Long, busy: Boolean): Boolean = reviewBlockers(d, now, busy).isEmpty()

/** Records a launch: stamps firstSeen on the first one, then only counts. Returns true for the first launch. */
fun reviewRecordLaunch(d: ReviewData, now: Long): Boolean {
    val first = d.firstSeen <= 0
    if (first) d.firstSeen = now
    d.launches++
    return first
}

/** Records a successful backend connection. */
fun reviewRecordSuccess(d: ReviewData) { d.successes++ }

/** A prompt was shown: count it and stamp the time (rate limiting starts at display, not at the tap). */
fun reviewRecordShown(d: ReviewData, now: Long) { d.shown++; d.lastShown = now }

/** "Rate": automatic prompts off for good. */
fun reviewChooseRate(d: ReviewData) { d.life = "rated"; d.snoozeUntil = 0 }

/** "Later" (also the feedback entry and the close button): snooze 60 days, lifecycle unchanged. */
fun reviewChooseLater(d: ReviewData, now: Long) { d.snoozeUntil = now + REVIEW_SNOOZE_MS }

/** "Don't remind me": automatic prompts off for good. */
fun reviewChooseNever(d: ReviewData) { d.life = "never"; d.snoozeUntil = 0 }
