package top.zxylearn.service;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;
import top.zxylearn.client.RiskCaptchaClient;
import top.zxylearn.dto.RegisterEmailCaptchaSendRequest;
import top.zxylearn.dto.RegisterRequest;
import top.zxylearn.dto.RiskSliderCaptchaVerifyRequest;
import top.zxylearn.entity.EleUser;
import top.zxylearn.mapper.EleUserMapper;
import top.zxylearn.result.Result;

@Service
public class RegisterService {

    private static final int MIN_PASSWORD_LENGTH = 6;
    private static final int MAX_PASSWORD_LENGTH = 20;
    private static final String PASSWORD_PATTERN = "^[A-Za-z0-9]{6,20}$";

    private final RiskCaptchaClient riskCaptchaClient;
    private final EmailCaptchaService emailCaptchaService;
    private final EleUserMapper eleUserMapper;
    private final PasswordEncoder passwordEncoder = new BCryptPasswordEncoder();

    public RegisterService(RiskCaptchaClient riskCaptchaClient,
                           EmailCaptchaService emailCaptchaService,
                           EleUserMapper eleUserMapper) {
        this.riskCaptchaClient = riskCaptchaClient;
        this.emailCaptchaService = emailCaptchaService;
        this.eleUserMapper = eleUserMapper;
    }

    public void sendRegisterEmailCode(RegisterEmailCaptchaSendRequest request) {
        validateRequest(request);
        if (isEmailRegistered(request.getEmail())) {
            throw new IllegalArgumentException("该邮箱已注册");
        }

        RiskSliderCaptchaVerifyRequest verifyRequest = new RiskSliderCaptchaVerifyRequest(
                request.getSliderCaptchaId(),
                request.getSliderCaptchaData()
        );
        verifySliderCaptcha(verifyRequest);

        emailCaptchaService.sendCode(request.getEmail());
    }

    public void register(RegisterRequest request) {
        validateRegisterRequest(request);
        if (isEmailRegistered(request.getEmail())) {
            throw new IllegalArgumentException("该邮箱已注册");
        }
        if (!emailCaptchaService.verifyCode(request.getEmail(), request.getEmailCode())) {
            throw new IllegalArgumentException("邮箱验证码不正确或已过期");
        }
        if (isEmailRegistered(request.getEmail())) {
            throw new IllegalArgumentException("该邮箱已注册");
        }

        EleUser user = new EleUser();
        user.setEmail(request.getEmail());
        user.setPasswordHash(passwordEncoder.encode(request.getPassword()));

        int rows;
        try {
            rows = eleUserMapper.insert(user);
        } catch (DuplicateKeyException ex) {
            throw new IllegalArgumentException("该邮箱已注册");
        }
        if (rows != 1) {
            throw new IllegalStateException("用户注册失败");
        }
    }

    private void validateRequest(RegisterEmailCaptchaSendRequest request) {
        if (request == null) {
            throw new IllegalArgumentException("请求参数不能为空");
        }
        if (!StringUtils.hasText(request.getEmail())) {
            throw new IllegalArgumentException("邮箱不能为空");
        }
        if (!StringUtils.hasText(request.getSliderCaptchaId()) || request.getSliderCaptchaData() == null) {
            throw new IllegalArgumentException("滑块验证码 ID 和滑动轨迹不能为空");
        }
    }

    private void validateRegisterRequest(RegisterRequest request) {
        if (request == null) {
            throw new IllegalArgumentException("请求参数不能为空");
        }
        if (!StringUtils.hasText(request.getEmail())) {
            throw new IllegalArgumentException("邮箱不能为空");
        }
        if (!StringUtils.hasText(request.getPassword())) {
            throw new IllegalArgumentException("密码不能为空");
        }
        if (request.getPassword().length() < MIN_PASSWORD_LENGTH || request.getPassword().length() > MAX_PASSWORD_LENGTH) {
            throw new IllegalArgumentException("密码长度必须为 6-20 位");
        }
        if (!request.getPassword().matches(PASSWORD_PATTERN)) {
            throw new IllegalArgumentException("密码只能包含字母或数字");
        }
        if (!StringUtils.hasText(request.getEmailCode())) {
            throw new IllegalArgumentException("邮箱验证码不能为空");
        }
    }

    private boolean isEmailRegistered(String email) {
        Long count = eleUserMapper.selectCount(new LambdaQueryWrapper<EleUser>()
                .eq(EleUser::getEmail, email));
        return count != null && count > 0;
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
}
