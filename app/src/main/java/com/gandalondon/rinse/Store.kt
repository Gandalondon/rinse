package com.gandalondon.rinse

import android.content.Context
import org.json.JSONObject

enum class Phase { Unlinked, Idle, Starting, Running, Done, Blocked }

/**
 * What the dishwasher is doing, as last seen. Cached so the widget and tile
 * can draw instantly without a network call.
 */
data class Snapshot(
    val phase: Phase,
    val programKey: String = Programs.ECO50,
    val endAt: Long = 0,          // Running: estimated finish. Done: when it finished.
    val progress: Int = 0,        // 0..100 while running
    val head: String = "",        // Blocked: big word
    val body: String = "",        // Blocked: the fix
    val retryStart: Boolean = false, // Blocked: Try again re-tries the start
) {
    fun toJson(): String = JSONObject()
        .put("phase", phase.name).put("program", programKey).put("endAt", endAt)
        .put("progress", progress).put("head", head).put("body", body).put("retry", retryStart)
        .toString()

    companion object {
        fun fromJson(s: String?): Snapshot? = try {
            val j = JSONObject(s ?: return null)
            Snapshot(
                Phase.valueOf(j.getString("phase")), j.optString("program", Programs.ECO50),
                j.optLong("endAt"), j.optInt("progress"), j.optString("head"), j.optString("body"),
                j.optBoolean("retry"),
            )
        } catch (_: Exception) { null }
    }
}

class Store(context: Context) {
    private val p = context.applicationContext.getSharedPreferences("rinse", Context.MODE_PRIVATE)

    var accessToken: String?
        get() = p.getString("access", null)
        set(v) = p.edit().putString("access", v).apply()
    var refreshToken: String?
        get() = p.getString("refresh", null)
        set(v) = p.edit().putString("refresh", v).apply()
    var tokenExpiresAt: Long
        get() = p.getLong("expires", 0)
        set(v) = p.edit().putLong("expires", v).apply()

    var haId: String?
        get() = p.getString("haId", null)
        set(v) = p.edit().putString("haId", v).apply()
    var haName: String
        get() = p.getString("haName", null) ?: "the dishwasher"
        set(v) = p.edit().putString("haName", v).apply()

    var snapshot: Snapshot
        get() = Snapshot.fromJson(p.getString("snapshot", null))
            ?: Snapshot(if (accessToken == null) Phase.Unlinked else Phase.Idle)
        set(v) = p.edit().putString("snapshot", v.toJson()).apply()

    /** The finish time we last notified about, so each cycle notifies once. */
    var notifiedEnd: Long
        get() = p.getLong("notifiedEnd", 0)
        set(v) = p.edit().putLong("notifiedEnd", v).apply()

    /** Learned programme lengths in minutes, keyed by programme. */
    fun minutes(key: String): Int? =
        p.getInt("min_$key", 0).takeIf { it > 0 } ?: Programs.defaultMinutes(key)

    fun learnMinutes(key: String, minutes: Int) {
        if (minutes in 10..600) p.edit().putInt("min_$key", minutes).apply()
    }

    /** A one-off programme for tonight. Expires at noon the next day, or once used. */
    var tonight: String?
        get() = if (System.currentTimeMillis() < p.getLong("tonightUntil", 0)) p.getString("tonight", null) else null
        set(v) {
            val e = p.edit().putString("tonight", v)
            if (v == null) e.putLong("tonightUntil", 0) else e.putLong("tonightUntil", nextNoon())
            e.apply()
        }

    val program: String get() = tonight ?: Programs.ECO50

    fun signOut() {
        p.edit().remove("access").remove("refresh").remove("expires").apply()
        snapshot = Snapshot(Phase.Unlinked)
    }

    private fun nextNoon(): Long {
        val c = java.util.Calendar.getInstance()
        if (c.get(java.util.Calendar.HOUR_OF_DAY) >= 12) c.add(java.util.Calendar.DAY_OF_YEAR, 1)
        c.set(java.util.Calendar.HOUR_OF_DAY, 12)
        c.set(java.util.Calendar.MINUTE, 0)
        c.set(java.util.Calendar.SECOND, 0)
        return c.timeInMillis
    }
}

object Programs {
    const val ECO50 = "Dishcare.Dishwasher.Program.Eco50"

    private val names = mapOf(
        "Eco50" to "Eco 50", "Auto1" to "Auto 35–45", "Auto2" to "Auto 45–65", "Auto3" to "Auto 65–75",
        "Quick45" to "Quick 45", "Quick65" to "Quick 65", "Intensiv70" to "Intensive 70",
        "Intensiv45" to "Intensive 45", "IntensivPower" to "Intensive power", "Normal65" to "Normal 65",
        "Normal45" to "Normal 45", "Glas40" to "Glass 40", "GlassCare" to "Glass care",
        "NightWash" to "Night wash", "PreRinse" to "Pre-rinse", "AutoHalfLoad" to "Auto half load",
        "MagicDaily" to "Magic daily", "Super60" to "Super 60", "Kurz60" to "Short 60",
        "ExpressSparkle65" to "Express sparkle 65", "MachineCare" to "Machine care",
        "SteamFresh" to "Steam fresh", "MaximumCleaning" to "Maximum cleaning", "MixedLoad" to "Mixed load",
    )

    fun name(key: String): String {
        val short = key.substringAfterLast('.')
        return names[short] ?: short.replace(Regex("(?<=[a-z])(?=[A-Z0-9])"), " ")
            .lowercase().replaceFirstChar { it.uppercase() }
    }

    fun defaultMinutes(key: String): Int? = if (key == ECO50) 290 else null
}
