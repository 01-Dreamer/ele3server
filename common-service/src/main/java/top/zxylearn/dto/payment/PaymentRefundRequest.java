package top.zxylearn.dto.payment;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.io.Serializable;

@Data
@NoArgsConstructor
@AllArgsConstructor
@Schema(description = "支付宝支付订单退款请求")
public class PaymentRefundRequest implements Serializable {

    @Schema(description = "支付ID，雪花ID使用字符串传输", example = "2067107601425895425")
    private String paymentId;
}
