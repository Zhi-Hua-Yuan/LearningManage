package com.spt.learningmanage.config;

import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.context.annotation.Configuration;
import org.springframework.validation.annotation.Validated;

@Data
@Validated
@Configuration
@ConfigurationProperties(prefix = "security.jwt")
public class JwtProperties {

    @NotBlank(message = "JWT_SECRET must not be blank")
    @Size(min = 32, message = "JWT_SECRET must contain at least 32 characters")
    private String secret;

    @Min(value = 60, message = "JWT_EXPIRE_SECONDS must be at least 60 seconds")
    private long expireSeconds = 86400;
}
