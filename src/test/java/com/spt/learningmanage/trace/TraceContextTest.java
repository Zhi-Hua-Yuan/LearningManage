package com.spt.learningmanage.trace;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.slf4j.MDC;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class TraceContextTest {

    private static final String CLIENT_TRACE = "s3_TO-REG-019_a7a49946621b";
    private static final String TELEMETRY_TRACE = "3d62c4a7f0076d5bc008176ce056448c";

    @AfterEach
    void tearDown() {
        TraceContext.clear();
        MDC.clear();
    }

    @Test
    void explicitValueWinsOverBoundValue() {
        TraceContext.bind(CLIENT_TRACE);

        assertEquals("explicit-trace-9", TraceContext.explicitOrCurrent("explicit-trace-9"));
    }

    @Test
    void explicitValueWinsOverTelemetryClobberedMdc() {
        MDC.put(TraceContext.MDC_KEY, TELEMETRY_TRACE);

        assertEquals(CLIENT_TRACE, TraceContext.explicitOrCurrent(CLIENT_TRACE));
    }

    @Test
    void boundValueWinsOverTelemetryClobberedMdc() {
        TraceContext.bind(CLIENT_TRACE);
        MDC.put(TraceContext.MDC_KEY, TELEMETRY_TRACE);

        assertEquals(CLIENT_TRACE, TraceContext.explicitOrCurrent(null));
        assertEquals(CLIENT_TRACE, TraceContext.currentOrCreate());
    }

    @Test
    void invalidExplicitValueFallsBackToBoundValue() {
        TraceContext.bind(CLIENT_TRACE);
        MDC.put(TraceContext.MDC_KEY, TELEMETRY_TRACE);

        assertEquals(CLIENT_TRACE, TraceContext.explicitOrCurrent("bad trace id with spaces"));
    }

    @Test
    void fallsBackToMdcWhenNothingIsBound() {
        MDC.put(TraceContext.MDC_KEY, "mdc_trace-abc");

        assertEquals("mdc_trace-abc", TraceContext.currentOrCreate());
        assertEquals("mdc_trace-abc", TraceContext.explicitOrCurrent(null));
    }

    @Test
    void invalidMdcValueIsReplacedByGeneratedTrace() {
        MDC.put(TraceContext.MDC_KEY, "not a valid trace id");

        assertTrue(TraceContext.currentOrCreate().matches("[a-f0-9]{32}"));
    }

    @Test
    void generatesTraceWhenNeitherBindingNorMdcIsAvailable() {
        assertTrue(TraceContext.currentOrCreate().matches("[a-f0-9]{32}"));
        assertTrue(TraceContext.explicitOrCurrent(null).matches("[a-f0-9]{32}"));
    }

    @Test
    void clearRemovesBindingSoLaterReadsCannotReuseIt() {
        TraceContext.bind(CLIENT_TRACE);
        TraceContext.clear();

        assertTrue(TraceContext.currentOrCreate().matches("[a-f0-9]{32}"));
    }

    @Test
    void bindWithBlankValueRemovesPreviousBinding() {
        TraceContext.bind(CLIENT_TRACE);
        TraceContext.bind("  ");

        assertTrue(TraceContext.currentOrCreate().matches("[a-f0-9]{32}"));
    }
}
