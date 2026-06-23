package top.zxylearn.service;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Service;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.security.SecureRandom;
import java.time.Duration;
import java.time.LocalTime;
import java.time.format.DateTimeFormatter;
import java.util.Base64;
import java.util.Map;

@Service
public class PowService {

    private static final String CHALLENGE_KEY_PREFIX = "agent:pow:challenge:";
    private static final String RATE_KEY_PREFIX = "agent:pow:rate:";
    private static final SecureRandom RANDOM = new SecureRandom();
    private static final int MIN_DIFFICULTY = 3;
    private static final int MAX_DIFFICULTY = 6;
    private static final Duration RATE_WINDOW = Duration.ofMinutes(1);

    private final StringRedisTemplate stringRedisTemplate;
    private final Duration challengeTtl;

    public PowService(StringRedisTemplate stringRedisTemplate,
                      @Value("${agent.pow.challenge-ttl}") Duration challengeTtl) {
        this.stringRedisTemplate = stringRedisTemplate;
        this.challengeTtl = challengeTtl;
    }

    public Map<String, Object> getChallenge(String userId) {
        int difficulty = calcDifficulty(userId);

        byte[] bytes = new byte[16];
        RANDOM.nextBytes(bytes);
        String challenge = Base64.getUrlEncoder().withoutPadding().encodeToString(bytes);

        stringRedisTemplate.opsForValue()
                .set(CHALLENGE_KEY_PREFIX + userId + ":" + challenge,
                        String.valueOf(difficulty), challengeTtl);

        return Map.of("challenge", challenge, "difficulty", (Object) difficulty,
                "expireSeconds", challengeTtl.getSeconds());
    }

    private int calcDifficulty(String userId) {
        String rateKey = RATE_KEY_PREFIX + userId;
        Long count = stringRedisTemplate.opsForValue().increment(rateKey);
        if (count != null && count == 1) {
            stringRedisTemplate.expire(rateKey, RATE_WINDOW);
        }
        long c = count == null ? 0 : count;
        int difficulty = MIN_DIFFICULTY + (int) (c / 5);
        return Math.min(difficulty, MAX_DIFFICULTY);
    }

    public String extractNonce(String userId, String powResponse, boolean consume) {
        if (powResponse == null || powResponse.isBlank()) {
            return null;
        }
        String[] parts = powResponse.trim().split(":");
        if (parts.length != 3) {
            return null;
        }
        String nonce = parts[0];
        int answer;
        try {
            answer = Integer.parseInt(parts[1]);
        } catch (NumberFormatException ex) {
            return null;
        }
        String expectedHash = parts[2];

        String key = CHALLENGE_KEY_PREFIX + userId + ":" + nonce;
        String difficultyStr = stringRedisTemplate.opsForValue().get(key);
        if (difficultyStr == null) {
            return null;
        }
        int difficulty;
        try {
            difficulty = Integer.parseInt(difficultyStr);
        } catch (NumberFormatException ex) {
            return null;
        }

        String raw = nonce + ":" + answer;
        String actualHash = sha256Hex(raw);
        if (!actualHash.equals(expectedHash)) {
            return null;
        }

        if (!checkDifficulty(actualHash, difficulty)) {
            return null;
        }

        if (consume) {
            stringRedisTemplate.delete(key);
        }
        return nonce;
    }

    public void consumeChallenge(String userId, String nonce) {
        if (nonce != null) {
            stringRedisTemplate.delete(CHALLENGE_KEY_PREFIX + userId + ":" + nonce);
        }
    }

    private String sha256Hex(String input) {
        try {
            MessageDigest md = MessageDigest.getInstance("SHA-256");
            byte[] hash = md.digest(input.getBytes(StandardCharsets.UTF_8));
            StringBuilder sb = new StringBuilder();
            for (byte b : hash) {
                sb.append(String.format("%02x", b));
            }
            return sb.toString();
        } catch (NoSuchAlgorithmException ex) {
            throw new RuntimeException("SHA-256不可用", ex);
        }
    }

    private boolean checkDifficulty(String hash, int difficulty) {
        if (difficulty <= 0 || difficulty > hash.length()) {
            return false;
        }
        for (int i = 0; i < difficulty; i++) {
            if (hash.charAt(i) != '0') {
                return false;
            }
        }
        return true;
    }
}
