package top.zxylearn.service;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import org.apache.seata.spring.annotation.GlobalTransactional;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.stereotype.Service;
import top.zxylearn.client.PaymentWalletClient;
import top.zxylearn.client.UserClient;
import top.zxylearn.dto.RegisterRequest;
import top.zxylearn.dto.payment.PaymentWalletCreateRequest;
import top.zxylearn.dto.user.UserCreateRequest;
import top.zxylearn.entity.AuthAccount;
import top.zxylearn.mapper.AuthAccountMapper;
import top.zxylearn.result.Result;
import top.zxylearn.util.PasswordValidator;
import top.zxylearn.vo.RegisterVO;

@Service
public class RegisterService {

    private static final String DEFAULT_ROLE = "USER";
    private static final int NORMAL_STATUS = 0;

    private final AuthAccountMapper authAccountMapper;
    private final EmailCaptchaService emailCaptchaService;
    private final UserClient userClient;
    private final PaymentWalletClient paymentWalletClient;
    private final BCryptPasswordEncoder passwordEncoder = new BCryptPasswordEncoder();

    public RegisterService(AuthAccountMapper authAccountMapper,
                           EmailCaptchaService emailCaptchaService,
                           UserClient userClient,
                           PaymentWalletClient paymentWalletClient) {
        this.authAccountMapper = authAccountMapper;
        this.emailCaptchaService = emailCaptchaService;
        this.userClient = userClient;
        this.paymentWalletClient = paymentWalletClient;
    }

    @GlobalTransactional(name = "auth-register", rollbackFor = Exception.class)
    public RegisterVO register(RegisterRequest request) {
        if (request == null) {
            throw new IllegalArgumentException("请求参数不能为空");
        }
        PasswordValidator.checkPassword(request.getPassword());
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

        Long userId = account.getUserId();
        String userIdText = String.valueOf(userId);
        checkInternalCall(userClient.createUser(new UserCreateRequest(userIdText)), "用户资料创建失败");
        checkInternalCall(paymentWalletClient.createWallet(new PaymentWalletCreateRequest(userIdText)), "用户钱包创建失败");

        return new RegisterVO(userIdText, account.getEmail(), account.getRole(), account.getStatus());
    }

    private void checkInternalCall(Result<?> result, String defaultMessage) {
        if (result == null) {
            throw new RuntimeException(defaultMessage);
        }
        if (result.getCode() == null || result.getCode() != 200) {
            throw new RuntimeException(result.getMessage() == null ? defaultMessage : result.getMessage());
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
