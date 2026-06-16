package top.zxylearn.vo;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;

@Data
@NoArgsConstructor
@AllArgsConstructor
@Schema(description = "用户钱包余额")
public class PaymentWalletVO {

    @Schema(description = "余额")
    private BigDecimal balance;
}
