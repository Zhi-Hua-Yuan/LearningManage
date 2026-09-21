package com.spt.learningmanage.ai.pipeline;

import com.fasterxml.jackson.core.JsonParseException;
import com.fasterxml.jackson.databind.DeserializationFeature;
import com.fasterxml.jackson.databind.JsonMappingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.spt.learningmanage.exception.AiResponseProcessingException;
import org.springframework.ai.converter.BeanOutputConverter;
import org.springframework.ai.converter.StructuredOutputConverter;
import org.springframework.stereotype.Component;

import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Spring AI structured-output converter used by the governed pipeline.
 * It deliberately performs no retry: retry policy remains owned by the AI governance layer.
 */
@Component
public class AiStructuredOutputDecoder {

    private final ObjectMapper objectMapper;
    private final Map<Class<?>, StructuredOutputConverter<?>> converters = new ConcurrentHashMap<>();

    public AiStructuredOutputDecoder(ObjectMapper objectMapper) {
        this.objectMapper = strictMapper(objectMapper);
    }

    public AiStructuredOutputDecoder() {
        this(new ObjectMapper());
    }

    public String format(Class<?> outputType) {
        return converter(outputType).getFormat();
    }

    public <T> T decode(String content, Class<T> outputType) {
        if (content == null || content.isBlank()) {
            throw AiResponseProcessingException.parse("AI 结构化响应为空", null);
        }
        try {
            return outputType.cast(converter(outputType).convert(content));
        } catch (RuntimeException exception) {
            if (contains(exception, JsonParseException.class)) {
                throw AiResponseProcessingException.parse("AI 结构化响应不是合法 JSON", exception);
            }
            if (contains(exception, JsonMappingException.class)) {
                throw AiResponseProcessingException.schema("AI 结构化响应不符合 Schema", exception);
            }
            throw AiResponseProcessingException.parse("AI 结构化响应解析失败", exception);
        }
    }

    @SuppressWarnings("unchecked")
    private <T> StructuredOutputConverter<T> converter(Class<T> outputType) {
        if (outputType == null) {
            throw new IllegalArgumentException("结构化输出类型不能为空");
        }
        return (StructuredOutputConverter<T>) converters.computeIfAbsent(
                outputType, type -> new BeanOutputConverter<>(outputType, objectMapper));
    }

    private ObjectMapper strictMapper(ObjectMapper source) {
        ObjectMapper copy = source == null ? new ObjectMapper() : source.copy();
        copy.findAndRegisterModules();
        return copy.enable(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES)
                .enable(DeserializationFeature.FAIL_ON_MISSING_CREATOR_PROPERTIES);
    }

    private boolean contains(Throwable throwable, Class<? extends Throwable> type) {
        Throwable current = throwable;
        while (current != null) {
            if (type.isInstance(current)) {
                return true;
            }
            current = current.getCause();
        }
        return false;
    }
}
