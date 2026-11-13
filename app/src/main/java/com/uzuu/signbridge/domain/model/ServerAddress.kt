package com.uzuu.signbridge.domain.model

@ConsistentCopyVisibility
data class ServerAddress private constructor(val ipv4: String, val port: Int, val useHttps: Boolean) {
    val baseUrl: String get() = "${if (useHttps) "https" else "http"}://$ipv4:$port/"

    companion object {
        fun parse(ipv4: String, port: String, useHttps: Boolean = false): ServerAddress {
            val parts = ipv4.trim().split('.')
            require(parts.size == 4 && parts.all { part ->
                part.isNotEmpty() && part.length <= 3 && part.all { it in '0'..'9' } &&
                    part.toInt() in 0..255
            }) { "Nhập IPv4 hợp lệ, ví dụ 192.168.1.10; không nhập http:// hoặc /api." }
            val normalized = parts.joinToString(".") { it.toInt().toString() }
            require(normalized != "0.0.0.0" && normalized != "255.255.255.255" &&
                parts[0].toInt() < 224) { "Hãy nhập địa chỉ của thiết bị chạy backend." }
            val portNumber = port.trim().toIntOrNull()
            require(portNumber != null && portNumber in 1..65535) { "Cổng cần là số từ 1 đến 65535." }
            require(useHttps || isLocalIpv4(normalized)) {
                "HTTP dùng cho IP nội bộ. Bật HTTPS nếu kết nối IP công cộng."
            }
            return ServerAddress(normalized, portNumber, useHttps)
        }

        fun isLocalIpv4(host: String): Boolean {
            val parts = host.split('.').map { it.toIntOrNull() ?: return false }
            if (parts.size != 4 || parts.any { it !in 0..255 }) return false
            return parts[0] == 10 || parts[0] == 127 ||
                (parts[0] == 192 && parts[1] == 168) ||
                (parts[0] == 172 && parts[1] in 16..31) ||
                (parts[0] == 169 && parts[1] == 254)
        }
    }
}
