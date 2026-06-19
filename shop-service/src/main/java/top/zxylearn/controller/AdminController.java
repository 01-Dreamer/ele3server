package top.zxylearn.controller;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import top.zxylearn.dto.ShopStatusUpdateRequest;
import top.zxylearn.result.Result;
import top.zxylearn.service.ShopService;
import top.zxylearn.vo.ShopItemVO;
import top.zxylearn.vo.ShopVO;

import java.util.List;

@Tag(name = "管理员接口")
@RestController
@RequestMapping("/api/shop/admin")
public class AdminController {

    private static final Logger log = LoggerFactory.getLogger(AdminController.class);

    private final ShopService shopService;

    public AdminController(ShopService shopService) {
        this.shopService = shopService;
    }

    @Operation(summary = "封禁或解封店铺")
    @PutMapping("/change-status/{shopId}")
    public Result<ShopVO> updateShopStatus(@PathVariable String shopId,
                                           @RequestBody ShopStatusUpdateRequest request) {
        try {
            return Result.success(shopService.updateShopStatus(shopId, request));
        } catch (IllegalArgumentException ex) {
            return Result.fail(400, ex.getMessage());
        } catch (RuntimeException ex) {
            log.error("店铺状态修改失败", ex);
            return Result.fail(500, "店铺状态修改失败");
        }
    }


    @Operation(summary = "根据店铺ID获取店铺信息")
    @GetMapping("/get-shop/{shopId}")
    public Result<ShopVO> getShop(@PathVariable String shopId) {
        try {
            return Result.success(shopService.getShopByAdmin(shopId));
        } catch (IllegalArgumentException ex) {
            return Result.fail(400, ex.getMessage());
        } catch (RuntimeException ex) {
            log.error("管理员获取店铺失败", ex);
            return Result.fail(500, "店铺获取失败");
        }
    }


    @Operation(summary = "根据店铺ID获取商品列表")
    @GetMapping("/list-item/{shopId}")
    public Result<List<ShopItemVO>> listItems(@PathVariable String shopId) {
        try {
            return Result.success(shopService.listShopItemsByAdmin(shopId));
        } catch (IllegalArgumentException ex) {
            return Result.fail(400, ex.getMessage());
        } catch (RuntimeException ex) {
            log.error("管理员获取商品列表失败", ex);
            return Result.fail(500, "商品列表获取失败");
        }
    }


    @Operation(summary = "删除任意店铺")
    @DeleteMapping("/delete-shop/{shopId}")
    public Result<?> deleteShop(@PathVariable String shopId) {
        try {
            shopService.deleteShopByAdmin(shopId);
            return Result.success();
        } catch (IllegalArgumentException ex) {
            return Result.fail(400, ex.getMessage());
        } catch (RuntimeException ex) {
            log.error("管理员删除店铺失败", ex);
            return Result.fail(500, "店铺删除失败");
        }
    }


    @Operation(summary = "删除任意店铺商品")
    @DeleteMapping("/delete-item/{itemId}")
    public Result<?> deleteItem(@PathVariable String itemId) {
        try {
            shopService.deleteItemByAdmin(itemId);
            return Result.success();
        } catch (IllegalArgumentException ex) {
            return Result.fail(400, ex.getMessage());
        } catch (RuntimeException ex) {
            log.error("管理员删除商品失败", ex);
            return Result.fail(500, "商品删除失败");
        }
    }
}
