package top.zxylearn.client;

import org.springframework.cloud.openfeign.FeignClient;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import top.zxylearn.dto.RiskSliderCaptchaVerifyRequest;
import top.zxylearn.result.Result;

@FeignClient(name = "risk-service")
public interface RiskCaptchaClient {

    @PostMapping("/internal/risk/captcha/slider/verify")
    Result<?> verifySliderCaptcha(@RequestBody RiskSliderCaptchaVerifyRequest request);
}
