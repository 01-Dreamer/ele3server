package top.zxylearn.mq;

import org.springframework.amqp.core.AnonymousQueue;
import org.springframework.amqp.core.Binding;
import org.springframework.amqp.core.BindingBuilder;
import org.springframework.amqp.core.Declarables;
import org.springframework.amqp.core.DirectExchange;
import org.springframework.amqp.core.TopicExchange;
import org.springframework.amqp.core.Queue;
import org.springframework.amqp.core.QueueBuilder;
import org.springframework.amqp.support.converter.JacksonJsonMessageConverter;
import org.springframework.amqp.support.converter.MessageConverter;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import top.zxylearn.constant.MqConstants;

import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

@Configuration
public class RabbitMessageConfig {

    @Bean
    public MessageConverter jacksonJsonMessageConverter() {
        return new JacksonJsonMessageConverter();
    }

    @Bean
    public Queue webSocketBroadcastQueue() {
        Map<String, Object> arguments = new HashMap<>();
        arguments.put("x-dead-letter-exchange", MqConstants.DLX_EXCHANGE);
        arguments.put("x-dead-letter-routing-key", MqConstants.DLX_ROUTING_KEY);
        return new AnonymousQueue(() -> MqConstants.MESSAGE_WS_QUEUE_PREFIX + UUID.randomUUID(), arguments);
    }

    @Bean
    public Declarables webSocketMessageDeclarables(Queue webSocketBroadcastQueue) {
        TopicExchange messageExchange = new TopicExchange(MqConstants.MESSAGE_EXCHANGE, true, false);
        DirectExchange deadLetterExchange = new DirectExchange(MqConstants.DLX_EXCHANGE, true, false);
        Queue chatPersistQueue = QueueBuilder.durable(MqConstants.MESSAGE_CHAT_PERSIST_QUEUE)
                .deadLetterExchange(MqConstants.DLX_EXCHANGE)
                .deadLetterRoutingKey(MqConstants.DLX_ROUTING_KEY)
                .build();
        Queue deadLetterQueue = QueueBuilder.durable(MqConstants.DLX_QUEUE).build();
        Binding chatPersistBinding = BindingBuilder.bind(chatPersistQueue)
                .to(messageExchange)
                .with(MqConstants.MESSAGE_CHAT_PERSIST_ROUTING_KEY);
        Binding messageBinding = BindingBuilder.bind(webSocketBroadcastQueue)
                .to(messageExchange)
                .with(MqConstants.MESSAGE_WS_ROUTING_KEY);
        Binding deadLetterBinding = BindingBuilder.bind(deadLetterQueue)
                .to(deadLetterExchange)
                .with(MqConstants.DLX_ROUTING_KEY);
        return new Declarables(messageExchange, deadLetterExchange,
                chatPersistQueue, deadLetterQueue, webSocketBroadcastQueue,
                chatPersistBinding, messageBinding, deadLetterBinding);
    }
}
