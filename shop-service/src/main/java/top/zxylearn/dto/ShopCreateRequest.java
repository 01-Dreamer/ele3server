package top.zxylearn.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;

import java.math.BigDecimal;

@Data
@Schema(description = "创建店铺请求")
public class ShopCreateRequest {

    @Schema(description = "店铺名称", example = "川味小馆")
    private String name;

    @Schema(description = "店铺头像URL")
    private String avatar;

    @Schema(description = "店铺描述")
    private String description;

    @Schema(description = "店铺地址")
    private String address;

    @Schema(description = "经度", example = "104.066801")
    private BigDecimal longitude;

    @Schema(description = "纬度", example = "30.572269")
    private BigDecimal latitude;

    @Schema(description = "配送费", example = "3.00")
    private BigDecimal deliveryFee;

    @Schema(description = "开始营业时间，HH:mm", example = "09:00")
    private String openTime;

    @Schema(description = "结束营业时间，HH:mm", example = "22:00")
    private String closeTime;
}
