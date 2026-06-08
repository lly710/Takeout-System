package com.example.takeout.admin;

import com.example.takeout.common.api.ApiResponse;
import com.example.takeout.common.auth.JwtTokenService;
import com.example.takeout.common.auth.PublicApi;
import com.example.takeout.common.auth.UserRole;
import com.example.takeout.common.db.PlatformDataRepository;
import com.example.takeout.common.db.NotificationRepository;
import java.util.Map;

import org.springframework.web.bind.annotation.*;

/**
 * 管理员后台接口，提供订单、商家、骑手和平台统计查看。
 */
@RestController
@RequestMapping("/api/admin")
public class AdminController {

    private final PlatformDataRepository repository;
    private final NotificationRepository notificationRepository;
    private final JwtTokenService jwtTokenService;

    public AdminController(PlatformDataRepository repository,
                           NotificationRepository notificationRepository,
                           JwtTokenService jwtTokenService) {
        this.repository = repository;
        this.notificationRepository = notificationRepository;
        this.jwtTokenService = jwtTokenService;
    }

    @PublicApi
    @PostMapping("/auth/login")
    public ApiResponse<?> login(@RequestBody Map<String, Object> body) {
        Map<String, Object> profile = repository.loginAdmin(body);
        return ApiResponse.ok(Map.of(
                "token", jwtTokenService.createToken((Long) profile.get("adminId"), UserRole.ADMIN, String.valueOf(profile.get("displayName"))),
                "profile", profile
        ));
    }

    @GetMapping("/orders")
    public ApiResponse<?> orders() {
        return ApiResponse.ok(repository.listAllOrders());
    }

    @GetMapping("/users")
    public ApiResponse<?> users() {
        return ApiResponse.ok(repository.listAllUsers());
    }

    @GetMapping("/accounts")
    public ApiResponse<?> accounts() {
        return ApiResponse.ok(repository.adminAccounts());
    }

    @GetMapping("/merchants")
    public ApiResponse<?> merchants() {
        return ApiResponse.ok(repository.merchantList());
    }

    @GetMapping("/merchants/{merchantId}/goods")
    public ApiResponse<?> merchantGoods(@PathVariable("merchantId") Long merchantId) {
        return ApiResponse.ok(repository.listMerchantMenuDetails(merchantId));
    }

    @GetMapping("/merchant-audits")
    public ApiResponse<?> merchantAudits() {
        return ApiResponse.ok(repository.adminMerchantAudits());
    }

    @GetMapping("/riders")
    public ApiResponse<?> riders() {
        return ApiResponse.ok(repository.riderStats());
    }

    @GetMapping("/riders/list")
    public ApiResponse<?> riderList() {
        return ApiResponse.ok(repository.riderList());
    }

    @GetMapping("/stats")
    public ApiResponse<?> stats() {
        return ApiResponse.ok(repository.adminStats());
    }

    @GetMapping("/categories")
    public ApiResponse<?> categories() {
        return ApiResponse.ok(repository.adminCategories());
    }

    @GetMapping("/marketing")
    public ApiResponse<?> marketing() {
        return ApiResponse.ok(repository.adminMarketing());
    }

    @GetMapping("/risk")
    public ApiResponse<?> risk() {
        return ApiResponse.ok(repository.adminRisk());
    }

    @GetMapping("/health")
    public ApiResponse<?> health() {
        return ApiResponse.ok(repository.adminHealth());
    }

    @GetMapping("/operation-logs")
    public ApiResponse<?> operationLogs() {
        return ApiResponse.ok(repository.adminOperationLogs());
    }

    @GetMapping("/permissions")
    public ApiResponse<?> permissions() {
        return ApiResponse.ok(repository.adminPermissions());
    }

    @GetMapping("/settings")
    public ApiResponse<?> settings() {
        return ApiResponse.ok(repository.adminSettings());
    }

    @GetMapping("/notifications")
    public ApiResponse<?> notifications(@RequestParam(name = "limit", required = false, defaultValue = "10") int limit,
                                        @RequestParam(name = "adminId", required = false, defaultValue = "1") Long adminId) {
        return ApiResponse.ok(notificationRepository.listNotifications(UserRole.ADMIN, adminId, limit));
    }

    @PostMapping("/notifications/{notificationId}/read")
    public ApiResponse<?> markNotificationRead(@PathVariable("notificationId") Long notificationId,
                                               @RequestParam(name = "adminId", required = false, defaultValue = "1") Long adminId) {
        return ApiResponse.ok(notificationRepository.markNotificationRead(UserRole.ADMIN, adminId, notificationId));
    }

    @PostMapping("/notifications/read-all")
    public ApiResponse<?> markAllNotificationsRead(@RequestParam(name = "adminId", required = false, defaultValue = "1") Long adminId) {
        return ApiResponse.ok(notificationRepository.markAllNotificationsRead(UserRole.ADMIN, adminId));
    }
}
