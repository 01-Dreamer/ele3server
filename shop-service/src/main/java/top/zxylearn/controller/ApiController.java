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
import org.springframework.web.bind.annotation.RestController;
import top.zxylearn.dto.ShopCreateRequest;
import top.zxylearn.dto.ShopItemCreateRequest;
import top.zxylearn.dto.ShopUpdateRequest;
import top.zxylearn.result.Result;
import top.zxylearn.service.ShopService;
import top.zxylearn.vo.ShopItemVO;
import top.zxylearn.vo.ShopVO;

import java.util.List;

@Tag(name = "用户接口")
@RestController
@RequestMapping("/api/shop")
public class ApiController {

    private static final Logger log = LoggerFactory.getLogger(ApiController.class);

    private final ShopService shopService;

    public ApiController(ShopService shopService) {
        this.shopService = shopService;
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

    @Operation(summary = "修改自己的店铺信息")
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

    @Operation(summary = "给自己的店铺添加商品")
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

    @Operation(summary = "删除自己店铺的商品")
    @DeleteMapping("/delete-item/{shopId}/{itemId}")
    public Result<?> deleteItem(@RequestHeader("X-User-Id") String userId,
                                @PathVariable String shopId,
                                @PathVariable String itemId) {
        try {
            shopService.deleteItem(userId, shopId, itemId);
            return Result.success();
        } catch (IllegalArgumentException ex) {
            return Result.fail(400, ex.getMessage());
        } catch (RuntimeException ex) {
            log.error("商品删除失败", ex);
            return Result.fail(500, "商品删除失败");
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


    @Operation(summary = "根据店铺ID获取店铺信息")
    @GetMapping("/get-shop/{shopId}")
    public Result<ShopVO> getShop(@PathVariable String shopId) {
        try {
            return Result.success(shopService.getShop(shopId));
        } catch (IllegalArgumentException ex) {
            return Result.fail(400, ex.getMessage());
        } catch (RuntimeException ex) {
            log.error("店铺获取失败", ex);
            return Result.fail(500, "店铺获取失败");
        }
    }

    @Operation(summary = "根据店铺ID获取商品列表")
    @GetMapping("/list-item/{shopId}")
    public Result<List<ShopItemVO>> listItems(@PathVariable String shopId) {
        try {
            return Result.success(shopService.listShopItems(shopId));
        } catch (IllegalArgumentException ex) {
            return Result.fail(400, ex.getMessage());
        } catch (RuntimeException ex) {
            log.error("商品列表获取失败", ex);
            return Result.fail(500, "商品列表获取失败");
        }
    }
}
