package com.schoolai.config;

import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.context.annotation.Configuration;

import java.util.List;

@Data
@Configuration
@ConfigurationProperties(prefix = "web-search")
public class WebSearchProperties {
    private boolean enabled = false;
    private String bochaApiKey;
    private int maxResults = 3;
    private int timeoutSeconds = 12;
    private List<String> allowedDomains = List.of();
}
