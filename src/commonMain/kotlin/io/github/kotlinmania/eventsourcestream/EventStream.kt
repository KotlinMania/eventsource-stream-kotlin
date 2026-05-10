// port-lint: source src/event_stream.rs
package io.github.kotlinmania.eventsourcestream

import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.FlowCollector
import kotlin.time.Duration
import kotlin.time.Duration.Companion.milliseconds

private class EventBuilder {
    var event: Event = Event()
    var isComplete: Boolean = false

    /**
     * From the HTML spec
     *
     * -> If the field name is "event"
     *    Set the event type buffer to field value.
     *
     * -> If the field name is "data"
     *    Append the field value to the data buffer, then append a single U+000A LINE FEED (LF)
     *    character to the data buffer.
     *
     * -> If the field name is "id"
     *    If the field value does not contain U+0000 NULL, then set the last event ID buffer
     *    to the field value. Otherwise, ignore the field.
     *
     * -> If the field name is "retry"
     *    If the field value consists of only ASCII digits, then interpret the field value as
     *    an integer in base ten, and set the event stream's reconnection time to that integer.
     *    Otherwise, ignore the field.
     *
     * -> Otherwise
     *    The field is ignored.
     */
    fun add(line: RawEventLine) {
        when (line) {
            is RawEventLine.Field -> {
                val value = line.value ?: ""
                when (line.name) {
                    "event" -> {
                        event.event = value
                    }
                    "data" -> {
                        event.data = event.data + value + '\n'
                    }
                    "id" -> {
                        if (!value.contains(0x0000.toChar())) {
                            event.id = value
                        }
                    }
                    "retry" -> {
                        val parsed = value.toULongOrNull()
                        if (parsed != null) {
                            event.retry = parsed.toLong().milliseconds
                        }
                    }
                    else -> {}
                }
            }
            is RawEventLine.Comment -> {}
            RawEventLine.Empty -> { isComplete = true }
        }
    }

    /**
     * From the HTML spec
     *
     * 1. Set the last event ID string of the event source to the value of the last event ID
     * buffer. The buffer does not get reset, so the last event ID string of the event source
     * remains set to this value until the next time it is set by the server.
     * 2. If the data buffer is an empty string, set the data buffer and the event type buffer
     * to the empty string and return.
     * 3. If the data buffer's last character is a U+000A LINE FEED (LF) character, then remove
     * the last character from the data buffer.
     * 4. Let event be the result of creating an event using MessageEvent, in the relevant Realm
     * of the EventSource object.
     * 5. Initialize event's type attribute to message, its data attribute to data, its origin
     * attribute to the serialization of the origin of the event stream's final URL (i.e., the
     * URL after redirects), and its lastEventId attribute to the last event ID string of the
     * event source.
     * 6. If the event type buffer has a value other than the empty string, change the type of
     * the newly created event to equal the value of the event type buffer.
     * 7. Set the data buffer and the event type buffer to the empty string.
     * 8. Queue a task which, if the readyState attribute is set to a value other than CLOSED,
     * dispatches the newly created event at the EventSource object.
     */
    fun dispatch(): Event? {
        val finished = event
        val carriedId = finished.id
        event = Event()
        event.id = carriedId
        isComplete = false

        if (finished.data.isEmpty()) {
            return null
        }

        val lastChar = finished.data.last()
        if (isLf(lastChar)) {
            finished.data = finished.data.substring(0, finished.data.length - 1)
        }

        if (finished.event.isEmpty()) {
            finished.event = "message"
        }

        return finished
    }
}

enum class EventStreamState {
    NotStarted,
    Started,
    Terminated;

    fun isTerminated(): Boolean = this == Terminated
    fun isStarted(): Boolean = this == Started
}

/** A Flow of events. */
class EventStream(stream: Flow<ByteArray>) : Flow<Event> {
    private val source: Flow<String> = stream.asUtf8Stream()
    private val buffer = StringBuilder()
    private val builder = EventBuilder()
    private var state: EventStreamState = EventStreamState.NotStarted
    private var lastEventIdField: String = ""

    /**
     * Set the last event ID of the stream. Useful for initializing the stream with a previous
     * last event ID.
     */
    fun setLastEventId(id: String) {
        lastEventIdField = id
    }

    /** Get the last event ID of the stream. */
    fun lastEventId(): String = lastEventIdField

    override suspend fun collect(collector: FlowCollector<Event>) {
        // Drain any pending data already buffered (rare unless a previous collect was cancelled).
        parseAndEmit(collector)
        if (state.isTerminated()) return

        try {
            source.collect { string ->
                if (string.isEmpty()) return@collect

                val slice = if (state.isStarted()) {
                    string
                } else {
                    state = EventStreamState.Started
                    if (isBom(string[0])) string.substring(1) else string
                }
                buffer.append(slice)
                parseAndEmit(collector)
            }
            state = EventStreamState.Terminated
        } catch (e: Utf8StreamError) {
            throw EventStreamError.from(e)
        }
    }

    private suspend fun parseAndEmit(collector: FlowCollector<Event>) {
        if (buffer.isEmpty()) return
        while (true) {
            val text = buffer.toString()
            if (text.isEmpty()) return
            when (val parsed = line(text)) {
                is LineParseResult.Ok -> {
                    builder.add(parsed.line)
                    val consumed = text.length - parsed.remaining.length
                    buffer.deleteRange(0, consumed)
                    if (builder.isComplete) {
                        val event = builder.dispatch()
                        if (event != null) {
                            lastEventIdField = event.id
                            collector.emit(event)
                        }
                    }
                }
                LineParseResult.Incomplete -> return
                is LineParseResult.Error -> throw EventStreamError.Parser(parsed.input)
            }
        }
    }
}

/** Error thrown while parsing an event line. */
sealed class EventStreamError(message: String, cause: Throwable? = null) : Throwable(message, cause) {
    /** Source stream is not valid UTF-8. */
    class Utf8(val bytes: ByteArray) :
        EventStreamError("invalid UTF-8 in event stream") {
        override fun equals(other: Any?): Boolean =
            other is Utf8 && bytes.contentEquals(other.bytes)

        override fun hashCode(): Int = bytes.contentHashCode()
    }

    /** Source stream is not a valid EventStream. */
    class Parser(val input: String) :
        EventStreamError("Parse error: $input") {
        override fun equals(other: Any?): Boolean = other is Parser && input == other.input
        override fun hashCode(): Int = input.hashCode()
    }

    /** Underlying source stream error. */
    class Transport(cause: Throwable) :
        EventStreamError(cause.message ?: "transport error", cause) {
        override fun equals(other: Any?): Boolean =
            other is Transport && cause === other.cause
        override fun hashCode(): Int = cause?.hashCode() ?: 0
    }

    companion object {
        internal fun from(error: Utf8StreamError): EventStreamError = when (error) {
            is Utf8StreamError.Utf8 -> Utf8(error.bytes)
            is Utf8StreamError.Transport -> Transport(error.cause ?: error)
        }
    }
}
