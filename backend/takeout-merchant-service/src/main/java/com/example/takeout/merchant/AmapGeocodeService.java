package com.example.takeout.merchant;

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

    public ResolvedLocation resolve(String address, String city) {
        String normalized = address == null ? "" : address.trim();
        if (normalized.isBlank()) {
            throw new ServiceException(400, "请输入地名或详细地址");
        }
        ResolvedLocation fallback = fallbackLocation(normalized);
        if (webServiceKey == null || webServiceKey.isBlank()) {
            if (fallback != null) {
                return fallback;
            }
            throw new ServiceException(500, "后端未配置高德 Web 服务 Key");
        }
        try {
            String url = AMAP_GEOCODE_URL
                    + "?key=" + encode(webServiceKey)
                    + "&address=" + encode(normalized)
                    + "&city=" + encode(city == null ? "" : city.trim())
                    + "&output=JSON";
            HttpRequest request = HttpRequest.newBuilder(URI.create(url)).GET().build();
            HttpResponse<String> response = httpClient.send(request, HttpResponse.BodyHandlers.ofString());
            return parseGeocode(response.body(), normalized);
        } catch (ServiceException exception) {
            if (fallback != null) {
                return fallback;
            }
            throw exception;
        } catch (Exception exception) {
            if (fallback != null) {
                return fallback;
            }
            throw new ServiceException(502, "地点解析失败，请换一个更具体的位置");
        }
    }

    public List<Map<String, Object>> suggestions(String keyword, String city) {
        String normalized = keyword == null ? "" : keyword.trim();
        if (normalized.isBlank()) {
            return List.of();
        }
        if (webServiceKey == null || webServiceKey.isBlank()) {
            return fallbackSuggestions(normalized);
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
            List<Map<String, Object>> suggestions = parseSuggestions(response.body());
            return suggestions.isEmpty() ? fallbackSuggestions(normalized) : suggestions;
        } catch (Exception exception) {
            return fallbackSuggestions(normalized);
        }
    }

    private ResolvedLocation parseGeocode(String body, String fallbackAddress) throws Exception {
        JsonNode root = objectMapper.readTree(body);
        if (!"1".equals(root.path("status").asText()) || root.path("geocodes").isEmpty()) {
            throw new ServiceException(404, "未找到该位置，请输入更具体的地址");
        }
        JsonNode geocode = root.path("geocodes").get(0);
        String location = geocode.path("location").asText("");
        String[] parts = location.split(",");
        if (parts.length != 2) {
            throw new ServiceException(404, "该位置没有有效经纬度");
        }
        String formattedAddress = geocode.path("formatted_address").asText(fallbackAddress);
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
            result.add(locationItem(
                    tip.path("name").asText(""),
                    tip.path("address").asText(""),
                    tip.path("district").asText(""),
                    Double.parseDouble(parts[0]),
                    Double.parseDouble(parts[1])
            ));
            if (result.size() >= 8) {
                break;
            }
        }
        return result;
    }

    private List<Map<String, Object>> fallbackSuggestions(String keyword) {
        return fallbackSeeds().stream()
                .filter(item -> {
                    String text = String.join(" ",
                            String.valueOf(item.get("name")),
                            String.valueOf(item.get("address")),
                            String.valueOf(item.get("district")));
                    return text.contains(keyword) || keyword.contains(String.valueOf(item.get("name")));
                })
                .limit(8)
                .toList();
    }

    private ResolvedLocation fallbackLocation(String address) {
        return fallbackSeeds().stream()
                .filter(item -> {
                    String text = String.join(" ",
                            String.valueOf(item.get("name")),
                            String.valueOf(item.get("address")),
                            String.valueOf(item.get("district")));
                    return text.contains(address) || address.contains(String.valueOf(item.get("name")));
                })
                .findFirst()
                .map(item -> new ResolvedLocation(
                        doubleValue(item.get("longitude")),
                        doubleValue(item.get("latitude")),
                        String.valueOf(item.get("address"))))
                .orElse(null);
    }

    private List<Map<String, Object>> fallbackSeeds() {
        return List.of(
                locationItem("望京SOHO", "北京市朝阳区望京街6号", "北京市朝阳区", 116.481488, 39.996214),
                locationItem("中关村大街", "北京市海淀区中关村大街27号", "北京市海淀区", 116.316726, 39.983238),
                locationItem("体育西路", "广州市天河区体育西路101号", "广州市天河区", 113.327055, 23.132535),
                locationItem("黄埔大沙东", "广州市黄埔区大沙东路258号", "广州市黄埔区", 113.458210, 23.108320),
                locationItem("徐家汇", "上海市徐汇区漕溪北路339号", "上海市徐汇区", 121.436512, 31.194179)
        );
    }

    private Map<String, Object> locationItem(String name, String address, String district, double longitude, double latitude) {
        Map<String, Object> item = new LinkedHashMap<>();
        item.put("name", name);
        item.put("address", address);
        item.put("district", district);
        item.put("longitude", longitude);
        item.put("latitude", latitude);
        return item;
    }

    private double doubleValue(Object value) {
        return value instanceof Number number ? number.doubleValue() : Double.parseDouble(String.valueOf(value));
    }

    private String encode(String value) {
        return URLEncoder.encode(value == null ? "" : value, StandardCharsets.UTF_8);
    }

    public record ResolvedLocation(double longitude, double latitude, String address) {
    }
}
