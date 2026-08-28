package com.spt.learningmanage.model.entity;

import com.baomidou.mybatisplus.core.MybatisConfiguration;
import com.baomidou.mybatisplus.core.metadata.TableFieldInfo;
import com.baomidou.mybatisplus.core.metadata.TableInfo;
import com.baomidou.mybatisplus.core.metadata.TableInfoHelper;
import org.apache.ibatis.builder.MapperBuilderAssistant;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

import java.util.Map;
import java.util.stream.Collectors;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;

class TaskIdentityMappingTest {

    @BeforeAll
    static void initTableInfo() {
        if (TableInfoHelper.getTableInfo(Task.class) == null) {
            MybatisConfiguration configuration = new MybatisConfiguration();
            configuration.setMapUnderscoreToCamelCase(true);
            TableInfoHelper.initTableInfo(new MapperBuilderAssistant(configuration, ""), Task.class);
        }
    }

    @Test
    void taskIdentityPropertiesMapToTheFrozenV2Columns() {
        TableInfo tableInfo = TableInfoHelper.getTableInfo(Task.class);
        assertNotNull(tableInfo);

        Map<String, String> propertyColumns = tableInfo.getFieldList().stream()
                .collect(Collectors.toMap(TableFieldInfo::getProperty, TableFieldInfo::getColumn));

        assertEquals("user_id", propertyColumns.get("createdByUserId"));
        assertEquals("assignee_user_id", propertyColumns.get("assigneeUserId"));
        assertEquals("assigned_by_user_id", propertyColumns.get("assignedByUserId"));
        assertEquals("assigned_at", propertyColumns.get("assignedAt"));
        assertFalse(propertyColumns.containsKey("userId"));
    }
}
