package top.zxylearn.service;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Service;

import java.time.Duration;
import java.util.concurrent.TimeUnit;

@Service
public class RiskScoreService {

    private static final Logger log = LoggerFactory.getLogger(RiskScoreService.class);

    private static final String USER_RISK_SCORE_KEY_PREFIX = "risk:score:user:";

    private final StringRedisTemplate stringRedisTemplate;
    private final ObjectMapper objectMapper = new ObjectMapper();
    private final long maxRiskScoreSeconds;

    public RiskScoreService(StringRedisTemplate stringRedisTemplate,
                            @Value("${risk.score.ttl}") Duration scoreTtl) {
        this.stringRedisTemplate = stringRedisTemplate;
        this.maxRiskScoreSeconds = scoreTtl.toSeconds();
    }

    public RiskScoreValue getExistingRiskScore(String userId) {
        String value = stringRedisTemplate.opsForValue().get(buildUserRiskScoreKey(userId));
        return hasText(value) ? readRiskScoreValue(value) : null;
    }

    public long getUserRiskScore(String userId) {
        Long ttl = stringRedisTemplate.getExpire(buildUserRiskScoreKey(userId), TimeUnit.SECONDS);
        return ttl == null || ttl <= 0 ? 0L : ttl;
    }


    public void clearRiskScore(String userId) {
        if (!hasText(userId)) {
            throw new IllegalArgumentException("用户ID不能为空");
        }
        stringRedisTemplate.delete(buildUserRiskScoreKey(userId));
    }

    public long increaseRiskScore(String userId, long riskScore) {
        return increaseRiskScore(userId, riskScore, null, null);
    }

    public long increaseRiskScore(String userId, long riskScore, String ip, String userAgent) {
        if (!hasText(userId)) {
            throw new IllegalArgumentException("用户ID不能为空");
        }
        if (riskScore < 0) {
            throw new IllegalArgumentException("风险分不能小于0");
        }
        String key = buildUserRiskScoreKey(userId);
        RiskScoreValue value = getExistingRiskScore(userId);
        if (value == null) {
            value = new RiskScoreValue(null, null, null);
        }
        if (ip != null) {
            value.setLastIp(ip);
        }
        if (userAgent != null) {
            value.setLastUserAgent(userAgent);
        }
        value.setUpdateTime(System.currentTimeMillis());

        long currentScore = getUserRiskScore(userId);
        long newScore = riskScore == 0 ? currentScore : capRiskScore(currentScore + riskScore);
        writeRiskScoreValue(key, value, newScore);
        return newScore;
    }

    private long capRiskScore(long score) {
        if (maxRiskScoreSeconds <= 0) {
            return score;
        }
        return Math.min(score, maxRiskScoreSeconds);
    }

    private RiskScoreValue readRiskScoreValue(String value) {
        String trimmed = value.trim();
        if (!trimmed.startsWith("{")) {
            return new RiskScoreValue(null, null, null);
        }
        try {
            return objectMapper.readValue(trimmed, RiskScoreValue.class);
        } catch (JsonProcessingException ex) {
            log.warn("用户风险上下文解析失败，已重置", ex);
            return new RiskScoreValue(null, null, null);
        }
    }

    private void writeRiskScoreValue(String key, RiskScoreValue value, long riskScoreSeconds) {
        try {
            String body = objectMapper.writeValueAsString(value);
            if (riskScoreSeconds > 0) {
                stringRedisTemplate.opsForValue().set(key, body, Duration.ofSeconds(riskScoreSeconds));
                return;
            }
            stringRedisTemplate.opsForValue().set(key, body);
        } catch (JsonProcessingException ex) {
            throw new RuntimeException("用户风险上下文序列化失败", ex);
        }
    }

    private String buildUserRiskScoreKey(String userId) {
        return USER_RISK_SCORE_KEY_PREFIX + userId;
    }

    private boolean hasText(String value) {
        return value != null && !value.isBlank();
    }

    @Data
    @NoArgsConstructor
    @AllArgsConstructor
    public static class RiskScoreValue {

        private String lastIp;

        private String lastUserAgent;

        private Long updateTime;
    }
}
