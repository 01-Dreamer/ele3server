package top.zxylearn.config;

import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;

@Data
@Component
@ConfigurationProperties(prefix = "payment.alipay")
public class AlipayProperties {

    private String gatewayUrl;

    private String format = "json";

    private String charset = "UTF-8";

    private String signType = "RSA2";

    private String appId;

    private String privateKey;

    private String publicKey;

    private String notifyUrl;
}
