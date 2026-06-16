package top.zxylearn.dto.payment;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.io.Serializable;
import java.math.BigDecimal;

@Data
@NoArgsConstructor
@AllArgsConstructor
public class PaymentWalletDeductRequest implements Serializable {

    private Long userId;

    private BigDecimal amount;
}
