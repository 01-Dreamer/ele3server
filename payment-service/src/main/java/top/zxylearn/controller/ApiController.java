package top.zxylearn.controller;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import top.zxylearn.result.Result;
import top.zxylearn.service.PaymentWalletService;
import top.zxylearn.vo.PaymentWalletVO;

@Tag(name = "支付服务")
@RestController
@RequestMapping("/api/payment")
public class ApiController {

    private final PaymentWalletService paymentWalletService;

    public ApiController(PaymentWalletService paymentWalletService) {
        this.paymentWalletService = paymentWalletService;
    }

    @Operation(summary = "获取自己的钱包余额")
    @GetMapping("/balance")
    public Result<PaymentWalletVO> getBalance(@RequestHeader("X-User-Id") String userId) {
        try {
            return Result.success(paymentWalletService.getBalance(userId));
        } catch (IllegalArgumentException ex) {
            return Result.fail(400, ex.getMessage());
        } catch (RuntimeException ex) {
            return Result.fail(500, "余额获取失败");
        }
    }
}
