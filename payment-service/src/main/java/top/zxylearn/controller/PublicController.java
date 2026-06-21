package top.zxylearn.controller;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.servlet.http.HttpServletRequest;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import top.zxylearn.service.PaymentService;

import java.util.Map;
import java.util.stream.Collectors;

@Tag(name = "公共接口")
@RestController
@RequestMapping("/api/payment/public")
public class PublicController {

    private static final Logger log = LoggerFactory.getLogger(PublicController.class);

    private final PaymentService paymentService;

    public PublicController(PaymentService paymentService) {
        this.paymentService = paymentService;
    }

    @Operation(summary = "支付宝支付回调")
    @PostMapping("/alipay/notify")
    public String alipayNotify(HttpServletRequest request) {
        Map<String, String> params = request.getParameterMap().entrySet().stream()
                .collect(Collectors.toMap(
                        Map.Entry::getKey,
                        entry -> String.join(",", entry.getValue() == null ? new String[0] : entry.getValue())
                ));
        log.info("收到支付宝支付回调: {}", params);
        try {
            paymentService.handleAlipayNotify(params);
            return "success";
        } catch (RuntimeException ex) {
            log.error("支付宝支付回调处理失败: {}", params, ex);
            return "fail";
        }
    }
}
