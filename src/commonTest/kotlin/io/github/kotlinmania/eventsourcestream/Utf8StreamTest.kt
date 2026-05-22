// port-lint: source src/utf8_stream.rs
package io.github.kotlinmania.eventsourcestream

import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.toList
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFails
import kotlin.test.assertTrue

class Utf8StreamTest {

    @Test
    fun validStreams() = runTest {
        // Upstream Utf8Stream is generic over `B: AsRef<[u8]>`, so the test exercises both
        // a byte-literal input (`b"Hello, world!"`, `&[u8; N]`) and a `&str` input. Both
        // forms reduce to the same ByteArray in Kotlin, so the duplicated assertion just
        // re-runs the same input shape; it's kept here to preserve the upstream test order.
        assertEquals(
            listOf("Hello, world!"),
            flowOf("Hello, world!".encodeToByteArray())
                .asUtf8Stream()
                .toList(),
        )
        assertEquals(
            listOf("Hello, world!"),
            flowOf("Hello, world!".encodeToByteArray())
                .asUtf8Stream()
                .toList(),
        )
        assertEquals(
            listOf(""),
            flowOf(byteArrayOf())
                .asUtf8Stream()
                .toList(),
        )
        assertEquals(
            listOf("Hello", ", world!"),
            flowOf("Hello".encodeToByteArray(), ", world!".encodeToByteArray())
                .asUtf8Stream()
                .toList(),
        )
        assertEquals(
            listOf("👍"),
            flowOf(byteArrayOf(240.toByte(), 159.toByte(), 145.toByte(), 141.toByte()))
                .asUtf8Stream()
                .toList(),
        )
        assertEquals(
            listOf("", "👍"),
            flowOf(
                byteArrayOf(240.toByte(), 159.toByte()),
                byteArrayOf(145.toByte(), 141.toByte()),
            )
                .asUtf8Stream()
                .toList(),
        )
        assertEquals(
            listOf("", "👍👍"),
            flowOf(
                byteArrayOf(240.toByte(), 159.toByte()),
                byteArrayOf(145.toByte(), 141.toByte(), 240.toByte(), 159.toByte(), 145.toByte(), 141.toByte()),
            )
                .asUtf8Stream()
                .toList(),
        )
    }

    @Test
    fun invalidStreams() = runTest {
        val results = mutableListOf<String>()
        val err = assertFails {
            flowOf(byteArrayOf(240.toByte(), 159.toByte()))
                .asUtf8Stream()
                .collect { results.add(it) }
        }
        assertEquals(listOf(""), results)
        assertTrue(err is Utf8StreamError.Utf8, "expected Utf8 error, got $err")

        val results2 = mutableListOf<String>()
        val err2 = assertFails {
            flowOf(
                byteArrayOf(240.toByte(), 159.toByte()),
                byteArrayOf(145.toByte(), 141.toByte(), 240.toByte(), 159.toByte(), 145.toByte()),
            )
                .asUtf8Stream()
                .collect { results2.add(it) }
        }
        assertEquals(listOf("", "👍"), results2)
        assertTrue(err2 is Utf8StreamError.Utf8, "expected Utf8 error, got $err2")
    }
}
