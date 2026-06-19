package top.zxylearn.service;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Service;
import top.zxylearn.dto.CoordinateUploadRequest;
import top.zxylearn.vo.CoordinateVO;

import java.math.BigDecimal;
import java.time.Duration;

@Service
public class LocationService {

    private static final String USER_COORDINATE_KEY_PREFIX = "location:user:coordinate:";
    private static final BigDecimal MIN_LONGITUDE = BigDecimal.valueOf(-180);
    private static final BigDecimal MAX_LONGITUDE = BigDecimal.valueOf(180);
    private static final BigDecimal MIN_LATITUDE = BigDecimal.valueOf(-90);
    private static final BigDecimal MAX_LATITUDE = BigDecimal.valueOf(90);

    private final StringRedisTemplate stringRedisTemplate;
    private final ObjectMapper objectMapper = new ObjectMapper();
    private final Duration coordinateTtl;

    public LocationService(StringRedisTemplate stringRedisTemplate,
                           @Value("${location.coordinate.ttl}") Duration coordinateTtl) {
        this.stringRedisTemplate = stringRedisTemplate;
        this.coordinateTtl = coordinateTtl;
    }

    public CoordinateVO uploadCoordinate(String userId, CoordinateUploadRequest request) {
        if (request == null) {
            throw new IllegalArgumentException("经纬度不能为空");
        }
        checkCoordinate(request.getLongitude(), request.getLatitude());
        CoordinateVO coordinate = new CoordinateVO(
                request.getLongitude(),
                request.getLatitude(),
                System.currentTimeMillis()
        );
        stringRedisTemplate.opsForValue().set(buildUserCoordinateKey(userId), toJson(coordinate), coordinateTtl);
        return coordinate;
    }

    public CoordinateVO getCoordinate(String userId) {
        String value = stringRedisTemplate.opsForValue().get(buildUserCoordinateKey(userId));
        if (value == null || value.isBlank()) {
            return null;
        }
        try {
            return objectMapper.readValue(value, CoordinateVO.class);
        } catch (JsonProcessingException ex) {
            throw new RuntimeException("经纬度信息解析失败", ex);
        }
    }

    private void checkCoordinate(BigDecimal longitude, BigDecimal latitude) {
        if (longitude == null || latitude == null) {
            throw new IllegalArgumentException("经纬度不能为空");
        }
        if (longitude.compareTo(MIN_LONGITUDE) < 0 || longitude.compareTo(MAX_LONGITUDE) > 0) {
            throw new IllegalArgumentException("经度范围必须在 -180 到 180 之间");
        }
        if (latitude.compareTo(MIN_LATITUDE) < 0 || latitude.compareTo(MAX_LATITUDE) > 0) {
            throw new IllegalArgumentException("纬度范围必须在 -90 到 90 之间");
        }
    }

    private String toJson(CoordinateVO coordinate) {
        try {
            return objectMapper.writeValueAsString(coordinate);
        } catch (JsonProcessingException ex) {
            throw new RuntimeException("经纬度信息序列化失败", ex);
        }
    }

    private String buildUserCoordinateKey(String userId) {
        if (userId == null || userId.isBlank()) {
            throw new IllegalArgumentException("用户ID不能为空");
        }
        return USER_COORDINATE_KEY_PREFIX + userId.trim();
    }
}
