package top.zxylearn.service;

import org.springframework.dao.DuplicateKeyException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import top.zxylearn.dto.user.UserCreateRequest;
import top.zxylearn.entity.User;
import top.zxylearn.mapper.UserMapper;

@Service
public class UserService {

    private static final String DEFAULT_NICKNAME = "饿了么用户";

    private final UserMapper userMapper;

    public UserService(UserMapper userMapper) {
        this.userMapper = userMapper;
    }

    @Transactional(rollbackFor = Exception.class)
    public void createUser(UserCreateRequest request) {
        if (request == null) {
            throw new IllegalArgumentException("用户ID不能为空");
        }
        User user = new User();
        user.setId(parseUserId(request.getUserId()));
        user.setNickname(DEFAULT_NICKNAME);
        try {
            userMapper.insert(user);
        } catch (DuplicateKeyException ex) {
            throw new IllegalArgumentException("用户资料已存在");
        }
    }

    private Long parseUserId(String userId) {
        if (userId == null || userId.isBlank()) {
            throw new IllegalArgumentException("用户ID不能为空");
        }
        try {
            return Long.valueOf(userId.trim());
        } catch (NumberFormatException ex) {
            throw new IllegalArgumentException("用户ID格式不正确");
        }
    }
}
