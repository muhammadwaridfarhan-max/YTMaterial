package com.example.ytclient.data

import okhttp3.Dns
import okhttp3.OkHttpClient
import okhttp3.Request
import org.json.JSONObject
import java.net.InetAddress
import java.net.UnknownHostException
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.TimeUnit

/**
 * DNS with a fallback: if the system resolver fails ("Unable to resolve host"), the name is
 * resolved over HTTPS (Cloudflare, then Google) using their IP addresses directly.
 */
object Net {
    private class Cached(val addrs: List<InetAddress>, val at: Long)

    private val cache = ConcurrentHashMap<String, Cached>()
    private val bootstrap = OkHttpClient.Builder()
        .connectTimeout(6, TimeUnit.SECONDS)
        .readTimeout(6, TimeUnit.SECONDS)
        .build()
    private val endpoints = listOf("https://1.1.1.1/dns-query", "https://8.8.8.8/resolve")

    val dns: Dns = object : Dns {
        override fun lookup(hostname: String): List<InetAddress> {
            try {
                return Dns.SYSTEM.lookup(hostname)
            } catch (e: UnknownHostException) {
                val hit = cache[hostname]
                if (hit != null && System.currentTimeMillis() - hit.at < 300_000L) return hit.addrs
                val resolved = doh(hostname)
                if (resolved.isNotEmpty()) {
                    cache[hostname] = Cached(resolved, System.currentTimeMillis())
                    return resolved
                }
                throw e
            }
        }
    }

    private fun doh(host: String): List<InetAddress> {
        for (base in endpoints) {
            try {
                val req = Request.Builder()
                    .url("$base?name=$host&type=A")
                    .header("Accept", "application/dns-json")
                    .build()
                val found = bootstrap.newCall(req).execute().use { r ->
                    if (!r.isSuccessful) return@use emptyList<InetAddress>()
                    val answers = JSONObject(r.body?.string().orEmpty()).optJSONArray("Answer")
                        ?: return@use emptyList<InetAddress>()
                    val out = ArrayList<InetAddress>()
                    for (i in 0 until answers.length()) {
                        val a = answers.getJSONObject(i)
                        if (a.optInt("type") == 1) {
                            val ip = InetAddress.getByName(a.getString("data"))
                            out.add(InetAddress.getByAddress(host, ip.address))
                        }
                    }
                    out
                }
                if (found.isNotEmpty()) return found
            } catch (_: Exception) {
            }
        }
        return emptyList()
    }
}
