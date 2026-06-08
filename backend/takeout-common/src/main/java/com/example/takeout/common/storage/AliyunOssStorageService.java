package com.example.takeout.common.storage;

import com.aliyun.oss.OSS;
import com.aliyun.oss.model.ObjectMetadata;
import com.aliyun.oss.model.PutObjectRequest;
import com.example.takeout.common.config.AliyunOssProperties;
import com.example.takeout.common.exception.ServiceException;
import java.io.IOException;
import java.io.InputStream;
import java.util.UUID;
import org.springframework.boot.autoconfigure.condition.ConditionalOnBean;
import org.springframework.stereotype.Service;
import org.springframework.web.multipart.MultipartFile;

/**
 * 阿里云 OSS 文件上传服务，统一处理头像、商家图片和菜品图片的上传与删除。
 */
@Service
@ConditionalOnBean(OSS.class)
public class AliyunOssStorageService {

    private final OSS ossClient;
    private final AliyunOssProperties properties;

    public AliyunOssStorageService(OSS ossClient, AliyunOssProperties properties) {
        this.ossClient = ossClient;
        this.properties = properties;
    }

    public String uploadAvatar(MultipartFile file) {
        return upload(file, properties.getAvatarPrefix());
    }

    public String uploadMerchantAsset(MultipartFile file) {
        return upload(file, properties.getMerchantPrefix());
    }

    public String uploadMenuAsset(MultipartFile file) {
        return upload(file, properties.getMenuPrefix());
    }

    public void delete(String objectUrlOrKey) {
        if (objectUrlOrKey == null || objectUrlOrKey.isBlank()) {
            return;
        }
        ossClient.deleteObject(properties.getBucketName(), extractObjectKey(objectUrlOrKey));
    }

    private String upload(MultipartFile file, String prefix) {
        if (file == null || file.isEmpty()) {
            throw new ServiceException("上传文件不能为空");
        }
        if (properties.getBucketName() == null || properties.getBucketName().isBlank()) {
            throw new ServiceException("阿里云 OSS 尚未配置");
        }

        String extension = extensionOf(file.getOriginalFilename());
        String objectKey = prefix + System.currentTimeMillis() + "-" + UUID.randomUUID().toString().replace("-", "") + extension;
        ObjectMetadata metadata = new ObjectMetadata();
        metadata.setContentLength(file.getSize());
        metadata.setContentType(file.getContentType());
        try (InputStream inputStream = file.getInputStream()) {
            ossClient.putObject(new PutObjectRequest(properties.getBucketName(), objectKey, inputStream, metadata));
            return buildPublicUrl(objectKey);
        } catch (IOException exception) {
            throw new ServiceException(500, "文件上传失败");
        }
    }

    private String buildPublicUrl(String objectKey) {
        if (properties.getPublicDomain() != null && !properties.getPublicDomain().isBlank()) {
            return properties.getPublicDomain().replaceAll("/$", "") + "/" + objectKey;
        }
        return "https://" + properties.getBucketName() + "." + properties.getEndpoint() + "/" + objectKey;
    }

    private String extractObjectKey(String objectUrlOrKey) {
        if (!objectUrlOrKey.startsWith("http")) {
            return objectUrlOrKey;
        }
        int pathIndex = objectUrlOrKey.indexOf('/', objectUrlOrKey.indexOf("//") + 2);
        return pathIndex < 0 ? objectUrlOrKey : objectUrlOrKey.substring(pathIndex + 1);
    }

    private String extensionOf(String originalFilename) {
        if (originalFilename == null) {
            return "";
        }
        int dotIndex = originalFilename.lastIndexOf('.');
        return dotIndex >= 0 ? originalFilename.substring(dotIndex) : "";
    }
}
