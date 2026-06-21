package top.zxylearn.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.math.BigDecimal;
import java.time.LocalDateTime;

@Data
@TableName("shop_item")
public class ShopItem {

    @TableId(value = "id", type = IdType.ASSIGN_ID)
    private Long id;

    private Long shopId;

    private String name;

    private String image;

    private String description;

    private BigDecimal price;

    /**
     * 排序值，越小越靠前，创建时默认使用雪花ID
     */
    private Long sort;

    /**
     * 商品状态：0正常，1下架
     */
    private Integer status;

    private LocalDateTime createTime;

    private LocalDateTime updateTime;
}
