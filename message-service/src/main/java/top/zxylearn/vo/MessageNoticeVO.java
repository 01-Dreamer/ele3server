package top.zxylearn.vo;

import lombok.AllArgsConstructor;
import lombok.Data;

import java.time.LocalDateTime;

@Data
@AllArgsConstructor
public class MessageNoticeVO {

    private String id;
    private String userId;
    private String title;
    private String content;
    private Integer isRead;
    private LocalDateTime createTime;
    private LocalDateTime updateTime;
}
