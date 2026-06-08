package com.example.takeout.rider;

import com.example.takeout.common.dto.DeliveryTrackingSnapshot;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.net.URI;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import org.springframework.stereotype.Component;
import org.springframework.web.socket.CloseStatus;
import org.springframework.web.socket.TextMessage;
import org.springframework.web.socket.WebSocketSession;
import org.springframework.web.socket.handler.TextWebSocketHandler;

/**
 * 配送轨迹 WebSocket 处理器。
 * 用户端订阅订单后，会通过这个处理器收到骑手实时位置和轨迹快照。
 */
@Component
public class TrackingWebSocketHandler extends TextWebSocketHandler {

    private final ObjectMapper objectMapper;
    private final DeliveryTrackingStore trackingStore;
    private final Map<Long, Set<WebSocketSession>> sessionsByOrder = new ConcurrentHashMap<>();

    public TrackingWebSocketHandler(ObjectMapper objectMapper, DeliveryTrackingStore trackingStore) {
        this.objectMapper = objectMapper;
        this.trackingStore = trackingStore;
    }

    @Override
    public void afterConnectionEstablished(WebSocketSession session) throws Exception {
        Long orderId = extractOrderId(session.getUri());
        if (orderId == null) {
            session.close(CloseStatus.BAD_DATA);
            return;
        }
        session.getAttributes().put("orderId", orderId);
        sessionsByOrder.computeIfAbsent(orderId, key -> ConcurrentHashMap.newKeySet()).add(session);
        sendSnapshot(session, trackingStore.getTrackingSnapshot(orderId));
    }

    @Override
    public void afterConnectionClosed(WebSocketSession session, CloseStatus status) {
        Object orderId = session.getAttributes().get("orderId");
        if (orderId instanceof Long value) {
            sessionsByOrder.getOrDefault(value, Set.of()).remove(session);
        }
    }

    public void broadcast(Long orderId, DeliveryTrackingSnapshot snapshot) {
        Set<WebSocketSession> sessions = sessionsByOrder.getOrDefault(orderId, Set.of());
        for (WebSocketSession session : sessions) {
            if (session.isOpen()) {
                try {
                    sendSnapshot(session, snapshot);
                } catch (Exception ignored) {
                }
            }
        }
    }

    private void sendSnapshot(WebSocketSession session, DeliveryTrackingSnapshot snapshot) throws Exception {
        try {
            session.sendMessage(new TextMessage(objectMapper.writeValueAsString(snapshot)));
        } catch (JsonProcessingException exception) {
            throw new IllegalStateException("Failed to serialize snapshot", exception);
        }
    }

    private Long extractOrderId(URI uri) {
        if (uri == null || uri.getQuery() == null) {
            return null;
        }
        for (String queryItem : uri.getQuery().split("&")) {
            String[] pair = queryItem.split("=", 2);
            if (pair.length == 2 && "orderId".equals(pair[0])) {
                return Long.parseLong(pair[1]);
            }
        }
        return null;
    }
}
