package top.zxylearn.constant;

public final class MqConstants {

    // 死信队列
    public static final String DLX_EXCHANGE = "ele.dlx.exchange";
    public static final String DLX_QUEUE = "ele.dlx.queue";
    public static final String DLX_ROUTING_KEY = "ele.dlx";

    // HTTP风控队列
    public static final String RISK_EXCHANGE = "ele.risk.exchange";
    public static final String HTTP_QUEUE = "ele.risk.http.queue";
    public static final String HTTP_ROUTING_KEY = "ele.risk.http";

    // 支付过期队列
    public static final String PAYMENT_EXCHANGE = "ele.payment.exchange";
    public static final String PAYMENT_EXPIRE_DELAY_QUEUE = "ele.payment.expire.delay.queue";
    public static final String PAYMENT_EXPIRE_QUEUE = "ele.payment.expire.queue";
    public static final String PAYMENT_EXPIRE_DELAY_ROUTING_KEY = "ele.payment.expire.delay";
    public static final String PAYMENT_EXPIRE_ROUTING_KEY = "ele.payment.expire";

    private MqConstants() {
    }
}
