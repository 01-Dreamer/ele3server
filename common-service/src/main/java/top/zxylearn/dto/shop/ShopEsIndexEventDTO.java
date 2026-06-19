package top.zxylearn.dto.shop;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.io.Serializable;

@Data
@NoArgsConstructor
@AllArgsConstructor
public class ShopEsIndexEventDTO implements Serializable {

    public static final String ACTION_UPSERT = "UPSERT";
    public static final String ACTION_DELETE = "DELETE";

    /**
     * 事件ID，由生产者生成 UUID
     */
    private String eventId;

    /**
     * 店铺ID，雪花ID使用字符串传输
     */
    private String shopId;

    /**
     * 同步动作：UPSERT更新或新增，DELETE删除
     */
    private String action;

    /**
     * 事件时间戳
     */
    private Long timestamp;
}
