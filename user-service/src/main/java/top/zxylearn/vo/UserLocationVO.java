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
@Schema(description = "用户收货地址")
public class UserLocationVO {

    @Schema(description = "收货地址ID，雪花ID使用字符串传输")
    private String locationId;

    @Schema(description = "收货人姓名")
    private String name;

    @Schema(description = "收货人手机号")
    private String phone;

    @Schema(description = "完整收货地址")
    private String address;

    @Schema(description = "经度")
    private BigDecimal longitude;

    @Schema(description = "纬度")
    private BigDecimal latitude;

    @Schema(description = "创建时间")
    private LocalDateTime createTime;

    @Schema(description = "更新时间")
    private LocalDateTime updateTime;
}
