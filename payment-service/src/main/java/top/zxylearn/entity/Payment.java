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

    private String businessType;

    private Long businessId;

    private BigDecimal amount;

    private String channel;

    /**
     * 支付状态：0等待支付，1支付成功，2支付过期，3支付取消，4支付退款
     */
    private Integer status;

    private String tradeNo;

    private String payUrl;

    private LocalDateTime expireTime;

    private LocalDateTime createTime;

    private LocalDateTime updateTime;
}
