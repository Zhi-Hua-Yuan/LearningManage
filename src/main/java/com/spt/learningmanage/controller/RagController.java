package com.spt.learningmanage.controller;

import com.spt.learningmanage.common.BaseResponse;
import com.spt.learningmanage.common.ResultUtils;
import com.spt.learningmanage.exception.BusinessException;
import com.spt.learningmanage.model.dto.rag.RagAskRequest;
import com.spt.learningmanage.model.vo.rag.RagAnswerVO;
import com.spt.learningmanage.service.RagService;
import com.spt.learningmanage.service.rag.RagStreamingService;
import com.spt.learningmanage.utils.UserHolder;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.http.MediaType;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;
import org.springframework.beans.factory.annotation.Autowired;

@Tag(name = "AI RAG", description = "权限感知的项目知识问答")
@RestController
@RequestMapping("/ai/rag")
public class RagController {
    private final RagService ragService;
    private final RagStreamingService streamingService;

    @Autowired
    public RagController(RagService ragService, RagStreamingService streamingService) {
        this.ragService = ragService;
        this.streamingService = streamingService;
    }

    /** Compatibility constructor retained for focused controller tests. */
    public RagController(RagService ragService) {
        this(ragService, null);
    }

    @Operation(summary = "基于当前用户可访问的项目知识回答问题")
    @PostMapping("/ask")
    public BaseResponse<RagAnswerVO> ask(@Valid @RequestBody RagAskRequest request) {
        return ResultUtils.success(ragService.ask(request));
    }

    @Operation(summary = "以安全阶段事件流返回权限感知的项目知识回答")
    @PostMapping(value = "/ask/stream", produces = MediaType.TEXT_EVENT_STREAM_VALUE)
    public SseEmitter askStream(@Valid @RequestBody RagAskRequest request) {
        if (streamingService == null) {
            throw new BusinessException(com.spt.learningmanage.exception.ErrorCode.RAG_DEPENDENCY_UNAVAILABLE,
                    "RAG 流式服务未装配");
        }
        return streamingService.start(request, UserHolder.get());
    }

    @Operation(summary = "读取并重新校验一个 RAG 结果")
    @GetMapping("/result/{requestId}")
    public BaseResponse<RagAnswerVO> getResult(@PathVariable String requestId) {
        return ResultUtils.success(ragService.getResult(requestId));
    }
}
