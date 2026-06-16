package top.zxylearn.controller;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import top.zxylearn.dto.payment.PaymentWalletCreateRequest;
import top.zxylearn.result.Result;
import top.zxylearn.service.PaymentWalletInternalService;

@Tag(name = "支付内部接口")
@RestController
@RequestMapping("/internal/payment")
public class InternalController {

    private final PaymentWalletInternalService paymentWalletInternalService;

    public InternalController(PaymentWalletInternalService paymentWalletInternalService) {
        this.paymentWalletInternalService = paymentWalletInternalService;
    }

    @Operation(summary = "创建用户钱包")
    @PostMapping("/create-wallet")
    public Result<?> createWallet(@RequestBody PaymentWalletCreateRequest request) {
        try {
            paymentWalletInternalService.createWallet(request);
            return Result.success();
        } catch (IllegalArgumentException ex) {
            return Result.fail(400, ex.getMessage());
        } catch (RuntimeException ex) {
            return Result.fail(500, "用户钱包创建失败");
        }
    }
}
