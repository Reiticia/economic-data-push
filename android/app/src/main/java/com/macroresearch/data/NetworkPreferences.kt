package com.macroresearch.data

import android.content.Context
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import java.net.InetSocketAddress
import java.net.Proxy
import java.net.URI

/**
 * Optional HTTP CONNECT proxy for public data requests (calendar and market/quotes). AI and
 * translation traffic carrying the user's key deliberately stays on the direct connection.
 */
class NetworkPreferences(context: Context) {
    private val preferences = context.getSharedPreferences("calendar_network", Context.MODE_PRIVATE)
    private val _address = MutableStateFlow(preferences.getString("proxy", "").orEmpty())
    val address = _address.asStateFlow()

    fun save(address: String) {
        val clean = normalizeProxyAddress(address)
        preferences.edit().putString("proxy", clean).apply()
        _address.value = clean
    }

    fun proxy(): Proxy? = httpProxy(_address.value)
}

internal fun normalizeProxyAddress(value: String): String {
    val clean = value.trim()
    if (clean.isEmpty()) return ""
    val uri = URI(if ("://" in clean) clean else "http://$clean")
    require(uri.scheme == "http" && !uri.host.isNullOrBlank() && uri.port in 1..65535 &&
        uri.userInfo == null && uri.rawQuery == null && uri.rawFragment == null &&
        uri.rawPath.isNullOrEmpty()) { "Use host:port for an HTTP proxy, without credentials or a path" }
    return uri.toASCIIString()
}

internal fun httpProxy(address: String): Proxy? {
    if (address.isBlank()) return null // Keep the system proxy policy by default.
    val uri = URI(normalizeProxyAddress(address))
    return Proxy(Proxy.Type.HTTP, InetSocketAddress.createUnresolved(uri.host, uri.port))
}
