package com.spt.learningmanage.service;

import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.spt.learningmanage.model.dto.ai.PromptTemplateCreateVersionRequest;
import com.spt.learningmanage.model.dto.ai.PromptTemplateQueryRequest;
import com.spt.learningmanage.model.vo.ai.PromptTemplateDetailVO;
import com.spt.learningmanage.model.vo.ai.PromptTemplateVO;

public interface PromptTemplateService {

    Page<PromptTemplateVO> page(PromptTemplateQueryRequest request);

    PromptTemplateDetailVO getDetail(Long id);

    Long createVersion(PromptTemplateCreateVersionRequest request);

    void activate(Long id);

    void deleteDisabledVersion(Long id);
}
