package top.zxylearn.dto.payment;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.io.Serializable;
import java.math.BigDecimal;

@Data
@NoArgsConstructor
@AllArgsConstructor
@Schema(description = "创建支付订单请求")
public class PaymentCreateRequest implements Serializable {

    @Schema(description = "支付标题", example = "订单支付")
    private String subject;

    @Schema(description = "业务ID，雪花ID使用字符串传输", example = "2066777636767580162")
    private String businessId;

    @Schema(description = "支付金额，最多两位小数", example = "12.50")
    private BigDecimal amount;

    @Schema(description = "支付过期时间，单位分钟，必填", example = "15")
    private Integer expireMinutes;
}
