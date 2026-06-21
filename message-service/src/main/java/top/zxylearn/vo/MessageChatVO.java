package top.zxylearn.vo;

import lombok.AllArgsConstructor;
import lombok.Data;

import java.time.LocalDateTime;

@Data
@AllArgsConstructor
public class MessageChatVO {

    private String id;
    private String senderId;
    private String receiverId;
    private String content;
    private LocalDateTime createTime;
}
