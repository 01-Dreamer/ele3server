package top.zxylearn.dto.shop;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.io.Serializable;
import java.util.List;

@Data
@NoArgsConstructor
@AllArgsConstructor
@Schema(description = "创建账单请求（内部接口）")
public class ShopBillCreateRequest implements Serializable {

    @Schema(description = "店铺ID，雪花ID使用字符串传输")
    private String shopId;

    @Schema(description = "购买商品列表")
    private List<ItemEntry> items;

    @Data
    @NoArgsConstructor
    @AllArgsConstructor
    @Schema(description = "购买商品条目")
    public static class ItemEntry implements Serializable {

        @Schema(description = "商品ID，雪花ID使用字符串传输")
        private String itemId;

        @Schema(description = "购买数量", example = "2")
        private Integer quantity;
    }
}
