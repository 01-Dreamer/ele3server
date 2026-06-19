package top.zxylearn.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;

import java.math.BigDecimal;

@Data
@Schema(description = "新增收货地址请求")
public class UserLocationCreateRequest {

    @Schema(description = "收货人姓名", example = "张三")
    private String name;

    @Schema(description = "收货人手机号", example = "13800138000")
    private String phone;

    @Schema(description = "完整收货地址", example = "四川省成都市高新区天府大道")
    private String address;

    @Schema(description = "经度", example = "104.066801")
    private BigDecimal longitude;

    @Schema(description = "纬度", example = "30.572269")
    private BigDecimal latitude;
}
