package top.zxylearn.controller;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.servlet.http.HttpServletRequest;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.Map;
import java.util.stream.Collectors;

@Tag(name = "公共接口")
@RestController
@RequestMapping("/api/payment/public")
public class PublicController {

    private static final Logger log = LoggerFactory.getLogger(PublicController.class);

    @Operation(summary = "支付宝支付回调")
    @PostMapping("/alipay/notify")
    public String alipayNotify(HttpServletRequest request) {
        Map<String, String> params = request.getParameterMap().entrySet().stream()
                .collect(Collectors.toMap(
                        Map.Entry::getKey,
                        entry -> String.join(",", entry.getValue() == null ? new String[0] : entry.getValue())
                ));
        log.info("收到支付宝支付回调: {}", params);
        System.out.println("收到支付宝支付回调: " + params);
        return "success";
    }
}
