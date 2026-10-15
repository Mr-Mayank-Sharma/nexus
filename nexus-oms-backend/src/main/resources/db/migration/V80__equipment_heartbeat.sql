-- When equipment last called Nexus. Null means "never reports in", so the watchdog leaves it alone.
ALTER TABLE nx_automation_systems ADD COLUMN IF NOT EXISTS last_heartbeat_at TIMESTAMP;
