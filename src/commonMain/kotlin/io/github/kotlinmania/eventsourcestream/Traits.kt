// port-lint: source traits.rs
package io.github.kotlinmania.eventsourcestream

import kotlinx.coroutines.flow.Flow

/**
 * Main entry point for creating [Event] streams.
 *
 * The Rust trait `Eventsource` was a generic extension on `Stream<Item = Result<B, E>>` where
 * `B: AsRef<[u8]>`. Kotlin's [Flow] reaches the same shape with bytes directly, so the trait
 * collapses into this single extension on `Flow<ByteArray>`.
 */
fun Flow<ByteArray>.eventsource(): EventStream = EventStream(this)
