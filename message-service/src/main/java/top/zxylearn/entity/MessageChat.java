package top.zxylearn.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.time.LocalDateTime;

@Data
@TableName("message_chat")
public class MessageChat {

    @TableId(value = "id", type = IdType.ASSIGN_ID)
    private Long id;

    private Long senderId;

    private Long receiverId;

    private String content;

    private LocalDateTime createTime;
}
