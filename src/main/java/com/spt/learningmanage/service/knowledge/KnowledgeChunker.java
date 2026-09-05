package com.spt.learningmanage.service.knowledge;

import com.spt.learningmanage.model.knowledge.KnowledgeChunk;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.List;

@Component
public class KnowledgeChunker {

    public static final String VERSION = "chunk-v1";
    public static final int MAX_CHARS = 1200;
    public static final int OVERLAP_CHARS = 150;

    public List<KnowledgeChunk> chunk(String repeatPrefix, String semanticBody) {
        String prefix = repeatPrefix == null ? "" : repeatPrefix.trim();
        String body = semanticBody == null ? "" : semanticBody.trim();
        if (body.isEmpty()) {
            return prefix.isEmpty() ? List.of() : List.of(new KnowledgeChunk(0, prefix));
        }
        int bodyLimit = Math.max(200, MAX_CHARS - (prefix.isEmpty() ? 0 : prefix.length() + 1));
        List<KnowledgeChunk> chunks = new ArrayList<>();
        int start = 0;
        while (start < body.length()) {
            int end = Math.min(body.length(), start + bodyLimit);
            if (end < body.length()) {
                end = preferredBoundary(body, start, end);
            }
            if (end <= start) {
                end = Math.min(body.length(), start + bodyLimit);
            }
            String piece = body.substring(start, end).trim();
            if (!piece.isEmpty()) {
                String text = prefix.isEmpty() ? piece : prefix + "\n" + piece;
                chunks.add(new KnowledgeChunk(chunks.size(), text));
            }
            if (end >= body.length()) {
                break;
            }
            start = Math.max(start + 1, end - Math.min(OVERLAP_CHARS, end - start - 1));
        }
        return List.copyOf(chunks);
    }

    private int preferredBoundary(String body, int start, int end) {
        int minimum = start + Math.max(1, (end - start) / 2);
        for (char boundary : new char[]{'\n', '。', '！', '？', '.', '!', '?'}) {
            int candidate = body.lastIndexOf(boundary, end - 1);
            if (candidate >= minimum) {
                return candidate + 1;
            }
        }
        return end;
    }
}
