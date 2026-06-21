package top.zxylearn.vo;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;

@Data
@NoArgsConstructor
@AllArgsConstructor
@Schema(description = "订单商品快照")
public class OrderItemVO {
    private String itemId;
    private String name;
    private BigDecimal price;
    private Integer quantity;
    private BigDecimal amount;
}
