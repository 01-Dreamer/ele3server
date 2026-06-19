package top.zxylearn.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;

@Data
@Schema(description = "回复店铺评价请求")
public class ShopReviewReplyRequest {

    @Schema(description = "评价ID", example = "2067107601425895425")
    private String reviewId;

    @Schema(description = "被@用户ID，可为空", example = "2066777636767580162")
    private String atUserId;

    @Schema(description = "回复内容")
    private String content;
}
