package com.spt.learningmanage.config;

import io.swagger.v3.oas.models.Components;
import io.swagger.v3.oas.models.OpenAPI;
import io.swagger.v3.oas.models.media.ObjectSchema;
import io.swagger.v3.oas.models.media.Schema;
import io.swagger.v3.oas.models.media.StringSchema;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;
import org.springdoc.core.customizers.OpenApiCustomizer;

/**
 * {@link OpenApiConfig#notBlankDerivedMinLengthCompatibility()} 的显式回归断言。
 *
 * <p>swagger-core 从 2.2.19 升到 2.2.47 后开始把 {@code @NotBlank} 推导成
 * {@code minLength: 1}，会让 {@code oasdiff breaking} 报
 * {@code request-property-min-length-increased}，直接打红
 * {@code scripts/ci/verify-runtime-api-contract.sh}。这个自定义器负责把它们抹平。
 *
 * <p>这里钉死三件事：
 * <ol>
 *   <li>名单里的三个字段，派生出来的 minLength 必须被清掉；</li>
 *   <li>名单外的 schema（哪怕属性同名，例如 {@code TeamMemberVO.role}）一律不动，
 *       防止自定义器退化成"见到 minLength == 1 就删"；</li>
 *   <li>{@code @Size} 派生的 {@code minLength: 0} 与 {@code maxLength} 不受影响。</li>
 * </ol>
 *
 * <p>自定义器只在最终 OpenAPI 文档上作业；真实文档里的结果由运行期 OpenAPI 重导出 +
 * oasdiff 比对来验证。
 */
class OpenApiConfigTest {

    private final OpenApiCustomizer customizer =
            new OpenApiConfig().notBlankDerivedMinLengthCompatibility();

    @Test
    void clearsMinLengthDerivedFromNotBlankWithoutSize() {
        OpenAPI openApi = documentOf(
                "CleanupRunCreateRequest", "clientRequestId", stringSchema(1),
                "TeamJoinRequest", "inviteCode", stringSchema(1),
                "TeamMemberRoleUpdateRequest", "role", stringSchema(1));

        customizer.customise(openApi);

        Assertions.assertNull(minLengthOf(openApi, "CleanupRunCreateRequest", "clientRequestId"));
        Assertions.assertNull(minLengthOf(openApi, "TeamJoinRequest", "inviteCode"));
        Assertions.assertNull(minLengthOf(openApi, "TeamMemberRoleUpdateRequest", "role"));
    }

    @Test
    void leavesSchemasOutsideTheAllowListUntouched() {
        // TeamMemberVO.role 与名单里的属性同名，但该 VO 上没有校验注解，不在名单内，必须保留。
        StringSchema unlisted = stringSchema(1);
        // TeamCreateRequest.name 是 @NotBlank + @Size(max = 60)：@Size 会把 minLength 写成 0 并补上 maxLength，
        // 自定义器不得碰它。
        StringSchema sized = new StringSchema();
        sized.setMinLength(0);
        sized.setMaxLength(60);

        OpenAPI openApi = documentOf(
                "TeamMemberVO", "role", unlisted,
                "TeamCreateRequest", "name", sized);

        customizer.customise(openApi);

        Assertions.assertEquals(1, minLengthOf(openApi, "TeamMemberVO", "role"), "名单外 schema 不应被改动");
        Assertions.assertEquals(0, minLengthOf(openApi, "TeamCreateRequest", "name"), "@Size 派生的 minLength 不应被改动");
        Assertions.assertEquals(60, sized.getMaxLength(), "@Size 派生的 maxLength 不应被改动");
    }

    @Test
    void toleratesDocumentsWithoutComponents() {
        Assertions.assertDoesNotThrow(() -> customizer.customise(new OpenAPI()));
        Assertions.assertDoesNotThrow(() -> customizer.customise(new OpenAPI().components(new Components())));
    }

    private static OpenAPI documentOf(Object... schemaNamePropertyNameAndSchema) {
        Components components = new Components();
        for (int i = 0; i < schemaNamePropertyNameAndSchema.length; i += 3) {
            String schemaName = (String) schemaNamePropertyNameAndSchema[i];
            String propertyName = (String) schemaNamePropertyNameAndSchema[i + 1];
            Schema<?> propertySchema = (Schema<?>) schemaNamePropertyNameAndSchema[i + 2];

            ObjectSchema objectSchema = new ObjectSchema();
            objectSchema.addProperty(propertyName, propertySchema);
            components.addSchemas(schemaName, objectSchema);
        }
        return new OpenAPI().components(components);
    }

    private static StringSchema stringSchema(Integer minLength) {
        StringSchema schema = new StringSchema();
        schema.setMinLength(minLength);
        return schema;
    }

    private static Integer minLengthOf(OpenAPI openApi, String schemaName, String propertyName) {
        Schema<?> schema = openApi.getComponents().getSchemas().get(schemaName);
        Assertions.assertNotNull(schema, "缺少 schema：" + schemaName);
        Schema<?> property = schema.getProperties().get(propertyName);
        Assertions.assertNotNull(property, "缺少属性：" + schemaName + "." + propertyName);
        return property.getMinLength();
    }
}
