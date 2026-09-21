package com.spt.learningmanage.ai.pipeline;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.spt.learningmanage.constant.AiCallFailureTypeEnum;
import com.spt.learningmanage.exception.AiResponseProcessingException;
import com.spt.learningmanage.model.dto.ai.structured.TodayOrderStructuredResponse;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class AiStructuredOutputDecoderTest {

    private final AiStructuredOutputDecoder decoder = new AiStructuredOutputDecoder(new ObjectMapper());

    @Test
    void decodesStrictObjectAndCodeFence() {
        TodayOrderStructuredResponse response = decoder.decode(
                "```json\n{\"strategy\":\"先完成高收益任务\",\"items\":[{\"taskId\":1,\"difficulty\":2,\"cost\":30,\"benefit\":5,\"estimatedMinutes\":45,\"reason\":\"收益高\"}]}\n```",
                TodayOrderStructuredResponse.class);

        assertEquals("先完成高收益任务", response.strategy());
        assertNotNull(response.items());
        assertEquals(1L, response.items().get(0).taskId());
    }

    @Test
    void classifiesMalformedJsonAsParseFailure() {
        AiResponseProcessingException exception = assertThrows(AiResponseProcessingException.class,
                () -> decoder.decode("{\"strategy\":", TodayOrderStructuredResponse.class));

        assertEquals(AiCallFailureTypeEnum.RESPONSE_PARSE, exception.getFailureType());
    }

    @Test
    void classifiesWrongRootAndUnknownFieldAsSchemaFailure() {
        AiResponseProcessingException wrongRoot = assertThrows(AiResponseProcessingException.class,
                () -> decoder.decode("[]", TodayOrderStructuredResponse.class));
        AiResponseProcessingException unknownField = assertThrows(AiResponseProcessingException.class,
                () -> decoder.decode("{\"strategy\":\"x\",\"items\":[],\"extra\":true}",
                        TodayOrderStructuredResponse.class));

        assertEquals(AiCallFailureTypeEnum.RESPONSE_SCHEMA, wrongRoot.getFailureType());
        assertEquals(AiCallFailureTypeEnum.RESPONSE_SCHEMA, unknownField.getFailureType());
    }

    @Test
    void classifiesMissingRequiredFieldAsSchemaFailure() {
        AiResponseProcessingException exception = assertThrows(AiResponseProcessingException.class,
                () -> decoder.decode("{\"strategy\":\"x\"}", TodayOrderStructuredResponse.class));

        assertEquals(AiCallFailureTypeEnum.RESPONSE_SCHEMA, exception.getFailureType());
        assertTrue(decoder.format(TodayOrderStructuredResponse.class).contains("items"));
    }
}
