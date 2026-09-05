package com.spt.learningmanage.controller;

import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.spt.learningmanage.common.BaseResponse;
import com.spt.learningmanage.common.ResultUtils;
import com.spt.learningmanage.model.dto.knowledge.KnowledgeBackfillCreateRequest;
import com.spt.learningmanage.model.dto.knowledge.KnowledgeEventQueryRequest;
import com.spt.learningmanage.model.vo.knowledge.KnowledgeBackfillVO;
import com.spt.learningmanage.model.vo.knowledge.KnowledgeEventVO;
import com.spt.learningmanage.model.vo.knowledge.KnowledgeIndexStatusVO;
import com.spt.learningmanage.service.KnowledgeAdminService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@Tag(name = "Knowledge index operations")
@RestController
@RequestMapping("/admin/ai/knowledge")
public class KnowledgeAdminController {

    private final KnowledgeAdminService service;

    public KnowledgeAdminController(KnowledgeAdminService service) {
        this.service = service;
    }

    @Operation(summary = "Knowledge index status metadata")
    @GetMapping("/status")
    public BaseResponse<KnowledgeIndexStatusVO> status() {
        return ResultUtils.success(service.status());
    }

    @Operation(summary = "List sanitized knowledge index events")
    @GetMapping("/events")
    public BaseResponse<Page<KnowledgeEventVO>> events(
            @RequestParam(required = false) String status,
            @RequestParam(defaultValue = "1") Long current,
            @RequestParam(defaultValue = "20") Long size) {
        KnowledgeEventQueryRequest request = new KnowledgeEventQueryRequest();
        request.setStatus(status);
        request.setCurrent(current);
        request.setSize(size);
        return ResultUtils.success(service.listEvents(request));
    }

    @Operation(summary = "Replay one DEAD knowledge index event")
    @PostMapping("/events/{eventId}/replay")
    public BaseResponse<Boolean> replay(@PathVariable Long eventId) {
        return ResultUtils.success(service.replayEvent(eventId));
    }

    @Operation(summary = "Start an idempotent knowledge backfill")
    @PostMapping("/backfills")
    public BaseResponse<KnowledgeBackfillVO> createBackfill(
            @RequestBody KnowledgeBackfillCreateRequest request) {
        return ResultUtils.success(service.createBackfill(request));
    }

    @Operation(summary = "Get knowledge backfill metadata")
    @GetMapping("/backfills/{runId}")
    public BaseResponse<KnowledgeBackfillVO> getBackfill(@PathVariable Long runId) {
        return ResultUtils.success(service.getBackfill(runId));
    }
}
