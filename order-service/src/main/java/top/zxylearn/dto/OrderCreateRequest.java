package top.zxylearn.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;

import java.math.BigDecimal;
import java.util.List;

@Data
@Schema(description = "创建订单请求")
public class OrderCreateRequest {
    @Schema(description = "店铺ID，雪花ID使用字符串传输")
    private String shopId;
    @Schema(description = "收货人姓名")
    private String receiverName;
    @Schema(description = "收货人手机号")
    private String receiverPhone;
    @Schema(description = "收货地址")
    private String receiverAddress;
    @Schema(description = "收货地址经度")
    private BigDecimal receiverLongitude;
    @Schema(description = "收货地址纬度")
    private BigDecimal receiverLatitude;
    @Schema(description = "防重token，先调用 /create-order-token 获取")
    private String token;
    @Schema(description = "用户备注")
    private String remark;
    @Schema(description = "订单商品列表")
    private List<ItemEntry> items;

    @Data
    public static class ItemEntry {
        @Schema(description = "店铺商品ID，雪花ID使用字符串传输")
        private String shopItemId;
        @Schema(description = "购买数量", example = "1")
        private Integer quantity;
    }
}
