package com.echo.service;

import com.alibaba.fastjson2.JSON;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.core.env.Environment;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;
import org.springframework.web.util.UriComponentsBuilder;

import java.io.IOException;
import java.io.InputStream;
import java.net.HttpURLConnection;
import java.net.URI;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.zip.GZIPInputStream;

/** Fixed-host adapter for QWeather. It receives only a model-extracted city name. */
@Service
public class WeatherLookupService {
    private static final String QWEATHER_DOCS_URL = "https://dev.qweather.com/docs/api/weather/";
    private final String apiHost;
    private final String apiKey;
    private final String apiToken;

    public WeatherLookupService(@Value("${app.ai.agent.weather.api-host:}") String apiHost,
                                Environment environment) {
        this.apiHost = normalizeHost(firstText(apiHost, environment.getProperty("app.weather.api-host")));
        // app.weather.* existed in the first local setup. Retain it as a private-config
        // compatibility fallback while the documented namespace remains app.ai.agent.weather.*.
        this.apiKey = firstText(environment.getProperty("app.ai.agent.weather.api-key"),
                environment.getProperty("app.weather.api-key"));
        this.apiToken = firstText(environment.getProperty("app.ai.agent.weather.api-token"),
                environment.getProperty("app.weather.api-token"));
    }

    public WeatherResult lookup(String city, int forecastDays) {
        String normalizedCity = city == null ? "" : city.trim();
        if (!StringUtils.hasText(normalizedCity) || normalizedCity.length() > 80) throw new IllegalArgumentException("城市名称无效");
        if (!StringUtils.hasText(apiHost)) {
            throw new IllegalArgumentException("天气服务尚未配置和风天气 API Host，请从项目控制台复制专属地址");
        }
        if (!StringUtils.hasText(apiKey) && !StringUtils.hasText(apiToken)) {
            throw new IllegalArgumentException("天气服务尚未配置和风天气 API Key 或访问凭据");
        }
        int days = Math.max(1, Math.min(forecastDays, 7));
        try {
            Map<String, Object> geo = get("/geo/v2/city/lookup", Map.of(
                    "location", normalizedCity, "range", "cn", "number", "1", "lang", "zh"));
            requireSuccess(geo, "城市搜索");
            List<Map<String, Object>> locations = list(geo.get("location"));
            if (locations.isEmpty()) throw new IllegalArgumentException("未找到该中国城市，请补充省份或城市名称");
            Map<String, Object> location = locations.get(0);
            String locationId = text(location.get("id"), "");
            if (!StringUtils.hasText(locationId)) throw new IllegalArgumentException("天气服务未返回有效城市标识");

            Map<String, Object> now = get("/v7/weather/now", Map.of("location", locationId, "lang", "zh"));
            requireSuccess(now, "实时天气");
            Map<String, Object> dailyResponse = get("/v7/weather/" + (days <= 3 ? "3d" : "7d"), Map.of("location", locationId, "lang", "zh"));
            requireSuccess(dailyResponse, "天气预报");
            String resolvedName = text(location.get("name"), normalizedCity);
            String admin = text(location.get("adm1"), "");
            String country = text(location.get("country"), "中国");
            String sourceUrl = text(now.get("fxLink"), text(location.get("fxLink"), QWEATHER_DOCS_URL));
            return new WeatherResult(resolvedName, admin, country, object(now.get("now")), list(dailyResponse.get("daily")), sourceUrl);
        } catch (IllegalArgumentException e) {
            throw e;
        } catch (QWeatherHttpException e) {
            int status = e.status();
            if (status == 400) throw new IllegalArgumentException(qweatherBadRequestMessage(e.body()));
            if (status == 401 || status == 403) throw new IllegalArgumentException("和风天气 API Host 或 API Key 无效、无权限或未启用所需数据");
            if (status == 429) throw new IllegalArgumentException("天气查询过于频繁，请稍后再试");
            if (status >= 500) throw new IllegalArgumentException("和风天气服务暂时不可用，请稍后重试");
            throw new IllegalArgumentException("和风天气请求失败（HTTP " + status + "）");
        } catch (Exception e) {
            throw new IllegalArgumentException("和风天气服务暂时不可用，请稍后重试");
        }
    }

    /** Bounded context returned to the Agent model; credentials and raw provider JSON never leave this service. */
    public String summarizeForAgent(WeatherResult result, int forecastDays) {
        String place = result.city() + (StringUtils.hasText(result.admin()) ? "·" + result.admin() : "")
                + (StringUtils.hasText(result.country()) ? "（" + result.country() + "）" : "");
        StringBuilder answer = new StringBuilder("天气查询结果：").append(place).append("\n");
        if (!result.now().isEmpty()) {
            answer.append("当前：").append(field(result.now(), "text", "未知天气")).append("，")
                    .append(field(result.now(), "temp", "—")).append("°C，体感 ")
                    .append(field(result.now(), "feelsLike", "—")).append("°C，")
                    .append(field(result.now(), "windDir", "风向未知")).append(" ")
                    .append(field(result.now(), "windSpeed", "—")).append(" km/h。\n");
        }
        int count = Math.min(Math.max(1, Math.min(forecastDays, 7)), result.daily().size());
        for (int i = 0; i < count; i++) {
            Map<String, Object> daily = result.daily().get(i);
            answer.append("预报 ").append(field(daily, "fxDate", "日期未知")).append("：")
                    .append(field(daily, "textDay", "未知天气")).append("，")
                    .append(field(daily, "tempMax", "—")).append("/")
                    .append(field(daily, "tempMin", "—")).append("°C，降水 ")
                    .append(field(daily, "precip", "—")).append(" mm。\n");
        }
        answer.append("数据来源：和风天气，原始来源链接：").append(result.sourceUrl());
        return answer.toString();
    }

    private String field(Map<String, Object> source, String key, String fallback) {
        return text(source.get(key), fallback);
    }

    private Map<String, Object> get(String path, Map<String, String> parameters) {
        UriComponentsBuilder builder = UriComponentsBuilder.fromUriString(apiHost + path);
        parameters.forEach((name, value) -> builder.queryParam(name, value));
        String url = builder.build().encode().toUriString();
        HttpURLConnection connection = null;
        try {
            connection = (HttpURLConnection) URI.create(url).toURL().openConnection();
            connection.setRequestMethod("GET");
            connection.setConnectTimeout(5_000);
            connection.setReadTimeout(8_000);
            connection.setRequestProperty("Accept", "application/json");
            connection.setRequestProperty("Accept-Encoding", "gzip");
            if (StringUtils.hasText(apiKey)) connection.setRequestProperty("X-QW-Api-Key", apiKey);
            else connection.setRequestProperty("Authorization", "Bearer " + apiToken);
            int status = connection.getResponseCode();
            String body = readBody(connection, status >= 400 ? connection.getErrorStream() : connection.getInputStream());
            if (status >= 400) throw new QWeatherHttpException(status, body);
            return object(body);
        } catch (QWeatherHttpException e) {
            throw e;
        } catch (IOException e) {
            throw new IllegalStateException("和风天气网络请求失败", e);
        } finally {
            if (connection != null) connection.disconnect();
        }
    }

    private String readBody(HttpURLConnection connection, InputStream source) throws IOException {
        if (source == null) return "";
        try (InputStream raw = source;
             InputStream decoded = "gzip".equalsIgnoreCase(connection.getContentEncoding()) ? new GZIPInputStream(raw) : raw) {
            return new String(decoded.readAllBytes(), StandardCharsets.UTF_8);
        }
    }

    @SuppressWarnings("unchecked")
    private String qweatherBadRequestMessage(String responseBody) {
        try {
            Object parsed = JSON.parse(responseBody);
            if (parsed instanceof Map<?, ?> outer && outer.get("error") instanceof Map<?, ?> error) {
                String type = String.valueOf(error.get("type"));
                if (type.contains("no-such-location")) return "未找到该中国城市，请补充省份或城市名称";
                Object invalid = error.get("invalidParams");
                if (invalid instanceof List<?> names && !names.isEmpty()) {
                    List<String> safeNames = names.stream().filter(String.class::isInstance).map(String.class::cast)
                            .filter(name -> name.matches("[A-Za-z][A-Za-z0-9_-]{0,40}")).limit(5).toList();
                    if (!safeNames.isEmpty()) return "和风天气请求参数无效：" + String.join("、", safeNames);
                }
            }
        } catch (Exception ignored) { }
        return "和风天气请求参数无效，请检查城市名称和项目 API 配置";
    }

    private void requireSuccess(Map<String, Object> response, String action) {
        String code = text(response.get("code"), "");
        if ("200".equals(code)) return;
        if ("401".equals(code) || "403".equals(code)) throw new IllegalArgumentException("和风天气访问凭据无效或无权限");
        if ("404".equals(code)) throw new IllegalArgumentException("未找到该中国城市，请补充省份或城市名称");
        if ("429".equals(code)) throw new IllegalArgumentException("天气查询过于频繁，请稍后再试");
        throw new IllegalArgumentException("和风天气" + action + "失败，请稍后重试");
    }

    private String normalizeHost(String value) {
        String normalized = value == null ? "" : value.trim();
        if (!StringUtils.hasText(normalized)) return "";
        // The console may display a bare host such as xxx.re.qweatherapi.com.
        // Accept that official form, but pin the request target to HTTPS under qweatherapi.com.
        if (!normalized.startsWith("https://")) normalized = "https://" + normalized;
        try {
            URI uri = URI.create(normalized);
            String host = uri.getHost();
            if (!"https".equalsIgnoreCase(uri.getScheme()) || !StringUtils.hasText(host)
                    || !host.matches("(?i)(?:[a-z0-9-]+\\.)+qweatherapi\\.com")
                    || uri.getPort() > 0 || StringUtils.hasText(uri.getRawQuery()) || StringUtils.hasText(uri.getRawFragment())
                    || (StringUtils.hasText(uri.getPath()) && !"/".equals(uri.getPath()))) {
                throw new IllegalArgumentException();
            }
            return "https://" + host;
        } catch (Exception ignored) {
            throw new IllegalStateException("QWEATHER_API_HOST 必须是 HTTPS API Host");
        }
    }
    private String firstText(String preferred, String fallback) {
        if (StringUtils.hasText(preferred)) return preferred.trim();
        return StringUtils.hasText(fallback) ? fallback.trim() : "";
    }

    @SuppressWarnings("unchecked")
    private Map<String, Object> object(Object body) {
        if (body instanceof Map<?, ?> map) return (Map<String, Object>) map;
        Object parsed = JSON.parse(String.valueOf(body));
        if (!(parsed instanceof Map<?, ?> map)) throw new IllegalArgumentException("天气服务返回无效数据");
        return (Map<String, Object>) map;
    }
    @SuppressWarnings("unchecked")
    private List<Map<String, Object>> list(Object value) {
        if (!(value instanceof List<?> list)) return List.of();
        List<Map<String, Object>> result = new ArrayList<>();
        for (Object item : list) if (item instanceof Map<?, ?> map) result.add((Map<String, Object>) map);
        return List.copyOf(result);
    }
    private String text(Object value, String fallback) { return value instanceof String text && StringUtils.hasText(text) ? text : fallback; }

    private static final class QWeatherHttpException extends RuntimeException {
        private final int status;
        private final String body;
        private QWeatherHttpException(int status, String body) {
            this.status = status;
            this.body = body;
        }
        private int status() { return status; }
        private String body() { return body; }
    }

    public record WeatherResult(String city, String admin, String country, Map<String, Object> now,
                                List<Map<String, Object>> daily, String sourceUrl) { }
}
