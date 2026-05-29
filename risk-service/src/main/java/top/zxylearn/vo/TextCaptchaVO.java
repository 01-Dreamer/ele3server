package top.zxylearn.vo;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@NoArgsConstructor
@AllArgsConstructor
@Schema(description = "图片验证码")
public class TextCaptchaVO {

    @Schema(description = "验证码 ID")
    private String id;

    @Schema(description = "验证码类型")
    private String type;

    @Schema(description = "Base64 图片")
    private String image;

    @Schema(description = "图片宽度")
    private Integer width;

    @Schema(description = "图片高度")
    private Integer height;
}
