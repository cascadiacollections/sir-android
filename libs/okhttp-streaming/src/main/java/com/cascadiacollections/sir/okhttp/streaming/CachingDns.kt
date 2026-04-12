package com.cascadiacollections.sir.okhttp.streaming

import okhttp3.Dns
import java.net.Inet4Address
import java.net.InetAddress
import java.util.concurrent.ConcurrentHashMap

/**
 * DNS resolver that caches lookups for [ttlMs] to avoid repeated resolution on reconnect.
 * Results are sorted to prefer IPv4 for faster connection on mobile networks.
 */
internal class CachingDns(private val ttlMs: Long = 5 * 60 * 1000L) : Dns {
    private val cache = ConcurrentHashMap<String, Pair<List<InetAddress>, Long>>()

    override fun lookup(hostname: String): List<InetAddress> {
        val now = System.currentTimeMillis()
        return cache[hostname]
            ?.takeIf { now - it.second < ttlMs }
            ?.first
            ?: Dns.SYSTEM.lookup(hostname)
                .sortedBy { it !is Inet4Address }
                .also { cache[hostname] = it to now }
    }
}
