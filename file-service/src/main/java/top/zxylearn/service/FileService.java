package top.zxylearn.service;

import com.aliyun.oss.OSS;
import com.aliyun.oss.OSSClientBuilder;
import com.aliyun.oss.model.ObjectMetadata;
import org.springframework.stereotype.Service;
import org.springframework.web.multipart.MultipartFile;
import top.zxylearn.config.AliyunOssProperties;
import top.zxylearn.dto.DirectUploadPolicyRequest;
import top.zxylearn.vo.DirectUploadPolicyVO;
import top.zxylearn.vo.FileUploadVO;

import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;
import java.io.IOException;
import java.io.InputStream;
import java.net.URI;
import java.net.URISyntaxException;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.time.Instant;
import java.time.format.DateTimeFormatter;
import java.util.Base64;
import java.util.Locale;
import java.util.Set;
import java.util.UUID;

@Service
public class FileService {

    private static final String FILE_ROOT = "ele/";
    private static final Set<String> ALLOWED_IMAGE_EXTENSIONS = Set.of(".jpg", ".jpeg", ".png", ".gif", ".webp", ".bmp");
    private static final long DIRECT_UPLOAD_MAX_SIZE = 5 * 1024 * 1024;
    private static final Duration DIRECT_UPLOAD_EXPIRE = Duration.ofMinutes(5);

    private final AliyunOssProperties aliyunOssProperties;

    public FileService(AliyunOssProperties aliyunOssProperties) {
        this.aliyunOssProperties = aliyunOssProperties;
    }

    public FileUploadVO upload(String userId, MultipartFile file) {
        if (file == null || file.isEmpty()) {
            throw new IllegalArgumentException("文件不能为空");
        }
        String userRoot = buildUserRoot(userId);
        String originalFilename = file.getOriginalFilename();
        String fileExtension = getFileExtension(originalFilename);
        validateImage(file.getContentType(), fileExtension);
        String objectName = userRoot + UUID.randomUUID().toString().replace("-", "") + fileExtension;

        ObjectMetadata metadata = new ObjectMetadata();
        metadata.setContentLength(file.getSize());
        if (hasText(file.getContentType())) {
            metadata.setContentType(file.getContentType());
        }

        OSS ossClient = createOssClient();
        try (InputStream inputStream = file.getInputStream()) {
            ossClient.putObject(aliyunOssProperties.getBucketName(), objectName, inputStream, metadata);
        } catch (IOException ex) {
            throw new RuntimeException("文件读取失败", ex);
        } finally {
            ossClient.shutdown();
        }

        return new FileUploadVO(
                objectName,
                buildFileUrl(objectName),
                originalFilename,
                file.getSize(),
                file.getContentType()
        );
    }

    public void delete(String userId, String objectName) {
        String normalizedObjectName = normalizeObjectName(objectName);
        if (!hasText(normalizedObjectName)) {
            throw new IllegalArgumentException("文件名不能为空");
        }
        String userRoot = buildUserRoot(userId);
        if (!normalizedObjectName.startsWith(userRoot)) {
            throw new IllegalArgumentException("只能删除当前用户目录下的文件");
        }

        OSS ossClient = createOssClient();
        try {
            ossClient.deleteObject(aliyunOssProperties.getBucketName(), normalizedObjectName);
        } finally {
            ossClient.shutdown();
        }
    }

    public void adminDelete(String fileUrl) {
        String normalizedObjectName = normalizeObjectName(fileUrl);
        if (!hasText(normalizedObjectName)) {
            throw new IllegalArgumentException("文件访问地址不能为空");
        }
        if (!normalizedObjectName.startsWith(FILE_ROOT)) {
            throw new IllegalArgumentException("只能删除 ele/ 目录下的文件");
        }
        if (!ALLOWED_IMAGE_EXTENSIONS.contains(getFileExtension(normalizedObjectName))) {
            throw new IllegalArgumentException("只能删除图片文件");
        }

        OSS ossClient = createOssClient();
        try {
            ossClient.deleteObject(aliyunOssProperties.getBucketName(), normalizedObjectName);
        } finally {
            ossClient.shutdown();
        }
    }

    public DirectUploadPolicyVO createDirectUploadPolicy(String userId, DirectUploadPolicyRequest request) {
        if (request == null) {
            throw new IllegalArgumentException("直传授权参数不能为空");
        }
        String contentType = request.getContentType();
        String fileExtension = getFileExtension(request.getOriginalFilename());
        validateImage(contentType, fileExtension);

        String objectName = buildUserRoot(userId) + UUID.randomUUID().toString().replace("-", "") + fileExtension;
        Instant expiration = Instant.now().plus(DIRECT_UPLOAD_EXPIRE);
        String policyJson = buildDirectUploadPolicyJson(objectName, contentType, expiration);
        String policy = Base64.getEncoder().encodeToString(policyJson.getBytes(StandardCharsets.UTF_8));
        String signature = signPolicy(policy);

        return new DirectUploadPolicyVO(
                buildOssHost(),
                objectName,
                buildFileUrl(objectName),
                aliyunOssProperties.getAccessKeyId(),
                policy,
                signature,
                expiration.getEpochSecond(),
                "200",
                contentType
        );
    }



    private String buildDirectUploadPolicyJson(String objectName, String contentType, Instant expiration) {
        return "{"
                + "\"expiration\":\"" + DateTimeFormatter.ISO_INSTANT.format(expiration) + "\","
                + "\"conditions\":["
                + "[\"eq\",\"$key\",\"" + objectName + "\"],"
                + "[\"eq\",\"$Content-Type\",\"" + contentType + "\"],"
                + "[\"content-length-range\",1," + DIRECT_UPLOAD_MAX_SIZE + "]"
                + "]}"
                ;
    }

    private String signPolicy(String policy) {
        try {
            Mac mac = Mac.getInstance("HmacSHA1");
            mac.init(new SecretKeySpec(
                    aliyunOssProperties.getAccessKeySecret().getBytes(StandardCharsets.UTF_8),
                    "HmacSHA1"
            ));
            byte[] signature = mac.doFinal(policy.getBytes(StandardCharsets.UTF_8));
            return Base64.getEncoder().encodeToString(signature);
        } catch (Exception ex) {
            throw new RuntimeException("生成直传签名失败", ex);
        }
    }

    private String buildOssHost() {
        String endpoint = aliyunOssProperties.getEndpoint();
        if (!hasText(endpoint)) {
            throw new IllegalArgumentException("OSS endpoint 未配置");
        }
        return "https://" + aliyunOssProperties.getBucketName() + "." + endpoint.replaceFirst("^https?://", "");
    }

    private String buildUserRoot(String userId) {
        return FILE_ROOT + "user" + userId + "/";
    }

    private OSS createOssClient() {
        return new OSSClientBuilder().build(
                normalizeEndpoint(aliyunOssProperties.getEndpoint()),
                aliyunOssProperties.getAccessKeyId(),
                aliyunOssProperties.getAccessKeySecret()
        );
    }

    private String buildFileUrl(String objectName) {
        String endpoint = aliyunOssProperties.getEndpoint();
        if (!hasText(endpoint)) {
            return objectName;
        }
        String normalizedEndpoint = endpoint.replaceFirst("^https?://", "");
        return "https://" + aliyunOssProperties.getBucketName() + "." + normalizedEndpoint + "/" + objectName;
    }

    private String normalizeEndpoint(String endpoint) {
        if (!hasText(endpoint)) {
            throw new IllegalArgumentException("OSS endpoint 未配置");
        }
        String value = endpoint.trim();
        if (value.startsWith("http://") || value.startsWith("https://")) {
            return value;
        }
        return "https://" + value;
    }

    private String normalizeObjectName(String objectName) {
        if (!hasText(objectName)) {
            return objectName;
        }
        String value = objectName.trim();
        if (value.startsWith("http://") || value.startsWith("https://")) {
            try {
                String path = new URI(value).getPath();
                value = path == null ? value : path;
            } catch (URISyntaxException ex) {
                throw new IllegalArgumentException("文件地址不合法");
            }
        }
        while (value.startsWith("/")) {
            value = value.substring(1);
        }
        return value;
    }

    private String getFileExtension(String originalFilename) {
        if (!hasText(originalFilename)) {
            return "";
        }
        String filename = originalFilename.trim();
        int dotIndex = filename.lastIndexOf('.');
        if (dotIndex < 0 || dotIndex == filename.length() - 1) {
            return "";
        }
        return filename.substring(dotIndex).toLowerCase(Locale.ROOT);
    }

    private void validateImage(String contentType, String fileExtension) {
        if (!hasText(contentType) || !contentType.toLowerCase(Locale.ROOT).startsWith("image/")) {
            throw new IllegalArgumentException("只允许上传图片文件");
        }
        if (!ALLOWED_IMAGE_EXTENSIONS.contains(fileExtension)) {
            throw new IllegalArgumentException("只允许上传 jpg、jpeg、png、gif、webp、bmp 格式图片");
        }
    }

    private boolean hasText(String value) {
        return value != null && !value.isBlank();
    }
}
