package top.zxylearn.service;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.data.elasticsearch.core.ElasticsearchOperations;
import org.springframework.data.elasticsearch.core.geo.GeoPoint;
import org.springframework.stereotype.Service;
import top.zxylearn.document.ShopDocument;
import top.zxylearn.dto.shop.ShopEsIndexEventDTO;
import top.zxylearn.entity.Shop;
import top.zxylearn.entity.ShopItem;
import top.zxylearn.mapper.ShopItemMapper;
import top.zxylearn.mapper.ShopMapper;

import java.math.BigDecimal;
import java.util.List;
import java.util.Objects;
import java.util.stream.Collectors;

@Service
public class ShopEsIndexService {

    private static final Logger log = LoggerFactory.getLogger(ShopEsIndexService.class);

    private final ShopMapper shopMapper;
    private final ShopItemMapper shopItemMapper;
    private final ElasticsearchOperations elasticsearchOperations;

    public ShopEsIndexService(ShopMapper shopMapper,
                             ShopItemMapper shopItemMapper,
                             ElasticsearchOperations elasticsearchOperations) {
        this.shopMapper = shopMapper;
        this.shopItemMapper = shopItemMapper;
        this.elasticsearchOperations = elasticsearchOperations;
    }

    public void sync(ShopEsIndexEventDTO event) {
        if (event == null || event.getShopId() == null || event.getShopId().isBlank()) {
            throw new IllegalArgumentException("店铺ES索引事件不能为空");
        }
        Long shopId = parseShopId(event.getShopId());
        if (ShopEsIndexEventDTO.ACTION_DELETE.equals(event.getAction())) {
            delete(shopId);
            return;
        }
        if (ShopEsIndexEventDTO.ACTION_UPSERT.equals(event.getAction())) {
            upsert(shopId);
            return;
        }
        throw new IllegalArgumentException("不支持的店铺ES索引动作: " + event.getAction());
    }

    private void upsert(Long shopId) {
        Shop shop = shopMapper.selectById(shopId);
        if (shop == null) {
            delete(shopId);
            return;
        }
        ShopDocument document = buildDocument(shop);
        elasticsearchOperations.save(document);
        log.info("店铺ES索引完成: shopId={}", shopId);
    }

    private void delete(Long shopId) {
        elasticsearchOperations.delete(String.valueOf(shopId), ShopDocument.class);
        log.info("店铺ES删除完成: shopId={}", shopId);
    }

    private ShopDocument buildDocument(Shop shop) {
        ShopDocument document = new ShopDocument();
        document.setId(shop.getId());
        document.setName(shop.getName());
        document.setAvatar(shop.getAvatar());
        document.setDescription(shop.getDescription());
        document.setAddress(shop.getAddress());
        if (shop.getLatitude() != null && shop.getLongitude() != null) {
            document.setLocation(new GeoPoint(shop.getLatitude().doubleValue(), shop.getLongitude().doubleValue()));
        }
        document.setOpenTime(shop.getOpenTime() == null ? null : shop.getOpenTime().toString());
        document.setCloseTime(shop.getCloseTime() == null ? null : shop.getCloseTime().toString());
        document.setReviewScore(toFloat(shop.getReviewScore()));
        document.setReviewCount(shop.getReviewCount());
        document.setSalesCount(shop.getSalesCount());
        document.setStatus(shop.getStatus());
        document.setItemContent(buildItemContent(shop.getId()));
        return document;
    }

    private String buildItemContent(Long shopId) {
        List<ShopItem> items = shopItemMapper.selectList(new LambdaQueryWrapper<ShopItem>()
                .eq(ShopItem::getShopId, shopId));
        return items.stream()
                .map(item -> String.join(" ",
                        nullToEmpty(item.getName()),
                        nullToEmpty(item.getDescription())))
                .filter(content -> !content.isBlank())
                .collect(Collectors.joining(" "));
    }

    private Float toFloat(BigDecimal value) {
        return value == null ? null : value.floatValue();
    }

    private String nullToEmpty(String value) {
        return Objects.toString(value, "");
    }

    private Long parseShopId(String shopId) {
        try {
            return Long.valueOf(shopId.trim());
        } catch (NumberFormatException ex) {
            throw new IllegalArgumentException("店铺ID格式不正确");
        }
    }
}
