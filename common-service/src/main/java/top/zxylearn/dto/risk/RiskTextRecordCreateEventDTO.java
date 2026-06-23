package top.zxylearn.dto.risk;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.io.Serializable;

@Data
@NoArgsConstructor
@AllArgsConstructor
@Schema(description = "创建文本风控记录事件")
public class RiskTextRecordCreateEventDTO implements Serializable {

    @Schema(description = "来源类型：NICKNAME=昵称，SHOP=店铺，SHOP_ITEM=店铺商品，MESSAGE=聊天消息，REVIEW=店铺评价，REVIEW_REPLY=评价回复，AGENT_CHAT=智能体对话", example = "REVIEW", allowableValues = {"NICKNAME", "SHOP", "SHOP_ITEM", "MESSAGE", "REVIEW", "REVIEW_REPLY", "AGENT_CHAT"})
    private String sourceType;

    @Schema(description = "来源业务ID，雪花ID使用字符串传输", example = "2067907527248560130")
    private String sourceId;

    @Schema(description = "文本发布用户ID，雪花ID使用字符串传输", example = "2066777636767580162")
    private String userId;

    @Schema(description = "违规文本内容")
    private String content;
}
