package top.zxylearn.service;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.conditions.update.LambdaUpdateWrapper;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.SerializationFeature;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.amqp.rabbit.core.RabbitTemplate;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.script.DefaultRedisScript;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.support.TransactionSynchronization;
import org.springframework.transaction.support.TransactionSynchronizationManager;
import top.zxylearn.constant.MqConstants;
import top.zxylearn.dto.ShopCreateRequest;
import top.zxylearn.dto.ShopItemCreateRequest;
import top.zxylearn.dto.ShopReviewReplyRequest;
import top.zxylearn.dto.ShopStatusUpdateRequest;
import top.zxylearn.dto.ShopUpdateRequest;
import top.zxylearn.dto.shop.ShopEsIndexEventDTO;
import top.zxylearn.dto.shop.ShopReviewCreateRequest;
import top.zxylearn.dto.shop.ShopSalesIncreaseRequest;
import top.zxylearn.entity.Shop;
import top.zxylearn.entity.ShopItem;
import top.zxylearn.entity.ShopReview;
import top.zxylearn.entity.ShopReviewImage;
import top.zxylearn.entity.ShopReviewReply;
import top.zxylearn.mapper.ShopItemMapper;
import top.zxylearn.mapper.ShopMapper;
import top.zxylearn.mapper.ShopReviewImageMapper;
import top.zxylearn.mapper.ShopReviewMapper;
import top.zxylearn.mapper.ShopReviewReplyMapper;
import top.zxylearn.vo.CursorPageVO;
import top.zxylearn.vo.ShopItemVO;
import top.zxylearn.vo.ShopReviewReplyVO;
import top.zxylearn.vo.ShopReviewVO;
import top.zxylearn.vo.ShopVO;

import java.math.BigDecimal;
import java.time.Duration;
import java.time.LocalTime;
import java.time.format.DateTimeParseException;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ThreadLocalRandom;

@Service
public class ShopService {

    private static final Logger log = LoggerFactory.getLogger(ShopService.class);

    private static final int STATUS_NORMAL = 0;
    private static final int STATUS_BANNED = 1;
    private static final BigDecimal ZERO_AMOUNT = new BigDecimal("0.00");
    private static final BigDecimal ZERO_SCORE = new BigDecimal("0.0");
    private static final BigDecimal MIN_LONGITUDE = new BigDecimal("-180");
    private static final BigDecimal MAX_LONGITUDE = new BigDecimal("180");
    private static final BigDecimal MIN_LATITUDE = new BigDecimal("-90");
    private static final BigDecimal MAX_LATITUDE = new BigDecimal("90");
    private static final BigDecimal MAX_REVIEW_SCORE = new BigDecimal("5");
    private static final int MAX_REVIEW_IMAGE_COUNT = 5;
    private static final String SHOP_INFO_KEY_PREFIX = "shop:info:";
    private static final String SHOP_ITEM_LIST_KEY_PREFIX = "shop:item:list:";
    private static final String SHOP_LOCK_KEY_PREFIX = "shop:lock:";
    private static final String SHOP_ES_INDEX_DELAY_KEY_PREFIX = "shop:es:index-delay:";
    private static final String NULL_CACHE_VALUE = "__NULL__";
    private static final DefaultRedisScript<Long> UNLOCK_SCRIPT = new DefaultRedisScript<>(
            "if redis.call('get', KEYS[1]) == ARGV[1] then return redis.call('del', KEYS[1]) else return 0 end",
            Long.class
    );

    private final ShopMapper shopMapper;
    private final ShopItemMapper shopItemMapper;
    private final ShopReviewMapper shopReviewMapper;
    private final ShopReviewImageMapper shopReviewImageMapper;
    private final ShopReviewReplyMapper shopReviewReplyMapper;
    private final RabbitTemplate rabbitTemplate;
    private final StringRedisTemplate stringRedisTemplate;
    private final ObjectMapper objectMapper;
    private final Duration cacheTtl;
    private final Duration nullCacheTtl;
    private final Duration lockTtl;
    private final int cacheTtlJitterSeconds;
    private final int nullCacheTtlJitterSeconds;
    private final Duration esIndexDelay;
    private final int defaultReviewPageSize;
    private final int maxReviewPageSize;

    public ShopService(ShopMapper shopMapper,
                       ShopItemMapper shopItemMapper,
                       ShopReviewMapper shopReviewMapper,
                       ShopReviewImageMapper shopReviewImageMapper,
                       ShopReviewReplyMapper shopReviewReplyMapper,
                       RabbitTemplate rabbitTemplate,
                       StringRedisTemplate stringRedisTemplate,
                       @Value("${shop.cache.ttl}") Duration cacheTtl,
                       @Value("${shop.cache.null-ttl}") Duration nullCacheTtl,
                       @Value("${shop.cache.lock-ttl}") Duration lockTtl,
                       @Value("${shop.cache.ttl-jitter-seconds}") int cacheTtlJitterSeconds,
                       @Value("${shop.cache.null-ttl-jitter-seconds}") int nullCacheTtlJitterSeconds,
                       @Value("${shop.es-index.delay}") Duration esIndexDelay,
                       @Value("${shop.review.page-size.default}") int defaultReviewPageSize,
                       @Value("${shop.review.page-size.max}") int maxReviewPageSize) {
        this.shopMapper = shopMapper;
        this.shopItemMapper = shopItemMapper;
        this.shopReviewMapper = shopReviewMapper;
        this.shopReviewImageMapper = shopReviewImageMapper;
        this.shopReviewReplyMapper = shopReviewReplyMapper;
        this.rabbitTemplate = rabbitTemplate;
        this.stringRedisTemplate = stringRedisTemplate;
        this.cacheTtl = cacheTtl;
        this.nullCacheTtl = nullCacheTtl;
        this.lockTtl = lockTtl;
        this.cacheTtlJitterSeconds = cacheTtlJitterSeconds;
        this.nullCacheTtlJitterSeconds = nullCacheTtlJitterSeconds;
        this.esIndexDelay = esIndexDelay;
        this.defaultReviewPageSize = defaultReviewPageSize;
        this.maxReviewPageSize = maxReviewPageSize;
        this.objectMapper = new ObjectMapper()
                .registerModule(new JavaTimeModule())
                .disable(SerializationFeature.WRITE_DATES_AS_TIMESTAMPS);
    }

    @Transactional(rollbackFor = Exception.class)
    public ShopVO createShop(String userId, ShopCreateRequest request) {
        if (request == null) {
            throw new IllegalArgumentException("店铺参数不能为空");
        }
        Shop shop = new Shop();
        shop.setUserId(parseLongId(userId, "用户ID"));
        shop.setName(checkRequiredText(request.getName(), 100, "店铺名称"));
        shop.setAvatar(checkOptionalText(request.getAvatar(), 500, "店铺头像URL"));
        shop.setDescription(checkRequiredText(request.getDescription(), 500, "店铺描述"));
        shop.setAddress(checkRequiredText(request.getAddress(), 255, "店铺地址"));
        shop.setLongitude(checkRequiredLongitude(request.getLongitude()));
        shop.setLatitude(checkRequiredLatitude(request.getLatitude()));
        shop.setDeliveryFee(checkMoney(request.getDeliveryFee(), "配送费"));
        shop.setOpenTime(parseRequiredTime(request.getOpenTime(), "开始营业时间"));
        shop.setCloseTime(parseRequiredTime(request.getCloseTime(), "结束营业时间"));
        shop.setReviewScore(ZERO_SCORE);
        shop.setReviewCount(0L);
        shop.setSalesCount(0L);
        shop.setStatus(STATUS_NORMAL);
        shopMapper.insert(shop);
        ShopVO shopVO = toShopVO(shop);
        cacheShop(shopVO);
        publishShopEsIndexAfterCommit(shop.getId(), ShopEsIndexEventDTO.ACTION_UPSERT);
        return shopVO;
    }

    @Transactional(rollbackFor = Exception.class)
    public ShopVO updateShop(String userId, String shopId, ShopUpdateRequest request) {
        if (request == null) {
            throw new IllegalArgumentException("店铺参数不能为空");
        }
        Shop shop = getOwnShop(userId, shopId);
        checkShopAvailable(shop);
        if (hasText(request.getName())) {
            shop.setName(checkRequiredText(request.getName(), 100, "店铺名称"));
        }
        if (hasText(request.getAvatar())) {
            shop.setAvatar(checkOptionalText(request.getAvatar(), 500, "店铺头像URL"));
        }
        if (hasText(request.getDescription())) {
            shop.setDescription(checkRequiredText(request.getDescription(), 500, "店铺描述"));
        }
        if (hasText(request.getAddress())) {
            shop.setAddress(checkRequiredText(request.getAddress(), 255, "店铺地址"));
        }
        if (request.getLongitude() != null) {
            shop.setLongitude(checkLongitude(request.getLongitude()));
        }
        if (request.getLatitude() != null) {
            shop.setLatitude(checkLatitude(request.getLatitude()));
        }
        if (request.getDeliveryFee() != null) {
            shop.setDeliveryFee(checkMoney(request.getDeliveryFee(), "配送费"));
        }
        if (hasText(request.getOpenTime())) {
            shop.setOpenTime(parseRequiredTime(request.getOpenTime(), "开始营业时间"));
        }
        if (hasText(request.getCloseTime())) {
            shop.setCloseTime(parseRequiredTime(request.getCloseTime(), "结束营业时间"));
        }
        shopMapper.updateById(shop);
        ShopVO shopVO = toShopVO(shopMapper.selectById(shop.getId()));
        cacheShop(shopVO);
        publishShopEsIndexAfterCommit(shop.getId(), ShopEsIndexEventDTO.ACTION_UPSERT);
        return shopVO;
    }

    @Transactional(rollbackFor = Exception.class)
    public ShopItemVO addItem(String userId, String shopId, ShopItemCreateRequest request) {
        if (request == null) {
            throw new IllegalArgumentException("商品参数不能为空");
        }
        Shop shop = getOwnShop(userId, shopId);
        checkShopAvailable(shop);
        ShopItem item = new ShopItem();
        item.setShopId(shop.getId());
        item.setName(checkRequiredText(request.getName(), 100, "商品名称"));
        item.setImage(checkOptionalText(request.getImage(), 500, "商品图片URL"));
        item.setDescription(checkRequiredText(request.getDescription(), 500, "商品描述"));
        item.setPrice(checkMoney(request.getPrice(), "商品价格"));
        item.setStatus(STATUS_NORMAL);
        shopItemMapper.insert(item);
        evictShopItemListCache(shop.getId());
        publishShopEsIndexAfterCommit(shop.getId(), ShopEsIndexEventDTO.ACTION_UPSERT);
        return toShopItemVO(item);
    }

    @Transactional(rollbackFor = Exception.class)
    public void deleteItem(String userId, String shopId, String itemId) {
        Shop shop = getOwnShop(userId, shopId);
        checkShopAvailable(shop);
        ShopItem item = getShopItemById(itemId);
        if (!shop.getId().equals(item.getShopId())) {
            throw new IllegalArgumentException("商品不属于该店铺");
        }
        shopItemMapper.deleteById(item.getId());
        evictShopItemListCache(shop.getId());
        publishShopEsIndexAfterCommit(shop.getId(), ShopEsIndexEventDTO.ACTION_UPSERT);
    }

    @Transactional(rollbackFor = Exception.class)
    public void deleteOwnShop(String userId, String shopId) {
        Shop shop = getOwnShop(userId, shopId);
        deleteShopWithItems(shop);
    }

    @Transactional(rollbackFor = Exception.class)
    public void deleteShopByAdmin(String shopId) {
        Shop shop = getShopById(shopId);
        deleteShopWithItems(shop);
    }

    @Transactional(rollbackFor = Exception.class)
    public void deleteItemByAdmin(String itemId) {
        ShopItem item = getShopItemById(itemId);
        shopItemMapper.deleteById(item.getId());
        evictShopItemListCache(item.getShopId());
        publishShopEsIndexAfterCommit(item.getShopId(), ShopEsIndexEventDTO.ACTION_UPSERT);
    }

    public ShopVO getShop(String shopId) {
        ShopVO shopVO = getShopWithCache(shopId);
        checkShopAvailable(shopVO);
        return shopVO;
    }

    public ShopVO getShopByAdmin(String shopId) {
        return getShopWithCache(shopId);
    }

    public List<ShopItemVO> listShopItems(String shopId) {
        ShopVO shopVO = getShop(shopId);
        return listShopItemsWithCache(shopVO.getShopId());
    }

    public List<ShopItemVO> listShopItemsByAdmin(String shopId) {
        ShopVO shopVO = getShopByAdmin(shopId);
        return listShopItemsWithCache(shopVO.getShopId());
    }

    public CursorPageVO<ShopReviewVO> listReviews(String shopId, String cursor, Integer size) {
        ShopVO shopVO = getShop(shopId);
        Long normalizedShopId = parseLongId(shopVO.getShopId(), "店铺ID");
        Long cursorId = parseNullableLongId(cursor, "游标");
        int pageSize = normalizeReviewPageSize(size);

        LambdaQueryWrapper<ShopReview> wrapper = new LambdaQueryWrapper<ShopReview>()
                .eq(ShopReview::getShopId, normalizedShopId)
                .orderByDesc(ShopReview::getId)
                .last("LIMIT " + (pageSize + 1));
        if (cursorId != null) {
            wrapper.lt(ShopReview::getId, cursorId);
        }

        List<ShopReview> reviews = shopReviewMapper.selectList(wrapper);
        boolean hasMore = reviews.size() > pageSize;
        List<ShopReview> pageReviews = hasMore ? reviews.subList(0, pageSize) : reviews;
        Map<Long, List<String>> imageMap = selectReviewImageMap(pageReviews.stream().map(ShopReview::getId).toList());
        List<ShopReviewVO> records = pageReviews.stream()
                .map(review -> toShopReviewVO(review, imageMap.getOrDefault(review.getId(), Collections.emptyList())))
                .toList();
        return new CursorPageVO<>(records, buildNextCursor(records, hasMore), hasMore);
    }

    public CursorPageVO<ShopReviewReplyVO> listReviewReplies(String reviewId, String cursor, Integer size) {
        ShopReview review = getAvailableReview(reviewId);
        Long cursorId = parseNullableLongId(cursor, "游标");
        int pageSize = normalizeReviewPageSize(size);

        LambdaQueryWrapper<ShopReviewReply> wrapper = new LambdaQueryWrapper<ShopReviewReply>()
                .eq(ShopReviewReply::getReviewId, review.getId())
                .orderByDesc(ShopReviewReply::getId)
                .last("LIMIT " + (pageSize + 1));
        if (cursorId != null) {
            wrapper.lt(ShopReviewReply::getId, cursorId);
        }

        List<ShopReviewReply> replies = shopReviewReplyMapper.selectList(wrapper);
        boolean hasMore = replies.size() > pageSize;
        List<ShopReviewReply> pageReplies = hasMore ? replies.subList(0, pageSize) : replies;
        List<ShopReviewReplyVO> records = pageReplies.stream().map(this::toShopReviewReplyVO).toList();
        return new CursorPageVO<>(records, buildReplyNextCursor(records, hasMore), hasMore);
    }

    @Transactional(rollbackFor = Exception.class)
    public void deleteReviewByAdmin(String reviewId) {
        Long id = parseLongId(reviewId, "评价ID");
        ShopReview review = shopReviewMapper.selectById(id);
        if (review == null) {
            throw new IllegalArgumentException("评价不存在");
        }
        Long shopId = review.getShopId();
        shopReviewReplyMapper.delete(new LambdaQueryWrapper<ShopReviewReply>().eq(ShopReviewReply::getReviewId, id));
        shopReviewImageMapper.delete(new LambdaQueryWrapper<ShopReviewImage>().eq(ShopReviewImage::getReviewId, id));
        shopReviewMapper.deleteById(id);
        refreshShopReviewSummary(shopId);
        Shop shop = shopMapper.selectById(shopId);
        if (shop != null) {
            cacheShop(toShopVO(shop));
            publishShopEsIndexAfterCommit(shopId, ShopEsIndexEventDTO.ACTION_UPSERT);
        }
    }

    @Transactional(rollbackFor = Exception.class)
    public void deleteReviewReplyByAdmin(String replyId) {
        Long id = parseLongId(replyId, "评价回复ID");
        int deleted = shopReviewReplyMapper.deleteById(id);
        if (deleted <= 0) {
            throw new IllegalArgumentException("评价回复不存在");
        }
    }


    @Transactional(rollbackFor = Exception.class)
    public void replyReview(String userId, ShopReviewReplyRequest request) {
        if (request == null) {
            throw new IllegalArgumentException("回复参数不能为空");
        }
        Long reviewId = parseLongId(request.getReviewId(), "评价ID");
        Long replyUserId = parseLongId(userId, "用户ID");
        Long atUserId = parseNullableLongId(request.getAtUserId(), "被@用户ID");
        String content = checkRequiredText(request.getContent(), 2000, "回复内容");

        getAvailableReview(String.valueOf(reviewId));

        ShopReviewReply reply = new ShopReviewReply();
        reply.setReviewId(reviewId);
        reply.setUserId(replyUserId);
        reply.setAtUserId(atUserId);
        reply.setContent(content);
        shopReviewReplyMapper.insert(reply);
    }

    @Transactional(rollbackFor = Exception.class)
    public void createReview(ShopReviewCreateRequest request) {
        if (request == null) {
            throw new IllegalArgumentException("评价参数不能为空");
        }
        Long orderId = parseLongId(request.getOrderId(), "订单ID");
        Long userId = parseLongId(request.getUserId(), "用户ID");
        Long shopId = parseLongId(request.getShopId(), "店铺ID");
        BigDecimal score = checkReviewScore(request.getScore());
        String content = checkRequiredText(request.getContent(), 2000, "评价内容");
        List<String> images = checkReviewImages(request.getImages());

        Shop shop = getShopById(String.valueOf(shopId));
        checkShopAvailable(shop);

        ShopReview review = new ShopReview();
        review.setOrderId(orderId);
        review.setShopId(shopId);
        review.setUserId(userId);
        review.setScore(score);
        review.setContent(content);
        shopReviewMapper.insert(review);

        for (int i = 0; i < images.size(); i++) {
            ShopReviewImage image = new ShopReviewImage();
            image.setReviewId(review.getId());
            image.setImage(images.get(i));
            image.setSort(i);
            shopReviewImageMapper.insert(image);
        }

        updateShopReviewSummary(shopId, score);
        Shop updatedShop = shopMapper.selectById(shopId);
        if (updatedShop != null) {
            cacheShop(toShopVO(updatedShop));
        }
        publishDelayedShopEsIndexAfterCommit(shopId);
    }

    @Transactional(rollbackFor = Exception.class)
    public void increaseSales(ShopSalesIncreaseRequest request) {
        if (request == null) {
            throw new IllegalArgumentException("店铺销量更新参数不能为空");
        }
        Long shopId = parseLongId(request.getShopId(), "店铺ID");
        Long salesDelta = request.getSalesDelta();
        if (salesDelta == null || salesDelta <= 0) {
            throw new IllegalArgumentException("销量增量必须大于0");
        }
        int updated = shopMapper.update(null, new LambdaUpdateWrapper<Shop>()
                .eq(Shop::getId, shopId)
                .setSql("sales_count = sales_count + " + salesDelta));
        if (updated <= 0) {
            throw new IllegalArgumentException("店铺不存在");
        }
        Shop shop = shopMapper.selectById(shopId);
        if (shop != null) {
            cacheShop(toShopVO(shop));
        }
        publishDelayedShopEsIndexAfterCommit(shopId);
    }

    @Transactional(rollbackFor = Exception.class)
    public ShopVO updateShopStatus(String shopId, ShopStatusUpdateRequest request) {
        if (request == null) {
            throw new IllegalArgumentException("店铺状态不能为空");
        }
        Integer status = request.getStatus();
        if (status == null) {
            throw new IllegalArgumentException("店铺状态不能为空");
        }
        if (!Integer.valueOf(STATUS_NORMAL).equals(status) && !Integer.valueOf(STATUS_BANNED).equals(status)) {
            throw new IllegalArgumentException("店铺状态只能为0正常或1封禁");
        }
        Shop shop = getShopById(shopId);
        shop.setStatus(status);
        shopMapper.updateById(shop);
        ShopVO shopVO = toShopVO(shopMapper.selectById(shop.getId()));
        cacheShop(shopVO);
        publishShopEsIndexAfterCommit(shop.getId(), ShopEsIndexEventDTO.ACTION_UPSERT);
        return shopVO;
    }


    private void updateShopReviewSummary(Long shopId, BigDecimal score) {
        int updated = shopMapper.update(null, new LambdaUpdateWrapper<Shop>()
                .eq(Shop::getId, shopId)
                .setSql("review_score = round(((review_score * review_count) + "
                        + score.toPlainString() + ") / (review_count + 1), 1)")
                .setSql("review_count = review_count + 1"));
        if (updated <= 0) {
            throw new IllegalArgumentException("店铺不存在");
        }
    }

    private void refreshShopReviewSummary(Long shopId) {
        List<ShopReview> reviews = shopReviewMapper.selectList(new LambdaQueryWrapper<ShopReview>()
                .eq(ShopReview::getShopId, shopId));
        long reviewCount = reviews.size();
        BigDecimal reviewScore = ZERO_SCORE;
        if (reviewCount > 0) {
            BigDecimal totalScore = reviews.stream()
                    .map(ShopReview::getScore)
                    .reduce(BigDecimal.ZERO, BigDecimal::add);
            reviewScore = totalScore.divide(BigDecimal.valueOf(reviewCount), 1, java.math.RoundingMode.HALF_UP);
        }
        int updated = shopMapper.update(null, new LambdaUpdateWrapper<Shop>()
                .eq(Shop::getId, shopId)
                .set(Shop::getReviewCount, reviewCount)
                .set(Shop::getReviewScore, reviewScore));
        if (updated <= 0) {
            throw new IllegalArgumentException("店铺不存在");
        }
    }

    private Map<Long, List<String>> selectReviewImageMap(List<Long> reviewIds) {
        if (reviewIds == null || reviewIds.isEmpty()) {
            return Collections.emptyMap();
        }
        List<ShopReviewImage> images = shopReviewImageMapper.selectList(new LambdaQueryWrapper<ShopReviewImage>()
                .in(ShopReviewImage::getReviewId, reviewIds)
                .orderByAsc(ShopReviewImage::getReviewId)
                .orderByAsc(ShopReviewImage::getSort));
        Map<Long, List<String>> imageMap = new LinkedHashMap<>();
        for (ShopReviewImage image : images) {
            imageMap.computeIfAbsent(image.getReviewId(), key -> new java.util.ArrayList<>()).add(image.getImage());
        }
        return imageMap;
    }

    private int normalizeReviewPageSize(Integer size) {
        if (size == null) {
            return defaultReviewPageSize;
        }
        if (size <= 0) {
            throw new IllegalArgumentException("分页大小必须大于0");
        }
        return Math.min(size, maxReviewPageSize);
    }

    private String buildNextCursor(List<ShopReviewVO> records, boolean hasMore) {
        if (!hasMore || records == null || records.isEmpty()) {
            return null;
        }
        return records.get(records.size() - 1).getReviewId();
    }

    private String buildReplyNextCursor(List<ShopReviewReplyVO> records, boolean hasMore) {
        if (!hasMore || records == null || records.isEmpty()) {
            return null;
        }
        return records.get(records.size() - 1).getReplyId();
    }

    private ShopReview getAvailableReview(String reviewId) {
        Long id = parseLongId(reviewId, "评价ID");
        ShopReview review = shopReviewMapper.selectById(id);
        if (review == null) {
            throw new IllegalArgumentException("评价不存在");
        }
        Shop shop = getShopById(String.valueOf(review.getShopId()));
        checkShopAvailable(shop);
        return review;
    }


    private BigDecimal checkReviewScore(BigDecimal score) {
        if (score == null) {
            throw new IllegalArgumentException("评价分数不能为空");
        }
        if (score.compareTo(BigDecimal.ZERO) < 0 || score.compareTo(MAX_REVIEW_SCORE) > 0) {
            throw new IllegalArgumentException("评价分数必须在0到5之间");
        }
        return score;
    }

    private List<String> checkReviewImages(List<String> images) {
        if (images == null || images.isEmpty()) {
            return Collections.emptyList();
        }
        if (images.size() > MAX_REVIEW_IMAGE_COUNT) {
            throw new IllegalArgumentException("评价图片最多5张");
        }
        return images.stream()
                .map(image -> checkRequiredText(image, 500, "评价图片URL"))
                .toList();
    }

    private Shop getShopById(String shopId) {
        Long id = parseLongId(shopId, "店铺ID");
        Shop shop = shopMapper.selectById(id);
        if (shop == null) {
            throw new IllegalArgumentException("店铺不存在");
        }
        return shop;
    }

    private Shop getOwnShop(String userId, String shopId) {
        Long ownerId = parseLongId(userId, "用户ID");
        Long id = parseLongId(shopId, "店铺ID");
        Shop shop = shopMapper.selectById(id);
        if (shop == null) {
            throw new IllegalArgumentException("店铺不存在");
        }
        if (!ownerId.equals(shop.getUserId())) {
            throw new IllegalArgumentException("只能操作自己的店铺");
        }
        return shop;
    }

    private ShopItem getShopItemById(String itemId) {
        Long id = parseLongId(itemId, "商品ID");
        ShopItem item = shopItemMapper.selectById(id);
        if (item == null) {
            throw new IllegalArgumentException("商品不存在");
        }
        return item;
    }


    private void publishShopEsIndexAfterCommit(Long shopId, String action) {
        if (shopId == null) {
            return;
        }
        ShopEsIndexEventDTO event = new ShopEsIndexEventDTO(
                UUID.randomUUID().toString(),
                String.valueOf(shopId),
                action,
                System.currentTimeMillis()
        );
        Runnable publisher = () -> publishShopEsIndex(event);
        if (TransactionSynchronizationManager.isSynchronizationActive()) {
            TransactionSynchronizationManager.registerSynchronization(new TransactionSynchronization() {
                @Override
                public void afterCommit() {
                    publisher.run();
                }
            });
            return;
        }
        publisher.run();
    }

    private void publishShopEsIndex(ShopEsIndexEventDTO event) {
        try {
            rabbitTemplate.convertAndSend(MqConstants.SHOP_EXCHANGE, MqConstants.SHOP_ES_INDEX_ROUTING_KEY, event);
        } catch (RuntimeException ex) {
            log.warn("店铺ES索引消息投递失败 shopId={}, action={}", event.getShopId(), event.getAction(), ex);
        }
    }

    private void publishDelayedShopEsIndexAfterCommit(Long shopId) {
        if (shopId == null) {
            return;
        }
        Runnable publisher = () -> publishDelayedShopEsIndex(shopId);
        if (TransactionSynchronizationManager.isSynchronizationActive()) {
            TransactionSynchronizationManager.registerSynchronization(new TransactionSynchronization() {
                @Override
                public void afterCommit() {
                    publisher.run();
                }
            });
            return;
        }
        publisher.run();
    }

    private void publishDelayedShopEsIndex(Long shopId) {
        String key = buildShopEsIndexDelayKey(shopId);
        String eventId = UUID.randomUUID().toString();
        long delayMillis = esIndexDelayMillis();
        Boolean acquired = stringRedisTemplate.opsForValue().setIfAbsent(key, eventId, Duration.ofMillis(delayMillis));
        if (!Boolean.TRUE.equals(acquired)) {
            return;
        }

        ShopEsIndexEventDTO event = new ShopEsIndexEventDTO(
                eventId,
                String.valueOf(shopId),
                ShopEsIndexEventDTO.ACTION_UPSERT,
                System.currentTimeMillis()
        );
        try {
            rabbitTemplate.convertAndSend(MqConstants.SHOP_EXCHANGE, MqConstants.SHOP_ES_INDEX_DELAY_ROUTING_KEY, event,
                    message -> {
                        message.getMessageProperties().setExpiration(String.valueOf(delayMillis));
                        return message;
                    });
        } catch (RuntimeException ex) {
            stringRedisTemplate.delete(key);
            log.warn("店铺ES索引延迟消息投递失败 shopId={}", shopId, ex);
        }
    }

    private long esIndexDelayMillis() {
        if (esIndexDelay == null || esIndexDelay.isNegative() || esIndexDelay.isZero()) {
            throw new IllegalStateException("shop.es-index.delay 配置必须大于0");
        }
        return esIndexDelay.toMillis();
    }

    private void deleteShopWithItems(Shop shop) {
        shopItemMapper.delete(new LambdaQueryWrapper<ShopItem>().eq(ShopItem::getShopId, shop.getId()));
        shopMapper.deleteById(shop.getId());
        evictShopCache(shop.getId());
        evictShopItemListCache(shop.getId());
        publishShopEsIndexAfterCommit(shop.getId(), ShopEsIndexEventDTO.ACTION_DELETE);
    }

    private void checkShopAvailable(Shop shop) {
        checkShopAvailableStatus(shop.getStatus());
    }

    private void checkShopAvailable(ShopVO shop) {
        checkShopAvailableStatus(shop.getStatus());
    }

    private void checkShopAvailableStatus(Integer status) {
        if (Integer.valueOf(STATUS_BANNED).equals(status)) {
            throw new IllegalArgumentException("店铺已被封禁，不能执行该操作");
        }
    }


    private ShopVO getShopWithCache(String shopId) {
        Long id = parseLongId(shopId, "店铺ID");
        String normalizedShopId = String.valueOf(id);
        String key = buildShopInfoKey(normalizedShopId);
        ShopVO cached = readShopCache(key);
        if (cached != null) {
            return cached;
        }

        String lockKey = buildShopInfoLockKey(normalizedShopId);
        String lockToken = tryLock(lockKey);
        if (lockToken != null) {
            try {
                cached = readShopCache(key);
                if (cached != null) {
                    return cached;
                }
                Shop shop = shopMapper.selectById(id);
                if (shop == null) {
                    cacheNullShop(key);
                    throw new IllegalArgumentException("店铺不存在");
                }
                ShopVO shopVO = toShopVO(shop);
                cacheShop(shopVO);
                return shopVO;
            } finally {
                unlock(lockKey, lockToken);
            }
        }

        String waitedValue = waitCacheValue(key);
        if (waitedValue != null) {
            return parseShopCacheValue(key, waitedValue);
        }
        Shop shop = shopMapper.selectById(id);
        if (shop == null) {
            cacheNullShop(key);
            throw new IllegalArgumentException("店铺不存在");
        }
        ShopVO shopVO = toShopVO(shop);
        cacheShop(shopVO);
        return shopVO;
    }

    private List<ShopItemVO> listShopItemsWithCache(String shopId) {
        String normalizedShopId = String.valueOf(parseLongId(shopId, "店铺ID"));
        String key = buildShopItemListKey(normalizedShopId);
        List<ShopItemVO> cached = readShopItemListCache(normalizedShopId);
        if (cached != null) {
            return cached;
        }

        String lockKey = buildShopItemListLockKey(normalizedShopId);
        String lockToken = tryLock(lockKey);
        if (lockToken != null) {
            try {
                cached = readShopItemListCache(normalizedShopId);
                if (cached != null) {
                    return cached;
                }
                List<ShopItemVO> items = selectShopItems(normalizedShopId);
                cacheShopItemList(normalizedShopId, items);
                return items;
            } finally {
                unlock(lockKey, lockToken);
            }
        }

        String waitedValue = waitCacheValue(key);
        if (waitedValue != null) {
            return parseShopItemListCacheValue(key, waitedValue);
        }
        List<ShopItemVO> items = selectShopItems(normalizedShopId);
        cacheShopItemList(normalizedShopId, items);
        return items;
    }

    private List<ShopItemVO> selectShopItems(String shopId) {
        return shopItemMapper.selectList(new LambdaQueryWrapper<ShopItem>()
                        .eq(ShopItem::getShopId, Long.valueOf(shopId))
                        .orderByDesc(ShopItem::getCreateTime))
                .stream()
                .map(this::toShopItemVO)
                .toList();
    }

    private void cacheShop(ShopVO shop) {
        if (shop != null && hasText(shop.getShopId())) {
            writeCache(buildShopInfoKey(shop.getShopId()), shop, "店铺缓存序列化失败", cacheTtlWithJitter());
        }
    }

    private void cacheNullShop(String key) {
        stringRedisTemplate.opsForValue().set(key, NULL_CACHE_VALUE, nullCacheTtlWithJitter());
    }

    private void cacheShopItemList(String shopId, List<ShopItemVO> items) {
        writeCache(buildShopItemListKey(shopId), items, "店铺商品列表缓存序列化失败", cacheTtlWithJitter());
    }

    private void evictShopCache(Long shopId) {
        if (shopId != null) {
            stringRedisTemplate.delete(buildShopInfoKey(String.valueOf(shopId)));
        }
    }

    private void evictShopItemListCache(Long shopId) {
        if (shopId != null) {
            stringRedisTemplate.delete(buildShopItemListKey(String.valueOf(shopId)));
        }
    }

    private ShopVO readShopCache(String key) {
        String value = stringRedisTemplate.opsForValue().get(key);
        if (value == null || value.isBlank()) {
            return null;
        }
        return parseShopCacheValue(key, value);
    }

    private ShopVO parseShopCacheValue(String key, String value) {
        if (NULL_CACHE_VALUE.equals(value)) {
            throw new IllegalArgumentException("店铺不存在");
        }
        try {
            return objectMapper.readValue(value, ShopVO.class);
        } catch (JsonProcessingException ex) {
            stringRedisTemplate.delete(key);
            throw new RuntimeException("店铺缓存解析失败", ex);
        }
    }

    private List<ShopItemVO> readShopItemListCache(String shopId) {
        String key = buildShopItemListKey(shopId);
        String value = stringRedisTemplate.opsForValue().get(key);
        if (value == null || value.isBlank()) {
            return null;
        }
        return parseShopItemListCacheValue(key, value);
    }

    private List<ShopItemVO> parseShopItemListCacheValue(String key, String value) {
        try {
            return objectMapper.readValue(value, new TypeReference<List<ShopItemVO>>() {
            });
        } catch (JsonProcessingException ex) {
            stringRedisTemplate.delete(key);
            throw new RuntimeException("店铺商品列表缓存解析失败", ex);
        }
    }

    private void writeCache(String key, Object value, String errorMessage, Duration ttl) {
        try {
            stringRedisTemplate.opsForValue().set(key, objectMapper.writeValueAsString(value), ttl);
        } catch (JsonProcessingException ex) {
            throw new RuntimeException(errorMessage, ex);
        }
    }

    private String tryLock(String key) {
        String token = UUID.randomUUID().toString();
        Boolean locked = stringRedisTemplate.opsForValue().setIfAbsent(key, token, lockTtl);
        return Boolean.TRUE.equals(locked) ? token : null;
    }

    private void unlock(String key, String token) {
        stringRedisTemplate.execute(UNLOCK_SCRIPT, Collections.singletonList(key), token);
    }

    private String waitCacheValue(String key) {
        for (int i = 0; i < 3; i++) {
            sleepQuietly(50);
            String value = stringRedisTemplate.opsForValue().get(key);
            if (value != null && !value.isBlank()) {
                return value;
            }
        }
        return null;
    }

    private void sleepQuietly(long millis) {
        try {
            Thread.sleep(millis);
        } catch (InterruptedException ex) {
            Thread.currentThread().interrupt();
        }
    }

    private Duration cacheTtlWithJitter() {
        return cacheTtl.plusSeconds(ThreadLocalRandom.current().nextInt(Math.max(cacheTtlJitterSeconds, 0) + 1));
    }

    private Duration nullCacheTtlWithJitter() {
        return nullCacheTtl.plusSeconds(ThreadLocalRandom.current().nextInt(Math.max(nullCacheTtlJitterSeconds, 0) + 1));
    }

    private String buildShopInfoKey(String shopId) {
        return SHOP_INFO_KEY_PREFIX + shopId;
    }

    private String buildShopItemListKey(String shopId) {
        return SHOP_ITEM_LIST_KEY_PREFIX + shopId;
    }

    private String buildShopEsIndexDelayKey(Long shopId) {
        return SHOP_ES_INDEX_DELAY_KEY_PREFIX + shopId;
    }

    private String buildShopInfoLockKey(String shopId) {
        return SHOP_LOCK_KEY_PREFIX + "info:" + shopId;
    }

    private String buildShopItemListLockKey(String shopId) {
        return SHOP_LOCK_KEY_PREFIX + "item-list:" + shopId;
    }

    private ShopVO toShopVO(Shop shop) {
        return new ShopVO(
                String.valueOf(shop.getId()),
                String.valueOf(shop.getUserId()),
                shop.getName(),
                shop.getAvatar(),
                shop.getDescription(),
                shop.getAddress(),
                shop.getLongitude(),
                shop.getLatitude(),
                shop.getDeliveryFee(),
                shop.getOpenTime() == null ? null : shop.getOpenTime().toString(),
                shop.getCloseTime() == null ? null : shop.getCloseTime().toString(),
                shop.getReviewScore(),
                shop.getReviewCount(),
                shop.getSalesCount(),
                shop.getStatus(),
                shop.getCreateTime(),
                shop.getUpdateTime()
        );
    }

    private ShopItemVO toShopItemVO(ShopItem item) {
        return new ShopItemVO(
                String.valueOf(item.getId()),
                String.valueOf(item.getShopId()),
                item.getName(),
                item.getImage(),
                item.getDescription(),
                item.getPrice(),
                item.getStatus(),
                item.getCreateTime(),
                item.getUpdateTime()
        );
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



    private ShopReviewVO toShopReviewVO(ShopReview review, List<String> images) {
        return new ShopReviewVO(
                String.valueOf(review.getId()),
                String.valueOf(review.getOrderId()),
                String.valueOf(review.getShopId()),
                String.valueOf(review.getUserId()),
                review.getScore(),
                review.getContent(),
                images,
                review.getCreateTime()
        );
    }

    private ShopReviewReplyVO toShopReviewReplyVO(ShopReviewReply reply) {
        return new ShopReviewReplyVO(
                String.valueOf(reply.getId()),
                String.valueOf(reply.getReviewId()),
                String.valueOf(reply.getUserId()),
                reply.getAtUserId() == null ? null : String.valueOf(reply.getAtUserId()),
                reply.getContent(),
                reply.getCreateTime()
        );
    }

    private Long parseNullableLongId(String value, String fieldName) {
        if (!hasText(value)) {
            return null;
        }
        return parseLongId(value, fieldName);
    }

    private String checkRequiredText(String value, int maxLength, String fieldName) {
        if (!hasText(value)) {
            throw new IllegalArgumentException(fieldName + "不能为空");
        }
        return checkTextLength(value.trim(), maxLength, fieldName);
    }

    private String checkOptionalText(String value, int maxLength, String fieldName) {
        if (value == null || value.isBlank()) {
            return null;
        }
        return checkTextLength(value.trim(), maxLength, fieldName);
    }

    private String checkTextLength(String value, int maxLength, String fieldName) {
        if (value.length() > maxLength) {
            throw new IllegalArgumentException(fieldName + "长度不能超过" + maxLength + "个字符");
        }
        return value;
    }

    private BigDecimal checkMoney(BigDecimal amount, String fieldName) {
        if (amount == null) {
            throw new IllegalArgumentException(fieldName + "不能为空");
        }
        if (amount.compareTo(BigDecimal.ZERO) < 0) {
            throw new IllegalArgumentException(fieldName + "不能小于0");
        }
        if (amount.stripTrailingZeros().scale() > 2) {
            throw new IllegalArgumentException(fieldName + "最多只能保留两位小数");
        }
        return amount;
    }

    private BigDecimal checkRequiredLongitude(BigDecimal longitude) {
        if (longitude == null) {
            throw new IllegalArgumentException("经度不能为空");
        }
        return checkLongitude(longitude);
    }

    private BigDecimal checkRequiredLatitude(BigDecimal latitude) {
        if (latitude == null) {
            throw new IllegalArgumentException("纬度不能为空");
        }
        return checkLatitude(latitude);
    }

    private BigDecimal checkLongitude(BigDecimal longitude) {
        if (longitude == null) {
            return null;
        }
        if (longitude.compareTo(MIN_LONGITUDE) < 0 || longitude.compareTo(MAX_LONGITUDE) > 0) {
            throw new IllegalArgumentException("经度范围必须在-180到180之间");
        }
        return longitude;
    }

    private BigDecimal checkLatitude(BigDecimal latitude) {
        if (latitude == null) {
            return null;
        }
        if (latitude.compareTo(MIN_LATITUDE) < 0 || latitude.compareTo(MAX_LATITUDE) > 0) {
            throw new IllegalArgumentException("纬度范围必须在-90到90之间");
        }
        return latitude;
    }

    private LocalTime parseRequiredTime(String value, String fieldName) {
        if (!hasText(value)) {
            throw new IllegalArgumentException(fieldName + "不能为空");
        }
        try {
            return LocalTime.parse(value.trim());
        } catch (DateTimeParseException ex) {
            throw new IllegalArgumentException(fieldName + "格式必须为HH:mm");
        }
    }

    private boolean hasText(String value) {
        return value != null && !value.isBlank();
    }
}
