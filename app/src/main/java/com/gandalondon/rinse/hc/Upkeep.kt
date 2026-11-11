package com.gandalondon.rinse.hc

import com.gandalondon.rinse.Store
import okhttp3.OkHttpClient
import okhttp3.Request
import org.json.JSONObject
import java.util.concurrent.TimeUnit

/**
 * Salt, rinse aid, filter and machine care alerts.
 *
 * Home Connect only announces these live (as events), usually as a wash ends.
 * Rinse listens for the last minutes of each wash and keeps what it hears:
 *  - Present adds an alert, Off or Confirmed removes it.
 *  - If the wash finishes while listening, what was heard replaces the old list,
 *    so a refill drops off after the next wash even if Bosch never sends Off.
 */
object Upkeep {
    private const val P = "Dishcare.Dishwasher.Event."

    /** Event key to the job, in the order they read in a sentence. */
    private val jobs = linkedMapOf(
        "SaltLack" to "salt", "SaltNearlyEmpty" to "salt", "ProgramBlockedSaltLack" to "salt",
        "RinseAidLack" to "rinse aid", "RinseAidNearlyEmpty" to "rinse aid",
        "SmartFilterCleaningReminder" to "filter",
        "MachineCareAndFilterCleaningReminder" to "care",
        "MachineCareAndLowMaintenanceFilterCleaningReminder" to "care",
        "MachineCareReminder" to "care",
    )

    fun isUpkeep(key: String) = key.startsWith(P) && key.removePrefix(P) in jobs

    /** "Add salt and rinse aid", "Clean the filter", "Add salt and clean the filter", or null. */
    fun phrase(keys: Set<String>): String? {
        val wanted = keys.mapNotNull { jobs[it.removePrefix(P)] }.toSet()
        if (wanted.isEmpty()) return null
        val parts = mutableListOf<String>()
        val add = listOf("salt", "rinse aid").filter { it in wanted }
        if (add.isNotEmpty()) parts += "add " + add.joinToString(" and ")
        if ("care" in wanted) parts += "run machine care"
        else if ("filter" in wanted) parts += "clean the filter"
        val s = parts.joinToString(" and ")
        return s.replaceFirstChar { it.uppercase() }
    }

    private val stream = OkHttpClient.Builder()
        .connectTimeout(10, TimeUnit.SECONDS)
        .readTimeout(90, TimeUnit.SECONDS) // Bosch sends a keep-alive about once a minute
        .build()

    /**
     * Listens to the dishwasher's events for up to [maxMs], stopping a little
     * after the wash finishes. Updates the stored alerts. Returns true if it saw the finish.
     */
    fun listen(store: Store, token: String, maxMs: Long): Boolean {
        val haId = store.haId ?: return false
        val heard = store.upkeep.toMutableSet()
        val fresh = mutableSetOf<String>()
        var finishedAt = 0L
        val deadline = System.currentTimeMillis() + maxMs

        val req = Request.Builder()
            .url("https://api.home-connect.com/api/homeappliances/$haId/events")
            .header("Authorization", "Bearer $token")
            .header("Accept", "text/event-stream")
            .build()
        val call = stream.newCall(req)
        try {
            call.execute().use { res ->
                if (!res.isSuccessful) return false
                val src = res.body?.source() ?: return false
                while (System.currentTimeMillis() < deadline) {
                    if (finishedAt > 0 && System.currentTimeMillis() - finishedAt > 90_000) break
                    val line = src.readUtf8Line() ?: break
                    if (!line.startsWith("data:")) continue
                    val data = line.removePrefix("data:").trim()
                    if (data.isEmpty()) continue
                    val items = try { JSONObject(data).optJSONArray("items") } catch (_: Exception) { null } ?: continue
                    for (i in 0 until items.length()) {
                        val item = items.getJSONObject(i)
                        val key = item.optString("key")
                        val value = item.opt("value")?.toString().orEmpty()
                        when {
                            isUpkeep(key) && value.endsWith("Present") -> { heard += key; fresh += key }
                            isUpkeep(key) -> { heard -= key; fresh -= key }
                            key == "BSH.Common.Event.ProgramFinished" ||
                                (key == "BSH.Common.Status.OperationState" && value.endsWith("Finished")) ->
                                if (finishedAt == 0L) finishedAt = System.currentTimeMillis()
                        }
                    }
                    store.upkeep = heard // save as we go, in case we are stopped early
                }
            }
        } catch (_: Exception) {
            // Lost the stream: keep what we heard.
        } finally {
            call.cancel()
        }
        // Saw the wash end: what it announced is the whole list now.
        store.upkeep = if (finishedAt > 0) fresh else heard
        return finishedAt > 0
    }
}
