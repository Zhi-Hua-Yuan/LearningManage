package com.spt.learningmanage.service.impl;

import cn.hutool.core.util.StrUtil;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.conditions.update.LambdaUpdateWrapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.spt.learningmanage.constant.AiPromptCodeEnum;
import com.spt.learningmanage.constant.AiSceneEnum;
import com.spt.learningmanage.exception.BusinessException;
import com.spt.learningmanage.exception.ErrorCode;
import com.spt.learningmanage.mapper.PromptTemplateMapper;
import com.spt.learningmanage.model.dto.ai.PromptTemplateCreateVersionRequest;
import com.spt.learningmanage.model.dto.ai.PromptTemplateQueryRequest;
import com.spt.learningmanage.model.entity.PromptTemplate;
import com.spt.learningmanage.model.vo.ai.PromptTemplateDetailVO;
import com.spt.learningmanage.model.vo.ai.PromptTemplateVO;
import com.spt.learningmanage.service.PromptTemplateService;
import lombok.RequiredArgsConstructor;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.Objects;

@Service
@RequiredArgsConstructor
public class PromptTemplateServiceImpl implements PromptTemplateService {

    private static final int TEMPLATE_CODE_MAX_LENGTH = 64;
    private static final int SCENE_MAX_LENGTH = 64;
    private static final int TEMPLATE_NAME_MAX_LENGTH = 100;
    private static final int TEMPLATE_CONTENT_MAX_LENGTH = 20000;
    private static final int REMARK_MAX_LENGTH = 255;
    private static final long PAGE_SIZE_MAX = 100L;

    private final PromptTemplateMapper promptTemplateMapper;

    @Override
    public Page<PromptTemplateVO> page(PromptTemplateQueryRequest request) {
        PromptTemplateQueryRequest validRequest = request == null
                ? new PromptTemplateQueryRequest()
                : request;
        validateQueryRequest(validRequest);

        LambdaQueryWrapper<PromptTemplate> wrapper = new LambdaQueryWrapper<>();
        if (StrUtil.isNotBlank(validRequest.getTemplateCode())) {
            AiPromptCodeEnum promptCode = requirePromptCode(validRequest.getTemplateCode());
            wrapper.eq(PromptTemplate::getTemplateCode, promptCode.getCode());
        }
        if (StrUtil.isNotBlank(validRequest.getScene())) {
            wrapper.eq(PromptTemplate::getScene, validRequest.getScene().trim());
        }
        if (validRequest.getEnabled() != null) {
            wrapper.eq(PromptTemplate::getEnabled, validRequest.getEnabled());
        }
        wrapper.orderByAsc(PromptTemplate::getTemplateCode)
                .orderByDesc(PromptTemplate::getVersion);

        Page<PromptTemplate> entityPage = new Page<>(
                safePageNum(validRequest.getPageNum()),
                safePageSize(validRequest.getPageSize())
        );
        Page<PromptTemplate> resultPage = promptTemplateMapper.selectPage(entityPage, wrapper);
        Page<PromptTemplateVO> voPage = new Page<>(
                resultPage.getCurrent(),
                resultPage.getSize(),
                resultPage.getTotal()
        );
        voPage.setRecords(resultPage.getRecords().stream().map(this::toListVO).toList());
        return voPage;
    }

    @Override
    public PromptTemplateDetailVO getDetail(Long id) {
        validateId(id);
        PromptTemplate template = promptTemplateMapper.selectById(id);
        if (template == null) {
            throw new BusinessException(ErrorCode.NOT_FOUND_ERROR, "Prompt 模板版本不存在");
        }
        return toDetailVO(template);
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public Long createVersion(PromptTemplateCreateVersionRequest request) {
        validateCreateRequest(request);
        AiPromptCodeEnum promptCode = requirePromptCode(request.getTemplateCode());

        Integer latestVersion = promptTemplateMapper.selectLatestVersionForUpdate(promptCode.getCode());
        int nextVersion = latestVersion == null ? 1 : latestVersion + 1;

        PromptTemplate template = new PromptTemplate();
        template.setTemplateCode(promptCode.getCode());
        template.setScene(promptCode.getScene().getCode());
        template.setTemplateName(request.getTemplateName().trim());
        template.setTemplateContent(request.getTemplateContent().trim());
        template.setVersion(nextVersion);
        template.setEnabled(0);
        template.setRemark(StrUtil.isBlank(request.getRemark()) ? null : request.getRemark().trim());
        template.setIsDelete(0);

        try {
            int rows = promptTemplateMapper.insert(template);
            if (rows != 1 || template.getId() == null) {
                throw new BusinessException(ErrorCode.OPERATION_ERROR, "Prompt 模板版本创建失败");
            }
        } catch (DuplicateKeyException e) {
            throw new BusinessException(ErrorCode.OPERATION_ERROR, "Prompt 模板版本创建冲突，请重试");
        }
        return template.getId();
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public void activate(Long id) {
        validateId(id);
        PromptTemplate snapshot = promptTemplateMapper.selectById(id);
        if (snapshot == null) {
            throw new BusinessException(ErrorCode.NOT_FOUND_ERROR, "Prompt 模板版本不存在");
        }

        List<PromptTemplate> lockedVersions = promptTemplateMapper
                .selectVersionsForUpdate(snapshot.getTemplateCode());
        PromptTemplate target = findLockedVersion(lockedVersions, id);
        if (Objects.equals(target.getEnabled(), 1)) {
            return;
        }

        promptTemplateMapper.update(null, new LambdaUpdateWrapper<PromptTemplate>()
                .eq(PromptTemplate::getTemplateCode, target.getTemplateCode())
                .eq(PromptTemplate::getEnabled, 1)
                .set(PromptTemplate::getEnabled, 0));

        int rows = promptTemplateMapper.update(null, new LambdaUpdateWrapper<PromptTemplate>()
                .eq(PromptTemplate::getId, target.getId())
                .eq(PromptTemplate::getEnabled, 0)
                .set(PromptTemplate::getEnabled, 1));
        if (rows != 1) {
            throw new BusinessException(ErrorCode.OPERATION_ERROR, "Prompt 模板启用失败");
        }
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public void deleteDisabledVersion(Long id) {
        validateId(id);
        PromptTemplate snapshot = promptTemplateMapper.selectById(id);
        if (snapshot == null) {
            throw new BusinessException(ErrorCode.NOT_FOUND_ERROR, "Prompt 模板版本不存在");
        }

        List<PromptTemplate> lockedVersions = promptTemplateMapper
                .selectVersionsForUpdate(snapshot.getTemplateCode());
        PromptTemplate target = findLockedVersion(lockedVersions, id);
        if (Objects.equals(target.getEnabled(), 1)) {
            throw new BusinessException(ErrorCode.OPERATION_ERROR, "当前启用版本不能删除");
        }

        int rows = promptTemplateMapper.deleteById(target.getId());
        if (rows != 1) {
            throw new BusinessException(ErrorCode.OPERATION_ERROR, "Prompt 模板版本删除失败");
        }
    }

    private PromptTemplate findLockedVersion(List<PromptTemplate> versions, Long id) {
        return versions.stream()
                .filter(item -> Objects.equals(item.getId(), id))
                .findFirst()
                .orElseThrow(() -> new BusinessException(
                        ErrorCode.NOT_FOUND_ERROR,
                        "Prompt 模板版本不存在"
                ));
    }

    private void validateCreateRequest(PromptTemplateCreateVersionRequest request) {
        if (request == null) {
            throw new BusinessException(ErrorCode.PARAMS_ERROR, "创建版本请求不能为空");
        }
        if (StrUtil.isBlank(request.getTemplateCode())) {
            throw new BusinessException(ErrorCode.PARAMS_ERROR, "模板编码不能为空");
        }
        if (request.getTemplateCode().trim().length() > TEMPLATE_CODE_MAX_LENGTH) {
            throw new BusinessException(ErrorCode.PARAMS_ERROR, "模板编码长度不能超过64个字符");
        }
        if (StrUtil.isBlank(request.getTemplateName())) {
            throw new BusinessException(ErrorCode.PARAMS_ERROR, "模板名称不能为空");
        }
        if (request.getTemplateName().trim().length() > TEMPLATE_NAME_MAX_LENGTH) {
            throw new BusinessException(ErrorCode.PARAMS_ERROR, "模板名称长度不能超过100个字符");
        }
        if (StrUtil.isBlank(request.getTemplateContent())) {
            throw new BusinessException(ErrorCode.PARAMS_ERROR, "模板内容不能为空");
        }
        if (request.getTemplateContent().trim().length() > TEMPLATE_CONTENT_MAX_LENGTH) {
            throw new BusinessException(ErrorCode.PARAMS_ERROR, "模板内容长度不能超过20000个字符");
        }
        if (StrUtil.isNotBlank(request.getRemark())
                && request.getRemark().trim().length() > REMARK_MAX_LENGTH) {
            throw new BusinessException(ErrorCode.PARAMS_ERROR, "版本说明长度不能超过255个字符");
        }
    }

    private void validateQueryRequest(PromptTemplateQueryRequest request) {
        if (StrUtil.isNotBlank(request.getTemplateCode())) {
            if (request.getTemplateCode().trim().length() > TEMPLATE_CODE_MAX_LENGTH) {
                throw new BusinessException(ErrorCode.PARAMS_ERROR, "模板编码长度不能超过64个字符");
            }
            requirePromptCode(request.getTemplateCode());
        }
        if (StrUtil.isNotBlank(request.getScene())) {
            String scene = request.getScene().trim();
            if (scene.length() > SCENE_MAX_LENGTH || !isSupportedScene(scene)) {
                throw new BusinessException(ErrorCode.PARAMS_ERROR, "不支持的 AI 场景：" + scene);
            }
        }
        if (request.getEnabled() != null
                && !Objects.equals(request.getEnabled(), 0)
                && !Objects.equals(request.getEnabled(), 1)) {
            throw new BusinessException(ErrorCode.PARAMS_ERROR, "enabled 只能是0或1");
        }
    }

    private AiPromptCodeEnum requirePromptCode(String code) {
        AiPromptCodeEnum promptCode = AiPromptCodeEnum.fromCode(code);
        if (promptCode == null) {
            throw new BusinessException(ErrorCode.PARAMS_ERROR, "不支持的 Prompt 模板编码：" + code);
        }
        return promptCode;
    }

    private boolean isSupportedScene(String scene) {
        for (AiSceneEnum value : AiSceneEnum.values()) {
            if (StrUtil.equals(value.getCode(), scene)) {
                return true;
            }
        }
        return false;
    }

    private void validateId(Long id) {
        if (id == null || id <= 0) {
            throw new BusinessException(ErrorCode.PARAMS_ERROR, "模板版本 ID 不合法");
        }
    }

    private long safePageNum(Long pageNum) {
        return pageNum == null || pageNum < 1 ? 1L : pageNum;
    }

    private long safePageSize(Long pageSize) {
        if (pageSize == null || pageSize < 1) {
            return 10L;
        }
        return Math.min(pageSize, PAGE_SIZE_MAX);
    }

    private PromptTemplateVO toListVO(PromptTemplate template) {
        PromptTemplateVO vo = new PromptTemplateVO();
        vo.setId(template.getId());
        vo.setTemplateCode(template.getTemplateCode());
        vo.setScene(template.getScene());
        vo.setTemplateName(template.getTemplateName());
        vo.setVersion(template.getVersion());
        vo.setEnabled(template.getEnabled());
        vo.setRemark(template.getRemark());
        vo.setCreateTime(template.getCreateTime());
        vo.setUpdateTime(template.getUpdateTime());
        return vo;
    }

    private PromptTemplateDetailVO toDetailVO(PromptTemplate template) {
        PromptTemplateDetailVO vo = new PromptTemplateDetailVO();
        vo.setId(template.getId());
        vo.setTemplateCode(template.getTemplateCode());
        vo.setScene(template.getScene());
        vo.setTemplateName(template.getTemplateName());
        vo.setTemplateContent(template.getTemplateContent());
        vo.setVersion(template.getVersion());
        vo.setEnabled(template.getEnabled());
        vo.setRemark(template.getRemark());
        vo.setCreateTime(template.getCreateTime());
        vo.setUpdateTime(template.getUpdateTime());
        return vo;
    }
}
