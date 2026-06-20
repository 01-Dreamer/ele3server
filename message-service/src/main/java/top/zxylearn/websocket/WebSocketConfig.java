package top.zxylearn.websocket;

import org.springframework.context.annotation.Configuration;
import org.springframework.web.socket.config.annotation.EnableWebSocket;
import org.springframework.web.socket.config.annotation.WebSocketConfigurer;
import org.springframework.web.socket.config.annotation.WebSocketHandlerRegistry;

@Configuration
@EnableWebSocket
public class WebSocketConfig implements WebSocketConfigurer {

    private final MessageWebSocketHandler messageWebSocketHandler;
    private final UserIdHandshakeInterceptor userIdHandshakeInterceptor;

    public WebSocketConfig(MessageWebSocketHandler messageWebSocketHandler,
                           UserIdHandshakeInterceptor userIdHandshakeInterceptor) {
        this.messageWebSocketHandler = messageWebSocketHandler;
        this.userIdHandshakeInterceptor = userIdHandshakeInterceptor;
    }

    @Override
    public void registerWebSocketHandlers(WebSocketHandlerRegistry registry) {
        registry.addHandler(messageWebSocketHandler, "/api/message/ws")
                .addInterceptors(userIdHandshakeInterceptor)
                .setAllowedOrigins("*");
    }
}
