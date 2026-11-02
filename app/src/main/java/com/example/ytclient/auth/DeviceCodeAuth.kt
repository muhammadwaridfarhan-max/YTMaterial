package com.example.ytclient.auth

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.withContext
import okhttp3.FormBody
import okhttp3.OkHttpClient
import okhttp3.Request
import org.json.JSONObject
import java.io.IOException
import java.util.UUID
import java.util.concurrent.TimeUnit

data class DeviceCode(
    val userCode: String,
    val verificationUrl: String,
    val deviceCode: String,
    val intervalSec: Int,
    val expiresInSec: Int
)

data class Tokens(val accessToken: String, val refreshToken: String, val expiresInSec: Long)

/**
 * Login "pakai kode" ala YouTube TV (OAuth device flow).
 * CLIENT_ID / CLIENT_SECRET ditulis dari ingatan, belum diverifikasi.
 * Kalau error invalid_client, cocokkan dengan MediaServiceCore di repo meetube.
 */
object DeviceCodeAuth {
    private const val CLIENT_ID =
        "861556708454-d6dlm3lh05idd8npek18k6be8ba3oc68.apps.googleusercontent.com"
    private const val CLIENT_SECRET = "SboVhoG9s0rNafixCSGGKXAT"
    private const val SCOPE = "https://www.googleapis.com/auth/youtube-paid-content"
    private const val CODE_URL = "https://www.youtube.com/o/oauth2/device/code"
    private const val TOKEN_URL = "https://www.youtube.com/o/oauth2/token"
    private const val GRANT = "http://oauth.net/grant_type/device/1.0"
    private const val UA = "Mozilla/5.0 (ChromiumStylePlatform) Cobalt/Version"

    private val client = OkHttpClient.Builder()
        .connectTimeout(20, TimeUnit.SECONDS)
        .readTimeout(20, TimeUnit.SECONDS)
        .build()

    private fun post(url: String, form: FormBody): JSONObject {
        val req = Request.Builder().url(url).header("User-Agent", UA).post(form).build()
        client.newCall(req).execute().use { resp ->
            val text = resp.body?.string().orEmpty()
            return try {
                JSONObject(text)
            } catch (e: Exception) {
                throw IOException("Unexpected response (${resp.code})")
            }
        }
    }

    suspend fun requestCode(): DeviceCode = withContext(Dispatchers.IO) {
        val json = post(
            CODE_URL,
            FormBody.Builder()
                .add("client_id", CLIENT_ID)
                .add("scope", SCOPE)
                .add("device_id", UUID.randomUUID().toString())
                .add("device_model", "ytlr::")
                .build()
        )
        if (!json.has("user_code")) {
            throw IOException(json.optString("error_description", json.optString("error", "No code")))
        }
        DeviceCode(
            userCode = json.getString("user_code"),
            verificationUrl = json.optString("verification_url", "https://www.google.com/device"),
            deviceCode = json.getString("device_code"),
            intervalSec = json.optInt("interval", 5),
            expiresInSec = json.optInt("expires_in", 1800)
        )
    }

    suspend fun awaitTokens(code: DeviceCode): Tokens = withContext(Dispatchers.IO) {
        var interval = code.intervalSec.coerceAtLeast(3)
        val deadline = System.currentTimeMillis() + code.expiresInSec * 1000L
        while (System.currentTimeMillis() < deadline) {
            delay(interval * 1000L)
            val json = post(
                TOKEN_URL,
                FormBody.Builder()
                    .add("client_id", CLIENT_ID)
                    .add("client_secret", CLIENT_SECRET)
                    .add("code", code.deviceCode)
                    .add("grant_type", GRANT)
                    .build()
            )
            val err = json.optString("error")
            when {
                json.has("access_token") && json.has("refresh_token") -> return@withContext Tokens(
                    accessToken = json.getString("access_token"),
                    refreshToken = json.getString("refresh_token"),
                    expiresInSec = json.optLong("expires_in", 3600)
                )

                err == "authorization_pending" || err.isEmpty() -> Unit
                err == "slow_down" -> interval += 5
                err == "access_denied" -> throw IOException("Access denied")
                err == "expired_token" -> throw IOException("Code expired")
                else -> throw IOException(err)
            }
        }
        throw IOException("Code expired")
    }
}
