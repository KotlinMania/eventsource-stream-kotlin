// port-lint: source parser.rs
package io.github.kotlinmania.eventsourcestream

/**
 * ABNF definition from HTML spec
 *
 * stream        = [ bom ] *event
 * event         = *( comment / field ) end-of-line
 * comment       = colon *any-char end-of-line
 * field         = 1*name-char [ colon [ space ] *any-char ] end-of-line
 * end-of-line   = ( cr lf / cr / lf )
 *
 * characters
 * lf            = %x000A ; U+000A LINE FEED (LF)
 * cr            = %x000D ; U+000D CARRIAGE RETURN (CR)
 * space         = %x0020 ; U+0020 SPACE
 * colon         = %x003A ; U+003A COLON (:)
 * bom           = %xFEFF ; U+FEFF BYTE ORDER MARK
 * name-char     = %x0000-0009 / %x000B-000C / %x000E-0039 / %x003B-10FFFF
 *                 ; a scalar value other than U+000A LINE FEED (LF), U+000D CARRIAGE RETURN (CR), or U+003A COLON (:)
 * any-char      = %x0000-0009 / %x000B-000C / %x000E-10FFFF
 *                 ; a scalar value other than U+000A LINE FEED (LF) or U+000D CARRIAGE RETURN (CR)
 */

sealed class RawEventLine {
    data class Comment(val text: String) : RawEventLine()
    data class Field(val name: String, val value: String?) : RawEventLine()
    data object Empty : RawEventLine()
}

internal sealed class LineParseResult {
    data class Ok(val remaining: String, val line: RawEventLine) : LineParseResult()
    data object Incomplete : LineParseResult()
    data class Error(val input: String) : LineParseResult()
}

fun isLf(c: Char): Boolean = c.code == 0x000A

fun isCr(c: Char): Boolean = c.code == 0x000D

fun isSpace(c: Char): Boolean = c.code == 0x0020

fun isColon(c: Char): Boolean = c.code == 0x003A

fun isBom(c: Char): Boolean = c.code == 0xFEFF

fun isNameChar(c: Char): Boolean {
    val n = c.code
    return n in 0x0000..0x0009 ||
        n in 0x000B..0x000C ||
        n in 0x000E..0x0039 ||
        n >= 0x003B
}

fun isAnyChar(c: Char): Boolean {
    val n = c.code
    return n in 0x0000..0x0009 ||
        n in 0x000B..0x000C ||
        n >= 0x000E
}

private sealed class TakeResult {
    data class Ok(val remaining: String) : TakeResult()
    data object Incomplete : TakeResult()
    data object Error : TakeResult()
}

/** Streaming `tag("\r\n")` — matches the two-byte CRLF terminator. */
private fun crlf(input: String): TakeResult {
    if (input.isEmpty()) return TakeResult.Incomplete
    if (input[0].code != 0x000D) return TakeResult.Error
    if (input.length < 2) return TakeResult.Incomplete
    if (input[1].code != 0x000A) return TakeResult.Error
    return TakeResult.Ok(input.substring(2))
}

/** Streaming combinator that consumes exactly one character matching [predicate]. */
private fun takeOne(input: String, predicate: (Char) -> Boolean): TakeResult {
    if (input.isEmpty()) return TakeResult.Incomplete
    if (!predicate(input[0])) return TakeResult.Error
    return TakeResult.Ok(input.substring(1))
}

/** Streaming combinator: matches CRLF, a bare CR, or a bare LF, trying in that order. */
private fun endOfLine(input: String): TakeResult {
    when (val r = crlf(input)) {
        is TakeResult.Ok -> return r
        is TakeResult.Incomplete -> return TakeResult.Incomplete
        is TakeResult.Error -> {}
    }
    when (val r = takeOne(input, ::isCr)) {
        is TakeResult.Ok -> return r
        is TakeResult.Incomplete -> return TakeResult.Incomplete
        is TakeResult.Error -> {}
    }
    return takeOne(input, ::isLf)
}

private sealed class TakeSpan {
    data class Ok(val taken: String, val remaining: String) : TakeSpan()
    data object Incomplete : TakeSpan()
    data object Empty : TakeSpan()
}

/**
 * Streaming combinator: takes zero or more characters matching [predicate]. If input runs out
 * while still matching, yields [TakeSpan.Incomplete] (more data may extend the match). If the
 * very first character fails [predicate], yields [TakeSpan.Empty] with an empty taken span.
 */
private fun takeWhileStreaming(input: String, predicate: (Char) -> Boolean): TakeSpan {
    var i = 0
    while (i < input.length && predicate(input[i])) {
        i += 1
    }
    if (i == input.length) return TakeSpan.Incomplete
    if (i == 0) return TakeSpan.Empty
    return TakeSpan.Ok(input.substring(0, i), input.substring(i))
}

private sealed class TakeOneOrMore {
    data class Ok(val taken: String, val remaining: String) : TakeOneOrMore()
    data object Incomplete : TakeOneOrMore()
    data object Error : TakeOneOrMore()
}

/**
 * Streaming combinator: needs at least one character matching [predicate]. If input runs out
 * while still matching, yields Incomplete. If the very first character fails, yields Error.
 */
private fun takeWhile1Streaming(input: String, predicate: (Char) -> Boolean): TakeOneOrMore {
    var i = 0
    while (i < input.length && predicate(input[i])) {
        i += 1
    }
    if (i == input.length) return TakeOneOrMore.Incomplete
    if (i == 0) return TakeOneOrMore.Error
    return TakeOneOrMore.Ok(input.substring(0, i), input.substring(i))
}

private fun comment(input: String): LineParseResult {
    val afterColon = when (val r = takeOne(input, ::isColon)) {
        is TakeResult.Ok -> r.remaining
        is TakeResult.Incomplete -> return LineParseResult.Incomplete
        is TakeResult.Error -> return LineParseResult.Error(input)
    }
    val (taken, afterAny) = when (val r = takeWhileStreaming(afterColon, ::isAnyChar)) {
        is TakeSpan.Ok -> r.taken to r.remaining
        TakeSpan.Empty -> "" to afterColon
        TakeSpan.Incomplete -> return LineParseResult.Incomplete
    }
    return when (val eol = endOfLine(afterAny)) {
        is TakeResult.Ok -> LineParseResult.Ok(eol.remaining, RawEventLine.Comment(taken))
        is TakeResult.Incomplete -> LineParseResult.Incomplete
        is TakeResult.Error -> LineParseResult.Error(afterAny)
    }
}

private fun field(input: String): LineParseResult {
    val (name, afterName) = when (val r = takeWhile1Streaming(input, ::isNameChar)) {
        is TakeOneOrMore.Ok -> r.taken to r.remaining
        TakeOneOrMore.Incomplete -> return LineParseResult.Incomplete
        TakeOneOrMore.Error -> return LineParseResult.Error(input)
    }
    var rest = afterName
    var value: String? = null
    when (val r = takeOne(rest, ::isColon)) {
        is TakeResult.Ok -> {
            rest = r.remaining
            val afterSpace = when (val s = takeOne(rest, ::isSpace)) {
                is TakeResult.Ok -> s.remaining
                is TakeResult.Incomplete -> return LineParseResult.Incomplete
                is TakeResult.Error -> rest
            }
            val (taken, afterAny) = when (val anyR = takeWhileStreaming(afterSpace, ::isAnyChar)) {
                is TakeSpan.Ok -> anyR.taken to anyR.remaining
                TakeSpan.Empty -> "" to afterSpace
                TakeSpan.Incomplete -> return LineParseResult.Incomplete
            }
            value = taken
            rest = afterAny
        }
        is TakeResult.Incomplete -> return LineParseResult.Incomplete
        is TakeResult.Error -> {
            // No colon; the entire token is just a bare field name.
        }
    }
    return when (val eol = endOfLine(rest)) {
        is TakeResult.Ok -> LineParseResult.Ok(eol.remaining, RawEventLine.Field(name, value))
        is TakeResult.Incomplete -> LineParseResult.Incomplete
        is TakeResult.Error -> LineParseResult.Error(rest)
    }
}

private fun empty(input: String): LineParseResult =
    when (val eol = endOfLine(input)) {
        is TakeResult.Ok -> LineParseResult.Ok(eol.remaining, RawEventLine.Empty)
        is TakeResult.Incomplete -> LineParseResult.Incomplete
        is TakeResult.Error -> LineParseResult.Error(input)
    }

internal fun line(input: String): LineParseResult {
    when (val r = comment(input)) {
        is LineParseResult.Ok -> return r
        is LineParseResult.Incomplete -> return LineParseResult.Incomplete
        is LineParseResult.Error -> {}
    }
    when (val r = field(input)) {
        is LineParseResult.Ok -> return r
        is LineParseResult.Incomplete -> return LineParseResult.Incomplete
        is LineParseResult.Error -> {}
    }
    return empty(input)
}
