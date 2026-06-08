package com.example.takeout.rider;

import com.example.takeout.common.api.ApiResponse;
import com.example.takeout.common.auth.JwtTokenService;
import com.example.takeout.common.auth.PublicApi;
import com.example.takeout.common.auth.UserRole;
import com.example.takeout.common.db.PlatformDataRepository;
import com.example.takeout.common.db.NotificationRepository;
import com.example.takeout.common.dto.RiderLocationUpdateRequest;
import java.time.LocalDateTime;
import java.util.Map;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/**
 * 骑手端核心接口。
 * 负责骑手登录、接单、上传定位、查看导航路线、实时轨迹查询和送达确认。
 */
@RestController
@RequestMapping("/api/rider")
public class RiderController {

    private final DeliveryTrackingStore deliveryTrackingStore;
    private final TrackingWebSocketHandler trackingWebSocketHandler;
    private final AmapRouteService amapRouteService;
    private final RiderRoutePlanningService riderRoutePlanningService;
    private final PlatformDataRepository repository;
    private final NotificationRepository notificationRepository;
    private final JwtTokenService jwtTokenService;
    private final AmapGeocodeService amapGeocodeService;

    public RiderController(DeliveryTrackingStore deliveryTrackingStore,
                           TrackingWebSocketHandler trackingWebSocketHandler,
                           AmapRouteService amapRouteService,
                           RiderRoutePlanningService riderRoutePlanningService,
                           PlatformDataRepository repository,
                           NotificationRepository notificationRepository,
                           JwtTokenService jwtTokenService,
                           AmapGeocodeService amapGeocodeService) {
        this.deliveryTrackingStore = deliveryTrackingStore;
        this.trackingWebSocketHandler = trackingWebSocketHandler;
        this.amapRouteService = amapRouteService;
        this.riderRoutePlanningService = riderRoutePlanningService;
        this.repository = repository;
        this.notificationRepository = notificationRepository;
        this.jwtTokenService = jwtTokenService;
        this.amapGeocodeService = amapGeocodeService;
    }

    @PublicApi
    @PostMapping("/auth/login")
    public ApiResponse<?> login(@RequestBody Map<String, Object> body) {
        Map<String, Object> profile = repository.loginRider(body);
        return ApiResponse.ok(Map.of(
                "token", jwtTokenService.createToken((Long) profile.get("riderId"), UserRole.RIDER, String.valueOf(profile.get("name"))),
                "profile", profile
        ));
    }

    @GetMapping("/profile")
    public ApiResponse<?> profile(@RequestParam(name = "riderId", required = false, defaultValue = "7001") Long riderId) {
        return ApiResponse.ok(repository.getRiderProfile(riderId));
    }

    @GetMapping("/dashboard")
    public ApiResponse<?> dashboard(@RequestParam(name = "riderId", required = false, defaultValue = "7001") Long riderId) {
        return ApiResponse.ok(repository.riderDashboard(riderId));
    }

    @GetMapping("/orders/available")
    public ApiResponse<?> availableOrders(@RequestParam(name = "riderId", required = false, defaultValue = "7001") Long riderId) {
        return ApiResponse.ok(repository.listAvailableOrders(riderId));
    }

    @GetMapping("/orders/buckets")
    public ApiResponse<?> taskBuckets(@RequestParam(name = "riderId", required = false, defaultValue = "7001") Long riderId) {
        return ApiResponse.ok(repository.riderTaskBuckets(riderId));
    }

    @GetMapping("/orders/history")
    public ApiResponse<?> history(@RequestParam(name = "riderId", required = false, defaultValue = "7001") Long riderId,
                                  @RequestParam(name = "filter", required = false, defaultValue = "ALL") String filter) {
        return ApiResponse.ok(repository.riderHistory(riderId, filter));
    }

    @GetMapping("/orders/dispatch-board")
    public ApiResponse<?> dispatchBoard(@RequestParam(name = "riderId", required = false, defaultValue = "7001") Long riderId) {
        return ApiResponse.ok(repository.riderDispatchBoard(riderId));
    }

    @GetMapping("/orders/{orderId}/detail")
    public ApiResponse<?> orderDetail(@PathVariable("orderId") Long orderId) {
        return ApiResponse.ok(repository.riderOrderDetail(orderId));
    }

    @PostMapping("/orders/{orderId}/grab")
    public ApiResponse<?> grab(@PathVariable("orderId") Long orderId,
                               @RequestParam(name = "riderId", required = false, defaultValue = "7001") Long riderId) {
        Map<String, Object> result = repository.assignRider(orderId, riderId);
        Map<String, Object> order = repository.queryOrderBase(orderId);
        if (order != null) {
            String orderNo = String.valueOf(order.get("order_no"));
            notificationRepository.createNotification(
                    UserRole.USER,
                    Long.parseLong(String.valueOf(order.get("user_id"))),
                    "RIDER_ACCEPTED",
                    "骑手已接单",
                    "订单 " + orderNo + " 已分配骑手，正在前往商家。",
                    "ORDER",
                    orderId,
                    "order:" + orderId + ":RIDER_ACCEPTED:USER");
            notificationRepository.createNotification(
                    UserRole.MERCHANT,
                    Long.parseLong(String.valueOf(order.get("merchant_id"))),
                    "RIDER_ACCEPTED",
                    "骑手已接单",
                    "订单 " + orderNo + " 的骑手已经接单，请准备餐品。",
                    "ORDER",
                    orderId,
                    "order:" + orderId + ":RIDER_ACCEPTED:MERCHANT");
            notificationRepository.createNotification(
                    UserRole.RIDER,
                    riderId,
                    "RIDER_ACCEPTED",
                    "接单成功",
                    "你已接单 " + orderNo + "，请前往商家取货。",
                    "ORDER",
                    orderId,
                    "order:" + orderId + ":RIDER_ACCEPTED:RIDER");
            notificationRepository.createNotification(
                    UserRole.ADMIN,
                    1L,
                    "RIDER_ACCEPTED",
                    "骑手已接单",
                    "订单 " + orderNo + " 已由骑手 " + riderId + " 接单。",
                    "ORDER",
                    orderId,
                    "order:" + orderId + ":RIDER_ACCEPTED:ADMIN");
        }
        return ApiResponse.ok("骑手已接单", result);
    }

    @PostMapping("/location/upload")
    public ApiResponse<?> uploadLocation(@RequestBody RiderLocationUpdateRequest request) {
        var snapshot = deliveryTrackingStore.updateLocation(request);
        trackingWebSocketHandler.broadcast(request.orderId(), snapshot);
        notifyStageMessage(request);
        return ApiResponse.ok("位置上传成功", Map.of(
                "time", LocalDateTime.now().toString(),
                "latest", request,
                "tracking", snapshot
        ));
    }

    @PostMapping("/location/manual")
    public ApiResponse<?> updateManualLocation(@RequestParam(name = "riderId", required = false, defaultValue = "7001") Long riderId,
                                               @RequestBody Map<String, Object> body) {
        String address = String.valueOf((body == null ? Map.of() : body).getOrDefault("address", ""));
        AmapGeocodeService.ResolvedLocation resolved = amapGeocodeService.resolve(address);
        Map<String, Object> profile = repository.updateRiderResolvedLocation(riderId, resolved.longitude(), resolved.latitude());
        return ApiResponse.ok("骑手定位已更新", Map.of(
                "profile", profile,
                "address", resolved.address()
        ));
    }

    @PostMapping("/location/suggestions")
    public ApiResponse<?> locationSuggestions(@RequestBody Map<String, Object> body) {
        String keyword = String.valueOf(body.getOrDefault("keyword", ""));
        String city = String.valueOf(body.getOrDefault("city", ""));
        return ApiResponse.ok(amapGeocodeService.suggestions(keyword, city));
    }

    @PostMapping("/location/service-area")
    public ApiResponse<?> updateServiceArea(@RequestParam(name = "riderId", required = false, defaultValue = "7001") Long riderId,
                                            @RequestBody Map<String, Object> body) {
        Map<String, Object> payload = body == null ? Map.of() : body;
        String address = String.valueOf(payload.getOrDefault("address", ""));
        AmapGeocodeService.ResolvedLocation resolved = amapGeocodeService.resolve(address);
        Map<String, Object> profile = repository.updateRiderServiceArea(riderId, Map.of(
                "address", resolved.address(),
                "longitude", resolved.longitude(),
                "latitude", resolved.latitude(),
                "riderType", String.valueOf(payload.getOrDefault("riderType", "CROWDSOURCE")),
                "serviceRadiusKm", payload.getOrDefault("serviceRadiusKm", 5)
        ));
        return ApiResponse.ok("常驻地设置已保存", Map.of(
                "profile", profile,
                "address", resolved.address()
        ));
    }

    @PostMapping("/status")
    public ApiResponse<?> updateStatus(@RequestParam(name = "riderId", required = false, defaultValue = "7001") Long riderId,
                                       @RequestBody Map<String, Object> body) {
        String status = String.valueOf((body == null ? Map.of() : body).getOrDefault("status", "ONLINE"));
        return ApiResponse.ok("骑手状态已更新", repository.updateRiderStatus(riderId, status));
    }

    @GetMapping("/orders/{orderId}/tracking")
    public ApiResponse<?> tracking(@PathVariable("orderId") Long orderId) {
        return ApiResponse.ok(deliveryTrackingStore.getTrackingSnapshot(orderId));
    }

    @GetMapping("/orders/{orderId}/navigation")
    public ApiResponse<?> navigation(@PathVariable("orderId") Long orderId) {
        var tracking = repository.getTrackingSnapshot(orderId);
        return ApiResponse.ok(Map.of(
                "orderId", orderId,
                "toMerchant", tracking.merchantCoordinate(),
                "toUser", tracking.userCoordinate()
        ));
    }

    @GetMapping("/orders/{orderId}/route")
    public ApiResponse<?> route(@PathVariable("orderId") Long orderId,
                                @RequestParam(name = "type", required = false) String type) {
        return ApiResponse.ok(amapRouteService.planRoute(orderId, type == null ? "delivery" : type));
    }

    @PostMapping("/orders/{orderId}/cancel-assignment")
    public ApiResponse<?> cancelAssignment(@PathVariable("orderId") Long orderId,
                                           @RequestParam(name = "riderId", required = false, defaultValue = "7001") Long riderId,
                                           @RequestBody(required = false) Map<String, Object> body) {
        String reason = String.valueOf((body == null ? Map.of() : body).getOrDefault("reason", "骑手取消接单，订单重新等待分配"));
        Map<String, Object> order = repository.queryOrderBase(orderId);
        Map<String, Object> result = repository.releaseRiderAssignment(orderId, riderId, reason);
        if (order != null) {
            String orderNo = String.valueOf(order.get("order_no"));
            notificationRepository.createNotification(
                    UserRole.USER,
                    Long.parseLong(String.valueOf(order.get("user_id"))),
                    "RIDER_CANCELLED_ASSIGNMENT",
                    "骑手已取消接单",
                    "订单 " + orderNo + " 的骑手已取消接单，平台正在重新分配其他骑手。",
                    "ORDER",
                    orderId,
                    "order:" + orderId + ":RIDER_CANCELLED_ASSIGNMENT:USER:" + System.currentTimeMillis());
            notificationRepository.createNotification(
                    UserRole.MERCHANT,
                    Long.parseLong(String.valueOf(order.get("merchant_id"))),
                    "RIDER_CANCELLED_ASSIGNMENT",
                    "骑手已取消接单",
                    "订单 " + orderNo + " 的骑手已取消接单，请等待其他骑手接单。",
                    "ORDER",
                    orderId,
                    "order:" + orderId + ":RIDER_CANCELLED_ASSIGNMENT:MERCHANT:" + System.currentTimeMillis());
            notificationRepository.createNotification(
                    UserRole.RIDER,
                    riderId,
                    "RIDER_CANCELLED_ASSIGNMENT",
                    "已取消接单",
                    "你已取消订单 " + orderNo + "，该订单已重新进入可接单池。",
                    "ORDER",
                    orderId,
                    "order:" + orderId + ":RIDER_CANCELLED_ASSIGNMENT:RIDER:" + System.currentTimeMillis());
            notificationRepository.createNotification(
                    UserRole.ADMIN,
                    1L,
                    "RIDER_CANCELLED_ASSIGNMENT",
                    "骑手取消接单",
                    "订单 " + orderNo + " 已由骑手 " + riderId + " 取消接单，当前等待重新分配。",
                    "ORDER",
                    orderId,
                    "order:" + orderId + ":RIDER_CANCELLED_ASSIGNMENT:ADMIN:" + System.currentTimeMillis());
        }
        return ApiResponse.ok("骑手已取消接单，订单已重新进入可接单池", result);
    }

    @GetMapping("/routes/plan")
    public ApiResponse<?> multiRoutePlan(@RequestParam(name = "riderId", required = false, defaultValue = "7001") Long riderId) {
        return ApiResponse.ok(riderRoutePlanningService.plan(riderId, null, null));
    }

    @PostMapping("/routes/prefer")
    public ApiResponse<?> preferRoutePoint(@RequestParam(name = "riderId", required = false, defaultValue = "7001") Long riderId,
                                           @RequestBody Map<String, Object> body) {
        return ApiResponse.ok("已按优先地点重新规划路线", riderRoutePlanningService.prefer(riderId, body));
    }

    @PostMapping("/routes/navigate")
    public ApiResponse<?> navigateRoutePoint(@RequestParam(name = "riderId", required = false, defaultValue = "7001") Long riderId,
                                             @RequestBody Map<String, Object> body) {
        return ApiResponse.ok("已记录导航地点", riderRoutePlanningService.navigate(riderId, body));
    }

    @PostMapping("/orders/{orderId}/complete")
    public ApiResponse<?> complete(@PathVariable("orderId") Long orderId) {
        Map<String, Object> result = repository.completeOrder(orderId);
        Map<String, Object> order = repository.queryOrderBase(orderId);
        if (order != null) {
            String orderNo = String.valueOf(order.get("order_no"));
            notificationRepository.createNotification(
                    UserRole.USER,
                    Long.parseLong(String.valueOf(order.get("user_id"))),
                    "ORDER_COMPLETED",
                    "订单已完成",
                    "订单 " + orderNo + " 已送达并完成配送。",
                    "ORDER",
                    orderId,
                    "order:" + orderId + ":ORDER_COMPLETED:USER");
            notificationRepository.createNotification(
                    UserRole.MERCHANT,
                    Long.parseLong(String.valueOf(order.get("merchant_id"))),
                    "ORDER_COMPLETED",
                    "订单已完成",
                    "订单 " + orderNo + " 已完成配送。",
                    "ORDER",
                    orderId,
                    "order:" + orderId + ":ORDER_COMPLETED:MERCHANT");
            notificationRepository.createNotification(
                    UserRole.ADMIN,
                    1L,
                    "ORDER_COMPLETED",
                    "订单已完成",
                    "订单 " + orderNo + " 已完成配送并送达。",
                    "ORDER",
                    orderId,
                    "order:" + orderId + ":ORDER_COMPLETED:ADMIN");
            Long riderId = order.get("rider_id") instanceof Number number ? number.longValue() : 0L;
            if (riderId > 0) {
                notificationRepository.createNotification(
                        UserRole.RIDER,
                        riderId,
                        "ORDER_COMPLETED",
                        "订单已完成",
                        "订单 " + orderNo + " 已确认送达。",
                        "ORDER",
                        orderId,
                        "order:" + orderId + ":ORDER_COMPLETED:RIDER");
            }
        }
        return ApiResponse.ok("订单已完成", result);
    }

    @GetMapping("/notifications")
    public ApiResponse<?> notifications(@RequestParam(name = "limit", required = false, defaultValue = "10") int limit,
                                        @RequestParam(name = "riderId", required = false, defaultValue = "7001") Long riderId) {
        return ApiResponse.ok(notificationRepository.listNotifications(UserRole.RIDER, riderId, limit));
    }

    @PostMapping("/notifications/{notificationId}/read")
    public ApiResponse<?> markNotificationRead(@PathVariable("notificationId") Long notificationId,
                                               @RequestParam(name = "riderId", required = false, defaultValue = "7001") Long riderId) {
        return ApiResponse.ok(notificationRepository.markNotificationRead(UserRole.RIDER, riderId, notificationId));
    }

    @PostMapping("/notifications/read-all")
    public ApiResponse<?> markAllNotificationsRead(@RequestParam(name = "riderId", required = false, defaultValue = "7001") Long riderId) {
        return ApiResponse.ok(notificationRepository.markAllNotificationsRead(UserRole.RIDER, riderId));
    }

    private void notifyStageMessage(RiderLocationUpdateRequest request) {
        Map<String, Object> order = repository.queryOrderBase(request.orderId());
        if (order == null) {
            return;
        }
        String stage = String.valueOf(request.stage());
        String orderNo = String.valueOf(order.get("order_no"));
        if (stage.contains("前往商家") || stage.contains("RIDER_ACCEPTED")) {
            notificationRepository.createNotification(
                    UserRole.USER,
                    Long.parseLong(String.valueOf(order.get("user_id"))),
                    "TRACK_RIDER_ACCEPTED",
                    "骑手正在前往商家",
                    "订单 " + orderNo + " 的骑手已出发，正在前往商家。",
                    "ORDER",
                    request.orderId(),
                    "order:" + request.orderId() + ":TRACK_RIDER_ACCEPTED:USER");
        }
        if (stage.contains("到店取餐") || stage.contains("ARRIVED_STORE")) {
            notificationRepository.createNotification(
                    UserRole.USER,
                    Long.parseLong(String.valueOf(order.get("user_id"))),
                    "TRACK_ARRIVED_STORE",
                    "骑手已到店取餐",
                    "订单 " + orderNo + " 的骑手已到店，正在取餐。",
                    "ORDER",
                    request.orderId(),
                    "order:" + request.orderId() + ":TRACK_ARRIVED_STORE:USER");
            notificationRepository.createNotification(
                    UserRole.MERCHANT,
                    Long.parseLong(String.valueOf(order.get("merchant_id"))),
                    "TRACK_ARRIVED_STORE",
                    "骑手已到店",
                    "订单 " + orderNo + " 的骑手已经到店，正在取餐。",
                    "ORDER",
                    request.orderId(),
                    "order:" + request.orderId() + ":TRACK_ARRIVED_STORE:MERCHANT");
            Long riderId = order.get("rider_id") instanceof Number number ? number.longValue() : request.riderId();
            notificationRepository.createNotification(
                    UserRole.RIDER,
                    riderId,
                    "TRACK_ARRIVED_STORE",
                    "已到店取货",
                    "订单 " + orderNo + " 已更新为到店取货状态。",
                    "ORDER",
                    request.orderId(),
                    "order:" + request.orderId() + ":TRACK_ARRIVED_STORE:RIDER");
            notificationRepository.createNotification(
                    UserRole.ADMIN,
                    1L,
                    "TRACK_ARRIVED_STORE",
                    "骑手已到店",
                    "订单 " + orderNo + " 骑手已到店取货。",
                    "ORDER",
                    request.orderId(),
                    "order:" + request.orderId() + ":TRACK_ARRIVED_STORE:ADMIN");
        }
        if (stage.contains("配送中") || stage.contains("DELIVERING")) {
            notificationRepository.createNotification(
                    UserRole.USER,
                    Long.parseLong(String.valueOf(order.get("user_id"))),
                    "TRACK_DELIVERING",
                    "订单配送中",
                    "订单 " + orderNo + " 正在配送中，请保持电话畅通。",
                    "ORDER",
                    request.orderId(),
                    "order:" + request.orderId() + ":TRACK_DELIVERING:USER");
            notificationRepository.createNotification(
                    UserRole.MERCHANT,
                    Long.parseLong(String.valueOf(order.get("merchant_id"))),
                    "TRACK_DELIVERING",
                    "订单配送中",
                    "订单 " + orderNo + " 骑手已开始配送。",
                    "ORDER",
                    request.orderId(),
                    "order:" + request.orderId() + ":TRACK_DELIVERING:MERCHANT");
            Long riderId = order.get("rider_id") instanceof Number number ? number.longValue() : request.riderId();
            notificationRepository.createNotification(
                    UserRole.RIDER,
                    riderId,
                    "TRACK_DELIVERING",
                    "订单配送中",
                    "订单 " + orderNo + " 已更新为配送中。",
                    "ORDER",
                    request.orderId(),
                    "order:" + request.orderId() + ":TRACK_DELIVERING:RIDER");
            notificationRepository.createNotification(
                    UserRole.ADMIN,
                    1L,
                    "TRACK_DELIVERING",
                    "订单配送中",
                    "订单 " + orderNo + " 已进入配送中。",
                    "ORDER",
                    request.orderId(),
                    "order:" + request.orderId() + ":TRACK_DELIVERING:ADMIN");
        }
        if (stage.contains("送达") || stage.contains("COMPLETED")) {
            notificationRepository.createNotification(
                    UserRole.USER,
                    Long.parseLong(String.valueOf(order.get("user_id"))),
                    "TRACK_DELIVERED",
                    "订单已送达",
                    "订单 " + orderNo + " 已送达，祝您用餐愉快。",
                    "ORDER",
                    request.orderId(),
                    "order:" + request.orderId() + ":TRACK_DELIVERED:USER");
        }
    }
}
