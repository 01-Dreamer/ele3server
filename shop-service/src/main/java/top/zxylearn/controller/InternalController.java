package top.zxylearn.controller;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import top.zxylearn.dto.shop.ShopBillCreateRequest;
import top.zxylearn.dto.shop.ShopBillVO;
import top.zxylearn.dto.shop.ShopReviewCreateRequest;
import top.zxylearn.dto.shop.ShopSalesIncreaseRequest;
import top.zxylearn.result.Result;
import top.zxylearn.service.ShopService;

@Tag(name = "内部接口")
@RestController
@RequestMapping("/internal/shop")
public class InternalController {

    private static final Logger log = LoggerFactory.getLogger(InternalController.class);

    private final ShopService shopService;

    public InternalController(ShopService shopService) {
        this.shopService = shopService;
    }

    @Operation(summary = "创建店铺评价")
    @PostMapping("/create-review")
    public Result<?> createReview(@RequestBody ShopReviewCreateRequest request) {
        try {
            shopService.createReview(request);
            return Result.success();
        } catch (IllegalArgumentException ex) {
            return Result.fail(400, ex.getMessage());
        } catch (RuntimeException ex) {
            log.error("店铺评价创建失败", ex);
            return Result.fail(500, "店铺评价创建失败");
        }
    }


    @Operation(summary = "增加店铺销量")
    @PostMapping("/increase-sales")
    public Result<?> increaseSales(@RequestBody ShopSalesIncreaseRequest request) {
        try {
            shopService.increaseSales(request);
            return Result.success();
        } catch (IllegalArgumentException ex) {
            return Result.fail(400, ex.getMessage());
        } catch (RuntimeException ex) {
            log.error("店铺销量更新失败", ex);
            return Result.fail(500, "店铺销量更新失败");
        }
    }


    @Operation(summary = "创建账单")
    @PostMapping("/create-bill")
    public Result<ShopBillVO> createBill(@RequestBody ShopBillCreateRequest request) {
        try {
            return Result.success(shopService.createBill(request));
        } catch (IllegalArgumentException ex) {
            return Result.fail(400, ex.getMessage());
        } catch (RuntimeException ex) {
            log.error("账单创建失败", ex);
            return Result.fail(500, "账单创建失败");
        }
    }
}
