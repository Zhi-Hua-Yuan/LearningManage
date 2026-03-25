package com.spt.learningmanage.service;

import com.spt.learningmanage.model.dto.milestone.MilestoneCreateRequest;
import com.spt.learningmanage.model.dto.milestone.MilestoneQueryRequest;
import com.spt.learningmanage.model.dto.milestone.MilestoneUpdateRequest;
import com.spt.learningmanage.model.vo.MilestoneVo;

import java.util.List;

public interface MilestoneService {
    /**
     * 创建里程碑，返回里程碑ID。
     */
    Long create(MilestoneCreateRequest request);

    /**
     * 查询里程碑列表。
     */
    List<MilestoneVo> list(MilestoneQueryRequest request);

    /**
     * 更新里程碑。
     */
    void update(MilestoneUpdateRequest request);

    /**
     * 删除里程碑。
     */
    void delete(Long id);
}

