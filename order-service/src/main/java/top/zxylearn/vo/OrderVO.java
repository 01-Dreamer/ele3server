package top.zxylearn.vo;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;

@Data
@NoArgsConstructor
@AllArgsConstructor
@Schema(description = "订单信息")
public class OrderVO {
    private String orderId;
    private String userId;
    private String shopId;
    private String shopOwnerId;
    private String riderId;
    private String shopName;
    private String receiverName;
    private String receiverPhone;
    private String receiverAddress;
    private BigDecimal receiverLongitude;
    private BigDecimal receiverLatitude;
    private String remark;
    private BigDecimal deliveryFee;
    private BigDecimal amount;
    private Integer status;
    private LocalDateTime expireTime;
    private LocalDateTime createTime;
    private LocalDateTime updateTime;
    private List<OrderItemVO> items;
}
