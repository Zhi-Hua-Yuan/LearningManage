package com.spt.learningmanage.service.rag;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.spt.learningmanage.model.rag.RagCandidate;
import com.spt.learningmanage.model.rag.RagContext;
import org.springframework.stereotype.Service;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

@Service
public class RagContextAssembler {
    private final ObjectMapper objectMapper;

    public RagContextAssembler(ObjectMapper objectMapper) {
        this.objectMapper = objectMapper;
    }

    public RagContext assemble(String question, List<RagCandidate> candidates) {
        try {
            ObjectNode prompt = objectMapper.createObjectNode();
            prompt.put("question", question);
            ArrayNode evidenceArray = prompt.putArray("evidence");
            ObjectNode log = objectMapper.createObjectNode();
            ArrayNode logEvidence = log.putArray("evidence");
            Map<String, RagCandidate> evidence = new LinkedHashMap<>();
            for (int index = 0; index < candidates.size(); index++) {
                String citationId = "S" + (index + 1);
                RagCandidate candidate = candidates.get(index);
                evidence.put(citationId, candidate);

                ObjectNode item = evidenceArray.addObject();
                item.put("id", citationId);
                item.put("sourceType", candidate.sourceType().name());
                item.put("title", candidate.title());
                if (candidate.sourceUpdatedAt() != null) {
                    item.put("updatedAt", candidate.sourceUpdatedAt().toString());
                }
                item.put("content", candidate.text());

                ObjectNode logged = logEvidence.addObject();
                logged.put("id", citationId);
                logged.put("sourceType", candidate.sourceType().name());
                logged.put("sourceId", candidate.sourceId());
                logged.put("contentHash", candidate.contentHash());
                logged.put("chunkIndex", candidate.chunkIndex());
            }
            log.put("evidenceCount", candidates.size());
            return new RagContext(objectMapper.writeValueAsString(prompt),
                    objectMapper.writeValueAsString(log), evidence);
        } catch (Exception exception) {
            throw new IllegalStateException("Unable to assemble RAG context", exception);
        }
    }
}
