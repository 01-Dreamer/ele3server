package top.zxylearn.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;

@Data
@Schema(description = "调换两个商品顺序请求")
public class ShopItemSwapRequest {

    @Schema(description = "商品A的ID")
    private String itemIdA;

    @Schema(description = "商品B的ID")
    private String itemIdB;
}
