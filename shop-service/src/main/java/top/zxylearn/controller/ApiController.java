package top.zxylearn.controller;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import top.zxylearn.dto.ShopCreateRequest;
import top.zxylearn.dto.ShopItemCreateRequest;
import top.zxylearn.dto.ShopItemSwapRequest;
import top.zxylearn.dto.ShopItemUpdateRequest;
import top.zxylearn.dto.ShopReviewReplyRequest;
import top.zxylearn.dto.ShopUpdateRequest;
import top.zxylearn.result.Result;
import top.zxylearn.service.ShopSearchResilienceService;
import top.zxylearn.service.ShopService;
import top.zxylearn.vo.CursorPageVO;
import top.zxylearn.vo.PageVO;
import top.zxylearn.vo.ShopItemVO;
import top.zxylearn.vo.ShopReviewReplyVO;
import top.zxylearn.vo.ShopReviewVO;
import top.zxylearn.vo.ShopVO;

import java.math.BigDecimal;
import java.util.List;

@Tag(name = "用户接口")
@RestController
@RequestMapping("/api/shop")
public class ApiController {

    private static final Logger log = LoggerFactory.getLogger(ApiController.class);

    private final ShopService shopService;
    private final ShopSearchResilienceService shopSearchResilienceService;

    public ApiController(ShopService shopService, ShopSearchResilienceService shopSearchResilienceService) {
        this.shopService = shopService;
        this.shopSearchResilienceService = shopSearchResilienceService;
    }

    @Operation(summary = "创建店铺")
    @PostMapping("/create-shop")
    public Result<ShopVO> createShop(@RequestHeader("X-User-Id") String userId,
                                     @RequestBody ShopCreateRequest request) {
        try {
            return Result.success(shopService.createShop(userId, request));
        } catch (IllegalArgumentException ex) {
            return Result.fail(400, ex.getMessage());
        } catch (RuntimeException ex) {
            log.error("店铺创建失败", ex);
            return Result.fail(500, "店铺创建失败");
        }
    }

    @Operation(summary = "修改店铺信息")
    @PutMapping("/modify-shop/{shopId}")
    public Result<ShopVO> updateShop(@RequestHeader("X-User-Id") String userId,
                                     @PathVariable String shopId,
                                     @RequestBody ShopUpdateRequest request) {
        try {
            return Result.success(shopService.updateShop(userId, shopId, request));
        } catch (IllegalArgumentException ex) {
            return Result.fail(400, ex.getMessage());
        } catch (RuntimeException ex) {
            log.error("店铺修改失败", ex);
            return Result.fail(500, "店铺修改失败");
        }
    }

    @Operation(summary = "给店铺添加商品")
    @PostMapping("/add-item/{shopId}")
    public Result<ShopItemVO> addItem(@RequestHeader("X-User-Id") String userId,
                                      @PathVariable String shopId,
                                      @RequestBody ShopItemCreateRequest request) {
        try {
            return Result.success(shopService.addItem(userId, shopId, request));
        } catch (IllegalArgumentException ex) {
            return Result.fail(400, ex.getMessage());
        } catch (RuntimeException ex) {
            log.error("商品添加失败", ex);
            return Result.fail(500, "商品添加失败");
        }
    }

    @Operation(summary = "删除店铺商品")
    @DeleteMapping("/delete-item/{itemId}")
    public Result<?> deleteItem(@RequestHeader("X-User-Id") String userId,
                                @PathVariable String itemId) {
        try {
            shopService.deleteItem(userId, itemId);
            return Result.success();
        } catch (IllegalArgumentException ex) {
            return Result.fail(400, ex.getMessage());
        } catch (RuntimeException ex) {
            log.error("商品删除失败", ex);
            return Result.fail(500, "商品删除失败");
        }
    }

    @Operation(summary = "修改店铺商品")
    @PutMapping("/modify-item/{itemId}")
    public Result<ShopItemVO> updateItem(@RequestHeader("X-User-Id") String userId,
                                          @PathVariable String itemId,
                                          @RequestBody ShopItemUpdateRequest request) {
        try {
            return Result.success(shopService.updateItem(userId, itemId, request));
        } catch (IllegalArgumentException ex) {
            return Result.fail(400, ex.getMessage());
        } catch (RuntimeException ex) {
            log.error("商品修改失败", ex);
            return Result.fail(500, "商品修改失败");
        }
    }

    @Operation(summary = "调换店铺两个商品的顺序")
    @PutMapping("/swap-items")
    public Result<?> swapItems(@RequestHeader("X-User-Id") String userId,
                               @RequestBody ShopItemSwapRequest request) {
        try {
            shopService.swapItems(userId, request);
            return Result.success();
        } catch (IllegalArgumentException ex) {
            return Result.fail(400, ex.getMessage());
        } catch (RuntimeException ex) {
            log.error("商品顺序调换失败", ex);
            return Result.fail(500, "商品顺序调换失败");
        }
    }

    @Operation(summary = "删除自己的店铺")
    @DeleteMapping("/delete-shop/{shopId}")
    public Result<?> deleteShop(@RequestHeader("X-User-Id") String userId,
                                @PathVariable String shopId) {
        try {
            shopService.deleteOwnShop(userId, shopId);
            return Result.success();
        } catch (IllegalArgumentException ex) {
            return Result.fail(400, ex.getMessage());
        } catch (RuntimeException ex) {
            log.error("店铺删除失败", ex);
            return Result.fail(500, "店铺删除失败");
        }
    }


    @Operation(summary = "获取自己的店铺列表")
    @GetMapping("/list-own-shop")
    public Result<PageVO<ShopVO>> listOwnShops(@RequestHeader("X-User-Id") String userId,
                                                @RequestParam(value = "page", required = false) Integer page,
                                                @RequestParam(value = "size", required = false) Integer size) {
        try {
            return Result.success(shopService.listOwnShops(userId, page, size));
        } catch (IllegalArgumentException ex) {
            return Result.fail(400, ex.getMessage());
        } catch (RuntimeException ex) {
            log.error("自己的店铺列表获取失败", ex);
            return Result.fail(500, "店铺列表获取失败");
        }
    }

    @Operation(summary = "根据店铺ID获取店铺信息")
    @GetMapping("/get-shop/{shopId}")
    public Result<ShopVO> getShop(@RequestHeader("X-User-Id") String userId,
                                  @PathVariable String shopId) {
        try {
            return Result.success(shopService.getShopForUser(userId, shopId));
        } catch (IllegalArgumentException ex) {
            return Result.fail(400, ex.getMessage());
        } catch (RuntimeException ex) {
            log.error("店铺获取失败", ex);
            return Result.fail(500, "店铺获取失败");
        }
    }

    @Operation(summary = "根据店铺ID获取商品列表")
    @GetMapping("/list-item/{shopId}")
    public Result<List<ShopItemVO>> listItems(@RequestHeader("X-User-Id") String userId,
                                              @PathVariable String shopId) {
        try {
            return Result.success(shopService.listShopItems(userId, shopId));
        } catch (IllegalArgumentException ex) {
            return Result.fail(400, ex.getMessage());
        } catch (RuntimeException ex) {
            log.error("商品列表获取失败", ex);
            return Result.fail(500, "商品列表获取失败");
        }
    }

    @Operation(summary = "搜索店铺")
    @GetMapping("/search-shop")
    public Result<CursorPageVO<ShopVO>> searchShops(@RequestParam(value = "longitude", required = false) BigDecimal longitude,
                                                    @RequestParam(value = "latitude", required = false) BigDecimal latitude,
                                                    @RequestParam(value = "query", required = false) String query,
                                                    @RequestParam(value = "sort", required = false) String sort,
                                                    @RequestParam(value = "cursor", required = false) String cursor,
                                                    @RequestParam(value = "size", required = false) Integer size) {
        try {
            return Result.success(shopSearchResilienceService.search(longitude, latitude, query, sort, cursor, size));
        } catch (ShopSearchResilienceService.DegradationException ex) {
            return new Result<>(200, "已降级查询", ex.getResult(), System.currentTimeMillis());
        } catch (IllegalArgumentException ex) {
            return Result.fail(400, ex.getMessage());
        } catch (RuntimeException ex) {
            log.error("店铺搜索失败", ex);
            return Result.fail(500, "店铺搜索失败");
        }
    }

    @Operation(summary = "查询热搜关键词")
    @GetMapping("/list-hot-search")
    public Result<List<String>> listHotSearch() {
        try {
            return Result.success(shopService.listHotSearch());
        } catch (RuntimeException ex) {
            log.error("热搜获取失败", ex);
            return Result.fail(500, "热搜获取失败");
        }
    }

    @Operation(summary = "搜索提示")
    @GetMapping("/suggest-search")
    public Result<List<String>> suggestSearch(@RequestParam("query") String query) {
        try {
            return Result.success(shopService.suggestSearch(query));
        } catch (IllegalArgumentException ex) {
            return Result.fail(400, ex.getMessage());
        } catch (RuntimeException ex) {
            log.error("搜索提示获取失败", ex);
            return Result.fail(500, "搜索提示获取失败");
        }
    }


    @Operation(summary = "查询店铺评价")
    @GetMapping("/list-review/{shopId}")
    public Result<CursorPageVO<ShopReviewVO>> listReviews(@PathVariable String shopId,
                                                          @RequestParam(value = "cursor", required = false) String cursor,
                                                          @RequestParam(value = "size", required = false) Integer size) {
        try {
            return Result.success(shopService.listReviews(shopId, cursor, size));
        } catch (IllegalArgumentException ex) {
            return Result.fail(400, ex.getMessage());
        } catch (RuntimeException ex) {
            log.error("评价列表获取失败", ex);
            return Result.fail(500, "评价列表获取失败");
        }
    }

    @Operation(summary = "查询评价回复")
    @GetMapping("/list-review-reply/{reviewId}")
    public Result<CursorPageVO<ShopReviewReplyVO>> listReviewReplies(@PathVariable String reviewId,
                                                                     @RequestParam(value = "cursor", required = false) String cursor,
                                                                     @RequestParam(value = "size", required = false) Integer size) {
        try {
            return Result.success(shopService.listReviewReplies(reviewId, cursor, size));
        } catch (IllegalArgumentException ex) {
            return Result.fail(400, ex.getMessage());
        } catch (RuntimeException ex) {
            log.error("评价回复列表获取失败", ex);
            return Result.fail(500, "评价回复列表获取失败");
        }
    }

    @Operation(summary = "回复店铺评价")
    @PostMapping("/reply-review")
    public Result<?> replyReview(@RequestHeader("X-User-Id") String userId,
                                 @RequestBody ShopReviewReplyRequest request) {
        try {
            shopService.replyReview(userId, request);
            return Result.success();
        } catch (IllegalArgumentException ex) {
            return Result.fail(400, ex.getMessage());
        } catch (RuntimeException ex) {
            log.error("评价回复失败", ex);
            return Result.fail(500, "评价回复失败");
        }
    }
}
