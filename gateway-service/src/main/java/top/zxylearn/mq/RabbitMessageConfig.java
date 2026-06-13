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
import top.zxylearn.constant.RiskMqConstants;

@Configuration
public class RabbitMessageConfig {

    @Bean
    public MessageConverter jacksonJsonMessageConverter() {
        return new JacksonJsonMessageConverter();
    }

    @Bean
    public Declarables riskRabbitDeclarables() {
        TopicExchange riskExchange = new TopicExchange(RiskMqConstants.RISK_EXCHANGE, true, false);
        DirectExchange deadLetterExchange = new DirectExchange(RiskMqConstants.DLX_EXCHANGE, true, false);
        Queue riskHttpQueue = QueueBuilder.durable(RiskMqConstants.HTTP_QUEUE)
                .deadLetterExchange(RiskMqConstants.DLX_EXCHANGE)
                .deadLetterRoutingKey(RiskMqConstants.DLX_ROUTING_KEY)
                .build();
        Queue deadLetterQueue = QueueBuilder.durable(RiskMqConstants.DLX_QUEUE).build();
        Binding riskHttpBinding = BindingBuilder.bind(riskHttpQueue)
                .to(riskExchange)
                .with(RiskMqConstants.HTTP_ROUTING_KEY);
        Binding deadLetterBinding = BindingBuilder.bind(deadLetterQueue)
                .to(deadLetterExchange)
                .with(RiskMqConstants.DLX_ROUTING_KEY);
        return new Declarables(riskExchange, deadLetterExchange, riskHttpQueue, deadLetterQueue,
                riskHttpBinding, deadLetterBinding);
    }
}
