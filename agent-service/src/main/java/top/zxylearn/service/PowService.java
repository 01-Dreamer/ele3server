package top.zxylearn.service;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Service;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.security.SecureRandom;
import java.time.Duration;
import java.util.Base64;
import java.util.Map;

@Service
public class PowService {

    private static final String CHALLENGE_KEY_PREFIX = "agent:pow:challenge:";
    private static final SecureRandom RANDOM = new SecureRandom();
    private static final ObjectMapper objectMapper = new ObjectMapper();

    private final StringRedisTemplate stringRedisTemplate;
    private final Duration challengeTtl;

    public PowService(StringRedisTemplate stringRedisTemplate,
                        @Value("${agent.pow.challenge-ttl}") Duration challengeTtl) {
        this.stringRedisTemplate = stringRedisTemplate;
        this.challengeTtl = challengeTtl;
    }

    public Map<String, Object> getChallenge(String userId) {
        byte[] bytes = new byte[16];
        RANDOM.nextBytes(bytes);
        String challenge = Base64.getUrlEncoder().withoutPadding().encodeToString(bytes);
        stringRedisTemplate.opsForValue()
                .set(CHALLENGE_KEY_PREFIX + userId + ":" + challenge, "1", challengeTtl);
        return Map.of("challenge", challenge, "difficulty", (Object) 3,
                "expireSeconds", challengeTtl.getSeconds());
    }

    /**
     * 验证 PoW。powResponse 格式: "nonce:answer:hash"
     */
    public boolean verifyPow(String userId, String powResponse, int difficulty) {
        if (powResponse == null || powResponse.isBlank()) {
            return false;
        }
        String[] parts = powResponse.trim().split(":");
        if (parts.length != 3) {
            return false;
        }
        String nonce = parts[0];
        int answer;
        try {
            answer = Integer.parseInt(parts[1]);
        } catch (NumberFormatException ex) {
            return false;
        }
        String expectedHash = parts[2];

        // Verify challenge exists in Redis (not expired, bound to this user)
        String key = CHALLENGE_KEY_PREFIX + userId + ":" + nonce;
        if (!Boolean.TRUE.equals(stringRedisTemplate.delete(key))) {
            return false;
        }

        // Verify hash = SHA256(nonce + ":" + answer)
        String raw = nonce + ":" + answer;
        String actualHash = sha256Hex(raw);
        if (!actualHash.equals(expectedHash)) {
            return false;
        }

        return checkDifficulty(actualHash, difficulty);
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
