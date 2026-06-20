package top.zxylearn.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.time.LocalDateTime;

@Data
@TableName("message_notice")
public class MessageNotice {

    @TableId(value = "id", type = IdType.ASSIGN_ID)
    private Long id;

    private Long userId;

    private String title;

    private String content;

    /**
     * 是否已读：0未读，1已读
     */
    private Integer isRead;

    private LocalDateTime createTime;

    private LocalDateTime updateTime;
}
