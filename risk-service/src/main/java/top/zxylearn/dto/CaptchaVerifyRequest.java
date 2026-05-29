package top.zxylearn.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;

import java.util.Map;

@Data
@Schema(description = "验证码校验请求")
public class CaptchaVerifyRequest {

    @Schema(description = "验证码 ID")
    private String id;

    @Schema(description = "验证码类型：SLIDER 滑块验证码，IMAGE 图片验证码", example = "IMAGE")
    private String type;

    @Schema(description = "图片验证码答案，图片验证码必填", example = "A7K9Q2")
    private String code;

    @Schema(description = "验证码扩展数据，滑块验证码传前端滑动轨迹")
    private Map<String, Object> data;
}
