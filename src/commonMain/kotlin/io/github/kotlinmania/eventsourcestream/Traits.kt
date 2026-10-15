// port-lint: source traits.rs
package io.github.kotlinmania.eventsourcestream

import kotlinx.coroutines.flow.Flow

/**
 * Main entry point for creating [Event] streams.
 * The upstream trait `Eventsource` was an extension on byte streams.
 * Kotlin's [Flow] reaches the same shape with bytes directly, so the trait
 * is provided via this extension on `Flow<ByteArray>`.
 */
fun Flow<ByteArray>.eventsource(): EventStream = EventStream(this)
