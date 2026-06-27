package com.nexus.oms.integration.core;

import com.nexus.oms.integration.dto.IntegrationEvent;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.CopyOnWriteArrayList;

@Component
public class WebhookManager {

    private static final Logger log = LoggerFactory.getLogger(WebhookManager.class);

    private final Map<String, CopyOnWriteArrayList<WebhookHandler>> handlers = new ConcurrentHashMap<>();

    public void registerHandler(String topic, WebhookHandler handler) {
        handlers.computeIfAbsent(topic, k -> new CopyOnWriteArrayList<>()).add(handler);
        log.info("Registered webhook handler for topic: {}", topic);
    }

    public void dispatch(String topic, IntegrationEvent event) {
        CopyOnWriteArrayList<WebhookHandler> topicHandlers = handlers.get(topic);
        if (topicHandlers == null) {
            log.debug("No handlers for webhook topic: {}", topic);
            return;
        }
        for (WebhookHandler handler : topicHandlers) {
            try {
                handler.handle(event);
            } catch (Exception e) {
                log.error("Webhook handler {} failed for topic {}", handler.getClass().getSimpleName(), topic, e);
            }
        }
    }

    public void dispatch(Map<String, IntegrationEvent> events) {
        events.forEach(this::dispatch);
    }

    public interface WebhookHandler {
        boolean canHandle(String topic, IntegrationEvent event);
        void handle(IntegrationEvent event);
    }
}
