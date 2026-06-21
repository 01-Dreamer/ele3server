package top.zxylearn.dto.shop;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.io.Serializable;
import java.math.BigDecimal;
import java.util.List;

@Data
@NoArgsConstructor
@AllArgsConstructor
@Schema(description = "账单信息（内部接口返回）")
public class ShopBillVO implements Serializable {

    @Schema(description = "店铺ID，雪花ID使用字符串传输")
    private String shopId;

    @Schema(description = "店铺名称")
    private String shopName;

    @Schema(description = "店主用户ID，雪花ID使用字符串传输")
    private String shopOwnerId;

    @Schema(description = "配送费")
    private BigDecimal deliveryFee;

    @Schema(description = "商品明细")
    private List<ItemEntry> items;

    @Schema(description = "商品总价（各项小计之和）")
    private BigDecimal itemsTotal;

    @Schema(description = "账单总金额（商品总价 + 配送费）")
    private BigDecimal totalAmount;

    @Data
    @NoArgsConstructor
    @AllArgsConstructor
    @Schema(description = "账单商品条目")
    public static class ItemEntry implements Serializable {

        @Schema(description = "商品ID")
        private String itemId;

        @Schema(description = "商品名称")
        private String itemName;

        @Schema(description = "商品单价")
        private BigDecimal unitPrice;

        @Schema(description = "购买数量")
        private Integer quantity;

        @Schema(description = "小计（单价 × 数量）")
        private BigDecimal subtotal;
    }
}
