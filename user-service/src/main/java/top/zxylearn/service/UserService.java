package top.zxylearn.service;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;
import top.zxylearn.config.UserLoginProperties;
import top.zxylearn.dto.LoginUser;
import top.zxylearn.dto.UserUpdateRequest;
import top.zxylearn.entity.EleUser;
import top.zxylearn.mapper.EleUserMapper;

@Service
public class UserService {

    private static final Integer BANNED_STATUS = 2;
    private static final int MAX_NICKNAME_LENGTH = 50;
    private static final int MAX_AVATAR_URL_LENGTH = 500;
    private static final String TOKEN_KEY_PREFIX = "user:login:token:";
    private static final String INFO_KEY_PREFIX = "user:login:info:";

    private final EleUserMapper eleUserMapper;
    private final StringRedisTemplate redisTemplate;
    private final UserLoginProperties userLoginProperties;
    private final ObjectMapper objectMapper = new ObjectMapper();

    public UserService(EleUserMapper eleUserMapper,
                       StringRedisTemplate redisTemplate,
                       UserLoginProperties userLoginProperties) {
        this.eleUserMapper = eleUserMapper;
        this.redisTemplate = redisTemplate;
        this.userLoginProperties = userLoginProperties;
    }

    public LoginUser updateProfile(String authorization, UserUpdateRequest request) {
        String userId = getUserIdFromToken(authorization);
        EleUser user = eleUserMapper.selectById(userId);
        if (user == null) {
            throw new IllegalArgumentException("用户不存在");
        }
        if (BANNED_STATUS.equals(user.getStatus())) {
            throw new IllegalArgumentException("账号已被封禁");
        }

        boolean changed = applyProfileChanges(user, request);
        if (changed) {
            int rows = eleUserMapper.updateById(user);
            if (rows != 1) {
                throw new IllegalStateException("用户资料修改失败");
            }
        }

        LoginUser loginUser = toLoginUser(user);
        refreshLoginUserCache(loginUser);
        return loginUser;
    }

    private String getUserIdFromToken(String authorization) {
        if (!StringUtils.hasText(authorization)) {
            throw new IllegalArgumentException("登录令牌不能为空");
        }
        String token = authorization.trim();
        if (token.regionMatches(true, 0, "Bearer ", 0, 7)) {
            token = token.substring(7).trim();
        }
        if (!StringUtils.hasText(token)) {
            throw new IllegalArgumentException("登录令牌不能为空");
        }

        String userId = redisTemplate.opsForValue().get(TOKEN_KEY_PREFIX + token);
        if (!StringUtils.hasText(userId)) {
            throw new IllegalArgumentException("登录状态已失效，请重新登录");
        }
        return userId;
    }

    private boolean applyProfileChanges(EleUser user, UserUpdateRequest request) {
        if (request == null) {
            throw new IllegalArgumentException("请求参数不能为空");
        }

        boolean changed = false;
        if (StringUtils.hasText(request.getNickname())) {
            String nickname = request.getNickname().trim();
            if (nickname.length() > MAX_NICKNAME_LENGTH) {
                throw new IllegalArgumentException("昵称长度不能超过 50 位");
            }
            user.setNickname(nickname);
            changed = true;
        }
        if (StringUtils.hasText(request.getAvatarUrl())) {
            String avatarUrl = request.getAvatarUrl().trim();
            if (avatarUrl.length() > MAX_AVATAR_URL_LENGTH) {
                throw new IllegalArgumentException("头像 URL 长度不能超过 500 位");
            }
            user.setAvatarUrl(avatarUrl);
            changed = true;
        }
        if (request.getGender() != null) {
            Integer gender = request.getGender();
            if (gender < 0 || gender > 2) {
                throw new IllegalArgumentException("性别参数不合法");
            }
            user.setGender(gender);
            changed = true;
        }
        return changed;
    }

    private LoginUser toLoginUser(EleUser user) {
        return new LoginUser(
                String.valueOf(user.getId()),
                user.getEmail(),
                user.getNickname(),
                user.getAvatarUrl(),
                user.getGender(),
                user.getStatus()
        );
    }

    private void refreshLoginUserCache(LoginUser loginUser) {
        try {
            redisTemplate.opsForValue().set(INFO_KEY_PREFIX + loginUser.getUserId(), objectMapper.writeValueAsString(loginUser), userLoginProperties.getTtl());
        } catch (JsonProcessingException ex) {
            throw new IllegalStateException("登录信息缓存失败", ex);
        }
    }
}
