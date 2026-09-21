package com.spt.learningmanage.service.rag;

import java.util.concurrent.atomic.AtomicBoolean;

/** Cancellation state shared by the HTTP lifecycle and the RAG worker. */
public final class RagCancellationToken {
    private final AtomicBoolean cancelled = new AtomicBoolean(false);

    public void cancel() {
        cancelled.set(true);
    }

    public boolean isCancelled() {
        return cancelled.get();
    }
}
