package top.zxylearn.service;

import org.springframework.dao.DuplicateKeyException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import top.zxylearn.dto.payment.PaymentWalletAddRequest;
import top.zxylearn.dto.payment.PaymentWalletCreateRequest;
import top.zxylearn.dto.payment.PaymentWalletDeductRequest;
import top.zxylearn.entity.PaymentWallet;
import top.zxylearn.mapper.PaymentWalletMapper;
import top.zxylearn.vo.PaymentWalletVO;

import java.math.BigDecimal;

@Service
public class PaymentWalletService {

    private final PaymentWalletMapper paymentWalletMapper;

    public PaymentWalletService(PaymentWalletMapper paymentWalletMapper) {
        this.paymentWalletMapper = paymentWalletMapper;
    }

    @Transactional(rollbackFor = Exception.class)
    public void createWallet(PaymentWalletCreateRequest request) {
        if (request == null) {
            throw new IllegalArgumentException("用户ID不能为空");
        }
        PaymentWallet wallet = new PaymentWallet();
        wallet.setUserId(parseUserId(request.getUserId()));
        wallet.setBalance(BigDecimal.ZERO);
        try {
            paymentWalletMapper.insert(wallet);
        } catch (DuplicateKeyException ex) {
            throw new IllegalArgumentException("用户钱包已存在");
        }
    }

    public PaymentWalletVO getBalance(String userId) {
        PaymentWallet wallet = getWallet(parseUserId(userId));
        return new PaymentWalletVO(wallet.getBalance());
    }

    @Transactional(rollbackFor = Exception.class)
    public void deductBalance(PaymentWalletDeductRequest request) {
        if (request == null) {
            throw new IllegalArgumentException("扣款参数不能为空");
        }
        Long userId = parseUserId(request.getUserId());
        BigDecimal amount = request.getAmount();
        checkAmount(amount, "扣款金额");
        int updated = paymentWalletMapper.deductBalance(userId, amount);
        if (updated > 0) {
            return;
        }
        getWallet(userId);
        throw new IllegalArgumentException("余额不足");
    }


    @Transactional(rollbackFor = Exception.class)
    public void addBalance(PaymentWalletAddRequest request) {
        if (request == null) {
            throw new IllegalArgumentException("加款参数不能为空");
        }
        addBalance(parseUserId(request.getUserId()), request.getAmount());
    }

    @Transactional(rollbackFor = Exception.class)
    public void addBalance(Long userId, BigDecimal amount) {
        if (userId == null) {
            throw new IllegalArgumentException("用户ID不能为空");
        }
        checkAmount(amount, "充值金额");
        int updated = paymentWalletMapper.addBalance(userId, amount);
        if (updated == 0) {
            throw new IllegalArgumentException("用户钱包不存在");
        }
    }

    private PaymentWallet getWallet(Long userId) {
        if (userId == null) {
            throw new IllegalArgumentException("用户ID不能为空");
        }
        PaymentWallet wallet = paymentWalletMapper.selectById(userId);
        if (wallet == null) {
            throw new IllegalArgumentException("用户钱包不存在");
        }
        return wallet;
    }

    private Long parseUserId(String userId) {
        if (userId == null || userId.isBlank()) {
            throw new IllegalArgumentException("用户ID不能为空");
        }
        try {
            return Long.valueOf(userId.trim());
        } catch (NumberFormatException ex) {
            throw new IllegalArgumentException("用户ID格式不正确");
        }
    }

    private void checkAmount(BigDecimal amount, String fieldName) {
        if (amount == null) {
            throw new IllegalArgumentException(fieldName + "不能为空");
        }
        if (amount.compareTo(BigDecimal.ZERO) <= 0) {
            throw new IllegalArgumentException(fieldName + "必须大于0");
        }
        if (amount.stripTrailingZeros().scale() > 2) {
            throw new IllegalArgumentException(fieldName + "最多只能保留两位小数");
        }
    }
}
