package com.aiinvestor.gateway.modules.identity.service;

import com.aiinvestor.gateway.modules.identity.config.AliyunOssProperties;
import com.aiinvestor.gateway.modules.shared.exception.BusinessException;
import com.aliyun.oss.OSS;
import com.aliyun.oss.OSSClientBuilder;
import com.aliyun.oss.model.ObjectMetadata;
import com.aliyun.oss.model.PutObjectRequest;
import org.springframework.stereotype.Service;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;
import java.io.InputStream;
import java.util.Locale;
import java.util.UUID;

/**
 * 阿里云 OSS 上传服务。
 */
@Service
public class AliyunOssService {

    private static final long MAX_FILE_SIZE = 5 * 1024 * 1024;

    private final AliyunOssProperties properties;

    public AliyunOssService(AliyunOssProperties properties) {
        this.properties = properties;
    }

    /**
     * 上传用户头像并返回可访问地址。
     */
    public String uploadAvatar(MultipartFile file, Long userId) {
        if (!properties.isEnabled()) {
            throw new BusinessException("当前环境未启用阿里云 OSS 上传");
        }
        if (file == null || file.isEmpty()) {
            throw new BusinessException("请选择头像图片");
        }
        if (file.getSize() > MAX_FILE_SIZE) {
            throw new BusinessException("头像图片不能超过 5MB");
        }

        String contentType = file.getContentType();
        if (contentType == null || !contentType.toLowerCase(Locale.ROOT).startsWith("image/")) {
            throw new BusinessException("只支持上传图片文件");
        }

        String endpoint = requireText(properties.getEndpoint(), "请先配置阿里云 OSS Endpoint");
        String bucket = requireText(properties.getBucket(), "请先配置阿里云 OSS Bucket");
        String accessKeyId = requireText(properties.getAccessKeyId(), "请先配置阿里云 OSS AccessKeyId");
        String accessKeySecret = requireText(properties.getAccessKeySecret(), "请先配置阿里云 OSS AccessKeySecret");

        String extension = resolveExtension(file.getOriginalFilename(), contentType);
        String key = normalizeDirectory(properties.getDirectory()) + userId + "/" + System.currentTimeMillis() + "-" + UUID.randomUUID() + extension;

        OSS ossClient = new OSSClientBuilder().build(endpoint, accessKeyId, accessKeySecret);
        try (InputStream inputStream = file.getInputStream()) {
            ObjectMetadata metadata = new ObjectMetadata();
            metadata.setContentType(contentType);
            metadata.setContentLength(file.getSize());
            PutObjectRequest request = new PutObjectRequest(bucket, key, inputStream, metadata);
            ossClient.putObject(request);
        } catch (IOException ex) {
            throw new BusinessException("头像上传失败，请稍后重试");
        } finally {
            ossClient.shutdown();
        }

        return buildPublicUrl(bucket, endpoint, key);
    }

    private String buildPublicUrl(String bucket, String endpoint, String key) {
        String publicBaseUrl = properties.getPublicBaseUrl();
        if (publicBaseUrl != null && !publicBaseUrl.isBlank()) {
            return trimTrailingSlash(publicBaseUrl) + "/" + key;
        }

        String normalizedEndpoint = endpoint.trim();
        if (!normalizedEndpoint.startsWith("http://") && !normalizedEndpoint.startsWith("https://")) {
            normalizedEndpoint = "https://" + normalizedEndpoint;
        }
        String pureEndpoint = normalizedEndpoint.replaceFirst("^https?://", "");
        return "https://" + bucket + "." + pureEndpoint + "/" + key;
    }

    private String normalizeDirectory(String directory) {
        String value = directory == null || directory.isBlank() ? "avatars" : directory.trim();
        value = value.replace("\\", "/");
        if (!value.endsWith("/")) {
            value = value + "/";
        }
        if (value.startsWith("/")) {
            value = value.substring(1);
        }
        return value;
    }

    private String resolveExtension(String originalFilename, String contentType) {
        if (originalFilename != null) {
            int dotIndex = originalFilename.lastIndexOf('.');
            if (dotIndex >= 0) {
                return originalFilename.substring(dotIndex).toLowerCase(Locale.ROOT);
            }
        }
        if ("image/png".equalsIgnoreCase(contentType)) {
            return ".png";
        }
        if ("image/webp".equalsIgnoreCase(contentType)) {
            return ".webp";
        }
        if ("image/gif".equalsIgnoreCase(contentType)) {
            return ".gif";
        }
        return ".jpg";
    }

    private String requireText(String value, String message) {
        if (value == null || value.isBlank()) {
            throw new BusinessException(message);
        }
        return value.trim();
    }

    private String trimTrailingSlash(String value) {
        return value.endsWith("/") ? value.substring(0, value.length() - 1) : value;
    }
}
