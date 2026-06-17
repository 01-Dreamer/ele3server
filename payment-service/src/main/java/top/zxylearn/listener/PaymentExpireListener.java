package top.zxylearn.listener;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.amqp.rabbit.annotation.RabbitListener;
import org.springframework.stereotype.Component;
import top.zxylearn.constant.MqConstants;
import top.zxylearn.service.PaymentService;

@Component
public class PaymentExpireListener {

    private static final Logger log = LoggerFactory.getLogger(PaymentExpireListener.class);

    private final PaymentService paymentService;

    public PaymentExpireListener(PaymentService paymentService) {
        this.paymentService = paymentService;
    }

    @RabbitListener(queues = MqConstants.PAYMENT_EXPIRE_QUEUE)
    public void listen(String paymentId) {
        log.info("收到支付过期消息: paymentId={}", paymentId);
        paymentService.expirePayment(paymentId);
    }
}
