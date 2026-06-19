package top.zxylearn.listener;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.amqp.rabbit.annotation.RabbitListener;
import org.springframework.stereotype.Component;
import top.zxylearn.constant.MqConstants;
import top.zxylearn.dto.shop.ShopEsIndexEventDTO;
import top.zxylearn.service.ShopEsIndexService;

@Component
public class ShopEsIndexListener {

    private static final Logger log = LoggerFactory.getLogger(ShopEsIndexListener.class);

    private final ShopEsIndexService shopEsIndexService;

    public ShopEsIndexListener(ShopEsIndexService shopEsIndexService) {
        this.shopEsIndexService = shopEsIndexService;
    }

    @RabbitListener(queues = MqConstants.SHOP_ES_INDEX_QUEUE)
    public void listen(ShopEsIndexEventDTO event) {
        log.info("收到店铺ES索引消息: {}", event);
        shopEsIndexService.sync(event);
    }
}
