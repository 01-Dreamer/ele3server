package top.zxylearn.websocket;

import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.cloud.client.ServiceInstance;
import org.springframework.cloud.client.loadbalancer.LoadBalancerClient;
import org.springframework.stereotype.Component;
import org.springframework.web.socket.CloseStatus;
import org.springframework.web.socket.TextMessage;
import org.springframework.web.socket.WebSocketHttpHeaders;
import org.springframework.web.socket.WebSocketSession;
import org.springframework.web.socket.client.standard.StandardWebSocketClient;
import org.springframework.web.socket.handler.TextWebSocketHandler;

import java.net.URI;
import java.time.Duration;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.TimeUnit;

@Slf4j
@Component
public class MessageWebSocketProxyHandler extends TextWebSocketHandler {

    private static final String USER_ID_HEADER = "X-User-Id";

    private final LoadBalancerClient loadBalancerClient;
    private final StandardWebSocketClient webSocketClient = new StandardWebSocketClient();
    private final ConcurrentHashMap<String, WebSocketSession> clientToBackend = new ConcurrentHashMap<>();
    private final ConcurrentHashMap<String, WebSocketSession> backendToClient = new ConcurrentHashMap<>();
    private final String messageServiceId;
    private final String messagePath;
    private final Duration connectTimeout;

    public MessageWebSocketProxyHandler(LoadBalancerClient loadBalancerClient,
                                        @Value("${message.websocket.message-service-id}") String messageServiceId,
                                        @Value("${message.websocket.message-path}") String messagePath,
                                        @Value("${message.websocket.connect-timeout}") Duration connectTimeout) {
        this.loadBalancerClient = loadBalancerClient;
        this.messageServiceId = messageServiceId;
        this.messagePath = messagePath;
        this.connectTimeout = connectTimeout;
    }

    @Override
    public void afterConnectionEstablished(WebSocketSession clientSession) throws Exception {
        String userId = getUserId(clientSession);
        if (userId == null || userId.isBlank()) {
            clientSession.close(CloseStatus.NOT_ACCEPTABLE.withReason("缺少X-User-Id"));
            return;
        }
        URI backendUri = resolveBackendUri();
        WebSocketHttpHeaders headers = new WebSocketHttpHeaders();
        headers.add(USER_ID_HEADER, userId);
        try {
            WebSocketSession backendSession = webSocketClient.execute(
                    new BackendWebSocketHandler(clientSession), headers, backendUri
            ).get(connectTimeout.toMillis(), TimeUnit.MILLISECONDS);
            clientToBackend.put(clientSession.getId(), backendSession);
            backendToClient.put(backendSession.getId(), clientSession);
            log.info("网关WebSocket代理建立 userId={}, clientSessionId={}, backendSessionId={}, backendUri={}",
                    userId, clientSession.getId(), backendSession.getId(), backendUri);
        } catch (Exception ex) {
            log.warn("网关WebSocket连接message-service失败 userId={}, backendUri={}", userId, backendUri, ex);
            clientSession.close(CloseStatus.SERVER_ERROR.withReason("连接message-service失败"));
        }
    }

    @Override
    protected void handleTextMessage(WebSocketSession clientSession, TextMessage message) throws Exception {
        WebSocketSession backendSession = clientToBackend.get(clientSession.getId());
        if (backendSession == null || !backendSession.isOpen()) {
            clientSession.close(CloseStatus.SERVER_ERROR.withReason("message-service连接不可用"));
            return;
        }
        backendSession.sendMessage(message);
    }

    @Override
    public void afterConnectionClosed(WebSocketSession clientSession, CloseStatus status) throws Exception {
        WebSocketSession backendSession = clientToBackend.remove(clientSession.getId());
        if (backendSession != null) {
            backendToClient.remove(backendSession.getId());
            if (backendSession.isOpen()) {
                backendSession.close(status);
            }
        }
        log.info("网关WebSocket客户端连接关闭 clientSessionId={}, status={}", clientSession.getId(), status);
    }

    @Override
    public void handleTransportError(WebSocketSession clientSession, Throwable exception) throws Exception {
        log.warn("网关WebSocket客户端传输异常 clientSessionId={}", clientSession.getId(), exception);
        if (clientSession.isOpen()) {
            clientSession.close(CloseStatus.SERVER_ERROR);
        }
    }

    private URI resolveBackendUri() {
        ServiceInstance instance = loadBalancerClient.choose(messageServiceId);
        if (instance == null) {
            throw new IllegalStateException("没有可用的message-service实例");
        }
        String scheme = instance.isSecure() ? "wss" : "ws";
        return URI.create(scheme + "://" + instance.getHost() + ":" + instance.getPort() + messagePath);
    }

    private String getUserId(WebSocketSession session) {
        Object userId = session.getAttributes().get(GatewayWebSocketHandshakeInterceptor.USER_ID_ATTRIBUTE);
        return userId == null ? null : String.valueOf(userId);
    }

    private class BackendWebSocketHandler extends TextWebSocketHandler {

        private final WebSocketSession clientSession;

        private BackendWebSocketHandler(WebSocketSession clientSession) {
            this.clientSession = clientSession;
        }

        @Override
        protected void handleTextMessage(WebSocketSession backendSession, TextMessage message) throws Exception {
            if (clientSession.isOpen()) {
                clientSession.sendMessage(message);
            }
        }

        @Override
        public void afterConnectionClosed(WebSocketSession backendSession, CloseStatus status) throws Exception {
            backendToClient.remove(backendSession.getId());
            clientToBackend.remove(clientSession.getId());
            if (clientSession.isOpen()) {
                clientSession.close(status);
            }
            log.info("网关WebSocket后端连接关闭 backendSessionId={}, status={}", backendSession.getId(), status);
        }

        @Override
        public void handleTransportError(WebSocketSession backendSession, Throwable exception) throws Exception {
            log.warn("网关WebSocket后端传输异常 backendSessionId={}", backendSession.getId(), exception);
            if (backendSession.isOpen()) {
                backendSession.close(CloseStatus.SERVER_ERROR);
            }
        }
    }
}
