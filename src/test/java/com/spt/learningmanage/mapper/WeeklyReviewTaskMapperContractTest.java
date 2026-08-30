package com.spt.learningmanage.mapper;

import org.junit.jupiter.api.Test;

import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.regex.Pattern;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class WeeklyReviewTaskMapperContractTest {

    private static final Path XML = Path.of(
            "src/main/resources/mapper/WeeklyReviewTaskMapper.xml");

    @Test
    void mapperShouldExposeOnlyAssociationColumnsAndAllPrimitives() throws Exception {
        String xml = Files.readString(XML, StandardCharsets.UTF_8);
        assertTrue(xml.contains(
                "namespace=\"com.spt.learningmanage.mapper.WeeklyReviewTaskMapper\""));
        assertTrue(xml.contains("id=\"selectByReviewIds\""));
        assertTrue(xml.contains("id=\"deleteByReviewId\""));
        assertTrue(xml.contains("id=\"batchInsert\""));
        for (String column : new String[]{"id", "weekly_review_id", "task_id", "create_time"}) {
            assertTrue(xml.contains(column), "missing association column: " + column);
        }
        assertFalse(xml.toLowerCase().contains("reflection"));
        assertFalse(xml.toLowerCase().contains("next_plan"));
        assertFalse(xml.toLowerCase().contains("shared_summary"));
        assertFalse(xml.contains("${"), "mapper must use bound parameters only");
        assertFalse(Pattern.compile("select\\s+\\*", Pattern.CASE_INSENSITIVE)
                .matcher(xml).find());
    }

    @Test
    void selectShouldGuardEmptyIdsAndUseStableOrdering() throws Exception {
        String xml = Files.readString(XML, StandardCharsets.UTF_8);
        String select = xml.substring(xml.indexOf("<select id=\"selectByReviewIds\""),
                xml.indexOf("</select>", xml.indexOf("<select id=\"selectByReviewIds\"")));
        String lower = select.toLowerCase();
        assertTrue(lower.contains("reviewids != null"));
        assertTrue(lower.contains("where 1 = 0"));
        assertTrue(lower.contains("#{reviewid}"));
        assertTrue(lower.contains("order by weekly_review_id asc, id asc"));
    }

    @Test
    void writesShouldBeScopedAndBatchShapeShouldBeExplicit() throws Exception {
        String xml = Files.readString(XML, StandardCharsets.UTF_8).toLowerCase();
        String delete = xml.substring(xml.indexOf("<delete id=\"deletebyreviewid\""),
                xml.indexOf("</delete>", xml.indexOf("<delete id=\"deletebyreviewid\"")));
        assertTrue(delete.contains("delete from weekly_review_task"));
        assertTrue(delete.contains("where weekly_review_id = #{weeklyreviewid}"));

        String insert = xml.substring(xml.indexOf("<insert id=\"batchinsert\""),
                xml.indexOf("</insert>", xml.indexOf("<insert id=\"batchinsert\"")));
        assertTrue(insert.contains("insert into weekly_review_task"));
        assertTrue(insert.contains("<foreach collection=\"relations\""));
        assertTrue(insert.contains("#{relation.id}"));
        assertTrue(insert.contains("#{relation.weeklyreviewid}"));
        assertTrue(insert.contains("#{relation.taskid}"));
    }
}
