-- Kafka delivers at least once, so a consumer sees the same event twice after a rebalance or a retry.
-- One row per (event, consumer group) lets the consumer skip what it already handled.
CREATE TABLE IF NOT EXISTS nx_processed_events (
    event_id     VARCHAR(64) PRIMARY KEY,
    topic        VARCHAR(128) NOT NULL,
    processed_at TIMESTAMP    NOT NULL DEFAULT now()
);
CREATE INDEX IF NOT EXISTS ix_processed_events_time ON nx_processed_events (processed_at);
