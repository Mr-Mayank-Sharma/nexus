package com.nexus.core.job;

/**
 * Consumes one received {@code system_message}. The message type's {@code consume_service_name} is the Spring bean
 * name of the handler. It runs in its own transaction: throwing rolls back what it did and the message is retried.
 */
public interface CoreMessageHandler {

    void consume(String systemMessageId, String messageText);
}
