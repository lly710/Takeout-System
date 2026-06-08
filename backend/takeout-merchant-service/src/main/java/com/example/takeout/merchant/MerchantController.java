package com.example.takeout.merchant;

import com.example.takeout.common.api.ApiResponse;
import com.example.takeout.common.auth.JwtTokenService;
import com.example.takeout.common.auth.PublicApi;
import com.example.takeout.common.auth.UserRole;
import com.example.takeout.common.db.NotificationRepository;
import com.example.takeout.common.db.PlatformDataRepository;
import java.util.Map;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/merchant")
public class MerchantController {

    private final PlatformDataRepository repository;
    private final NotificationRepository notificationRepository;
    private final JwtTokenService jwtTokenService;
    private final AmapGeocodeService amapGeocodeService;

    public MerchantController(PlatformDataRepository repository,
                              NotificationRepository notificationRepository,
                              JwtTokenService jwtTokenService,
                              AmapGeocodeService amapGeocodeService) {
        this.repository = repository;
        this.notificationRepository = notificationRepository;
        this.jwtTokenService = jwtTokenService;
        this.amapGeocodeService = amapGeocodeService;
    }

    @PublicApi
    @PostMapping("/auth/login")
    public ApiResponse<?> login(@RequestBody Map<String, Object> body) {
        Map<String, Object> profile = repository.loginMerchant(body);
        return ApiResponse.ok(Map.of(
                "token", jwtTokenService.createToken((Long) profile.get("merchantId"), UserRole.MERCHANT, String.valueOf(profile.get("name"))),
                "profile", profile
        ));
    }

    @GetMapping("/shop")
    public ApiResponse<?> shopProfile(@RequestParam(name = "merchantId", required = false, defaultValue = "1") Long merchantId) {
        return ApiResponse.ok(repository.merchantShopProfile(merchantId));
    }

    @PutMapping("/shop")
    public ApiResponse<?> updateShopProfile(@RequestParam(name = "merchantId", required = false, defaultValue = "1") Long merchantId,
                                            @RequestBody Map<String, Object> body) {
        return ApiResponse.ok("店铺信息已更新", repository.updateMerchantProfile(merchantId, body));
    }

    @GetMapping("/shop/location/suggestions")
    public ApiResponse<?> locationSuggestions(@RequestParam(name = "keyword", required = false) String keyword,
                                              @RequestParam(name = "city", required = false) String city) {
        return ApiResponse.ok(amapGeocodeService.suggestions(keyword, city));
    }

    @PostMapping("/shop/location")
    public ApiResponse<?> updateShopLocation(@RequestParam(name = "merchantId", required = false, defaultValue = "1") Long merchantId,
                                             @RequestBody Map<String, Object> body) {
        Map<String, Object> payload = body == null ? Map.of() : body;
        String address = String.valueOf(payload.getOrDefault("address", ""));
        String city = String.valueOf(payload.getOrDefault("city", ""));
        AmapGeocodeService.ResolvedLocation resolved = amapGeocodeService.resolve(address, city);
        return ApiResponse.ok("店铺地址已更新", repository.updateMerchantResolvedLocation(
                merchantId,
                resolved.address(),
                resolved.longitude(),
                resolved.latitude()
        ));
    }

    @PutMapping("/shop/delivery-area")
    public ApiResponse<?> updateDeliveryArea(@RequestParam(name = "merchantId", required = false, defaultValue = "1") Long merchantId,
                                             @RequestBody Map<String, Object> body) {
        return ApiResponse.ok("配送范围已保存", repository.updateMerchantDeliveryArea(merchantId, body == null ? Map.of() : body));
    }

    @GetMapping("/menus")
    public ApiResponse<?> menus(@RequestParam(name = "merchantId", required = false, defaultValue = "1") Long merchantId) {
        return ApiResponse.ok(repository.listMerchantMenuDetails(merchantId));
    }

    @PostMapping("/menus")
    public ApiResponse<?> createMenu(@RequestParam(name = "merchantId", required = false, defaultValue = "1") Long merchantId,
                                     @RequestBody Map<String, Object> body) {
        return ApiResponse.ok("菜品已新增", repository.createMenuItem(merchantId, body));
    }

    @PutMapping("/menus/{menuItemId}")
    public ApiResponse<?> updateMenu(@RequestParam(name = "merchantId", required = false, defaultValue = "1") Long merchantId,
                                     @PathVariable("menuItemId") Long menuItemId,
                                     @RequestBody Map<String, Object> body) {
        return ApiResponse.ok("菜品已更新", repository.updateMenuItem(merchantId, menuItemId, body));
    }

    @DeleteMapping("/menus/{menuItemId}")
    public ApiResponse<?> deleteMenu(@RequestParam(name = "merchantId", required = false, defaultValue = "1") Long merchantId,
                                     @PathVariable("menuItemId") Long menuItemId) {
        repository.deleteMenuItem(merchantId, menuItemId);
        return ApiResponse.ok("菜品已删除", repository.listMerchantMenuDetails(merchantId));
    }

    @GetMapping("/orders")
    public ApiResponse<?> orders(@RequestParam(name = "merchantId", required = false, defaultValue = "1") Long merchantId) {
        return ApiResponse.ok(repository.listOrdersForMerchant(merchantId));
    }

    @PostMapping("/orders/{orderId}/accept")
    public ApiResponse<?> accept(@RequestParam(name = "merchantId", required = false, defaultValue = "1") Long merchantId,
                                 @PathVariable("orderId") Long orderId,
                                 @RequestBody(required = false) Map<String, Object> body) {
        Map<String, Object> result = repository.acceptMerchantOrder(merchantId, orderId, body == null ? Map.of() : body);
        Map<String, Object> order = repository.queryOrderBase(orderId);
        if (order != null) {
            String orderNo = String.valueOf(order.get("order_no"));
            notificationRepository.createNotification(
                    UserRole.USER,
                    Long.parseLong(String.valueOf(order.get("user_id"))),
                    "MERCHANT_ACCEPTED",
                    "商家已接单",
                    "订单 " + orderNo + " 已由商家接单，正在准备餐品。",
                    "ORDER",
                    orderId,
                    "order:" + orderId + ":MERCHANT_ACCEPTED:USER");
            Long riderId = order.get("rider_id") instanceof Number number ? number.longValue() : 0L;
            if (riderId > 0) {
                notificationRepository.createNotification(
                        UserRole.RIDER,
                        riderId,
                        "MERCHANT_ACCEPTED",
                        "商家已接单",
                        "订单 " + orderNo + " 商家已接单，请关注取货状态。",
                        "ORDER",
                        orderId,
                        "order:" + orderId + ":MERCHANT_ACCEPTED:RIDER");
            }
            notificationRepository.createNotification(
                    UserRole.ADMIN,
                    1L,
                    "MERCHANT_ACCEPTED",
                    "商家已接单",
                    "商家 " + String.valueOf(order.get("merchant_name")) + " 已接单 " + orderNo + "。",
                    "ORDER",
                    orderId,
                    "order:" + orderId + ":MERCHANT_ACCEPTED:ADMIN");
        }
        return ApiResponse.ok("商家已接单", result);
    }

    @PostMapping("/orders/{orderId}/prepare")
    public ApiResponse<?> prepare(@RequestParam(name = "merchantId", required = false, defaultValue = "1") Long merchantId,
                                  @PathVariable("orderId") Long orderId,
                                  @RequestBody(required = false) Map<String, Object> body) {
        Map<String, Object> result = repository.prepareMerchantOrder(merchantId, orderId, body == null ? Map.of() : body);
        Map<String, Object> order = repository.queryOrderBase(orderId);
        if (order != null) {
            String orderNo = String.valueOf(order.get("order_no"));
            notificationRepository.createNotification(
                    UserRole.USER,
                    Long.parseLong(String.valueOf(order.get("user_id"))),
                    "MERCHANT_PREPARED",
                    "商家已出餐",
                    "订单 " + orderNo + " 已出餐，等待骑手到店取货。",
                    "ORDER",
                    orderId,
                    "order:" + orderId + ":MERCHANT_PREPARED:USER");
            Long riderId = order.get("rider_id") instanceof Number number ? number.longValue() : 0L;
            if (riderId > 0) {
                notificationRepository.createNotification(
                        UserRole.RIDER,
                        riderId,
                        "MERCHANT_PREPARED",
                        "商家已出餐",
                        "订单 " + orderNo + " 已出餐，请到店取货。",
                        "ORDER",
                        orderId,
                        "order:" + orderId + ":MERCHANT_PREPARED:RIDER");
            }
            notificationRepository.createNotification(
                    UserRole.ADMIN,
                    1L,
                    "MERCHANT_PREPARED",
                    "商家已出餐",
                    "订单 " + orderNo + " 已由商家确认出餐。",
                    "ORDER",
                    orderId,
                    "order:" + orderId + ":MERCHANT_PREPARED:ADMIN");
        }
        return ApiResponse.ok("商家已备餐", result);
    }

    @PostMapping("/orders/{orderId}/remind")
    public ApiResponse<?> remind(@PathVariable("orderId") Long orderId) {
        return ApiResponse.ok("催单已发送", repository.remindMerchantOrder(orderId));
    }

    @GetMapping("/notifications")
    public ApiResponse<?> notifications(@RequestParam(name = "limit", required = false, defaultValue = "10") int limit,
                                        @RequestParam(name = "merchantId", required = false, defaultValue = "1") Long merchantId) {
        return ApiResponse.ok(notificationRepository.listNotifications(UserRole.MERCHANT, merchantId, limit));
    }

    @PostMapping("/notifications/{notificationId}/read")
    public ApiResponse<?> markNotificationRead(@PathVariable("notificationId") Long notificationId,
                                               @RequestParam(name = "merchantId", required = false, defaultValue = "1") Long merchantId) {
        return ApiResponse.ok(notificationRepository.markNotificationRead(UserRole.MERCHANT, merchantId, notificationId));
    }

    @PostMapping("/notifications/read-all")
    public ApiResponse<?> markAllNotificationsRead(@RequestParam(name = "merchantId", required = false, defaultValue = "1") Long merchantId) {
        return ApiResponse.ok(notificationRepository.markAllNotificationsRead(UserRole.MERCHANT, merchantId));
    }
}
