package top.zxylearn.service;

import cloud.tianai.captcha.application.ImageCaptchaApplication;
import cloud.tianai.captcha.application.vo.ImageCaptchaVO;
import cloud.tianai.captcha.common.constant.CaptchaTypeConstant;
import cloud.tianai.captcha.common.response.ApiResponse;
import cloud.tianai.captcha.validator.common.model.dto.ImageCaptchaTrack;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Service;
import top.zxylearn.vo.TextCaptchaVO;

import javax.imageio.ImageIO;
import java.awt.BasicStroke;
import java.awt.Color;
import java.awt.Font;
import java.awt.Graphics2D;
import java.awt.RenderingHints;
import java.awt.geom.AffineTransform;
import java.awt.image.BufferedImage;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.security.SecureRandom;
import java.time.Duration;
import java.util.Base64;
import java.util.Locale;
import java.util.UUID;

@Service
public class ImageCaptchaService {

    public static final String TEXT_CAPTCHA_TYPE = "IMAGE";

    private static final String TEXT_CAPTCHA_KEY_PREFIX = "risk:captcha:text:";
    private static final String TEXT_CAPTCHA_CHARS = "23456789ABCDEFGHJKLMNPQRSTUVWXYZabcdefghijkmnpqrstuvwxyz";
    private static final int TEXT_CAPTCHA_LENGTH = 6;
    private static final int TEXT_CAPTCHA_WIDTH = 160;
    private static final int TEXT_CAPTCHA_HEIGHT = 60;
    private static final SecureRandom RANDOM = new SecureRandom();

    private final ImageCaptchaApplication imageCaptchaApplication;
    private final StringRedisTemplate stringRedisTemplate;
    private final Duration textCaptchaTtl;

    public ImageCaptchaService(ImageCaptchaApplication imageCaptchaApplication,
                               StringRedisTemplate stringRedisTemplate,
                               @Value("${captcha.text.ttl:120s}") Duration textCaptchaTtl) {
        this.imageCaptchaApplication = imageCaptchaApplication;
        this.stringRedisTemplate = stringRedisTemplate;
        this.textCaptchaTtl = textCaptchaTtl;
    }

    public ApiResponse<ImageCaptchaVO> generateSliderCaptcha() {
        return imageCaptchaApplication.generateCaptcha(CaptchaTypeConstant.SLIDER);
    }

    public TextCaptchaVO generateTextCaptcha() {
        String id = TEXT_CAPTCHA_TYPE + "_" + UUID.randomUUID().toString().replace("-", "");
        String code = generateCode();
        stringRedisTemplate.opsForValue().set(buildTextCaptchaKey(id), code, textCaptchaTtl);
        return new TextCaptchaVO(id, TEXT_CAPTCHA_TYPE, renderImage(code), TEXT_CAPTCHA_WIDTH, TEXT_CAPTCHA_HEIGHT);
    }

    public ApiResponse<?> verifySliderCaptcha(String id, ImageCaptchaTrack data) {
        return imageCaptchaApplication.matching(id, data);
    }

    public boolean verifyTextCaptcha(String id, String code) {
        if (id == null || id.isBlank() || code == null || code.isBlank()) {
            throw new IllegalArgumentException("验证码 ID 和图片验证码不能为空");
        }
        String key = buildTextCaptchaKey(id.trim());
        String cachedCode = stringRedisTemplate.opsForValue().get(key);
        if (cachedCode == null) {
            return false;
        }
        boolean matched = cachedCode.equalsIgnoreCase(code.trim());
        if (matched) {
            stringRedisTemplate.delete(key);
        }
        return matched;
    }

    private String generateCode() {
        StringBuilder code = new StringBuilder(TEXT_CAPTCHA_LENGTH);
        for (int i = 0; i < TEXT_CAPTCHA_LENGTH; i++) {
            code.append(TEXT_CAPTCHA_CHARS.charAt(RANDOM.nextInt(TEXT_CAPTCHA_CHARS.length())));
        }
        return code.toString();
    }

    private String buildTextCaptchaKey(String id) {
        return TEXT_CAPTCHA_KEY_PREFIX + id;
    }

    private String renderImage(String code) {
        BufferedImage image = new BufferedImage(TEXT_CAPTCHA_WIDTH, TEXT_CAPTCHA_HEIGHT, BufferedImage.TYPE_INT_RGB);
        Graphics2D graphics = image.createGraphics();
        try {
            graphics.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
            graphics.setColor(new Color(246, 248, 250));
            graphics.fillRect(0, 0, TEXT_CAPTCHA_WIDTH, TEXT_CAPTCHA_HEIGHT);
            drawNoise(graphics);
            drawCode(graphics, code);

            ByteArrayOutputStream outputStream = new ByteArrayOutputStream();
            ImageIO.write(image, "png", outputStream);
            return "data:image/png;base64," + Base64.getEncoder().encodeToString(outputStream.toByteArray());
        } catch (IOException ex) {
            throw new IllegalStateException("图片验证码生成失败", ex);
        } finally {
            graphics.dispose();
        }
    }

    private void drawNoise(Graphics2D graphics) {
        graphics.setStroke(new BasicStroke(1.2F));
        for (int i = 0; i < 8; i++) {
            graphics.setColor(randomColor(120, 220));
            int x1 = RANDOM.nextInt(TEXT_CAPTCHA_WIDTH);
            int y1 = RANDOM.nextInt(TEXT_CAPTCHA_HEIGHT);
            int x2 = RANDOM.nextInt(TEXT_CAPTCHA_WIDTH);
            int y2 = RANDOM.nextInt(TEXT_CAPTCHA_HEIGHT);
            graphics.drawLine(x1, y1, x2, y2);
        }
        for (int i = 0; i < 80; i++) {
            graphics.setColor(randomColor(120, 230));
            graphics.fillOval(RANDOM.nextInt(TEXT_CAPTCHA_WIDTH), RANDOM.nextInt(TEXT_CAPTCHA_HEIGHT), 2, 2);
        }
    }

    private void drawCode(Graphics2D graphics, String code) {
        graphics.setFont(new Font(Font.SANS_SERIF, Font.BOLD, 34));
        int charSpace = TEXT_CAPTCHA_WIDTH / (TEXT_CAPTCHA_LENGTH + 1);
        for (int i = 0; i < code.length(); i++) {
            AffineTransform oldTransform = graphics.getTransform();
            int x = 14 + i * charSpace;
            int y = 40 + RANDOM.nextInt(8);
            double angle = Math.toRadians(RANDOM.nextInt(41) - 20);
            graphics.rotate(angle, x + 10, y - 12);
            graphics.setColor(randomColor(20, 120));
            graphics.drawString(String.valueOf(code.charAt(i)), x, y);
            graphics.setTransform(oldTransform);
        }
    }

    private Color randomColor(int min, int max) {
        int red = min + RANDOM.nextInt(max - min);
        int green = min + RANDOM.nextInt(max - min);
        int blue = min + RANDOM.nextInt(max - min);
        return new Color(red, green, blue);
    }

    public String normalizeCaptchaType(String type) {
        return type == null ? null : type.trim().toUpperCase(Locale.ROOT);
    }
}
