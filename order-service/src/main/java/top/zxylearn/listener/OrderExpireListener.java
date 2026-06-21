package top.zxylearn.listener;

import org.springframework.amqp.rabbit.annotation.RabbitListener;
import org.springframework.stereotype.Component;
import top.zxylearn.constant.MqConstants;
import top.zxylearn.service.OrderService;

@Component
public class OrderExpireListener {
    private final OrderService orderService;
    public OrderExpireListener(OrderService orderService) { this.orderService = orderService; }
    @RabbitListener(queues = MqConstants.ORDER_EXPIRE_QUEUE)
    public void onOrderExpire(String orderId) {
        orderService.expireOrder(orderId);
    }
}
