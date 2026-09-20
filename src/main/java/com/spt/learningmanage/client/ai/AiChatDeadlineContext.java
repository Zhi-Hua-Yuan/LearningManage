package com.spt.learningmanage.client.ai;

/**
 * Carries the governance deadline into the synchronous Spring AI HTTP request
 * factory.  The transport remains unaware of fallback or retry decisions; it
 * only uses the current absolute deadline to cap socket timeouts for the one
 * attempt that is already being executed by the governance layer.
 */
public final class AiChatDeadlineContext {

    private static final ThreadLocal<Long> DEADLINE_NANOS = new ThreadLocal<>();

    private AiChatDeadlineContext() {
    }

    public static Scope open(long deadlineNanos) {
        Long previous = DEADLINE_NANOS.get();
        DEADLINE_NANOS.set(deadlineNanos);
        return () -> {
            if (previous == null) {
                DEADLINE_NANOS.remove();
            } else {
                DEADLINE_NANOS.set(previous);
            }
        };
    }

    public static Long currentDeadlineNanos() {
        return DEADLINE_NANOS.get();
    }

    @FunctionalInterface
    public interface Scope extends AutoCloseable {

        @Override
        void close();
    }
}
