package top.zxylearn.dto.risk;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;

import java.io.Serializable;

@Data
@Schema(description = "用户风险分增加事件")
public class RiskScoreIncreaseEventDTO implements Serializable {

    @Schema(description = "用户ID，雪花ID使用字符串传输", example = "2066777636767580162")
    private String userId;

    @Schema(description = "增加的风险分，会转换为风险 TTL 秒数", example = "200")
    private Long riskScore;

    @Schema(description = "风险来源，可为空，例如 HTTP、TEXT、PAYMENT", example = "TEXT")
    private String source;

    @Schema(description = "事件时间戳，毫秒")
    private Long timestamp;
}
