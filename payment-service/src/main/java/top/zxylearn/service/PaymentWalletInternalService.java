package top.zxylearn.service;

import org.springframework.dao.DuplicateKeyException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import top.zxylearn.dto.payment.PaymentWalletCreateRequest;
import top.zxylearn.entity.PaymentWallet;
import top.zxylearn.mapper.PaymentWalletMapper;

import java.math.BigDecimal;

@Service
public class PaymentWalletInternalService {

    private final PaymentWalletMapper paymentWalletMapper;

    public PaymentWalletInternalService(PaymentWalletMapper paymentWalletMapper) {
        this.paymentWalletMapper = paymentWalletMapper;
    }

    @Transactional(rollbackFor = Exception.class)
    public void createWallet(PaymentWalletCreateRequest request) {
        if (request == null || request.getUserId() == null) {
            throw new IllegalArgumentException("用户ID不能为空");
        }
        PaymentWallet wallet = new PaymentWallet();
        wallet.setUserId(request.getUserId());
        wallet.setBalance(BigDecimal.ZERO);
        try {
            paymentWalletMapper.insert(wallet);
        } catch (DuplicateKeyException ex) {
            throw new IllegalArgumentException("用户钱包已存在");
        }
    }
}
