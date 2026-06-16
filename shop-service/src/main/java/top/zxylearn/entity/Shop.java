package top.zxylearn.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.time.LocalTime;

@Data
@TableName("shop")
public class Shop {

    @TableId(value = "id", type = IdType.ASSIGN_ID)
    private Long id;

    private String name;

    private String avatar;

    private String description;

    private String address;

    private BigDecimal longitude;

    private BigDecimal latitude;

    private BigDecimal deliveryFee;

    private LocalTime openTime;

    private LocalTime closeTime;

    private BigDecimal reviewScore;

    private Long reviewCount;

    /**
     * 店铺状态：0正常，1封禁
     */
    private Integer status;

    private LocalDateTime createTime;

    private LocalDateTime updateTime;
}
