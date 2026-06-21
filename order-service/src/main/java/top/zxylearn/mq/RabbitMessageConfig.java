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
    public Declarables orderRabbitDeclarables() {
        TopicExchange orderExchange = new TopicExchange(MqConstants.ORDER_EXCHANGE, true, false);
        DirectExchange deadLetterExchange = new DirectExchange(MqConstants.DLX_EXCHANGE, true, false);
        Queue delayQueue = QueueBuilder.durable(MqConstants.ORDER_EXPIRE_DELAY_QUEUE)
                .deadLetterExchange(MqConstants.ORDER_EXCHANGE)
                .deadLetterRoutingKey(MqConstants.ORDER_EXPIRE_ROUTING_KEY)
                .build();
        Queue expireQueue = QueueBuilder.durable(MqConstants.ORDER_EXPIRE_QUEUE)
                .deadLetterExchange(MqConstants.DLX_EXCHANGE)
                .deadLetterRoutingKey(MqConstants.DLX_ROUTING_KEY)
                .build();
        Queue deadLetterQueue = QueueBuilder.durable(MqConstants.DLX_QUEUE).build();
        Binding delayBinding = BindingBuilder.bind(delayQueue).to(orderExchange).with(MqConstants.ORDER_EXPIRE_DELAY_ROUTING_KEY);
        Binding expireBinding = BindingBuilder.bind(expireQueue).to(orderExchange).with(MqConstants.ORDER_EXPIRE_ROUTING_KEY);
        Binding deadLetterBinding = BindingBuilder.bind(deadLetterQueue).to(deadLetterExchange).with(MqConstants.DLX_ROUTING_KEY);
        return new Declarables(orderExchange, deadLetterExchange, delayQueue, expireQueue, deadLetterQueue, delayBinding, expireBinding, deadLetterBinding);
    }
}
