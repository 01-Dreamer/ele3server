package top.zxylearn.vo;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;

@Data
@NoArgsConstructor
@AllArgsConstructor
public class RiskTextRecordVO {
    private String id;
    private String sourceType;
    private String sourceId;
    private String userId;
    private String content;
    private Integer status;
    private String handleOpinion;
    private LocalDateTime handleTime;
    private LocalDateTime createTime;
    private LocalDateTime updateTime;
}
