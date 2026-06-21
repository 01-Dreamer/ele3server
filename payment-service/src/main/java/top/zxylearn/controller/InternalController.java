package top.zxylearn.controller;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import top.zxylearn.dto.payment.PaymentCloseRequest;
import top.zxylearn.dto.payment.PaymentCreateRequest;
import top.zxylearn.dto.payment.PaymentCreateVO;
import top.zxylearn.dto.payment.PaymentOrderRefundRequest;
import top.zxylearn.dto.payment.PaymentRefundRequest;
import top.zxylearn.dto.payment.PaymentWalletAddRequest;
import top.zxylearn.dto.payment.PaymentWalletCreateRequest;
import top.zxylearn.dto.payment.PaymentWalletDeductRequest;
import top.zxylearn.result.Result;
import top.zxylearn.service.PaymentService;
import top.zxylearn.service.PaymentWalletService;

@Tag(name = "内部接口")
@RestController
@RequestMapping("/internal/payment")
public class InternalController {

    private final PaymentWalletService paymentWalletService;
    private final PaymentService paymentService;

    public InternalController(PaymentWalletService paymentWalletService, PaymentService paymentService) {
        this.paymentWalletService = paymentWalletService;
        this.paymentService = paymentService;
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


    @Operation(summary = "增加用户钱包余额")
    @PostMapping("/add-balance")
    public Result<?> addBalance(@RequestBody PaymentWalletAddRequest request) {
        try {
            paymentWalletService.addBalance(request);
            return Result.success();
        } catch (IllegalArgumentException ex) {
            return Result.fail(400, ex.getMessage());
        } catch (RuntimeException ex) {
            return Result.fail(500, "余额增加失败");
        }
    }

    @Operation(summary = "创建支付宝支付订单")
    @PostMapping("/create-alipay-order")
    public Result<PaymentCreateVO> createAlipayOrder(@RequestBody PaymentCreateRequest request) {
        try {
            return Result.success(paymentService.createAlipayOrder(request));
        } catch (IllegalArgumentException ex) {
            return Result.fail(400, ex.getMessage());
        } catch (RuntimeException ex) {
            return Result.fail(500, ex.getMessage() == null ? "支付订单创建失败" : ex.getMessage());
        }
    }

    @Operation(summary = "关闭支付宝支付订单")
    @PostMapping("/close-alipay-order")
    public Result<?> closeAlipayOrder(@RequestBody PaymentCloseRequest request) {
        try {
            paymentService.closeAlipayOrder(request);
            return Result.success();
        } catch (IllegalArgumentException ex) {
            return Result.fail(400, ex.getMessage());
        } catch (RuntimeException ex) {
            return Result.fail(500, ex.getMessage() == null ? "支付订单关闭失败" : ex.getMessage());
        }
    }


    @Operation(summary = "按订单退款支付宝支付")
    @PostMapping("/refund-alipay-order-by-order")
    public Result<?> refundAlipayOrderByOrder(@RequestBody PaymentOrderRefundRequest request) {
        try {
            paymentService.refundAlipayOrderByOrderId(request);
            return Result.success();
        } catch (IllegalArgumentException ex) {
            return Result.fail(400, ex.getMessage());
        } catch (RuntimeException ex) {
            return Result.fail(500, ex.getMessage() == null ? "支付订单退款失败" : ex.getMessage());
        }
    }

    @Operation(summary = "支付宝支付订单退款")
    @PostMapping("/refund-alipay-order")
    public Result<?> refundAlipayOrder(@RequestBody PaymentRefundRequest request) {
        try {
            paymentService.refundAlipayOrder(request);
            return Result.success();
        } catch (IllegalArgumentException ex) {
            return Result.fail(400, ex.getMessage());
        } catch (RuntimeException ex) {
            return Result.fail(500, ex.getMessage() == null ? "支付订单退款失败" : ex.getMessage());
        }
    }

}
