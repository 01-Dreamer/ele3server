package top.zxylearn.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.math.BigDecimal;
import java.time.LocalDateTime;

@Data
@TableName("`order`")
public class Order {

    @TableId(value = "id", type = IdType.ASSIGN_ID)
    private Long id;

    private Long userId;

    private Long shopId;

    private String shopName;

    private String receiverName;

    private String receiverPhone;

    private String receiverAddress;

    private BigDecimal receiverLongitude;

    private BigDecimal receiverLatitude;

    private String remark;

    private BigDecimal deliveryFee;

    private BigDecimal amount;

    /**
     * 订单状态：0待支付，1待接单，2待配送，3待评价，4已完成，5已过期，6已取消
     */
    private Integer status;

    private LocalDateTime expireTime;

    private LocalDateTime createTime;

    private LocalDateTime updateTime;
}
