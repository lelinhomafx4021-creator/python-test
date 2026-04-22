package com.aiinvestor.gateway.config;

import jakarta.validation.constraints.NotBlank;
import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.validation.annotation.Validated;

@Data
@Validated
@ConfigurationProperties(prefix = "python.ai")
public class PythonAiProperties {

    @NotBlank
    private String baseUrl;
}
