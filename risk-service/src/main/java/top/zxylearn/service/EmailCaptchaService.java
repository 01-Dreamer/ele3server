package top.zxylearn.service;

import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.mail.SimpleMailMessage;
import org.springframework.mail.javamail.JavaMailSender;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

import java.security.SecureRandom;
import java.time.Duration;
import java.util.regex.Pattern;

@Service
public class EmailCaptchaService {

    private static final Pattern EMAIL_PATTERN = Pattern.compile("^[A-Za-z0-9+_.-]+@[A-Za-z0-9.-]+$");
    private static final Duration CODE_TTL = Duration.ofMinutes(5);
    private static final String CODE_KEY_PREFIX = "risk:email:captcha:";
    private static final SecureRandom RANDOM = new SecureRandom();

    private final JavaMailSender mailSender;
    private final StringRedisTemplate redisTemplate;

    public EmailCaptchaService(JavaMailSender mailSender, StringRedisTemplate redisTemplate) {
        this.mailSender = mailSender;
        this.redisTemplate = redisTemplate;
    }

    public void sendCode(String email) {
        validateEmail(email);
        String code = generateCode();
        redisTemplate.opsForValue().set(buildCodeKey(email), code, CODE_TTL);
        SimpleMailMessage message = new SimpleMailMessage();
        message.setFrom("2711339704@qq.com");
        message.setTo(email);
        message.setSubject("Ele3");
        message.setText("你的邮箱验证码是：" + code + "，5 分钟内有效。");
        mailSender.send(message);
    }

    public boolean verifyCode(String email, String code) {
        validateEmail(email);
        if (!StringUtils.hasText(code)) {
            return false;
        }
        String key = buildCodeKey(email);
        String cachedCode = redisTemplate.opsForValue().get(key);
        if (!code.equals(cachedCode)) {
            return false;
        }
        redisTemplate.delete(key);
        return true;
    }

    private void validateEmail(String email) {
        if (!StringUtils.hasText(email) || !EMAIL_PATTERN.matcher(email).matches()) {
            throw new IllegalArgumentException("email format is invalid");
        }
    }

    private String generateCode() {
        return String.format("%06d", RANDOM.nextInt(1_000_000));
    }

    private String buildCodeKey(String email) {
        return CODE_KEY_PREFIX + email;
    }
}
