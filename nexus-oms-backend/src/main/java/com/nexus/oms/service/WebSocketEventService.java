package com.nexus.oms.service;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.messaging.simp.SimpMessagingTemplate;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;
import java.util.HashMap;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;

@Service
public class WebSocketEventService {

    private static final Logger log = LoggerFactory.getLogger(WebSocketEventService.class);

    private final SimpMessagingTemplate messagingTemplate;
    private final Set<String> onlineUsers = ConcurrentHashMap.newKeySet();

    public WebSocketEventService(SimpMessagingTemplate messagingTemplate) {
        this.messagingTemplate = messagingTemplate;
    }

    public void broadcastOrderUpdate(String orderId, String status, String message) {
        Map<String, Object> payload = new HashMap<>();
        payload.put("type", "ORDER_UPDATE");
        payload.put("orderId", orderId);
        payload.put("status", status);
        payload.put("message", message);
        payload.put("timestamp", LocalDateTime.now().toString());

        messagingTemplate.convertAndSend("/topic/orders", payload);
        log.debug("Broadcast order update: {} - {}", orderId, status);
    }

    public void broadcastInventoryAlert(String productId, String warehouseId, String alertType, String message) {
        Map<String, Object> payload = new HashMap<>();
        payload.put("type", "INVENTORY_ALERT");
        payload.put("productId", productId);
        payload.put("warehouseId", warehouseId);
        payload.put("alertType", alertType);
        payload.put("message", message);
        payload.put("timestamp", LocalDateTime.now().toString());

        messagingTemplate.convertAndSend("/topic/inventory", payload);
        log.debug("Broadcast inventory alert: {} - {}", productId, alertType);
    }

    public void broadcastShipmentUpdate(String shipmentId, String status, String trackingNumber) {
        Map<String, Object> payload = new HashMap<>();
        payload.put("type", "SHIPMENT_UPDATE");
        payload.put("shipmentId", shipmentId);
        payload.put("status", status);
        payload.put("trackingNumber", trackingNumber);
        payload.put("timestamp", LocalDateTime.now().toString());

        messagingTemplate.convertAndSend("/topic/shipments", payload);
        log.debug("Broadcast shipment update: {} - {}", shipmentId, status);
    }

    public void broadcastToUser(String username, String eventType, Map<String, Object> data) {
        Map<String, Object> payload = new HashMap<>();
        payload.put("type", eventType);
        payload.putAll(data);
        payload.put("timestamp", LocalDateTime.now().toString());

        messagingTemplate.convertAndSendToUser(username, "/queue/notifications", payload);
        log.debug("Send notification to user {}: {}", username, eventType);
    }

    public void broadcastSystemAlert(String severity, String title, String message) {
        Map<String, Object> payload = new HashMap<>();
        payload.put("type", "SYSTEM_ALERT");
        payload.put("severity", severity);
        payload.put("title", title);
        payload.put("message", message);
        payload.put("timestamp", LocalDateTime.now().toString());

        messagingTemplate.convertAndSend("/topic/system", payload);
        log.debug("Broadcast system alert: {} - {}", severity, title);
    }

    public void broadcastDashboardUpdate(String dashboardType, Map<String, Object> metrics) {
        Map<String, Object> payload = new HashMap<>();
        payload.put("type", "DASHBOARD_UPDATE");
        payload.put("dashboardType", dashboardType);
        payload.put("metrics", metrics);
        payload.put("timestamp", LocalDateTime.now().toString());

        messagingTemplate.convertAndSend("/topic/dashboard", payload);
        log.debug("Broadcast dashboard update: {}", dashboardType);
    }

    public void userConnected(String username) {
        onlineUsers.add(username);
        Map<String, Object> payload = new HashMap<>();
        payload.put("type", "USER_STATUS");
        payload.put("username", username);
        payload.put("status", "ONLINE");
        payload.put("timestamp", LocalDateTime.now().toString());

        messagingTemplate.convertAndSend("/topic/users", payload);
        log.debug("User connected: {}", username);
    }

    public void userDisconnected(String username) {
        onlineUsers.remove(username);
        Map<String, Object> payload = new HashMap<>();
        payload.put("type", "USER_STATUS");
        payload.put("username", username);
        payload.put("status", "OFFLINE");
        payload.put("timestamp", LocalDateTime.now().toString());

        messagingTemplate.convertAndSend("/topic/users", payload);
        log.debug("User disconnected: {}", username);
    }

    public Set<String> getOnlineUsers() {
        return Set.copyOf(onlineUsers);
    }
}
