package top.zxylearn.dto.order;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.io.Serializable;

@Data
@NoArgsConstructor
@AllArgsConstructor
@Schema(description = "订单支付成功请求")
public class OrderPaidRequest implements Serializable {

    @Schema(description = "订单ID，雪花ID使用字符串传输", example = "2066777636767580162")
    private String orderId;
}
