package top.zxylearn.vo;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;

@Data
@NoArgsConstructor
@AllArgsConstructor
public class ShopReviewVO {

    private String reviewId;

    private String orderId;

    private String shopId;

    private String userId;

    private BigDecimal score;

    private String content;

    private List<String> images;

    private LocalDateTime createTime;
}
