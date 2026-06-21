package top.zxylearn.controller;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import top.zxylearn.dto.order.OrderPaidRequest;
import top.zxylearn.result.Result;
import top.zxylearn.service.OrderService;

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
}
