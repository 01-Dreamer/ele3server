package top.zxylearn.service;

import co.elastic.clients.elasticsearch._types.DistanceUnit;
import co.elastic.clients.elasticsearch._types.GeoDistanceType;
import co.elastic.clients.elasticsearch._types.GeoLocation;
import co.elastic.clients.elasticsearch._types.SortOptions;
import co.elastic.clients.elasticsearch._types.SortOrder;
import co.elastic.clients.elasticsearch._types.query_dsl.BoolQuery;
import co.elastic.clients.elasticsearch._types.query_dsl.Query;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.conditions.update.LambdaUpdateWrapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.SerializationFeature;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.amqp.rabbit.core.RabbitTemplate;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.elasticsearch.client.elc.NativeQuery;
import org.springframework.data.elasticsearch.core.ElasticsearchOperations;
import org.springframework.data.elasticsearch.core.SearchHit;
import org.springframework.data.elasticsearch.core.SearchHits;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.ZSetOperations;
import org.springframework.data.redis.core.script.DefaultRedisScript;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.support.TransactionSynchronization;
import org.springframework.transaction.support.TransactionSynchronizationManager;
import top.zxylearn.constant.MqConstants;
import top.zxylearn.document.ShopDocument;
import top.zxylearn.dto.message.WebSocketMessageDTO;
import top.zxylearn.dto.ShopCreateRequest;
import top.zxylearn.dto.ShopItemCreateRequest;
import top.zxylearn.dto.ShopItemSwapRequest;
import top.zxylearn.dto.ShopItemUpdateRequest;
import top.zxylearn.dto.ShopReviewReplyRequest;
import top.zxylearn.dto.ShopStatusUpdateRequest;
import top.zxylearn.dto.ShopUpdateRequest;
import top.zxylearn.dto.shop.ShopEsIndexEventDTO;
import top.zxylearn.dto.risk.RiskTextRecordCreateEventDTO;
import top.zxylearn.dto.shop.ShopBillCreateRequest;
import top.zxylearn.dto.shop.ShopBillVO;
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
import top.zxylearn.vo.PageVO;
import top.zxylearn.vo.ShopItemVO;
import top.zxylearn.vo.ShopReviewReplyVO;
import top.zxylearn.vo.ShopReviewVO;
import top.zxylearn.vo.ShopVO;

import java.math.BigDecimal;
import java.time.Duration;
import java.time.LocalDate;
import java.time.LocalTime;
import java.time.format.DateTimeFormatter;
import java.time.format.DateTimeParseException;
import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
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
    private static final String SHOP_SEARCH_HOT_KEY_PREFIX = "shop:search:hot:";
    private static final String SHOP_SEARCH_SUGGEST_KEY_PREFIX = "shop:search:suggest:";
    private static final String NULL_CACHE_VALUE = "__NULL__";
    private static final DateTimeFormatter SEARCH_HOT_DATE_FORMATTER = DateTimeFormatter.BASIC_ISO_DATE;
    private static final String SEARCH_SORT_RATING = "rating";
    private static final String SEARCH_SORT_SALES = "sales";
    private static final String ES_FIELD_LOCATION = "location";
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
    private final ElasticsearchOperations elasticsearchOperations;
    private final ObjectMapper objectMapper;
    private final Duration cacheTtl;
    private final Duration nullCacheTtl;
    private final Duration lockTtl;
    private final int cacheTtlJitterSeconds;
    private final int nullCacheTtlJitterSeconds;
    private final Duration esIndexDelay;
    private final int defaultReviewPageSize;
    private final int maxReviewPageSize;
    private final int defaultSearchPageSize;
    private final int maxSearchPageSize;
    private final Duration hotSearchTtl;
    private final Duration suggestSearchTtl;
    private final int suggestMaxPrefixLength;

    public ShopService(ShopMapper shopMapper,
                       ShopItemMapper shopItemMapper,
                       ShopReviewMapper shopReviewMapper,
                       ShopReviewImageMapper shopReviewImageMapper,
                       ShopReviewReplyMapper shopReviewReplyMapper,
                       RabbitTemplate rabbitTemplate,
                       StringRedisTemplate stringRedisTemplate,
                       ElasticsearchOperations elasticsearchOperations,
                       @Value("${shop.cache.ttl}") Duration cacheTtl,
                       @Value("${shop.cache.null-ttl}") Duration nullCacheTtl,
                       @Value("${shop.cache.lock-ttl}") Duration lockTtl,
                       @Value("${shop.cache.ttl-jitter-seconds}") int cacheTtlJitterSeconds,
                       @Value("${shop.cache.null-ttl-jitter-seconds}") int nullCacheTtlJitterSeconds,
                       @Value("${shop.es-index.delay}") Duration esIndexDelay,
                       @Value("${shop.review.page-size.default}") int defaultReviewPageSize,
                       @Value("${shop.review.page-size.max}") int maxReviewPageSize,
                       @Value("${shop.search.page-size.default}") int defaultSearchPageSize,
                       @Value("${shop.search.page-size.max}") int maxSearchPageSize,
                       @Value("${shop.search.hot-ttl}") Duration hotSearchTtl,
                       @Value("${shop.search.suggest-ttl}") Duration suggestSearchTtl,
                       @Value("${shop.search.suggest-max-prefix-length}") int suggestMaxPrefixLength) {
        this.shopMapper = shopMapper;
        this.shopItemMapper = shopItemMapper;
        this.shopReviewMapper = shopReviewMapper;
        this.shopReviewImageMapper = shopReviewImageMapper;
        this.shopReviewReplyMapper = shopReviewReplyMapper;
        this.rabbitTemplate = rabbitTemplate;
        this.stringRedisTemplate = stringRedisTemplate;
        this.elasticsearchOperations = elasticsearchOperations;
        this.cacheTtl = cacheTtl;
        this.nullCacheTtl = nullCacheTtl;
        this.lockTtl = lockTtl;
        this.cacheTtlJitterSeconds = cacheTtlJitterSeconds;
        this.nullCacheTtlJitterSeconds = nullCacheTtlJitterSeconds;
        this.esIndexDelay = esIndexDelay;
        this.defaultReviewPageSize = defaultReviewPageSize;
        this.maxReviewPageSize = maxReviewPageSize;
        this.defaultSearchPageSize = defaultSearchPageSize;
        this.maxSearchPageSize = maxSearchPageSize;
        this.hotSearchTtl = hotSearchTtl;
        this.suggestSearchTtl = suggestSearchTtl;
        this.suggestMaxPrefixLength = suggestMaxPrefixLength;
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
        publishRiskText("SHOP", String.valueOf(shop.getId()), userId, shop.getName() + " " + shop.getDescription());
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
            publishRiskText("SHOP", shopId, userId, shop.getName());
        }
        if (hasText(request.getAvatar())) {
            String avatar = checkOptionalText(request.getAvatar(), 500, "店铺头像URL");
            publishOldImageDeleteAfterCommit(shop.getAvatar(), avatar);
            shop.setAvatar(avatar);
        }
        if (hasText(request.getDescription())) {
            shop.setDescription(checkRequiredText(request.getDescription(), 500, "店铺描述"));
            publishRiskText("SHOP", shopId, userId, shop.getDescription());
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
        item.setSort(0L); // 临时值，insert 后替换为雪花 ID
        item.setStatus(STATUS_NORMAL);
        shopItemMapper.insert(item);
        publishRiskText("SHOP_ITEM", String.valueOf(item.getId()), userId, item.getName() + " " + item.getDescription());
        item.setSort(item.getId());
        shopItemMapper.updateById(item);
        refreshShopItemListCache(shop.getId());
        publishShopEsIndexAfterCommit(shop.getId(), ShopEsIndexEventDTO.ACTION_UPSERT);
        return toShopItemVO(item);
    }

    @Transactional(rollbackFor = Exception.class)
    public void deleteItem(String userId, String itemId) {
        ShopItem item = getShopItemById(itemId);
        Shop shop = getOwnShop(userId, String.valueOf(item.getShopId()));
        checkShopAvailable(shop);
        shopItemMapper.deleteById(item.getId());
        refreshShopItemListCache(shop.getId());
        publishShopEsIndexAfterCommit(shop.getId(), ShopEsIndexEventDTO.ACTION_UPSERT);
    }

    @Transactional(rollbackFor = Exception.class)
    public ShopItemVO updateItem(String userId, String itemId, ShopItemUpdateRequest request) {
        if (request == null) {
            throw new IllegalArgumentException("商品修改参数不能为空");
        }
        ShopItem item = getShopItemById(itemId);
        Shop shop = getOwnShop(userId, String.valueOf(item.getShopId()));
        checkShopAvailable(shop);

        boolean changed = false;
        if (hasText(request.getName())) {
            item.setName(checkRequiredText(request.getName(), 100, "商品名称"));
            publishRiskText("SHOP_ITEM", itemId, userId, item.getName());
            changed = true;
        }
        if (request.getImage() != null) {
            String image = request.getImage().isBlank() ? null : checkOptionalText(request.getImage(), 500, "商品图片URL");
            publishOldImageDeleteAfterCommit(item.getImage(), image);
            item.setImage(image);
            changed = true;
        }
        if (hasText(request.getDescription())) {
            item.setDescription(checkRequiredText(request.getDescription(), 500, "商品描述"));
            publishRiskText("SHOP_ITEM", itemId, userId, item.getDescription());
            changed = true;
        }
        if (request.getPrice() != null) {
            item.setPrice(checkMoney(request.getPrice(), "商品价格"));
            changed = true;
        }
        if (request.getStatus() != null) {
            int status = request.getStatus();
            if (status != 0 && status != 1) {
                throw new IllegalArgumentException("商品状态只能为0正常或1下架");
            }
            item.setStatus(status);
            changed = true;
        }

        if (!changed) {
            return toShopItemVO(item);
        }
        shopItemMapper.updateById(item);
        refreshShopItemListCache(shop.getId());
        publishShopEsIndexAfterCommit(shop.getId(), ShopEsIndexEventDTO.ACTION_UPSERT);
        return toShopItemVO(shopItemMapper.selectById(item.getId()));
    }

    @Transactional(rollbackFor = Exception.class)
    public void swapItems(String userId, ShopItemSwapRequest request) {
        if (request == null) {
            throw new IllegalArgumentException("交换参数不能为空");
        }
        ShopItem itemA = getShopItemById(request.getItemIdA());
        ShopItem itemB = getShopItemById(request.getItemIdB());
        if (!itemA.getShopId().equals(itemB.getShopId())) {
            throw new IllegalArgumentException("两个商品不属于同一店铺");
        }
        Shop shop = getOwnShop(userId, String.valueOf(itemA.getShopId()));
        checkShopAvailable(shop);

        Long sortA = itemA.getSort();
        Long sortB = itemB.getSort();
        itemA.setSort(sortB);
        itemB.setSort(sortA);
        shopItemMapper.updateById(itemA);
        shopItemMapper.updateById(itemB);
        refreshShopItemListCache(shop.getId());
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
        refreshShopItemListCache(item.getShopId());
        publishShopEsIndexAfterCommit(item.getShopId(), ShopEsIndexEventDTO.ACTION_UPSERT);
    }

    public PageVO<ShopVO> listOwnShops(String userId, Integer page, Integer size) {
        Long userLong = parseLongId(userId, "用户ID");
        int pageNum = page != null && page > 0 ? page : 1;
        int pageSize = normalizePageSize(size);

        Page<Shop> mpPage = new Page<>(pageNum, pageSize);
        Page<Shop> result = shopMapper.selectPage(mpPage,
                new LambdaQueryWrapper<Shop>().eq(Shop::getUserId, userLong));

        List<ShopVO> items = result.getRecords().stream().map(this::toShopVO).toList();
        return new PageVO<>(items, result.getTotal(), pageNum, pageSize);
    }

    public ShopVO getShop(String shopId) {
        ShopVO shopVO = getShopWithCache(shopId);
        checkShopAvailable(shopVO);
        return shopVO;
    }

    public ShopVO getShopForUser(String userId, String shopId) {
        Long userLong = userId != null ? parseLongId(userId, "用户ID") : null;
        ShopVO shopVO = getShopWithCache(shopId);
        if (userLong != null && String.valueOf(userLong).equals(shopVO.getUserId())) {
            return shopVO;
        }
        checkShopAvailable(shopVO);
        return shopVO;
    }

    public ShopVO getShopByAdmin(String shopId) {
        return getShopWithCache(shopId);
    }

    public List<ShopItemVO> listShopItems(String userId, String shopId) {
        ShopVO shopVO = getShopForUser(userId, shopId);
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

        ShopReview review = getAvailableReview(String.valueOf(reviewId));

        ShopReviewReply reply = new ShopReviewReply();
        reply.setReviewId(reviewId);
        reply.setUserId(replyUserId);
        reply.setAtUserId(atUserId);
        reply.setContent(content);
        shopReviewReplyMapper.insert(reply);
        publishRiskText("REVIEW_REPLY", String.valueOf(reply.getId()), userId, content);

        String snippet = review.getContent() != null && review.getContent().length() > 30
                ? review.getContent().substring(0, 30) + "…" : review.getContent();
        String noticeContent = "您的评论「" + snippet + "」被回复";
        // 通知原评论者
        if (!replyUserId.equals(review.getUserId())) {
            sendMqNotice(String.valueOf(review.getUserId()), "评论被回复", noticeContent);
        }
        // 通知被@用户
        if (atUserId != null && !atUserId.equals(replyUserId) && !atUserId.equals(review.getUserId())) {
            sendMqNotice(String.valueOf(atUserId), "评论被回复", noticeContent);
        }
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
        publishRiskText("REVIEW", String.valueOf(review.getId()), String.valueOf(userId), content);

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

    public ShopBillVO createBill(ShopBillCreateRequest request) {
        if (request == null) {
            throw new IllegalArgumentException("账单参数不能为空");
        }
        Long shopId = parseLongId(request.getShopId(), "店铺ID");
        Shop shop = shopMapper.selectById(shopId);
        if (shop == null) {
            throw new IllegalArgumentException("店铺不存在");
        }
        checkShopAvailable(shop);

        List<ShopBillCreateRequest.ItemEntry> requestItems = request.getItems();
        if (requestItems == null || requestItems.isEmpty()) {
            throw new IllegalArgumentException("购买商品列表不能为空");
        }

        List<Long> itemIds = new java.util.ArrayList<>();
        java.util.Map<Long, Integer> quantityMap = new java.util.LinkedHashMap<>();
        for (ShopBillCreateRequest.ItemEntry entry : requestItems) {
            Long itemId = parseLongId(entry.getItemId(), "商品ID");
            Integer quantity = entry.getQuantity();
            if (quantity == null || quantity <= 0) {
                throw new IllegalArgumentException("商品" + entry.getItemId() + "的购买数量必须大于0");
            }
            if (quantityMap.containsKey(itemId)) {
                throw new IllegalArgumentException("商品" + entry.getItemId() + "重复提交");
            }
            quantityMap.put(itemId, quantity);
            itemIds.add(itemId);
        }

        List<ShopItem> items = shopItemMapper.selectBatchIds(itemIds);
        if (items.size() != itemIds.size()) {
            throw new IllegalArgumentException("部分商品不存在");
        }

        java.util.Map<Long, ShopItem> itemMap = new java.util.LinkedHashMap<>();
        for (ShopItem item : items) {
            itemMap.put(item.getId(), item);
        }

        BigDecimal itemsTotal = BigDecimal.ZERO;
        List<ShopBillVO.ItemEntry> billItems = new java.util.ArrayList<>();

        for (Long itemId : itemIds) {
            ShopItem item = itemMap.get(itemId);
            if (!shopId.equals(item.getShopId())) {
                throw new IllegalArgumentException("商品\"" + item.getName() + "\"不属于该店铺");
            }
            if (item.getStatus() != null && item.getStatus() != 0) {
                throw new IllegalArgumentException("商品\"" + item.getName() + "\"已下架");
            }
            Integer quantity = quantityMap.get(itemId);
            BigDecimal subtotal = item.getPrice().multiply(BigDecimal.valueOf(quantity));
            itemsTotal = itemsTotal.add(subtotal);
            billItems.add(new ShopBillVO.ItemEntry(
                    String.valueOf(item.getId()),
                    item.getName(),
                    item.getPrice(),
                    quantity,
                    subtotal
            ));
        }

        BigDecimal deliveryFee = shop.getDeliveryFee() != null ? shop.getDeliveryFee() : BigDecimal.ZERO;
        BigDecimal totalAmount = itemsTotal.add(deliveryFee);

        return new ShopBillVO(
                String.valueOf(shop.getId()),
                shop.getName(),
                String.valueOf(shop.getUserId()),
                deliveryFee,
                billItems,
                itemsTotal,
                totalAmount
        );
    }


    public List<String> listHotSearch() {
        return readTopZSetMembers(buildHotSearchKey(), 10);
    }

    public List<String> suggestSearch(String query) {
        String normalizedQuery = normalizeSearchQuery(query);
        if (!hasText(normalizedQuery)) {
            throw new IllegalArgumentException("搜索提示关键字不能为空");
        }
        return readTopZSetMembers(buildSuggestSearchKey(normalizedQuery), 10);
    }

    public CursorPageVO<ShopVO> searchShops(BigDecimal longitude,
                                            BigDecimal latitude,
                                            String query,
                                            String sort,
                                            String cursor,
                                            Integer size) {
        int pageSize = normalizeSearchPageSize(size);
        boolean hasLongitude = longitude != null;
        boolean hasLatitude = latitude != null;
        if (!hasLongitude != !hasLatitude) {
            throw new IllegalArgumentException("经纬度必须同时传入");
        }
        String normalizedQuery = normalizeSearchQuery(query);
        recordSearchKeyword(normalizedQuery);
        String normalizedSort = normalizeSearchSort(sort);
        BigDecimal normalizedLongitude = hasLongitude ? checkLongitude(longitude) : null;
        BigDecimal normalizedLatitude = hasLatitude ? checkLatitude(latitude) : null;

        int expectedSortValues = hasText(normalizedQuery) ? 2 : (hasLongitude ? 3 : 2);
        Object[] searchAfter = parseSearchAfterCursor(cursor, expectedSortValues);
        return searchShopsFromEs(normalizedLongitude, normalizedLatitude, normalizedQuery, normalizedSort, searchAfter, pageSize);
    }

    public CursorPageVO<ShopVO> searchShopsFromMysql(String cursor, Integer size) {
        LambdaQueryWrapper<Shop> wrapper = new LambdaQueryWrapper<Shop>()
                .eq(Shop::getStatus, STATUS_NORMAL)
                .orderByDesc(Shop::getReviewScore)
                .last("LIMIT 10");
        List<Shop> shops = shopMapper.selectList(wrapper);
        shops = shops.stream().filter(this::isShopVisibleNow).toList();
        List<ShopVO> records = shops.stream().map(this::toShopVO).toList();
        return new CursorPageVO<>(records, null, false);
    }

    private CursorPageVO<ShopVO> searchShopsFromEs(BigDecimal longitude,
                                                   BigDecimal latitude,
                                                   String keyword,
                                                   String sort,
                                                   Object[] searchAfter,
                                                   int pageSize) {
        int fetchSize = Math.min(pageSize * 4 + 1, maxSearchPageSize * 4 + 1);
        List<ShopVO> records = new ArrayList<>(pageSize);
        boolean hasMore = false;
        Set<Long> seenIds = new LinkedHashSet<>();
        List<Object> lastRecordSortValues = null;

        List<Double> radiusPlan = searchAfter != null
                ? java.util.Collections.singletonList(null)
                : buildSearchRadiusPlan(longitude);

        for (Double radiusKm : radiusPlan) {
            while (records.size() < pageSize + 1) {
                SearchHits<ShopDocument> hits = searchShopDocuments(longitude, latitude, keyword, sort, radiusKm, searchAfter, fetchSize);
                List<SearchHit<ShopDocument>> hitList = hits.getSearchHits();
                if (hitList.isEmpty()) {
                    break;
                }
                Map<Long, List<Object>> sortValuesMap = new LinkedHashMap<>();
                List<Long> ids = new ArrayList<>();
                for (SearchHit<ShopDocument> hit : hitList) {
                    Long id = hit.getContent().getId();
                    if (id != null && seenIds.add(id)) {
                        ids.add(id);
                        sortValuesMap.put(id, hit.getSortValues());
                    }
                }
                if (ids.isEmpty()) {
                    searchAfter = hitList.get(hitList.size() - 1).getSortValues().toArray();
                    if (hitList.size() < fetchSize) break;
                    continue;
                }
                for (Shop shop : selectShopsByIdsInOrder(ids)) {
                    if (isShopVisibleNow(shop)) {
                        records.add(toShopVO(shop));
                        lastRecordSortValues = sortValuesMap.get(shop.getId());
                        if (records.size() == pageSize + 1) {
                            hasMore = true;
                            break;
                        }
                    }
                }
                if (records.size() >= pageSize + 1) {
                    break;
                }
                if (hitList.size() == fetchSize) {
                    searchAfter = hitList.get(hitList.size() - 1).getSortValues().toArray();
                } else {
                    break;
                }
            }
            if (records.size() >= pageSize + 1) {
                break;
            }
        }

        if (records.size() > pageSize) {
            records = records.subList(0, pageSize);
        }

        String nextCursor = null;
        if (hasMore && lastRecordSortValues != null) {
            nextCursor = encodeSearchAfterCursorFromSortValues(lastRecordSortValues);
        }
        return new CursorPageVO<>(records, nextCursor, hasMore);
    }

    private static final List<Double> SEARCH_RADIUS_PLAN = java.util.Arrays.asList(5.0, 10.0, 30.0, 50.0, null);

    private List<Double> buildSearchRadiusPlan(BigDecimal longitude) {
        return longitude != null ? SEARCH_RADIUS_PLAN : java.util.Collections.singletonList(null);
    }

    private Query buildShopSearchQuery(BigDecimal longitude,
                                       BigDecimal latitude,
                                       String keyword,
                                       Double radiusKm) {
        BoolQuery.Builder bool = new BoolQuery.Builder();
        bool.filter(q -> q.term(t -> t.field("status").value(STATUS_NORMAL)));
        if (radiusKm != null && longitude != null) {
            bool.filter(q -> q.geoDistance(g -> g
                    .field(ES_FIELD_LOCATION)
                    .location(buildGeoLocation(longitude, latitude))
                    .distance(formatRadius(radiusKm))
                    .distanceType(GeoDistanceType.Arc)));
        }
        if (hasText(keyword)) {
            String normalizedKeyword = keyword.trim();
            bool.must(q -> q.multiMatch(m -> m
                    .query(normalizedKeyword)
                    .fields("name^3", "description^2", "item_content")
                    .minimumShouldMatch("75%")));
        } else {
            bool.must(q -> q.matchAll(m -> m));
        }
        return new Query.Builder().bool(bool.build()).build();
    }

    private List<SortOptions> buildShopSearchSort(BigDecimal longitude, BigDecimal latitude, String sort,
                                                   boolean hasKeyword) {
        List<SortOptions> sortOptions = new ArrayList<>();
        boolean hasLocation = longitude != null;
        if (hasKeyword) {
            sortOptions.add(SortOptions.of(s -> s.score(f -> f.order(SortOrder.Desc))));
        } else if (SEARCH_SORT_SALES.equals(sort)) {
            sortOptions.add(SortOptions.of(s -> s.field(f -> f.field("sales_count").order(SortOrder.Desc).missing(0))));
            if (hasLocation) {
                sortOptions.add(buildGeoDistanceSort(longitude, latitude));
            }
        } else {
            sortOptions.add(SortOptions.of(s -> s.field(f -> f.field("review_score").order(SortOrder.Desc).missing(0))));
            if (hasLocation) {
                sortOptions.add(buildGeoDistanceSort(longitude, latitude));
            }
        }
        sortOptions.add(SortOptions.of(s -> s.field(f -> f.field("id").order(SortOrder.Desc))));
        return sortOptions;
    }

    private SearchHits<ShopDocument> searchShopDocuments(BigDecimal longitude,
                                                         BigDecimal latitude,
                                                         String keyword,
                                                         String sort,
                                                         Double radiusKm,
                                                         Object[] searchAfter,
                                                         int fetchSize) {
        NativeQuery nativeQuery = NativeQuery.builder()
                .withQuery(buildShopSearchQuery(longitude, latitude, keyword, radiusKm))
                .withSort(buildShopSearchSort(longitude, latitude, sort, hasText(keyword)))
                .withSearchAfter(searchAfter != null ? List.of(searchAfter) : null)
                .withPageable(PageRequest.of(0, fetchSize))
                .build();
        return elasticsearchOperations.search(nativeQuery, ShopDocument.class);
    }

    private String encodeSearchAfterCursorFromSortValues(List<Object> sortValues) {
        if (sortValues == null || sortValues.isEmpty()) {
            return null;
        }
        return encodeSearchAfterCursor(sortValues.toArray());
    }

    private SortOptions buildGeoDistanceSort(BigDecimal longitude, BigDecimal latitude) {
        return SortOptions.of(s -> s.geoDistance(g -> g
                .field(ES_FIELD_LOCATION)
                .location(buildGeoLocation(longitude, latitude))
                .order(SortOrder.Asc)
                .unit(DistanceUnit.Kilometers)
                .distanceType(GeoDistanceType.Arc)
                .ignoreUnmapped(true)));
    }

    private GeoLocation buildGeoLocation(BigDecimal longitude, BigDecimal latitude) {
        return GeoLocation.of(g -> g.latlon(ll -> ll
                .lat(latitude.doubleValue())
                .lon(longitude.doubleValue())));
    }

    private String formatRadius(Double radiusKm) {
        if (radiusKm == null) {
            return null;
        }
        if (Math.floor(radiusKm) == radiusKm) {
            return radiusKm.longValue() + "km";
        }
        return radiusKm + "km";
    }

    private List<Shop> selectShopsByIdsInOrder(List<Long> ids) {
        if (ids == null || ids.isEmpty()) {
            return Collections.emptyList();
        }
        List<Shop> shops = shopMapper.selectBatchIds(ids);
        Map<Long, Shop> shopMap = new LinkedHashMap<>();
        for (Shop shop : shops) {
            shopMap.put(shop.getId(), shop);
        }
        return ids.stream()
                .map(shopMap::get)
                .filter(shop -> shop != null)
                .toList();
    }

    private boolean isShopVisibleNow(Shop shop) {
        return shop != null
                && Integer.valueOf(STATUS_NORMAL).equals(shop.getStatus())
                && isOpenNow(shop.getOpenTime(), shop.getCloseTime());
    }

    private boolean isOpenNow(LocalTime openTime, LocalTime closeTime) {
        if (openTime == null || closeTime == null) {
            return false;
        }
        if (openTime.equals(closeTime)) {
            return true;
        }
        LocalTime now = LocalTime.now();
        if (openTime.isBefore(closeTime)) {
            return !now.isBefore(openTime) && !now.isAfter(closeTime);
        }
        return !now.isBefore(openTime) || !now.isAfter(closeTime);
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


    private void recordSearchKeyword(String query) {
        if (!hasText(query)) {
            return;
        }
        String hotKey = buildHotSearchKey();
        stringRedisTemplate.opsForZSet().incrementScore(hotKey, query, 1D);
        stringRedisTemplate.expire(hotKey, hotSearchTtl);

        int maxLength = Math.min(query.length(), Math.max(suggestMaxPrefixLength, 1));
        for (int i = 1; i <= maxLength; i++) {
            String suggestKey = buildSuggestSearchKey(query.substring(0, i));
            stringRedisTemplate.opsForZSet().incrementScore(suggestKey, query, 1D);
            stringRedisTemplate.expire(suggestKey, suggestSearchTtl);
        }
    }

    private List<String> readTopZSetMembers(String key, int limit) {
        Set<String> values = stringRedisTemplate.opsForZSet()
                .reverseRange(key, 0, Math.max(limit, 1) - 1L);
        if (values == null || values.isEmpty()) {
            return Collections.emptyList();
        }
        return List.copyOf(values);
    }

    private String normalizeSearchQuery(String query) {
        if (!hasText(query)) {
            return null;
        }
        return query.trim();
    }

    private String buildHotSearchKey() {
        return SHOP_SEARCH_HOT_KEY_PREFIX + SEARCH_HOT_DATE_FORMATTER.format(LocalDate.now());
    }

    private String buildSuggestSearchKey(String prefix) {
        return SHOP_SEARCH_SUGGEST_KEY_PREFIX + prefix;
    }

    private int normalizeSearchPageSize(Integer size) {
        if (size == null) {
            return defaultSearchPageSize;
        }
        if (size <= 0) {
            throw new IllegalArgumentException("分页大小必须大于0");
        }
        return Math.min(size, maxSearchPageSize);
    }

    /**
     * 解析游标为 search_after 值数组。游标格式：{v1}_{v2}_{...}
     * 若游标值个数与预期不符（如切换了排序方式），返回 null 从头开始。
     */
    private Object[] parseSearchAfterCursor(String cursor, int expectedCount) {
        if (!hasText(cursor)) {
            return null;
        }
        try {
            String[] parts = cursor.trim().split("_");
            if (parts.length != expectedCount) {
                return null; // 排序方式变了，忽略旧游标从头搜
            }
            Object[] values = new Object[parts.length];
            for (int i = 0; i < parts.length; i++) {
                String part = parts[i];
                if (part.contains(".")) {
                    values[i] = Double.parseDouble(part);
                } else {
                    values[i] = Long.parseLong(part);
                }
            }
            return values;
        } catch (Exception ex) {
            return null;
        }
    }

    private String encodeSearchAfterCursor(Object... values) {
        StringBuilder sb = new StringBuilder();
        for (int i = 0; i < values.length; i++) {
            if (i > 0) sb.append('_');
            sb.append(values[i]);
        }
        return sb.toString();
    }

    private String normalizeSearchSort(String sort) {
        if (!hasText(sort)) {
            return SEARCH_SORT_RATING;
        }
        String raw = sort.trim();
        String normalized = raw.toLowerCase();
        if ("score".equals(normalized) || "rating".equals(normalized)
                || "review".equals(normalized)
                || "评分".equals(raw) || "评价".equals(raw)
                || "评价平均分".equals(raw) || "综合".equals(raw)) {
            return SEARCH_SORT_RATING;
        }
        if ("sales".equals(normalized) || "销量".equals(raw)) {
            return SEARCH_SORT_SALES;
        }
        return SEARCH_SORT_RATING;
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

    private int normalizePageSize(Integer size) {
        if (size == null) {
            return 20;
        }
        if (size <= 0) {
            throw new IllegalArgumentException("分页大小必须大于0");
        }
        return Math.min(size, 100);
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
        refreshShopItemListCache(shop.getId());
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
                        .orderByAsc(ShopItem::getStatus)
                        .orderByAsc(ShopItem::getSort))
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

    private void refreshShopItemListCache(Long shopId) {
        if (shopId != null) {
            String normalizedShopId = String.valueOf(shopId);
            List<ShopItemVO> items = selectShopItems(normalizedShopId);
            cacheShopItemList(normalizedShopId, items);
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
                item.getSort(),
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

    private void publishRiskText(String sourceType, String sourceId, String userId, String content) {
        if (!hasText(content)) return;
        try {
            rabbitTemplate.convertAndSend(MqConstants.RISK_EXCHANGE, MqConstants.RISK_TEXT_RECORD_ROUTING_KEY,
                    new RiskTextRecordCreateEventDTO(sourceType, sourceId, userId, content));
        } catch (RuntimeException ex) {
            log.warn("风控文本事件发送失败", ex);
        }
    }

    private void sendMqNotice(String receiverId, String title, String content) {
        try {
            WebSocketMessageDTO<Map<String, String>> dto = WebSocketMessageDTO.notice(
                    receiverId, Map.of("title", title, "content", content));
            rabbitTemplate.convertAndSend(MqConstants.MESSAGE_EXCHANGE, MqConstants.MESSAGE_WS_ROUTING_KEY, dto);
        } catch (RuntimeException ex) {
            log.warn("通知发送失败 receiverId={}, title={}", receiverId, title, ex);
        }
    }

    private void publishOldImageDeleteAfterCommit(String oldUrl, String newUrl) {
        if (!hasText(oldUrl)) {
            return;
        }
        String normalizedOldUrl = oldUrl.trim();
        String normalizedNewUrl = newUrl == null ? null : newUrl.trim();
        if (normalizedOldUrl.equals(normalizedNewUrl)) {
            return;
        }
        Runnable publisher = () -> rabbitTemplate.convertAndSend(
                MqConstants.FILE_EXCHANGE,
                MqConstants.FILE_IMAGE_DELETE_ROUTING_KEY,
                normalizedOldUrl
        );
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
}
