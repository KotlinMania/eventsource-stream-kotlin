// port-lint: source src/event_stream.rs
package io.github.kotlinmania.eventsourcestream

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertSame
import kotlin.test.assertTrue

class EventStreamErrorTest {

    @Test
    fun utf8DisplayMatchesUpstream() {
        val err = EventStreamError.Utf8(byteArrayOf(0xF0.toByte(), 0x9F.toByte()))
        assertTrue(
            err.message?.startsWith("UTF8 error: ") == true,
            "expected upstream-style \"UTF8 error: ...\" prefix, got: ${err.message}",
        )
    }

    @Test
    fun parserDisplayMatchesUpstream() {
        val err = EventStreamError.Parser("bad input")
        assertEquals("Parse error: bad input", err.message)
    }

    @Test
    fun transportDisplayMatchesUpstream() {
        val inner = RuntimeException("oops")
        val err = EventStreamError.Transport(inner)
        assertEquals("Transport error: oops", err.message)
        assertSame(inner, err.cause)
    }

    @Test
    fun convertFromUtf8StreamErrorPreservesBytes() {
        val bytes = byteArrayOf(0xF0.toByte(), 0x9F.toByte())
        val converted = EventStreamError.from(Utf8StreamError.Utf8(bytes))
        assertTrue(converted is EventStreamError.Utf8)
        assertTrue(bytes.contentEquals(converted.bytes))
    }

    @Test
    fun convertFromUtf8StreamErrorPreservesTransportCause() {
        val inner = RuntimeException("downstream failure")
        val converted = EventStreamError.from(Utf8StreamError.Transport(inner))
        assertTrue(converted is EventStreamError.Transport)
        assertSame(inner, converted.cause)
    }
}
