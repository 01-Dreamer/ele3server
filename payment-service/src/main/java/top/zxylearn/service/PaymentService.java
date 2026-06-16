package top.zxylearn.service;

import com.alipay.api.AlipayApiException;
import com.alipay.api.AlipayClient;
import com.alipay.api.AlipayConfig;
import com.alipay.api.DefaultAlipayClient;
import com.alipay.api.request.AlipayTradePrecreateRequest;
import com.alipay.api.response.AlipayTradePrecreateResponse;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import top.zxylearn.config.AlipayProperties;
import top.zxylearn.dto.payment.PaymentCreateRequest;
import top.zxylearn.dto.payment.PaymentCreateVO;
import top.zxylearn.entity.Payment;
import top.zxylearn.mapper.PaymentMapper;
import top.zxylearn.vo.PaymentStatusVO;

import java.math.BigDecimal;
import java.util.LinkedHashMap;
import java.util.Map;

@Service
public class PaymentService {

    private static final String CHANNEL_ALIPAY = "ALIPAY";
    private static final String ALIPAY_TRADE_NO_PREFIX = "ALIPAY";
    private static final int STATUS_PENDING = 0;

    private final PaymentMapper paymentMapper;
    private final AlipayProperties alipayProperties;
    private final ObjectMapper objectMapper = new ObjectMapper();

    public PaymentService(PaymentMapper paymentMapper, AlipayProperties alipayProperties) {
        this.paymentMapper = paymentMapper;
        this.alipayProperties = alipayProperties;
    }

    @Transactional(rollbackFor = Exception.class)
    public PaymentCreateVO createAlipayOrder(PaymentCreateRequest request) {
        checkCreateRequest(request);

        Payment payment = new Payment();
        payment.setSubject(request.getSubject().trim());
        payment.setBusinessType(request.getBusinessType().trim());
        payment.setBusinessId(parseLongId(request.getBusinessId(), "业务ID"));
        payment.setAmount(request.getAmount());
        payment.setChannel(CHANNEL_ALIPAY);
        payment.setStatus(STATUS_PENDING);
        payment.setTradeNo("");
        paymentMapper.insert(payment);

        String qrCode = createAlipayQrCode(payment);
        return new PaymentCreateVO(String.valueOf(payment.getId()), qrCode);
    }

    @Transactional(rollbackFor = Exception.class)
    public PaymentStatusVO getPaymentStatus(String paymentId) {
        Payment payment = getPayment(paymentId);
        return new PaymentStatusVO(
                String.valueOf(payment.getId()),
                payment.getSubject(),
                payment.getBusinessType(),
                String.valueOf(payment.getBusinessId()),
                formatTradeNo(payment),
                payment.getAmount(),
                payment.getChannel(),
                payment.getStatus()
        );
    }

    private String createAlipayQrCode(Payment payment) {
        try {
            AlipayClient alipayClient = new DefaultAlipayClient(buildAlipayConfig());
            AlipayTradePrecreateRequest request = new AlipayTradePrecreateRequest();
            request.setNotifyUrl(alipayProperties.getNotifyUrl());
            request.setBizContent(buildPrecreateBizContent(payment));
            AlipayTradePrecreateResponse response = alipayClient.execute(request);
            if (response.isSuccess() && hasText(response.getQrCode())) {
                return response.getQrCode();
            }
            String message = hasText(response.getSubMsg()) ? response.getSubMsg() : response.getMsg();
            throw new RuntimeException("支付宝支付订单创建失败：" + message);
        } catch (AlipayApiException ex) {
            throw new RuntimeException("支付宝支付订单创建失败", ex);
        }
    }

    private AlipayConfig buildAlipayConfig() {
        AlipayConfig config = new AlipayConfig();
        config.setServerUrl(alipayProperties.getGatewayUrl());
        config.setAppId(alipayProperties.getAppId());
        config.setPrivateKey(alipayProperties.getPrivateKey());
        config.setFormat(alipayProperties.getFormat());
        config.setCharset(alipayProperties.getCharset());
        config.setAlipayPublicKey(alipayProperties.getPublicKey());
        config.setSignType(alipayProperties.getSignType());
        return config;
    }

    private String buildPrecreateBizContent(Payment payment) {
        Map<String, Object> bizContent = new LinkedHashMap<>();
        bizContent.put("out_trade_no", String.valueOf(payment.getId()));
        bizContent.put("total_amount", payment.getAmount().toPlainString());
        bizContent.put("subject", payment.getSubject());
        return toJson(bizContent);
    }

    private String toJson(Map<String, Object> value) {
        try {
            return objectMapper.writeValueAsString(value);
        } catch (JsonProcessingException ex) {
            throw new RuntimeException("支付宝支付参数序列化失败", ex);
        }
    }

    private Payment getPayment(String paymentId) {
        Long id = parseLongId(paymentId, "支付ID");
        Payment payment = paymentMapper.selectById(id);
        if (payment == null) {
            throw new IllegalArgumentException("支付订单不存在");
        }
        return payment;
    }

    private void checkCreateRequest(PaymentCreateRequest request) {
        if (request == null) {
            throw new IllegalArgumentException("支付参数不能为空");
        }
        if (!hasText(request.getSubject())) {
            throw new IllegalArgumentException("支付标题不能为空");
        }
        if (!hasText(request.getBusinessType())) {
            throw new IllegalArgumentException("业务类型不能为空");
        }
        parseLongId(request.getBusinessId(), "业务ID");
        checkAmount(request.getAmount());
    }

    private void checkAmount(BigDecimal amount) {
        if (amount == null) {
            throw new IllegalArgumentException("支付金额不能为空");
        }
        if (amount.compareTo(BigDecimal.ZERO) <= 0) {
            throw new IllegalArgumentException("支付金额必须大于0");
        }
        if (amount.stripTrailingZeros().scale() > 2) {
            throw new IllegalArgumentException("支付金额最多只能保留两位小数");
        }
    }

    private Long parseLongId(String value, String fieldName) {
        if (!hasText(value)) {
            throw new IllegalArgumentException(fieldName + "不能为空");
        }
        try {
            return Long.valueOf(value.trim());
        } catch (NumberFormatException ex) {
            throw new IllegalArgumentException(fieldName + "格式不正确");
        }
    }

    private String formatTradeNo(Payment payment) {
        if (!CHANNEL_ALIPAY.equals(payment.getChannel()) || !hasText(payment.getTradeNo())) {
            return payment.getTradeNo();
        }
        return ALIPAY_TRADE_NO_PREFIX + payment.getTradeNo();
    }

    private boolean hasText(String value) {
        return value != null && !value.isBlank();
    }
}
