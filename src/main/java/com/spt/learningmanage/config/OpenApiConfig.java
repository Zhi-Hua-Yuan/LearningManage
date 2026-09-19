package com.spt.learningmanage.config;

import io.swagger.v3.oas.models.Components;
import io.swagger.v3.oas.models.OpenAPI;
import io.swagger.v3.oas.models.info.Contact;
import io.swagger.v3.oas.models.info.Info;
import io.swagger.v3.oas.models.media.Schema;
import org.springdoc.core.customizers.OpenApiCustomizer;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import java.util.Map;
import java.util.Set;

@Configuration
public class OpenApiConfig {

    /**
     * 需要抹掉 {@code minLength} 的字段清单：schema 名 -&gt; 属性名。
     *
     * <p>这些是仓库里"只有 {@code @NotBlank}、没有伴生 {@code @Size}"的字符串字段，
     * 也就是 swagger-core 2.2.47 会额外派生 {@code minLength: 1} 的全部字段。
     * 用显式名单而不是"见到 minLength == 1 就删"，是为了不误伤将来真正需要
     * {@code minLength: 1}（例如 {@code @Size(min = 1)}）的字段。
     *
     * <p>失败模式是"响亮"的：若这些字段将来被改名或删除，名单便不再命中，
     * 对应字段的 {@code minLength: 1} 会重新出现，并被
     * {@code scripts/ci/verify-runtime-api-contract.sh} 的 oasdiff breaking 判定拦下；
     * 反之若新增了同类字段，也会以同样的方式暴露出来，需要显式决定后加进名单。
     */
    private static final Map<String, Set<String>> NOT_BLANK_DERIVED_MIN_LENGTH_FIELDS = Map.of(
            "CleanupRunCreateRequest", Set.of("clientRequestId"),
            "TeamJoinRequest", Set.of("inviteCode"),
            "TeamMemberRoleUpdateRequest", Set.of("role"));

    @Bean
    public OpenAPI openAPI() {
        return new OpenAPI()
                .info(new Info()
                        .title("LearningManage API")
                        .description("学习管理系统API文档")
                        .version("1.0")
                        .contact(new Contact().name("宋小通")));
    }

    /**
     * 抹平 swagger-core 升级派生出来的 {@code minLength}，让运行时 OpenAPI 与 Phase 0
     * 冻结基线（{@code docs/api/baseline/stage8-pre-spring-ai-v1.0.0-openapi.json}）
     * 保持逐节点等价。
     *
     * <p><b>差异来源不是 springdoc，而是它的传递依赖 swagger-core。</b>
     * 基线由 swagger-core 2.2.19（knife4j 4.5.0 → springdoc 2.3.0 传递引入）生成；
     * 换用 springdoc 2.8.17 后传递依赖升到 swagger-core 2.2.47，其
     * {@code ModelResolver} 新增了 {@code applyNotBlankConstraint}，
     * 于是"只有 {@code @NotBlank}、没有 {@code @Size}"的字符串字段会多出
     * {@code minLength: 1}。反之两者同时存在时 {@code @Size} 会把 minLength 写成 0，
     * 所以另外 6 处带 {@code @Size} 的 {@code @NotBlank} 字段不受影响。
     * 全量比对下来，运行时文档与基线的差异只有 12 处叶子节点：
     * 3 处本类负责清除的 {@code minLength}，外加 9 处 MyBatis-Plus
     * {@code IPage#getPages()} 被标 {@code @Deprecated} 带来的 {@code deprecated: true}
     * （属元数据，oasdiff 不判为 breaking）。
     *
     * <p>对 {@code oasdiff breaking} 而言前者是
     * {@code request-property-min-length-increased}，会被
     * {@code scripts/ci/verify-runtime-api-contract.sh} 判为破坏性变更并使门禁失败。
     * 但本次升级没有改变任何运行期行为：Bean Validation 依然照旧拒绝空串与纯空白串，
     * 这里移除的只是文档表述。既然基线与"PR 1 不修改 REST API"两条约束都不允许被本次
     * 升级改写，就在生成侧丢弃这条派生约束，而不是去重签基线。
     *
     * <p><b>为什么不用 {@code PropertyCustomizer}：</b>实测该扩展点确实会被逐属性调用，
     * 调用时也能看到 {@code minLength} 已是 1、注解里有 {@code @NotBlank}，
     * 但它拿到的是中间态 schema——swagger-core 在链调用之后会把 Bean Validation
     * 结果重新施加到真正进入文档的那份 schema 上，因此那里的修改会被覆盖掉。
     * 只有作用于最终 {@code OpenAPI} 对象的本扩展点才可靠。
     */
    @Bean
    public OpenApiCustomizer notBlankDerivedMinLengthCompatibility() {
        return openApi -> {
            Components components = openApi.getComponents();
            if (components == null) {
                return;
            }
            Map<String, Schema> schemas = components.getSchemas();
            if (schemas == null) {
                return;
            }
            NOT_BLANK_DERIVED_MIN_LENGTH_FIELDS.forEach(
                    (schemaName, propertyNames) -> clearMinLength(schemas, schemaName, propertyNames));
        };
    }

    private static void clearMinLength(Map<String, Schema> schemas, String schemaName, Set<String> propertyNames) {
        Schema<?> schema = schemas.get(schemaName);
        if (schema == null) {
            return;
        }
        Map<String, Schema> properties = schema.getProperties();
        if (properties == null) {
            return;
        }
        for (String propertyName : propertyNames) {
            Schema<?> property = properties.get(propertyName);
            if (property != null && Integer.valueOf(1).equals(property.getMinLength())) {
                property.setMinLength(null);
            }
        }
    }
}
