package com.spt.learningmanage.prompt;

import cn.hutool.core.util.StrUtil;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.spt.learningmanage.constant.AiPromptCodeEnum;
import com.spt.learningmanage.constant.AiPromptSourceEnum;
import com.spt.learningmanage.exception.BusinessException;
import com.spt.learningmanage.exception.ErrorCode;
import com.spt.learningmanage.mapper.PromptTemplateMapper;
import com.spt.learningmanage.model.entity.PromptTemplate;
import lombok.RequiredArgsConstructor;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

import java.util.List;

/**
 * 优先解析数据库启用模板，异常或配置非法时回退到内置模板。
 */
@Component
@RequiredArgsConstructor
public class PromptTemplateResolver {

    private static final Logger log = LoggerFactory.getLogger(PromptTemplateResolver.class);

    private final PromptTemplateMapper promptTemplateMapper;

    private final DefaultAiPromptTemplateProvider defaultProvider;

    public AiPromptTemplate resolve(AiPromptCodeEnum promptCode) {
        if (promptCode == null) {
            throw new BusinessException(ErrorCode.PARAMS_ERROR, "Prompt 模板编码不能为空");
        }

        // 优先从数据库里获取提示词模版，如果有问题则获取兜底的默认提示词模版
        try {
            List<PromptTemplate> templates = promptTemplateMapper.selectList(
                    new LambdaQueryWrapper<PromptTemplate>()
                            .eq(PromptTemplate::getTemplateCode, promptCode.getCode())
                            .eq(PromptTemplate::getEnabled, 1)
                            .orderByDesc(PromptTemplate::getVersion)
            );
            if (templates.size() != 1) {
                log.warn("Prompt 启用版本数量异常，使用内置模板: code={}, count={}",
                        promptCode.getCode(), templates.size());
                return defaultProvider.getRequired(promptCode);
            }

            PromptTemplate template = templates.get(0);
            if (!isValid(template, promptCode)) {
                log.warn("Prompt 模板配置不合法，使用内置模板: code={}, id={}",
                        promptCode.getCode(), template.getId());
                return defaultProvider.getRequired(promptCode);
            }

            return new AiPromptTemplate(
                    template.getId(),
                    template.getTemplateCode(),
                    template.getScene(),
                    template.getVersion(),
                    AiPromptSourceEnum.DATABASE,
                    template.getTemplateContent().trim()
            );
        } catch (Exception e) {
            log.error("查询 Prompt 模板失败，使用内置模板: code={}", promptCode.getCode(), e);
            return defaultProvider.getRequired(promptCode);
        }
    }

    private boolean isValid(PromptTemplate template, AiPromptCodeEnum promptCode) {
        return template != null
                && template.getVersion() != null
                && template.getVersion() > 0
                && StrUtil.isNotBlank(template.getTemplateContent())
                && StrUtil.equals(template.getTemplateCode(), promptCode.getCode())
                && StrUtil.equals(template.getScene(), promptCode.getScene().getCode());
    }
}
