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
@Schema(description = "创建店铺评价请求")
public class ShopReviewCreateRequest implements Serializable {

    @Schema(description = "订单ID，雪花ID使用字符串传输")
    private String orderId;

    @Schema(description = "评价用户ID，雪花ID使用字符串传输")
    private String userId;

    @Schema(description = "店铺ID，雪花ID使用字符串传输")
    private String shopId;

    @Schema(description = "评分，0到5", example = "4.5")
    private BigDecimal score;

    @Schema(description = "评价内容，必填")
    private String content;

    @Schema(description = "评价图片URL列表，可为空，最多5张")
    private List<String> images;
}
