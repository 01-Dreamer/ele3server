package top.zxylearn.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.time.LocalDateTime;

@Data
@TableName("shop_review_reply")
public class ShopReviewReply {

    @TableId(value = "id", type = IdType.ASSIGN_ID)
    private Long id;

    private Long reviewId;

    private Long userId;

    private Integer userType;

    private Long atUserId;

    private String content;

    private LocalDateTime createTime;

    private LocalDateTime updateTime;
}
