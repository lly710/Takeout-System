package com.example.takeout.user;

import com.example.takeout.common.exception.ServiceException;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.io.IOException;
import java.net.URI;
import java.net.URLEncoder;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

/**
 * 服务端高德地理编码服务：前端只提交地名，高德 Web Key 始终保存在后端。
 */
@Service
public class AmapGeocodeService {

    private static final String AMAP_GEOCODE_URL = "https://restapi.amap.com/v3/geocode/geo";
    private static final String AMAP_INPUT_TIPS_URL = "https://restapi.amap.com/v3/assistant/inputtips";

    private final String webServiceKey;
    private final ObjectMapper objectMapper;
    private final HttpClient httpClient = HttpClient.newHttpClient();

    public AmapGeocodeService(@Value("${amap.web-service-key:}") String webServiceKey,
                              ObjectMapper objectMapper) {
        this.webServiceKey = webServiceKey;
        this.objectMapper = objectMapper;
    }

    public Map<String, Object> geocode(String keyword, String city) {
        if (keyword == null || keyword.isBlank()) {
            throw new ServiceException(400, "请输入要搜索的位置");
        }
        if (webServiceKey == null || webServiceKey.isBlank()) {
            throw new ServiceException(500, "后端未配置高德 Web 服务 Key");
        }

        try {
            String url = AMAP_GEOCODE_URL
                    + "?key=" + encode(webServiceKey)
                    + "&address=" + encode(keyword.trim())
                    + "&city=" + encode(city == null ? "" : city.trim())
                    + "&output=JSON";
            HttpRequest request = HttpRequest.newBuilder(URI.create(url)).GET().build();
            HttpResponse<String> response = httpClient.send(request, HttpResponse.BodyHandlers.ofString());
            return parseGeocode(response.body(), keyword.trim());
        } catch (ServiceException exception) {
            throw exception;
        } catch (Exception exception) {
            throw new ServiceException(500, "位置解析失败，请稍后重试");
        }
    }

    public List<Map<String, Object>> suggestions(String keyword, String city) {
        if (keyword == null || keyword.isBlank()) {
            return List.of();
        }
        if (webServiceKey == null || webServiceKey.isBlank()) {
            throw new ServiceException(500, "后端未配置高德 Web 服务 Key");
        }
        try {
            String url = AMAP_INPUT_TIPS_URL
                    + "?key=" + encode(webServiceKey)
                    + "&keywords=" + encode(keyword.trim())
                    + "&city=" + encode(city == null ? "" : city.trim())
                    + "&datatype=all"
                    + "&output=JSON";
            HttpRequest request = HttpRequest.newBuilder(URI.create(url)).GET().build();
            HttpResponse<String> response = httpClient.send(request, HttpResponse.BodyHandlers.ofString());
            return parseSuggestions(response.body());
        } catch (ServiceException exception) {
            throw exception;
        } catch (Exception exception) {
            throw new ServiceException(500, "位置联想失败，请稍后重试");
        }
    }

    private Map<String, Object> parseGeocode(String body, String fallbackAddress) throws IOException {
        JsonNode root = objectMapper.readTree(body);
        if (!"1".equals(root.path("status").asText()) || root.path("geocodes").isEmpty()) {
            String message = root.path("info").asText("未找到该位置");
            throw new ServiceException(404, "未找到该位置：" + message);
        }

        JsonNode geocode = root.path("geocodes").get(0);
        String location = geocode.path("location").asText("");
        String[] values = location.split(",");
        if (values.length != 2) {
            throw new ServiceException(404, "未找到该位置的经纬度");
        }

        Map<String, Object> result = new LinkedHashMap<>();
        result.put("address", readableAddress(geocode, fallbackAddress));
        result.put("longitude", Double.parseDouble(values[0]));
        result.put("latitude", Double.parseDouble(values[1]));
        result.put("province", geocode.path("province").asText(""));
        result.put("city", geocode.path("city").asText(""));
        result.put("district", geocode.path("district").asText(""));
        return result;
    }

    private List<Map<String, Object>> parseSuggestions(String body) throws IOException {
        JsonNode root = objectMapper.readTree(body);
        if (!"1".equals(root.path("status").asText())) {
            return List.of();
        }
        List<Map<String, Object>> result = new ArrayList<>();
        for (JsonNode tip : root.path("tips")) {
            String location = tip.path("location").asText("");
            String[] values = location.split(",");
            if (values.length != 2 || values[0].isBlank() || values[1].isBlank()) {
                continue;
            }
            Map<String, Object> item = new LinkedHashMap<>();
            item.put("name", tip.path("name").asText(""));
            item.put("address", tip.path("address").asText(""));
            item.put("district", tip.path("district").asText(""));
            item.put("longitude", Double.parseDouble(values[0]));
            item.put("latitude", Double.parseDouble(values[1]));
            result.add(item);
            if (result.size() >= 8) {
                break;
            }
        }
        return result;
    }

    private String readableAddress(JsonNode geocode, String fallbackAddress) {
        String formatted = geocode.path("formatted_address").asText("");
        if (!formatted.isBlank()) {
            return formatted;
        }
        return fallbackAddress;
    }

    private String encode(String value) {
        return URLEncoder.encode(value == null ? "" : value, StandardCharsets.UTF_8);
    }
}
