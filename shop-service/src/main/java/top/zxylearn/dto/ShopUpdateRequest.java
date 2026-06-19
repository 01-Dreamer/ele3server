package top.zxylearn.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;

import java.math.BigDecimal;

@Data
@Schema(description = "修改店铺请求")
public class ShopUpdateRequest {

    @Schema(description = "店铺名称，null或空字符串表示不修改")
    private String name;

    @Schema(description = "店铺头像URL，null或空字符串表示不修改")
    private String avatar;

    @Schema(description = "店铺描述，null或空字符串表示不修改")
    private String description;

    @Schema(description = "店铺地址，null或空字符串表示不修改")
    private String address;

    @Schema(description = "经度，null表示不修改")
    private BigDecimal longitude;

    @Schema(description = "纬度，null表示不修改")
    private BigDecimal latitude;

    @Schema(description = "配送费，null表示不修改")
    private BigDecimal deliveryFee;

    @Schema(description = "开始营业时间，HH:mm，null或空字符串表示不修改")
    private String openTime;

    @Schema(description = "结束营业时间，HH:mm，null或空字符串表示不修改")
    private String closeTime;
}
