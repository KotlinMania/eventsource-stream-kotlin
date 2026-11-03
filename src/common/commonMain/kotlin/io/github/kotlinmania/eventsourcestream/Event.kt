// port-lint: source event.rs
package io.github.kotlinmania.eventsourcestream

import kotlin.time.Duration

/** An Event */
data class Event(
    /** The event name if given */
    var event: String = "",
    /** The event data */
    var data: String = "",
    /** The event id if given */
    var id: String = "",
    /** Retry duration if given */
    var retry: Duration? = null,
)
