package com.spt.learningmanage.agent;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import jakarta.validation.constraints.Size;

import java.lang.reflect.RecordComponent;

/**
 * Builds the provider-neutral JSON Schema exposed for an Agent Tool.
 *
 * <p>This deliberately lives outside the Spring AI adapter boundary.  Tool
 * policy and schema metadata belong to the application layer and must remain
 * available to both the legacy and Spring AI transports.</p>
 */
public final class AgentToolSchemaGenerator {
    private AgentToolSchemaGenerator() {
    }

    public static JsonNode generate(ObjectMapper objectMapper, Class<?> argumentType) {
        ObjectNode schema = objectMapper.createObjectNode();
        schema.put("type", "object");
        ObjectNode properties = schema.putObject("properties");
        if (!argumentType.isRecord()) {
            return schema;
        }
        for (RecordComponent component : argumentType.getRecordComponents()) {
            ObjectNode property = properties.putObject(component.getName());
            writeType(property, component.getType());
            Size size = component.getAccessor().getAnnotation(Size.class);
            if (size != null && size.max() != Integer.MAX_VALUE) {
                property.put("maxLength", size.max());
            }
            if (size != null && size.min() > 0) {
                property.put("minLength", size.min());
            }
        }
        return schema;
    }

    private static void writeType(ObjectNode property, Class<?> type) {
        if (type == String.class || type.isEnum()) {
            property.put("type", "string");
            if (type.isEnum()) {
                var values = property.putArray("enum");
                for (Object constant : type.getEnumConstants()) {
                    values.add(constant.toString());
                }
            }
        } else if (type == boolean.class || type == Boolean.class) {
            property.put("type", "boolean");
        } else if (type == byte.class || type == short.class || type == int.class
                || type == long.class || type == Byte.class || type == Short.class
                || type == Integer.class || type == Long.class) {
            property.put("type", "integer");
        } else if (type == float.class || type == double.class
                || type == Float.class || type == Double.class) {
            property.put("type", "number");
        } else if (type.isArray()) {
            property.put("type", "array");
            ObjectNode items = property.putObject("items");
            writeType(items, type.getComponentType());
        } else {
            property.put("type", "object");
        }
    }
}
