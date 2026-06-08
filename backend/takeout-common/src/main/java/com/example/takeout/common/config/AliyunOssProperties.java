package com.example.takeout.common.config;

import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * 阿里云 OSS 的配置属性，包含 endpoint、密钥、bucket 和常用上传前缀。
 */
@ConfigurationProperties(prefix = "aliyun.oss")
public class AliyunOssProperties {

    private String endpoint;
    private String region;
    private String accessKeyId;
    private String accessKeySecret;
    private String bucketName;
    private String publicDomain;
    private String avatarPrefix = "takeout/avatar/";
    private String merchantPrefix = "takeout/merchant/";
    private String menuPrefix = "takeout/menu/";

    public String getEndpoint() {
        return endpoint;
    }

    public void setEndpoint(String endpoint) {
        this.endpoint = endpoint;
    }

    public String getRegion() {
        return region;
    }

    public void setRegion(String region) {
        this.region = region;
    }

    public String getAccessKeyId() {
        return accessKeyId;
    }

    public void setAccessKeyId(String accessKeyId) {
        this.accessKeyId = accessKeyId;
    }

    public String getAccessKeySecret() {
        return accessKeySecret;
    }

    public void setAccessKeySecret(String accessKeySecret) {
        this.accessKeySecret = accessKeySecret;
    }

    public String getBucketName() {
        return bucketName;
    }

    public void setBucketName(String bucketName) {
        this.bucketName = bucketName;
    }

    public String getPublicDomain() {
        return publicDomain;
    }

    public void setPublicDomain(String publicDomain) {
        this.publicDomain = publicDomain;
    }

    public String getAvatarPrefix() {
        return avatarPrefix;
    }

    public void setAvatarPrefix(String avatarPrefix) {
        this.avatarPrefix = avatarPrefix;
    }

    public String getMerchantPrefix() {
        return merchantPrefix;
    }

    public void setMerchantPrefix(String merchantPrefix) {
        this.merchantPrefix = merchantPrefix;
    }

    public String getMenuPrefix() {
        return menuPrefix;
    }

    public void setMenuPrefix(String menuPrefix) {
        this.menuPrefix = menuPrefix;
    }
}
