package top.zxylearn.dto;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@NoArgsConstructor
@AllArgsConstructor
public class LoginUser {

    private String userId;

    private String email;

    private String nickname;

    private String avatarUrl;

    private Integer gender;

    private Integer status;
}
