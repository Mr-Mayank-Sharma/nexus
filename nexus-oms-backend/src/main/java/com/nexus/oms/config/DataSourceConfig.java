package com.nexus.oms.config;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.BeansException;
import org.springframework.beans.factory.config.BeanPostProcessor;
import org.springframework.context.annotation.Configuration;

import javax.sql.DataSource;

/**
 * Wraps the auto-configured HikariCP {@link DataSource} with
 * {@link TenantAwareDataSource} so every JDBC connection carries the
 * correct {@code app.current_tenant_id} session variable for
 * PostgreSQL Row-Level Security.
 *
 * <p>Uses a {@link BeanPostProcessor} rather than defining a new
 * {@code @Primary DataSource} bean to avoid circular-dependency issues
 * with Spring Boot's {@code DataSourceAutoConfiguration} (which skips
 * creating its own DataSource when any DataSource bean is already
 * registered).</p>
 */
@Configuration
public class DataSourceConfig implements BeanPostProcessor {

    private static final Logger log = LoggerFactory.getLogger(DataSourceConfig.class);

    @Override
    public Object postProcessAfterInitialization(Object bean, String beanName) throws BeansException {
        if (bean instanceof DataSource ds && !(bean instanceof TenantAwareDataSource)) {
            log.info("Wrapping DataSource '{}' with TenantAwareDataSource for RLS enforcement", beanName);
            return new TenantAwareDataSource(ds);
        }
        return bean;
    }
}
