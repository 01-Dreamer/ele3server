package top.zxylearn.client;

import org.springframework.cloud.openfeign.FeignClient;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import top.zxylearn.dto.order.OrderPaidRequest;
import top.zxylearn.result.Result;

@FeignClient(name = "order-service")
public interface OrderClient {

    @PostMapping("/internal/order/mark-paid")
    Result<?> markPaid(@RequestBody OrderPaidRequest request);
}
