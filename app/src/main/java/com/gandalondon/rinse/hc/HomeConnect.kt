package com.gandalondon.rinse.hc

import com.gandalondon.rinse.BuildConfig
import com.gandalondon.rinse.Store
import okhttp3.FormBody
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import org.json.JSONObject
import java.io.IOException
import java.util.concurrent.TimeUnit

/** Thrown when Home Connect refuses a request. [key] is Bosch's error key. */
class HcError(val code: Int, val key: String, override val message: String) : Exception(message)

/** The sign-in is gone (revoked or expired): the user has to link Ran again. */
class SignedOut : Exception("Signed out")

data class DeviceCode(
    val deviceCode: String,
    val userCode: String,
    val verifyUrl: String,
    val intervalSec: Int,
    val expiresAt: Long,
)

/**
 * Minimal Home Connect client: Device Flow sign-in, token refresh and JSON calls.
 * Docs: https://api-docs.home-connect.com
 */
class HomeConnect(private val store: Store) {

    private val http = OkHttpClient.Builder()
        .connectTimeout(10, TimeUnit.SECONDS)
        .readTimeout(15, TimeUnit.SECONDS)
        .build()

    private val base = "https://api.home-connect.com"
    private val bsh = "application/vnd.bsh.sdk.v1+json"
    private val scope = "IdentifyAppliance Dishwasher"

    // --- Sign-in (Device Flow) ---

    fun startSignIn(): DeviceCode {
        val form = FormBody.Builder()
            .add("client_id", BuildConfig.HC_CLIENT_ID)
            .add("scope", scope)
            .build()
        val json = postForm("/security/oauth/device_authorization", form)
        val verify = json.optString("verification_uri_complete").ifEmpty { json.getString("verification_uri") }
        return DeviceCode(
            deviceCode = json.getString("device_code"),
            userCode = json.getString("user_code"),
            verifyUrl = verify,
            intervalSec = json.optInt("interval", 5),
            expiresAt = System.currentTimeMillis() + json.optInt("expires_in", 300) * 1000L,
        )
    }

    /** True once the user has approved; false while still waiting. */
    fun pollSignIn(code: DeviceCode): Boolean {
        val form = FormBody.Builder()
            .add("grant_type", "device_code")
            .add("device_code", code.deviceCode)
            .add("client_id", BuildConfig.HC_CLIENT_ID)
            .build()
        return try {
            saveTokens(postForm("/security/oauth/token", form))
            true
        } catch (e: HcError) {
            if (e.key == "authorization_pending" || e.key == "slow_down") false else throw e
        }
    }

    private fun saveTokens(json: JSONObject) {
        store.accessToken = json.getString("access_token")
        json.optString("refresh_token").takeIf { it.isNotEmpty() }?.let { store.refreshToken = it }
        // Refresh a little early so a call never lands on an expired token.
        store.tokenExpiresAt = System.currentTimeMillis() + (json.optLong("expires_in", 86400) - 300) * 1000L
    }

    @Synchronized
    private fun refresh() {
        val refreshToken = store.refreshToken ?: throw SignedOut()
        val form = FormBody.Builder()
            .add("grant_type", "refresh_token")
            .add("refresh_token", refreshToken)
            .add("client_id", BuildConfig.HC_CLIENT_ID)
            .add("client_secret", BuildConfig.HC_CLIENT_SECRET)
            .build()
        try {
            saveTokens(postForm("/security/oauth/token", form))
        } catch (e: HcError) {
            if (e.code in 400..401) {
                store.signOut()
                throw SignedOut()
            }
            throw e
        }
    }

    fun freshToken(): String = token()

    private fun token(): String {
        if (store.accessToken == null) throw SignedOut()
        if (System.currentTimeMillis() > store.tokenExpiresAt) refresh()
        return store.accessToken ?: throw SignedOut()
    }

    // --- API calls ---

    fun get(path: String): JSONObject = call("GET", path, null)
    fun put(path: String, body: JSONObject): JSONObject = call("PUT", path, body)
    fun delete(path: String): JSONObject = call("DELETE", path, null)

    private fun call(method: String, path: String, body: JSONObject?, retried: Boolean = false): JSONObject {
        val req = Request.Builder()
            .url(base + path)
            .header("Authorization", "Bearer ${token()}")
            .header("Accept", bsh)
            .header("Accept-Language", "en-GB")
            .method(method, body?.toString()?.toRequestBody(bsh.toMediaType()))
            .build()
        http.newCall(req).execute().use { res ->
            val text = res.body?.string().orEmpty()
            if (res.code == 401 && !retried) {
                refresh()
                return call(method, path, body, retried = true)
            }
            if (!res.isSuccessful) throw parseError(res.code, text)
            return if (text.isBlank()) JSONObject() else JSONObject(text)
        }
    }

    private fun postForm(path: String, form: FormBody): JSONObject {
        val req = Request.Builder().url(base + path).post(form).build()
        http.newCall(req).execute().use { res ->
            val text = res.body?.string().orEmpty()
            if (!res.isSuccessful) throw parseError(res.code, text)
            return JSONObject(text)
        }
    }

    private fun parseError(code: Int, text: String): HcError {
        return try {
            val json = JSONObject(text)
            val err = json.opt("error")
            when (err) {
                // API errors: {"error":{"key":"...","description":"..."}}
                is JSONObject -> HcError(code, err.optString("key"), err.optString("description").ifEmpty { "Error $code" })
                // OAuth errors: {"error":"authorization_pending","error_description":"..."}
                is String -> HcError(code, err, json.optString("error_description").ifEmpty { err })
                else -> HcError(code, "", "Error $code")
            }
        } catch (_: Exception) {
            HcError(code, "", "Error $code")
        }
    }

    companion object {
        fun isNetwork(e: Throwable) = e is IOException
    }
}
