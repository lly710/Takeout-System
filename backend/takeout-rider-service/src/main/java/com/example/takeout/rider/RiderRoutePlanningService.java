package com.example.takeout.rider;

import com.example.takeout.common.db.PlatformDataRepository;
import com.example.takeout.common.dto.Coordinate;
import com.example.takeout.common.dto.OrderSummary;
import com.example.takeout.common.dto.RoutePoint;
import com.example.takeout.common.enums.OrderStatus;
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
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

@Service
public class RiderRoutePlanningService {

    private static final String AMAP_BICYCLING_URL = "https://restapi.amap.com/v5/direction/bicycling";
    private static final int MAX_DYNAMIC_ORDERS = 8;
    private static final long INF = Long.MAX_VALUE / 4;

    private final String webServiceKey;
    private final PlatformDataRepository repository;
    private final ObjectMapper objectMapper;
    private final HttpClient httpClient = HttpClient.newHttpClient();

    public RiderRoutePlanningService(@Value("${amap.web-service-key:}") String webServiceKey,
                                     PlatformDataRepository repository,
                                     ObjectMapper objectMapper) {
        this.webServiceKey = webServiceKey;
        this.repository = repository;
        this.objectMapper = objectMapper;
    }

    public Map<String, Object> plan(Long riderId, String preferredPointKey, String navigationPointKey) {
        Map<String, Object> profile = repository.getRiderProfile(riderId);
        Coordinate rider = new Coordinate(
                number(profile.get("longitude"), number(profile.get("serviceLongitude"), 116.4108)),
                number(profile.get("latitude"), number(profile.get("serviceLatitude"), 39.9202)),
                "骑手当前位置"
        );
        List<RouteOrder> orders = activeOrders(riderId);
        if (orders.isEmpty()) {
            Map<String, Object> empty = linkedMap(
                    "riderId", riderId,
                    "algorithm", "DP_WITH_PICKUP_DELIVERY_CONSTRAINT",
                    "generatedAt", LocalDateTime.now().toString(),
                    "riderLocation", pointMap("rider", 0L, "RIDER", "骑手当前位置", profile.get("address"), rider, 0, true, false),
                    "points", List.of(),
                    "nextPoint", null,
                    "polyline", List.of(new RoutePoint(rider.longitude(), rider.latitude())),
                    "totalDistanceMeters", 0,
                    "totalDurationSeconds", 0,
                    "message", "当前没有待规划的配送订单"
            );
            persist(riderId, preferredPointKey, navigationPointKey, empty);
            return empty;
        }

        Preferred preferred = resolvePreferred(orders, preferredPointKey);
        SearchResult search = shortestRoute(rider, orders, preferred);
        List<Map<String, Object>> points = new ArrayList<>();
        List<RoutePoint> polyline = new ArrayList<>();
        Coordinate cursor = rider;
        int sequence = 1;
        long totalDistance = 0;
        long totalDuration = 0;
        for (RouteStop stop : search.stops()) {
            RouteLeg leg = routeLeg(cursor, stop.coordinate());
            totalDistance += leg.distanceMeters();
            totalDuration += leg.durationSeconds();
            appendPolyline(polyline, leg.polyline());
            points.add(pointMap(
                    stop.key(),
                    stop.order().summary().orderId(),
                    stop.type(),
                    stop.title(),
                    stop.address(),
                    stop.coordinate(),
                    sequence++,
                    stop.key().equals(preferredPointKey),
                    stop.key().equals(navigationPointKey)
            ));
            cursor = stop.coordinate();
        }
        if (polyline.isEmpty()) {
            polyline.add(new RoutePoint(rider.longitude(), rider.latitude()));
        }

        Map<String, Object> result = linkedMap(
                "riderId", riderId,
                "algorithm", "DP_WITH_PICKUP_DELIVERY_CONSTRAINT",
                "generatedAt", LocalDateTime.now().toString(),
                "riderLocation", pointMap("rider", 0L, "RIDER", "骑手当前位置", profile.get("address"), rider, 0, true, false),
                "points", points,
                "nextPoint", points.isEmpty() ? null : points.get(0),
                "polyline", polyline,
                "totalDistanceMeters", totalDistance,
                "totalDurationSeconds", totalDuration,
                "message", preferredPointKey == null || preferredPointKey.isBlank() ? "已按最短路规划" : "已按优先点重新规划"
        );
        persist(riderId, preferredPointKey, navigationPointKey, result);
        return result;
    }

    public Map<String, Object> prefer(Long riderId, Map<String, Object> body) {
        String pointKey = String.valueOf(body == null ? "" : body.getOrDefault("pointKey", ""));
        Map<String, Object> plan = plan(riderId, pointKey, null);
        repository.recordRiderRouteEvent(riderId, orderIdFromPoint(plan.get("nextPoint")), pointTypeFromPoint(plan.get("nextPoint")), "PREFER_POINT");
        return plan;
    }

    public Map<String, Object> navigate(Long riderId, Map<String, Object> body) {
        String pointKey = String.valueOf(body == null ? "" : body.getOrDefault("pointKey", ""));
        Map<String, Object> plan = plan(riderId, pointKey, pointKey);
        repository.recordRiderRouteEvent(riderId, orderIdFromPoint(plan.get("nextPoint")), pointTypeFromPoint(plan.get("nextPoint")), "START_NAVIGATION");
        return plan;
    }

    @SuppressWarnings("unchecked")
    private List<RouteOrder> activeOrders(Long riderId) {
        Map<String, Object> buckets = repository.riderTaskBuckets(riderId);
        List<OrderSummary> raw = new ArrayList<>();
        raw.addAll((List<OrderSummary>) buckets.getOrDefault("pickup", List.of()));
        raw.addAll((List<OrderSummary>) buckets.getOrDefault("delivering", List.of()));
        return raw.stream()
                .filter(order -> order != null && order.status() != OrderStatus.COMPLETED)
                .limit(MAX_DYNAMIC_ORDERS)
                .map(RouteOrder::new)
                .toList();
    }

    private Preferred resolvePreferred(List<RouteOrder> orders, String pointKey) {
        if (pointKey == null || pointKey.isBlank()) {
            return null;
        }
        for (int i = 0; i < orders.size(); i++) {
            RouteOrder order = orders.get(i);
            if (order.pickupKey().equals(pointKey)) {
                return new Preferred(i, "PICKUP");
            }
            if (order.deliveryKey().equals(pointKey)) {
                if (!order.alreadyPicked()) {
                    throw new ServiceException(400, "该订单还未取货，不能优先送货");
                }
                return new Preferred(i, "DELIVERY");
            }
        }
        throw new ServiceException(404, "路线点不存在或不属于当前骑手");
    }

    private SearchResult shortestRoute(Coordinate start, List<RouteOrder> orders, Preferred preferred) {
        Map<String, RouteLeg> legCache = new HashMap<>();
        List<RouteStop> prefix = new ArrayList<>();
        int pickedMask = 0;
        int deliveredMask = 0;
        Coordinate current = start;
        for (int i = 0; i < orders.size(); i++) {
            if (orders.get(i).alreadyPicked()) {
                pickedMask |= bit(i);
            }
        }
        if (preferred != null) {
            RouteOrder order = orders.get(preferred.index());
            RouteStop stop = "PICKUP".equals(preferred.type()) ? order.pickupStop() : order.deliveryStop();
            prefix.add(stop);
            if ("PICKUP".equals(preferred.type())) {
                pickedMask |= bit(preferred.index());
            } else {
                deliveredMask |= bit(preferred.index());
            }
            current = stop.coordinate();
        }
        DpResult suffix = dp(current, pickedMask, deliveredMask, orders, legCache, new HashMap<>());
        List<RouteStop> stops = new ArrayList<>(prefix);
        stops.addAll(suffix.stops());
        return new SearchResult(stops);
    }

    private DpResult dp(Coordinate current,
                        int pickedMask,
                        int deliveredMask,
                        List<RouteOrder> orders,
                        Map<String, RouteLeg> legCache,
                        Map<String, DpResult> memo) {
        int allMask = (1 << orders.size()) - 1;
        if (deliveredMask == allMask) {
            return new DpResult(0, List.of());
        }
        String memoKey = coordKey(current) + "|" + pickedMask + "|" + deliveredMask;
        if (memo.containsKey(memoKey)) {
            return memo.get(memoKey);
        }
        DpResult best = new DpResult(INF, List.of());
        for (int i = 0; i < orders.size(); i++) {
            RouteOrder order = orders.get(i);
            if ((pickedMask & bit(i)) == 0) {
                best = chooseBetter(best, current, order.pickupStop(), pickedMask | bit(i), deliveredMask, orders, legCache, memo);
            } else if ((deliveredMask & bit(i)) == 0) {
                best = chooseBetter(best, current, order.deliveryStop(), pickedMask, deliveredMask | bit(i), orders, legCache, memo);
            }
        }
        memo.put(memoKey, best);
        return best;
    }

    private DpResult chooseBetter(DpResult best,
                                  Coordinate current,
                                  RouteStop stop,
                                  int nextPickedMask,
                                  int nextDeliveredMask,
                                  List<RouteOrder> orders,
                                  Map<String, RouteLeg> legCache,
                                  Map<String, DpResult> memo) {
        RouteLeg leg = routeLeg(current, stop.coordinate(), legCache);
        DpResult next = dp(stop.coordinate(), nextPickedMask, nextDeliveredMask, orders, legCache, memo);
        long distance = safeAdd(leg.distanceMeters(), next.distanceMeters());
        if (distance >= best.distanceMeters()) {
            return best;
        }
        List<RouteStop> stops = new ArrayList<>();
        stops.add(stop);
        stops.addAll(next.stops());
        return new DpResult(distance, stops);
    }

    private RouteLeg routeLeg(Coordinate origin, Coordinate destination) {
        return routeLeg(origin, destination, new HashMap<>());
    }

    private RouteLeg routeLeg(Coordinate origin, Coordinate destination, Map<String, RouteLeg> cache) {
        String key = coordKey(origin) + "->" + coordKey(destination);
        if (cache.containsKey(key)) {
            return cache.get(key);
        }
        RouteLeg leg = queryAmapLeg(origin, destination);
        cache.put(key, leg);
        return leg;
    }

    private RouteLeg queryAmapLeg(Coordinate origin, Coordinate destination) {
        if (webServiceKey == null || webServiceKey.isBlank()) {
            return fallbackLeg(origin, destination);
        }
        try {
            String url = AMAP_BICYCLING_URL
                    + "?key=" + encode(webServiceKey)
                    + "&origin=" + encode(origin.longitude() + "," + origin.latitude())
                    + "&destination=" + encode(destination.longitude() + "," + destination.latitude())
                    + "&show_fields=cost,polyline";
            HttpResponse<String> response = httpClient.send(HttpRequest.newBuilder(URI.create(url)).GET().build(), HttpResponse.BodyHandlers.ofString());
            return parseLeg(response.body(), origin, destination);
        } catch (Exception ignored) {
            return fallbackLeg(origin, destination);
        }
    }

    private RouteLeg parseLeg(String body, Coordinate origin, Coordinate destination) throws IOException {
        JsonNode root = objectMapper.readTree(body);
        if (!"1".equals(root.path("status").asText()) || root.path("route").path("paths").isEmpty()) {
            return fallbackLeg(origin, destination);
        }
        JsonNode path = root.path("route").path("paths").get(0);
        List<RoutePoint> polyline = new ArrayList<>();
        for (JsonNode step : path.path("steps")) {
            appendRawPolyline(polyline, step.path("polyline").asText());
        }
        return new RouteLeg(
                path.path("distance").asLong((long) Math.round(haversineMeters(origin, destination) * 1.25)),
                path.path("cost").path("duration").asLong(0),
                polyline.isEmpty() ? fallbackPolyline(origin, destination) : polyline
        );
    }

    private RouteLeg fallbackLeg(Coordinate origin, Coordinate destination) {
        long distance = Math.round(haversineMeters(origin, destination) * 1.25);
        return new RouteLeg(distance, Math.max(60, distance / 4), fallbackPolyline(origin, destination));
    }

    private void appendRawPolyline(List<RoutePoint> polyline, String raw) {
        if (raw == null || raw.isBlank()) {
            return;
        }
        for (String pair : raw.split(";")) {
            String[] values = pair.split(",");
            if (values.length == 2) {
                polyline.add(new RoutePoint(Double.parseDouble(values[0]), Double.parseDouble(values[1])));
            }
        }
    }

    private void appendPolyline(List<RoutePoint> target, List<RoutePoint> source) {
        if (source == null || source.isEmpty()) {
            return;
        }
        if (!target.isEmpty()) {
            target.remove(target.size() - 1);
        }
        target.addAll(source);
    }

    private List<RoutePoint> fallbackPolyline(Coordinate origin, Coordinate destination) {
        return List.of(
                new RoutePoint(origin.longitude(), origin.latitude()),
                new RoutePoint((origin.longitude() + destination.longitude()) / 2, (origin.latitude() + destination.latitude()) / 2),
                new RoutePoint(destination.longitude(), destination.latitude())
        );
    }

    private Map<String, Object> pointMap(String key,
                                         Long orderId,
                                         String type,
                                         String title,
                                         Object address,
                                         Coordinate coordinate,
                                         int sequence,
                                         boolean preferred,
                                         boolean navigating) {
        return linkedMap(
                "pointKey", key,
                "orderId", orderId,
                "type", type,
                "title", title,
                "address", String.valueOf(address == null ? "" : address),
                "longitude", coordinate.longitude(),
                "latitude", coordinate.latitude(),
                "sequence", sequence,
                "preferred", preferred,
                "navigating", navigating
        );
    }

    private void persist(Long riderId, String preferredPointKey, String navigationPointKey, Map<String, Object> plan) {
        try {
            repository.saveRiderRoutePlan(
                    riderId,
                    preferredPointKey,
                    navigationPointKey,
                    intValue(plan.get("totalDistanceMeters")),
                    intValue(plan.get("totalDurationSeconds")),
                    objectMapper.writeValueAsString(plan)
            );
        } catch (Exception ignored) {
            // Route planning must still be usable if optional persistence fails.
        }
    }

    private Long orderIdFromPoint(Object value) {
        if (value instanceof Map<?, ?> map && map.get("orderId") instanceof Number number) {
            return number.longValue();
        }
        return null;
    }

    private String pointTypeFromPoint(Object value) {
        if (value instanceof Map<?, ?> map) {
            Object type = map.get("type");
            return type == null ? "" : String.valueOf(type);
        }
        return "";
    }

    private double number(Object value, double fallback) {
        if (value instanceof Number number) {
            return number.doubleValue();
        }
        try {
            return Double.parseDouble(String.valueOf(value));
        } catch (Exception ignored) {
            return fallback;
        }
    }

    private int intValue(Object value) {
        if (value instanceof Number number) {
            return number.intValue();
        }
        return 0;
    }

    private int bit(int index) {
        return 1 << index;
    }

    private long safeAdd(long left, long right) {
        if (left >= INF || right >= INF) {
            return INF;
        }
        return Math.min(INF, left + right);
    }

    private String coordKey(Coordinate coordinate) {
        return String.format("%.6f,%.6f", coordinate.longitude(), coordinate.latitude());
    }

    private String encode(String value) {
        return URLEncoder.encode(value, StandardCharsets.UTF_8);
    }

    private double haversineMeters(Coordinate from, Coordinate to) {
        double earthRadius = 6371000;
        double deltaLat = Math.toRadians(to.latitude() - from.latitude());
        double deltaLng = Math.toRadians(to.longitude() - from.longitude());
        double startLat = Math.toRadians(from.latitude());
        double endLat = Math.toRadians(to.latitude());
        double a = Math.sin(deltaLat / 2) * Math.sin(deltaLat / 2)
                + Math.cos(startLat) * Math.cos(endLat) * Math.sin(deltaLng / 2) * Math.sin(deltaLng / 2);
        return earthRadius * 2 * Math.atan2(Math.sqrt(a), Math.sqrt(1 - a));
    }

    private Map<String, Object> linkedMap(Object... pairs) {
        Map<String, Object> map = new LinkedHashMap<>();
        for (int i = 0; i + 1 < pairs.length; i += 2) {
            map.put(String.valueOf(pairs[i]), pairs[i + 1]);
        }
        return map;
    }

    private record Preferred(int index, String type) {
    }

    private record RouteLeg(long distanceMeters, long durationSeconds, List<RoutePoint> polyline) {
    }

    private record SearchResult(List<RouteStop> stops) {
    }

    private record DpResult(long distanceMeters, List<RouteStop> stops) {
    }

    private record RouteStop(String key,
                             RouteOrder order,
                             String type,
                             String title,
                             String address,
                             Coordinate coordinate) {
    }

    private static final class RouteOrder {
        private final OrderSummary summary;

        private RouteOrder(OrderSummary summary) {
            this.summary = summary;
        }

        private OrderSummary summary() {
            return summary;
        }

        private boolean alreadyPicked() {
            return summary.status() == OrderStatus.ARRIVED_STORE || summary.status() == OrderStatus.DELIVERING;
        }

        private String pickupKey() {
            return "pickup-" + summary.orderId();
        }

        private String deliveryKey() {
            return "delivery-" + summary.orderId();
        }

        private RouteStop pickupStop() {
            return new RouteStop(
                    pickupKey(),
                    this,
                    "PICKUP",
                    summary.merchant().name(),
                    summary.merchant().coordinate().label(),
                    summary.merchant().coordinate()
            );
        }

        private RouteStop deliveryStop() {
            return new RouteStop(
                    deliveryKey(),
                    this,
                    "DELIVERY",
                    summary.userName(),
                    summary.userCoordinate().label(),
                    summary.userCoordinate()
            );
        }
    }
}
