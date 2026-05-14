// port-lint: source src/utf8_stream.rs
package io.github.kotlinmania.eventsourcestream

import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flow

/**
 * Decoded a [Flow] of byte chunks into a [Flow] of UTF-8 [String] chunks. Bytes that fall on a
 * partial UTF-8 sequence at the end of a chunk are buffered until the next chunk arrives; on
 * termination, any buffered bytes are decoded into the final emission, and an invalid trailing
 * sequence surfaces as [Utf8StreamError.Utf8].
 *
 * The downstream contract mirrors Rust's `Utf8Stream`:
 *   - Each successful upstream chunk produces one downstream emission, possibly empty.
 *   - Upstream errors are re-thrown as [Utf8StreamError.Transport].
 *   - Upon upstream completion with non-empty buffer, the buffer is decoded; if invalid,
 *     [Utf8StreamError.Utf8] is thrown.
 */
fun Flow<ByteArray>.asUtf8Stream(): Flow<String> = flow {
    val buffer = ArrayList<Byte>()
    var terminatedByError = false
    try {
        collect { chunk ->
            val combined = if (buffer.isEmpty()) chunk else combine(buffer, chunk)
            val validLen = validUtf8PrefixLength(combined)
            val prefix = combined.decodeToString(0, validLen)
            buffer.clear()
            for (i in validLen until combined.size) buffer.add(combined[i])
            emit(prefix)
        }
    } catch (t: CancellationException) {
        throw t
    } catch (t: Utf8StreamError) {
        terminatedByError = true
        throw t
    } catch (t: Throwable) {
        terminatedByError = true
        throw Utf8StreamError.Transport(t)
    }
    if (!terminatedByError && buffer.isNotEmpty()) {
        val remaining = buffer.toByteArray()
        val validLen = validUtf8PrefixLength(remaining)
        if (validLen != remaining.size) {
            throw Utf8StreamError.Utf8(remaining)
        }
        emit(remaining.decodeToString())
    }
}

private fun combine(buffer: List<Byte>, chunk: ByteArray): ByteArray {
    val out = ByteArray(buffer.size + chunk.size)
    for (i in buffer.indices) out[i] = buffer[i]
    chunk.copyInto(out, buffer.size)
    return out
}

/**
 * Returns the length of the longest UTF-8 valid prefix of [bytes]. Mirrors Rust's
 * `String::from_utf8(...).err().valid_up_to()` which both halts on invalid sequences and on
 * truncated multi-byte sequences at the end.
 */
internal fun validUtf8PrefixLength(bytes: ByteArray): Int {
    var i = 0
    while (i < bytes.size) {
        val b = bytes[i].toInt() and 0xFF
        val seqLen = when {
            b < 0x80 -> 1
            b < 0xC2 -> return i
            b < 0xE0 -> 2
            b < 0xF0 -> 3
            b < 0xF8 -> 4
            else -> return i
        }
        if (i + seqLen > bytes.size) return i
        for (j in 1 until seqLen) {
            val cb = bytes[i + j].toInt() and 0xFF
            if (cb < 0x80 || cb >= 0xC0) return i
        }
        i += seqLen
    }
    return i
}

/** Errors emitted by [asUtf8Stream]. */
sealed class Utf8StreamError(message: String, cause: Throwable? = null) : Throwable(message, cause) {
    /** Source stream is not valid UTF-8. */
    class Utf8(val bytes: ByteArray) :
        Utf8StreamError("invalid UTF-8 in stream") {
        override fun equals(other: Any?): Boolean =
            other is Utf8 && bytes.contentEquals(other.bytes)

        override fun hashCode(): Int = bytes.contentHashCode()
    }

    /** Underlying source stream error. */
    class Transport(cause: Throwable) :
        Utf8StreamError(cause.message ?: "transport error", cause)
}
