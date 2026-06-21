package top.zxylearn.vo;

import lombok.AllArgsConstructor;
import lombok.Data;

import java.time.LocalDateTime;

@Data
@AllArgsConstructor
public class MessageSessionVO {

    private String id;
    private String smallerUserId;
    private String largerUserId;
    private String lastMessageId;
    private String lastMessageContent;
    private LocalDateTime lastMessageTime;
    private Long smallerUserUnreadCount;
    private Long largerUserUnreadCount;
    private Integer smallerUserShow;
    private Integer largerUserShow;
    private LocalDateTime createTime;
    private LocalDateTime updateTime;
}
