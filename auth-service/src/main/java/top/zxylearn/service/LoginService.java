package top.zxylearn.service;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.stereotype.Service;
import top.zxylearn.client.RiskCaptchaClient;
import top.zxylearn.dto.LoginRequest;
import top.zxylearn.dto.RiskCaptchaVerifyRequest;
import top.zxylearn.entity.AuthAccount;
import top.zxylearn.mapper.AuthAccountMapper;
import top.zxylearn.result.Result;
import top.zxylearn.vo.LoginTokenVO;
import top.zxylearn.vo.LoginUserVO;
import top.zxylearn.vo.LoginVO;

import java.time.Duration;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.Set;
import java.util.UUID;

@Service
public class LoginService {

    private static final int NORMAL_STATUS = 0;
    private static final int BANNED_STATUS = 1;
    private static final String LOGIN_TOKEN_KEY_PREFIX = "auth:login:";
    private static final String LOGIN_USER_KEY_PREFIX = "auth:user:";
    private static final String LOGIN_TOKENS_KEY_PREFIX = "auth:login:tokens:";
    private static final String IMAGE_CAPTCHA_TYPE = "IMAGE";
    private static final DateTimeFormatter LOGIN_TIME_FORMATTER = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss");

    private final AuthAccountMapper authAccountMapper;
    private final RiskCaptchaClient riskCaptchaClient;
    private final StringRedisTemplate stringRedisTemplate;
    private final ObjectMapper objectMapper = new ObjectMapper();
    private final Duration loginTtl;
    private final BCryptPasswordEncoder passwordEncoder = new BCryptPasswordEncoder();

    public LoginService(AuthAccountMapper authAccountMapper,
                        RiskCaptchaClient riskCaptchaClient,
                        StringRedisTemplate stringRedisTemplate,
                        @Value("${auth.login.ttl}") Duration loginTtl) {
        this.authAccountMapper = authAccountMapper;
        this.riskCaptchaClient = riskCaptchaClient;
        this.stringRedisTemplate = stringRedisTemplate;
        this.loginTtl = loginTtl;
    }

    public LoginVO login(LoginRequest request, String loginIp) {
        if (request == null) {
            throw new IllegalArgumentException("请求参数不能为空");
        }
        String email = checkEmail(request.getEmail());
        String password = checkPassword(request.getPassword());
        verifyImageCaptcha(request);

        AuthAccount account = authAccountMapper.selectOne(
                new LambdaQueryWrapper<AuthAccount>().eq(AuthAccount::getEmail, email)
        );
        if (account == null || account.getPasswordHash() == null || !passwordEncoder.matches(password, account.getPasswordHash())) {
            throw new IllegalArgumentException("邮箱或密码错误");
        }
        if (account.getStatus() != null && account.getStatus() == BANNED_STATUS) {
            throw new IllegalArgumentException("账号已被封禁");
        }
        if (account.getStatus() == null || account.getStatus() != NORMAL_STATUS) {
            throw new IllegalArgumentException("账号状态异常");
        }

        String token = UUID.randomUUID().toString().replace("-", "");
        LoginUserVO userInfo = new LoginUserVO(
                String.valueOf(account.getUserId()),
                account.getEmail(),
                account.getRole(),
                account.getStatus()
        );

        cacheLoginInfo(token, userInfo, loginIp);
        return new LoginVO(token, userInfo);
    }

    private void cacheLoginInfo(String token, LoginUserVO userInfo, String loginIp) {
        String userId = userInfo.getUserId();
        LoginTokenVO loginToken = new LoginTokenVO(
                userId,
                loginIp,
                LocalDateTime.now().format(LOGIN_TIME_FORMATTER)
        );
        stringRedisTemplate.opsForValue().set(buildLoginTokenKey(token), toJson(loginToken), loginTtl);
        stringRedisTemplate.opsForValue().set(buildLoginUserKey(userId), toJson(userInfo), loginTtl);
        cleanExpiredTokenMembers(userId);
        stringRedisTemplate.opsForSet().add(buildLoginTokensKey(userId), token);
        stringRedisTemplate.expire(buildLoginTokensKey(userId), loginTtl);
    }

    private void cleanExpiredTokenMembers(String userId) {
        String tokensKey = buildLoginTokensKey(userId);
        Set<String> tokens = stringRedisTemplate.opsForSet().members(tokensKey);
        if (tokens == null || tokens.isEmpty()) {
            return;
        }
        for (String token : tokens) {
            if (!Boolean.TRUE.equals(stringRedisTemplate.hasKey(buildLoginTokenKey(token)))) {
                stringRedisTemplate.opsForSet().remove(tokensKey, token);
            }
        }
    }

    private String checkEmail(String email) {
        if (email == null || email.isBlank()) {
            throw new IllegalArgumentException("邮箱不能为空");
        }
        return email.trim();
    }

    private String checkPassword(String password) {
        if (password == null || password.isBlank()) {
            throw new IllegalArgumentException("密码不能为空");
        }
        return password;
    }

    private void verifyImageCaptcha(LoginRequest request) {
        RiskCaptchaVerifyRequest verifyRequest = new RiskCaptchaVerifyRequest(
                request.getCaptchaId(),
                IMAGE_CAPTCHA_TYPE,
                request.getCaptchaCode(),
                null
        );
        try {
            Result<?> result = riskCaptchaClient.verifyCaptcha(verifyRequest);
            if (result == null || result.getCode() == null || result.getCode() != 200) {
                String message = result == null || result.getMessage() == null ? "图形验证码校验失败" : result.getMessage();
                throw new IllegalArgumentException(message);
            }
        } catch (IllegalArgumentException ex) {
            throw ex;
        } catch (RuntimeException ex) {
            return;
        }
    }

    private String toJson(LoginUserVO userInfo) {
        return toJson((Object) userInfo);
    }

    private String toJson(LoginTokenVO loginToken) {
        return toJson((Object) loginToken);
    }

    private String toJson(Object value) {
        try {
            return objectMapper.writeValueAsString(value);
        } catch (JsonProcessingException ex) {
            throw new IllegalStateException("登录信息序列化失败", ex);
        }
    }

    private String buildLoginTokenKey(String token) {
        return LOGIN_TOKEN_KEY_PREFIX + token;
    }

    private String buildLoginUserKey(String userId) {
        return LOGIN_USER_KEY_PREFIX + userId;
    }

    private String buildLoginTokensKey(String userId) {
        return LOGIN_TOKENS_KEY_PREFIX + userId;
    }
}
