package top.zxylearn.controller;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import top.zxylearn.result.Result;
import top.zxylearn.service.PaymentService;
import top.zxylearn.service.PaymentWalletService;
import top.zxylearn.vo.PaymentStatusVO;
import top.zxylearn.vo.PaymentWalletVO;

@Tag(name = "用户接口")
@RestController
@RequestMapping("/api/payment")
public class ApiController {

    private final PaymentWalletService paymentWalletService;
    private final PaymentService paymentService;

    public ApiController(PaymentWalletService paymentWalletService, PaymentService paymentService) {
        this.paymentWalletService = paymentWalletService;
        this.paymentService = paymentService;
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

    @Operation(summary = "查询支付订单状态")
    @GetMapping("/status")
    public Result<PaymentStatusVO> getPaymentStatus(@RequestParam("paymentId") String paymentId) {
        try {
            return Result.success(paymentService.getPaymentStatus(paymentId));
        } catch (IllegalArgumentException ex) {
            return Result.fail(400, ex.getMessage());
        } catch (RuntimeException ex) {
            return Result.fail(500, "支付状态获取失败");
        }
    }

}
