package com.gandalondon.rinse.hc

import com.gandalondon.rinse.Phase
import com.gandalondon.rinse.Programs
import com.gandalondon.rinse.Snapshot
import com.gandalondon.rinse.Store
import org.json.JSONArray
import org.json.JSONObject

/** Reads and drives the one dishwasher on the account. All calls block: run on Dispatchers.IO. */
class Dishwasher(private val store: Store) {

    val api = HomeConnect(store)

    private val ha get() = "/api/homeappliances/${store.haId}"

    /** After sign-in: find the dishwasher on the account (ignores the oven). */
    fun findDishwasher(): Boolean {
        val list = api.get("/api/homeappliances").getJSONObject("data").getJSONArray("homeappliances")
        for (i in 0 until list.length()) {
            val a = list.getJSONObject(i)
            if (a.optString("type") == "Dishwasher") {
                store.haId = a.getString("haId")
                store.haName = a.optString("name").ifEmpty { "the dishwasher" }
                return true
            }
        }
        return false
    }

    /** Fetches the current state and caches it. Never throws: problems become a Blocked snapshot. */
    fun refresh(): Snapshot {
        val snap = guard { read() }
        store.snapshot = snap
        return snap
    }

    private fun read(): Snapshot {
        if (store.haId == null && !findDishwasher()) return noDishwasher()
        val appliance = api.get(ha).getJSONObject("data")
        if (!appliance.optBoolean("connected", true)) return offline()

        val status = statusMap()
        val op = status["BSH.Common.Status.OperationState"]?.toString()?.substringAfterLast('.') ?: "Ready"
        return when (op) {
            "Run", "DelayedStart", "Pause" -> running()
            "Finished" -> {
                val prev = store.snapshot
                val endAt = if (prev.phase == Phase.Done || prev.phase == Phase.Running) prev.endAt.coerceAtMost(now())
                else now()
                Snapshot(Phase.Done, prev.programKey, endAt = endAt)
            }
            "ActionRequired", "Error" -> Snapshot(
                Phase.Blocked, head = "${store.haName} needs you. Check its display.",
            )
            else -> Snapshot(Phase.Idle, store.program)
        }
    }

    private fun running(): Snapshot {
        val active = api.get("$ha/programs/active").getJSONObject("data")
        val key = active.optString("key").ifEmpty { store.snapshot.programKey }
        val opts = options(active.optJSONArray("options"))
        val remaining = (opts["BSH.Common.Option.RemainingProgramTime"] as? Number)?.toLong()
        val progress = (opts["BSH.Common.Option.ProgramProgress"] as? Number)?.toInt() ?: 0
        val total = (opts["BSH.Common.Option.EstimatedTotalProgramTime"] as? Number)?.toLong()

        // Learn how long this programme really takes, so Idle can say "Done at".
        if (total != null) store.learnMinutes(key, (total / 60).toInt())
        else if (remaining != null && progress in 1..30) store.learnMinutes(key, (remaining / 60 / (1 - progress / 100.0)).toInt())

        val endAt = when {
            remaining != null -> now() + remaining * 1000
            store.snapshot.phase == Phase.Running -> store.snapshot.endAt
            else -> now() + (store.minutes(key) ?: 120) * 60_000L
        }
        return Snapshot(Phase.Running, key, endAt = endAt, progress = progress)
    }

    /** Starts tonight's programme. Returns what happened, ready to show. */
    fun start(): Snapshot {
        val key = store.program
        val snap = guard {
            if (store.haId == null && !findDishwasher()) return@guard noDishwasher()
            val appliance = api.get(ha).getJSONObject("data")
            if (!appliance.optBoolean("connected", true)) return@guard offline()

            val status = statusMap()
            val door = status["BSH.Common.Status.DoorState"]?.toString()?.substringAfterLast('.')
            if (door == "Open") return@guard doorOpen()
            if (status["BSH.Common.Status.RemoteControlStartAllowed"] == false) return@guard remoteOff()

            // Wake it up if it has switched itself off. Not every model needs or allows this.
            try {
                api.put("$ha/settings/BSH.Common.Setting.PowerState", JSONObject().put("data",
                    JSONObject().put("key", "BSH.Common.Setting.PowerState").put("value", "BSH.Common.EnumType.PowerState.On")))
                Thread.sleep(1500)
            } catch (_: HcError) { }

            api.put("$ha/programs/active", JSONObject().put("data",
                JSONObject().put("key", key).put("options", JSONArray())))
            store.tonight = null
            Snapshot(Phase.Running, key, endAt = now() + (store.minutes(key) ?: 120) * 60_000L)
        }
        val result = if (snap.phase == Phase.Blocked) snap.copy(retryStart = true) else snap
        store.snapshot = result
        return result
    }

    fun stop(): Snapshot {
        guard { api.delete("$ha/programs/active"); Snapshot(Phase.Idle) }
        Thread.sleep(1500)
        return refresh()
    }

    /** Programmes this dishwasher offers, Eco 50 first. */
    fun programs(): List<String> = try {
        val arr = api.get("$ha/programs/available").getJSONObject("data").getJSONArray("programs")
        val keys = (0 until arr.length()).map { arr.getJSONObject(it).getString("key") }
        listOf(Programs.ECO50) + keys.filter { it != Programs.ECO50 }
    } catch (_: Exception) { listOf(Programs.ECO50) }

    // --- helpers ---

    private fun statusMap(): Map<String, Any?> {
        val arr = api.get("$ha/status").getJSONObject("data").getJSONArray("status")
        return (0 until arr.length()).associate { arr.getJSONObject(it).let { s -> s.getString("key") to s.opt("value") } }
    }

    private fun options(arr: JSONArray?): Map<String, Any?> {
        if (arr == null) return emptyMap()
        return (0 until arr.length()).associate { arr.getJSONObject(it).let { o -> o.getString("key") to o.opt("value") } }
    }

    private inline fun guard(block: () -> Snapshot): Snapshot = try {
        block()
    } catch (_: SignedOut) {
        Snapshot(Phase.Unlinked)
    } catch (e: HcError) {
        fromError(e)
    } catch (e: Exception) {
        if (HomeConnect.isNetwork(e)) Snapshot(Phase.Blocked, head = "No connection. Check your phone is online.")
        else Snapshot(Phase.Blocked, head = "Something went wrong. Try again.")
    }

    private fun fromError(e: HcError): Snapshot {
        val k = e.key.lowercase()
        return when {
            "door" in k -> doorOpen()
            "remotestart" in k || "remotecontrol" in k -> remoteOff()
            "connection" in k || "offline" in k || "disconnected" in k -> offline()
            e.code == 429 -> Snapshot(Phase.Blocked, head = "Too many tries. Wait a minute.")
            else -> Snapshot(Phase.Blocked, head = "${store.haName} said no. ${e.message.trimEnd('.')}.")
        }
    }

    private fun doorOpen() = Snapshot(Phase.Blocked, head = "Door open. Close it, then try again.", retryStart = true)
    private fun remoteOff() = Snapshot(Phase.Blocked, head = "Remote start is off. Turn it on at ${store.haName}, then try again.", retryStart = true)
    private fun offline() = Snapshot(Phase.Blocked, head = "Can’t reach ${store.haName}. Check it is on and online.")
    private fun noDishwasher() = Snapshot(Phase.Blocked, head = "No dishwasher on this Bosch account.")
    private fun now() = System.currentTimeMillis()
}
