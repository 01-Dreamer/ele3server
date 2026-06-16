package top.zxylearn.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;

import java.math.BigDecimal;

@Data
@Schema(description = "经纬度上传请求")
public class CoordinateUploadRequest {

    @Schema(description = "经度，范围 -180 到 180", example = "104.066801")
    private BigDecimal longitude;

    @Schema(description = "纬度，范围 -90 到 90", example = "30.572269")
    private BigDecimal latitude;
}
