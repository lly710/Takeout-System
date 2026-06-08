package com.example.takeout.rider;

import com.example.takeout.common.exception.ServiceException;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
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

    public ResolvedLocation resolve(String address) {
        String normalized = address == null ? "" : address.trim();
        if (normalized.isBlank()) {
            throw new ServiceException(400, "请输入地名或地址");
        }
        ResolvedLocation fallback = fallbackLocation(normalized);
        if (webServiceKey == null || webServiceKey.isBlank()) {
            if (fallback != null) {
                return fallback;
            }
            throw new ServiceException(500, "高德地理编码服务未配置");
        }
        try {
            String url = AMAP_GEOCODE_URL
                    + "?key=" + encode(webServiceKey)
                    + "&address=" + encode(normalized);
            HttpRequest request = HttpRequest.newBuilder(URI.create(url)).GET().build();
            HttpResponse<String> response = httpClient.send(request, HttpResponse.BodyHandlers.ofString());
            ResolvedLocation resolved = parse(response.body(), normalized);
            return resolved == null && fallback != null ? fallback : resolved;
        } catch (Exception exception) {
            if (fallback != null) {
                return fallback;
            }
            throw new ServiceException(502, "地名解析失败，请换一个更具体的位置");
        }
    }

    public List<Map<String, Object>> suggestions(String keyword, String city) {
        String normalized = keyword == null ? "" : keyword.trim();
        if (normalized.isBlank()) {
            return List.of();
        }
        if (webServiceKey == null || webServiceKey.isBlank()) {
            throw new ServiceException(500, "高德地理编码服务未配置");
        }
        try {
            String url = AMAP_INPUT_TIPS_URL
                    + "?key=" + encode(webServiceKey)
                    + "&keywords=" + encode(normalized)
                    + "&city=" + encode(city == null ? "" : city.trim())
                    + "&datatype=all"
                    + "&output=JSON";
            HttpRequest request = HttpRequest.newBuilder(URI.create(url)).GET().build();
            HttpResponse<String> response = httpClient.send(request, HttpResponse.BodyHandlers.ofString());
            return parseSuggestions(response.body());
        } catch (ServiceException exception) {
            throw exception;
        } catch (Exception exception) {
            throw new ServiceException(502, "地点联想失败，请稍后重试");
        }
    }

    private ResolvedLocation parse(String body, String address) throws Exception {
        JsonNode root = objectMapper.readTree(body);
        JsonNode geocodes = root.path("geocodes");
        if (!"1".equals(root.path("status").asText()) || geocodes.isEmpty()) {
            throw new ServiceException(400, "没有找到该地名，请输入更具体的地址");
        }
        String location = geocodes.get(0).path("location").asText("");
        String[] parts = location.split(",");
        if (parts.length != 2) {
            throw new ServiceException(400, "该地名没有有效坐标，请换一个地址");
        }
        String formattedAddress = geocodes.get(0).path("formatted_address").asText(address);
        return new ResolvedLocation(Double.parseDouble(parts[0]), Double.parseDouble(parts[1]), formattedAddress);
    }

    private List<Map<String, Object>> parseSuggestions(String body) throws Exception {
        JsonNode root = objectMapper.readTree(body);
        if (!"1".equals(root.path("status").asText())) {
            return List.of();
        }
        List<Map<String, Object>> result = new ArrayList<>();
        for (JsonNode tip : root.path("tips")) {
            String location = tip.path("location").asText("");
            String[] parts = location.split(",");
            if (parts.length != 2 || parts[0].isBlank() || parts[1].isBlank()) {
                continue;
            }
            Map<String, Object> item = new LinkedHashMap<>();
            item.put("name", tip.path("name").asText(""));
            item.put("address", tip.path("address").asText(""));
            item.put("district", tip.path("district").asText(""));
            item.put("longitude", Double.parseDouble(parts[0]));
            item.put("latitude", Double.parseDouble(parts[1]));
            result.add(item);
            if (result.size() >= 8) {
                break;
            }
        }
        return result;
    }

    private ResolvedLocation fallbackLocation(String address) {
        Map<String, ResolvedLocation> seeds = Map.of(
                "北京望京", new ResolvedLocation(116.481488, 39.996214, "北京望京"),
                "广州天河", new ResolvedLocation(113.327055, 23.132535, "广州天河"),
                "上海徐汇", new ResolvedLocation(121.436512, 31.194179, "上海徐汇")
        );
        return seeds.entrySet().stream()
                .filter(entry -> address.contains(entry.getKey()))
                .map(Map.Entry::getValue)
                .findFirst()
                .orElse(null);
    }

    private String encode(String value) {
        return URLEncoder.encode(value, StandardCharsets.UTF_8);
    }

    public record ResolvedLocation(double longitude, double latitude, String address) {
    }
}
