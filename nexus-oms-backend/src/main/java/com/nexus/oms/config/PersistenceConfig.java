package com.nexus.oms.config;

import com.nexus.core.CoreTransactional;
import com.nexus.oms.tenancy.CoreRoutingDataSource;
import com.nexus.oms.tenancy.TenantDatabaseRegistry;
import com.zaxxer.hikari.HikariDataSource;
import jakarta.persistence.EntityManager;
import jakarta.persistence.EntityManagerFactory;
import org.hibernate.cfg.AvailableSettings;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.beans.factory.config.BeanDefinition;
import org.springframework.beans.factory.config.BeanFactoryPostProcessor;
import org.springframework.beans.factory.config.ConfigurableListableBeanFactory;
import org.springframework.boot.autoconfigure.jdbc.DataSourceProperties;
import org.springframework.boot.autoconfigure.orm.jpa.HibernateProperties;
import org.springframework.boot.autoconfigure.orm.jpa.HibernateSettings;
import org.springframework.boot.autoconfigure.orm.jpa.JpaProperties;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.boot.orm.jpa.EntityManagerFactoryBuilder;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Primary;
import org.springframework.data.jpa.repository.config.EnableJpaRepositories;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.orm.hibernate5.SpringBeanContainer;
import org.springframework.orm.jpa.JpaTransactionManager;
import org.springframework.orm.jpa.LocalContainerEntityManagerFactoryBean;
import org.springframework.orm.jpa.SharedEntityManagerCreator;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;

import javax.sql.DataSource;
import java.util.HashMap;
import java.util.Map;

/**
 * Two persistence units while the old model is being removed (doc 29, phase B).
 * <ul>
 *   <li><b>Old model</b> ({@code com.nexus.oms} entities and repositories): the shared database, through the
 *       row-level-security wrapper. Its beans are the primary ones, so the old code keeps working unchanged.</li>
 *   <li><b>Core</b> ({@code com.nexus.core.model}): a routing data source that sends every connection to the signed-in
 *       company's own database, or to the shared one when it has none. Core code uses {@link CoreTransactional} and
 *       the core beans by name. The injectable {@link EntityManager} is the core one: only core code injects it.</li>
 * </ul>
 * Until a company is given its own database both units use the same database, so behaviour does not change.
 */
@Configuration
@EnableJpaRepositories(basePackages = "com.nexus.oms", entityManagerFactoryRef = "entityManagerFactory", transactionManagerRef = "transactionManager")
public class PersistenceConfig {

    static final String OLD_MODEL_PACKAGES = "com.nexus.oms";
    static final String CORE_MODEL_PACKAGES = "com.nexus.core.model";
    static final String SPRING_DATA_SHARED_ENTITY_MANAGER_PREFIX = "jpaSharedEM_";

    // ── the shared database ──

    @Bean
    @Primary
    @ConfigurationProperties("spring.datasource")
    public DataSourceProperties dataSourceProperties() {
        return new DataSourceProperties();
    }

    /** The shared database's pool (wrapped for row-level security by {@link DataSourceConfig}). */
    @Bean
    @Primary
    @ConfigurationProperties("spring.datasource.hikari")
    public DataSource dataSource(DataSourceProperties dataSourceProperties) {
        return dataSourceProperties.initializeDataSourceBuilder().type(HikariDataSource.class).build();
    }

    @Bean
    @Primary
    public JdbcTemplate jdbcTemplate(DataSource dataSource) {
        return new JdbcTemplate(dataSource);
    }

    // ── the old model ──

    @Bean
    @Primary
    public LocalContainerEntityManagerFactoryBean entityManagerFactory(EntityManagerFactoryBuilder builder, DataSource dataSource,
                                                                       JpaProperties jpaProperties, HibernateProperties hibernateProperties,
                                                                       ConfigurableListableBeanFactory beanFactory) {
        return builder.dataSource(dataSource)
                .packages(OLD_MODEL_PACKAGES)
                .persistenceUnit("oldModel")
                .properties(hibernateSettings(jpaProperties, hibernateProperties, beanFactory))
                .build();
    }

    @Bean
    @Primary
    public PlatformTransactionManager transactionManager(@Qualifier("entityManagerFactory") EntityManagerFactory entityManagerFactory) {
        return new JpaTransactionManager(entityManagerFactory);
    }

    @Bean
    @Primary
    public TransactionTemplate transactionTemplate(PlatformTransactionManager transactionManager) {
        return new TransactionTemplate(transactionManager);
    }

    // ── the core ──

    /** Routes each connection to the company's own database, or the shared one. Not wrapped for row-level security. */
    @Bean
    public CoreRoutingDataSource coreDataSource(DataSource dataSource, TenantDatabaseRegistry registry) {
        return new CoreRoutingDataSource(dataSource, registry);
    }

    @Bean(CoreTransactional.ENTITY_MANAGER_FACTORY)
    public LocalContainerEntityManagerFactoryBean coreEntityManagerFactory(EntityManagerFactoryBuilder builder, CoreRoutingDataSource coreDataSource,
                                                                           JpaProperties jpaProperties, HibernateProperties hibernateProperties,
                                                                           ConfigurableListableBeanFactory beanFactory) {
        return builder.dataSource(coreDataSource)
                .packages(CORE_MODEL_PACKAGES)
                .persistenceUnit("core")
                .properties(hibernateSettings(jpaProperties, hibernateProperties, beanFactory))
                .build();
    }

    @Bean(CoreTransactional.MANAGER)
    public PlatformTransactionManager coreTransactionManager(@Qualifier(CoreTransactional.ENTITY_MANAGER_FACTORY) EntityManagerFactory coreEntityManagerFactory) {
        return new JpaTransactionManager(coreEntityManagerFactory);
    }

    @Bean(CoreTransactional.TEMPLATE)
    public TransactionTemplate coreTransactionTemplate(@Qualifier(CoreTransactional.MANAGER) PlatformTransactionManager coreTransactionManager) {
        return new TransactionTemplate(coreTransactionManager);
    }

    /** The entity manager every core class injects; bound to the core transaction of the current thread. */
    @Bean
    @Primary
    public EntityManager coreEntityManager(@Qualifier(CoreTransactional.ENTITY_MANAGER_FACTORY) EntityManagerFactory coreEntityManagerFactory) {
        return SharedEntityManagerCreator.createSharedEntityManager(coreEntityManagerFactory);
    }

    @Bean(CoreTransactional.JDBC)
    public JdbcTemplate coreJdbcTemplate(CoreRoutingDataSource coreDataSource) {
        return new JdbcTemplate(coreDataSource);
    }

    /**
     * Spring Data registers a shared entity manager for each unit ({@code jpaSharedEM_AWC_*}), both eligible for
     * autowiring. Only core code injects an {@code EntityManager} (the old model uses repositories), so those copies are
     * taken out of autowiring and {@link #coreEntityManager} is the one injected.
     */
    @Bean
    public static BeanFactoryPostProcessor onlyCoreEntityManagerIsInjected() {
        return beanFactory -> {
            for (String beanName : beanFactory.getBeanDefinitionNames()) {
                if (beanName.startsWith(SPRING_DATA_SHARED_ENTITY_MANAGER_PREFIX)) {
                    BeanDefinition definition = beanFactory.getBeanDefinition(beanName);
                    definition.setAutowireCandidate(false);
                    definition.setPrimary(false);
                }
            }
        };
    }

    /**
     * The Hibernate settings Spring Boot would apply to a single unit: spring.jpa.* (including ddl-auto and the naming
     * strategies), and Spring-managed beans for converters and listeners.
     */
    private static Map<String, Object> hibernateSettings(JpaProperties jpaProperties, HibernateProperties hibernateProperties,
                                                         ConfigurableListableBeanFactory beanFactory) {
        Map<String, Object> settings = new HashMap<>(hibernateProperties.determineHibernateProperties(jpaProperties.getProperties(), new HibernateSettings()));
        settings.put(AvailableSettings.BEAN_CONTAINER, new SpringBeanContainer(beanFactory));
        return settings;
    }
}
