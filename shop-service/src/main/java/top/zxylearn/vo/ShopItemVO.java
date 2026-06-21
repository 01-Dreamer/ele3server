package top.zxylearn.vo;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;
import java.time.LocalDateTime;

@Data
@NoArgsConstructor
@AllArgsConstructor
@Schema(description = "店铺商品")
public class ShopItemVO {

    @Schema(description = "商品ID，雪花ID使用字符串传输")
    private String itemId;

    @Schema(description = "店铺ID，雪花ID使用字符串传输")
    private String shopId;

    private String name;

    private String image;

    private String description;

    private BigDecimal price;

    private Long sort;

    private Integer status;

    private LocalDateTime createTime;

    private LocalDateTime updateTime;
}
