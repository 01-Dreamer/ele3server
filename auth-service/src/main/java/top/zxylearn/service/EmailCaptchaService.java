package top.zxylearn.service;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.mail.SimpleMailMessage;
import org.springframework.mail.javamail.JavaMailSender;
import org.springframework.stereotype.Service;
import top.zxylearn.client.RiskCaptchaClient;
import top.zxylearn.dto.EmailCaptchaSendRequest;
import top.zxylearn.dto.RiskCaptchaVerifyRequest;
import top.zxylearn.entity.AuthAccount;
import top.zxylearn.enums.EmailCaptchaScene;
import top.zxylearn.mapper.AuthAccountMapper;
import top.zxylearn.result.Result;

import java.security.SecureRandom;
import java.time.Duration;
import java.util.regex.Pattern;

@Service
public class EmailCaptchaService {

    private static final Pattern EMAIL_PATTERN = Pattern.compile("^[A-Za-z0-9._%+-]+@[A-Za-z0-9.-]+\\.[A-Za-z]{2,}$");
    private static final SecureRandom RANDOM = new SecureRandom();
    private static final String EMAIL_CAPTCHA_KEY_PREFIX = "auth:email:captcha:";
    private static final String SLIDER_CAPTCHA_TYPE = "SLIDER";

    private final AuthAccountMapper authAccountMapper;
    private final RiskCaptchaClient riskCaptchaClient;
    private final StringRedisTemplate stringRedisTemplate;
    private final JavaMailSender javaMailSender;
    private final String mailFrom;
    private final Duration ttl;

    public EmailCaptchaService(AuthAccountMapper authAccountMapper,
                               RiskCaptchaClient riskCaptchaClient,
                               StringRedisTemplate stringRedisTemplate,
                               JavaMailSender javaMailSender,
                               @Value("${spring.mail.username}") String mailFrom,
                               @Value("${auth.email-captcha.ttl:5m}") Duration ttl) {
        this.authAccountMapper = authAccountMapper;
        this.riskCaptchaClient = riskCaptchaClient;
        this.stringRedisTemplate = stringRedisTemplate;
        this.javaMailSender = javaMailSender;
        this.mailFrom = mailFrom;
        this.ttl = ttl;
    }

    public long sendRegisterEmailCaptcha(EmailCaptchaSendRequest request) {
        if (request == null) {
            throw new IllegalArgumentException("请求参数不能为空");
        }
        verifySliderCaptcha(request);
        return sendEmailCaptcha(request.getEmail(), EmailCaptchaScene.REGISTER);
    }

    public long sendForgotPasswordEmailCaptcha(EmailCaptchaSendRequest request) {
        if (request == null) {
            throw new IllegalArgumentException("请求参数不能为空");
        }
        verifySliderCaptcha(request);
        return sendEmailCaptcha(request.getEmail(), EmailCaptchaScene.FORGOT_PASSWORD);
    }

    public String verifyRegisterEmailCaptcha(String email, String code) {
        return verifyEmailCaptcha(email, code, EmailCaptchaScene.REGISTER);
    }

    public long sendChangePasswordEmailCaptcha(String email, EmailCaptchaSendRequest request) {
        if (request == null) {
            throw new IllegalArgumentException("请求参数不能为空");
        }
        verifySliderCaptcha(request);
        return sendEmailCaptcha(email, EmailCaptchaScene.CHANGE_PASSWORD);
    }

    public boolean verifyChangePasswordEmailCaptcha(String email, String code) {
        if (code == null || code.isBlank()) {
            return false;
        }
        try {
            verifyEmailCaptcha(email, code, EmailCaptchaScene.CHANGE_PASSWORD);
            return true;
        } catch (IllegalArgumentException ex) {
            return false;
        }
    }

    public String verifyForgotPasswordEmailCaptcha(String email, String code) {
        return verifyEmailCaptcha(email, code, EmailCaptchaScene.FORGOT_PASSWORD);
    }

    private long sendEmailCaptcha(String email, EmailCaptchaScene scene) {
        String normalizedEmail = normalizeEmail(email);
        checkScene(scene);

        if (scene == EmailCaptchaScene.REGISTER) {
            checkEmailNotRegistered(normalizedEmail);
        }
        if (scene == EmailCaptchaScene.FORGOT_PASSWORD) {
            checkEmailRegistered(normalizedEmail);
        }

        String key = buildEmailCaptchaKey(scene, normalizedEmail);
        if (Boolean.TRUE.equals(stringRedisTemplate.hasKey(key))) {
            throw new IllegalArgumentException("验证码已发送，请稍后再试");
        }

        String code = generateCode();
        sendMail(normalizedEmail, code, scene);
        stringRedisTemplate.opsForValue().set(key, code, ttl);
        return ttl.toSeconds();
    }

    private void checkScene(EmailCaptchaScene scene) {
        if (scene == null) {
            throw new IllegalArgumentException("验证码场景不能为空");
        }
    }

    private void verifySliderCaptcha(EmailCaptchaSendRequest request) {
        RiskCaptchaVerifyRequest verifyRequest = new RiskCaptchaVerifyRequest(
                request.getCaptchaId(),
                SLIDER_CAPTCHA_TYPE,
                null,
                request.getCaptchaData()
        );
        try {
            Result<?> result = riskCaptchaClient.verifyCaptcha(verifyRequest);
            if (result == null || result.getCode() == null || result.getCode() != 200) {
                String message = result == null || result.getMessage() == null ? "滑块验证码校验失败" : result.getMessage();
                throw new IllegalArgumentException(message);
            }
        } catch (IllegalArgumentException ex) {
            throw ex;
        } catch (RuntimeException ex) {
            return;
        }
    }

    private String verifyEmailCaptcha(String email, String code, EmailCaptchaScene scene) {
        String normalizedEmail = normalizeEmail(email);
        checkScene(scene);
        if (code == null || code.isBlank()) {
            throw new IllegalArgumentException("邮箱验证码不能为空");
        }
        String key = buildEmailCaptchaKey(scene, normalizedEmail);
        String cachedCode = stringRedisTemplate.opsForValue().get(key);
        if (cachedCode == null) {
            throw new IllegalArgumentException("邮箱验证码已过期");
        }
        if (!cachedCode.equals(code.trim())) {
            throw new IllegalArgumentException("邮箱验证码错误");
        }
        stringRedisTemplate.delete(key);
        return normalizedEmail;
    }

    private String normalizeEmail(String email) {
        if (email == null || email.isBlank()) {
            throw new IllegalArgumentException("邮箱不能为空");
        }
        String normalizedEmail = email.trim();
        if (!EMAIL_PATTERN.matcher(normalizedEmail).matches()) {
            throw new IllegalArgumentException("邮箱格式不正确");
        }
        return normalizedEmail;
    }

    private void checkEmailNotRegistered(String email) {
        Long count = authAccountMapper.selectCount(
                new LambdaQueryWrapper<AuthAccount>().eq(AuthAccount::getEmail, email)
        );
        if (count != null && count > 0) {
            throw new IllegalArgumentException("邮箱已注册");
        }
    }

    private void checkEmailRegistered(String email) {
        Long count = authAccountMapper.selectCount(
                new LambdaQueryWrapper<AuthAccount>().eq(AuthAccount::getEmail, email)
        );
        if (count == null || count == 0) {
            throw new IllegalArgumentException("邮箱未注册");
        }
    }

    private String generateCode() {
        return String.valueOf(RANDOM.nextInt(900000) + 100000);
    }

    private String buildEmailCaptchaKey(EmailCaptchaScene scene, String email) {
        return EMAIL_CAPTCHA_KEY_PREFIX + scene.getKey() + ":" + email;
    }

    private void sendMail(String email, String code, EmailCaptchaScene scene) {
        SimpleMailMessage message = new SimpleMailMessage();
        message.setFrom(mailFrom);
        message.setTo(email);
        message.setSubject("ele-" + scene.getDescription() + "邮箱验证码");
        message.setText("您的" + scene.getDescription() + "邮箱验证码是：" + code + "，有效期 "
                + ttl.toMinutes() + " 分钟。请勿将验证码告诉他人。");
        javaMailSender.send(message);
    }
}
