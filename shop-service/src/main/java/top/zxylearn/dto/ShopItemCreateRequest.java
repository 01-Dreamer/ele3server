package top.zxylearn.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;

import java.math.BigDecimal;

@Data
@Schema(description = "添加商品请求")
public class ShopItemCreateRequest {

    @Schema(description = "商品名称", example = "招牌牛肉饭")
    private String name;

    @Schema(description = "商品图片URL，可为空")
    private String image;

    @Schema(description = "商品描述，必填")
    private String description;

    @Schema(description = "商品价格", example = "18.50")
    private BigDecimal price;
}
