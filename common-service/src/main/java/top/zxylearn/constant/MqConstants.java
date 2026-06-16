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

    private MqConstants() {
    }
}
