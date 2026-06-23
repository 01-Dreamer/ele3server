package top.zxylearn.client;

import org.springframework.cloud.openfeign.FeignClient;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestParam;
import top.zxylearn.result.Result;

import java.math.BigDecimal;
import java.util.List;
import java.util.Map;

@FeignClient(name = "shop-service")
public interface ShopToolClient {

    @GetMapping("/internal/shop/agent/hot-search")
    Result<List<String>> hotSearch();

    @GetMapping("/internal/shop/agent/search-shops")
    Result<Map<String, Object>> searchShops(@RequestParam("userId") String userId,
                                             @RequestParam(value = "keyword", required = false) String keyword,
                                             @RequestParam(value = "longitude", required = false) BigDecimal longitude,
                                             @RequestParam(value = "latitude", required = false) BigDecimal latitude,
                                             @RequestParam(value = "sort", required = false) String sort,
                                             @RequestParam(value = "cursor", required = false) String cursor,
                                             @RequestParam(value = "size", required = false) Integer size);

    @GetMapping("/internal/shop/agent/shop-detail")
    Result<Map<String, Object>> getShopDetail(@RequestParam("userId") String userId,
                                               @RequestParam("shopId") String shopId);
}
