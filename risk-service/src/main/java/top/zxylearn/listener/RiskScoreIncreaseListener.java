package top.zxylearn.listener;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.amqp.core.Message;
import org.springframework.amqp.rabbit.annotation.RabbitListener;
import org.springframework.stereotype.Component;
import top.zxylearn.constant.MqConstants;
import top.zxylearn.dto.risk.RiskScoreIncreaseEventDTO;
import top.zxylearn.service.RiskScoreService;

@Component
public class RiskScoreIncreaseListener {

    private static final Logger log = LoggerFactory.getLogger(RiskScoreIncreaseListener.class);

    private final ObjectMapper objectMapper = new ObjectMapper();
    private final RiskScoreService riskScoreService;

    public RiskScoreIncreaseListener(RiskScoreService riskScoreService) {
        this.riskScoreService = riskScoreService;
    }

    @RabbitListener(queues = MqConstants.RISK_SCORE_INCREASE_QUEUE)
    public void listen(Message message) {
        String body = new String(message.getBody());
        try {
            RiskScoreIncreaseEventDTO event = objectMapper.readValue(body, RiskScoreIncreaseEventDTO.class);
            if (event.getRiskScore() == null || event.getRiskScore() <= 0) {
                throw new IllegalArgumentException("增加的风险分必须大于0");
            }
            long score = riskScoreService.increaseRiskScore(event.getUserId(), event.getRiskScore());
            log.info("用户风险分MQ累加完成 userId={}, source={}, delta={}, ttlScore={}",
                    event.getUserId(), event.getSource(), event.getRiskScore(), score);
        } catch (JsonProcessingException ex) {
            log.warn("用户风险分MQ消息不是标准JSON，已忽略 body={}", body);
        }
    }
}
