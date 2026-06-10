package top.zxylearn.listener;

import org.springframework.amqp.rabbit.annotation.RabbitListener;
import org.springframework.stereotype.Component;
import top.zxylearn.constant.RiskMqConstants;
import top.zxylearn.dto.risk.HttpRiskEventDTO;

@Component
public class HttpRiskEventListener {

    @RabbitListener(queues = RiskMqConstants.HTTP_QUEUE)
    public void listen(HttpRiskEventDTO event) {
        System.out.println("收到HTTP风控事件: " + event);
    }
}
