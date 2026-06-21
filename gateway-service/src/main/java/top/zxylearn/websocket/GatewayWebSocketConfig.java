package top.zxylearn.websocket;

import org.springframework.context.annotation.Configuration;
import org.springframework.core.Ordered;
import org.springframework.web.socket.config.annotation.EnableWebSocket;
import org.springframework.web.socket.config.annotation.WebSocketConfigurer;
import org.springframework.web.socket.config.annotation.WebSocketHandlerRegistry;
import org.springframework.web.socket.config.annotation.ServletWebSocketHandlerRegistry;

@Configuration
@EnableWebSocket
public class GatewayWebSocketConfig implements WebSocketConfigurer {

    private final MessageWebSocketProxyHandler messageWebSocketProxyHandler;
    private final GatewayWebSocketHandshakeInterceptor gatewayWebSocketHandshakeInterceptor;

    public GatewayWebSocketConfig(MessageWebSocketProxyHandler messageWebSocketProxyHandler,
                                  GatewayWebSocketHandshakeInterceptor gatewayWebSocketHandshakeInterceptor) {
        this.messageWebSocketProxyHandler = messageWebSocketProxyHandler;
        this.gatewayWebSocketHandshakeInterceptor = gatewayWebSocketHandshakeInterceptor;
    }

    @Override
    public void registerWebSocketHandlers(WebSocketHandlerRegistry registry) {
        if (registry instanceof ServletWebSocketHandlerRegistry servletRegistry) {
            servletRegistry.setOrder(Ordered.HIGHEST_PRECEDENCE);
        }
        registry.addHandler(messageWebSocketProxyHandler, "/api/message/ws")
                .addInterceptors(gatewayWebSocketHandshakeInterceptor)
                .setAllowedOrigins("*");
    }
}
