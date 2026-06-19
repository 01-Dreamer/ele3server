package top.zxylearn.service;

import org.springframework.dao.DuplicateKeyException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import top.zxylearn.dto.UserLocationCreateRequest;
import top.zxylearn.dto.UserUpdateRequest;
import top.zxylearn.dto.user.UserCreateRequest;
import top.zxylearn.entity.User;
import top.zxylearn.entity.UserLocation;
import top.zxylearn.mapper.UserLocationMapper;
import top.zxylearn.mapper.UserMapper;
import top.zxylearn.vo.UserLocationVO;
import top.zxylearn.vo.UserVO;

import java.math.BigDecimal;

@Service
public class UserService {

    private static final String DEFAULT_NICKNAME = "饿了么用户";
    private static final BigDecimal MIN_LONGITUDE = new BigDecimal("-180");
    private static final BigDecimal MAX_LONGITUDE = new BigDecimal("180");
    private static final BigDecimal MIN_LATITUDE = new BigDecimal("-90");
    private static final BigDecimal MAX_LATITUDE = new BigDecimal("90");

    private final UserMapper userMapper;
    private final UserLocationMapper userLocationMapper;

    public UserService(UserMapper userMapper, UserLocationMapper userLocationMapper) {
        this.userMapper = userMapper;
        this.userLocationMapper = userLocationMapper;
    }

    @Transactional(rollbackFor = Exception.class)
    public void createUser(UserCreateRequest request) {
        if (request == null) {
            throw new IllegalArgumentException("用户ID不能为空");
        }
        User user = new User();
        user.setId(parseLongId(request.getUserId(), "用户ID"));
        user.setNickname(DEFAULT_NICKNAME);
        try {
            userMapper.insert(user);
        } catch (DuplicateKeyException ex) {
            throw new IllegalArgumentException("用户资料已存在");
        }
    }

    public UserVO getUser(String userId) {
        return toUserVO(getUserById(parseLongId(userId, "用户ID")));
    }

    @Transactional(rollbackFor = Exception.class)
    public UserVO updateUser(String userId, UserUpdateRequest request) {
        if (request == null) {
            throw new IllegalArgumentException("用户资料不能为空");
        }
        Long id = parseLongId(userId, "用户ID");
        User user = getUserById(id);
        boolean changed = false;
        if (hasText(request.getNickname())) {
            String nickname = request.getNickname().trim();
            checkLength(nickname, 50, "昵称");
            user.setNickname(nickname);
            changed = true;
        }
        if (hasText(request.getAvatar())) {
            String avatar = request.getAvatar().trim();
            checkLength(avatar, 500, "头像URL");
            user.setAvatar(avatar);
            changed = true;
        }
        if (changed) {
            userMapper.updateById(user);
            user = getUserById(id);
        }
        return toUserVO(user);
    }

    @Transactional(rollbackFor = Exception.class)
    public UserLocationVO addLocation(String userId, UserLocationCreateRequest request) {
        if (request == null) {
            throw new IllegalArgumentException("收货地址不能为空");
        }
        Long ownerId = parseLongId(userId, "用户ID");
        getUserById(ownerId);
        UserLocation location = new UserLocation();
        location.setUserId(ownerId);
        location.setName(checkRequiredText(request.getName(), 50, "收货人姓名"));
        location.setPhone(checkRequiredText(request.getPhone(), 20, "收货人手机号"));
        location.setAddress(checkRequiredText(request.getAddress(), 255, "收货地址"));
        checkLongitude(request.getLongitude());
        checkLatitude(request.getLatitude());
        location.setLongitude(request.getLongitude());
        location.setLatitude(request.getLatitude());
        userLocationMapper.insert(location);
        return toLocationVO(location);
    }

    @Transactional(rollbackFor = Exception.class)
    public void deleteLocation(String userId, String locationId) {
        Long ownerId = parseLongId(userId, "用户ID");
        Long id = parseLongId(locationId, "收货地址ID");
        UserLocation location = userLocationMapper.selectById(id);
        if (location == null) {
            throw new IllegalArgumentException("收货地址不存在");
        }
        if (!ownerId.equals(location.getUserId())) {
            throw new IllegalArgumentException("只能删除自己的收货地址");
        }
        userLocationMapper.deleteById(id);
    }

    private User getUserById(Long userId) {
        if (userId == null) {
            throw new IllegalArgumentException("用户ID不能为空");
        }
        User user = userMapper.selectById(userId);
        if (user == null) {
            throw new IllegalArgumentException("用户资料不存在");
        }
        return user;
    }

    private UserVO toUserVO(User user) {
        return new UserVO(
                String.valueOf(user.getId()),
                user.getNickname(),
                user.getAvatar(),
                user.getCreateTime(),
                user.getUpdateTime()
        );
    }

    private UserLocationVO toLocationVO(UserLocation location) {
        return new UserLocationVO(
                String.valueOf(location.getId()),
                location.getName(),
                location.getPhone(),
                location.getAddress(),
                location.getLongitude(),
                location.getLatitude(),
                location.getCreateTime(),
                location.getUpdateTime()
        );
    }

    private Long parseLongId(String value, String fieldName) {
        if (!hasText(value)) {
            throw new IllegalArgumentException(fieldName + "不能为空");
        }
        try {
            return Long.valueOf(value.trim());
        } catch (NumberFormatException ex) {
            throw new IllegalArgumentException(fieldName + "格式不正确");
        }
    }

    private String checkRequiredText(String value, int maxLength, String fieldName) {
        if (!hasText(value)) {
            throw new IllegalArgumentException(fieldName + "不能为空");
        }
        String trimmed = value.trim();
        checkLength(trimmed, maxLength, fieldName);
        return trimmed;
    }

    private void checkLength(String value, int maxLength, String fieldName) {
        if (value.length() > maxLength) {
            throw new IllegalArgumentException(fieldName + "长度不能超过" + maxLength + "个字符");
        }
    }

    private void checkLongitude(BigDecimal longitude) {
        if (longitude == null) {
            return;
        }
        if (longitude.compareTo(MIN_LONGITUDE) < 0 || longitude.compareTo(MAX_LONGITUDE) > 0) {
            throw new IllegalArgumentException("经度范围必须在-180到180之间");
        }
    }

    private void checkLatitude(BigDecimal latitude) {
        if (latitude == null) {
            return;
        }
        if (latitude.compareTo(MIN_LATITUDE) < 0 || latitude.compareTo(MAX_LATITUDE) > 0) {
            throw new IllegalArgumentException("纬度范围必须在-90到90之间");
        }
    }

    private boolean hasText(String value) {
        return value != null && !value.isBlank();
    }
}
