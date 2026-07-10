package com.spt.learningmanage.model.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableLogic;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.time.LocalDateTime;

/**
 * 数据库中的 AI Prompt 模板版本。
 */
@Data
@TableName("prompt_template")
public class PromptTemplate {

    @TableId(type = IdType.ASSIGN_ID)
    private Long id;

    private String templateCode;

    private String scene;

    private String templateName;

    private String templateContent;

    private Integer version;

    private Integer enabled;

    private String remark;

    @TableLogic
    private Integer isDelete;

    private LocalDateTime createTime;

    private LocalDateTime updateTime;
}
