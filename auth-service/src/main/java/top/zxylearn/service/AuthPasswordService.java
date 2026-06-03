package top.zxylearn.service;

import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.stereotype.Service;
import top.zxylearn.dto.ChangePasswordRequest;
import top.zxylearn.dto.ForgotPasswordResetRequest;
import top.zxylearn.entity.AuthAccount;
import top.zxylearn.mapper.AuthAccountMapper;
import top.zxylearn.util.PasswordValidator;

@Service
public class AuthPasswordService {

    private static final int BANNED_STATUS = 2;

    private final AuthAccountMapper authAccountMapper;
    private final EmailCaptchaService emailCaptchaService;
    private final BCryptPasswordEncoder passwordEncoder = new BCryptPasswordEncoder();

    public AuthPasswordService(AuthAccountMapper authAccountMapper, EmailCaptchaService emailCaptchaService) {
        this.authAccountMapper = authAccountMapper;
        this.emailCaptchaService = emailCaptchaService;
    }

    public long sendChangePasswordEmailCaptcha(String userId) {
        AuthAccount account = getAvailableAccount(parseUserId(userId));
        return emailCaptchaService.sendChangePasswordEmailCaptcha(account.getEmail());
    }

    public void changePassword(String userId, ChangePasswordRequest request) {
        Long parsedUserId = parseUserId(userId);
        if (request == null) {
            throw new IllegalArgumentException("请求参数不能为空");
        }
        PasswordValidator.checkPassword(request.getNewPassword());

        AuthAccount account = getAvailableAccount(parsedUserId);
        boolean oldPasswordPassed = verifyOldPassword(request.getOldPassword(), account);
        boolean emailCaptchaPassed = emailCaptchaService.verifyChangePasswordEmailCaptcha(
                account.getEmail(),
                request.getEmailCaptcha()
        );
        if (!oldPasswordPassed && !emailCaptchaPassed) {
            throw new IllegalArgumentException("旧密码或邮箱验证码错误");
        }
        if (passwordEncoder.matches(request.getNewPassword(), account.getPasswordHash())) {
            throw new IllegalArgumentException("新密码不能和旧密码相同");
        }

        account.setPasswordHash(passwordEncoder.encode(request.getNewPassword()));
        authAccountMapper.updateById(account);
    }

    public void resetForgotPassword(ForgotPasswordResetRequest request) {
        if (request == null) {
            throw new IllegalArgumentException("请求参数不能为空");
        }
        PasswordValidator.checkPassword(request.getNewPassword());
        String email = emailCaptchaService.verifyForgotPasswordEmailCaptcha(request.getEmail(), request.getEmailCaptcha());
        AuthAccount account = getAvailableAccountByEmail(email);
        if (passwordEncoder.matches(request.getNewPassword(), account.getPasswordHash())) {
            throw new IllegalArgumentException("新密码不能和旧密码相同");
        }
        account.setPasswordHash(passwordEncoder.encode(request.getNewPassword()));
        authAccountMapper.updateById(account);
    }

    private AuthAccount getAvailableAccount(Long userId) {
        AuthAccount account = authAccountMapper.selectById(userId);
        if (account == null) {
            throw new IllegalArgumentException("账号不存在");
        }
        if (account.getStatus() != null && account.getStatus() == BANNED_STATUS) {
            throw new IllegalArgumentException("账号已被封禁");
        }
        return account;
    }

    private AuthAccount getAvailableAccountByEmail(String email) {
        AuthAccount account = authAccountMapper.selectOne(
                new com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper<AuthAccount>()
                        .eq(AuthAccount::getEmail, email)
        );
        if (account == null) {
            throw new IllegalArgumentException("账号不存在");
        }
        if (account.getStatus() != null && account.getStatus() == BANNED_STATUS) {
            throw new IllegalArgumentException("账号已被封禁");
        }
        return account;
    }

    private boolean verifyOldPassword(String oldPassword, AuthAccount account) {
        if (oldPassword == null || oldPassword.isBlank() || account.getPasswordHash() == null) {
            return false;
        }
        return passwordEncoder.matches(oldPassword, account.getPasswordHash());
    }

    private Long parseUserId(String userId) {
        if (userId == null || userId.isBlank()) {
            throw new IllegalArgumentException("请先登录");
        }
        try {
            return Long.valueOf(userId);
        } catch (NumberFormatException ex) {
            throw new IllegalArgumentException("登录用户 ID 不合法");
        }
    }
}
