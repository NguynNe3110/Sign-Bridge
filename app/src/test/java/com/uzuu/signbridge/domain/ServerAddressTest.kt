package com.uzuu.signbridge.domain

import com.uzuu.signbridge.domain.model.ServerAddress
import org.junit.Assert.*
import org.junit.Test

class ServerAddressTest {
    @Test fun normalizesIpv4AndSupportsEmulatorAndLoopback() {
        assertEquals("http://192.168.1.10:8080/", ServerAddress.parse(" 192.168.001.010 ", "8080").baseUrl)
        assertEquals("http://10.0.2.2:3000/", ServerAddress.parse("10.0.2.2", "3000").baseUrl)
        assertEquals("http://127.0.0.1:8080/", ServerAddress.parse("127.0.0.1", "8080").baseUrl)
    }
    @Test fun rejectsMalformedAddressesAndPorts() {
        listOf("", "192.168.1", "256.1.1.1", "http://192.168.1.2", "192.168.1.1/api",
            "1..2.3", "192.168.a.1", "0.0.0.0", "255.255.255.255", "224.1.2.3").forEach {
            assertThrows(it, IllegalArgumentException::class.java) { ServerAddress.parse(it, "8080") }
        }
        listOf("", "0", "65536", "-1", "abc").forEach {
            assertThrows(IllegalArgumentException::class.java) { ServerAddress.parse("192.168.1.2", it) }
        }
    }
    @Test fun publicAddressNeedsHttpsAndPrivateRangesAreBounded() {
        assertThrows(IllegalArgumentException::class.java) { ServerAddress.parse("8.8.8.8", "8080") }
        assertEquals("https://8.8.8.8:443/", ServerAddress.parse("8.8.8.8", "443", true).baseUrl)
        assertTrue(ServerAddress.isLocalIpv4("172.16.0.1"))
        assertTrue(ServerAddress.isLocalIpv4("172.31.255.254"))
        assertFalse(ServerAddress.isLocalIpv4("172.32.0.1"))
        assertFalse(ServerAddress.isLocalIpv4("example.com"))
    }
}
