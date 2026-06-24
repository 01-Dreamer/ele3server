package top.zxylearn.service;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.conditions.update.LambdaUpdateWrapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;

import lombok.extern.slf4j.Slf4j;

import org.apache.seata.spring.annotation.GlobalTransactional;
import org.springframework.amqp.rabbit.core.RabbitTemplate;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import top.zxylearn.client.PaymentClient;
import top.zxylearn.client.ShopClient;
import top.zxylearn.constant.MqConstants;
import top.zxylearn.dto.OrderCreateRequest;
import top.zxylearn.dto.OrderReviewRequest;
import top.zxylearn.dto.order.OrderPaidRequest;
import top.zxylearn.dto.payment.PaymentCreateRequest;
import top.zxylearn.dto.payment.PaymentCreateVO;
import top.zxylearn.dto.payment.PaymentOrderRefundRequest;
import top.zxylearn.dto.payment.PaymentWalletAddRequest;
import top.zxylearn.dto.payment.PaymentWalletDeductRequest;
import top.zxylearn.dto.shop.ShopBillCreateRequest;
import top.zxylearn.dto.shop.ShopBillVO;
import top.zxylearn.dto.shop.ShopReviewCreateRequest;
import top.zxylearn.dto.shop.ShopSalesIncreaseRequest;
import top.zxylearn.entity.Order;
import top.zxylearn.entity.OrderItem;
import top.zxylearn.mapper.OrderItemMapper;
import top.zxylearn.mapper.OrderMapper;
import top.zxylearn.result.Result;
import top.zxylearn.dto.message.WebSocketMessageDTO;
import top.zxylearn.vo.OrderItemVO;
import top.zxylearn.vo.OrderVO;
import top.zxylearn.vo.PageVO;

import java.math.BigDecimal;
import java.time.Duration;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;
import java.util.UUID;

@Slf4j
@Service
public class OrderService {

    private static final int STATUS_PENDING_PAYMENT = 0;
    private static final int STATUS_WAIT_ACCEPT = 1;
    private static final int STATUS_WAIT_DELIVERY = 2;
    private static final int STATUS_WAIT_ARRIVE = 3;
    private static final int STATUS_WAIT_REVIEW = 4;
    private static final int STATUS_FINISHED = 5;
    private static final int STATUS_EXPIRED = 6;
    private static final int STATUS_CANCELLED = 7;

    private static final String CREATE_TOKEN_KEY_PREFIX = "order:token:";
    private static final int CREATE_TOKEN_TTL_SECONDS = 1800;

    private final OrderMapper orderMapper;
    private final OrderItemMapper orderItemMapper;
    private final ShopClient shopClient;
    private final PaymentClient paymentClient;
    private final RabbitTemplate rabbitTemplate;
    private final StringRedisTemplate stringRedisTemplate;
    private final int expireMinutes;
    private final int mqGraceMinutes;

    public OrderService(OrderMapper orderMapper,
                        OrderItemMapper orderItemMapper,
                        ShopClient shopClient,
                        PaymentClient paymentClient,
                        RabbitTemplate rabbitTemplate,
                        StringRedisTemplate stringRedisTemplate,
                        @Value("${order.expire.minutes}") int expireMinutes,
                        @Value("${order.expire.mq-grace-minutes}") int mqGraceMinutes) {
        this.orderMapper = orderMapper;
        this.orderItemMapper = orderItemMapper;
        this.shopClient = shopClient;
        this.paymentClient = paymentClient;
        this.rabbitTemplate = rabbitTemplate;
        this.stringRedisTemplate = stringRedisTemplate;
        this.expireMinutes = expireMinutes;
        this.mqGraceMinutes = mqGraceMinutes;
    }

    public String createOrderToken(String userId) {
        if (!hasText(userId)) {
            throw new IllegalArgumentException("用户ID不能为空");
        }
        String token = UUID.randomUUID().toString().replace("-", "");
        stringRedisTemplate.opsForValue()
                .set(buildCreateTokenKey(userId.trim(), token), "1", Duration.ofSeconds(CREATE_TOKEN_TTL_SECONDS));
        return token;
    }

    @Transactional(rollbackFor = Exception.class)
    public OrderVO createOrder(String userId, OrderCreateRequest request) {
        if (request == null) {
            throw new IllegalArgumentException("订单参数不能为空");
        }
        if (!hasText(request.getToken())) {
            throw new IllegalArgumentException("下单token不能为空，请先调用 /create-order-token 获取");
        }
        Long buyerId = parseLongId(userId, "用户ID");
        String tokenKey = buildCreateTokenKey(userId, request.getToken().trim());
        if (!Boolean.TRUE.equals(stringRedisTemplate.delete(tokenKey))) {
            throw new IllegalArgumentException("下单token已失效，请重新获取");
        }
        Long shopId = parseLongId(request.getShopId(), "店铺ID");
        List<OrderCreateRequest.ItemEntry> requestItems = request.getItems();
        if (requestItems == null || requestItems.isEmpty()) {
            throw new IllegalArgumentException("订单商品不能为空");
        }

        ShopBillVO bill = callData(shopClient.createBill(new ShopBillCreateRequest(
                String.valueOf(shopId),
                requestItems.stream()
                        .map(item -> new ShopBillCreateRequest.ItemEntry(item.getShopItemId(), item.getQuantity()))
                        .toList()
        )), "账单创建失败");

        Order order = new Order();
        order.setUserId(buyerId);
        order.setShopId(shopId);
        order.setShopOwnerId(parseLongId(bill.getShopOwnerId(), "店主用户ID"));
        order.setShopName(checkText(bill.getShopName(), "店铺名称"));
        order.setReceiverName(checkText(request.getReceiverName(), "收货人姓名"));
        order.setReceiverPhone(checkText(request.getReceiverPhone(), "收货人手机号"));
        order.setReceiverAddress(checkText(request.getReceiverAddress(), "收货地址"));
        order.setReceiverLongitude(request.getReceiverLongitude());
        order.setReceiverLatitude(request.getReceiverLatitude());
        order.setRemark(normalizeOptionalText(request.getRemark()));
        order.setDeliveryFee(bill.getDeliveryFee() == null ? BigDecimal.ZERO : bill.getDeliveryFee());
        order.setAmount(checkAmount(bill.getTotalAmount(), "订单金额"));
        order.setStatus(STATUS_PENDING_PAYMENT);
        order.setExpireTime(LocalDateTime.now().plusMinutes(expireMinutes));
        orderMapper.insert(order);

        for (ShopBillVO.ItemEntry item : bill.getItems()) {
            OrderItem orderItem = new OrderItem();
            orderItem.setOrderId(order.getId());
            orderItem.setName(checkText(item.getItemName(), "商品名称"));
            orderItem.setPrice(checkAmount(item.getUnitPrice(), "商品单价"));
            orderItem.setQuantity(item.getQuantity());
            orderItem.setAmount(checkAmount(item.getSubtotal(), "商品小计"));
            orderItemMapper.insert(orderItem);
        }

        sendOrderExpireMessage(order.getId());
        return toOrderVO(orderMapper.selectById(order.getId()), selectOrderItems(order.getId()));
    }

    @Transactional(rollbackFor = Exception.class)
    public PaymentCreateVO payByAlipay(String userId, String orderId) {
        Order order = getOwnOrder(userId, orderId);
        checkStatus(order, STATUS_PENDING_PAYMENT, "只有待支付订单可以发起支付");
        if (order.getExpireTime() != null && order.getExpireTime().isBefore(LocalDateTime.now())) {
            expireOrder(String.valueOf(order.getId()));
            throw new IllegalArgumentException("订单已过期");
        }
        long remainMillis = Duration.between(LocalDateTime.now(), order.getExpireTime()).toMillis();
        long minutes = Math.max((remainMillis + 59_999L) / 60_000L, 1L);
        return callData(paymentClient.createAlipayOrder(new PaymentCreateRequest(
                "订单支付",
                String.valueOf(order.getId()),
                order.getAmount(),
                Math.toIntExact(minutes)
        )), "支付宝支付订单创建失败");
    }

    @GlobalTransactional(name = "order-wallet-pay", rollbackFor = Exception.class)
    public void payByWallet(String userId, String orderId) {
        Order order = getOwnOrder(userId, orderId);
        checkStatus(order, STATUS_PENDING_PAYMENT, "只有待支付订单可以支付");
        if (order.getExpireTime() != null && order.getExpireTime().isBefore(LocalDateTime.now())) {
            expireOrder(String.valueOf(order.getId()));
            throw new IllegalArgumentException("订单已过期");
        }
        call(paymentClient.closeAlipayOrderByOrderId(orderId), "支付订单关闭失败");
        call(paymentClient.deductBalance(new PaymentWalletDeductRequest(String.valueOf(order.getUserId()), order.getAmount())), "钱包扣款失败");
        updateStatus(order.getId(), STATUS_PENDING_PAYMENT, STATUS_WAIT_ACCEPT, "订单状态已变更");
        sendNotice(String.valueOf(order.getShopOwnerId()), "新订单", "您有新的订单「" + order.getShopName() + "」待接单");
    }

    @GlobalTransactional(name = "cancel-order", rollbackFor = Exception.class)
    public void cancelOrder(String userId, String orderId) {
        Order order = getOwnOrder(userId, orderId);
        call(paymentClient.closeAlipayOrderByOrderId(orderId), "支付订单关闭失败");
        updateStatus(order.getId(), STATUS_PENDING_PAYMENT, STATUS_CANCELLED, "只有待支付订单可以取消");
    }

    @GlobalTransactional(name = "merchant-accept-order", rollbackFor = Exception.class)
    public void merchantAccept(String merchantId, String orderId) {
        Order order = getOrder(orderId);
        checkShopOwner(merchantId, order);
        updateStatus(order.getId(), STATUS_WAIT_ACCEPT, STATUS_WAIT_DELIVERY, "只有待接单订单可以接单");
        sendNotice(String.valueOf(order.getUserId()), "商家已接单", "您的订单「" + order.getShopName() + "」已被商家接单，正在准备中");
        BigDecimal merchantAmount = order.getAmount().subtract(order.getDeliveryFee() == null ? BigDecimal.ZERO : order.getDeliveryFee());
        if (merchantAmount.compareTo(BigDecimal.ZERO) > 0) {
            call(paymentClient.addBalance(new PaymentWalletAddRequest(merchantId, merchantAmount)), "商家钱包加款失败");
        }
    }

    @GlobalTransactional(name = "merchant-reject-order", rollbackFor = Exception.class)
    public void merchantReject(String merchantId, String orderId) {
        Order order = getOrder(orderId);
        checkShopOwner(merchantId, order);
        updateStatus(order.getId(), STATUS_WAIT_ACCEPT, STATUS_CANCELLED, "只有待接单订单可以拒单");
        sendNotice(String.valueOf(order.getUserId()), "商家已拒单", "您的订单「" + order.getShopName() + "」已被商家拒单");
        refundBuyer(order);
    }

    @Transactional(rollbackFor = Exception.class)
    public void riderAccept(String riderId, String orderId) {
        Long normalizedRiderId = parseLongId(riderId, "骑手ID");
        Long normalizedOrderId = parseLongId(orderId, "订单ID");
        int updated = orderMapper.update(null, new LambdaUpdateWrapper<Order>()
                .set(Order::getStatus, STATUS_WAIT_ARRIVE)
                .set(Order::getRiderId, normalizedRiderId)
                .eq(Order::getId, normalizedOrderId)
                .eq(Order::getStatus, STATUS_WAIT_DELIVERY));
        if (updated <= 0) {
            throw new IllegalArgumentException("只有待配送订单可以由骑手接单");
        }
        Order order = getOrder(String.valueOf(normalizedOrderId));
        sendNotice(String.valueOf(order.getUserId()), "骑手已接单", "您的订单「" + order.getShopName() + "」骑手已接单，正在赶来");
    }

    @GlobalTransactional(name = "rider-arrive-order", rollbackFor = Exception.class)
    public void riderArrive(String riderId, String orderId) {
        Long normalizedRiderId = parseLongId(riderId, "骑手ID");
        Order order = getOrder(orderId);
        if (!normalizedRiderId.equals(order.getRiderId())) {
            throw new IllegalArgumentException("只能完成自己接单的订单");
        }
        updateStatus(order.getId(), STATUS_WAIT_ARRIVE, STATUS_WAIT_REVIEW, "只有待送达订单可以确认送达");
        sendNotice(String.valueOf(order.getUserId()), "订单已送达", "您的订单「" + order.getShopName() + "」已送达，请评价");
        BigDecimal deliveryFee = order.getDeliveryFee() == null ? BigDecimal.ZERO : order.getDeliveryFee();
        if (deliveryFee.compareTo(BigDecimal.ZERO) > 0) {
            call(paymentClient.addBalance(new PaymentWalletAddRequest(String.valueOf(normalizedRiderId), deliveryFee)), "骑手钱包加款失败");
        }
    }

    @GlobalTransactional(name = "order-review", rollbackFor = Exception.class)
    public void reviewOrder(String userId, String orderId, OrderReviewRequest request) {
        if (request == null) {
            throw new IllegalArgumentException("评价参数不能为空");
        }
        Order order = getOwnOrder(userId, orderId);
        checkStatus(order, STATUS_WAIT_REVIEW, "只有待评价订单可以评价");
        call(shopClient.createReview(new ShopReviewCreateRequest(
                String.valueOf(order.getId()),
                String.valueOf(order.getUserId()),
                String.valueOf(order.getShopId()),
                request.getScore(),
                request.getContent(),
                request.getImages()
        )), "店铺评价创建失败");
        call(shopClient.increaseSales(new ShopSalesIncreaseRequest(String.valueOf(order.getShopId()), sumQuantity(order.getId()))), "店铺销量更新失败");
        updateStatus(order.getId(), STATUS_WAIT_REVIEW, STATUS_FINISHED, "订单状态已变更");
        sendNotice(String.valueOf(order.getShopOwnerId()), "新评价", "您的店铺「" + order.getShopName() + "」收到一条新评价");
    }

    @Transactional(rollbackFor = Exception.class)
    public OrderVO getOrderDetail(String userId, String orderId) {
        Order order = getOwnOrder(userId, orderId);
        return toOrderVO(order, selectOrderItems(order.getId()));
    }

    public void markPaid(OrderPaidRequest request) {
        if (request == null) {
            throw new IllegalArgumentException("订单ID不能为空");
        }
        Long orderId = parseLongId(request.getOrderId(), "订单ID");
        updateStatus(orderId, STATUS_PENDING_PAYMENT, STATUS_WAIT_ACCEPT, "只有待支付订单可以修改为待接单");
        Order order = getOrder(String.valueOf(orderId));
        sendNotice(String.valueOf(order.getShopOwnerId()), "新订单", "您有新的订单「" + order.getShopName() + "」待接单");
    }

    @Transactional(rollbackFor = Exception.class)
    public void expireOrder(String orderId) {
        Long id = parseLongId(orderId, "订单ID");
        orderMapper.update(null, new LambdaUpdateWrapper<Order>()
                .set(Order::getStatus, STATUS_EXPIRED)
                .eq(Order::getId, id)
                .eq(Order::getStatus, STATUS_PENDING_PAYMENT));
    }

    public PageVO<OrderVO> listUserOrders(String userId, Integer status, Long page, Long size) {
        Long buyerId = parseLongId(userId, "用户ID");
        Integer normalizedStatus = normalizeStatus(status);
        long currentPage = normalizePage(page);
        long pageSize = normalizeSize(size);

        LambdaQueryWrapper<Order> wrapper = new LambdaQueryWrapper<Order>()
                .eq(Order::getUserId, buyerId)
                .eq(normalizedStatus != null, Order::getStatus, normalizedStatus)
                .orderByDesc(Order::getCreateTime)
                .orderByDesc(Order::getId);
        return fetchOrders(wrapper, currentPage, pageSize);
    }

    public PageVO<OrderVO> listShopOwnerOrders(String userId, Integer status, Long page, Long size) {
        Long ownerId = parseLongId(userId, "用户ID");
        Integer normalizedStatus = normalizeStatus(status);
        long currentPage = normalizePage(page);
        long pageSize = normalizeSize(size);

        LambdaQueryWrapper<Order> wrapper = new LambdaQueryWrapper<Order>()
                .eq(Order::getShopOwnerId, ownerId)
                .eq(normalizedStatus != null, Order::getStatus, normalizedStatus)
                .orderByDesc(Order::getCreateTime)
                .orderByDesc(Order::getId);
        return fetchOrders(wrapper, currentPage, pageSize);
    }

    public PageVO<OrderVO> listRiderOrders(String userId, Integer status, Long page, Long size) {
        Long riderId = parseLongId(userId, "用户ID");
        Integer normalizedStatus = normalizeStatus(status);
        long currentPage = normalizePage(page);
        long pageSize = normalizeSize(size);

        LambdaQueryWrapper<Order> wrapper = new LambdaQueryWrapper<Order>()
                .and(w -> w.eq(Order::getRiderId, riderId)
                        .or().eq(Order::getStatus, STATUS_WAIT_DELIVERY).isNull(Order::getRiderId))
                .eq(normalizedStatus != null, Order::getStatus, normalizedStatus)
                .orderByDesc(Order::getCreateTime)
                .orderByDesc(Order::getId);
        return fetchOrders(wrapper, currentPage, pageSize);
    }

    private PageVO<OrderVO> fetchOrders(LambdaQueryWrapper<Order> wrapper, long page, long size) {
        Page<Order> result = orderMapper.selectPage(new Page<>(page, size), wrapper);
        List<OrderVO> records = result.getRecords().stream()
                .map(order -> toOrderVO(order, selectOrderItems(order.getId())))
                .toList();
        return new PageVO<>(records, result.getTotal(), result.getCurrent(), result.getSize(), result.getPages());
    }

    private long normalizePage(Long page) {
        if (page == null) return 1L;
        if (page < 1) throw new IllegalArgumentException("页码必须大于0");
        return page;
    }

    private long normalizeSize(Long size) {
        if (size == null) return 10L;
        if (size < 1 || size > 100) throw new IllegalArgumentException("每页数量必须在1到100之间");
        return size;
    }

    private String buildCreateTokenKey(String userId, String token) {
        return CREATE_TOKEN_KEY_PREFIX + userId + ":" + token;
    }

    private void sendNotice(String receiverId, String title, String content) {
        try {
            WebSocketMessageDTO<Map<String, String>> dto = WebSocketMessageDTO.notice(
                    receiverId, Map.of("title", title, "content", content));
            rabbitTemplate.convertAndSend(MqConstants.MESSAGE_EXCHANGE, MqConstants.MESSAGE_WS_ROUTING_KEY, dto);
        } catch (RuntimeException ex) {
            log.warn("订单通知发送失败 receiverId={}, title={}", receiverId, title, ex);
        }
    }

    private boolean hasText(String value) {
        return value != null && !value.isBlank();
    }

    private Integer normalizeStatus(Integer status) {
        if (status != null && (status < STATUS_PENDING_PAYMENT || status > STATUS_CANCELLED)) {
            throw new IllegalArgumentException("订单状态不正确");
        }
        return status;
    }

    private void sendOrderExpireMessage(Long orderId) {
        long ttlMillis = Duration.ofMinutes((long) expireMinutes + mqGraceMinutes).toMillis();
        rabbitTemplate.convertAndSend(MqConstants.ORDER_EXCHANGE, MqConstants.ORDER_EXPIRE_DELAY_ROUTING_KEY, String.valueOf(orderId), message -> {
            message.getMessageProperties().setExpiration(String.valueOf(ttlMillis));
            return message;
        });
    }



    private void refundBuyer(Order order) {
        Result<?> alipayRefundResult = paymentClient.refundAlipayOrderByOrder(new PaymentOrderRefundRequest(String.valueOf(order.getId())));
        if (isSuccess(alipayRefundResult)) {
            return;
        }
        if (alipayRefundResult != null && Integer.valueOf(400).equals(alipayRefundResult.getCode())
                && "支付宝已支付订单不存在".equals(alipayRefundResult.getMessage())) {
            call(paymentClient.addBalance(new PaymentWalletAddRequest(String.valueOf(order.getUserId()), order.getAmount())), "买家退款失败");
            return;
        }
        String message = alipayRefundResult == null || alipayRefundResult.getMessage() == null
                ? "支付宝退款失败" : alipayRefundResult.getMessage();
        throw new RuntimeException(message);
    }

    private boolean isSuccess(Result<?> result) {
        return result != null && result.getCode() != null && result.getCode() >= 200 && result.getCode() < 300;
    }

    private void checkShopOwner(String merchantId, Order order) {
        Long normalizedMerchantId = parseLongId(merchantId, "商家用户ID");
        if (order.getShopOwnerId() == null || !normalizedMerchantId.equals(order.getShopOwnerId())) {
            throw new IllegalArgumentException("只能处理自己的店铺订单");
        }
    }

    private Order getOwnOrder(String userId, String orderId) {
        Long buyerId = parseLongId(userId, "用户ID");
        Order order = getOrder(orderId);
        if (!buyerId.equals(order.getUserId())) {
            throw new IllegalArgumentException("只能操作自己的订单");
        }
        return order;
    }

    private Order getOrder(String orderId) {
        Long id = parseLongId(orderId, "订单ID");
        Order order = orderMapper.selectById(id);
        if (order == null) {
            throw new IllegalArgumentException("订单不存在");
        }
        return order;
    }

    private String statusText(Integer status) {
        if (status == null) return "未知";
        return switch (status) {
            case STATUS_PENDING_PAYMENT -> "待支付";
            case STATUS_WAIT_ACCEPT -> "待接单";
            case STATUS_WAIT_DELIVERY -> "待配送";
            case STATUS_WAIT_ARRIVE -> "待送达";
            case STATUS_WAIT_REVIEW -> "待评价";
            case STATUS_FINISHED -> "已完成";
            case STATUS_EXPIRED -> "已过期";
            case STATUS_CANCELLED -> "已取消";
            default -> "未知";
        };
    }

    private void checkStatus(Order order, int expectedStatus, String message) {
        if (order.getStatus() == null || order.getStatus() != expectedStatus) {
            throw new IllegalArgumentException(message);
        }
    }

    private void updateStatus(Long orderId, int oldStatus, int newStatus, String failMessage) {
        int updated = orderMapper.update(null, new LambdaUpdateWrapper<Order>()
                .set(Order::getStatus, newStatus)
                .eq(Order::getId, orderId)
                .eq(Order::getStatus, oldStatus));
        if (updated <= 0) {
            Order order = orderMapper.selectById(orderId);
            if (order == null) {
                throw new IllegalArgumentException("订单不存在");
            }
            throw new IllegalArgumentException(failMessage);
        }
    }

    private List<OrderItem> selectOrderItems(Long orderId) {
        return orderItemMapper.selectList(new LambdaQueryWrapper<OrderItem>().eq(OrderItem::getOrderId, orderId));
    }

    private long sumQuantity(Long orderId) {
        return selectOrderItems(orderId).stream().map(OrderItem::getQuantity).filter(q -> q != null).mapToLong(Integer::longValue).sum();
    }

    private <T> T callData(Result<T> result, String message) {
        call(result, message);
        return result.getData();
    }

    private void call(Result<?> result, String message) {
        if (result == null) {
            throw new RuntimeException(message);
        }
        if (result.getCode() == null || result.getCode() < 200 || result.getCode() >= 300) {
            throw new RuntimeException(result.getMessage() == null ? message : result.getMessage());
        }
    }

    private OrderVO toOrderVO(Order order, List<OrderItem> items) {
        return new OrderVO(
                String.valueOf(order.getId()),
                String.valueOf(order.getUserId()),
                String.valueOf(order.getShopId()),
                String.valueOf(order.getShopOwnerId()),
                order.getRiderId() == null ? null : String.valueOf(order.getRiderId()),
                order.getShopName(),
                order.getReceiverName(),
                order.getReceiverPhone(),
                order.getReceiverAddress(),
                order.getReceiverLongitude(),
                order.getReceiverLatitude(),
                order.getRemark(),
                order.getDeliveryFee(),
                order.getAmount(),
                order.getStatus(),
                statusText(order.getStatus()),
                order.getExpireTime(),
                order.getCreateTime(),
                order.getUpdateTime(),
                items.stream().map(item -> new OrderItemVO(
                        String.valueOf(item.getId()), item.getName(), item.getPrice(), item.getQuantity(), item.getAmount()
                )).toList()
        );
    }

    private String checkText(String value, String fieldName) {
        if (value == null || value.isBlank()) {
            throw new IllegalArgumentException(fieldName + "不能为空");
        }
        return value.trim();
    }

    private String normalizeOptionalText(String value) {
        return value == null || value.isBlank() ? null : value.trim();
    }

    private BigDecimal checkAmount(BigDecimal amount, String fieldName) {
        if (amount == null || amount.compareTo(BigDecimal.ZERO) < 0 || amount.stripTrailingZeros().scale() > 2) {
            throw new IllegalArgumentException(fieldName + "不正确");
        }
        return amount;
    }

    private Long parseLongId(String value, String fieldName) {
        if (value == null || value.isBlank()) {
            throw new IllegalArgumentException(fieldName + "不能为空");
        }
        try {
            return Long.valueOf(value.trim());
        } catch (NumberFormatException ex) {
            throw new IllegalArgumentException(fieldName + "格式不正确");
        }
    }
}
