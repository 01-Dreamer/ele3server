package top.zxylearn.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;

@Data
@Schema(description = "修改店铺状态请求")
public class ShopStatusUpdateRequest {

    @Schema(description = "店铺状态：0正常，1封禁", example = "1")
    private Integer status;
}
