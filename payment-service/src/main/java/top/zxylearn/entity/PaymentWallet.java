package top.zxylearn.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.math.BigDecimal;
import java.time.LocalDateTime;

@Data
@TableName("payment_wallet")
public class PaymentWallet {

    @TableId(value = "user_id", type = IdType.INPUT)
    private Long userId;

    private BigDecimal balance;

    private LocalDateTime createTime;

    private LocalDateTime updateTime;
}
