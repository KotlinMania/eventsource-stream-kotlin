#if canImport(Testing)
import Testing
import EventsourceStream

@Suite("EventsourceStream Swift Export Tests")
struct EventsourceStreamExportTests {
    @Test("Swift module loads")
    func testSwiftModuleLoads() {
        #expect(Bool(true), "EventsourceStream swift module imported cleanly")
    }
}
#elseif canImport(XCTest)
import XCTest
import EventsourceStream

final class EventsourceStreamExportTests: XCTestCase {
    func testSwiftModuleLoads() throws {
        XCTAssertTrue(true, "EventsourceStream swift module imported cleanly")
    }
}
#endif
