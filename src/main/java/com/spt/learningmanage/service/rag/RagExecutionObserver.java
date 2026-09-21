package com.spt.learningmanage.service.rag;

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
}
