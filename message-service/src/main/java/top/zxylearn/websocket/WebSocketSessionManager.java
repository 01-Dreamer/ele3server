package top.zxylearn.websocket;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;
import org.springframework.web.socket.TextMessage;
import org.springframework.web.socket.WebSocketSession;

import java.io.IOException;

import java.util.Collections;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;

@Component
public class WebSocketSessionManager {

    private final ConcurrentHashMap<String, Set<WebSocketSession>> userSessions = new ConcurrentHashMap<>();
    private final int maxConnectionsPerUser;

    public WebSocketSessionManager(@Value("${message.websocket.max-connections-per-user}") int maxConnectionsPerUser) {
        this.maxConnectionsPerUser = maxConnectionsPerUser;
    }

    public boolean canConnect(String userId) {
        return getConnectionCount(userId) < maxConnectionsPerUser;
    }

    public boolean addSession(String userId, WebSocketSession session) {
        Set<WebSocketSession> sessions = userSessions.computeIfAbsent(userId, key -> ConcurrentHashMap.newKeySet());
        synchronized (sessions) {
            removeClosedSessions(sessions);
            if (sessions.size() >= maxConnectionsPerUser) {
                return false;
            }
            sessions.add(session);
            return true;
        }
    }

    public void removeSession(String userId, WebSocketSession session) {
        Set<WebSocketSession> sessions = userSessions.get(userId);
        if (sessions == null) {
            return;
        }
        synchronized (sessions) {
            sessions.remove(session);
            removeClosedSessions(sessions);
            if (sessions.isEmpty()) {
                userSessions.remove(userId, sessions);
            }
        }
    }

    public int getConnectionCount(String userId) {
        Set<WebSocketSession> sessions = userSessions.getOrDefault(userId, Collections.emptySet());
        removeClosedSessions(sessions);
        return sessions.size();
    }

    public int sendToUser(String userId, String payload) {
        Set<WebSocketSession> sessions = userSessions.get(userId);
        if (sessions == null || sessions.isEmpty()) {
            return 0;
        }
        int successCount = 0;
        synchronized (sessions) {
            removeClosedSessions(sessions);
            for (WebSocketSession session : sessions) {
                if (send(session, payload)) {
                    successCount++;
                }
            }
        }
        return successCount;
    }

    public boolean hasLocalUser(String userId) {
        return getConnectionCount(userId) > 0;
    }

    private boolean send(WebSocketSession session, String payload) {
        if (!session.isOpen()) {
            return false;
        }
        try {
            session.sendMessage(new TextMessage(payload));
            return true;
        } catch (IOException ex) {
            return false;
        }
    }

    private void removeClosedSessions(Set<WebSocketSession> sessions) {
        sessions.removeIf(session -> !session.isOpen());
    }
}
