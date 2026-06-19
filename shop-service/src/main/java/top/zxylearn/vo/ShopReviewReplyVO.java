package top.zxylearn.vo;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;

@Data
@NoArgsConstructor
@AllArgsConstructor
public class ShopReviewReplyVO {

    private String replyId;

    private String reviewId;

    private String userId;

    private String atUserId;

    private String content;

    private LocalDateTime createTime;
}
