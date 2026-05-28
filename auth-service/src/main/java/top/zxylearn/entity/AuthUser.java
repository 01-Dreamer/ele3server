package top.zxylearn.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.time.LocalDateTime;

@Data
@TableName("auth_user")
public class AuthUser {

    @TableId(value = "id", type = IdType.ASSIGN_ID)
    private Long id;

    private String email;

    private String passwordHash;

    private String nickname;

    private String avatarUrl;

    private Integer gender;

    private Integer status;

    private LocalDateTime createTime;

    private LocalDateTime updateTime;
}
