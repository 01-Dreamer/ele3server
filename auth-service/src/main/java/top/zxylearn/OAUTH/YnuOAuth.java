package top.zxylearn.OAUTH;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.stereotype.Service;
import top.zxylearn.entity.AuthThirdAccount;
import top.zxylearn.mapper.AuthThirdAccountMapper;
import top.zxylearn.service.LoginService;
import top.zxylearn.vo.LoginVO;

import java.io.IOException;
import java.net.URI;
import java.net.URLEncoder;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

@Service
public class YnuOAuth {

    private static final String BASE_URL = "http://127.0.0.1:7890/api/ynu-oauth";
    private static final String PROVIDER = "CAMPUS";
    private static final Duration TIMEOUT = Duration.ofSeconds(5);

    private final HttpClient httpClient = HttpClient.newBuilder()
            .connectTimeout(TIMEOUT)
            .build();
    private final ObjectMapper objectMapper = new ObjectMapper();
    private final AuthThirdAccountMapper authThirdAccountMapper;
    private final LoginService loginService;

    public YnuOAuth(AuthThirdAccountMapper authThirdAccountMapper,
                    LoginService loginService) {
        this.authThirdAccountMapper = authThirdAccountMapper;
        this.loginService = loginService;
    }

    public Map<String, Object> getQrCode() {
        return get(BASE_URL + "/get-qrcode");
    }

    public Map<String, Object> checkQrCode(String uuid) {
        if (uuid == null || uuid.isBlank()) {
            throw new IllegalArgumentException("二维码uuid不能为空");
        }
        return get(BASE_URL + "/check-qrcode?uuid=" + URLEncoder.encode(uuid.trim(), StandardCharsets.UTF_8));
    }

    public Object checkQrCodeAndLogin(String uuid, String loginIp) {
        Map<String, Object> response = checkQrCode(uuid);
        String studentId = extractStudentId(response);
        if (!hasText(studentId)) {
            return response;
        }

        AuthThirdAccount thirdAccount = findByOpenId(studentId);
        if (thirdAccount == null) {
            throw new IllegalArgumentException("该YNU账号未绑定，请先登录后绑定账号");
        }
        LoginVO loginVO = loginService.loginByUserId(String.valueOf(thirdAccount.getUserId()), loginIp);
        Map<String, Object> result = new LinkedHashMap<>();
        result.put("studentId", studentId);
        result.put("login", loginVO);
        return result;
    }

    public Object checkQrCodeAndBind(String userId, String uuid) {
        Long currentUserId = parseUserId(userId);
        Map<String, Object> response = checkQrCode(uuid);
        String studentId = extractStudentId(response);
        if (!hasText(studentId)) {
            return response;
        }

        AuthThirdAccount existingByOpenId = findByOpenId(studentId);
        if (existingByOpenId != null) {
            if (currentUserId.equals(existingByOpenId.getUserId())) {
                return buildBindResult(studentId, "BOUND");
            }
            throw new IllegalArgumentException("该YNU账号已绑定其他用户");
        }

        AuthThirdAccount existingByUser = authThirdAccountMapper.selectOne(
                new LambdaQueryWrapper<AuthThirdAccount>()
                        .eq(AuthThirdAccount::getProvider, PROVIDER)
                        .eq(AuthThirdAccount::getUserId, currentUserId)
                        .last("limit 1")
        );
        if (existingByUser != null) {
            throw new IllegalArgumentException("当前用户已绑定其他YNU账号");
        }

        AuthThirdAccount thirdAccount = new AuthThirdAccount();
        thirdAccount.setUserId(currentUserId);
        thirdAccount.setProvider(PROVIDER);
        thirdAccount.setOpenId(studentId);
        try {
            authThirdAccountMapper.insert(thirdAccount);
        } catch (DuplicateKeyException ex) {
            throw new IllegalArgumentException("该YNU账号已绑定其他用户");
        }
        return buildBindResult(studentId, "BOUND");
    }

    private AuthThirdAccount findByOpenId(String studentId) {
        return authThirdAccountMapper.selectOne(
                new LambdaQueryWrapper<AuthThirdAccount>()
                        .eq(AuthThirdAccount::getProvider, PROVIDER)
                        .eq(AuthThirdAccount::getOpenId, studentId)
                        .last("limit 1")
        );
    }

    private Map<String, Object> buildBindResult(String studentId, String status) {
        Map<String, Object> result = new LinkedHashMap<>();
        result.put("provider", PROVIDER);
        result.put("studentId", studentId);
        result.put("status", status);
        return result;
    }

    private String extractStudentId(Object value) {
        if (value instanceof Map<?, ?> map) {
            for (Map.Entry<?, ?> entry : map.entrySet()) {
                String key = String.valueOf(entry.getKey());
                Object entryValue = entry.getValue();
                if (("student_id".equals(key) || "studentId".equals(key)) && entryValue != null) {
                    return String.valueOf(entryValue).trim();
                }
                String nested = extractStudentId(entryValue);
                if (hasText(nested)) {
                    return nested;
                }
            }
        }
        if (value instanceof List<?> list) {
            for (Object item : list) {
                String nested = extractStudentId(item);
                if (hasText(nested)) {
                    return nested;
                }
            }
        }
        return null;
    }

    private Long parseUserId(String userId) {
        if (!hasText(userId)) {
            throw new IllegalArgumentException("用户ID不能为空");
        }
        try {
            return Long.valueOf(userId.trim());
        } catch (NumberFormatException ex) {
            throw new IllegalArgumentException("用户ID格式错误");
        }
    }

    private Map<String, Object> get(String url) {
        HttpRequest request = HttpRequest.newBuilder()
                .uri(URI.create(url))
                .timeout(TIMEOUT)
                .GET()
                .build();
        try {
            HttpResponse<String> response = httpClient.send(request, HttpResponse.BodyHandlers.ofString(StandardCharsets.UTF_8));
            if (response.statusCode() < 200 || response.statusCode() >= 300) {
                throw new RuntimeException("YNU OAuth请求失败，HTTP状态码=" + response.statusCode());
            }
            return objectMapper.readValue(response.body(), new TypeReference<>() {
            });
        } catch (IOException ex) {
            throw new RuntimeException("YNU OAuth响应解析失败", ex);
        } catch (InterruptedException ex) {
            Thread.currentThread().interrupt();
            throw new RuntimeException("YNU OAuth请求被中断", ex);
        }
    }

    private boolean hasText(String value) {
        return value != null && !value.isBlank();
    }
}
