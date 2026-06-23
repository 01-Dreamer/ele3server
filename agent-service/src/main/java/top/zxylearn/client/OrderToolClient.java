package top.zxylearn.client;

import org.springframework.cloud.openfeign.FeignClient;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestParam;
import top.zxylearn.result.Result;

import java.util.Map;

@FeignClient(name = "order-service")
public interface OrderToolClient {

    @GetMapping("/internal/order/list-recent")
    Result<Map<String, Object>> listRecentOrders(@RequestParam("userId") String userId,
                                                   @RequestParam(value = "limit", required = false) Integer limit);

    @GetMapping("/internal/order/detail")
    Result<Map<String, Object>> getOrderDetail(@RequestParam("userId") String userId,
                                                @RequestParam("orderId") String orderId);
}
