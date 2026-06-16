package top.zxylearn.client;

import org.springframework.cloud.openfeign.FeignClient;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import top.zxylearn.dto.payment.PaymentWalletCreateRequest;
import top.zxylearn.result.Result;

@FeignClient(name = "payment-service")
public interface PaymentWalletClient {

    @PostMapping("/internal/payment/create-wallet")
    Result<?> createWallet(@RequestBody PaymentWalletCreateRequest request);
}
