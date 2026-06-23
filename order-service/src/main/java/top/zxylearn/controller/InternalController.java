package top.zxylearn.controller;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import top.zxylearn.dto.order.OrderPaidRequest;
import top.zxylearn.result.Result;
import top.zxylearn.service.OrderService;
import top.zxylearn.vo.OrderVO;
import top.zxylearn.vo.PageVO;

@Tag(name = "内部接口")
@RestController
@RequestMapping("/internal/order")
public class InternalController {

    private final OrderService orderService;

    public InternalController(OrderService orderService) {
        this.orderService = orderService;
    }

    @Operation(summary = "订单支付成功后修改状态")
    @PostMapping("/mark-paid")
    public Result<?> markPaid(@RequestBody OrderPaidRequest request) {
        try {
            orderService.markPaid(request);
            return Result.success();
        } catch (IllegalArgumentException ex) {
            return Result.fail(400, ex.getMessage());
        } catch (RuntimeException ex) {
            return Result.fail(500, "订单状态修改失败");
        }
    }

    @Operation(summary = "获取用户最近的订单列表")
    @GetMapping("/list-recent")
    public Result<PageVO<OrderVO>> listRecentOrders(@RequestParam("userId") String userId,
                                                      @RequestParam(value = "limit", required = false) Integer limit) {
        try {
            return Result.success(orderService.listUserOrders(userId, null, 1L, limit != null ? limit.longValue() : 5L));
        } catch (IllegalArgumentException ex) {
            return Result.fail(400, ex.getMessage());
        } catch (RuntimeException ex) {
            return Result.fail(500, "订单列表获取失败");
        }
    }

    @Operation(summary = "获取订单详情（内部，含权限校验）")
    @GetMapping("/detail")
    public Result<OrderVO> getOrderDetail(@RequestParam("userId") String userId,
                                           @RequestParam("orderId") String orderId) {
        try {
            return Result.success(orderService.getOrderDetail(userId, orderId));
        } catch (IllegalArgumentException ex) {
            return Result.fail(400, ex.getMessage());
        } catch (RuntimeException ex) {
            return Result.fail(500, "订单详情获取失败");
        }
    }
}
