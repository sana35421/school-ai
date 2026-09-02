package com.schoolai.service;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.schoolai.config.WebSearchProperties;
import com.schoolai.model.vo.WebSearchResultVO;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import okhttp3.MediaType;
import okhttp3.OkHttpClient;
import okhttp3.Request;
import okhttp3.RequestBody;
import okhttp3.Response;
import org.springframework.stereotype.Service;

import java.io.IOException;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.concurrent.TimeUnit;

@Slf4j
@Service
@RequiredArgsConstructor
public class WebSearchService {
    private static final MediaType JSON = MediaType.parse("application/json");
    private static final int MIN_DISPLAY_RESULTS = 3;
    private static final int MAX_DISPLAY_RESULTS = 5;
    private static final int MIN_CANDIDATE_RESULTS = 10;
    private final WebSearchProperties properties;
    private final ObjectMapper objectMapper;

    public boolean isAvailable() {
        return properties.isEnabled() && properties.getBochaApiKey() != null
                && !properties.getBochaApiKey().isBlank();
    }

    public List<WebSearchResultVO> search(String query) {
        if (!isAvailable() || query == null || query.isBlank()) return List.of();
        OkHttpClient client = new OkHttpClient.Builder()
                .connectTimeout(properties.getTimeoutSeconds(), TimeUnit.SECONDS)
                .readTimeout(properties.getTimeoutSeconds(), TimeUnit.SECONDS)
                .build();
        try {
            int displayCount = Math.min(Math.max(properties.getMaxResults(), MIN_DISPLAY_RESULTS), MAX_DISPLAY_RESULTS);
            Map<String, Object> body = Map.of(
                    "query", query,
                    "freshness", "noLimit",
                    "summary", true,
                    // Fetch a larger candidate pool so domain filtering and deduplication do not leave too few results.
                    "count", Math.min(Math.max(displayCount * 3, MIN_CANDIDATE_RESULTS), 20));
            Request request = new Request.Builder().url("https://api.bochaai.com/v1/web-search")
                    .addHeader("Authorization", "Bearer " + properties.getBochaApiKey())
                    .addHeader("Content-Type", "application/json")
                    .post(RequestBody.create(objectMapper.writeValueAsString(body), JSON)).build();
            try (Response response = client.newCall(request).execute()) {
                if (!response.isSuccessful() || response.body() == null) {
                    log.warn("Web search failed with HTTP {}", response.code());
                    return List.of();
                }
                JsonNode results = objectMapper.readTree(response.body().string())
                        .path("data").path("webPages").path("value");
                List<WebSearchResultVO> candidates = new ArrayList<>();
                for (JsonNode item : results) {
                    String url = item.path("url").asText();
                    if (url.isBlank()) continue;
                    WebSearchResultVO result = new WebSearchResultVO();
                    result.setTitle(item.path("name").asText(url));
                    result.setUrl(url);
                    result.setContent(item.path("summary").asText(item.path("snippet").asText()));
                    result.setScore(item.path("score").asDouble(0));
                    candidates.add(result);
                }
                return rankAndSelect(candidates, displayCount);
            }
        } catch (IOException e) {
            log.warn("Web search request failed", e);
            return List.of();
        }
    }

    /** Keeps trusted-domain hits first, then fills the minimum with the next highest-ranked hits. */
    List<WebSearchResultVO> rankAndSelect(List<WebSearchResultVO> candidates, int displayCount) {
        int limit = Math.min(Math.max(displayCount, MIN_DISPLAY_RESULTS), MAX_DISPLAY_RESULTS);
        // Java's stream sort is stable: ties keep Bocha's own relevance/popularity order.
        Comparator<WebSearchResultVO> ranking = Comparator.comparingDouble(WebSearchResultVO::getScore).reversed();
        List<WebSearchResultVO> trusted = candidates.stream()
                .filter(result -> isAllowed(result.getUrl()))
                .sorted(ranking)
                .toList();
        List<WebSearchResultVO> fallback = candidates.stream()
                .filter(result -> !isAllowed(result.getUrl()))
                .sorted(ranking)
                .toList();

        LinkedHashMap<String, WebSearchResultVO> selected = new LinkedHashMap<>();
        trusted.forEach(result -> selected.putIfAbsent(normalizeUrl(result.getUrl()), result));
        fallback.forEach(result -> {
            if (selected.size() < Math.max(MIN_DISPLAY_RESULTS, limit)) {
                selected.putIfAbsent(normalizeUrl(result.getUrl()), result);
            }
        });
        return selected.values().stream().limit(limit).toList();
    }

    private String normalizeUrl(String url) {
        if (url == null) return "";
        String normalized = url.trim().toLowerCase(Locale.ROOT);
        int fragment = normalized.indexOf('#');
        normalized = fragment >= 0 ? normalized.substring(0, fragment) : normalized;
        int query = normalized.indexOf('?');
        return query >= 0 ? normalized.substring(0, query) : normalized;
    }

    private boolean isAllowed(String url) {
        if (properties.getAllowedDomains() == null || properties.getAllowedDomains().isEmpty()) return true;
        String normalized = url.toLowerCase(Locale.ROOT);
        return properties.getAllowedDomains().stream().filter(domain -> domain != null && !domain.isBlank())
                .map(domain -> domain.toLowerCase(Locale.ROOT)).anyMatch(domain -> normalized.contains("://" + domain) || normalized.contains("." + domain + "/"));
    }
}
