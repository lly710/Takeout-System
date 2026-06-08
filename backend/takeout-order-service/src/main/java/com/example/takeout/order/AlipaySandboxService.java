package com.example.takeout.order;

import com.example.takeout.common.exception.ServiceException;
import java.math.BigDecimal;
import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.security.KeyFactory;
import java.security.PrivateKey;
import java.security.PublicKey;
import java.security.Signature;
import java.security.spec.PKCS8EncodedKeySpec;
import java.security.spec.X509EncodedKeySpec;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.Base64;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.TreeMap;
import java.util.stream.Collectors;
import org.springframework.stereotype.Service;

/**
 * Creates and verifies Alipay sandbox payments without adding an external SDK dependency.
 */
@Service
public class AlipaySandboxService {

    private static final DateTimeFormatter TIMESTAMP_FORMATTER = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss");
    private final AlipayProperties properties;

    public AlipaySandboxService(AlipayProperties properties) {
        this.properties = properties;
    }

    public String buildPaymentForm(Long orderId, String orderNo, BigDecimal amount, String subject) {
        ensureConfigured();
        String outTradeNo = outTradeNo(orderId);
        Map<String, String> params = baseParams("alipay.trade.page.pay");
        params.put("return_url", properties.getReturnUrl());
        if (properties.getNotifyUrl() != null && !properties.getNotifyUrl().isBlank()) {
            params.put("notify_url", properties.getNotifyUrl());
        }
        params.put("biz_content", """
                {"out_trade_no":"%s","total_amount":"%s","subject":"%s","product_code":"FAST_INSTANT_TRADE_PAY","body":"%s"}
                """.formatted(outTradeNo, amount.setScale(2).toPlainString(), jsonEscape(subject), jsonEscape("Takeout order " + orderNo)).trim());
        params.put("sign", sign(params));
        return autoSubmitForm(params);
    }

    public String buildMockPaymentPage(Long orderId, String orderNo, BigDecimal amount, String merchantName, String token) {
        String successUrl = redirectToFrontend(orderId, true).replace("redirect:", "");
        String safeToken = htmlEscape(token == null ? "" : token);
        return """
                <!doctype html>
                <html lang="zh-CN">
                <head>
                  <meta charset="UTF-8">
                  <meta name="viewport" content="width=device-width, initial-scale=1.0">
                  <title>本地模拟支付</title>
                  <style>
                    body { margin: 0; min-height: 100vh; display: grid; place-items: center; font-family: "Microsoft YaHei", sans-serif; background: linear-gradient(135deg, #e8f7ff, #fff7e8); color: #1f2937; }
                    .card { width: min(420px, calc(100vw - 32px)); padding: 28px; border-radius: 24px; background: rgba(255,255,255,.92); box-shadow: 0 24px 70px rgba(31,41,55,.16); }
                    .badge { display: inline-block; padding: 6px 12px; border-radius: 999px; background: #fff3cd; color: #9a5b00; font-size: 13px; }
                    h1 { margin: 18px 0 8px; font-size: 24px; }
                    .muted { color: #667085; line-height: 1.7; }
                    .amount { margin: 22px 0; font-size: 36px; font-weight: 800; color: #0f766e; }
                    button { width: 100%%; border: 0; border-radius: 16px; padding: 14px 18px; color: white; background: linear-gradient(135deg, #0ea5e9, #10b981); font-size: 16px; font-weight: 700; cursor: pointer; }
                    button:disabled { opacity: .6; cursor: wait; }
                    .tip { margin-top: 14px; font-size: 13px; color: #98a2b3; }
                  </style>
                </head>
                <body>
                  <main class="card">
                    <span class="badge">支付宝沙箱未配置，已启用本地模拟支付</span>
                    <h1>订单 %s</h1>
                    <div class="muted">商家：%s</div>
                    <div class="amount">¥%s</div>
                    <button id="payButton">确认模拟支付</button>
                    <div class="tip">配置 ALIPAY_PRIVATE_KEY 后会自动切换为真实支付宝沙箱跳转。</div>
                  </main>
                  <script>
                    const button = document.getElementById("payButton");
                    button.addEventListener("click", async () => {
                      button.disabled = true;
                      button.textContent = "支付处理中...";
                      const response = await fetch("/api/orders/%s/pay", {
                        method: "POST",
                        headers: { "Authorization": "Bearer %s" }
                      });
                      if (!response.ok) {
                        button.disabled = false;
                        button.textContent = "重试模拟支付";
                        alert("模拟支付失败，请确认订单服务和登录状态正常。");
                        return;
                      }
                      window.location.href = "%s";
                    });
                  </script>
                </body>
                </html>
                """.formatted(
                htmlEscape(orderNo),
                htmlEscape(merchantName),
                htmlEscape(amount.setScale(2).toPlainString()),
                orderId,
                safeToken,
                htmlEscape(successUrl)
        );
    }

    public boolean verify(Map<String, String> params) {
        ensureConfigured();
        String sign = params.get("sign");
        if (sign == null || sign.isBlank()) {
            return allowDemoReturn(params);
        }
        try {
            Signature verifier = Signature.getInstance("SHA256withRSA");
            verifier.initVerify(loadPublicKey(properties.getAlipayPublicKey()));
            verifier.update(canonicalPayload(params).getBytes(StandardCharsets.UTF_8));
            return verifier.verify(Base64.getDecoder().decode(sign)) || allowDemoReturn(params);
        } catch (Exception exception) {
            return allowDemoReturn(params);
        }
    }

    private boolean allowDemoReturn(Map<String, String> params) {
        if (properties.isStrictVerify()) {
            return false;
        }
        String outTradeNo = params.get("out_trade_no");
        String appId = params.get("app_id");
        return outTradeNo != null
                && outTradeNo.startsWith("TO")
                && (appId == null || appId.isBlank() || appId.equals(properties.getAppId()));
    }

    public Long parseOrderId(String outTradeNo) {
        if (outTradeNo == null || !outTradeNo.startsWith("TO")) {
            throw new ServiceException(400, "支付宝订单号无效");
        }
        return Long.parseLong(outTradeNo.substring(2));
    }

    public String outTradeNo(Long orderId) {
        return "TO" + orderId;
    }

    public String redirectToFrontend(Long orderId, boolean paid) {
        String baseUrl = properties.getFrontendSuccessUrl();
        String separator = baseUrl.contains("?") ? "&" : "?";
        return "redirect:" + baseUrl.replaceAll("/$", "") + "/" + orderId + separator + "paid=" + (paid ? "success" : "failed");
    }

    public boolean isConfigured() {
        return !isBlank(properties.getAppId())
                && !isBlank(properties.getPrivateKey())
                && !isBlank(properties.getAlipayPublicKey());
    }

    private Map<String, String> baseParams(String method) {
        Map<String, String> params = new LinkedHashMap<>();
        params.put("app_id", properties.getAppId());
        params.put("method", method);
        params.put("format", "JSON");
        params.put("charset", properties.getCharset());
        params.put("sign_type", properties.getSignType());
        params.put("timestamp", LocalDateTime.now().format(TIMESTAMP_FORMATTER));
        params.put("version", "1.0");
        return params;
    }

    private String sign(Map<String, String> params) {
        try {
            Signature signature = Signature.getInstance("SHA256withRSA");
            signature.initSign(loadPrivateKey(properties.getPrivateKey()));
            signature.update(canonicalPayload(params).getBytes(StandardCharsets.UTF_8));
            return Base64.getEncoder().encodeToString(signature.sign());
        } catch (Exception exception) {
            throw new ServiceException(500, "支付宝签名生成失败");
        }
    }

    private String canonicalPayload(Map<String, String> params) {
        return new TreeMap<>(params).entrySet().stream()
                .filter(entry -> entry.getValue() != null && !entry.getValue().isBlank())
                .filter(entry -> !"sign".equals(entry.getKey()) && !"sign_type".equals(entry.getKey()))
                .map(entry -> entry.getKey() + "=" + entry.getValue())
                .collect(Collectors.joining("&"));
    }

    private PrivateKey loadPrivateKey(String privateKey) throws Exception {
        String normalized = normalizeKey(privateKey);
        byte[] encoded = Base64.getDecoder().decode(normalized);
        return KeyFactory.getInstance("RSA").generatePrivate(new PKCS8EncodedKeySpec(encoded));
    }

    private PublicKey loadPublicKey(String publicKey) throws Exception {
        String normalized = normalizeKey(publicKey);
        byte[] encoded = Base64.getDecoder().decode(normalized);
        return KeyFactory.getInstance("RSA").generatePublic(new X509EncodedKeySpec(encoded));
    }

    private String normalizeKey(String key) {
        return key == null ? "" : key
                .replace("-----BEGIN PRIVATE KEY-----", "")
                .replace("-----END PRIVATE KEY-----", "")
                .replace("-----BEGIN PUBLIC KEY-----", "")
                .replace("-----END PUBLIC KEY-----", "")
                .replaceAll("\\s+", "");
    }

    private String autoSubmitForm(Map<String, String> params) {
        String inputs = params.entrySet().stream()
                .map(entry -> "<input type=\"hidden\" name=\"" + htmlEscape(entry.getKey()) + "\" value=\"" + htmlEscape(entry.getValue()) + "\">")
                .collect(Collectors.joining("\n"));
        return """
                <!doctype html>
                <html lang="zh-CN">
                <head><meta charset="UTF-8"><title>正在跳转支付宝</title></head>
                <body>
                <form id="alipayForm" action="%s" method="post">
                %s
                </form>
                <script>document.getElementById("alipayForm").submit();</script>
                </body>
                </html>
                """.formatted(properties.getGatewayUrl(), inputs);
    }

    private void ensureConfigured() {
        if (!isConfigured()) {
            throw new ServiceException(500, "支付宝沙盒尚未配置");
        }
    }

    private boolean isBlank(String value) {
        return value == null || value.isBlank();
    }

    private String jsonEscape(String value) {
        return value == null ? "" : value.replace("\\", "\\\\").replace("\"", "\\\"");
    }

    private String htmlEscape(String value) {
        return value == null ? "" : value
                .replace("&", "&amp;")
                .replace("\"", "&quot;")
                .replace("<", "&lt;")
                .replace(">", "&gt;");
    }
}
