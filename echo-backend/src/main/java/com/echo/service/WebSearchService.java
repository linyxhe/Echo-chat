package com.echo.service;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.core.env.Environment;
import org.springframework.http.MediaType;
import org.springframework.http.client.SimpleClientHttpRequestFactory;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;
import org.springframework.web.client.RestClient;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Narrow server-side boundary for web search. The model and browser never see
 * the provider credential; the only accepted remote endpoint is Tavily Search.
 */
@Service
public class WebSearchService {
    private static final String TAVILY_SEARCH_URL = "https://api.tavily.com/search";
    private static final String TAVILY_DOCS_URL = "https://docs.tavily.com/documentation/api-reference/endpoint/search";
    private final RestClient client;
    private final String provider;
    private final String apiKey;
    private final int maxResults;

    public WebSearchService(@Value("${app.ai.agent.search.provider:tavily}") String provider,
                            @Value("${app.ai.agent.search.api-key:}") String apiKey,
                            @Value("${app.ai.agent.search.max-results:5}") int maxResults,
                            Environment environment) {
        SimpleClientHttpRequestFactory factory = new SimpleClientHttpRequestFactory();
        factory.setConnectTimeout(5_000);
        factory.setReadTimeout(12_000);
        this.client = RestClient.builder().requestFactory(factory).build();
        // Keep compatibility with the local setup used before the Agent
        // namespace was introduced: app.search.* is still a valid private
        // config path, while app.ai.agent.search.* remains canonical.
        String configuredProvider = firstText(provider, environment.getProperty("app.search.provider"));
        String configuredApiKey = firstText(apiKey, environment.getProperty("app.search.api-key"));
        int configuredMaxResults = maxResults;
        String legacyMaxResults = environment.getProperty("app.search.max-results");
        if (legacyMaxResults != null && maxResults == 5) {
            try { configuredMaxResults = Integer.parseInt(legacyMaxResults); } catch (NumberFormatException ignored) { }
        }
        this.provider = configuredProvider.toLowerCase(java.util.Locale.ROOT);
        this.apiKey = configuredApiKey;
        this.maxResults = Math.max(1, Math.min(configuredMaxResults, 5));
    }

    public boolean isConfigured() {
        return "tavily".equals(provider) && StringUtils.hasText(apiKey);
    }

    public SearchResult search(String query) {
        if (!isConfigured()) throw new IllegalStateException("联网搜索尚未由管理员配置");
        String normalized = normalizeQuery(query);
        Map<String, Object> body = new LinkedHashMap<>();
        body.put("query", normalized);
        body.put("search_depth", "basic");
        body.put("max_results", maxResults);
        body.put("include_answer", false);
        body.put("include_raw_content", false);
        try {
            @SuppressWarnings("unchecked")
            Map<String, Object> response = client.post().uri(TAVILY_SEARCH_URL)
                    .contentType(MediaType.APPLICATION_JSON)
                    .header("Authorization", "Bearer " + apiKey)
                    .body(body).retrieve().body(Map.class);
            return new SearchResult(normalized, parseResults(response == null ? null : response.get("results")), TAVILY_DOCS_URL);
        } catch (Exception e) {
            throw new IllegalStateException("联网搜索服务暂不可用，请稍后再试", e);
        }
    }

    public String normalizeQuery(String query) {
        if (!StringUtils.hasText(query)) throw new IllegalArgumentException("搜索关键词不能为空");
        String normalized = query.trim();
        if (normalized.length() > 400) throw new IllegalArgumentException("搜索关键词不能超过 400 个字符");
        return normalized;
    }

    @SuppressWarnings("unchecked")
    private List<SearchItem> parseResults(Object rawResults) {
        if (!(rawResults instanceof List<?> values)) return List.of();
        List<SearchItem> items = new ArrayList<>();
        for (Object value : values) {
            if (!(value instanceof Map<?, ?> raw) || items.size() >= maxResults) continue;
            Object urlValue = raw.get("url");
            if (!(urlValue instanceof String url) || !url.startsWith("https://")) continue;
            String title = text(raw.get("title"), 160);
            String content = text(raw.get("content"), 500);
            if (!StringUtils.hasText(title) && !StringUtils.hasText(content)) continue;
            items.add(new SearchItem(title.isBlank() ? url : title, content, url));
        }
        return List.copyOf(items);
    }

    private String text(Object value, int max) {
        if (!(value instanceof String text)) return "";
        String normalized = text.replaceAll("\\s+", " ").trim();
        return normalized.substring(0, Math.min(normalized.length(), max));
    }

    private String firstText(String preferred, String fallback) {
        if (StringUtils.hasText(preferred)) return preferred.trim();
        return StringUtils.hasText(fallback) ? fallback.trim() : "";
    }

    public record SearchItem(String title, String content, String url) { }
    public record SearchResult(String query, List<SearchItem> items, String providerDocsUrl) { }
}
