package top.zxylearn.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.math.BigDecimal;
import java.time.LocalDateTime;

@Data
@TableName("user_location")
public class UserLocation {

    @TableId(value = "id", type = IdType.ASSIGN_ID)
    private Long id;

    private Long userId;

    private String name;

    private String phone;

    private String address;

    private BigDecimal longitude;

    private BigDecimal latitude;

    private LocalDateTime createTime;

    private LocalDateTime updateTime;
}
