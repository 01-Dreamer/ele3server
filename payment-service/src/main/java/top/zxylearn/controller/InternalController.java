package top.zxylearn.controller;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import top.zxylearn.dto.payment.PaymentWalletCreateRequest;
import top.zxylearn.dto.payment.PaymentWalletDeductRequest;
import top.zxylearn.result.Result;
import top.zxylearn.service.PaymentWalletService;

@Tag(name = "支付内部接口")
@RestController
@RequestMapping("/internal/payment")
public class InternalController {

    private final PaymentWalletService paymentWalletService;

    public InternalController(PaymentWalletService paymentWalletService) {
        this.paymentWalletService = paymentWalletService;
    }

    @Operation(summary = "创建用户钱包")
    @PostMapping("/create-wallet")
    public Result<?> createWallet(@RequestBody PaymentWalletCreateRequest request) {
        try {
            paymentWalletService.createWallet(request);
            return Result.success();
        } catch (IllegalArgumentException ex) {
            return Result.fail(400, ex.getMessage());
        } catch (RuntimeException ex) {
            return Result.fail(500, "用户钱包创建失败");
        }
    }

    @Operation(summary = "扣减用户钱包余额")
    @PostMapping("/deduct-balance")
    public Result<?> deductBalance(@RequestBody PaymentWalletDeductRequest request) {
        try {
            paymentWalletService.deductBalance(request);
            return Result.success();
        } catch (IllegalArgumentException ex) {
            return Result.fail(400, ex.getMessage());
        } catch (RuntimeException ex) {
            return Result.fail(500, "余额扣减失败");
        }
    }
}
