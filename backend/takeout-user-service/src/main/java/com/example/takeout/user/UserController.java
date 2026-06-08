package com.example.takeout.user;

import com.example.takeout.common.api.ApiResponse;
import com.example.takeout.common.auth.AuthContext;
import com.example.takeout.common.auth.JwtTokenService;
import com.example.takeout.common.auth.PublicApi;
import com.example.takeout.common.auth.RequireRole;
import com.example.takeout.common.auth.UserRole;
import com.example.takeout.common.db.PlatformDataRepository;
import com.example.takeout.common.db.NotificationRepository;
import com.example.takeout.common.storage.AliyunOssStorageService;
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
import org.springframework.web.multipart.MultipartFile;

/**
 * 用户端核心接口，覆盖账号、地址、商家浏览、购物车和轨迹查询。
 */
@RestController
@RequestMapping("/api/users")
@RequireRole(UserRole.USER)
public class UserController {

    private final PlatformDataRepository repository;
    private final NotificationRepository notificationRepository;
    private final JwtTokenService jwtTokenService;
    private final AliyunOssStorageService ossStorageService;
    private final UserVerificationCodeService verificationCodeService;
    private final AmapGeocodeService amapGeocodeService;

    public UserController(PlatformDataRepository repository,
                          NotificationRepository notificationRepository,
                          JwtTokenService jwtTokenService,
                          AliyunOssStorageService ossStorageService,
                          UserVerificationCodeService verificationCodeService,
                          AmapGeocodeService amapGeocodeService) {
        this.repository = repository;
        this.notificationRepository = notificationRepository;
        this.jwtTokenService = jwtTokenService;
        this.ossStorageService = ossStorageService;
        this.verificationCodeService = verificationCodeService;
        this.amapGeocodeService = amapGeocodeService;
    }

    @PublicApi
    @PostMapping("/auth/register")
    public ApiResponse<Map<String, Object>> register(@RequestBody Map<String, Object> body) {
        String phone = String.valueOf(body.getOrDefault("phone", ""));
        String code = String.valueOf(body.getOrDefault("code", ""));
        verificationCodeService.verifyCode(phone, "REGISTER", code);
        Map<String, Object> profile = repository.registerUser(body);
        return ApiResponse.ok("注册成功", authPayload(profile));
    }

    @PublicApi
    @PostMapping("/auth/login")
    public ApiResponse<Map<String, Object>> login(@RequestBody Map<String, Object> body) {
        Map<String, Object> profile = repository.loginUser(body);
        return ApiResponse.ok("登录成功", authPayload(profile));
    }

    @PublicApi
    @PostMapping("/auth/code/send")
    public ApiResponse<?> sendCode(@RequestBody Map<String, Object> body) {
        String phone = String.valueOf(body.getOrDefault("phone", ""));
        String purpose = String.valueOf(body.getOrDefault("purpose", "LOGIN"));
        return ApiResponse.ok("验证码已发送", verificationCodeService.sendCode(phone, purpose));
    }

    @PublicApi
    @PostMapping("/auth/code/login")
    public ApiResponse<Map<String, Object>> loginByCode(@RequestBody Map<String, Object> body) {
        String phone = String.valueOf(body.getOrDefault("phone", ""));
        String code = String.valueOf(body.getOrDefault("code", ""));
        verificationCodeService.verifyCode(phone, "LOGIN", code);
        Map<String, Object> profile = repository.loginUserByPhone(phone);
        return ApiResponse.ok("登录成功", authPayload(profile));
    }

    @PublicApi
    @PostMapping("/auth/password/reset")
    public ApiResponse<Map<String, Object>> resetPassword(@RequestBody Map<String, Object> body) {
        String phone = String.valueOf(body.getOrDefault("phone", ""));
        String code = String.valueOf(body.getOrDefault("code", ""));
        verificationCodeService.verifyCode(phone, "RESET", code);
        Map<String, Object> profile = repository.resetUserPassword(phone, String.valueOf(body.getOrDefault("password", "")));
        return ApiResponse.ok("密码重置成功", authPayload(profile));
    }

    @PostMapping("/auth/logout")
    public ApiResponse<?> logout() {
        return ApiResponse.ok("退出登录成功", Map.of("userId", currentUserId(null)));
    }

    @GetMapping("/profile")
    public ApiResponse<?> profile() {
        return ApiResponse.ok(repository.getUserProfile(currentUserId(null)));
    }

    @PutMapping("/profile")
    public ApiResponse<?> updateProfile(@RequestBody Map<String, Object> body) {
        return ApiResponse.ok("个人信息已更新", repository.updateUserProfile(currentUserId(null), body));
    }

    @PostMapping("/profile/avatar")
    public ApiResponse<?> uploadAvatar(@RequestParam("file") MultipartFile file) {
        String avatarUrl = ossStorageService.uploadAvatar(file);
        return ApiResponse.ok("头像上传成功", repository.updateUserAvatar(currentUserId(null), avatarUrl));
    }

    @GetMapping("/addresses")
    public ApiResponse<?> addresses() {
        return ApiResponse.ok(repository.listUserAddresses(currentUserId(null)));
    }

    @PostMapping("/addresses")
    public ApiResponse<?> createAddress(@RequestBody Map<String, Object> body) {
        return ApiResponse.ok("地址已保存", repository.upsertAddress(currentUserId(null), body));
    }

    @PutMapping("/addresses/{addressId}")
    public ApiResponse<?> updateAddress(@PathVariable("addressId") Long addressId, @RequestBody Map<String, Object> body) {
        body.put("id", addressId);
        return ApiResponse.ok("地址已更新", repository.upsertAddress(currentUserId(null), body));
    }

    @DeleteMapping("/addresses/{addressId}")
    public ApiResponse<?> deleteAddress(@PathVariable("addressId") Long addressId) {
        repository.deleteUserAddress(currentUserId(null), addressId);
        return ApiResponse.ok("地址已删除", repository.listUserAddresses(currentUserId(null)));
    }

    @PostMapping("/addresses/{addressId}/default")
    public ApiResponse<?> setDefaultAddress(@PathVariable("addressId") Long addressId) {
        return ApiResponse.ok("默认地址已更新", repository.setDefaultAddress(currentUserId(null), addressId));
    }

    @PostMapping("/location")
    public ApiResponse<Map<String, Object>> location(@RequestBody Map<String, Object> body) {
        body.put("userId", currentUserId(body.get("userId")));
        return ApiResponse.ok("定位已更新", repository.updateUserLocation(body));
    }

    @PostMapping("/location/search")
    public ApiResponse<?> searchLocation(@RequestBody Map<String, Object> body) {
        String keyword = String.valueOf(body.getOrDefault("keyword", ""));
        String city = String.valueOf(body.getOrDefault("city", ""));
        Map<String, Object> geocoded = amapGeocodeService.geocode(keyword, city);
        Map<String, Object> locationBody = Map.of(
                "userId", currentUserId(body.get("userId")),
                "longitude", geocoded.get("longitude"),
                "latitude", geocoded.get("latitude"),
                "address", geocoded.get("address")
        );
        repository.updateUserLocation(locationBody);
        return ApiResponse.ok("位置已切换", Map.of(
                "address", geocoded.get("address"),
                "province", geocoded.get("province"),
                "city", geocoded.get("city"),
                "district", geocoded.get("district"),
                "merchants", repository.listNearbyMerchants(
                        stringOrNull(body.get("merchantKeyword")),
                        stringOrNull(body.get("category")),
                        Double.parseDouble(String.valueOf(geocoded.get("longitude"))),
                        Double.parseDouble(String.valueOf(geocoded.get("latitude"))),
                        5.0
                )
        ));
    }

    @PostMapping("/location/suggestions")
    public ApiResponse<?> locationSuggestions(@RequestBody Map<String, Object> body) {
        String keyword = String.valueOf(body.getOrDefault("keyword", ""));
        String city = String.valueOf(body.getOrDefault("city", ""));
        return ApiResponse.ok(amapGeocodeService.suggestions(keyword, city));
    }

    @PublicApi
    @GetMapping("/merchants/categories")
    public ApiResponse<?> merchantCategories() {
        return ApiResponse.ok(repository.listMerchantCategories());
    }

    @PublicApi
    @GetMapping("/merchants/nearby")
    public ApiResponse<?> nearbyMerchants(@RequestParam(name = "keyword", required = false) String keyword,
                                          @RequestParam(name = "category", required = false) String category,
                                          @RequestParam(name = "longitude", required = false) Double longitude,
                                          @RequestParam(name = "latitude", required = false) Double latitude,
                                          @RequestParam(name = "radiusKm", required = false) Double radiusKm) {
        return ApiResponse.ok(repository.listNearbyMerchants(keyword, category, longitude, latitude, radiusKm));
    }

    @PublicApi
    @GetMapping("/merchants/{merchantId}")
    public ApiResponse<?> merchantDetail(@PathVariable("merchantId") Long merchantId) {
        return ApiResponse.ok(repository.getMerchantDetail(merchantId));
    }

    @GetMapping("/cart")
    public ApiResponse<?> cart(@RequestParam(name = "merchantId", required = false) Long merchantId) {
        return ApiResponse.ok(repository.getCart(currentUserId(null), merchantId));
    }

    @PostMapping("/cart/items")
    public ApiResponse<?> addCartItem(@RequestBody Map<String, Object> body) {
        return ApiResponse.ok("购物车已更新", repository.addCartItem(currentUserId(null), body));
    }

    @PutMapping("/cart/items/{cartItemId}")
    public ApiResponse<?> updateCartItem(@PathVariable("cartItemId") Long cartItemId, @RequestBody Map<String, Object> body) {
        return ApiResponse.ok("购物车商品已更新", repository.updateCartItem(currentUserId(null), cartItemId, body));
    }

    @DeleteMapping("/cart/items/{cartItemId}")
    public ApiResponse<?> deleteCartItem(@PathVariable("cartItemId") Long cartItemId) {
        return ApiResponse.ok("购物车商品已删除", repository.deleteCartItem(currentUserId(null), cartItemId));
    }

    @PostMapping("/cart/toggle")
    public ApiResponse<?> toggleCart(@RequestBody Map<String, Object> body) {
        return ApiResponse.ok("购物车选择状态已更新", repository.toggleCart(currentUserId(null), body));
    }

    @DeleteMapping("/cart")
    public ApiResponse<?> clearCart(@RequestParam("merchantId") Long merchantId) {
        repository.clearCart(currentUserId(null), merchantId);
        return ApiResponse.ok("购物车已清空", Map.of("merchantId", merchantId));
    }

    @GetMapping("/coupons")
    public ApiResponse<?> coupons(@RequestParam("merchantId") Long merchantId) {
        return ApiResponse.ok(repository.listUserCoupons(currentUserId(null), merchantId));
    }

    @GetMapping("/coupons/wallet")
    public ApiResponse<?> couponWallet() {
        return ApiResponse.ok(repository.listUserCouponWallet(currentUserId(null)));
    }

    @GetMapping("/coupons/platform")
    public ApiResponse<?> availableCoupons(@RequestParam(name = "merchantId", required = false) Long merchantId) {
        return ApiResponse.ok(repository.listAvailableCoupons(currentUserId(null), merchantId));
    }

    @PostMapping("/coupons/{couponId}/claim")
    public ApiResponse<?> claimCoupon(@PathVariable("couponId") Long couponId) {
        return ApiResponse.ok(repository.claimCoupon(currentUserId(null), couponId));
    }

    @GetMapping("/orders/{orderId}/tracking")
    public ApiResponse<?> tracking(@PathVariable("orderId") Long orderId) {
        return ApiResponse.ok(Map.of(
                "orderId", orderId,
                "trackingEndpoint", "/api/rider/orders/" + orderId + "/tracking",
                "websocketEndpoint", "ws://localhost:8103/ws/tracking?orderId=" + orderId,
                "tracking", repository.getTrackingSnapshot(orderId)
        ));
    }

    @GetMapping("/notifications")
    public ApiResponse<?> notifications(@RequestParam(name = "limit", required = false, defaultValue = "10") int limit) {
        Long userId = currentUserId(null);
        return ApiResponse.ok(notificationRepository.listNotifications(UserRole.USER, userId, limit));
    }

    @PostMapping("/notifications/{notificationId}/read")
    public ApiResponse<?> markNotificationRead(@PathVariable("notificationId") Long notificationId) {
        return ApiResponse.ok(notificationRepository.markNotificationRead(UserRole.USER, currentUserId(null), notificationId));
    }

    @PostMapping("/notifications/read-all")
    public ApiResponse<?> markAllNotificationsRead() {
        return ApiResponse.ok(notificationRepository.markAllNotificationsRead(UserRole.USER, currentUserId(null)));
    }

    private Map<String, Object> authPayload(Map<String, Object> profile) {
        Long userId = Long.parseLong(String.valueOf(profile.get("userId")));
        return Map.of(
                "token", jwtTokenService.createToken(userId, UserRole.USER, String.valueOf(profile.get("name"))),
                "profile", profile
        );
    }

    private Long currentUserId(Object fallback) {
        Long currentUserId = AuthContext.getUserId();
        if (currentUserId != null) {
            return currentUserId;
        }
        if (fallback == null) {
            return null;
        }
        if (fallback instanceof Number number) {
            return number.longValue();
        }
        return Long.parseLong(String.valueOf(fallback));
    }

    private String stringOrNull(Object value) {
        if (value == null) {
            return null;
        }
        String text = String.valueOf(value).trim();
        return text.isEmpty() ? null : text;
    }
}
