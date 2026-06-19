package top.zxylearn.service;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import top.zxylearn.dto.risk.HttpRiskEventDTO;

import java.util.Map;

@Service
public class HttpRiskService {

    private static final Logger log = LoggerFactory.getLogger(HttpRiskService.class);

    private static final String USER_AGENT_HEADER = "User-Agent";

    private final RiskScoreService riskScoreService;
    private final long baseScore;
    private final long userAgentChangeScore;
    private final long ipChangeScore;
    private final long noUserAgentScore;

    public HttpRiskService(RiskScoreService riskScoreService,
                           @Value("${risk.http.base-score}") long baseScore,
                           @Value("${risk.http.user-agent-change-score}") long userAgentChangeScore,
                           @Value("${risk.http.ip-change-score}") long ipChangeScore,
                           @Value("${risk.http.no-user-agent-score}") long noUserAgentScore) {
        this.riskScoreService = riskScoreService;
        this.baseScore = baseScore;
        this.userAgentChangeScore = userAgentChangeScore;
        this.ipChangeScore = ipChangeScore;
        this.noUserAgentScore = noUserAgentScore;
    }

    public void analyze(HttpRiskEventDTO event) {
        if (event == null || !hasText(event.getUserId())) {
            return;
        }
        String userAgent = getHeader(event.getHeaders(), USER_AGENT_HEADER);
        RiskScoreService.RiskScoreValue riskScore = riskScoreService.getExistingRiskScore(event.getUserId());
        boolean firstRequest = riskScore == null;

        long delta = 0L;
        if (!firstRequest) {
            delta += baseScore;
            if (!hasText(userAgent)) {
                delta += noUserAgentScore;
            } else if (hasText(riskScore.getLastUserAgent()) && !userAgent.equals(riskScore.getLastUserAgent())) {
                delta += userAgentChangeScore;
            }
            if (hasText(event.getIp()) && hasText(riskScore.getLastIp()) && !event.getIp().equals(riskScore.getLastIp())) {
                delta += ipChangeScore;
            }
        }

        long updatedScore = riskScoreService.increaseRiskScore(event.getUserId(), delta, event.getIp(), userAgent);
        if (delta > 0) {
            log.info("HTTP风控计分: userId={}, path={}, delta={}, ttlScore={}",
                    event.getUserId(), event.getPath(), delta, updatedScore);
        }
    }

    private String getHeader(Map<String, String> headers, String targetName) {
        if (headers == null || headers.isEmpty()) {
            return null;
        }
        for (Map.Entry<String, String> entry : headers.entrySet()) {
            if (entry.getKey() != null && entry.getKey().equalsIgnoreCase(targetName)) {
                return entry.getValue();
            }
        }
        return null;
    }

    private boolean hasText(String value) {
        return value != null && !value.isBlank();
    }
}
