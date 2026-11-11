package com.gandalondon.rinse

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.gandalondon.rinse.hc.DeviceCode
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

data class Ui(
    val snap: Snapshot,
    val settings: Boolean = false,
    val programs: List<String> = emptyList(),
    val signIn: DeviceCode? = null,   // showing a sign-in code
    val busy: Boolean = false,
    val clock: Long = System.currentTimeMillis(),
)

class RinseViewModel(app: Application) : AndroidViewModel(app) {
    private val c get() = getApplication<Application>()
    private val store = Rinse.store(app)

    private val _ui = MutableStateFlow(Ui(store.snapshot))
    val ui: StateFlow<Ui> = _ui

    private var polling: Job? = null

    /** While the app is open: refresh now, then every 30 s while running, every minute otherwise. */
    fun resume() {
        polling?.cancel()
        polling = viewModelScope.launch {
            while (true) {
                if (_ui.value.signIn == null && store.accessToken != null && !_ui.value.busy) {
                    val snap = Rinse.refresh(c)
                    _ui.value = _ui.value.copy(snap = snap)
                } else if (store.accessToken == null) {
                    _ui.value = _ui.value.copy(snap = Snapshot(Phase.Unlinked))
                }
                val wait = if (_ui.value.snap.phase == Phase.Running) 30_000L else 60_000L
                var waited = 0L
                while (waited < wait) { // tick the clock so "Done at" and "left" stay current
                    delay(10_000); waited += 10_000
                    _ui.value = _ui.value.copy(clock = System.currentTimeMillis())
                }
            }
        }
    }

    fun pause() { polling?.cancel() }

    fun start() = act { Rinse.start(c) }
    fun stop() = act { Rinse.stop(c) }

    fun retry() = act {
        if (_ui.value.snap.retryStart) Rinse.start(c) else Rinse.refresh(c)
    }

    private fun act(block: suspend () -> Snapshot) {
        if (_ui.value.busy) return
        _ui.value = _ui.value.copy(busy = true)
        viewModelScope.launch {
            val snap = block()
            _ui.value = _ui.value.copy(snap = snap, busy = false)
        }
    }

    // --- Tonight's programme ---

    fun openSettings() {
        _ui.value = _ui.value.copy(settings = true)
        viewModelScope.launch {
            val list = withContext(Dispatchers.IO) { Rinse.dishwasher(c).programs() }
            _ui.value = _ui.value.copy(programs = list)
        }
    }

    fun closeSettings() { _ui.value = _ui.value.copy(settings = false) }

    fun pick(key: String) {
        store.tonight = if (key == Programs.ECO50) null else key
        _ui.value = _ui.value.copy(settings = false, snap = _ui.value.snap.copy(programKey = store.program))
        Rinse.publish(c, store.snapshot)
    }

    // --- Linking Ran (Home Connect Device Flow) ---

    /** Gets a code, then waits for it to be approved in the browser. [openBrowser] gets the Bosch page. */
    fun signIn(openBrowser: (String) -> Unit) {
        if (_ui.value.busy) return
        _ui.value = _ui.value.copy(busy = true)
        viewModelScope.launch {
            val code = try {
                withContext(Dispatchers.IO) { Rinse.dishwasher(c).api.startSignIn() }
            } catch (e: Exception) {
                _ui.value = _ui.value.copy(busy = false, snap = Snapshot(Phase.Blocked,
                    head = "Can’t reach Bosch", body = "Check your phone is online, then try again"))
                return@launch
            }
            _ui.value = _ui.value.copy(busy = false, signIn = code)
            openBrowser(code.verifyUrl)

            var interval = code.intervalSec
            while (System.currentTimeMillis() < code.expiresAt && _ui.value.signIn != null) {
                delay(interval * 1000L)
                val ok = try {
                    withContext(Dispatchers.IO) { Rinse.dishwasher(c).api.pollSignIn(code) }
                } catch (e: com.gandalondon.rinse.hc.HcError) {
                    if (e.key == "slow_down") { interval += 5; false } else break
                } catch (_: Exception) { false }
                if (ok) {
                    store.haId = null // refresh finds the dishwasher on this account
                    val snap = withContext(Dispatchers.IO) { Rinse.dishwasher(c).refresh() }
                    Rinse.publish(c, snap)
                    _ui.value = _ui.value.copy(signIn = null, snap = snap)
                    return@launch
                }
            }
            _ui.value = _ui.value.copy(signIn = null) // expired or refused: back to the start
        }
    }

    fun cancelSignIn() { _ui.value = _ui.value.copy(signIn = null) }
}
