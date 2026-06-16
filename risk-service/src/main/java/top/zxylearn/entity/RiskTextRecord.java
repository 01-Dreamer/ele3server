package top.zxylearn.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.time.LocalDateTime;

@Data
@TableName("risk_text_record")
public class RiskTextRecord {

    @TableId(value = "id", type = IdType.ASSIGN_ID)
    private Long id;

    private String sourceType;

    private Long sourceId;

    private Long userId;

    private String content;

    /**
     * 处理状态：0待处理，1已处理
     */
    private Integer status;

    private LocalDateTime createTime;

    private LocalDateTime updateTime;
}
