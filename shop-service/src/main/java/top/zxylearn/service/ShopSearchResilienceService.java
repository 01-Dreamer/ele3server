package top.zxylearn.service;

import io.github.resilience4j.circuitbreaker.CallNotPermittedException;
import io.github.resilience4j.circuitbreaker.CircuitBreaker;
import io.github.resilience4j.circuitbreaker.CircuitBreakerRegistry;
import io.github.resilience4j.ratelimiter.RateLimiter;
import io.github.resilience4j.ratelimiter.RateLimiterRegistry;
import org.springframework.stereotype.Service;
import top.zxylearn.vo.CursorPageVO;
import top.zxylearn.vo.ShopVO;

import java.math.BigDecimal;
import java.util.function.Supplier;

@Service
public class ShopSearchResilienceService {

    private final ShopService shopService;
    private final RateLimiter rateLimiter;
    private final CircuitBreaker circuitBreaker;

    public ShopSearchResilienceService(ShopService shopService,
                                        RateLimiterRegistry rateLimiterRegistry,
                                        CircuitBreakerRegistry circuitBreakerRegistry) {
        this.shopService = shopService;
        this.rateLimiter = rateLimiterRegistry.rateLimiter("shopSearchLimiter");
        this.circuitBreaker = circuitBreakerRegistry.circuitBreaker("elasticsearch");
    }

    public CursorPageVO<ShopVO> search(BigDecimal longitude, BigDecimal latitude,
                                        String query, String sort, String cursor, Integer size) {
        // 限流
        if (!rateLimiter.acquirePermission()) {
            throw new IllegalArgumentException("系统繁忙，请稍后再试");
        }

        Supplier<CursorPageVO<ShopVO>> esSupplier = CircuitBreaker
                .decorateSupplier(circuitBreaker, () -> shopService.searchShops(longitude, latitude, query, sort, cursor, size));
        try {
            return esSupplier.get();
        } catch (CallNotPermittedException e) {
            // 熔断
            throw new IllegalArgumentException("服务不可用，请稍后再试");
        } catch (Exception e) {
            // 降级
            throw new DegradationException(shopService.searchShopsFromMysql(cursor, size));
        }
    }

    public static class DegradationException extends RuntimeException {
        private final CursorPageVO<ShopVO> result;
        public DegradationException(CursorPageVO<ShopVO> result) { this.result = result; }
        public CursorPageVO<ShopVO> getResult() { return result; }
    }
}
