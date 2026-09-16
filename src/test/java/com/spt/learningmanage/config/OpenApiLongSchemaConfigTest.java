package com.spt.learningmanage.config;

import io.swagger.v3.oas.models.media.StringSchema;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;

class OpenApiLongSchemaConfigTest {

    @Test
    void documentsSerializedLongsAsDecimalStrings() {
        StringSchema schema = OpenApiLongSchemaConfig.serializedLongSchema();

        assertEquals("string", schema.getType());
        assertEquals("^-?\\d+$", schema.getPattern());
        assertEquals("2098698814079639554", schema.getExample());
    }
}
