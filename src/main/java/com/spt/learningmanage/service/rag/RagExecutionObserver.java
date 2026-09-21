package com.spt.learningmanage.service.rag;

import java.util.function.Supplier;

/** Receives internal RAG progress without exposing model tokens or evidence text. */
public interface RagExecutionObserver {
    RagExecutionObserver NOOP = new RagExecutionObserver() {
        @Override
        public void onStage(String stage, int attempt) { }

        @Override
        public boolean isCancelled() {
            return false;
        }
    };

    void onStage(String stage, int attempt);

    boolean isCancelled();

    default void checkCancelled() {
        if (isCancelled()) {
            throw new RagStreamCancelledException();
        }
    }

    /**
     * Runs result persistence with a cancellation checkpoint registered by the
     * persistence transaction. The default keeps synchronous callers unchanged.
     */
    default <T> T executePersistence(Supplier<T> action) {
        checkCancelled();
        return action.get();
    }
}
