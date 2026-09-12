package com.spt.learningmanage.config;

import io.swagger.v3.oas.models.media.StringSchema;
import org.springdoc.core.utils.SpringDocUtils;
import org.springframework.context.annotation.Configuration;

/**
 * Keeps the generated OpenAPI schema aligned with {@link JacksonConfig}, which
 * serializes every Java {@code long}/{@link Long} as a decimal JSON string.
 */
@Configuration
public class OpenApiLongSchemaConfig {

    static {
        SpringDocUtils.getConfig()
                .replaceWithSchema(Long.class, serializedLongSchema())
                .replaceWithSchema(Long.TYPE, serializedLongSchema());
    }

    static StringSchema serializedLongSchema() {
        StringSchema schema = new StringSchema();
        schema.setPattern("^-?\\d+$");
        schema.setExample("2098698814079639554");
        schema.setDescription("64-bit integer serialized as a decimal string to preserve JavaScript precision");
        return schema;
    }
}
