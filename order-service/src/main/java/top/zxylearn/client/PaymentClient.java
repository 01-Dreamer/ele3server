package top.zxylearn.client;

import org.springframework.cloud.openfeign.FeignClient;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestParam;
import top.zxylearn.dto.payment.PaymentCreateRequest;
import top.zxylearn.dto.payment.PaymentCreateVO;
import top.zxylearn.dto.payment.PaymentOrderRefundRequest;
import top.zxylearn.dto.payment.PaymentWalletAddRequest;
import top.zxylearn.dto.payment.PaymentWalletDeductRequest;
import top.zxylearn.result.Result;

@FeignClient(name = "payment-service")
public interface PaymentClient {
    @PostMapping("/internal/payment/create-alipay-order")
    Result<PaymentCreateVO> createAlipayOrder(@RequestBody PaymentCreateRequest request);
    @PostMapping("/internal/payment/close-alipay-order-by-order")
    Result<?> closeAlipayOrderByOrderId(@RequestParam("orderId") String orderId);
    @PostMapping("/internal/payment/deduct-balance")
    Result<?> deductBalance(@RequestBody PaymentWalletDeductRequest request);
    @PostMapping("/internal/payment/add-balance")
    Result<?> addBalance(@RequestBody PaymentWalletAddRequest request);
    @PostMapping("/internal/payment/refund-alipay-order-by-order")
    Result<?> refundAlipayOrderByOrder(@RequestBody PaymentOrderRefundRequest request);
}
