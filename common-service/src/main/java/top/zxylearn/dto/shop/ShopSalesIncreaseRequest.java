package top.zxylearn.dto.shop;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.io.Serializable;

@Data
@NoArgsConstructor
@AllArgsConstructor
@Schema(description = "增加店铺销量请求")
public class ShopSalesIncreaseRequest implements Serializable {

    @Schema(description = "店铺ID，雪花ID使用字符串传输")
    private String shopId;

    @Schema(description = "销量增量，必须大于0", example = "1")
    private Long salesDelta;
}
