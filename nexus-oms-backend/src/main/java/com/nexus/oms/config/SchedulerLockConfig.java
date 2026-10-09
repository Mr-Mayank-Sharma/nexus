package com.nexus.oms.config;

import net.javacrumbs.shedlock.core.LockProvider;
import net.javacrumbs.shedlock.provider.jdbctemplate.JdbcTemplateLockProvider;
import net.javacrumbs.shedlock.spring.annotation.EnableSchedulerLock;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.jdbc.core.JdbcTemplate;

import javax.sql.DataSource;

/**
 * Cluster-wide locks for {@code @Scheduled} jobs. Without them every replica (Helm runs two)
 * executed each job: double brokering passes, duplicate syncs, repeated pre-order releases.
 *
 * <p>Locks live in the {@code shedlock} table (V71). Time comes from the database clock so
 * replicas with skewed clocks still agree. Jobs run without a tenant context, so the lock
 * table carries no tenant_id and is outside Row-Level Security.</p>
 */
@Configuration
@EnableSchedulerLock(defaultLockAtMostFor = "PT10M")
public class SchedulerLockConfig {

    @Bean
    public LockProvider lockProvider(DataSource dataSource) {
        return new JdbcTemplateLockProvider(JdbcTemplateLockProvider.Configuration.builder()
                .withJdbcTemplate(new JdbcTemplate(dataSource))
                .usingDbTime()
                .build());
    }
}
