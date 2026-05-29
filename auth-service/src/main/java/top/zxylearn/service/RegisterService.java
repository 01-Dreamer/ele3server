package top.zxylearn.service;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.stereotype.Service;
import top.zxylearn.dto.RegisterRequest;
import top.zxylearn.entity.AuthAccount;
import top.zxylearn.mapper.AuthAccountMapper;
import top.zxylearn.vo.RegisterVO;

import java.util.regex.Pattern;

@Service
public class RegisterService {

    private static final Pattern PASSWORD_PATTERN = Pattern.compile("^[A-Za-z0-9]{6,20}$");
    private static final String DEFAULT_ROLE = "USER";
    private static final int NORMAL_STATUS = 1;

    private final AuthAccountMapper authAccountMapper;
    private final EmailCaptchaService emailCaptchaService;
    private final BCryptPasswordEncoder passwordEncoder = new BCryptPasswordEncoder();

    public RegisterService(AuthAccountMapper authAccountMapper, EmailCaptchaService emailCaptchaService) {
        this.authAccountMapper = authAccountMapper;
        this.emailCaptchaService = emailCaptchaService;
    }

    public RegisterVO register(RegisterRequest request) {
        if (request == null) {
            throw new IllegalArgumentException("请求参数不能为空");
        }
        checkPassword(request.getPassword());
        String email = emailCaptchaService.verifyRegisterEmailCaptcha(request.getEmail(), request.getEmailCaptcha());
        checkEmailNotRegistered(email);

        AuthAccount account = new AuthAccount();
        account.setEmail(email);
        account.setPasswordHash(passwordEncoder.encode(request.getPassword()));
        account.setRole(DEFAULT_ROLE);
        account.setStatus(NORMAL_STATUS);

        try {
            authAccountMapper.insert(account);
        } catch (DuplicateKeyException ex) {
            throw new IllegalArgumentException("邮箱已注册");
        }

        return new RegisterVO(String.valueOf(account.getUserId()), account.getEmail(), account.getRole(), account.getStatus());
    }

    private void checkPassword(String password) {
        if (password == null || password.isBlank()) {
            throw new IllegalArgumentException("密码不能为空");
        }
        if (!PASSWORD_PATTERN.matcher(password).matches()) {
            throw new IllegalArgumentException("密码只能包含字母和数字，长度为 6-20 位");
        }
    }

    private void checkEmailNotRegistered(String email) {
        Long count = authAccountMapper.selectCount(
                new LambdaQueryWrapper<AuthAccount>().eq(AuthAccount::getEmail, email)
        );
        if (count != null && count > 0) {
            throw new IllegalArgumentException("邮箱已注册");
        }
    }
}
