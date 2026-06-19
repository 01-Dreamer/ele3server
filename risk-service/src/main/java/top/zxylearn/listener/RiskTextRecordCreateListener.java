package top.zxylearn.listener;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.github.houbb.sensitive.word.bs.SensitiveWordBs;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.amqp.core.Message;
import org.springframework.amqp.rabbit.annotation.RabbitListener;
import org.springframework.stereotype.Component;
import top.zxylearn.constant.MqConstants;
import top.zxylearn.dto.risk.RiskTextRecordCreateEventDTO;
import top.zxylearn.entity.RiskTextRecord;
import top.zxylearn.mapper.RiskTextRecordMapper;

@Component
public class RiskTextRecordCreateListener {

    private static final Logger log = LoggerFactory.getLogger(RiskTextRecordCreateListener.class);

    private final ObjectMapper objectMapper = new ObjectMapper();
    private final RiskTextRecordMapper riskTextRecordMapper;
    private final SensitiveWordBs sensitiveWordBs;

    public RiskTextRecordCreateListener(RiskTextRecordMapper riskTextRecordMapper, SensitiveWordBs sensitiveWordBs) {
        this.riskTextRecordMapper = riskTextRecordMapper;
        this.sensitiveWordBs = sensitiveWordBs;
    }

    @RabbitListener(queues = MqConstants.RISK_TEXT_RECORD_QUEUE)
    public void listen(Message message) {
        String body = new String(message.getBody());
        try {
            RiskTextRecordCreateEventDTO event = objectMapper.readValue(body, RiskTextRecordCreateEventDTO.class);
            RiskTextRecord record = buildRecord(event);
            if (!sensitiveWordBs.contains(record.getContent())) {
                log.info("文本风控未命中敏感词，已忽略 sourceType={}, sourceId={}, userId={}",
                        record.getSourceType(), record.getSourceId(), record.getUserId());
                return;
            }
            riskTextRecordMapper.insert(record);
            log.info("文本风控命中敏感词，记录已入库 id={}, sourceType={}, sourceId={}, userId={}",
                    record.getId(), record.getSourceType(), record.getSourceId(), record.getUserId());
        } catch (JsonProcessingException ex) {
            log.warn("文本风控MQ消息不是标准JSON body={}", body);
            throw new IllegalArgumentException("文本风控MQ消息不是标准JSON", ex);
        }
    }

    private RiskTextRecord buildRecord(RiskTextRecordCreateEventDTO event) {
        if (event == null) {
            throw new IllegalArgumentException("文本风控消息不能为空");
        }
        RiskTextRecord record = new RiskTextRecord();
        record.setSourceType(checkRequiredText(event.getSourceType(), 50, "来源类型"));
        record.setSourceId(parseLongId(event.getSourceId(), "来源业务ID"));
        record.setUserId(parseLongId(event.getUserId(), "文本发布用户ID"));
        record.setContent(checkRequiredText(event.getContent(), Integer.MAX_VALUE, "违规文本内容"));
        record.setStatus(0);
        return record;
    }

    private Long parseLongId(String value, String fieldName) {
        if (value == null || value.isBlank()) {
            throw new IllegalArgumentException(fieldName + "不能为空");
        }
        try {
            return Long.valueOf(value.trim());
        } catch (NumberFormatException ex) {
            throw new IllegalArgumentException(fieldName + "格式不正确");
        }
    }

    private String checkRequiredText(String value, int maxLength, String fieldName) {
        if (value == null || value.isBlank()) {
            throw new IllegalArgumentException(fieldName + "不能为空");
        }
        String trimmed = value.trim();
        if (trimmed.length() > maxLength) {
            throw new IllegalArgumentException(fieldName + "长度不能超过" + maxLength + "个字符");
        }
        return trimmed;
    }
}
