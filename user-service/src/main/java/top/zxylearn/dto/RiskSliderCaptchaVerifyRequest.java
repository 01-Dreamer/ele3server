package top.zxylearn.dto;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.Map;

@Data
@NoArgsConstructor
@AllArgsConstructor
public class RiskSliderCaptchaVerifyRequest {

    private String id;

    private Map<String, Object> data;
}
