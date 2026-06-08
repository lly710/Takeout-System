package com.example.takeout.rider;

import com.example.takeout.common.db.PlatformDataRepository;
import com.example.takeout.common.dto.Coordinate;
import com.example.takeout.common.dto.RoutePlanSnapshot;
import com.example.takeout.common.dto.RoutePoint;
import com.example.takeout.common.dto.RouteStep;
import com.example.takeout.common.mock.MockPlatformData;
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
import java.util.List;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

/**
 * 高德路线规划服务，优先调用真实 Web 服务，失败时自动回退到模拟路线。
 */
@Service
public class AmapRouteService {

    private static final String AMAP_BICYCLING_URL = "https://restapi.amap.com/v5/direction/bicycling";
    private final String webServiceKey;
    private final DeliveryTrackingStore trackingStore;
    private final PlatformDataRepository repository;
    private final ObjectMapper objectMapper;
    private final HttpClient httpClient = HttpClient.newHttpClient();

    public AmapRouteService(@Value("${amap.web-service-key:}") String webServiceKey,
                            DeliveryTrackingStore trackingStore,
                            PlatformDataRepository repository,
                            ObjectMapper objectMapper) {
        this.webServiceKey = webServiceKey;
        this.trackingStore = trackingStore;
        this.repository = repository;
        this.objectMapper = objectMapper;
    }

    public RoutePlanSnapshot planRoute(Long orderId, String type) {
        var tracking = trackingStore.getTrackingSnapshot(orderId);
        Coordinate destination = "pickup".equalsIgnoreCase(type)
                ? tracking.merchantCoordinate()
                : tracking.userCoordinate();
        Coordinate origin = new Coordinate(
                tracking.riderLocation().longitude(),
                tracking.riderLocation().latitude(),
                "Rider Location"
        );

        if (destination == null || (destination.longitude() == 0.0 && destination.latitude() == 0.0)) {
            destination = "pickup".equalsIgnoreCase(type)
                    ? repository.firstMerchant().coordinate()
                    : MockPlatformData.demoOrder().userCoordinate();
        }

        if (webServiceKey == null || webServiceKey.isBlank()) {
            return fallbackRoute(type, origin, destination);
        }

        try {
            String originValue = origin.longitude() + "," + origin.latitude();
            String destinationValue = destination.longitude() + "," + destination.latitude();
            String url = AMAP_BICYCLING_URL
                    + "?key=" + encode(webServiceKey)
                    + "&origin=" + encode(originValue)
                    + "&destination=" + encode(destinationValue)
                    + "&show_fields=cost,polyline";
            HttpRequest request = HttpRequest.newBuilder(URI.create(url)).GET().build();
            HttpResponse<String> response = httpClient.send(request, HttpResponse.BodyHandlers.ofString());
            return parseRoute(type, response.body(), origin, destination);
        } catch (Exception ignored) {
            return fallbackRoute(type, origin, destination);
        }
    }

    private RoutePlanSnapshot parseRoute(String type, String body, Coordinate origin, Coordinate destination) throws IOException {
        JsonNode root = objectMapper.readTree(body);
        if (!"1".equals(root.path("status").asText()) || root.path("route").path("paths").isEmpty()) {
            return fallbackRoute(type, origin, destination);
        }

        JsonNode path = root.path("route").path("paths").get(0);
        List<RouteStep> steps = new ArrayList<>();
        List<RoutePoint> polyline = new ArrayList<>();
        for (JsonNode stepNode : path.path("steps")) {
            steps.add(new RouteStep(
                    stepNode.path("instruction").asText(),
                    stepNode.path("road_name").asText(),
                    stepNode.path("step_distance").asText(),
                    stepNode.path("cost").path("duration").asText()
            ));
            appendPolyline(polyline, stepNode.path("polyline").asText());
        }
        return new RoutePlanSnapshot(
                type,
                path.path("distance").asText(),
                path.path("cost").path("duration").asText(),
                polyline.isEmpty() ? defaultPolyline(origin, destination) : polyline,
                steps
        );
    }

    private RoutePlanSnapshot fallbackRoute(String type, Coordinate origin, Coordinate destination) {
        return new RoutePlanSnapshot(
                type,
                "1800",
                "720",
                defaultPolyline(origin, destination),
                List.of(
                        new RouteStep("Leave current location", "Mock Route", "600", "240"),
                        new RouteStep("Ride along the recommended road", "Mock Route", "800", "300"),
                        new RouteStep("Arrive near the destination", "Mock Route", "400", "180")
                )
        );
    }

    private List<RoutePoint> defaultPolyline(Coordinate origin, Coordinate destination) {
        return List.of(
                new RoutePoint(origin.longitude(), origin.latitude()),
                new RoutePoint((origin.longitude() + destination.longitude()) / 2, (origin.latitude() + destination.latitude()) / 2),
                new RoutePoint(destination.longitude(), destination.latitude())
        );
    }

    private void appendPolyline(List<RoutePoint> polyline, String rawPolyline) {
        if (rawPolyline == null || rawPolyline.isBlank()) {
            return;
        }
        for (String pair : rawPolyline.split(";")) {
            String[] values = pair.split(",");
            if (values.length == 2) {
                polyline.add(new RoutePoint(Double.parseDouble(values[0]), Double.parseDouble(values[1])));
            }
        }
    }

    private String encode(String value) {
        return URLEncoder.encode(value, StandardCharsets.UTF_8);
    }
}
