package com.spt.learningmanage.service;

import com.spt.learningmanage.model.vo.ops.DependencyStatusVO;

import java.util.Map;

public interface AiDependencyHealthService {
    Map<String, DependencyStatusVO> snapshot();

    DependencyStatusVO status(String dependency);

    void refresh();
}
