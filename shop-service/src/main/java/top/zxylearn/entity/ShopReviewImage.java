package top.zxylearn.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.time.LocalDateTime;

@Data
@TableName("shop_review_image")
public class ShopReviewImage {

    @TableId(value = "id", type = IdType.ASSIGN_ID)
    private Long id;

    private Long reviewId;

    private String image;

    private Integer sort;

    private LocalDateTime createTime;
}
