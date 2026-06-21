package top.zxylearn.client;

import org.springframework.cloud.openfeign.FeignClient;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import top.zxylearn.dto.shop.ShopBillCreateRequest;
import top.zxylearn.dto.shop.ShopBillVO;
import top.zxylearn.dto.shop.ShopReviewCreateRequest;
import top.zxylearn.dto.shop.ShopSalesIncreaseRequest;
import top.zxylearn.result.Result;

@FeignClient(name = "shop-service")
public interface ShopClient {
    @PostMapping("/internal/shop/create-bill")
    Result<ShopBillVO> createBill(@RequestBody ShopBillCreateRequest request);
    @PostMapping("/internal/shop/create-review")
    Result<?> createReview(@RequestBody ShopReviewCreateRequest request);
    @PostMapping("/internal/shop/increase-sales")
    Result<?> increaseSales(@RequestBody ShopSalesIncreaseRequest request);
}
