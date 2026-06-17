package top.zxylearn.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;

@Data
@NoArgsConstructor
@AllArgsConstructor
@Schema(description = "钱包充值请求")
public class WalletRechargeRequest {

    @Schema(description = "充值金额，最多两位小数", example = "20.00")
    private BigDecimal amount;
}
