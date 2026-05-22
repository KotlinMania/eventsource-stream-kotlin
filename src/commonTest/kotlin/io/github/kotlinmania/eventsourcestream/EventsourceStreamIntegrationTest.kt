// port-lint: source tests/eventsource-stream.rs
package io.github.kotlinmania.eventsourcestream

import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.toList
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.time.Duration.Companion.milliseconds

class EventsourceStreamIntegrationTest {

    @Test
    fun populateFields() = runTest {
        val body =
            "\n" +
                "\n" +
                ":\n" +
                "\n" +
                "event: my-event\r\ndata:line1\n" +
                "data: line2\n" +
                ":\n" +
                "id: my-id\n" +
                ":should be ignored too\rretry:42\n" +
                "\n" +
                "data:second\n" +
                "\n" +
                "data:ignored\n"

        val events = EventStream(flowOf(body.encodeToByteArray())).toList()

        assertEquals(2, events.size)

        val first = events[0]
        assertEquals("my-event", first.event)
        assertEquals(
            "line1\n" +
                "line2",
            first.data,
        )
        assertEquals("my-id", first.id)
        assertNotNull(first.retry)
        assertEquals(42.milliseconds, first.retry)

        val second = events[1]
        assertEquals("message", second.event)
        assertEquals("second", second.data)

        // Trailing "data:ignored\n" has no terminating blank line, so no third event.
        assertNull(events.getOrNull(2))
    }
}
