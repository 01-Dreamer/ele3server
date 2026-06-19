package top.zxylearn.listener;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.amqp.core.Message;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.amqp.rabbit.annotation.RabbitListener;
import org.springframework.stereotype.Component;
import top.zxylearn.constant.MqConstants;
import top.zxylearn.dto.risk.HttpRiskEventDTO;
import top.zxylearn.service.HttpRiskService;

@Component
public class HttpRiskEventListener {

    private static final Logger log = LoggerFactory.getLogger(HttpRiskEventListener.class);

    private final ObjectMapper objectMapper = new ObjectMapper();
    private final HttpRiskService httpRiskService;

    public HttpRiskEventListener(HttpRiskService httpRiskService) {
        this.httpRiskService = httpRiskService;
    }

    @RabbitListener(queues = MqConstants.HTTP_QUEUE)
    public void listen(Message message) {
        String body = new String(message.getBody());
        log.info("收到HTTP风控原始消息: {}", body);
        try {
            HttpRiskEventDTO event = objectMapper.readValue(body, HttpRiskEventDTO.class);
            log.info("收到HTTP风控事件: {}", event);
            httpRiskService.analyze(event);
        } catch (JsonProcessingException ex) {
            log.warn("HTTP风控消息不是标准JSON事件，已忽略解析 body={}", body);
        }
    }
}
