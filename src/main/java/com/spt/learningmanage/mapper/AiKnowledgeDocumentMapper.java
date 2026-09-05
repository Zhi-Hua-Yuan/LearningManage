package com.spt.learningmanage.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.spt.learningmanage.model.entity.AiKnowledgeDocument;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;

import java.util.List;

@Mapper
public interface AiKnowledgeDocumentMapper extends BaseMapper<AiKnowledgeDocument> {

    @Select("""
            SELECT * FROM ai_knowledge_document
            WHERE source_type = #{sourceType} AND source_id = #{sourceId}
            """)
    List<AiKnowledgeDocument> selectBySource(@Param("sourceType") String sourceType,
                                             @Param("sourceId") Long sourceId);

    @Select("""
            SELECT * FROM ai_knowledge_document
            WHERE document_key = #{documentKey}
            LIMIT 1
            """)
    AiKnowledgeDocument selectByDocumentKey(@Param("documentKey") String documentKey);
}
