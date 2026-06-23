package top.zxylearn.service;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.SerializationFeature;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;
import org.springframework.amqp.rabbit.core.RabbitTemplate;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.script.DefaultRedisScript;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.support.TransactionSynchronization;
import org.springframework.transaction.support.TransactionSynchronizationManager;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import top.zxylearn.constant.MqConstants;
import top.zxylearn.dto.UserLocationCreateRequest;
import top.zxylearn.dto.UserUpdateRequest;
import top.zxylearn.dto.risk.RiskTextRecordCreateEventDTO;
import top.zxylearn.dto.user.UserCreateRequest;
import top.zxylearn.entity.User;
import top.zxylearn.entity.UserLocation;
import top.zxylearn.mapper.UserLocationMapper;
import top.zxylearn.mapper.UserMapper;
import top.zxylearn.vo.PageVO;
import top.zxylearn.vo.UserBriefVO;
import top.zxylearn.vo.UserLocationVO;
import top.zxylearn.vo.UserVO;

import java.math.BigDecimal;
import java.time.Duration;
import java.util.Collections;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.ThreadLocalRandom;

@Service
public class UserService {

    private static final Logger log = LoggerFactory.getLogger(UserService.class);
    private static final String DEFAULT_NICKNAME = "饿了么用户";
    private static final BigDecimal MIN_LONGITUDE = new BigDecimal("-180");
    private static final BigDecimal MAX_LONGITUDE = new BigDecimal("180");
    private static final BigDecimal MIN_LATITUDE = new BigDecimal("-90");
    private static final BigDecimal MAX_LATITUDE = new BigDecimal("90");
    private static final String USER_INFO_KEY_PREFIX = "user:info:";
    private static final String USER_LOCK_KEY_PREFIX = "user:lock:";
    private static final String NULL_CACHE_VALUE = "__NULL__";
    private static final DefaultRedisScript<Long> UNLOCK_SCRIPT = new DefaultRedisScript<>(
            "if redis.call('get', KEYS[1]) == ARGV[1] then return redis.call('del', KEYS[1]) else return 0 end",
            Long.class
    );

    private final UserMapper userMapper;
    private final UserLocationMapper userLocationMapper;
    private final StringRedisTemplate stringRedisTemplate;
    private final RabbitTemplate rabbitTemplate;
    private final ObjectMapper objectMapper;
    private final Duration cacheTtl;
    private final Duration nullCacheTtl;
    private final Duration lockTtl;
    private final int cacheTtlJitterSeconds;
    private final int nullCacheTtlJitterSeconds;

    public UserService(UserMapper userMapper,
                       UserLocationMapper userLocationMapper,
                       StringRedisTemplate stringRedisTemplate,
                       RabbitTemplate rabbitTemplate,
                       @Value("${user.cache.ttl}") Duration cacheTtl,
                       @Value("${user.cache.null-ttl}") Duration nullCacheTtl,
                       @Value("${user.cache.lock-ttl}") Duration lockTtl,
                       @Value("${user.cache.ttl-jitter-seconds}") int cacheTtlJitterSeconds,
                       @Value("${user.cache.null-ttl-jitter-seconds}") int nullCacheTtlJitterSeconds) {
        this.userMapper = userMapper;
        this.userLocationMapper = userLocationMapper;
        this.stringRedisTemplate = stringRedisTemplate;
        this.rabbitTemplate = rabbitTemplate;
        this.cacheTtl = cacheTtl;
        this.nullCacheTtl = nullCacheTtl;
        this.lockTtl = lockTtl;
        this.cacheTtlJitterSeconds = cacheTtlJitterSeconds;
        this.nullCacheTtlJitterSeconds = nullCacheTtlJitterSeconds;
        this.objectMapper = new ObjectMapper()
                .registerModule(new JavaTimeModule())
                .disable(SerializationFeature.WRITE_DATES_AS_TIMESTAMPS);
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
            User savedUser = getUserById(user.getId());
            cacheUser(toUserVO(savedUser));
        } catch (DuplicateKeyException ex) {
            throw new IllegalArgumentException("用户资料已存在");
        }
    }

    public UserVO getUser(String userId) {
        return getUserWithCache(userId);
    }

    public UserBriefVO getUserBrief(String userId) {
        UserVO user = getUserWithCache(userId);
        return new UserBriefVO(user.getUserId(), user.getNickname(), user.getAvatar());
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
            publishRiskText("NICKNAME", userId, nickname);
            user.setNickname(nickname);
            changed = true;
        }
        if (hasText(request.getAvatar())) {
            String avatar = request.getAvatar().trim();
            checkLength(avatar, 500, "头像URL");
            publishOldImageDeleteAfterCommit(user.getAvatar(), avatar);
            user.setAvatar(avatar);
            changed = true;
        }
        if (changed) {
            userMapper.updateById(user);
            user = getUserById(id);
            cacheUser(toUserVO(user));
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

    public UserLocationVO getLocation(String userId, String locationId) {
        Long ownerId = parseLongId(userId, "用户ID");
        if (!hasText(locationId)) {
            List<UserLocation> locations = userLocationMapper.selectList(
                    new LambdaQueryWrapper<UserLocation>()
                            .eq(UserLocation::getUserId, ownerId)
                            .orderByDesc(UserLocation::getCreateTime)
                            .last("LIMIT 1"));
            if (locations.isEmpty()) {
                throw new IllegalArgumentException("暂无收货地址");
            }
            return toLocationVO(locations.get(0));
        }
        Long id = parseLongId(locationId, "收货地址ID");
        UserLocation location = userLocationMapper.selectById(id);
        if (location == null) {
            throw new IllegalArgumentException("收货地址不存在");
        }
        if (!ownerId.equals(location.getUserId())) {
            throw new IllegalArgumentException("只能查看自己的收货地址");
        }
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

    public PageVO<UserLocationVO> listLocations(String userId, Integer page, Integer size) {
        Long ownerId = parseLongId(userId, "用户ID");
        int pageNum = page != null && page > 0 ? page : 1;
        int pageSize = size != null && size > 0 ? Math.min(size, 100) : 20;

        Page<UserLocation> mpPage = new Page<>(pageNum, pageSize);
        Page<UserLocation> result = userLocationMapper.selectPage(mpPage,
                new LambdaQueryWrapper<UserLocation>()
                        .eq(UserLocation::getUserId, ownerId)
                        .orderByDesc(UserLocation::getCreateTime));

        List<UserLocationVO> items = result.getRecords().stream()
                .map(this::toLocationVO).toList();
        return new PageVO<>(items, result.getTotal(), pageNum, pageSize);
    }

    private UserVO getUserWithCache(String userId) {
        Long id = parseLongId(userId, "用户ID");
        String normalizedUserId = String.valueOf(id);
        String key = buildUserInfoKey(normalizedUserId);
        UserVO cached = readUserCache(key);
        if (cached != null) {
            return cached;
        }

        String lockKey = buildUserInfoLockKey(normalizedUserId);
        String lockToken = tryLock(lockKey);
        if (lockToken != null) {
            try {
                cached = readUserCache(key);
                if (cached != null) {
                    return cached;
                }
                User user = userMapper.selectById(id);
                if (user == null) {
                    cacheNullUser(key);
                    throw new IllegalArgumentException("用户资料不存在");
                }
                UserVO userVO = toUserVO(user);
                cacheUser(userVO);
                return userVO;
            } finally {
                unlock(lockKey, lockToken);
            }
        }

        String waitedValue = waitCacheValue(key);
        if (waitedValue != null) {
            return parseUserCacheValue(key, waitedValue);
        }
        User user = userMapper.selectById(id);
        if (user == null) {
            cacheNullUser(key);
            throw new IllegalArgumentException("用户资料不存在");
        }
        UserVO userVO = toUserVO(user);
        cacheUser(userVO);
        return userVO;
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

    private void cacheUser(UserVO user) {
        if (user != null && hasText(user.getUserId())) {
            writeCache(buildUserInfoKey(user.getUserId()), user, cacheTtlWithJitter());
        }
    }

    private void cacheNullUser(String key) {
        stringRedisTemplate.opsForValue().set(key, NULL_CACHE_VALUE, nullCacheTtlWithJitter());
    }

    private UserVO readUserCache(String key) {
        String value = stringRedisTemplate.opsForValue().get(key);
        if (value == null || value.isBlank()) {
            return null;
        }
        return parseUserCacheValue(key, value);
    }

    private UserVO parseUserCacheValue(String key, String value) {
        if (NULL_CACHE_VALUE.equals(value)) {
            throw new IllegalArgumentException("用户资料不存在");
        }
        try {
            return objectMapper.readValue(value, UserVO.class);
        } catch (JsonProcessingException ex) {
            stringRedisTemplate.delete(key);
            throw new RuntimeException("用户缓存解析失败", ex);
        }
    }

    private void writeCache(String key, Object value, Duration ttl) {
        try {
            stringRedisTemplate.opsForValue().set(key, objectMapper.writeValueAsString(value), ttl);
        } catch (JsonProcessingException ex) {
            throw new RuntimeException("用户缓存序列化失败", ex);
        }
    }

    private String tryLock(String key) {
        String token = UUID.randomUUID().toString();
        Boolean locked = stringRedisTemplate.opsForValue().setIfAbsent(key, token, lockTtl);
        return Boolean.TRUE.equals(locked) ? token : null;
    }

    private void unlock(String key, String token) {
        stringRedisTemplate.execute(UNLOCK_SCRIPT, Collections.singletonList(key), token);
    }

    private String waitCacheValue(String key) {
        for (int i = 0; i < 3; i++) {
            sleepQuietly(50);
            String value = stringRedisTemplate.opsForValue().get(key);
            if (value != null && !value.isBlank()) {
                return value;
            }
        }
        return null;
    }

    private void sleepQuietly(long millis) {
        try {
            Thread.sleep(millis);
        } catch (InterruptedException ex) {
            Thread.currentThread().interrupt();
        }
    }

    private Duration cacheTtlWithJitter() {
        return cacheTtl.plusSeconds(ThreadLocalRandom.current().nextInt(Math.max(cacheTtlJitterSeconds, 0) + 1));
    }

    private Duration nullCacheTtlWithJitter() {
        return nullCacheTtl.plusSeconds(ThreadLocalRandom.current().nextInt(Math.max(nullCacheTtlJitterSeconds, 0) + 1));
    }

    private String buildUserInfoKey(String userId) {
        return USER_INFO_KEY_PREFIX + userId;
    }

    private String buildUserInfoLockKey(String userId) {
        return USER_LOCK_KEY_PREFIX + "info:" + userId;
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

    private void publishRiskText(String sourceType, String sourceId, String content) {
        if (!hasText(content)) return;
        try {
            rabbitTemplate.convertAndSend(MqConstants.RISK_EXCHANGE, MqConstants.RISK_TEXT_RECORD_ROUTING_KEY,
                    new RiskTextRecordCreateEventDTO(sourceType, sourceId, sourceId, content));
        } catch (RuntimeException ex) {
            log.warn("风控文本事件发送失败", ex);
        }
    }

    private void publishOldImageDeleteAfterCommit(String oldUrl, String newUrl) {
        if (!hasText(oldUrl)) {
            return;
        }
        String normalizedOldUrl = oldUrl.trim();
        String normalizedNewUrl = newUrl == null ? null : newUrl.trim();
        if (normalizedOldUrl.equals(normalizedNewUrl)) {
            return;
        }
        Runnable publisher = () -> rabbitTemplate.convertAndSend(
                MqConstants.FILE_EXCHANGE,
                MqConstants.FILE_IMAGE_DELETE_ROUTING_KEY,
                normalizedOldUrl
        );
        if (TransactionSynchronizationManager.isSynchronizationActive()) {
            TransactionSynchronizationManager.registerSynchronization(new TransactionSynchronization() {
                @Override
                public void afterCommit() {
                    publisher.run();
                }
            });
            return;
        }
        publisher.run();
    }
}
