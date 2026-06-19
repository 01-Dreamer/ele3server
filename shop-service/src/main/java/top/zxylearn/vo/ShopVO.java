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
@Schema(description = "店铺信息")
public class ShopVO {

    @Schema(description = "店铺ID，雪花ID使用字符串传输")
    private String shopId;

    @Schema(description = "店主用户ID，雪花ID使用字符串传输")
    private String userId;

    private String name;

    private String avatar;

    private String description;

    private String address;

    private BigDecimal longitude;

    private BigDecimal latitude;

    private BigDecimal deliveryFee;

    private String openTime;

    private String closeTime;

    private BigDecimal reviewScore;

    private Long reviewCount;

    private Long salesCount;

    private Integer status;

    private LocalDateTime createTime;

    private LocalDateTime updateTime;
}
