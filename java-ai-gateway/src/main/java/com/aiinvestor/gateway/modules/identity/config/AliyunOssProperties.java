package com.aiinvestor.gateway.modules.identity.config;

import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;

/**
 * 阿里云 OSS 配置。
 */
@ConfigurationProperties(prefix = "aliyun.oss")
@Component
public class AliyunOssProperties {

    /**
     * 是否启用 OSS 上传。
     */
    private boolean enabled;

    /**
     * OSS 访问节点，例如 https://oss-cn-shanghai.aliyuncs.com。
     */
    private String endpoint;

    /**
     * 目标桶名称。
     */
    private String bucket;

    /**
     * 访问密钥 ID。
     */
    private String accessKeyId;

    /**
     * 访问密钥 Secret。
     */
    private String accessKeySecret;

    /**
     * 对外访问域名，未配置时自动按 bucket + endpoint 拼接。
     */
    private String publicBaseUrl;

    /**
     * 存储目录，默认 avatars。
     */
    private String directory = "avatars";

    public boolean isEnabled() {
        return enabled;
    }

    public void setEnabled(boolean enabled) {
        this.enabled = enabled;
    }

    public String getEndpoint() {
        return endpoint;
    }

    public void setEndpoint(String endpoint) {
        this.endpoint = endpoint;
    }

    public String getBucket() {
        return bucket;
    }

    public void setBucket(String bucket) {
        this.bucket = bucket;
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

    public String getPublicBaseUrl() {
        return publicBaseUrl;
    }

    public void setPublicBaseUrl(String publicBaseUrl) {
        this.publicBaseUrl = publicBaseUrl;
    }

    public String getDirectory() {
        return directory;
    }

    public void setDirectory(String directory) {
        this.directory = directory;
    }
}
