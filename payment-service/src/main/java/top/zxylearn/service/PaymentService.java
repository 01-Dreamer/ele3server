package top.zxylearn.service;

import com.alipay.api.AlipayApiException;
import com.alipay.api.AlipayClient;
import com.alipay.api.AlipayConfig;
import com.alipay.api.DefaultAlipayClient;
import com.alipay.api.internal.util.AlipaySignature;
import com.alipay.api.request.AlipayFundTransUniTransferRequest;
import com.alipay.api.request.AlipayTradeCloseRequest;
import com.alipay.api.request.AlipayTradePrecreateRequest;
import com.alipay.api.request.AlipayTradeRefundRequest;
import com.alipay.api.response.AlipayFundTransUniTransferResponse;
import com.alipay.api.response.AlipayTradeCloseResponse;
import com.alipay.api.response.AlipayTradePrecreateResponse;
import com.alipay.api.response.AlipayTradeRefundResponse;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.conditions.update.LambdaUpdateWrapper;
import com.baomidou.mybatisplus.core.toolkit.IdWorker;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.amqp.rabbit.core.RabbitTemplate;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import top.zxylearn.client.OrderClient;
import top.zxylearn.config.AlipayProperties;
import top.zxylearn.constant.MqConstants;
import top.zxylearn.dto.WalletRechargeRequest;
import top.zxylearn.dto.WalletWithdrawRequest;
import top.zxylearn.dto.order.OrderPaidRequest;
import top.zxylearn.dto.payment.PaymentCloseRequest;
import top.zxylearn.dto.payment.PaymentCreateRequest;
import top.zxylearn.dto.payment.PaymentCreateVO;
import top.zxylearn.dto.payment.PaymentOrderRefundRequest;
import top.zxylearn.dto.payment.PaymentRefundRequest;
import top.zxylearn.dto.payment.PaymentWalletDeductRequest;
import top.zxylearn.entity.Payment;
import top.zxylearn.mapper.PaymentMapper;
import top.zxylearn.vo.PaymentStatusVO;
import top.zxylearn.vo.WalletWithdrawVO;
import top.zxylearn.result.Result;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.LinkedHashMap;
import java.util.Map;

@Service
public class PaymentService {

    private static final String CHANNEL_ALIPAY = "ALIPAY";
    private static final String ALIPAY_TRADE_NO_PREFIX = "ALIPAY";
    private static final String ALIPAY_TRANSFER_PRODUCT_CODE = "TRANS_ACCOUNT_NO_PWD";
    private static final String ALIPAY_TRANSFER_BIZ_SCENE = "DIRECT_TRANSFER";
    private static final String ALIPAY_USER_IDENTITY_TYPE = "ALIPAY_USER_ID";
    private static final int STATUS_PENDING = 0;
    private static final int STATUS_SUCCESS = 1;
    private static final int STATUS_EXPIRED = 2;
    private static final int STATUS_CANCELLED = 3;
    private static final int STATUS_REFUNDED = 4;
    private static final String BUSINESS_TYPE_ORDER = "ORDER";
    private static final String BUSINESS_TYPE_RECHARGE = "RECHARGE";
    private static final String ALIPAY_TRADE_SUCCESS = "TRADE_SUCCESS";
    private static final String ALIPAY_TRADE_FINISHED = "TRADE_FINISHED";
    private static final String ALIPAY_TRADE_CLOSED = "TRADE_CLOSED";
    private static final DateTimeFormatter DATE_TIME_FORMATTER = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss");

    private final PaymentMapper paymentMapper;
    private final PaymentWalletService paymentWalletService;
    private final OrderClient orderClient;
    private final RabbitTemplate rabbitTemplate;
    private final AlipayProperties alipayProperties;
    private final ObjectMapper objectMapper = new ObjectMapper();
    private final Integer rechargeExpireMinutes;

    public PaymentService(PaymentMapper paymentMapper,
                          PaymentWalletService paymentWalletService,
                          OrderClient orderClient,
                          RabbitTemplate rabbitTemplate,
                          AlipayProperties alipayProperties,
                          @Value("${payment.recharge.expire-minutes}") Integer rechargeExpireMinutes) {
        this.paymentMapper = paymentMapper;
        this.paymentWalletService = paymentWalletService;
        this.orderClient = orderClient;
        this.rabbitTemplate = rabbitTemplate;
        this.alipayProperties = alipayProperties;
        this.rechargeExpireMinutes = rechargeExpireMinutes;
    }

    @Transactional(rollbackFor = Exception.class)
    public PaymentCreateVO createAlipayOrder(PaymentCreateRequest request) {
        checkCreateRequest(request);

        Long businessId = parseLongId(request.getBusinessId(), "订单ID");
        Payment pendingPayment = getPendingAlipayPayment(BUSINESS_TYPE_ORDER, businessId);
        if (pendingPayment != null) {
            return new PaymentCreateVO(String.valueOf(pendingPayment.getId()), pendingPayment.getPayUrl(),
                    formatExpireTime(pendingPayment.getExpireTime()));
        }

        Payment payment = buildPendingAlipayPayment(
                request.getSubject().trim(),
                BUSINESS_TYPE_ORDER,
                businessId,
                request.getAmount());
        return createAlipayOrder(payment, request.getExpireMinutes());
    }

    @Transactional(rollbackFor = Exception.class)
    public PaymentCreateVO createRechargeAlipayOrder(String userId, WalletRechargeRequest request) {
        if (request == null) {
            throw new IllegalArgumentException("充值参数不能为空");
        }
        Long rechargeUserId = parseLongId(userId, "用户ID");
        checkAmount(request.getAmount());
        checkExpireMinutes(rechargeExpireMinutes);
        Payment payment = buildPendingAlipayPayment(
                "钱包充值",
                BUSINESS_TYPE_RECHARGE,
                rechargeUserId,
                request.getAmount());
        return createAlipayOrder(payment, rechargeExpireMinutes);
    }

    @Transactional(rollbackFor = Exception.class)
    public PaymentStatusVO getPaymentStatus(String paymentId) {
        Payment payment = getPayment(paymentId);
        return new PaymentStatusVO(
                String.valueOf(payment.getId()),
                payment.getSubject(),
                payment.getBusinessType(),
                payment.getBusinessId() == null ? null : String.valueOf(payment.getBusinessId()),
                formatTradeNo(payment),
                payment.getAmount(),
                payment.getChannel(),
                payment.getStatus()
        );
    }

    @Transactional(rollbackFor = Exception.class)
    public WalletWithdrawVO withdrawToAlipay(String userId, WalletWithdrawRequest request) {
        if (request == null) {
            throw new IllegalArgumentException("提现参数不能为空");
        }
        Long ownerId = parseLongId(userId, "用户ID");
        String alipayUserId = checkRequiredText(request.getAlipayUserId(), 64, "支付宝用户UID");
        checkAmount(request.getAmount());

        String withdrawId = String.valueOf(IdWorker.getId());
        paymentWalletService.deductBalance(new PaymentWalletDeductRequest(String.valueOf(ownerId), request.getAmount()));
        AlipayFundTransUniTransferResponse response = transferToAlipayUser(
                withdrawId, alipayUserId, request.getAmount());
        return new WalletWithdrawVO(
                withdrawId,
                response.getOrderId(),
                response.getPayFundOrderId(),
                request.getAmount(),
                response.getStatus()
        );
    }

    private PaymentCreateVO createAlipayOrder(Payment payment, Integer expireMinutes) {
        String qrCode = createAlipayQrCode(payment, expireMinutes);
        payment.setPayUrl(qrCode);
        payment.setExpireTime(calculateExpireTime(expireMinutes));
        paymentMapper.insert(payment);
        sendPaymentExpireMessage(payment.getId(), expireMinutes);
        return new PaymentCreateVO(String.valueOf(payment.getId()), qrCode, formatExpireTime(payment.getExpireTime()));
    }

    private String formatExpireTime(LocalDateTime expireTime) {
        return expireTime == null ? null : DATE_TIME_FORMATTER.format(expireTime);
    }

    private Payment buildPendingAlipayPayment(String subject,
                                              String businessType,
                                              Long businessId,
                                              BigDecimal amount) {
        Payment payment = new Payment();
        payment.setId(IdWorker.getId());
        payment.setSubject(subject);
        payment.setBusinessType(businessType);
        payment.setBusinessId(businessId);
        payment.setAmount(amount);
        payment.setChannel(CHANNEL_ALIPAY);
        payment.setStatus(STATUS_PENDING);
        return payment;
    }

    @Transactional(rollbackFor = Exception.class)
    public void closeAlipayOrder(PaymentCloseRequest request) {
        if (request == null) {
            throw new IllegalArgumentException("支付ID不能为空");
        }
        Payment payment = getPayment(request.getPaymentId());
        if (!CHANNEL_ALIPAY.equals(payment.getChannel())) {
            throw new IllegalArgumentException("只支持关闭支付宝支付订单");
        }
        if (!Integer.valueOf(STATUS_PENDING).equals(payment.getStatus())) {
            throw new IllegalArgumentException("只有待支付订单可以关闭");
        }
        closeAlipayTrade(payment);
        int updated = paymentMapper.update(null, new LambdaUpdateWrapper<Payment>()
                .set(Payment::getStatus, STATUS_CANCELLED)
                .eq(Payment::getId, payment.getId())
                .eq(Payment::getStatus, STATUS_PENDING));
        if (updated == 0) {
            throw new IllegalArgumentException("支付订单状态已变更");
        }
    }

    @Transactional(rollbackFor = Exception.class)
    public void refundAlipayOrder(PaymentRefundRequest request) {
        if (request == null) {
            throw new IllegalArgumentException("支付ID不能为空");
        }
        Payment payment = getPayment(request.getPaymentId());
        if (!CHANNEL_ALIPAY.equals(payment.getChannel())) {
            throw new IllegalArgumentException("只支持支付宝支付订单退款");
        }
        if (!Integer.valueOf(STATUS_SUCCESS).equals(payment.getStatus())) {
            throw new IllegalArgumentException("只有支付成功的订单可以退款");
        }
        refundAlipayTrade(payment);
        int updated = paymentMapper.update(null, new LambdaUpdateWrapper<Payment>()
                .set(Payment::getStatus, STATUS_REFUNDED)
                .eq(Payment::getId, payment.getId())
                .eq(Payment::getStatus, STATUS_SUCCESS));
        if (updated == 0) {
            throw new IllegalArgumentException("支付订单状态已变更");
        }
    }


    @Transactional(rollbackFor = Exception.class)
    public void refundAlipayOrderByOrderId(PaymentOrderRefundRequest request) {
        if (request == null) {
            throw new IllegalArgumentException("订单ID不能为空");
        }
        Long orderId = parseLongId(request.getOrderId(), "订单ID");
        Payment payment = paymentMapper.selectOne(new LambdaQueryWrapper<Payment>()
                .eq(Payment::getBusinessType, BUSINESS_TYPE_ORDER)
                .eq(Payment::getBusinessId, orderId)
                .eq(Payment::getChannel, CHANNEL_ALIPAY)
                .eq(Payment::getStatus, STATUS_SUCCESS)
                .last("LIMIT 1"));
        if (payment == null) {
            throw new IllegalArgumentException("支付宝已支付订单不存在");
        }
        refundAlipayTrade(payment);
        int updated = paymentMapper.update(null, new LambdaUpdateWrapper<Payment>()
                .set(Payment::getStatus, STATUS_REFUNDED)
                .eq(Payment::getId, payment.getId())
                .eq(Payment::getStatus, STATUS_SUCCESS));
        if (updated == 0) {
            throw new IllegalArgumentException("支付订单状态已变更");
        }
    }

    @Transactional(rollbackFor = Exception.class)
    public void handleAlipayNotify(Map<String, String> params) {
        checkAlipayNotifySignature(params);
        checkNotifyAppId(params);

        String outTradeNo = getRequiredNotifyParam(params, "out_trade_no");
        String tradeStatus = getRequiredNotifyParam(params, "trade_status");
        String tradeNo = params.get("trade_no");
        Payment payment = getPayment(outTradeNo);
        checkNotifyPayment(payment);

        if (hasText(tradeNo)) {
            payment.setTradeNo(tradeNo.trim());
        }

        if (ALIPAY_TRADE_SUCCESS.equals(tradeStatus) || ALIPAY_TRADE_FINISHED.equals(tradeStatus)) {
            checkNotifyAmount(payment, params.get("total_amount"));
            if (Integer.valueOf(STATUS_SUCCESS).equals(payment.getStatus())) {
                paymentMapper.updateById(payment);
                return;
            }
            handlePaymentBusiness(payment);
            payment.setStatus(STATUS_SUCCESS);
            paymentMapper.updateById(payment);
            return;
        }

        if (ALIPAY_TRADE_CLOSED.equals(tradeStatus)) {
            if (!Integer.valueOf(STATUS_SUCCESS).equals(payment.getStatus())) {
                payment.setStatus(STATUS_EXPIRED);
            }
            paymentMapper.updateById(payment);
            return;
        }

        if (hasText(tradeNo)) {
            paymentMapper.updateById(payment);
        }
    }

    @Transactional(rollbackFor = Exception.class)
    public void expirePayment(String paymentId) {
        Payment payment = getPayment(paymentId);
        if (!Integer.valueOf(STATUS_PENDING).equals(payment.getStatus())) {
            return;
        }
        if (payment.getExpireTime() != null && payment.getExpireTime().isAfter(LocalDateTime.now())) {
            sendPaymentExpireMessage(payment.getId(), payment.getExpireTime());
            return;
        }
        paymentMapper.update(null, new LambdaUpdateWrapper<Payment>()
                .set(Payment::getStatus, STATUS_EXPIRED)
                .eq(Payment::getId, payment.getId())
                .eq(Payment::getStatus, STATUS_PENDING));
    }

    private void sendPaymentExpireMessage(Long paymentId, Integer expireMinutes) {
        sendPaymentExpireMessage(paymentId, expireMinutes * 60_000L);
    }

    private void sendPaymentExpireMessage(Long paymentId, LocalDateTime expireTime) {
        long ttlMillis = java.time.Duration.between(LocalDateTime.now(), expireTime).toMillis();
        sendPaymentExpireMessage(paymentId, Math.max(ttlMillis, 1L));
    }

    private void sendPaymentExpireMessage(Long paymentId, long ttlMillis) {
        rabbitTemplate.convertAndSend(
                MqConstants.PAYMENT_EXCHANGE,
                MqConstants.PAYMENT_EXPIRE_DELAY_ROUTING_KEY,
                String.valueOf(paymentId),
                message -> {
                    message.getMessageProperties().setExpiration(String.valueOf(ttlMillis));
                    return message;
                });
    }

    private Payment getPendingAlipayPayment(String businessType, Long businessId) {
        if (businessId == null) {
            return null;
        }
        return paymentMapper.selectOne(new LambdaQueryWrapper<Payment>()
                .eq(Payment::getBusinessType, businessType)
                .eq(Payment::getBusinessId, businessId)
                .eq(Payment::getChannel, CHANNEL_ALIPAY)
                .eq(Payment::getStatus, STATUS_PENDING)
                .isNotNull(Payment::getPayUrl)
                .last("LIMIT 1"));
    }

    private void handlePaymentBusiness(Payment payment) {
        String businessType = payment.getBusinessType();
        if (BUSINESS_TYPE_ORDER.equals(businessType)) {
            if (payment.getBusinessId() == null) {
                throw new IllegalArgumentException("订单业务ID不能为空");
            }
            checkInternalCall(orderClient.markPaid(new OrderPaidRequest(String.valueOf(payment.getBusinessId()))),
                    "订单状态修改失败");
            return;
        }
        if (BUSINESS_TYPE_RECHARGE.equals(businessType)) {
            if (payment.getBusinessId() == null) {
                throw new IllegalArgumentException("充值业务ID不能为空");
            }
            paymentWalletService.addBalance(payment.getBusinessId(), payment.getAmount());
            return;
        }
        System.out.println("支付成功，未知业务类型暂未处理: businessType=" + businessType
                + ", paymentId=" + payment.getId());
    }

    private void checkInternalCall(Result<?> result, String message) {
        if (result == null) {
            throw new RuntimeException(message);
        }
        if (result.getCode() == null || result.getCode() < 200 || result.getCode() >= 300) {
            throw new RuntimeException(result.getMessage() == null ? message : result.getMessage());
        }
    }

    private void checkAlipayNotifySignature(Map<String, String> params) {
        try {
            boolean valid = AlipaySignature.rsaCheckV1(
                    params,
                    alipayProperties.getPublicKey(),
                    alipayProperties.getCharset(),
                    alipayProperties.getSignType());
            if (!valid) {
                throw new IllegalArgumentException("支付宝回调验签失败");
            }
        } catch (AlipayApiException ex) {
            throw new IllegalArgumentException("支付宝回调验签失败", ex);
        }
    }

    private void checkNotifyAppId(Map<String, String> params) {
        String appId = getRequiredNotifyParam(params, "app_id");
        if (!appId.equals(alipayProperties.getAppId())) {
            throw new IllegalArgumentException("支付宝回调app_id不匹配");
        }
    }

    private void checkNotifyPayment(Payment payment) {
        if (!CHANNEL_ALIPAY.equals(payment.getChannel())) {
            throw new IllegalArgumentException("支付宝回调支付渠道不匹配");
        }
    }

    private void checkNotifyAmount(Payment payment, String totalAmount) {
        if (!hasText(totalAmount)) {
            throw new IllegalArgumentException("支付宝回调金额不能为空");
        }
        BigDecimal notifyAmount;
        try {
            notifyAmount = new BigDecimal(totalAmount.trim());
        } catch (NumberFormatException ex) {
            throw new IllegalArgumentException("支付宝回调金额格式不正确");
        }
        if (notifyAmount.compareTo(payment.getAmount()) != 0) {
            throw new IllegalArgumentException("支付宝回调金额与支付订单金额不一致");
        }
    }

    private String getRequiredNotifyParam(Map<String, String> params, String name) {
        if (params == null || !hasText(params.get(name))) {
            throw new IllegalArgumentException("支付宝回调参数缺失: " + name);
        }
        return params.get(name).trim();
    }

    private AlipayFundTransUniTransferResponse transferToAlipayUser(String withdrawId, String alipayUserId, BigDecimal amount) {
        try {
            AlipayClient alipayClient = new DefaultAlipayClient(buildAlipayConfig());
            AlipayFundTransUniTransferRequest request = new AlipayFundTransUniTransferRequest();
            Map<String, Object> payeeInfo = new LinkedHashMap<>();
            payeeInfo.put("identity", alipayUserId);
            payeeInfo.put("identity_type", ALIPAY_USER_IDENTITY_TYPE);

            Map<String, Object> bizContent = new LinkedHashMap<>();
            bizContent.put("out_biz_no", withdrawId);
            bizContent.put("trans_amount", amount.toPlainString());
            bizContent.put("product_code", ALIPAY_TRANSFER_PRODUCT_CODE);
            bizContent.put("biz_scene", ALIPAY_TRANSFER_BIZ_SCENE);
            bizContent.put("order_title", "钱包提现");
            bizContent.put("payee_info", payeeInfo);
            request.setBizContent(toJson(bizContent));

            AlipayFundTransUniTransferResponse response = alipayClient.execute(request);
            if (response.isSuccess()) {
                return response;
            }
            String message = hasText(response.getSubMsg()) ? response.getSubMsg() : response.getMsg();
            throw new RuntimeException("支付宝提现转账失败：" + message);
        } catch (AlipayApiException ex) {
            throw new RuntimeException("支付宝提现转账失败", ex);
        }
    }

    private void closeAlipayTrade(Payment payment) {
        try {
            AlipayClient alipayClient = new DefaultAlipayClient(buildAlipayConfig());
            AlipayTradeCloseRequest request = new AlipayTradeCloseRequest();
            Map<String, Object> bizContent = new LinkedHashMap<>();
            bizContent.put("out_trade_no", String.valueOf(payment.getId()));
            request.setBizContent(toJson(bizContent));
            AlipayTradeCloseResponse response = alipayClient.execute(request);
            if (response.isSuccess()) {
                return;
            }
            String message = hasText(response.getSubMsg()) ? response.getSubMsg() : response.getMsg();
            throw new RuntimeException("支付宝支付订单关闭失败：" + message);
        } catch (AlipayApiException ex) {
            throw new RuntimeException("支付宝支付订单关闭失败", ex);
        }
    }

    private void refundAlipayTrade(Payment payment) {
        try {
            AlipayClient alipayClient = new DefaultAlipayClient(buildAlipayConfig());
            AlipayTradeRefundRequest request = new AlipayTradeRefundRequest();
            Map<String, Object> bizContent = new LinkedHashMap<>();
            bizContent.put("out_trade_no", String.valueOf(payment.getId()));
            bizContent.put("refund_amount", payment.getAmount().toPlainString());
            request.setBizContent(toJson(bizContent));
            AlipayTradeRefundResponse response = alipayClient.execute(request);
            if (response.isSuccess()) {
                return;
            }
            String message = hasText(response.getSubMsg()) ? response.getSubMsg() : response.getMsg();
            throw new RuntimeException("支付宝支付订单退款失败：" + message);
        } catch (AlipayApiException ex) {
            throw new RuntimeException("支付宝支付订单退款失败", ex);
        }
    }

    private String createAlipayQrCode(Payment payment, Integer expireMinutes) {
        try {
            AlipayClient alipayClient = new DefaultAlipayClient(buildAlipayConfig());
            AlipayTradePrecreateRequest request = new AlipayTradePrecreateRequest();
            request.setNotifyUrl(alipayProperties.getNotifyUrl());
            request.setBizContent(buildPrecreateBizContent(payment, expireMinutes));
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

    private String buildPrecreateBizContent(Payment payment, Integer expireMinutes) {
        Map<String, Object> bizContent = new LinkedHashMap<>();
        bizContent.put("out_trade_no", String.valueOf(payment.getId()));
        bizContent.put("total_amount", payment.getAmount().toPlainString());
        bizContent.put("subject", payment.getSubject());
        bizContent.put("timeout_express", expireMinutes + "m");
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
        parseLongId(request.getBusinessId(), "订单ID");
        checkAmount(request.getAmount());
        checkExpireMinutes(request.getExpireMinutes());
    }

    private String checkRequiredText(String value, int maxLength, String fieldName) {
        if (!hasText(value)) {
            throw new IllegalArgumentException(fieldName + "不能为空");
        }
        String trimmed = value.trim();
        if (trimmed.length() > maxLength) {
            throw new IllegalArgumentException(fieldName + "长度不能超过" + maxLength + "个字符");
        }
        return trimmed;
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


    private Long parseNullableLongId(String value, String fieldName) {
        if (!hasText(value)) {
            return null;
        }
        return parseLongId(value, fieldName);
    }

    private void checkExpireMinutes(Integer expireMinutes) {
        if (expireMinutes == null) {
            throw new IllegalArgumentException("支付过期时间不能为空");
        }
        if (expireMinutes <= 0) {
            throw new IllegalArgumentException("支付过期时间必须大于0分钟");
        }
    }

    private LocalDateTime calculateExpireTime(Integer expireMinutes) {
        return LocalDateTime.now().plusMinutes(expireMinutes);
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
