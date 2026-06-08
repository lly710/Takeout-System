package com.example.takeout.order;

import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;

/**
 * Alipay sandbox configuration used by the order service.
 */
@Component
@ConfigurationProperties(prefix = "takeout.alipay")
public class AlipayProperties {

    private String gatewayUrl = "https://openapi-sandbox.dl.alipaydev.com/gateway.do";
    private String appId = "";
    private String privateKey = "";
    private String alipayPublicKey = "";
    private String returnUrl = "http://localhost:8088/api/orders/alipay/return";
    private String notifyUrl = "";
    private String frontendSuccessUrl = "http://localhost:5173/orders";
    private String charset = "UTF-8";
    private String signType = "RSA2";
    private boolean strictVerify = false;

    public String getGatewayUrl() {
        return gatewayUrl;
    }

    public void setGatewayUrl(String gatewayUrl) {
        this.gatewayUrl = gatewayUrl;
    }

    public String getAppId() {
        return appId;
    }

    public void setAppId(String appId) {
        this.appId = appId;
    }

    public String getPrivateKey() {
        return privateKey;
    }

    public void setPrivateKey(String privateKey) {
        this.privateKey = privateKey;
    }

    public String getAlipayPublicKey() {
        return alipayPublicKey;
    }

    public void setAlipayPublicKey(String alipayPublicKey) {
        this.alipayPublicKey = alipayPublicKey;
    }

    public String getReturnUrl() {
        return returnUrl;
    }

    public void setReturnUrl(String returnUrl) {
        this.returnUrl = returnUrl;
    }

    public String getNotifyUrl() {
        return notifyUrl;
    }

    public void setNotifyUrl(String notifyUrl) {
        this.notifyUrl = notifyUrl;
    }

    public String getFrontendSuccessUrl() {
        return frontendSuccessUrl;
    }

    public void setFrontendSuccessUrl(String frontendSuccessUrl) {
        this.frontendSuccessUrl = frontendSuccessUrl;
    }

    public String getCharset() {
        return charset;
    }

    public void setCharset(String charset) {
        this.charset = charset;
    }

    public String getSignType() {
        return signType;
    }

    public void setSignType(String signType) {
        this.signType = signType;
    }

    public boolean isStrictVerify() {
        return strictVerify;
    }

    public void setStrictVerify(boolean strictVerify) {
        this.strictVerify = strictVerify;
    }
}
