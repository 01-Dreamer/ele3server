package top.zxylearn.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;

import java.math.BigDecimal;
import java.util.List;

@Data
@Schema(description = "订单评价请求")
public class OrderReviewRequest {
    @Schema(description = "评分，0到5", example = "4.5")
    private BigDecimal score;
    @Schema(description = "评价内容")
    private String content;
    @Schema(description = "评价图片URL列表，可为空，最多5张")
    private List<String> images;
}
