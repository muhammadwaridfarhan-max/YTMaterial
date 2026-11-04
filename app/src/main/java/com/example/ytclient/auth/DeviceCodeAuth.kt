package com.example.ytclient.auth

import com.example.ytclient.data.Net
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.withContext
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
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

/** OAuth client id/secret of the YouTube TV app, plus the visitor id of the same page. */
internal class TvClientData(val id: String, val secret: String, val visitorData: String?)

/**
 * Login "pakai kode" ala YouTube TV (OAuth device flow), same flow MeeTube/SmartTube use.
 *
 * - Request bodies are JSON (the TV endpoints answer invalid_client to form bodies).
 * - Client id/secret are read from the YouTube TV web app itself instead of being hardcoded, so
 *   they keep working when Google rotates them. The old constants stay only as a last resort.
 */
object DeviceCodeAuth {
    private const val TV_URL = "https://www.youtube.com/tv"
    private const val SCOPE =
        "http://gdata.youtube.com https://www.googleapis.com/auth/youtube-paid-content"
    private const val CODE_URL = "https://www.youtube.com/o/oauth2/device/code"
    private const val TOKEN_URL = "https://www.youtube.com/o/oauth2/token"
    private const val GRANT_DEVICE = "http://oauth.net/grant_type/device/1.0"
    private const val GRANT_REFRESH = "refresh_token"
    private const val MODEL = "ytlr::"

    private const val FALLBACK_ID =
        "861556708454-d6dlm3lh05idd8npek18k6be8ba3oc68.apps.googleusercontent.com"
    private const val FALLBACK_SECRET = "SboVhoG9s0rNafixCSGGKXAT"

    internal const val UA =
        "Mozilla/5.0 (ChromiumStylePlatform) Cobalt/25.lts.30.1034943-gold (unlike Gecko), " +
            "Unknown_TV_Unknown_0/Unknown (Unknown, Unknown)"

    private val JSON = "application/json".toMediaType()

    internal val client: OkHttpClient = OkHttpClient.Builder()
        .dns(Net.dns)
        .connectTimeout(20, TimeUnit.SECONDS)
        .readTimeout(20, TimeUnit.SECONDS)
        .build()

    @Volatile
    private var cached: TvClientData? = null

    private fun get(url: String): String {
        val req = Request.Builder().url(url).header("User-Agent", UA).build()
        client.newCall(req).execute().use { resp ->
            if (!resp.isSuccessful) throw IOException("HTTP ${resp.code}")
            return resp.body?.string().orEmpty()
        }
    }

    private fun absolute(path: String): String = when {
        path.startsWith("http") -> path
        path.startsWith("//") -> "https:$path"
        path.startsWith("/") -> "https://www.youtube.com$path"
        else -> "https://www.youtube.com/$path"
    }

    /** Blocking: call from Dispatchers.IO. */
    internal fun clientData(): TvClientData {
        cached?.let { return it }
        var visitor: String? = null
        val scraped = runCatching {
            val html = get(TV_URL)
            visitor = Regex("\"visitorData\":\"(.*?)\"").find(html)?.groupValues?.get(1)
            val jsPath = listOf(
                "id=\"base-js\" src=\"(.*?)\"",
                "\\.src = '(.*?m=base)'",
                "\\.src = '(.*?)'; .\\.id = 'base-js'"
            ).firstNotNullOfOrNull { Regex(it).find(html)?.groupValues?.get(1) }
                ?: error("base js not found")
            val js = get(absolute(jsPath.replace("\\/", "/")))
            val m = Regex(
                "clientId:\"([-\\w]+\\.apps\\.googleusercontent\\.com)\",\\n?[\$\\w]+:\"(\\w+)\""
            ).find(js) ?: error("client data not found")
            TvClientData(m.groupValues[1], m.groupValues[2], visitor)
        }.getOrNull()
        val data = scraped ?: TvClientData(FALLBACK_ID, FALLBACK_SECRET, visitor)
        if (scraped != null) cached = data
        return data
    }

    internal fun visitorData(): String? = cached?.visitorData

    private fun postJson(url: String, body: JSONObject): JSONObject {
        val req = Request.Builder()
            .url(url)
            .header("User-Agent", UA)
            .post(body.toString().toRequestBody(JSON))
            .build()
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
        val c = clientData()
        val json = postJson(
            CODE_URL,
            JSONObject()
                .put("client_id", c.id)
                .put("device_id", UUID.randomUUID().toString())
                .put("model_name", MODEL)
                .put("scope", SCOPE)
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
        val c = clientData()
        var interval = code.intervalSec.coerceAtLeast(3)
        val deadline = System.currentTimeMillis() + code.expiresInSec * 1000L
        while (System.currentTimeMillis() < deadline) {
            delay(interval * 1000L)
            // The app may be in the background while the user approves the code in the browser;
            // a failed tick (DNS/timeout) must not end the sign-in.
            val json = try {
                postJson(
                    TOKEN_URL,
                    JSONObject()
                        .put("code", code.deviceCode)
                        .put("client_id", c.id)
                        .put("client_secret", c.secret)
                        .put("grant_type", GRANT_DEVICE)
                )
            } catch (e: IOException) {
                continue
            }
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

    /** New access token from a stored refresh token (the refresh token itself does not change). */
    suspend fun refresh(refreshToken: String): Tokens? = withContext(Dispatchers.IO) {
        val c = clientData()
        val json = postJson(
            TOKEN_URL,
            JSONObject()
                .put("refresh_token", refreshToken)
                .put("client_id", c.id)
                .put("client_secret", c.secret)
                .put("grant_type", GRANT_REFRESH)
        )
        if (!json.has("access_token")) null
        else Tokens(
            accessToken = json.getString("access_token"),
            refreshToken = refreshToken,
            expiresInSec = json.optLong("expires_in", 3600)
        )
    }
}
