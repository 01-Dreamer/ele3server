package top.zxylearn.service;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.SerializationFeature;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.script.DefaultRedisScript;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import top.zxylearn.dto.ShopCreateRequest;
import top.zxylearn.dto.ShopItemCreateRequest;
import top.zxylearn.dto.ShopStatusUpdateRequest;
import top.zxylearn.dto.ShopUpdateRequest;
import top.zxylearn.entity.Shop;
import top.zxylearn.entity.ShopItem;
import top.zxylearn.mapper.ShopItemMapper;
import top.zxylearn.mapper.ShopMapper;
import top.zxylearn.vo.ShopItemVO;
import top.zxylearn.vo.ShopVO;

import java.math.BigDecimal;
import java.time.Duration;
import java.time.LocalTime;
import java.time.format.DateTimeParseException;
import java.util.Collections;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.ThreadLocalRandom;

@Service
public class ShopService {

    private static final int STATUS_NORMAL = 0;
    private static final int STATUS_BANNED = 1;
    private static final BigDecimal ZERO_AMOUNT = new BigDecimal("0.00");
    private static final BigDecimal ZERO_SCORE = new BigDecimal("0.0");
    private static final BigDecimal MIN_LONGITUDE = new BigDecimal("-180");
    private static final BigDecimal MAX_LONGITUDE = new BigDecimal("180");
    private static final BigDecimal MIN_LATITUDE = new BigDecimal("-90");
    private static final BigDecimal MAX_LATITUDE = new BigDecimal("90");
    private static final String SHOP_INFO_KEY_PREFIX = "shop:info:";
    private static final String SHOP_ITEM_LIST_KEY_PREFIX = "shop:item:list:";
    private static final String SHOP_LOCK_KEY_PREFIX = "shop:lock:";
    private static final String NULL_CACHE_VALUE = "__NULL__";
    private static final Duration NULL_CACHE_TTL = Duration.ofMinutes(2);
    private static final Duration LOCK_TTL = Duration.ofSeconds(5);
    private static final int CACHE_TTL_JITTER_SECONDS = 60;
    private static final int NULL_CACHE_TTL_JITTER_SECONDS = 30;
    private static final DefaultRedisScript<Long> UNLOCK_SCRIPT = new DefaultRedisScript<>(
            "if redis.call('get', KEYS[1]) == ARGV[1] then return redis.call('del', KEYS[1]) else return 0 end",
            Long.class
    );

    private final ShopMapper shopMapper;
    private final ShopItemMapper shopItemMapper;
    private final StringRedisTemplate stringRedisTemplate;
    private final ObjectMapper objectMapper;
    private final Duration cacheTtl;

    public ShopService(ShopMapper shopMapper,
                       ShopItemMapper shopItemMapper,
                       StringRedisTemplate stringRedisTemplate,
                       @Value("${shop.cache.ttl:10m}") Duration cacheTtl) {
        this.shopMapper = shopMapper;
        this.shopItemMapper = shopItemMapper;
        this.stringRedisTemplate = stringRedisTemplate;
        this.cacheTtl = cacheTtl;
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
        shop.setAvatar(checkRequiredText(request.getAvatar(), 500, "店铺头像URL"));
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
        return shopVO;
    }

    @Transactional(rollbackFor = Exception.class)
    public ShopVO updateShop(String userId, String shopId, ShopUpdateRequest request) {
        if (request == null) {
            throw new IllegalArgumentException("店铺参数不能为空");
        }
        Shop shop = getOwnShop(userId, shopId);
        checkShopAvailable(shop);
        if (request.getName() != null) {
            shop.setName(checkRequiredText(request.getName(), 100, "店铺名称"));
        }
        if (request.getAvatar() != null) {
            shop.setAvatar(checkRequiredText(request.getAvatar(), 500, "店铺头像URL"));
        }
        if (request.getDescription() != null) {
            shop.setDescription(checkRequiredText(request.getDescription(), 500, "店铺描述"));
        }
        if (request.getAddress() != null) {
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
        if (request.getOpenTime() != null) {
            shop.setOpenTime(parseRequiredTime(request.getOpenTime(), "开始营业时间"));
        }
        if (request.getCloseTime() != null) {
            shop.setCloseTime(parseRequiredTime(request.getCloseTime(), "结束营业时间"));
        }
        shopMapper.updateById(shop);
        ShopVO shopVO = toShopVO(shopMapper.selectById(shop.getId()));
        cacheShop(shopVO);
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
        item.setDescription(checkOptionalText(request.getDescription(), 500, "商品描述"));
        item.setPrice(checkMoney(request.getPrice(), "商品价格"));
        item.setStatus(STATUS_NORMAL);
        shopItemMapper.insert(item);
        evictShopItemListCache(shop.getId());
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
        return shopVO;
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

    private void deleteShopWithItems(Shop shop) {
        shopItemMapper.delete(new LambdaQueryWrapper<ShopItem>().eq(ShopItem::getShopId, shop.getId()));
        shopMapper.deleteById(shop.getId());
        evictShopCache(shop.getId());
        evictShopItemListCache(shop.getId());
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
        Boolean locked = stringRedisTemplate.opsForValue().setIfAbsent(key, token, LOCK_TTL);
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
        return cacheTtl.plusSeconds(ThreadLocalRandom.current().nextInt(CACHE_TTL_JITTER_SECONDS + 1));
    }

    private Duration nullCacheTtlWithJitter() {
        return NULL_CACHE_TTL.plusSeconds(ThreadLocalRandom.current().nextInt(NULL_CACHE_TTL_JITTER_SECONDS + 1));
    }

    private String buildShopInfoKey(String shopId) {
        return SHOP_INFO_KEY_PREFIX + shopId;
    }

    private String buildShopItemListKey(String shopId) {
        return SHOP_ITEM_LIST_KEY_PREFIX + shopId;
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
