package com.spt.learningmanage.service;

import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.spt.learningmanage.model.dto.knowledge.KnowledgeBackfillCreateRequest;
import com.spt.learningmanage.model.dto.knowledge.KnowledgeEventQueryRequest;
import com.spt.learningmanage.model.vo.knowledge.KnowledgeBackfillVO;
import com.spt.learningmanage.model.vo.knowledge.KnowledgeEventVO;
import com.spt.learningmanage.model.vo.knowledge.KnowledgeIndexStatusVO;

public interface KnowledgeAdminService {
    KnowledgeIndexStatusVO status();
    Page<KnowledgeEventVO> listEvents(KnowledgeEventQueryRequest request);
    boolean replayEvent(Long eventId);
    KnowledgeBackfillVO createBackfill(KnowledgeBackfillCreateRequest request);
    KnowledgeBackfillVO getBackfill(Long runId);
}
