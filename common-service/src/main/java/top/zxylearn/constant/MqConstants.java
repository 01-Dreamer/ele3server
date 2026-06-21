package top.zxylearn.constant;

public final class MqConstants {

    // 死信队列
    public static final String DLX_EXCHANGE = "ele.dlx.exchange";
    public static final String DLX_QUEUE = "ele.dlx.queue";
    public static final String DLX_ROUTING_KEY = "ele.dlx";

    // 风控队列
    public static final String RISK_EXCHANGE = "ele.risk.exchange";
    public static final String HTTP_QUEUE = "ele.risk.http.queue";
    public static final String HTTP_ROUTING_KEY = "ele.risk.http";
    public static final String RISK_SCORE_INCREASE_QUEUE = "ele.risk.score.increase.queue";
    public static final String RISK_SCORE_INCREASE_ROUTING_KEY = "ele.risk.score.increase";
    public static final String RISK_TEXT_RECORD_QUEUE = "ele.risk.text.record.queue";
    public static final String RISK_TEXT_RECORD_ROUTING_KEY = "ele.risk.text.record";

    // 店铺ES索引队列
    public static final String SHOP_EXCHANGE = "ele.shop.exchange";
    public static final String SHOP_ES_INDEX_DELAY_QUEUE = "ele.shop.es.index.delay.queue";
    public static final String SHOP_ES_INDEX_QUEUE = "ele.shop.es.index.queue";
    public static final String SHOP_ES_INDEX_DELAY_ROUTING_KEY = "ele.shop.es.index.delay";
    public static final String SHOP_ES_INDEX_ROUTING_KEY = "ele.shop.es.index";

    // 消息服务队列
    public static final String MESSAGE_EXCHANGE = "ele.message.exchange";
    public static final String MESSAGE_PERSIST_QUEUE = "ele.message.persist.queue";
    public static final String MESSAGE_WS_QUEUE_PREFIX = "ele.message.ws.queue.";
    public static final String MESSAGE_WS_ROUTING_KEY = "ele.message.ws";

    // 订单过期队列
    public static final String ORDER_EXCHANGE = "ele.order.exchange";
    public static final String ORDER_EXPIRE_DELAY_QUEUE = "ele.order.expire.delay.queue";
    public static final String ORDER_EXPIRE_QUEUE = "ele.order.expire.queue";
    public static final String ORDER_EXPIRE_DELAY_ROUTING_KEY = "ele.order.expire.delay";
    public static final String ORDER_EXPIRE_ROUTING_KEY = "ele.order.expire";

    // 支付过期队列
    public static final String PAYMENT_EXCHANGE = "ele.payment.exchange";
    public static final String PAYMENT_EXPIRE_DELAY_QUEUE = "ele.payment.expire.delay.queue";
    public static final String PAYMENT_EXPIRE_QUEUE = "ele.payment.expire.queue";
    public static final String PAYMENT_EXPIRE_DELAY_ROUTING_KEY = "ele.payment.expire.delay";
    public static final String PAYMENT_EXPIRE_ROUTING_KEY = "ele.payment.expire";

    private MqConstants() {
    }
}
