package top.zxylearn.service;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;
import top.zxylearn.client.RiskCaptchaClient;
import top.zxylearn.config.UserLoginProperties;
import top.zxylearn.dto.LoginRequest;
import top.zxylearn.dto.LoginResponse;
import top.zxylearn.dto.LoginUser;
import top.zxylearn.dto.RiskSliderCaptchaVerifyRequest;
import top.zxylearn.entity.EleUser;
import top.zxylearn.mapper.EleUserMapper;
import top.zxylearn.result.Result;

import java.util.Set;
import java.util.UUID;

@Service
public class LoginService {

    private static final Integer BANNED_STATUS = 2;
    private static final String TOKEN_KEY_PREFIX = "user:login:token:";
    private static final String INFO_KEY_PREFIX = "user:login:info:";
    private static final String TOKENS_KEY_PREFIX = "user:login:tokens:";

    private final RiskCaptchaClient riskCaptchaClient;
    private final EleUserMapper eleUserMapper;
    private final StringRedisTemplate redisTemplate;
    private final UserLoginProperties userLoginProperties;
    private final PasswordEncoder passwordEncoder = new BCryptPasswordEncoder();
    private final ObjectMapper objectMapper = new ObjectMapper();

    public LoginService(RiskCaptchaClient riskCaptchaClient,
                        EleUserMapper eleUserMapper,
                        StringRedisTemplate redisTemplate,
                        UserLoginProperties userLoginProperties) {
        this.riskCaptchaClient = riskCaptchaClient;
        this.eleUserMapper = eleUserMapper;
        this.redisTemplate = redisTemplate;
        this.userLoginProperties = userLoginProperties;
    }

    public LoginResponse login(LoginRequest request) {
        validateLoginRequest(request);
        verifySliderCaptcha(new RiskSliderCaptchaVerifyRequest(
                request.getSliderCaptchaId(),
                request.getSliderCaptchaData()
        ));

        EleUser user = eleUserMapper.selectOne(new LambdaQueryWrapper<EleUser>()
                .eq(EleUser::getEmail, request.getEmail()));
        if (user == null || !passwordEncoder.matches(request.getPassword(), user.getPasswordHash())) {
            throw new IllegalArgumentException("邮箱或密码错误");
        }
        if (BANNED_STATUS.equals(user.getStatus())) {
            throw new IllegalArgumentException("账号已被封禁");
        }

        String token = generateToken();
        LoginUser loginUser = new LoginUser(
                String.valueOf(user.getId()),
                user.getEmail(),
                user.getNickname(),
                user.getAvatarUrl(),
                user.getGender(),
                user.getStatus()
        );
        cacheLoginUser(token, loginUser);
        return new LoginResponse(
                token,
                loginUser.getUserId(),
                loginUser.getEmail(),
                loginUser.getNickname(),
                loginUser.getAvatarUrl(),
                loginUser.getGender()
        );
    }

    private void validateLoginRequest(LoginRequest request) {
        if (request == null) {
            throw new IllegalArgumentException("请求参数不能为空");
        }
        if (!StringUtils.hasText(request.getEmail())) {
            throw new IllegalArgumentException("邮箱不能为空");
        }
        if (!StringUtils.hasText(request.getPassword())) {
            throw new IllegalArgumentException("密码不能为空");
        }
        if (!StringUtils.hasText(request.getSliderCaptchaId()) || request.getSliderCaptchaData() == null) {
            throw new IllegalArgumentException("滑块验证码 ID 和滑动轨迹不能为空");
        }
    }

    private void verifySliderCaptcha(RiskSliderCaptchaVerifyRequest verifyRequest) {
        Result<?> verifyResult;
        try {
            verifyResult = riskCaptchaClient.verifySliderCaptcha(verifyRequest);
        } catch (RuntimeException ex) {
            return;
        }

        if (verifyResult == null || verifyResult.getCode() == null || verifyResult.getCode() != 200) {
            String message = verifyResult == null || !StringUtils.hasText(verifyResult.getMessage())
                    ? "滑块验证码校验失败"
                    : "滑块验证码校验失败：" + verifyResult.getMessage();
            throw new IllegalArgumentException(message);
        }
    }

    private void cacheLoginUser(String token, LoginUser loginUser) {
        try {
            String userId = loginUser.getUserId();
            String tokensKey = TOKENS_KEY_PREFIX + userId;

            cleanupExpiredTokens(tokensKey);
            redisTemplate.opsForValue().set(TOKEN_KEY_PREFIX + token, userId, userLoginProperties.getTtl());
            redisTemplate.opsForValue().set(INFO_KEY_PREFIX + userId, objectMapper.writeValueAsString(loginUser), userLoginProperties.getTtl());
            redisTemplate.opsForSet().add(tokensKey, token);
            redisTemplate.expire(tokensKey, userLoginProperties.getTtl());
        } catch (JsonProcessingException ex) {
            throw new IllegalStateException("登录信息缓存失败", ex);
        }
    }

    private void cleanupExpiredTokens(String tokensKey) {
        Set<String> tokens = redisTemplate.opsForSet().members(tokensKey);
        if (tokens == null || tokens.isEmpty()) {
            return;
        }
        for (String token : tokens) {
            if (!Boolean.TRUE.equals(redisTemplate.hasKey(TOKEN_KEY_PREFIX + token))) {
                redisTemplate.opsForSet().remove(tokensKey, token);
            }
        }
    }

    private String generateToken() {
        return UUID.randomUUID().toString().replace("-", "");
    }
}
