package com.spt.learningmanage.service.rag;

import com.spt.learningmanage.model.rag.RagAnswerContent;
import com.spt.learningmanage.model.rag.RagCandidate;
import org.springframework.stereotype.Service;

import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

@Service
public class RagCitationValidator {
    private static final Pattern MARKER = Pattern.compile("\\[(S[1-9][0-9]*)]");
    private static final Pattern CITATION_ID = Pattern.compile("S[1-9][0-9]*");
    private static final int MAX_ANSWER_CHARS = 6000;

    public RagAnswerContent validate(RagAnswerContent answer,
                                     Map<String, RagCandidate> evidence) {
        if (answer == null || answer.answer() == null || answer.answer().isBlank()
                || answer.answer().length() > MAX_ANSWER_CHARS) {
            throw new IllegalArgumentException("RAG answer is blank or too long");
        }
        Set<String> declared = new LinkedHashSet<>();
        for (String citation : answer.citations()) {
            if (citation == null || !CITATION_ID.matcher(citation).matches()
                    || !evidence.containsKey(citation) || !declared.add(citation)) {
                throw new IllegalArgumentException("RAG answer declares an invalid citation");
            }
        }
        Set<String> markers = new LinkedHashSet<>();
        Matcher matcher = MARKER.matcher(answer.answer());
        while (matcher.find()) {
            String citation = matcher.group(1);
            if (!evidence.containsKey(citation)) {
                throw new IllegalArgumentException("RAG answer contains an unknown citation marker");
            }
            markers.add(citation);
        }
        if (!markers.equals(declared)) {
            throw new IllegalArgumentException("RAG citation markers and declaration differ");
        }
        if (!answer.insufficientEvidence() && declared.isEmpty()) {
            throw new IllegalArgumentException("Supported RAG answer must contain citations");
        }
        return new RagAnswerContent(answer.answer().trim(), answer.insufficientEvidence(),
                List.copyOf(declared));
    }
}
