package top.zxylearn.dto.payment;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.io.Serializable;

@Data
@NoArgsConstructor
@AllArgsConstructor
@Schema(description = "按订单退款支付宝支付请求")
public class PaymentOrderRefundRequest implements Serializable {

    @Schema(description = "订单ID，雪花ID使用字符串传输", example = "2067107601425895425")
    private String orderId;
}
