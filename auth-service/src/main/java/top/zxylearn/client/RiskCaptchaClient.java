package top.zxylearn.client;

import org.springframework.cloud.openfeign.FeignClient;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import top.zxylearn.dto.RiskCaptchaVerifyRequest;
import top.zxylearn.result.Result;

@FeignClient(name = "risk-service")
public interface RiskCaptchaClient {

    @PostMapping("/internal/risk/captcha/verify")
    Result<?> verifyCaptcha(@RequestBody RiskCaptchaVerifyRequest request);
}
