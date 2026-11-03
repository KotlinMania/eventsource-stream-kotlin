// port-lint: source src/lib.rs
package io.github.kotlinmania.eventsourcestream

/**
 * A basic building block for building an Eventsource from a [Flow] of byte array like objects.
 * To learn more about Server Sent Events (SSE) take a look at
 * [the MDN docs](https://developer.mozilla.org/en-US/docs/Web/API/Server-sent_events/Using_server-sent_events).
 *
 * # Example
 *
 * ```kotlin
 * val stream: Flow<Event> = httpClient
 *     .get("http://localhost:7020/notifications")
 *     .bodyAsBytesFlow()
 *     .eventsource()
 *
 * stream.collect { event ->
 *     println("received event[type=${event.event}]: ${event.data}")
 * }
 * ```
 * Re-exports from upstream `src/lib.rs` include [Event], [EventStream], [EventStreamError],
 * and the [eventsource] extension function.
 */
public const val EVENTSOURCE_STREAM_VERSION: String = "0.2.3"
