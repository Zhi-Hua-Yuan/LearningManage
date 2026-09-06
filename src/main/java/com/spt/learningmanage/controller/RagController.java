package com.spt.learningmanage.controller;

import com.spt.learningmanage.common.BaseResponse;
import com.spt.learningmanage.common.ResultUtils;
import com.spt.learningmanage.model.dto.rag.RagAskRequest;
import com.spt.learningmanage.model.vo.rag.RagAnswerVO;
import com.spt.learningmanage.service.RagService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@Tag(name = "AI RAG", description = "权限感知的项目知识问答")
@RestController
@RequestMapping("/ai/rag")
public class RagController {
    private final RagService ragService;

    public RagController(RagService ragService) {
        this.ragService = ragService;
    }

    @Operation(summary = "基于当前用户可访问的项目知识回答问题")
    @PostMapping("/ask")
    public BaseResponse<RagAnswerVO> ask(@Valid @RequestBody RagAskRequest request) {
        return ResultUtils.success(ragService.ask(request));
    }

    @Operation(summary = "读取并重新校验一个 RAG 结果")
    @GetMapping("/result/{requestId}")
    public BaseResponse<RagAnswerVO> getResult(@PathVariable String requestId) {
        return ResultUtils.success(ragService.getResult(requestId));
    }
}
