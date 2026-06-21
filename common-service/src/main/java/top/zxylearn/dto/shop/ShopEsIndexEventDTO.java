package top.zxylearn.dto.shop;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.io.Serializable;

@Data
@NoArgsConstructor
@AllArgsConstructor
@Schema(description = "店铺ES索引更新事件")
public class ShopEsIndexEventDTO implements Serializable {

    public static final String ACTION_UPSERT = "UPSERT";
    public static final String ACTION_DELETE = "DELETE";

    @Schema(description = "事件ID，由生产者生成 UUID")
    private String eventId;

    @Schema(description = "店铺ID，雪花ID使用字符串传输", example = "2067907527248560130")
    private String shopId;

    @Schema(description = "索引动作：UPSERT 更新或新增，DELETE 删除", example = "UPSERT")
    private String action;

    @Schema(description = "事件时间戳，毫秒")
    private Long timestamp;
}
