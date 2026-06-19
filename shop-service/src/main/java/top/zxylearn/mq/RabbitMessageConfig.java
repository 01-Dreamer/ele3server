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
    public Declarables shopRabbitDeclarables() {
        TopicExchange shopExchange = new TopicExchange(MqConstants.SHOP_EXCHANGE, true, false);
        DirectExchange deadLetterExchange = new DirectExchange(MqConstants.DLX_EXCHANGE, true, false);
        Queue shopEsIndexDelayQueue = QueueBuilder.durable(MqConstants.SHOP_ES_INDEX_DELAY_QUEUE)
                .deadLetterExchange(MqConstants.SHOP_EXCHANGE)
                .deadLetterRoutingKey(MqConstants.SHOP_ES_INDEX_ROUTING_KEY)
                .build();
        Queue shopEsIndexQueue = QueueBuilder.durable(MqConstants.SHOP_ES_INDEX_QUEUE)
                .deadLetterExchange(MqConstants.DLX_EXCHANGE)
                .deadLetterRoutingKey(MqConstants.DLX_ROUTING_KEY)
                .build();
        Queue deadLetterQueue = QueueBuilder.durable(MqConstants.DLX_QUEUE).build();
        Binding shopEsIndexDelayBinding = BindingBuilder.bind(shopEsIndexDelayQueue)
                .to(shopExchange)
                .with(MqConstants.SHOP_ES_INDEX_DELAY_ROUTING_KEY);
        Binding shopEsIndexBinding = BindingBuilder.bind(shopEsIndexQueue)
                .to(shopExchange)
                .with(MqConstants.SHOP_ES_INDEX_ROUTING_KEY);
        Binding deadLetterBinding = BindingBuilder.bind(deadLetterQueue)
                .to(deadLetterExchange)
                .with(MqConstants.DLX_ROUTING_KEY);
        return new Declarables(shopExchange, deadLetterExchange, shopEsIndexDelayQueue, shopEsIndexQueue,
                deadLetterQueue, shopEsIndexDelayBinding, shopEsIndexBinding, deadLetterBinding);
    }
}
