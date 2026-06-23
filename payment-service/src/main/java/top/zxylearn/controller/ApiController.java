package top.zxylearn.controller;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import top.zxylearn.dto.WalletRechargeRequest;
import top.zxylearn.dto.WalletWithdrawRequest;
import top.zxylearn.dto.payment.PaymentCreateVO;
import top.zxylearn.result.Result;
import top.zxylearn.service.PaymentService;
import top.zxylearn.service.PaymentWalletService;
import top.zxylearn.vo.PaymentStatusVO;
import top.zxylearn.vo.PaymentWalletVO;
import top.zxylearn.vo.WalletWithdrawVO;

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

    @Operation(summary = "创建钱包充值支付宝支付订单")
    @PostMapping("/alipay-recharge")
    public Result<PaymentCreateVO> createRechargeOrder(@RequestHeader("X-User-Id") String userId,
                                                       @RequestBody WalletRechargeRequest request) {
        try {
            return Result.success(paymentService.createRechargeAlipayOrder(userId, request));
        } catch (IllegalArgumentException ex) {
            return Result.fail(400, ex.getMessage());
        } catch (RuntimeException ex) {
            return Result.fail(500, ex.getMessage() == null ? "充值支付订单创建失败" : ex.getMessage());
        }
    }

    @Operation(summary = "钱包提现到支付宝账户")
    @PostMapping("/alipay-withdraw")
    public Result<WalletWithdrawVO> withdrawToAlipay(@RequestHeader("X-User-Id") String userId,
                                                     @RequestBody WalletWithdrawRequest request) {
        try {
            return Result.success(paymentService.withdrawToAlipay(userId, request));
        } catch (IllegalArgumentException ex) {
            return Result.fail(400, ex.getMessage());
        } catch (RuntimeException ex) {
            return Result.fail(500, ex.getMessage() == null ? "钱包提现失败" : ex.getMessage());
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

    @Operation(summary = "刷新支付宝支付二维码")
    @PostMapping("/refresh-alipay/{paymentId}")
    public Result<PaymentCreateVO> refreshAlipay(@PathVariable String paymentId,
                                                  @RequestParam(value = "expireMinutes", required = false) Integer expireMinutes) {
        try {
            return Result.success(paymentService.refreshAlipayOrder(paymentId, expireMinutes));
        } catch (IllegalArgumentException ex) {
            return Result.fail(400, ex.getMessage());
        } catch (RuntimeException ex) {
            return Result.fail(500, ex.getMessage() == null ? "支付二维码刷新失败" : ex.getMessage());
        }
    }

}
