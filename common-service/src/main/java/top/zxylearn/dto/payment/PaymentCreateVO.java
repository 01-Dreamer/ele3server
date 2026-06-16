package top.zxylearn.dto.payment;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.io.Serializable;

@Data
@NoArgsConstructor
@AllArgsConstructor
@Schema(description = "创建支付订单结果")
public class PaymentCreateVO implements Serializable {

    @Schema(description = "支付ID，雪花ID使用字符串传输")
    private String paymentId;

    @Schema(description = "支付二维码内容，前端可渲染为文本二维码")
    private String payUrl;
}
