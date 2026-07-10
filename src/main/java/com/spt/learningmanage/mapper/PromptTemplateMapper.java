package com.spt.learningmanage.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.spt.learningmanage.model.entity.PromptTemplate;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;

import java.util.List;

@Mapper
public interface PromptTemplateMapper extends BaseMapper<PromptTemplate> {

    /**
     * 查询包含逻辑删除记录在内的最大版本，并锁定对应记录，避免复用已删除的版本号。
     */
    @Select("SELECT `version` FROM `prompt_template` "
            + "WHERE `template_code` = #{templateCode} "
            + "ORDER BY `version` DESC LIMIT 1 FOR UPDATE")
    Integer selectLatestVersionForUpdate(@Param("templateCode") String templateCode);

    /**
     * 按固定顺序锁定同一模板编码下所有未删除版本，用于原子切换启用版本。
     */
    @Select("SELECT * FROM `prompt_template` "
            + "WHERE `template_code` = #{templateCode} AND `is_delete` = 0 "
            + "ORDER BY `version` ASC FOR UPDATE")
    List<PromptTemplate> selectVersionsForUpdate(@Param("templateCode") String templateCode);
}
