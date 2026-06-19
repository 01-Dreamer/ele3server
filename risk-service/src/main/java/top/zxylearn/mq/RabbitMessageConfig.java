package top.zxylearn.mq;

import org.springframework.amqp.core.Binding;
import org.springframework.amqp.core.BindingBuilder;
import org.springframework.amqp.core.Declarables;
import org.springframework.amqp.core.DirectExchange;
import org.springframework.amqp.core.Queue;
import org.springframework.amqp.core.QueueBuilder;
import org.springframework.amqp.core.TopicExchange;
import org.springframework.amqp.support.converter.JacksonJsonMessageConverter;
import org.springframework.amqp.support.converter.MessageConverter;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import top.zxylearn.constant.MqConstants;

@Configuration
public class RabbitMessageConfig {

    @Bean
    public MessageConverter jacksonJsonMessageConverter() {
        return new JacksonJsonMessageConverter();
    }

    @Bean
    public Declarables riskRabbitDeclarables() {
        TopicExchange riskExchange = new TopicExchange(MqConstants.RISK_EXCHANGE, true, false);
        DirectExchange deadLetterExchange = new DirectExchange(MqConstants.DLX_EXCHANGE, true, false);
        Queue riskHttpQueue = QueueBuilder.durable(MqConstants.HTTP_QUEUE)
                .deadLetterExchange(MqConstants.DLX_EXCHANGE)
                .deadLetterRoutingKey(MqConstants.DLX_ROUTING_KEY)
                .build();
        Queue riskScoreIncreaseQueue = QueueBuilder.durable(MqConstants.RISK_SCORE_INCREASE_QUEUE)
                .deadLetterExchange(MqConstants.DLX_EXCHANGE)
                .deadLetterRoutingKey(MqConstants.DLX_ROUTING_KEY)
                .build();
        Queue riskTextRecordQueue = QueueBuilder.durable(MqConstants.RISK_TEXT_RECORD_QUEUE)
                .deadLetterExchange(MqConstants.DLX_EXCHANGE)
                .deadLetterRoutingKey(MqConstants.DLX_ROUTING_KEY)
                .build();
        Queue deadLetterQueue = QueueBuilder.durable(MqConstants.DLX_QUEUE).build();
        Binding riskHttpBinding = BindingBuilder.bind(riskHttpQueue)
                .to(riskExchange)
                .with(MqConstants.HTTP_ROUTING_KEY);
        Binding riskScoreIncreaseBinding = BindingBuilder.bind(riskScoreIncreaseQueue)
                .to(riskExchange)
                .with(MqConstants.RISK_SCORE_INCREASE_ROUTING_KEY);
        Binding riskTextRecordBinding = BindingBuilder.bind(riskTextRecordQueue)
                .to(riskExchange)
                .with(MqConstants.RISK_TEXT_RECORD_ROUTING_KEY);
        Binding deadLetterBinding = BindingBuilder.bind(deadLetterQueue)
                .to(deadLetterExchange)
                .with(MqConstants.DLX_ROUTING_KEY);
        return new Declarables(riskExchange, deadLetterExchange, riskHttpQueue, riskScoreIncreaseQueue, riskTextRecordQueue, deadLetterQueue,
                riskHttpBinding, riskScoreIncreaseBinding, riskTextRecordBinding, deadLetterBinding);
    }
}
