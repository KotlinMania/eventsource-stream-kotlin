import Testing
import EventsourceStream

@Suite("EventsourceStream Swift Export Tests")
struct EventsourceStreamExportTests {
    @Test("Event defaults, mutation, and clone work across Swift Export")
    func eventCreationAndClone() {
        let event = Event(event: "update", data: "payload", id: "42", retry: nil)
        #expect(event.event == "update")
        #expect(event.data == "payload")
        #expect(event.id == "42")

        let cloned = event.clone()
        cloned.data = "mutated"
        #expect(event.data == "payload")
        #expect(cloned.data == "mutated")
    }

    @Test("SSE parser line combinators and character predicates behave accurately")
    func parserLineAndCharacterPredicates() {
        #expect(isLf(c: 0x0A))
        #expect(isCr(c: 0x0D))
        #expect(isSpace(c: 0x20))
        #expect(isColon(c: 0x3A))
        #expect(isBom(c: 0xFEFF))
        #expect(isNameChar(c: 0x61)) // 'a'
        #expect(!isNameChar(c: 0x3A)) // ':'
        #expect(isAnyChar(c: 0x3A)) // ':'

        let fieldResult = line(input: "event: message\ndata: hello\n\n")
        if let ok = fieldResult as? LineParseResult.Ok {
            #expect(ok.remaining == "data: hello\n\n")
            if let field = ok.line as? RawEventLine.Field {
                #expect(field.name == "event")
                #expect(field.value == "message")
            } else {
                Issue.record("Expected RawEventLine.Field")
            }
        } else {
            Issue.record("Expected LineParseResult.Ok")
        }

        let commentResult = line(input: ": heartbeat\r\n")
        if let ok = commentResult as? LineParseResult.Ok, let comment = ok.line as? RawEventLine.Comment {
            #expect(comment.text == " heartbeat")
            #expect(ok.remaining == "")
        } else {
            Issue.record("Expected RawEventLine.Comment")
        }

        #expect(line(input: "data: partial") is LineParseResult.Incomplete)
    }

    @Test("EventStreamState transitions and EventStreamError payloads bridge cleanly")
    func streamStateAndErrors() {
        #expect(!EventStreamState.NotStarted.isStarted())
        #expect(!EventStreamState.NotStarted.isTerminated())
        #expect(EventStreamState.Started.isStarted())
        #expect(EventStreamState.Terminated.isTerminated())

        let parserErr = EventStreamError.Parser(input: "bad")
        #expect(parserErr.input == "bad")
        #expect(EVENTSOURCE_STREAM_VERSION == "0.2.3")
    }
}
