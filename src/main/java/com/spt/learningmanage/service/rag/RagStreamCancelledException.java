package com.spt.learningmanage.service.rag;

/** Internal control-flow signal used when an SSE client disconnects. */
public class RagStreamCancelledException extends RuntimeException {
    public RagStreamCancelledException() {
        super("RAG stream cancelled by client");
    }
}
