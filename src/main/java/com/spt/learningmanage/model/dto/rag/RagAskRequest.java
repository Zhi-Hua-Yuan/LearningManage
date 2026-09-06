package com.spt.learningmanage.model.dto.rag;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;
import jakarta.validation.constraints.Size;
import lombok.Data;

@Data
public class RagAskRequest {
    @NotBlank(message = "问题不能为空")
    @Size(max = 1000, message = "问题长度不能超过1000个字符")
    private String question;

    @NotNull(message = "项目ID不能为空")
    @Positive(message = "项目ID必须为正整数")
    private Long projectId;
}
