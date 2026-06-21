package top.zxylearn.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;

@Data
@NoArgsConstructor
@AllArgsConstructor
@Schema(description = "钱包提现请求")
public class WalletWithdrawRequest {

    @Schema(description = "支付宝用户UID", example = "2088722101573040")
    private String alipayUserId;

    @Schema(description = "提现金额，最多两位小数", example = "10.00")
    private BigDecimal amount;
}
