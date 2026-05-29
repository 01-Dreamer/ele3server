package top.zxylearn.dto;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.Map;

@Data
@NoArgsConstructor
@AllArgsConstructor
public class RiskCaptchaVerifyRequest {

    private String id;

    private String type;

    private String code;

    private Map<String, Object> data;
}
