package top.zxylearn.dto.payment;

import lombok.AllArgsConstructor;
import lombok.Data;
import io.swagger.v3.oas.annotations.media.Schema;
import lombok.NoArgsConstructor;

import java.io.Serializable;
import java.math.BigDecimal;

@Data
@NoArgsConstructor
@AllArgsConstructor
@Schema(description = "用户钱包扣款请求")
public class PaymentWalletDeductRequest implements Serializable {

    @Schema(description = "用户ID，雪花ID使用字符串传输", example = "2066777636767580162")
    private String userId;

    @Schema(description = "扣款金额，最多两位小数", example = "0.99")
    private BigDecimal amount;
}
