package top.zxylearn.controller;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import top.zxylearn.dto.OrderCreateRequest;
import top.zxylearn.dto.OrderReviewRequest;
import top.zxylearn.dto.payment.PaymentCreateVO;
import top.zxylearn.result.Result;
import top.zxylearn.service.OrderService;
import top.zxylearn.vo.OrderVO;
import top.zxylearn.vo.PageVO;

@Tag(name = "用户接口")
@RestController
@RequestMapping("/api/order")
public class ApiController {

    private static final Logger log = LoggerFactory.getLogger(ApiController.class);

    private final OrderService orderService;

    public ApiController(OrderService orderService) {
        this.orderService = orderService;
    }

    @Operation(summary = "获取自己的订单列表")
    @GetMapping("/list-order")
    public Result<PageVO<OrderVO>> listOrders(@RequestHeader("X-User-Id") String userId,
                                              @RequestParam(value = "status", required = false) Integer status,
                                              @RequestParam(value = "page", required = false) Long page,
                                              @RequestParam(value = "size", required = false) Long size) {
        try { return Result.success(orderService.listOrders(userId, status, page, size)); }
        catch (IllegalArgumentException ex) { return Result.fail(400, ex.getMessage()); }
        catch (RuntimeException ex) { log.error("订单列表获取失败", ex); return Result.fail(500, "订单列表获取失败"); }
    }

    @Operation(summary = "创建订单")
    @PostMapping("/create-order")
    public Result<OrderVO> createOrder(@RequestHeader("X-User-Id") String userId, @RequestBody OrderCreateRequest request) {
        try { return Result.success(orderService.createOrder(userId, request)); }
        catch (IllegalArgumentException ex) { return Result.fail(400, ex.getMessage()); }
        catch (RuntimeException ex) { log.error("订单创建失败", ex); return Result.fail(500, "订单创建失败"); }
    }

    @Operation(summary = "支付宝支付订单")
    @PostMapping("/pay-alipay/{orderId}")
    public Result<PaymentCreateVO> payByAlipay(@RequestHeader("X-User-Id") String userId, @PathVariable String orderId) {
        try { return Result.success(orderService.payByAlipay(userId, orderId)); }
        catch (IllegalArgumentException ex) { return Result.fail(400, ex.getMessage()); }
        catch (RuntimeException ex) { log.error("支付宝支付失败", ex); return Result.fail(500, "支付宝支付失败"); }
    }

    @Operation(summary = "钱包支付订单")
    @PostMapping("/pay-wallet/{orderId}")
    public Result<?> payByWallet(@RequestHeader("X-User-Id") String userId, @PathVariable String orderId) {
        try { orderService.payByWallet(userId, orderId); return Result.success(); }
        catch (IllegalArgumentException ex) { return Result.fail(400, ex.getMessage()); }
        catch (RuntimeException ex) { log.error("钱包支付失败", ex); return Result.fail(500, "钱包支付失败"); }
    }

    @Operation(summary = "商家接单")
    @PostMapping("/merchant-accept/{orderId}")
    public Result<?> merchantAccept(@RequestHeader("X-User-Id") String userId, @PathVariable String orderId) {
        try { orderService.merchantAccept(userId, orderId); return Result.success(); }
        catch (IllegalArgumentException ex) { return Result.fail(400, ex.getMessage()); }
        catch (RuntimeException ex) { log.error("商家接单失败", ex); return Result.fail(500, "商家接单失败"); }
    }

    @Operation(summary = "商家拒单")
    @PostMapping("/merchant-reject/{orderId}")
    public Result<?> merchantReject(@RequestHeader("X-User-Id") String userId, @PathVariable String orderId) {
        try { orderService.merchantReject(userId, orderId); return Result.success(); }
        catch (IllegalArgumentException ex) { return Result.fail(400, ex.getMessage()); }
        catch (RuntimeException ex) { log.error("商家拒单失败", ex); return Result.fail(500, "商家拒单失败"); }
    }

    @Operation(summary = "骑手接单")
    @PostMapping("/rider-accept/{orderId}")
    public Result<?> riderAccept(@RequestHeader("X-User-Id") String userId, @PathVariable String orderId) {
        try { orderService.riderAccept(userId, orderId); return Result.success(); }
        catch (IllegalArgumentException ex) { return Result.fail(400, ex.getMessage()); }
        catch (RuntimeException ex) { log.error("骑手接单失败", ex); return Result.fail(500, "骑手接单失败"); }
    }

    @Operation(summary = "骑手送达")
    @PostMapping("/rider-arrive/{orderId}")
    public Result<?> riderArrive(@RequestHeader("X-User-Id") String userId, @PathVariable String orderId) {
        try { orderService.riderArrive(userId, orderId); return Result.success(); }
        catch (IllegalArgumentException ex) { return Result.fail(400, ex.getMessage()); }
        catch (RuntimeException ex) { log.error("骑手送达失败", ex); return Result.fail(500, "骑手送达失败"); }
    }

    @Operation(summary = "用户评价订单")
    @PostMapping("/create-review/{orderId}")
    public Result<?> reviewOrder(@RequestHeader("X-User-Id") String userId, @PathVariable String orderId, @RequestBody OrderReviewRequest request) {
        try { orderService.reviewOrder(userId, orderId, request); return Result.success(); }
        catch (IllegalArgumentException ex) { return Result.fail(400, ex.getMessage()); }
        catch (RuntimeException ex) { log.error("订单评价失败", ex); return Result.fail(500, "订单评价失败"); }
    }
}
