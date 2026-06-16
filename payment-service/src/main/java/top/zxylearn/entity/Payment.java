package top.zxylearn.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.math.BigDecimal;
import java.time.LocalDateTime;

@Data
@TableName("payment")
public class Payment {

    @TableId(value = "id", type = IdType.ASSIGN_ID)
    private Long id;

    private String subject;

    private String remark;

    private String businessType;

    private Long businessId;

    private BigDecimal amount;

    private String channel;

    /**
     * 支付状态：0待支付，1支付成功，2支付失败，3已退款
     */
    private Integer status;

    private String tradeNo;

    private LocalDateTime createTime;

    private LocalDateTime updateTime;
}
