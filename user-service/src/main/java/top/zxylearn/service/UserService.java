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
        if (request == null || request.getUserId() == null) {
            throw new IllegalArgumentException("用户ID不能为空");
        }
        User user = new User();
        user.setId(request.getUserId());
        user.setNickname(DEFAULT_NICKNAME);
        try {
            userMapper.insert(user);
        } catch (DuplicateKeyException ex) {
            throw new IllegalArgumentException("用户资料已存在");
        }
    }
}
