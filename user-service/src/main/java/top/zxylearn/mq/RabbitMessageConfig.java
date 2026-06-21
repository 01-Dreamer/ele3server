package top.zxylearn.mq;

import org.springframework.amqp.core.Declarables;
import org.springframework.amqp.core.TopicExchange;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import top.zxylearn.constant.MqConstants;

@Configuration
public class RabbitMessageConfig {

    @Bean
    public Declarables userRabbitDeclarables() {
        TopicExchange fileExchange = new TopicExchange(MqConstants.FILE_EXCHANGE, true, false);
        return new Declarables(fileExchange);
    }
}
