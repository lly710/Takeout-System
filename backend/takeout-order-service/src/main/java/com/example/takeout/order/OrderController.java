package com.example.takeout.order;

import com.example.takeout.common.api.ApiResponse;
import com.example.takeout.common.auth.AuthContext;
import com.example.takeout.common.auth.PublicApi;
import com.example.takeout.common.auth.RequireRole;
import com.example.takeout.common.auth.UserRole;
import com.example.takeout.common.db.NotificationRepository;
import com.example.takeout.common.db.PlatformDataRepository;
import java.math.BigDecimal;
import java.util.Map;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.http.MediaType;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.servlet.view.RedirectView;

/**
 * Order APIs for preview, creation, payment, cancellation, detail, and flow queries.
 */
@RestController
@RequestMapping("/api/orders")
@RequireRole(UserRole.USER)
public class OrderController {

    private final PlatformDataRepository repository;
    private final NotificationRepository notificationRepository;
    private final AlipaySandboxService alipaySandboxService;

    public OrderController(PlatformDataRepository repository,
                           NotificationRepository notificationRepository,
                           AlipaySandboxService alipaySandboxService) {
        this.repository = repository;
        this.notificationRepository = notificationRepository;
        this.alipaySandboxService = alipaySandboxService;
    }

    @PostMapping("/preview")
    public ApiResponse<?> preview(@RequestBody Map<String, Object> body) {
        return ApiResponse.ok(repository.previewOrder(AuthContext.getUserId(), body));
    }

    @PostMapping
    public ApiResponse<?> create(@RequestBody Map<String, Object> body) {
        Map<String, Object> result = repository.createOrder(AuthContext.getUserId(), body);
        return ApiResponse.ok("订单创建成功", result);
    }

    @PostMapping("/{orderId}/pay")
    public ApiResponse<?> pay(@PathVariable("orderId") Long orderId) {
        Map<String, Object> result = repository.payOrder(orderId);
        Map<String, Object> order = repository.queryOrderBase(orderId);
        if (order != null) {
            String orderNo = String.valueOf(order.get("order_no"));
            notificationRepository.createNotification(
                    UserRole.USER,
                    AuthContext.getUserId(),
                    "ORDER_PAID",
                    "支付成功",
                    "订单 " + orderNo + " 已支付成功，正在等待商家接单。",
                    "ORDER",
                    orderId,
                    "order:" + orderId + ":ORDER_PAID:USER");
            notificationRepository.createNotification(
                    UserRole.MERCHANT,
                    Long.parseLong(String.valueOf(order.get("merchant_id"))),
                    "ORDER_PAID",
                    "新订单已支付",
                    "订单 " + orderNo + " 已支付，请尽快处理。",
                    "ORDER",
                    orderId,
                    "order:" + orderId + ":ORDER_PAID:MERCHANT");
            notificationRepository.createNotification(
                    UserRole.ADMIN,
                    1L,
                    "ORDER_PAID",
                    "订单已支付",
                    "订单 " + orderNo + " 已支付，等待商家接单。",
                    "ORDER",
                    orderId,
                    "order:" + orderId + ":ORDER_PAID:ADMIN");
        }
        return ApiResponse.ok("支付成功", result);
    }

    @GetMapping(value = "/{orderId}/alipay/page", produces = MediaType.TEXT_HTML_VALUE)
    public String alipayPage(@PathVariable("orderId") Long orderId,
                             @RequestParam(value = "token", required = false) String token) {
        Map<String, Object> order = repository.queryOrderBase(orderId);
        if (order == null || !String.valueOf(AuthContext.getUserId()).equals(String.valueOf(order.get("user_id")))) {
            throw new IllegalArgumentException("订单不存在");
        }
        String orderNo = String.valueOf(order.get("order_no"));
        String merchantName = String.valueOf(order.get("merchant_name"));
        BigDecimal amount = toBigDecimal(order.get("amount"));
        if (!alipaySandboxService.isConfigured()) {
            return alipaySandboxService.buildMockPaymentPage(orderId, orderNo, amount, merchantName, token);
        }
        return alipaySandboxService.buildPaymentForm(orderId, orderNo, amount, "外卖订单-" + merchantName);
    }

    @PublicApi
    @GetMapping("/alipay/return")
    public RedirectView alipayReturn(@RequestParam Map<String, String> params) {
        boolean verified = alipaySandboxService.verify(params);
        Long orderId = verified ? alipaySandboxService.parseOrderId(params.get("out_trade_no")) : 0L;
        if (verified) {
            repository.payOrder(orderId);
        }
        RedirectView redirectView = new RedirectView(alipaySandboxService.redirectToFrontend(orderId, verified).replace("redirect:", ""));
        redirectView.setExposeModelAttributes(false);
        return redirectView;
    }

    @PublicApi
    @PostMapping("/alipay/notify")
    public String alipayNotify(@RequestParam Map<String, String> params) {
        if (!alipaySandboxService.verify(params)) {
            return "failure";
        }
        String tradeStatus = params.getOrDefault("trade_status", "");
        if ("TRADE_SUCCESS".equals(tradeStatus) || "TRADE_FINISHED".equals(tradeStatus)) {
            repository.payOrder(alipaySandboxService.parseOrderId(params.get("out_trade_no")));
        }
        return "success";
    }

    @PostMapping("/{orderId}/cancel")
    public ApiResponse<?> cancel(@PathVariable("orderId") Long orderId, @RequestBody(required = false) Map<String, Object> body) {
        String reason = body == null ? "用户取消订单" : String.valueOf(body.getOrDefault("reason", "用户取消订单"));
        Map<String, Object> result = repository.cancelOrder(AuthContext.getUserId(), orderId, reason);
        notificationRepository.createNotification(
                UserRole.USER,
                AuthContext.getUserId(),
                "ORDER_CANCELLED",
                "订单已取消",
                "订单 " + orderId + " 已取消。",
                "ORDER",
                orderId,
                "order:" + orderId + ":ORDER_CANCELLED:USER");
        Map<String, Object> order = repository.queryOrderBase(orderId);
        if (order != null) {
            notificationRepository.createNotification(
                    UserRole.MERCHANT,
                    Long.parseLong(String.valueOf(order.get("merchant_id"))),
                    "ORDER_CANCELLED",
                    "订单已取消",
                    "订单 " + String.valueOf(order.get("order_no")) + " 已取消，原因：" + reason,
                    "ORDER",
                    orderId,
                    "order:" + orderId + ":ORDER_CANCELLED:MERCHANT");
            notificationRepository.createNotification(
                    UserRole.ADMIN,
                    1L,
                    "ORDER_CANCELLED",
                    "订单已取消",
                    "订单 " + String.valueOf(order.get("order_no")) + " 已取消。",
                    "ORDER",
                    orderId,
                    "order:" + orderId + ":ORDER_CANCELLED:ADMIN");
        }
        return ApiResponse.ok("订单已取消", result);
    }

    @GetMapping("/my")
    public ApiResponse<?> myOrders() {
        return ApiResponse.ok(repository.listUserOrders(AuthContext.getUserId()));
    }

    @GetMapping("/{orderId}")
    public ApiResponse<?> detail(@PathVariable("orderId") Long orderId) {
        return ApiResponse.ok(repository.getOrderDetail(orderId));
    }

    @GetMapping("/{orderId}/flow")
    public ApiResponse<?> flow(@PathVariable("orderId") Long orderId) {
        return ApiResponse.ok(repository.getOrderFlow(orderId));
    }

    private BigDecimal toBigDecimal(Object value) {
        if (value instanceof BigDecimal decimal) {
            return decimal;
        }
        if (value instanceof Number number) {
            return BigDecimal.valueOf(number.doubleValue());
        }
        return new BigDecimal(String.valueOf(value));
    }
}
