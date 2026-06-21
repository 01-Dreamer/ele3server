package top.zxylearn.vo;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;

@Data
@NoArgsConstructor
@AllArgsConstructor
public class ThirdAccountVO {

    private String id;
    private String userId;
    private String provider;
    private String openId;
    private LocalDateTime createTime;
}
