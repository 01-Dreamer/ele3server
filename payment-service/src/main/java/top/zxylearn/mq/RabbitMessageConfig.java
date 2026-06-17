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
    public Declarables paymentRabbitDeclarables() {
        TopicExchange paymentExchange = new TopicExchange(MqConstants.PAYMENT_EXCHANGE, true, false);
        DirectExchange deadLetterExchange = new DirectExchange(MqConstants.DLX_EXCHANGE, true, false);

        Queue paymentExpireDelayQueue = QueueBuilder.durable(MqConstants.PAYMENT_EXPIRE_DELAY_QUEUE)
                .deadLetterExchange(MqConstants.PAYMENT_EXCHANGE)
                .deadLetterRoutingKey(MqConstants.PAYMENT_EXPIRE_ROUTING_KEY)
                .build();
        Queue paymentExpireQueue = QueueBuilder.durable(MqConstants.PAYMENT_EXPIRE_QUEUE)
                .deadLetterExchange(MqConstants.DLX_EXCHANGE)
                .deadLetterRoutingKey(MqConstants.DLX_ROUTING_KEY)
                .build();
        Queue deadLetterQueue = QueueBuilder.durable(MqConstants.DLX_QUEUE).build();

        Binding paymentExpireDelayBinding = BindingBuilder.bind(paymentExpireDelayQueue)
                .to(paymentExchange)
                .with(MqConstants.PAYMENT_EXPIRE_DELAY_ROUTING_KEY);
        Binding paymentExpireBinding = BindingBuilder.bind(paymentExpireQueue)
                .to(paymentExchange)
                .with(MqConstants.PAYMENT_EXPIRE_ROUTING_KEY);
        Binding deadLetterBinding = BindingBuilder.bind(deadLetterQueue)
                .to(deadLetterExchange)
                .with(MqConstants.DLX_ROUTING_KEY);

        return new Declarables(paymentExchange, deadLetterExchange, paymentExpireDelayQueue,
                paymentExpireQueue, deadLetterQueue, paymentExpireDelayBinding,
                paymentExpireBinding, deadLetterBinding);
    }
}
