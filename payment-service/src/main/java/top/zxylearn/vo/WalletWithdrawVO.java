package top.zxylearn.vo;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;

@Data
@NoArgsConstructor
@AllArgsConstructor
@Schema(description = "钱包提现结果")
public class WalletWithdrawVO {

    @Schema(description = "提现流水号，雪花ID使用字符串传输")
    private String withdrawId;

    @Schema(description = "支付宝订单号")
    private String alipayOrderId;

    @Schema(description = "支付宝支付资金流水号")
    private String payFundOrderId;

    @Schema(description = "提现金额")
    private BigDecimal amount;

    @Schema(description = "支付宝转账状态")
    private String status;
}
