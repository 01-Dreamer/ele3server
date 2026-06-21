package top.zxylearn.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;

import java.math.BigDecimal;

@Data
@Schema(description = "修改商品请求")
public class ShopItemUpdateRequest {

    @Schema(description = "商品名称，null或空字符串表示不修改")
    private String name;

    @Schema(description = "商品图片URL，null或空字符串表示不修改")
    private String image;

    @Schema(description = "商品描述，null或空字符串表示不修改")
    private String description;

    @Schema(description = "商品价格，null表示不修改")
    private BigDecimal price;

    @Schema(description = "商品状态：0正常，1下架，null表示不修改")
    private Integer status;
}
