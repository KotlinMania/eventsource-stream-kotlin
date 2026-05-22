// port-lint: source event_stream.rs
package io.github.kotlinmania.eventsourcestream

import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.toList
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertEquals

class EventStreamTest {

    private fun events(vararg chunks: String) =
        EventStream(flowOf(*chunks.map { it.encodeToByteArray() }.toTypedArray()))

    @Test
    fun validDataFields() = runTest {
        assertEquals(
            listOf(Event(event = "message", data = "Hello, world!")),
            events("data: Hello, world!\n\n").toList(),
        )
        assertEquals(
            listOf(Event(event = "message", data = "Hello, world!")),
            events("data: Hello,", " world!\n\n").toList(),
        )
        assertEquals(
            listOf(Event(event = "message", data = "Hello, world!")),
            events("data: Hello,", "", " world!\n\n").toList(),
        )
        assertEquals(
            emptyList(),
            events("data: Hello, world!\n").toList(),
        )
        assertEquals(
            listOf(Event(event = "message", data = "Hello,\nworld!")),
            events("data: Hello,\ndata: world!\n\n").toList(),
        )
        assertEquals(
            listOf(
                Event(event = "message", data = "Hello,"),
                Event(event = "message", data = "world!"),
            ),
            events("data: Hello,\n\ndata: world!\n\n").toList(),
        )
    }

    @Test
    fun specExamples() = runTest {
        assertEquals(
            listOf(
                Event(event = "message", data = "This is the first message."),
                Event(event = "message", data = "This is the second message, it\nhas two lines."),
                Event(event = "message", data = "This is the third message."),
            ),
            events(
                "data: This is the first message.\n" +
                    "\n" +
                    "data: This is the second message, it\n" +
                    "data: has two lines.\n" +
                    "\n" +
                    "data: This is the third message.\n" +
                    "\n",
            ).toList(),
        )
        assertEquals(
            listOf(
                Event(event = "add", data = "73857293"),
                Event(event = "remove", data = "2153"),
                Event(event = "add", data = "113411"),
            ),
            events(
                "event: add\n" +
                    "data: 73857293\n" +
                    "\n" +
                    "event: remove\n" +
                    "data: 2153\n" +
                    "\n" +
                    "event: add\n" +
                    "data: 113411\n" +
                    "\n",
            ).toList(),
        )
        assertEquals(
            listOf(Event(event = "message", data = "YHOO\n+2\n10")),
            events(
                "data: YHOO\n" +
                    "data: +2\n" +
                    "data: 10\n" +
                    "\n",
            ).toList(),
        )
        assertEquals(
            listOf(
                Event(event = "message", id = "1", data = "first event"),
                Event(event = "message", data = "second event"),
                Event(event = "message", data = " third event"),
            ),
            events(
                ": test stream\n" +
                    "\n" +
                    "data: first event\n" +
                    "id: 1\n" +
                    "\n" +
                    "data:second event\n" +
                    "id\n" +
                    "\n" +
                    "data:  third event\n" +
                    "\n",
            ).toList(),
        )
        assertEquals(
            listOf(
                Event(event = "message", data = ""),
                Event(event = "message", data = "\n"),
            ),
            events(
                "data\n" +
                    "\n" +
                    "data\n" +
                    "data\n" +
                    "\n" +
                    "data:\n",
            ).toList(),
        )
        assertEquals(
            listOf(
                Event(event = "message", data = "test"),
                Event(event = "message", data = "test"),
            ),
            events(
                "data:test\n" +
                    "\n" +
                    "data: test\n" +
                    "\n",
            ).toList(),
        )
    }
}
