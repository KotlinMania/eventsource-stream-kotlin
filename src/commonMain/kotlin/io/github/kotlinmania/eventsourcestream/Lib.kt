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
 *
 * Tracked Rust re-exports from `src/lib.rs`:
 *
 * ```rust
 * pub use event::Event;
 * pub use event_stream::{EventStream, EventStreamError};
 * pub use traits::Eventsource;
 * ```
 *
 * The Kotlin counterparts live in [Event], [EventStream], [EventStreamError], and the
 * [eventsource] extension function.
 */
