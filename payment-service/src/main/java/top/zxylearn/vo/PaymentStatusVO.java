package top.zxylearn.vo;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;

@Data
@NoArgsConstructor
@AllArgsConstructor
@Schema(description = "支付订单状态")
public class PaymentStatusVO {

    @Schema(description = "支付ID，雪花ID使用字符串传输")
    private String paymentId;

    @Schema(description = "支付标题")
    private String subject;

    @Schema(description = "业务类型")
    private String businessType;

    @Schema(description = "业务ID，雪花ID使用字符串传输")
    private String businessId;

    @Schema(description = "支付宝交易号，支付宝渠道返回 ALIPAY 前缀")
    private String tradeNo;

    @Schema(description = "支付金额")
    private BigDecimal amount;

    @Schema(description = "支付渠道")
    private String channel;

    @Schema(description = "支付状态：0等待支付，1支付成功，2支付过期，3支付取消，4支付退款")
    private Integer status;
}
