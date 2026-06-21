package top.zxylearn.mq;

import org.springframework.amqp.core.Binding;
import org.springframework.amqp.core.BindingBuilder;
import org.springframework.amqp.core.Declarables;
import org.springframework.amqp.core.DirectExchange;
import org.springframework.amqp.core.Queue;
import org.springframework.amqp.core.QueueBuilder;
import org.springframework.amqp.core.TopicExchange;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import top.zxylearn.constant.MqConstants;

@Configuration
public class RabbitMessageConfig {

    @Bean
    public Declarables fileRabbitDeclarables() {
        TopicExchange fileExchange = new TopicExchange(MqConstants.FILE_EXCHANGE, true, false);
        DirectExchange deadLetterExchange = new DirectExchange(MqConstants.DLX_EXCHANGE, true, false);
        Queue imageDeleteQueue = QueueBuilder.durable(MqConstants.FILE_IMAGE_DELETE_QUEUE)
                .deadLetterExchange(MqConstants.DLX_EXCHANGE)
                .deadLetterRoutingKey(MqConstants.DLX_ROUTING_KEY)
                .build();
        Queue deadLetterQueue = QueueBuilder.durable(MqConstants.DLX_QUEUE).build();
        Binding imageDeleteBinding = BindingBuilder.bind(imageDeleteQueue)
                .to(fileExchange)
                .with(MqConstants.FILE_IMAGE_DELETE_ROUTING_KEY);
        Binding deadLetterBinding = BindingBuilder.bind(deadLetterQueue)
                .to(deadLetterExchange)
                .with(MqConstants.DLX_ROUTING_KEY);
        return new Declarables(fileExchange, deadLetterExchange, imageDeleteQueue, deadLetterQueue,
                imageDeleteBinding, deadLetterBinding);
    }
}
